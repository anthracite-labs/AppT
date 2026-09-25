package dev.anthracite.appt.samsung

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * The external interface of the `samsung` module (docs/architecture/samsung-interface.md).
 *
 * S02 realizes [discover] only. S03 adds the first live control session: [open] plus [RemoteSession]
 * and the command path. The canonical contract also lists `wake`, `forget`, `rememberedIds` and
 * `redactedDiagnostics`; each arrives, unchanged in shape, in the slice that makes it observable
 * (S04 secrets and `forget`, S12 wake). Declaring them now would force either a stub that pretends to
 * work or an exception path no caller is allowed to rely on.
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
     *
     * * Pass a `TvId` from `discover()`. The most recent confirmed control evidence for that
     *   television is private to this module, so the caller never supplies an address, a port, a
     *   MAC, or a protocol generation.
     * * Pass the caller's scope. Cancelling that scope, or calling [RemoteSession.close], releases
     *   the socket and stops the session. S03 keeps no durable pairing material, so closing is not
     *   forgetting and nothing is deleted.
     * * An `Unsupported` television yields a session that reports `Unsupported` and sends no
     *   functional command. An unknown id yields `Unreachable`. Neither opens a socket.
     * * First contact sends no saved token: the television is asked to allow AppT, and the session
     *   moves to [SessionState.AwaitingTvApproval] until the user approves.
     * * While [SessionState.Ready], [RemoteSession.command] writes on this already open session. It
     *   does not open a second socket.
     */
    fun open(id: TvId, scope: CoroutineScope): RemoteSession
}
