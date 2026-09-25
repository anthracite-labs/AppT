package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvFailure

/**
 * The minimal S03 remote (presentation.md#remote).
 *
 * S03 is the first live control surface, not the S05 full layout: a volume row and a directional
 * set, both driven by live capability evidence. Power, the favourite shelf, secondary controls, the
 * switch sheet and edit mode arrive with the slices that make them real.
 *
 * @property tvName the television's friendly name, blank when it reported none.
 * @property connection how the session presents (presentation.md's `ConnectionUi` table).
 * @property keys the keys the television currently accepts, in render order.
 */
data class RemoteUiState(
    val tvName: String,
    val connection: ConnectionUi,
    val keys: List<RemoteKey>,
) {
    companion object {
        val Initial: RemoteUiState =
            RemoteUiState(tvName = "", connection = ConnectionUi.Connecting, keys = emptyList())

        /**
         * Maps a live session snapshot onto the remote presentation. Pure, so it is a unit test.
         */
        fun of(tvName: String, session: SessionSnapshot?): RemoteUiState {
            val state = session?.state ?: SessionState.Connecting
            return RemoteUiState(
                tvName = tvName,
                connection = ConnectionUi.of(state, session?.repairReason),
                keys =
                    if (state == SessionState.Ready) {
                        session?.capabilities?.keys.orEmpty().sortedBy(RemoteKey::ordinal)
                    } else {
                        emptyList()
                    },
            )
        }
    }
}

/** How the session presents on the remote surface (presentation.md#remote's `ConnectionUi`). */
sealed interface ConnectionUi {
    /** Opening the session. Not an error; nothing to act on. */
    data object Connecting : ConnectionUi

    /** The television is asking whether to allow AppT. */
    data object WaitingForApproval : ConnectionUi

    /** Commands write immediately. */
    data object Ready : ConnectionUi

    /** User action is required, with the reason in ordinary language. */
    data class NeedsRepair(val failure: TvFailure) : ConnectionUi

    /** The television cannot be reached, or the session is gone. */
    data class Unavailable(val failure: TvFailure) : ConnectionUi

    /** No adopted control path. */
    data object Unsupported : ConnectionUi

    companion object {
        fun of(state: SessionState, reason: RepairReason?): ConnectionUi =
            when (state) {
                SessionState.Connecting -> Connecting
                SessionState.AwaitingTvApproval -> WaitingForApproval
                SessionState.Ready -> Ready
                SessionState.NeedsRepair ->
                    NeedsRepair(
                        when (reason) {
                            RepairReason.ApprovalTimedOut -> TvFailure.TimedOut
                            else -> TvFailure.NeedsRepair
                        }
                    )
                SessionState.Unreachable -> Unavailable(TvFailure.Unreachable)
                SessionState.Unsupported -> Unsupported
                SessionState.Closed -> Unavailable(TvFailure.Unavailable)
            }
    }
}

/** The outcome of one command on the remote surface. */
sealed interface CommandOutcome {
    /** The command frame was written to the live session. */
    data object Written : CommandOutcome

    /** The command was not written. */
    data class NotWritten(val failure: TvFailure) : CommandOutcome

    companion object {
        fun of(result: CommandResult): CommandOutcome =
            when (result) {
                CommandResult.Accepted -> Written
                is CommandResult.Rejected -> NotWritten(result.failure)
            }
    }
}

/**
 * The keys the minimal remote renders, in the order the layout shows them (presentation.md#remote:
 * "Every control comes from live capability evidence").
 *
 * Power is deliberately absent: this surface is a volume and directional set, and power control
 * arrives with the chrome zone that isolates it.
 */
val MINIMAL_REMOTE_KEYS: List<RemoteKey> =
    listOf(
        RemoteKey.VolumeUp,
        RemoteKey.VolumeDown,
        RemoteKey.Mute,
        RemoteKey.Up,
        RemoteKey.Left,
        RemoteKey.Enter,
        RemoteKey.Right,
        RemoteKey.Down,
        RemoteKey.Back,
        RemoteKey.Home,
    )
