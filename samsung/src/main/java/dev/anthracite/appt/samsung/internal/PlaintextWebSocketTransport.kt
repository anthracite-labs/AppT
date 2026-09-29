package dev.anthracite.appt.samsung.internal

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The plaintext [SessionTransport] adapter: a bounded raw TCP WebSocket on port 8001
 * (docs/architecture/protocol.md#endpoints, modules.md#internal-seams-inside-samsung).
 *
 * It exists so the adopted `ws://` fallback does not require application-wide cleartext traffic.
 * Device-info on 8001 already uses a raw socket for the same reason. TLS stays on OkHttp.
 *
 * Nothing about a URL, frame, token, address or port is logged.
 */
internal class PlaintextWebSocketTransport(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeout: Duration = CONNECT_TIMEOUT,
    private val keepalive: Duration = KEEPALIVE_INTERVAL,
    private val writeTimeout: Duration = COMMAND_WRITE_TIMEOUT,
    private val fetchCurrentUuid: suspend (String, Int) -> String? =
        { host, port -> fetchPlaintextDeviceUuid(host, port) },
) : SessionTransport {

    /**
     * Opens the plaintext channel. The saved pairing never changes what is written here
     * (protocol.md#endpoints): the plaintext remote-channel URL carries the encoded client name and
     * never a token, so a saved token is structurally absent from this channel — including for a
     * television that previously completed TLS pairing (connection.md#security-identity).
     *
     * A resumed plaintext pairing re-establishes identity here, at the socket seam: the current
     * protocol UUID is read from fresh device-info and compared against the saved one before any
     * remote-channel socket is opened. A record-resolved television carries the saved UUID as its
     * own, so the record alone is not evidence — the comparison needs current facts
     * (connection.md#security-identity). A changed UUID, or one that cannot be established, is
     * [ConnectionAttempt.IdentityMismatch]: fail closed, no socket, no token.
     */
    override suspend fun connect(
        television: ConfirmedTelevision,
        saved: PairingSecret?,
    ): ConnectionAttempt {
        if (saved != null) {
            val expected = television.uuid ?: return ConnectionAttempt.IdentityMismatch
            val current = fetchCurrentUuid(television.host, television.remotePort)
            if (current == null || current != expected) {
                return ConnectionAttempt.IdentityMismatch
            }
        }
        return open(television.host, television.remotePort)?.let(ConnectionAttempt::Opened)
            ?: ConnectionAttempt.Unreachable
    }

    /**
     * Opens the plaintext channel at [host]:[port]. Production always uses port 8001; tests may
     * point at a loopback peer.
     */
    suspend fun open(host: String, port: Int): SessionConnection? {
        val socket = Socket()
        val connection =
            try {
                blockingIo(dispatcher, onCancel = socket::close) { handshake(socket, host, port) }
            } catch (cancelled: CancellationException) {
                socket.closeQuietly()
                throw cancelled
            } catch (_: IOException) {
                socket.closeQuietly()
                null
            } catch (_: SecurityException) {
                socket.closeQuietly()
                null
            }
        connection?.start()
        return connection
    }

    private fun handshake(socket: Socket, host: String, port: Int): PlaintextWebSocketConnection? {
        val address = InetAddress.getByName(unbracketed(host))
        val boundMillis = connectTimeout.inWholeMilliseconds.toInt()
        socket.connect(InetSocketAddress(address, port), boundMillis)
        // TCP accept is not enough: a silent peer would stall forever on the upgrade read.
        socket.soTimeout = boundMillis
        val key = websocketKey()
        val output = socket.getOutputStream()
        output.write(upgradeRequest(host, port, key).encodeToByteArray())
        output.flush()
        if (!readUpgradeAccepted(socket.getInputStream(), key)) {
            socket.closeQuietly()
            return null
        }
        socket.soTimeout = 0
        return PlaintextWebSocketConnection(socket, dispatcher, keepalive, writeTimeout, output)
    }

    internal class PlaintextWebSocketConnection(
        private val socket: Socket,
        private val dispatcher: CoroutineDispatcher,
        private val keepalive: Duration,
        private val writeTimeout: Duration,
        private val output: OutputStream,
    ) : SessionConnection {

        private val inbound = Channel<String>(Channel.UNLIMITED)
        private val closed = AtomicBoolean(false)
        private val pending = AtomicInteger(0)
        private val writeLock = Any()
        private val scope = CoroutineScope(dispatcher + SupervisorJob())

        override val certificateIdentity: String? = null

        override val frames: Flow<String> = flow {
            for (frame in inbound) {
                emit(frame)
            }
        }

        fun start() {
            scope.launch { readLoop() }
            scope.launch { pingLoop(keepalive) }
        }

        override suspend fun send(frame: String): Boolean {
            if (closed.get() || pending.get() >= UNSENT_FRAME_CAP) return false
            pending.incrementAndGet()
            val written =
                try {
                    withTimeout(writeTimeout) {
                        blockingIo(dispatcher, onCancel = ::abortWrite) {
                            write(WebSocketFrames.encodeText(frame))
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    false
                } catch (_: IOException) {
                    false
                } finally {
                    pending.decrementAndGet()
                }
            return written
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            write(WebSocketFrames.encodeClose())
            scope.cancel()
            inbound.close()
            socket.closeQuietly()
        }

        private suspend fun pingLoop(keepalive: Duration) {
            while (!closed.get()) {
                delay(keepalive)
                if (!write(WebSocketFrames.encodePing())) close()
            }
        }

        private fun readLoop() {
            try {
                val input = socket.getInputStream()
                while (!closed.get()) {
                    when (val inboundFrame = WebSocketFrames.read(input)) {
                        WebSocketFrames.Inbound.End,
                        WebSocketFrames.Inbound.Invalid -> break
                        is WebSocketFrames.Inbound.Frame -> onFrame(inboundFrame)
                    }
                }
            } catch (_: IOException) {
                // A reset or closed stream is connection loss, not a crash.
            }
            inbound.close()
            socket.closeQuietly()
        }

        private fun onFrame(frame: WebSocketFrames.Inbound.Frame) {
            when {
                !frame.fin -> close()
                frame.opcode == WebSocketFrames.OPCODE_TEXT -> {
                    inbound.trySend(frame.payload.decodeToString())
                }
                frame.opcode == WebSocketFrames.OPCODE_PING ->
                    write(WebSocketFrames.encodePong(frame.payload))
                frame.opcode == WebSocketFrames.OPCODE_CLOSE -> close()
                frame.opcode == WebSocketFrames.OPCODE_PONG ||
                    frame.opcode == WebSocketFrames.OPCODE_CONTINUATION -> Unit
                else -> Unit
            }
        }

        private fun abortWrite() {
            try {
                output.close()
            } catch (_: IOException) {
                // Closing is how a stalled write is unblocked.
            }
            socket.closeQuietly()
        }

        private fun write(bytes: ByteArray): Boolean {
            if (closed.get()) return false
            return try {
                synchronized(writeLock) {
                    output.write(bytes)
                    output.flush()
                }
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    companion object {
        /** protocol.md: connect timeout. */
        val CONNECT_TIMEOUT: Duration = 5.seconds

        /** protocol.md: keepalive ping interval on the live socket. */
        val KEEPALIVE_INTERVAL: Duration = 20.seconds

        /** connection.md: command write timeout. */
        val COMMAND_WRITE_TIMEOUT: Duration = 2.seconds

        /** samsung-interface.md: unsent frames held before further commands are rejected. */
        const val UNSENT_FRAME_CAP: Int = 32

        private const val WEBSOCKET_KEY_BYTES = 16
        private const val WEBSOCKET_VERSION = "13"
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val MAX_HEADER_LINES = 64
        private const val MAX_LINE_BYTES = 8 * 1024
        private val SWITCHING_PROTOCOLS = Regex("^HTTP/1\\.[01] 101(\\s.*)?$")

        private fun upgradeRequest(host: String, port: Int, key: String): String {
            val authority = handshakeAuthority(host, port)
            return "GET ${RemoteChannel.remoteRequestTarget()} HTTP/1.1\r\n" +
                "Host: $authority\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: $WEBSOCKET_VERSION\r\n" +
                "\r\n"
        }

        private fun handshakeAuthority(host: String, port: Int): String {
            val literal = unbracketed(host)
            val display = if (':' in literal) "[$literal]" else literal
            return "$display:$port"
        }

        private fun unbracketed(host: String): String = host.removePrefix("[").removeSuffix("]")

        /**
         * The television's current protocol UUID, read from a fresh bounded device-info request to
         * the same host and port the plaintext channel would open
         * (connection.md#security-identity). Null when the document is unreachable, over-limit, or
         * carries no UUID — the caller fails closed on null. The socket pattern mirrors [open]:
         * raw, bounded, and never bound to a scan's LAN context, with no URL, address or port
         * recorded anywhere.
         */
        private suspend fun fetchPlaintextDeviceUuid(host: String, port: Int): String? {
            val socket = Socket()
            return try {
                socket.use {
                    blockingIo(dispatcher = Dispatchers.IO, onCancel = socket::close) {
                        socket.soTimeout = DEVICE_INFO_READ_TIMEOUT_MILLIS
                        socket.connect(
                            InetSocketAddress(InetAddress.getByName(unbracketed(host)), port),
                            DEVICE_INFO_CONNECT_TIMEOUT_MILLIS,
                        )
                        val request =
                            "GET ${DeviceInfoHttp.DEVICE_INFO_PATH} HTTP/1.1\r\n" +
                                "Host: ${handshakeAuthority(host, port)}\r\n" +
                                "Accept: application/json\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                        // Write without closing the stream: closing a socket's output stream
                        // closes the socket, and the response is still to be read.
                        val output = socket.getOutputStream()
                        output.write(request.encodeToByteArray())
                        output.flush()
                        BoundedHttpResponse.readOkBody(socket.inputStream, MAX_DEVICE_INFO_BYTES)
                            ?.let(DeviceInfoParser::parse)
                            ?.uuid
                    }
                }
            } catch (cancelled: CancellationException) {
                socket.closeQuietly()
                throw cancelled
            } catch (ignored: IOException) {
                socket.closeQuietly()
                null
            } catch (ignored: SecurityException) {
                socket.closeQuietly()
                null
            }
        }

        private fun websocketKey(): String {
            val bytes = ByteArray(WEBSOCKET_KEY_BYTES)
            SecureRandom().nextBytes(bytes)
            return Base64.getEncoder().encodeToString(bytes)
        }

        private fun acceptFor(key: String): String {
            val digest =
                MessageDigest.getInstance("SHA-1")
                    .digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII))
            return Base64.getEncoder().encodeToString(digest)
        }

        private fun readUpgradeAccepted(input: InputStream, key: String): Boolean {
            val status = readLine(input) ?: return false
            val headers = if (SWITCHING_PROTOCOLS.matches(status)) readHeaders(input) else null
            return headers != null && upgradeMatches(headers, key)
        }

        private fun upgradeMatches(headers: Map<String, String>, key: String): Boolean {
            val upgrade = headers["UPGRADE"]?.lowercase(Locale.ROOT)
            val connection = headers["CONNECTION"]?.lowercase(Locale.ROOT).orEmpty()
            return upgrade == "websocket" &&
                connection.contains("upgrade") &&
                headers["SEC-WEBSOCKET-ACCEPT"] == acceptFor(key)
        }

        private fun readHeaders(input: InputStream): Map<String, String>? {
            val headers = mutableMapOf<String, String>()
            repeat(MAX_HEADER_LINES) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) return headers
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().uppercase(Locale.ROOT)] =
                        line.substring(colon + 1).trim()
                }
            }
            return null
        }

        private fun readLine(input: InputStream): String? {
            val line = ByteArrayOutputStream()
            while (line.size() <= MAX_LINE_BYTES) {
                when (val byte = input.read()) {
                    -1 -> return null
                    '\n'.code -> return line.toByteArray().decodeToString().removeSuffix("\r")
                    else -> line.write(byte)
                }
            }
            return null
        }
    }
}

private fun Socket.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // Already closed, or the peer reset. The holder is releasing either way.
    }
}
private const val DEVICE_INFO_CONNECT_TIMEOUT_MILLIS = 5_000

private const val DEVICE_INFO_READ_TIMEOUT_MILLIS = 3_000
