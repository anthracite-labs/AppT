package dev.anthracite.appt.samsung.internal

import android.content.Context
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.ForgetResult
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCapabilities
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.job

/**
 * The production [SamsungTvs] adapter.
 *
 * It is the single scan owner: at most one scan runs per instance. Starting a new collection of
 * [discover] cancels the previous scan and waits for it to release its probes and multicast lock
 * before the new one begins. The previous collector is cancelled (it sees a
 * `CancellationException`), never failed, and receives neither `Finished` nor `Failed`.
 *
 * It owns two private records per television, so the caller's opaque [TvId] is enough to reach and
 * remember a television without an address, port, MAC or protocol generation ever crossing the
 * seam:
 * * the in-memory [ConfirmedTelevisions] evidence the most recent scan produced, and
 * * the durable [SamsungSecretStore] — the Keystore-backed secret and the samsung-private device
 *   record that survive process death (docs/architecture/data.md).
 *
 * It also owns the per-television [SessionGeneration] order
 * (docs/architecture/connection.md#evidence-and-race-handling-harvest): a newer `open` or `forget`
 * invalidates older sessions' persistence and publication rights.
 */
internal class SamsungTvsImpl(
    private val newScan: () -> DiscoveryScan,
    private val confirmed: ConfirmedTelevisions = ConfirmedTelevisions(),
    private val secrets: SamsungSecretStore,
    private val diagnostics: SamsungDiagnosticRecorder = SamsungDiagnosticRecorder(),
    /**
     * One bounded internal rediscovery per reconnect budget (connection.md#supervised-reconnect).
     * Production wires the LAN probes through the same [DiscoveryTransport] the scan uses; tests
     * script it.
     */
    private val rediscovery: SessionRediscovery = SessionRediscovery.None,
    private val newSession:
        ((ConfirmedTelevision, CoroutineScope, SessionGeneration) -> RemoteSession)? =
        null,
) : SamsungTvs {
    private val activeScan = AtomicReference<Job?>(null)

    /** One monotonic generation counter per television. */
    private val generations = ConcurrentHashMap<TvId, AtomicLong>()

    override fun discover(): Flow<DiscoveryEvent> = channelFlow {
        val scan = coroutineContext.job
        activeScan.getAndSet(scan)?.cancelAndJoin()
        try {
            newScan().run { event -> send(event) }
        } finally {
            activeScan.compareAndSet(scan, null)
        }
    }

    override fun open(id: TvId, scope: CoroutineScope): RemoteSession {
        val television =
            confirmed.latest(id)
                ?: secrets.loadDevice(id)?.toTelevision(id)
                ?: return UnavailableSession(SessionState.Unreachable)
        if (!television.adoptedChannel) {
            return UnavailableSession(SessionState.Unsupported)
        }
        val generation = beginGeneration(id)
        return newSession?.invoke(television, scope, generation)
            ?: LiveSession(
                television,
                ProductionSessionTransport(),
                scope,
                secrets,
                generation,
                diagnostics = diagnostics,
                rediscovery = rediscovery,
                recordTelevision = { confirmed.record(it) },
            )
    }

    /**
     * The idempotent low-level forget (docs/architecture/samsung-interface.md#forget): removes this
     * phone's Samsung secret and samsung-private device record for [id] and nothing else. Room
     * rows, favourites and the management transaction belong to S09. In-flight sessions for this id
     * are invalidated first, so a late approval can neither publish `Ready` nor persist a pairing
     * for a television this phone just forgot.
     */
    override suspend fun forget(id: TvId): ForgetResult {
        invalidate(id)
        return try {
            secrets.forget(id)
            ForgetResult.Forgotten
        } catch (ignored: IOException) {
            ForgetResult.Failed
        }
    }

    /** The ids that have samsung-private records. Not a UI list and not an account concept. */
    override fun rememberedIds(): Set<TvId> = secrets.rememberedIds()

    override fun redactedDiagnostics() = diagnostics.report()

    /** The newest generation for [id], which invalidates every older session's effects. */
    private fun beginGeneration(id: TvId): SessionGeneration {
        val counter = generations.computeIfAbsent(id) { AtomicLong(0) }
        val mine = counter.incrementAndGet()
        return SessionGeneration { counter.get() == mine }
    }

    /** Makes every existing generation for [id] stale. Used by the destructive `forget`. */
    private fun invalidate(id: TvId) {
        generations.computeIfAbsent(id) { AtomicLong(0) }.incrementAndGet()
    }

    companion object {
        fun create(context: Context): SamsungTvs {
            val confirmed = ConfirmedTelevisions()
            val secrets = KeystoreSamsungStore.fromContext(context)
            return SamsungTvsImpl(
                newScan = { DiscoveryScan(AndroidDiscoveryTransport(context), confirmed, secrets) },
                confirmed = confirmed,
                secrets = secrets,
                rediscovery = LanSessionRediscovery { AndroidDiscoveryTransport(context) },
            )
        }
    }
}

/** Rebuilds the private control evidence from the durable samsung-private device record. */
private fun SamsungDeviceRecord.toTelevision(id: TvId): ConfirmedTelevision =
    ConfirmedTelevision(
        id = id,
        host = lastAddress,
        tls = tls,
        adoptedChannel = adoptedChannel,
        uuid = uuid,
        displayName = displayName,
    )

/**
 * A session that never opens a socket, for a television that cannot be controlled.
 *
 * This is not a stub standing in for a later feature. `Unsupported` and an unknown id are
 * documented `open` outcomes (samsung-interface.md#open), and both must reach the caller as a
 * session that reports the state and rejects every command rather than as an exception.
 */
internal class UnavailableSession(private val unavailable: SessionState) : RemoteSession {
    private val mutableSnapshot =
        MutableStateFlow(SessionSnapshot(unavailable, TvCapabilities(emptySet())))

    override val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun command(command: TvCommand): CommandResult =
        CommandResult.Rejected(TvFailure.Unavailable)

    override suspend fun retryApproval() = Unit

    override suspend fun confirmRepair() = Unit

    override fun close() = Unit
}
