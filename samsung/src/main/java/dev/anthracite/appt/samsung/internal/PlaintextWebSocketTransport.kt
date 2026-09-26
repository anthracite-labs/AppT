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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

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
) : SessionTransport {

    override suspend fun connect(television: ConfirmedTelevision): SessionConnection? =
        open(television.host, television.remotePort)

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
        socket.connect(InetSocketAddress(address, port), connectTimeout.inWholeMilliseconds.toInt())
        val key = websocketKey()
        val output = socket.getOutputStream()
        output.write(upgradeRequest(host, port, key).encodeToByteArray())
        output.flush()
        return if (readUpgradeAccepted(socket.getInputStream(), key)) {
            PlaintextWebSocketConnection(socket, dispatcher, keepalive)
        } else {
            socket.closeQuietly()
            null
        }
    }

    private class PlaintextWebSocketConnection(
        private val socket: Socket,
        dispatcher: CoroutineDispatcher,
        private val keepalive: Duration,
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
            return try {
                write(WebSocketFrames.encodeText(frame))
            } finally {
                pending.decrementAndGet()
            }
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

        private fun write(bytes: ByteArray): Boolean {
            if (closed.get()) return false
            return try {
                synchronized(writeLock) {
                    val output: OutputStream = socket.getOutputStream()
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

        private fun unbracketed(host: String): String =
            host.removePrefix("[").removeSuffix("]")

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
