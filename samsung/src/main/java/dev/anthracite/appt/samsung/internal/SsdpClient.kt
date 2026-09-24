package dev.anthracite.appt.samsung.internal

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SSDP search as client traffic only (docs/architecture/discovery.md#probes).
 *
 * One ephemeral UDP socket per scan, bound to the scan's network, sends `M-SEARCH` for the two V1
 * search targets and reads the unicast replies. It never binds port 1900, never joins the
 * multicast group, and is closed when the scan ends or is cancelled.
 *
 * @param target where searches are sent: the SSDP multicast group in production, a loopback
 *   responder in tests.
 * @param resendAt offsets (from the start of the scan) at which the searches are repeated, since
 *   UDP may drop a datagram. All fall well inside the scan bound.
 */
internal class SsdpClient(
    private val target: InetSocketAddress,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val resendAt: List<Long> = RESEND_AT_MILLIS,
) {
    /** Replies whose response already identifies a Samsung device, as (sender, probe). */
    fun replies(lan: Lan): Flow<Pair<InetAddress, Probe>> = channelFlow {
        val socket = open(lan)
        launch { search(socket) }
        launch {
            val buffer = ByteArray(MAX_DATAGRAM_BYTES)
            while (isActive) {
                val reply = receive(socket, buffer) ?: continue
                Ssdp.classify(reply.second)?.let { probe -> send(reply.first to probe) }
            }
        }
        awaitClose { socket.close() }
    }

    private fun open(lan: Lan): DatagramSocket =
        try {
            DatagramSocket(0).also { socket ->
                try {
                    lan.bind(socket)
                    socket.soTimeout = RECEIVE_POLL_MILLIS
                } catch (failed: IOException) {
                    socket.close()
                    throw failed
                }
            }
        } catch (denied: SecurityException) {
            throw ScanAbort(LanPolicy.failureFor(denied), denied)
        } catch (failed: IOException) {
            throw ScanAbort(LanPolicy.failureFor(failed), failed)
        }

    private suspend fun search(socket: DatagramSocket) {
        val hostHeader = "${target.address.hostAddress}:${target.port}"
        val messages = Ssdp.SEARCH_TARGETS.map { Ssdp.mSearch(it, hostHeader) }
        var elapsed = 0L
        for (offset in resendAt) {
            delay(offset - elapsed)
            elapsed = offset
            messages.forEach { message -> send(socket, message) }
        }
    }

    private suspend fun send(socket: DatagramSocket, message: ByteArray) {
        try {
            blockingIo(dispatcher, onCancel = socket::close) {
                socket.send(DatagramPacket(message, message.size, target))
            }
        } catch (denied: SecurityException) {
            throw ScanAbort(LanPolicy.failureFor(denied), denied)
        } catch (failed: IOException) {
            throw ScanAbort(LanPolicy.failureFor(failed), failed)
        }
    }

    /** One reply as (sender, text), or null when the poll timed out without one. */
    private suspend fun receive(socket: DatagramSocket, buffer: ByteArray): Pair<InetAddress, String>? =
        try {
            blockingIo(dispatcher, onCancel = socket::close) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                packet.address to packet.data.decodeToString(0, packet.length)
            }
        } catch (ignored: SocketTimeoutException) {
            null
        } catch (failed: IOException) {
            throw ScanAbort(LanPolicy.failureFor(failed), failed)
        }

    companion object {
        private const val SSDP_PORT = 1900
        private const val RECEIVE_POLL_MILLIS = 250
        private const val MAX_DATAGRAM_BYTES = 2048

        /** Searches at 0 s, 1 s and 3 s; replies arrive within `MX` (2 s) of each. */
        val RESEND_AT_MILLIS: List<Long> = listOf(0L, 1_000L, 3_000L)

        /** The SSDP multicast group (239.255.255.250), built from octets rather than a literal. */
        private val SSDP_GROUP = byteArrayOf(239.toByte(), 255.toByte(), 255.toByte(), 250.toByte())

        fun multicastTarget(): InetSocketAddress =
            InetSocketAddress(InetAddress.getByAddress(SSDP_GROUP), SSDP_PORT)
    }
}
