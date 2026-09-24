package dev.anthracite.appt.samsung.internal

import java.net.DatagramSocket
import java.net.Socket
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A [Lan] that binds nothing; fixture traffic never reaches a socket. */
internal class TestLan(override val requiresMulticastLock: Boolean = true) : Lan {
    override fun bind(socket: DatagramSocket) = Unit

    override fun bind(socket: Socket) = Unit
}

/**
 * Replays a [Fixture] through the real classifiers ([Ssdp.classify], [AirPlayTxt]) and records
 * every outbound request, lock acquisition and probe lifetime so tests can assert on them.
 *
 * The transport has no way to send a key, text or launch frame at all: the only outbound
 * operation discovery has is [deviceInfo], and it is recorded in [outbound].
 */
internal class FixtureTransport(
    private val fixture: Fixture,
    private val lan: Lan? = TestLan(),
    /** Thrown by the probes at [abortAtMs], e.g. a blocked local-network socket. */
    private val abort: ScanAbort? = null,
    private val abortAtMs: Long = 0,
) : DiscoveryTransport {
    data class Request(val host: String, val port: Int)

    val outbound = mutableListOf<Request>()
    val lockLog = mutableListOf<String>()
    var probesRunning = 0
        private set

    private var scans = 0

    val lockHeld: Boolean
        get() = lockLog.count { it == ACQUIRE } > lockLog.count { it == RELEASE }

    override fun activeLan(): Lan? = lan

    override fun holdMulticastLock(): AutoCloseable {
        lockLog += ACQUIRE
        return AutoCloseable { lockLog += RELEASE }
    }

    override fun candidates(lan: Lan): Flow<Candidate> = flow {
        val scan = ++scans
        probesRunning++
        try {
            var now = 0L
            val events =
                fixture.probes
                    .filter { it.scan == null || it.scan == scan }
                    .map { it.atMs to classify(it) } +
                    listOfNotNull(abort?.let { abortAtMs to null })
            for ((atMs, candidate) in events.sortedBy { it.first }) {
                delay(atMs - now)
                now = atMs
                if (candidate != null) emit(candidate) else if (abort != null && atMs == abortAtMs) throw abort
            }
            // The probes keep listening until the scan ends.
            awaitCancellation()
        } finally {
            probesRunning--
        }
    }

    override suspend fun deviceInfo(lan: Lan, host: String, port: Int): String? {
        outbound += Request(host, port)
        val served = fixture.deviceInfo[host to port] ?: return awaitCancellation()
        delay(served.delayMs)
        return served.document
    }

    private fun classify(event: Fixture.ProbeEvent): Candidate? =
        when (event) {
            is Fixture.ProbeEvent.SsdpReply ->
                Ssdp.classify(event.response)?.let { Candidate(event.host, it) }
            is Fixture.ProbeEvent.AirPlayService ->
                if (AirPlayTxt.identifiesSamsung(event.txt)) {
                    Candidate(event.host, Probe.AirPlaySamsung)
                } else {
                    null
                }
        }

    companion object {
        const val ACQUIRE = "acquire"
        const val RELEASE = "release"
    }
}
