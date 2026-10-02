package dev.anthracite.appt.samsung

// Caller-facing session, command and capability types, taken from the S03-relevant shapes in
// docs/architecture/samsung-interface.md. No caller-facing type contains an address, MAC, token,
// certificate, Wi-Fi name, key string, or raw payload.
//
// Caller-visible types arrive with the slice that makes them observable:
//   * SessionState.Reconnecting arrives with supervised reconnect in S06.
//   * TvCapabilities.keys and pointer/power flags support S05 live-evidence rendering; production
//     pointer remains false until an accepted exact fixture establishes the wire behavior.
// Text/apps
//     become live with their later surfaces.
//   * TvCommand.Tap is the adopted control channel. Pointer, text and app commands remain typed but
//     unavailable until their accepted protocol behavior exists.
//   * RepairReason.TokenRejected and IdentityChanged preserve S04's saved-identity handling.
//   * RedactedDiagnosticReport is S05's bounded Samsung-local report; LaunchableApp/WakeResult
//     remain with S12.

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

    /** The adopted session is being recovered without showing a modal interruption. */
    data object Reconnecting : SessionState

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

    /**
     * The television refused the saved token after it was legitimately sent to a matching security
     * identity. `retryApproval` never fixes this; only the confirmed re-pair does.
     */
    TokenRejected,

    /**
     * The television's persistent security identity changed since this phone saved it. The saved
     * token was withheld, so it never reached whatever answered. Only the confirmed re-pair
     * replaces the saved identity (docs/architecture/connection.md#security-identity).
     */
    IdentityChanged,
}

/**
 * The outcome of the idempotent forget primitive (docs/architecture/samsung-interface.md#forget).
 */
sealed interface ForgetResult {
    /** No Samsung secret or private record remains for this id, or none ever did. */
    data object Forgotten : ForgetResult

    /** Deletion failed; the caller must retry before treating the television as forgotten. */
    data object Failed : ForgetResult
}

/**
 * Live capability evidence for one television. `app` renders only the keys present here and keeps
 * no parallel model or year table (docs/architecture/commands.md#evidence).
 */
data class RedactedDiagnosticReport(val events: List<RedactedEvent>)

data class RedactedEvent(val elapsedMs: Long, val name: String, val fields: Map<String, String>)

data class TvCapabilities(
    val keys: Set<RemoteKey>,
    val pointer: Boolean = false,
    val textInput: Boolean = false,
    val apps: Boolean = false,
    val powerOn: PowerOn = PowerOn.Unavailable,
    val powerOff: Boolean = RemoteKey.Power in keys,
)

enum class PowerOn {
    Attemptable,
    Unavailable,
}

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

    /**
     * Typed pointer movement. Production remains unavailable until an accepted wire fixture exists.
     */
    data class PointerMove(val dx: Int, val dy: Int) : TvCommand

    /**
     * Typed pointer click. Production remains unavailable until an accepted wire fixture exists.
     */
    data object PointerClick : TvCommand
}

/** The outcome of one command. */
sealed interface CommandResult {
    /** The command frame was written to the live session. */
    data object Accepted : CommandResult

    /** The command was not written. The failure is a value, never a thrown exception. */
    data class Rejected(val failure: TvFailure) : CommandResult
}
