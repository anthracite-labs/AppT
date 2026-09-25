package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One bounded scan (docs/architecture/discovery.md#what-a-scan-is).
 *
 * Policy lives here; the network lives behind [DiscoveryTransport]:
 * 1. No usable non-VPN Wi-Fi/Ethernet network: `Failed(Unreachable)` once.
 * 2. Acquire the multicast lock (Wi-Fi only) for this scan.
 * 3. Collect candidates until the bound. Every new candidate host is confirmed with device-info on
 *    that host, port 8001, before any card is emitted.
 * 4. Release the lock in `finally` (bound, failure, and cancellation alike), then emit `Finished`
 *    or the single failure.
 *
 * The scan deliberately runs on its caller's dispatcher and never switches dispatchers itself: the
 * transport moves blocking socket work off-thread. That keeps the bound on the caller's clock,
 * which is what lets tests run the full 10 seconds in virtual time.
 */
internal class DiscoveryScan(
    private val transport: DiscoveryTransport,
    private val mintId: () -> String = TvIdentity::mint,
    private val bound: Duration = SCAN_BOUND,
) {
    suspend fun run(emit: suspend (DiscoveryEvent) -> Unit) {
        val lan = transport.activeLan()
        if (lan == null) {
            emit(DiscoveryEvent.Failed(TvFailure.Unreachable))
            return
        }
        val failure = probeWithinBound(lan, emit)
        emit(failure?.let(DiscoveryEvent::Failed) ?: DiscoveryEvent.Finished)
    }

    /** Returns the failure that ended the scan early, or null when it reached the bound. */
    private suspend fun probeWithinBound(
        lan: Lan,
        emit: suspend (DiscoveryEvent) -> Unit,
    ): TvFailure? {
        val lock = if (lan.requiresMulticastLock) transport.holdMulticastLock() else null
        return try {
            withTimeoutOrNull(bound) { probe(lan, emit) }
            null
        } catch (abort: ScanAbort) {
            abort.failure
        } finally {
            lock?.close()
        }
    }

    private suspend fun probe(lan: Lan, emit: suspend (DiscoveryEvent) -> Unit): Nothing =
        coroutineScope {
            val claims = ScanClaims()
            transport.candidates(lan).collect { candidate ->
                if (claims.claimHost(candidate.host)) {
                    launch { confirm(lan, candidate.host, claims, emit) }
                }
            }
            // Probes may finish sending early; the scan still runs to its bound so a late
            // television can appear.
            awaitCancellation()
        }

    private suspend fun confirm(
        lan: Lan,
        host: String,
        claims: ScanClaims,
        emit: suspend (DiscoveryEvent) -> Unit,
    ) {
        val document =
            DEVICE_INFO_PORTS.firstNotNullOfOrNull { port -> transport.deviceInfo(lan, host, port) }
        val info = document?.let(DeviceInfoParser::parse) ?: return
        // A document that names a different host does not belong to this candidate
        // (protocol.md#limits); soundbars, speakers and players are not televisions.
        if (!info.isTelevision || (info.reportedHost != null && info.reportedHost != host)) return

        val id = TvId(info.uuid ?: mintId())
        if (!claims.claimTv(id)) return
        emit(
            DiscoveryEvent.Found(
                DiscoveredTv(
                    id = id,
                    name = info.name,
                    // No samsung-private record exists before S04's store.
                    remembered = false,
                    stableIdentity = info.uuid != null,
                    availability = info.availability,
                )
            )
        )
    }

    /** Per-scan dedup: each host is confirmed once, each television is emitted once. */
    private class ScanClaims {
        private val hosts = ConcurrentHashMap.newKeySet<String>()
        private val tvs = ConcurrentHashMap.newKeySet<TvId>()

        fun claimHost(host: String): Boolean = hosts.add(host)

        fun claimTv(id: TvId): Boolean = tvs.add(id)
    }

    companion object {
        /** discovery.md: explicit scan bound, 10 seconds from start, then `Finished`. */
        val SCAN_BOUND: Duration = 10.seconds

        /**
         * Device-info ports tried for a candidate, on the candidate's own host only. No other port
         * is ever contacted: there is no LAN port scan.
         *
         * S02 reads device-info over port 8001. The 8002 TLS variant needs the candidate-pin trust
         * rules in connection.md, which arrive with the S03 session; see the S02 pull request.
         */
        val DEVICE_INFO_PORTS: List<Int> = listOf(8001)
    }
}
