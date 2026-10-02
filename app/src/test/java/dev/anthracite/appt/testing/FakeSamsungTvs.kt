package dev.anthracite.appt.testing

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.ForgetResult
import dev.anthracite.appt.samsung.RedactedDiagnosticReport
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCapabilities
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * The scripted [SamsungTvs] adapter for `app` tests (samsung-interface.md: `app` tests cross the
 * seam with a fake). Each collection of [discover] is one [Scan] whose events the test sends, and
 * each [open] is one [Session] whose snapshot the test drives.
 *
 * Two adapters cross the seam, so this is a real seam rather than an implementation detail
 * (modules.md#internal-seams-inside-samsung). The fake holds no socket: it exists so the caller's
 * behaviour is tested without a television.
 */
class FakeSamsungTvs : SamsungTvs {
    class Scan {
        internal val events = Channel<DiscoveryEvent>(Channel.UNLIMITED)
        var cancelled = false
            internal set

        var completed = false
            internal set

        fun send(event: DiscoveryEvent) {
            events.trySend(event)
        }
    }

    /** One scripted live session. The test publishes its state and scripts its command results. */
    class Session : RemoteSession {
        private val mutableSnapshot =
            MutableStateFlow(SessionSnapshot(SessionState.Connecting, TvCapabilities(emptySet())))

        override val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

        /** Every command sent, in order. */
        val commands = mutableListOf<TvCommand>()

        var retryApprovals = 0
            private set

        /** How many times the confirmed re-pair was requested on this session. */
        var confirmRepairs = 0
            private set

        var closed = false
            private set

        /** What the next [command] returns. A real session writes on its retained socket. */
        var nextResult: CommandResult = CommandResult.Rejected(TvFailure.Unavailable)

        fun publish(
            state: SessionState,
            keys: Set<RemoteKey> = emptySet(),
            repairReason: RepairReason? = null,
            pointer: Boolean = false,
        ) {
            mutableSnapshot.value =
                SessionSnapshot(state, TvCapabilities(keys, pointer = pointer), repairReason)
        }

        /** Publishes `Ready` with the standard remote keys, as a real session does on approval. */
        fun ready(keys: Set<RemoteKey> = RemoteKey.entries.toSet(), pointer: Boolean = false) {
            publish(SessionState.Ready, keys, pointer = pointer)
        }

        override suspend fun command(command: TvCommand): CommandResult {
            commands += command
            val current = mutableSnapshot.value
            val result =
                if (current.state == SessionState.Ready) nextResult
                else CommandResult.Rejected(TvFailure.Unavailable)
            if (command is TvCommand.Tap) {
                when (result) {
                    is CommandResult.Rejected ->
                        if (result.failure == TvFailure.Rejected) {
                            mutableSnapshot.value =
                                current.copy(
                                    capabilities =
                                        current.capabilities.copy(
                                            keys = current.capabilities.keys - command.key
                                        )
                                )
                        }
                    CommandResult.Accepted -> Unit
                }
            }
            return result
        }

        override suspend fun retryApproval() {
            retryApprovals++
        }

        override suspend fun confirmRepair() {
            confirmRepairs++
        }

        override fun close() {
            closed = true
        }
    }

    val scans = mutableListOf<Scan>()

    private val sessions = mutableListOf<Session>()

    /** The ids the seam reports as remembered; tests prime it as the durable store would. */
    private val remembered = mutableSetOf<TvId>()

    /** The result the next [forget] returns; resets to [ForgetResult.Forgotten] after each call. */
    var nextForgetResult: ForgetResult = ForgetResult.Forgotten

    /** Every television [forget] was asked for, in order, including repeats. */
    val forgottenIds = mutableListOf<TvId>()

    /** Every television [open] was asked for, in order. */
    val openedIds = mutableListOf<TvId>()

    /** Collections started, i.e. scans started. */
    val discoverCalls: Int
        get() = scans.size

    val latest: Scan
        get() = scans.last()

    /**
     * The most recent session [open] returned for [tvId], or null when it was never opened.
     *
     * Most recent, not first: a session that ended is opened again for the same television, and a
     * test driving the live session has to reach the replacement rather than the one that died.
     */
    fun sessionFor(tvId: TvId): Session? =
        openedIds.lastIndexOf(tvId).takeIf { it >= 0 }?.let { sessions[it] }

    override fun discover(): Flow<DiscoveryEvent> = flow {
        val scan = Scan()
        scans += scan
        try {
            for (event in scan.events) {
                emit(event)
                if (event == DiscoveryEvent.Finished || event is DiscoveryEvent.Failed) break
            }
            scan.completed = true
        } finally {
            if (!scan.completed) scan.cancelled = true
        }
    }

    override fun open(id: TvId, scope: CoroutineScope): RemoteSession {
        openedIds += id
        val session = Session()
        sessions += session
        return session
    }

    override suspend fun forget(id: TvId): ForgetResult {
        forgottenIds += id
        val result = nextForgetResult
        if (result == ForgetResult.Forgotten) {
            remembered.remove(id)
        }
        nextForgetResult = ForgetResult.Forgotten
        return result
    }

    override fun rememberedIds(): Set<TvId> = remembered.toSet()

    override fun redactedDiagnostics(): RedactedDiagnosticReport =
        RedactedDiagnosticReport(emptyList())

    /** Primes the remembered set, as the durable store would report it after a pairing. */
    fun remember(ids: Set<TvId>) {
        remembered += ids
    }

    companion object {
        const val LIVING_ROOM_ID = "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"
        const val OLDER_ID = "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

        fun found(
            id: String = LIVING_ROOM_ID,
            name: String = "Living Room TV",
            availability: ControlAvailability = ControlAvailability.NeedsPairing,
            remembered: Boolean = false,
        ): DiscoveryEvent.Found =
            DiscoveryEvent.Found(
                DiscoveredTv(
                    id = TvId(id),
                    name = name,
                    remembered = remembered,
                    stableIdentity = true,
                    availability = availability,
                )
            )
    }
}
