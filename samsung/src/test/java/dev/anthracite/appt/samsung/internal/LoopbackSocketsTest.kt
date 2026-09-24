package dev.anthracite.appt.samsung.internal

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * The two real socket clients against loopback peers: request shape, bounds, and that
 * cancellation closes sockets promptly instead of waiting for a socket timeout.
 */
class LoopbackSocketsTest {
    /** Real sockets and threads: a regression must fail here, never hang the build. */
    @get:Rule val timeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    @Test
    fun deviceInfoIsOneTokenFreeGetToTheApiPath() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            var request = ""
            val peer = thread {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    request = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.joinToString("\n")
                    val body = """{"device":{"type":"Samsung SmartTV"}}"""
                    socket.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body".encodeToByteArray()
                    )
                }
            }
            val document = DeviceInfoHttp().get(TestLan(), loopback, server.localPort)
            peer.join()

            assertEquals("""{"device":{"type":"Samsung SmartTV"}}""", document)
            val lines = request.lines()
            assertEquals("GET /api/v2/ HTTP/1.1", lines.first())
            assertTrue(lines.contains("Host: ${loopback.hostAddress}:${server.localPort}"))
            assertFalse("no token during discovery", request.contains("token", ignoreCase = true))
            assertFalse(request.contains("Authorization", ignoreCase = true))
        }
    }

    @Test
    fun unreadableDeviceInfoIsNull() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val peer = thread {
                server.accept().use { it.getOutputStream().write("HTTP/1.1 302 Found\r\n\r\n".encodeToByteArray()) }
            }
            assertNull(DeviceInfoHttp().get(TestLan(), loopback, server.localPort))
            peer.join()
        }
        // Nothing listening: refused, which is simply "no card".
        val closedPort = ServerSocket(0, 1, loopback).use { it.localPort }
        assertNull(DeviceInfoHttp().get(TestLan(), loopback, closedPort))
    }

    @Test
    fun cancellingADeviceInfoReadClosesTheSocketPromptly() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            // Accepts and then never answers; the client's read timeout is far away.
            val accepted = CopyOnWriteArrayList<java.net.Socket>()
            val peer = thread { accepted += server.accept() }
            val client = DeviceInfoHttp(readTimeoutMillis = 60_000)
            val started = System.nanoTime()
            val job = launch { client.get(TestLan(), loopback, server.localPort) }
            // Wait for the connection by suspending, never by blocking: runBlocking has one thread,
            // and Thread.join() here would stop the launched client from ever connecting.
            withTimeout(5.seconds) { while (accepted.isEmpty()) delay(POLL_MILLIS) }
            job.cancel()
            withTimeout(2.seconds) { job.join() }
            assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
            accepted.forEach { it.close() }
            peer.join()
        }
    }

    @Test
    fun ssdpSearchesOnlyTheV1TargetsAndKeepsOnlySamsungReplies() = runBlocking {
        DatagramSocket(0, loopback).use { responder ->
            val searches = CopyOnWriteArrayList<String>()
            responder.soTimeout = 5_000
            val peer = thread {
                // Both searches arrive before any reply, so the client cannot stop early.
                val received =
                    List(2) {
                        DatagramPacket(ByteArray(2048), 2048).also { packet -> responder.receive(packet) }
                    }
                received.forEach { packet ->
                    val search = packet.data.decodeToString(0, packet.length)
                    searches += search
                    val reply =
                        if (search.contains(Ssdp.DIAL)) {
                            "HTTP/1.1 200 OK\r\nST: ${Ssdp.DIAL}\r\nSERVER: Linux Cast\r\n\r\n"
                        } else {
                            "HTTP/1.1 200 OK\r\nST: ${Ssdp.REMOTE_CONTROL_RECEIVER}\r\n\r\n"
                        }
                    val bytes = reply.encodeToByteArray()
                    responder.send(DatagramPacket(bytes, bytes.size, packet.socketAddress))
                }
            }
            val client =
                SsdpClient(InetSocketAddress(loopback, responder.localPort), resendAt = listOf(0L))
            val (host, probe) = withTimeout(5.seconds) { client.replies(TestLan()).first() }
            peer.join()

            assertEquals(loopback, host)
            assertEquals(Probe.RemoteControlReceiver, probe)
            assertEquals(2, searches.size)
            assertTrue(searches.any { it.contains("ST: ${Ssdp.REMOTE_CONTROL_RECEIVER}\r\n") })
            assertTrue(searches.any { it.contains("ST: ${Ssdp.DIAL}\r\n") })
            assertTrue(searches.none { it.contains("ssdp:all") })
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 30L
        const val POLL_MILLIS = 10L
    }
}
