package dev.anthracite.appt.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pairing for one television (presentation.md#pairing).
 *
 * The ViewModel owns no socket: it observes [ActiveRemoteHost], which is where the session lives, so
 * Pairing and Remote read the same session and a configuration change re-attaches to it rather than
 * opening a second one. `SamsungTvs.open` is never called from here.
 */
class PairingViewModel(
    private val tvId: TvId,
    private val activeRemoteHost: ActiveRemoteHost,
    private val tvProfiles: TvProfiles,
) : ViewModel() {

    private val name = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { name.value = tvProfiles.find(tvId)?.friendlyName }
    }

    val state: StateFlow<PairingUiState> =
        combine(activeRemoteHost.current, name) { current, label ->
            val held = current?.takeIf { it.tvId == tvId }
            PairingUiState.of(label.orEmpty(), held?.session)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT),
                initialValue = PairingUiState.Initial,
            )

    /**
     * Asks the television for approval again, from a repair phase only. The session ignores it
     * anywhere else, so this needs no local phase bookkeeping.
     */
    fun onRetryApproval() {
        viewModelScope.launch { activeRemoteHost.current.value?.session?.retryApproval() }
    }

    private companion object {
        /** presentation.md: one `StateFlow<UiState>` per route, shared for five seconds. */
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}
