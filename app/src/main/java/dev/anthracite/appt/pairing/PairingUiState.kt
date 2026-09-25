package dev.anthracite.appt.pairing

import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvFailure

/**
 * Pairing is one focused state for one television, never a spinner inside the discovery list
 * (presentation.md#pairing).
 *
 * @property tvName the television's friendly name from the profile row this phone just wrote; blank
 *   when the television reported none, in which case the screen shows its own localized label.
 * @property phase what the session is doing, in ordinary language.
 * @property recallHintVisible true while the approval prompt is in progress, where a short hint
 *   about what to look for on the television helps.
 */
data class PairingUiState(
    val tvName: String,
    val phase: PairingPhase,
    val recallHintVisible: Boolean,
) {
    companion object {
        val Initial: PairingUiState =
            PairingUiState(tvName = "", phase = PairingPhase.Connecting, recallHintVisible = false)

        /**
         * Maps a live session snapshot onto the pairing presentation. Pure, so it is a unit test.
         */
        fun of(tvName: String, session: SessionSnapshot?): PairingUiState {
            val state = session?.state ?: SessionState.Connecting
            val phase =
                when (state) {
                    SessionState.Connecting -> PairingPhase.Connecting
                    SessionState.AwaitingTvApproval -> PairingPhase.WaitingForApproval
                    SessionState.Ready -> PairingPhase.Succeeded
                    else -> PairingPhase.Failed(state.failure())
                }
            return PairingUiState(
                tvName = tvName,
                phase = phase,
                recallHintVisible = phase is PairingPhase.WaitingForApproval,
            )
        }

        private fun SessionState.failure(): TvFailure =
            when (this) {
                SessionState.NeedsRepair -> TvFailure.NeedsRepair
                SessionState.Unreachable -> TvFailure.Unreachable
                SessionState.Unsupported -> TvFailure.Unsupported
                // Closed is not a pairing outcome the user can act on; the route has already left.
                else -> TvFailure.Unavailable
            }
    }
}

/** What Pairing is doing, in the caller's language rather than the session's. */
sealed interface PairingPhase {
    /** The session is opening. Not an error. */
    data object Connecting : PairingPhase

    /** The television is asking whether to allow AppT. */
    data object WaitingForApproval : PairingPhase

    /** The session is `Ready`; the route hands off to Remote. */
    data object Succeeded : PairingPhase

    /** The session ended without control, with the reason in ordinary language. */
    data class Failed(val failure: TvFailure) : PairingPhase
}
