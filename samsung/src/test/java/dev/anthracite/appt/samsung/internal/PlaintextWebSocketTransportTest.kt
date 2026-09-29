package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * The plaintext remote channel against a loopback peer: handshake shape, one text frame, and that
 * failure is contained. Real sockets, so a hang is a failed test rather than a stuck build.
 */
class PlaintextWebSocketTransportTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val loopbackHost: String = requireNotNull(loopback.hostAddress)

    private fun plaintextTelevision(uuid: String?) =
        ConfirmedTelevision(
            id = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"),
            host = loopbackHost,
            tls = false,
            adoptedChannel = true,
            uuid = uuid,
        )

    // --- the resumed-pairing identity gate (connection.md#security-identity) ---------------

    @Test
    fun aResumedPlaintextPairingFailsClosedWhenTheFreshIdentityDiffers() = runBlocking {
        val transport =
            PlaintextWebSocketTransport(
                keepalive = 1.hours,
                fetchCurrentUuid = { _, _ -> "a-different-current-uuid" },
            )
        val saved = PairingSecret(token = "resume-token", pin = null)
        // IdentityMismatch, not Unreachable: the decision happened before any socket was opened.
        assertEquals(
            ConnectionAttempt.IdentityMismatch,
            transport.connect(plaintextTelevision("saved-uuid"), saved),
        )
    }

    @Test
    fun aResumedPlaintextPairingFailsClosedWhenTheFreshIdentityCannotBeEstablished() = runBlocking {
        val transport =
            PlaintextWebSocketTransport(keepalive = 1.hours, fetchCurrentUuid = { _, _ -> null })
        val saved = PairingSecret(token = "resume-token", pin = null)
        assertEquals(
            ConnectionAttempt.IdentityMismatch,
            transport.connect(plaintextTelevision("saved-uuid"), saved),
        )
    }

    @Test
    fun aResumedPlaintextPairingFailsClosedWithoutASavedUuidToCompare() = runBlocking {
        val transport =
            PlaintextWebSocketTransport(
                keepalive = 1.hours,
                fetchCurrentUuid = { _, _ -> throw AssertionError("nothing to compare against") },
            )
        val saved = PairingSecret(token = "resume-token", pin = null)
        assertEquals(
            ConnectionAttempt.IdentityMismatch,
            transport.connect(plaintextTelevision(uuid = null), saved),
        )
    }

    @Test
    fun aMatchedFreshIdentityProceedsToTheSocket() = runBlocking {
        val transport =
            PlaintextWebSocketTransport(
                keepalive = 1.hours,
                fetchCurrentUuid = { _, _ -> "saved-uuid" },
            )
        val saved = PairingSecret(token = "resume-token", pin = null)
        // Nothing listens on the plaintext port here, so passing the gate ends in Unreachable —
        // which is the proof that the gate passed and the socket was attempted.
        assertEquals(
            ConnectionAttempt.Unreachable,
            transport.connect(plaintextTelevision("saved-uuid"), saved),
        )
    }

    @Test
    fun firstContactIsNeverIdentityProbed() = runBlocking {
        val transport =
            PlaintextWebSocketTransport(
                keepalive = 1.hours,
                fetchCurrentUuid = { _, _ ->
                    throw AssertionError("first contact has no saved identity")
                },
            )
        assertEquals(
            ConnectionAttempt.Unreachable,
            transport.connect(plaintextTelevision("saved-uuid"), saved = null),
        )
    }

    @Test
    fun theRealProbeFormatsABareIpv6LiteralAsABracketedAuthority() = runBlocking {
        // The probe's fresh device-info request must carry a valid authority: a bare IPv6 literal
        // is bracketed exactly as the upgrade request brackets it. The probe reaches a matched
        // identity here, so passing the gate ends in Unreachable (nothing serves the remote
        // channel: the second connection is accepted by nobody, the upgrade read stalls past
        // its bound, and open fails normally), which is the proof the probe ran and returned
        // the document's UUID.
        val ipv6Loopback = InetAddress.getByName("::1")
        // The probe dials the television's derived plaintext remote port, so the stub serves there.
        ServerSocket(ConfirmedTelevision.PLAINTEXT_REMOTE_PORT, 1, ipv6Loopback).use { server ->
            server.soTimeout = 30_000
            var hostHeader: String? = null
            val peer = thread {
                server.accept().use { socket ->
                    val request = readRequest(socket.getInputStream())
                    hostHeader = request.lines().first { it.startsWith("Host:", ignoreCase = true) }
                    val document = """{"device":{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7"}}"""
                    val response =
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${document.encodeToByteArray().size}\r\n" +
                            "\r\n" +
                            document
                    socket.getOutputStream().write(response.encodeToByteArray())
                    socket.getOutputStream().flush()
                }
            }
            val television =
                plaintextTelevision("7c9e6679-7425-40de-944b-e07fc1f90ae7").copy(host = "::1")
            val attempt =
                PlaintextWebSocketTransport(keepalive = 1.hours)
                    .connect(television, PairingSecret(token = "resume-token", pin = null))
            peer.join()

            assertEquals(
                "the probe's Host header must bracket the IPv6 literal (got: $hostHeader)",
                "Host: [::1]:${server.localPort}",
                hostHeader,
            )
            assertEquals(
                "the served identity should have passed the gate (attempt: $attempt)",
                ConnectionAttempt.Unreachable,
                attempt,
            )
        }
    }

    @Test
    fun theRealProbeFailsClosedOnAnUnreadableDocument() = runBlocking {
        // The default probe returns null for a non-200 document, and a resumed pairing with no
        // establishable current identity is IdentityMismatch — no remote socket, no token.
        // The probe dials the television's derived plaintext remote port, so the stub serves there.
        ServerSocket(ConfirmedTelevision.PLAINTEXT_REMOTE_PORT, 1, loopback).use { server ->
            server.soTimeout = 30_000
            val peer = thread {
                server.accept().use { socket ->
                    val response = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n"
                    socket.getOutputStream().write(response.encodeToByteArray())
                    socket.getOutputStream().flush()
                }
            }
            val saved = PairingSecret(token = "resume-token", pin = null)
            val attempt =
                PlaintextWebSocketTransport(keepalive = 1.hours)
                    .connect(plaintextTelevision("saved-uuid"), saved)
            peer.join()

            assertEquals(ConnectionAttempt.IdentityMismatch, attempt)
        }
    }

    @Test
    fun handshakeIsATokenFreeUpgradeOnARawSocket() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            var request = ""
            val peer = thread {
                server.accept().use { socket ->
                    request = readRequest(socket.getInputStream())
                    writeHandshake(socket, request)
                }
            }
            val connection =
                PlaintextWebSocketTransport(keepalive = 1.hours)
                    .open(loopbackHost, server.localPort)
            assertNotNull(connection)
            connection!!.close()
            peer.join()

            val lines = request.lines()
            assertEquals("GET ${RemoteChannel.remoteRequestTarget()} HTTP/1.1", lines.first())
            assertTrue(lines.contains("Host: ${loopback.hostAddress}:${server.localPort}"))
            assertTrue(lines.any { it.equals("Upgrade: websocket", ignoreCase = true) })
            assertFalse("no token on first contact", request.contains("token", ignoreCase = true))
            assertFalse(request.contains("Authorization", ignoreCase = true))
        }
    }

    @Test
    fun aTextFrameIsReadAndACommandFrameIsWritten() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val written = CopyOnWriteArrayList<String>()
            val peer = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val request = readRequest(input)
                    writeHandshake(socket, request)
                    socket
                        .getOutputStream()
                        .write(unmaskedText("""{"event":"ms.channel.unauthorized"}"""))
                    socket.getOutputStream().flush()
                    written += readMaskedText(input)
                }
            }
            val connection =
                PlaintextWebSocketTransport(keepalive = 1.hours)
                    .open(loopbackHost, server.localPort)!!
            val inbound = withTimeout(5.seconds) { connection.frames.first() }
            assertTrue(connection.send("""{"method":"ms.remote.control"}"""))
            withTimeout(5.seconds) { while (written.isEmpty()) delay(POLL_MILLIS) }
            connection.close()
            peer.join()

            assertEquals("""{"event":"ms.channel.unauthorized"}""", inbound)
            assertEquals("""{"method":"ms.remote.control"}""", written.single())
        }
    }

    @Test
    fun connectFailureAndMalformedFramesDoNotThrow() = runBlocking {
        val closedPort = ServerSocket(0, 1, loopback).use { it.localPort }
        assertNull(PlaintextWebSocketTransport().open(loopbackHost, closedPort))

        ServerSocket(0, 1, loopback).use { server ->
            val peer = thread {
                server.accept().use { socket ->
                    val request = readRequest(socket.getInputStream())
                    writeHandshake(socket, request)
                    socket.getOutputStream().write(byteArrayOf(0xC1.toByte(), 0x00))
                    socket.getOutputStream().flush()
                }
            }
            val connection =
                PlaintextWebSocketTransport(keepalive = 1.hours)
                    .open(loopbackHost, server.localPort)!!
            val inbound = withTimeout(5.seconds) { connection.frames.firstOrNull() }
            connection.close()
            peer.join()
            assertNull(inbound)
        }
    }

    @Test
    fun aReadSideResetCompletesTheFrameFlowWithoutThrowing() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val peer = thread {
                server.accept().use { socket ->
                    val request = readRequest(socket.getInputStream())
                    writeHandshake(socket, request)
                    // RST rather than a clean FIN: the client read must not escape IOException.
                    socket.setSoLinger(true, 0)
                    socket.close()
                }
            }
            val connection =
                PlaintextWebSocketTransport(keepalive = 1.hours)
                    .open(loopbackHost, server.localPort)!!
            val inbound = withTimeout(5.seconds) { connection.frames.firstOrNull() }
            connection.close()
            peer.join()
            assertNull(inbound)
        }
    }

    @Test
    fun upgradeStallPastTheConnectBoundFailsNormally() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val accepted = CopyOnWriteArrayList<Socket>()
            val peer = thread { accepted += server.accept() }
            val started = System.nanoTime()
            val connection =
                PlaintextWebSocketTransport(connectTimeout = 300.milliseconds, keepalive = 1.hours)
                    .open(loopbackHost, server.localPort)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            accepted.forEach { it.close() }
            peer.join()
            assertNull(connection)
            assertTrue("upgrade stall must not hang the open", elapsedMs < 2_000)
        }
    }

    @Test
    fun aStalledCommandWriteIsContainedAsAFailedSend() = runBlocking {
        val stall = StallStream()
        val connection =
            PlaintextWebSocketTransport.PlaintextWebSocketConnection(
                Socket(),
                Dispatchers.IO,
                1.hours,
                200.milliseconds,
                stall,
            )
        val started = System.nanoTime()
        val sent = connection.send("{\"method\":\"ms.remote.control\"}")
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        connection.close()
        assertFalse(sent)
        assertTrue("command write must honour the 2-second bound", elapsedMs < 2_000)
    }

    @Test
    fun productionPlaintextAdapterDoesNotImportOkHttp() {
        val source =
            File(
                "src/main/java/dev/anthracite/appt/samsung/internal/PlaintextWebSocketTransport.kt"
            )
        assertTrue(source.isFile)
        val imports = source.readText().lineSequence().filter { it.startsWith("import ") }
        assertFalse(imports.any { it.contains("okhttp3") || it.contains("OkHttp") })
    }

    @Test
    fun theAppDoesNotOptIntoCleartextTraffic() {
        val manifests =
            listOf(
                File("../app/src/main/AndroidManifest.xml"),
                File("src/main/AndroidManifest.xml"),
            )
        manifests.forEach { manifest ->
            assertTrue(manifest.path, manifest.isFile)
            val text = manifest.readText()
            assertFalse(manifest.path, text.contains("usesCleartextTraffic"))
            assertFalse(manifest.path, text.contains("networkSecurityConfig"))
        }
        val xml = File("../app/src/main/res/xml")
        assertTrue(xml.isDirectory)
        xml.listFiles()?.forEach { file ->
            assertFalse(file.name, file.readText().contains("cleartextTrafficPermitted"))
        }
    }

    private fun readRequest(input: InputStream): String =
        generateSequence { readLine(input) }.takeWhile { it.isNotEmpty() }.joinToString("\n")

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            when (val byte = input.read()) {
                -1 -> return if (line.isEmpty()) null else line.toString()
                '\n'.code -> return line.toString().removeSuffix("\r")
                else -> line.append(byte.toChar())
            }
        }
    }

    private fun writeHandshake(socket: Socket, request: String) {
        val key =
            request
                .lines()
                .first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                .substringAfter(':')
                .trim()
        val accept =
            Base64.getEncoder()
                .encodeToString(
                    MessageDigest.getInstance("SHA-1")
                        .digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII))
                )
        val response =
            "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n" +
                "\r\n"
        socket.getOutputStream().write(response.encodeToByteArray())
        socket.getOutputStream().flush()
    }

    private fun unmaskedText(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x81.toByte(), payload.size.toByte()) + payload
    }

    private fun readMaskedText(input: InputStream): String {
        val first = input.read()
        val second = input.read()
        check(first >= 0 && second >= 0)
        val length = second and LENGTH_MASK
        val mask = ByteArray(MASK_KEY_BYTES) { input.read().toByte() }
        val payload =
            ByteArray(length) { index ->
                val raw = input.read()
                check(raw >= 0)
                (raw xor (mask[index % MASK_KEY_BYTES].toInt() and 0xFF)).toByte()
            }
        return payload.decodeToString()
    }

    private class StallStream : OutputStream() {
        private val closed = CountDownLatch(1)

        override fun write(b: Int) = stall()

        override fun write(b: ByteArray, off: Int, len: Int) = stall()

        override fun close() {
            closed.countDown()
        }

        private fun stall() {
            closed.await()
            throw IOException("closed")
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L
        const val POLL_MILLIS = 10L
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val LENGTH_MASK = 0x7F
        const val MASK_KEY_BYTES = 4
    }
}
