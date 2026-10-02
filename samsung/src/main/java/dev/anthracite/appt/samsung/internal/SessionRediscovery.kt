package dev.anthracite.appt.samsung.internal

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The internal rediscovery seam for supervised reconnect (docs/architecture/connection.md
 * "Supervised reconnect").
 *
 * When the last known address stops answering, the session runs **one** bounded rediscovery for the
 * saved protocol UUID before spending the rest of the reconnect budget on a dead address. This is
 * not the caller-facing `discover()` scan: it emits no cards, it is never started by a screen, it
 * has no scan UI, and it accepts only the television whose UUID the saved pairing recorded.
 *
 * The returned television keeps the same `TvId` and may carry a new address. Everything
 * credential-shaped is unchanged: the saved security identity is still checked on the next connect,
 * so a different television answering on the new address fails closed as `IdentityChanged` rather
 * than receiving the saved token.
 *
 * This is a test detail of the `samsung` module, like the other internal transports
 * (docs/architecture/modules.md).
 */
internal fun interface SessionRediscovery {
    /**
     * One bounded rediscovery of [television]'s saved identity, or null when nothing matched within
     * the bound. Never throws across the seam; a failure is a null result.
     */
    suspend fun rediscover(television: ConfirmedTelevision): ConfirmedTelevision?

    companion object {
        /** For sessions with no rediscovery wiring; a reconnect then stays on the saved address. */
        val None: SessionRediscovery =
            object : SessionRediscovery {
                override suspend fun rediscover(
                    television: ConfirmedTelevision
                ): ConfirmedTelevision? = null
            }
    }
}

/**
 * The production [SessionRediscovery]: client-side probes on the active non-VPN LAN, then
 * device-info confirmation on the candidate's own host, matching the saved protocol UUID only.
 *
 * It reuses [DiscoveryTransport], so the VPN rule, the LAN-address rule and the bounded device-info
 * read are the same rules the scan uses (docs/architecture/discovery.md). The multicast lock is
 * held only for this bounded rediscovery and always released.
 */
internal class LanSessionRediscovery(
    private val bound: Duration = REDISCOVERY_BOUND,
    private val newTransport: () -> DiscoveryTransport,
) : SessionRediscovery {

    override suspend fun rediscover(television: ConfirmedTelevision): ConfirmedTelevision? {
        val uuid = television.uuid ?: return null
        val transport = newTransport()
        val lan = transport.activeLan() ?: return null
        val lock = if (lan.requiresMulticastLock) transport.holdMulticastLock() else null
        return try {
            withTimeoutOrNull(bound) {
                transport
                    .candidates(lan)
                    .mapNotNull { candidate -> match(transport, lan, television, uuid, candidate) }
                    .firstOrNull()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Exception) {
            null
        } finally {
            lock?.close()
        }
    }

    /**
     * Confirms one candidate host. It is this session's television only when the device-info
     * document is a television whose UUID is exactly the identity the pairing saved and whose own
     * reported address is the candidate the transport answered for.
     */
    private suspend fun match(
        transport: DiscoveryTransport,
        lan: Lan,
        television: ConfirmedTelevision,
        uuid: String,
        candidate: Candidate,
    ): ConfirmedTelevision? {
        val info = deviceInfo(transport, lan, candidate) ?: return null
        val savedIdentity = info.isTelevision && info.uuid == uuid
        val sameRoute = info.reportedHost == null || info.reportedHost == candidate.host
        return if (savedIdentity && sameRoute) {
            // Only the address (and the television-supplied name) may change here. The adopted
            // channel, the security identity and every credential stay exactly as the pairing
            // saved them: rediscovery may never move a session from its adopted channel to another
            // one or relax the pin check (connection.md#security-identity).
            television.copy(
                host = candidate.host,
                uuid = info.uuid,
                displayName = info.name.takeIf { it.isNotBlank() } ?: television.displayName,
            )
        } else {
            null
        }
    }

    /** The bounded device-info read for one candidate host, or null when nothing usable answers. */
    private suspend fun deviceInfo(
        transport: DiscoveryTransport,
        lan: Lan,
        candidate: Candidate,
    ): DeviceInfo? =
        DiscoveryScan.DEVICE_INFO_PORTS.firstNotNullOfOrNull { port ->
            transport.deviceInfo(lan, candidate.host, port)
        }?.let(DeviceInfoParser::parse)

    companion object {
        /** connection.md: one internal rediscovery, 5 seconds, saved UUID only. */
        val REDISCOVERY_BOUND: Duration = 5.seconds
    }
}
