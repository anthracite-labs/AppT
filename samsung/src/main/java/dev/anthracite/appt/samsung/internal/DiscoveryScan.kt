package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Each confirmed television is also written to [ConfirmedTelevisions] with the private control
 * evidence `open` needs later. Nothing in that record is caller-visible, and the raw device-info
 * document is discarded as soon as the card is emitted.
 *
 * The durable [SamsungSecretStore] is read-only here: a saved pairing makes the card `remembered`
 * and moves its availability to `ReadyToOpen` (samsung-interface.md#discover), and this is the only
 * place discovery consults it. The scan never writes secrets and never writes device records.
 *
 * The scan deliberately runs on its caller's dispatcher and never switches dispatchers itself: the
 * transport moves blocking socket work off-thread. That keeps the bound on the caller's clock,
 * which is what lets tests run the full 10 seconds in virtual time. The one exception is the
 * durable-store read below: file and Keystore work is confined to [readDispatcher] (IO in
 * production) so it never blocks the caller's (main) thread. Tests inject an unconfined dispatcher,
 * which keeps the read on the caller's clock like every other step.
 */
internal class DiscoveryScan(
    private val transport: DiscoveryTransport,
    private val confirmed: ConfirmedTelevisions,
    private val secrets: SamsungSecretStore,
    private val mintId: () -> String = TvIdentity::mint,
    private val bound: Duration = SCAN_BOUND,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
        // Private control evidence for `open`: the address the television answered on, and which
        // adopted channel its own flags select. Never caller-visible; the durable part is owned by
        // the pairing write, not by discovery.
        confirmed.record(
            ConfirmedTelevision(
                id = id,
                host = host,
                tls = info.tokenAuthSupport,
                adoptedChannel = info.availability != ControlAvailability.Unsupported,
                uuid = info.uuid,
                displayName = info.name.takeIf { it.isNotBlank() },
            )
        )
        // samsung-interface.md#discover: `remembered` means a secret or saved identity exists for
        // this id, and `ReadyToOpen` means `open` should resume a saved pairing. Both read the
        // durable store; neither is inferred from a year, a model, or a name.
        // The only dispatcher switch the scan makes: durable file/Keystore reads leave the
        // caller's (main) thread, and nothing here participates in the caller's bound clock.
        val (savedSecret, hasRecord) =
            withContext(readDispatcher) {
                secrets.loadSecret(id) to (secrets.loadDevice(id) != null)
            }
        val remembered = hasRecord || savedSecret != StoredSecret.Absent
        val availability =
            when {
                info.availability == ControlAvailability.Unsupported ->
                    ControlAvailability.Unsupported
                savedSecret is StoredSecret.Available -> ControlAvailability.ReadyToOpen
                else -> ControlAvailability.NeedsPairing
            }
        emit(
            DiscoveryEvent.Found(
                DiscoveredTv(
                    id = id,
                    name = info.name,
                    remembered = remembered,
                    stableIdentity = info.uuid != null,
                    availability = availability,
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
         * S02 reads device-info over port 8001, and S03 keeps that read: the TokenAuthSupport flag
         * is the handshake evidence that selects the TLS remote channel (port 8002) for the
         * session, so no 8002 probe is needed before open.
         */
        val DEVICE_INFO_PORTS: List<Int> = listOf(8001)
    }
}
