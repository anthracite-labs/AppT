package dev.anthracite.appt.samsung

import kotlinx.coroutines.flow.Flow

/**
 * The external interface of the `samsung` module (docs/architecture/samsung-interface.md).
 *
 * S02 realizes [discover] only. The canonical contract also lists `open`, `wake`, `forget`,
 * `rememberedIds` and `redactedDiagnostics`; each arrives, unchanged in shape, in the slice that
 * makes it observable (S03 session and commands, S04 secrets and `forget`, S12 wake). Declaring
 * them now would force either a stub that pretends to work or an exception path no caller is
 * allowed to rely on.
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
}
