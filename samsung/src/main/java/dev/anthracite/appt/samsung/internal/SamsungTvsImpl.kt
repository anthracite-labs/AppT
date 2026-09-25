package dev.anthracite.appt.samsung.internal

import android.content.Context
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCapabilities
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
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
 * before the new one begins. The previous collector is cancelled (it sees a `CancellationException`),
 * never failed, and receives neither `Finished` nor `Failed`.
 *
 * It is also the owner of the private, in-memory record of the most recent confirmed control evidence
 * per television: [DiscoveryScan] writes it when a candidate is confirmed and [open] reads it, so the
 * caller's opaque [TvId] is enough to reach the selected television without an address, port, MAC or
 * protocol generation ever crossing the seam.
 */
internal class SamsungTvsImpl(
    private val newScan: () -> DiscoveryScan,
    private val confirmed: ConfirmedTelevisions = ConfirmedTelevisions(),
    private val newSession: (ConfirmedTelevision, CoroutineScope) -> RemoteSession =
        { television, scope -> LiveSession(television, OkHttpSessionTransport(), scope) },
) : SamsungTvs {
    private val activeScan = AtomicReference<Job?>(null)

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
        val television = confirmed.latest(id) ?: return UnavailableSession(SessionState.Unreachable)
        if (!television.adoptedChannel) {
            return UnavailableSession(SessionState.Unsupported)
        }
        return newSession(television, scope)
    }

    companion object {
        fun create(context: Context): SamsungTvs {
            val confirmed = ConfirmedTelevisions()
            return SamsungTvsImpl(
                newScan = { DiscoveryScan(AndroidDiscoveryTransport(context), confirmed) },
                confirmed = confirmed,
            )
        }
    }
}

/**
 * A session that never opens a socket, for a television that cannot be controlled.
 *
 * This is not a stub standing in for a later feature. `Unsupported` and an unknown id are documented
 * `open` outcomes (samsung-interface.md#open), and both must reach the caller as a session that
 * reports the state and rejects every command rather than as an exception.
 */
internal class UnavailableSession(private val unavailable: SessionState) : RemoteSession {
    private val mutableSnapshot =
        MutableStateFlow(SessionSnapshot(unavailable, TvCapabilities(emptySet())))

    override val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun command(command: TvCommand): CommandResult =
        CommandResult.Rejected(TvFailure.Unavailable)

    override suspend fun retryApproval() = Unit

    override fun close() = Unit
}
