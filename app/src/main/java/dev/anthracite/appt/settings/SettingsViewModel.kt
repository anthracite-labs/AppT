package dev.anthracite.appt.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.preferences.InteractionPreferences
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.samsung.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One Settings route state, composed by the ViewModel from device-local preferences and TV evidence. */
data class SettingsUiState(
    val interaction: InteractionPreferences,
    val pointerAvailable: Boolean,
    val appVersion: String,
) {
    val effectiveNavigationMode: NavigationMode
        get() = if (pointerAvailable) interaction.navigationMode else NavigationMode.Directional
}

class SettingsViewModel(
    private val preferenceStore: PreferenceStore,
    activeRemoteHost: ActiveRemoteHost,
    appVersion: String,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(preferenceStore.interaction, activeRemoteHost.current) { interaction, remote ->
                SettingsUiState(
                    interaction = interaction,
                    pointerAvailable =
                        remote?.snapshot?.let {
                            it.state == SessionState.Ready && it.capabilities.pointer
                        } == true,
                    appVersion = appVersion,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SettingsUiState(
                    InteractionPreferences(),
                    pointerAvailable = false,
                    appVersion = appVersion,
                ),
            )

    fun onHaptics(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.setHapticsEnabled(enabled) }
    }

    fun onPhoneVolumeButtons(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.setVolumeButtonsControlTv(enabled) }
    }

    fun onNavigationMode(mode: NavigationMode) {
        if (mode == NavigationMode.Pointer && !state.value.pointerAvailable) return
        viewModelScope.launch { preferenceStore.setNavigationMode(mode) }
    }
}
