package dev.anthracite.appt.samsung

// Caller-facing session, command and capability types, taken from the S03-relevant shapes in
// docs/architecture/samsung-interface.md. No caller-facing type contains an address, MAC, token,
// certificate, Wi-Fi name, key string, or raw payload.
//
// S03 deliberately carries only what the first live control session makes observable:
//   * SessionState.Reconnecting arrives with the supervised reconnect in S06.
//   * TvCapabilities.keys arrives here; pointer, textInput, apps and powerOn arrive with the
//     surfaces that make them real (S11, S12).
//   * TvCommand.Tap arrives here; Hold, pointer, text and app commands arrive with their slices.
//   * RepairReason.ApprovalDenied and ApprovalTimedOut arrive here; TokenRejected and
//     IdentityChanged arrive with S04's saved-secret comparison.
//   * LaunchableApp, WakeResult, ForgetResult and RedactedDiagnosticReport arrive with S04/S12/S13.

/**
 * What the caller can observe about one live session.
 *
 * @property state the session state; see [SessionState].
 * @property capabilities the live evidence for this television. Keys reflect the last evidence, so
 *   the UI can hide controls it already knows are dead instead of flashing a full remote.
 * @property repairReason non-null only while [state] is [SessionState.NeedsRepair].
 */
data class SessionSnapshot(
    val state: SessionState,
    val capabilities: TvCapabilities,
    val repairReason: RepairReason? = null,
)

/** The caller-visible session states S03 can reach. */
sealed interface SessionState {
    /** Opening the session. Not an error. */
    data object Connecting : SessionState

    /** The user must allow AppT on the television. The approval prompt is in progress. */
    data object AwaitingTvApproval : SessionState

    /** Commands write immediately on the open session. */
    data object Ready : SessionState

    /** User action is required. Branch on [SessionSnapshot.repairReason]. */
    data object NeedsRepair : SessionState

    /** Bounded recovery ended, or the television cannot be reached. Not a modal loop. */
    data object Unreachable : SessionState

    /** No adopted control path. No command controls. */
    data object Unsupported : SessionState

    /** The holder released the session. */
    data object Closed : SessionState
}

/** Why a session needs the user to act. */
enum class RepairReason {
    /** The user denied the approval prompt on the television. */
    ApprovalDenied,

    /** The approval prompt was not answered within the approval wait. */
    ApprovalTimedOut,
}

/**
 * Live capability evidence for one television. `app` renders only the keys present here and keeps
 * no parallel model or year table (docs/architecture/commands.md#evidence).
 */
data class TvCapabilities(val keys: Set<RemoteKey>)

/** The opaque remote keys the adopted remote channel accepts. Callers do not send wire strings. */
enum class RemoteKey {
    Up,
    Down,
    Left,
    Right,
    Enter,
    Back,
    Home,
    VolumeUp,
    VolumeDown,
    Mute,
    Power,
}

/** One typed command. S03 realizes the standard remote-channel tap only. */
sealed interface TvCommand {
    /** One press of [key] on the adopted remote channel. */
    data class Tap(val key: RemoteKey) : TvCommand
}

/** The outcome of one command. */
sealed interface CommandResult {
    /** The command frame was written to the live session. */
    data object Accepted : CommandResult

    /** The command was not written. The failure is a value, never a thrown exception. */
    data class Rejected(val failure: TvFailure) : CommandResult
}
