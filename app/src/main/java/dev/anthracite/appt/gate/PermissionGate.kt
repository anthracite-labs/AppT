package dev.anthracite.appt.gate

import kotlinx.coroutines.flow.StateFlow

/**
 * Local-network permission gate phases (docs/architecture/discovery.md#permission-gate).
 * * [Explain]: the explanation must be shown before the first scan.
 * * [Requesting]: a system prompt is on screen. Only used when a chosen API actually shows one;
 *   the V1 probes at targetSdk 36 never do, so the V1 gate never enters it.
 * * [Granted]: discovery may start.
 * * [Denied]: a scan observed that local-network access is blocked; the explanation returns with
 *   a way to retry.
 */
enum class LocalNetworkPhase {
    Explain,
    Requesting,
    Granted,
    Denied,
}

/**
 * The one gate in `app` that owns local-network permission policy. `samsung` never launches
 * permission UI; it reports a blocked network as `Failed(LocalNetworkDenied)` and the caller tells
 * the gate through [reportDenied].
 */
interface PermissionGate {
    val phase: StateFlow<LocalNetworkPhase>

    /** The user chose Continue (or Try again) on the explanation. */
    fun acknowledge()

    /** A scan observed that local-network access is blocked. */
    fun reportDenied()
}
