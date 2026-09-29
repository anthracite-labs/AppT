package dev.anthracite.appt.samsung

import kotlinx.coroutines.flow.StateFlow

/**
 * One live control session for one television (docs/architecture/samsung-interface.md).
 *
 * The holder owns it: `ActiveRemoteHost` in `app` opens at most one session per television and
 * screens observe the same retained session, so Pairing and Remote never open a second socket
 * between them. A screen or ViewModel never calls `SamsungTvs.open` directly
 * (docs/architecture/lifecycle.md).
 */
interface RemoteSession {
    /**
     * The observable session state. The initial value is [SessionSnapshot] in
     * [SessionState.Connecting]; it is never null and updates without the caller polling.
     */
    val snapshot: StateFlow<SessionSnapshot>

    /**
     * Sends one typed command on the already open session.
     * * Returns [CommandResult.Accepted] when the command frame has been written to the live
     *   session, not when the television has visibly acted.
     * * Returns [CommandResult.Rejected] for expected failures, including any command sent while
     *   the session is not [SessionState.Ready]. Nothing is queued for later replay.
     * * Throws `CancellationException` only when the calling coroutine is cancelled. Malformed
     *   television traffic never reaches the caller as an exception.
     * * Never opens a socket and never contacts a cloud participant.
     */
    suspend fun command(command: TvCommand): CommandResult

    /**
     * Asks the television for approval again. Applies only from [SessionState.NeedsRepair] with a
     * retryable [RepairReason] (the user denied, or the prompt timed out); elsewhere the snapshot
     * is unchanged. It does not throw.
     *
     * The re-pair path for a rejected token or a changed security identity is [confirmRepair].
     */
    suspend fun retryApproval()

    /**
     * The explicit re-pair. Applies only from [SessionState.NeedsRepair] with
     * [RepairReason.TokenRejected] or [RepairReason.IdentityChanged]: the saved approval material
     * is discarded first, then the session pairs as new television contact — no saved token, no
     * saved pin. Anywhere else the snapshot is unchanged; a healthy session is never dropped by
     * this call. It does not throw.
     *
     * The caller owes the confirmation: `app` must not call this without an explicit user
     * confirmation, because it replaces a saved security relationship
     * (docs/architecture/samsung-interface.md#retryapproval-and-confirmrepair). There is no way to
     * reach this effect from `app` without it, and no "ignore security errors" alternative exists.
     */
    suspend fun confirmRepair()

    /**
     * Releases the socket and discards transient first-contact material. Idempotent. This is not
     * `forget`: S03 persists no pairing material, so there is nothing to delete.
     */
    fun close()
}
