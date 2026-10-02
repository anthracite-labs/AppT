package dev.anthracite.appt.remote

import dev.anthracite.appt.preferences.InteractionPreferences
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvFailure

/** Immutable state for the phone-native Remote, composed from live TV evidence and preferences. */
data class RemoteUiState(
    val tvName: String,
    val connection: ConnectionUi,
    val keys: List<RemoteKey>,
    val pointerAvailable: Boolean = false,
    val navigationMode: NavigationMode = NavigationMode.Directional,
    val hapticsEnabled: Boolean = true,
    val volumeButtonsControlTv: Boolean = true,
    val powerOffAvailable: Boolean = false,
) {
    companion object {
        val Initial = RemoteUiState("", ConnectionUi.Connecting, emptyList())

        fun of(
            tvName: String,
            session: SessionSnapshot?,
            interaction: InteractionPreferences = InteractionPreferences(),
        ): RemoteUiState {
            val state = session?.state ?: SessionState.Connecting
            val capabilities = session?.capabilities
            val pointerAvailable = state == SessionState.Ready && capabilities?.pointer == true
            return RemoteUiState(
                tvName = tvName,
                connection = ConnectionUi.of(state, session?.repairReason),
                keys =
                    if (state == SessionState.Ready) {
                        capabilities?.keys.orEmpty().sortedBy(RemoteKey::ordinal)
                    } else {
                        emptyList()
                    },
                pointerAvailable = pointerAvailable,
                navigationMode =
                    if (pointerAvailable) interaction.navigationMode
                    else NavigationMode.Directional,
                hapticsEnabled = interaction.hapticsEnabled,
                volumeButtonsControlTv = interaction.volumeButtonsControlTv,
                powerOffAvailable =
                    state == SessionState.Ready &&
                        capabilities?.powerOff == true &&
                        RemoteKey.Power in capabilities.keys,
            )
        }
    }
}

/** How the session presents on the remote surface. */
sealed interface ConnectionUi {
    data object Connecting : ConnectionUi

    data object WaitingForApproval : ConnectionUi

    data object Ready : ConnectionUi

    data object Reconnecting : ConnectionUi

    data class NeedsRepair(val failure: TvFailure) : ConnectionUi

    data class Unavailable(val failure: TvFailure) : ConnectionUi

    data object Unsupported : ConnectionUi

    companion object {
        fun of(state: SessionState, reason: RepairReason?): ConnectionUi =
            when (state) {
                SessionState.Connecting -> Connecting
                SessionState.AwaitingTvApproval -> WaitingForApproval
                SessionState.Ready -> Ready
                SessionState.Reconnecting -> Reconnecting
                SessionState.NeedsRepair ->
                    NeedsRepair(
                        when (reason) {
                            null -> TvFailure.SecretsUnavailable
                            RepairReason.ApprovalDenied -> TvFailure.NeedsRepair
                            RepairReason.ApprovalTimedOut -> TvFailure.TimedOut
                            RepairReason.TokenRejected,
                            RepairReason.IdentityChanged -> TvFailure.IdentityChanged
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
    data object Written : CommandOutcome

    data class NotWritten(val failure: TvFailure) : CommandOutcome

    companion object {
        fun of(result: CommandResult): CommandOutcome =
            when (result) {
                CommandResult.Accepted -> Written
                is CommandResult.Rejected -> NotWritten(result.failure)
            }
    }
}

/** Product-priority order only; every item is still filtered through live [RemoteUiState.keys]. */
val REMOTE_PRIMARY_KEYS: List<RemoteKey> =
    listOf(
        RemoteKey.Power,
        RemoteKey.Up,
        RemoteKey.Down,
        RemoteKey.Left,
        RemoteKey.Right,
        RemoteKey.Enter,
        RemoteKey.Back,
        RemoteKey.Home,
        RemoteKey.VolumeUp,
        RemoteKey.VolumeDown,
        RemoteKey.Mute,
    )
