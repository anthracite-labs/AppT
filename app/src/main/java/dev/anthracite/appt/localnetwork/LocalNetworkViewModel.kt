package dev.anthracite.appt.localnetwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.AppSettingsLauncher
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.gate.PermissionGate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** presentation.md#localnetwork: `LocalNetworkUiState(phase, canOpenAppSettings)`. */
data class LocalNetworkUiState(val phase: LocalNetworkPhase, val canOpenAppSettings: Boolean) {
    companion object {
        fun of(phase: LocalNetworkPhase): LocalNetworkUiState =
            LocalNetworkUiState(phase, canOpenAppSettings = phase == LocalNetworkPhase.Denied)
    }
}

/** Observes the [PermissionGate]; `Granted` advances to Discovery (done by the route). */
class LocalNetworkViewModel(
    private val gate: PermissionGate,
    private val appSettings: AppSettingsLauncher,
) : ViewModel() {

    val state: StateFlow<LocalNetworkUiState> =
        gate.phase
            .map(LocalNetworkUiState::of)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                LocalNetworkUiState.of(gate.phase.value),
            )

    fun onContinue() = gate.acknowledge()

    fun onRetry() = gate.acknowledge()

    fun onOpenSettings() = appSettings.open()
}
