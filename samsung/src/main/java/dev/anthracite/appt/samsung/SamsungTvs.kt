package dev.anthracite.appt.samsung

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * The external interface of the `samsung` module (docs/architecture/samsung-interface.md).
 *
 * S02 realizes [discover] only. S03 adds the first live control session: [open] plus
 * [RemoteSession] and the command path. The canonical contract also lists `wake`, `forget`,
 * `rememberedIds` and `redactedDiagnostics`; each arrives in the slice that makes it observable:
 * S04 secrets/forget, S05 bounded diagnostics, and S12 wake. These methods are live implementations,
 * not placeholder stubs.
 *
 * Two adapters cross this seam: the production `SamsungTvsImpl` (internal to this module, created
 * through [SamsungModule]) and the scripted fake that `app` tests use.
 */
interface SamsungTvs {
    /**
     * Starts one bounded scan and emits [DiscoveryEvent.Found] cards as televisions are confirmed,
     * then [DiscoveryEvent.Finished].
     * * Collecting the flow starts the scan; cancelling collection stops probes and releases the
     *   multicast lock.
     * * Only one scan runs. A new `discover()` cancels the previous scan; the previous flow is
     *   cancelled, not failed.
     * * The scan always runs to its bound (10 seconds) unless cancelled. Callers do not pass a
     *   duration.
     * * At most one `Found` per [TvId] per scan. Cards carry friendly names, never addresses.
     * * If local-network access is blocked, the flow emits `Failed(TvFailure.LocalNetworkDenied)`
     *   once and stops. It never launches permission UI.
     */
    fun discover(): Flow<DiscoveryEvent>

    /**
     * Opens one live control session for [id] and returns it immediately with a `Connecting`
     * snapshot; the snapshot is never null and never absent.
     * * Pass a `TvId` from `discover()` or `rememberedIds()`. The most recent confirmed control
     *   evidence for that television is private to this module, so the caller never supplies an
     *   address, a port, a MAC, or a protocol generation.
     * * Pass the caller's scope. Cancelling that scope, or calling [RemoteSession.close], releases
     *   the socket and stops the session. Closing is not forgetting and nothing is deleted.
     * * An `Unsupported` television yields a session that reports `Unsupported` and sends no
     *   functional command. An unknown id yields `Unreachable`. Neither opens a socket.
     * * A remembered television resumes: its saved security identity (the TLS pin, or the protocol
     *   UUID on the plaintext channel) is checked first, and only a match presents the saved token,
     *   so the session reaches `Ready` without another approval prompt. A changed identity is
     *   `NeedsRepair` with `RepairReason.IdentityChanged`, and the saved token never reaches that
     *   connection (docs/architecture/connection.md#security-identity).
     * * Saved material that cannot be decrypted surfaces as `NeedsRepair` without a repair reason
     *   (`TvFailure.SecretsUnavailable`): no fallback, no token, and the user can pair again.
     * * First contact sends no saved token: the television is asked to allow AppT, and the session
     *   moves to [SessionState.AwaitingTvApproval] until the user approves. Approval success
     *   persists the token and the security identity atomically for the next open.
     * * While [SessionState.Ready], [RemoteSession.command] writes on this already open session. It
     *   does not open a second socket.
     */
    fun open(id: TvId, scope: CoroutineScope): RemoteSession

    /**
     * Removes this phone's saved Samsung relationship for [id]: the pairing secret and the
     * samsung-private device record. Idempotent and safe to retry. It deletes nothing else — Room
     * rows, favourites, and the user-facing management transaction are not this module's.
     * * Returns [ForgetResult.Forgotten] when no Samsung material remains, including when there was
     *   none.
     * * Returns [ForgetResult.Failed] when deletion genuinely failed; the caller must retry before
     *   treating the television as forgotten.
     * * A session that is already open for this id is not closed by `forget` — releasing a session
     *   remains the holder's job — but a late approval from it can no longer publish `Ready` or
     *   persist pairing evidence
     *   (docs/architecture/connection.md#evidence-and-race-handling-harvest).
     */
    suspend fun forget(id: TvId): ForgetResult

    /**
     * The ids for which samsung-private records exist on this phone. `app` uses this at startup to
     * retry `forget` for ids it still tracks as pending removal; it is not a UI list and not an
     * account or cloud concept (docs/architecture/data.md).
     */
    fun rememberedIds(): Set<TvId>

    /** Already-redacted bounded local events; this API never returns identifiers or payloads. */
    fun redactedDiagnostics(): RedactedDiagnosticReport
}
