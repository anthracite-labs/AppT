package dev.anthracite.appt.samsung.internal

import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
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
                    .open(loopback.hostAddress, server.localPort)
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
                    .open(loopback.hostAddress, server.localPort)!!
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
        assertNull(PlaintextWebSocketTransport().open(loopback.hostAddress, closedPort))

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
                    .open(loopback.hostAddress, server.localPort)!!
            val inbound = withTimeout(5.seconds) { connection.frames.firstOrNull() }
            connection.close()
            peer.join()
            assertNull(inbound)
        }
    }

    @Test
    fun productionPlaintextAdapterDoesNotImportOkHttp() {
        val source =
            File("src/main/java/dev/anthracite/appt/samsung/internal/PlaintextWebSocketTransport.kt")
        assertTrue(source.isFile)
        assertFalse(source.readText().contains("okhttp3"))
        assertFalse(source.readText().contains("OkHttp"))
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
            request.lines().first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
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

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L
        const val POLL_MILLIS = 10L
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val LENGTH_MASK = 0x7F
        const val MASK_KEY_BYTES = 4
    }
}
