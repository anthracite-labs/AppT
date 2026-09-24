package dev.anthracite.appt.testing

import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.gate.PermissionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scripted [PermissionGate]: behaves like the V1 gate and counts calls. */
class FakePermissionGate(initial: LocalNetworkPhase = LocalNetworkPhase.Explain) : PermissionGate {
    private val mutablePhase = MutableStateFlow(initial)
    override val phase: StateFlow<LocalNetworkPhase> = mutablePhase

    var acknowledgeCalls = 0
        private set

    var deniedCalls = 0
        private set

    override fun acknowledge() {
        acknowledgeCalls++
        mutablePhase.value = LocalNetworkPhase.Granted
    }

    override fun reportDenied() {
        deniedCalls++
        mutablePhase.value = LocalNetworkPhase.Denied
    }
}
