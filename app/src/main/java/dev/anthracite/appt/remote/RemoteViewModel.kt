package dev.anthracite.appt.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Remote for one television (presentation.md#remote).
 *
 * The ViewModel owns no socket: it observes [ActiveRemoteHost], which Pairing already entered, so
 * the handoff reuses the session instead of opening a second one. `SamsungTvs.open` is never called
 * from here, and a configuration change re-attaches rather than re-opening.
 */
class RemoteViewModel(
    private val tvId: TvId,
    private val activeRemoteHost: ActiveRemoteHost,
    private val tvProfiles: TvProfiles,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    private val name = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { name.value = tvProfiles.find(tvId)?.friendlyName }
    }

    val state: StateFlow<RemoteUiState> =
        combine(activeRemoteHost.current, name) { current, label ->
            val held = current?.takeIf { it.tvId == tvId }
            RemoteUiState.of(label.orEmpty(), held?.snapshot)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT),
                initialValue = RemoteUiState.Initial,
            )

    /**
     * Sends one command on the retained session.
     *
     * A command is only offered while the session is `Ready`, so a write that the session accepts is
     * the first-control event: `firstControlAchieved` is set here, once, and setting it never
     * interrupts the session and never shows account UI (data.md, sync.md#remote-entry-gate).
     */
    fun onCommand(command: TvCommand) {
        viewModelScope.launch {
            val held = activeRemoteHost.current.value?.takeIf { it.tvId == tvId } ?: return@launch
            if (held.session.command(command) is CommandResult.Accepted) {
                preferenceStore.setFirstControlAchieved()
            }
        }
    }

    /** Re-enters the television after an unavailable state. */
    fun onRetry() {
        activeRemoteHost.enter(tvId)
    }

    private companion object {
        /** presentation.md: one `StateFlow<UiState>` per route, shared for five seconds. */
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}
