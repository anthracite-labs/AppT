package dev.anthracite.appt.remote

import android.os.SystemClock
import android.os.Trace
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.diagnostics.AppDiagnosticName
import dev.anthracite.appt.diagnostics.LocalDiagnostics
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Remote for one television; the application-scoped host remains the sole session owner. */
class RemoteViewModel(
    private val tvId: TvId,
    private val activeRemoteHost: ActiveRemoteHost,
    private val tvProfiles: TvProfiles,
    private val preferenceStore: PreferenceStore,
    private val diagnostics: LocalDiagnostics? = null,
) : ViewModel() {
    private val name = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { name.value = tvProfiles.find(tvId)?.friendlyName }
    }

    val state: StateFlow<RemoteUiState> =
        combine(activeRemoteHost.current, name, preferenceStore.interaction) { current, label, prefs ->
                val held = current?.takeIf { it.tvId == tvId }
                RemoteUiState.of(label.orEmpty(), held?.snapshot, prefs)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT),
                initialValue = RemoteUiState.Initial,
            )

    /** Sends a typed command on the already-retained session. */
    fun onCommand(command: TvCommand) {
        val traceCookie = nextTraceCookie.incrementAndGet()
        Trace.beginAsyncSection(COMMAND_LATENCY_TRACE, traceCookie)
        diagnostics?.recordApp(AppDiagnosticName.RemoteInteraction, SystemClock.elapsedRealtime())
        viewModelScope.launch {
            var traceEnded = false
            try {
                val held = activeRemoteHost.current.value?.takeIf { it.tvId == tvId } ?: return@launch
                val result = held.session.command(command)
                Trace.endAsyncSection(COMMAND_LATENCY_TRACE, traceCookie)
                traceEnded = true
                if (result == CommandResult.Accepted) preferenceStore.setFirstControlAchieved()
            } finally {
                if (!traceEnded) Trace.endAsyncSection(COMMAND_LATENCY_TRACE, traceCookie)
            }
        }
    }

    /**
     * Handles one Android hardware-volume event. The caller installs this only while Remote is
     * started; capability and preference gates are rechecked for every event. A repeat is consumed
     * without creating an artificial burst of television commands.
     */
    fun onHardwareVolumeKey(key: RemoteKey, action: Int, repeatCount: Int): Boolean {
        val current = activeRemoteHost.current.value?.takeIf { it.tvId == tvId } ?: return false
        if (!state.value.volumeButtonsControlTv) return false
        if (current.snapshot.state != SessionState.Ready || key !in current.snapshot.capabilities.keys) {
            return false
        }
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) onCommand(TvCommand.Tap(key))
        return true
    }

    fun onToggleNavigationMode() {
        val current = state.value
        if (!current.pointerAvailable) return
        val next =
            if (current.navigationMode == NavigationMode.Directional) NavigationMode.Pointer
            else NavigationMode.Directional
        viewModelScope.launch { preferenceStore.setNavigationMode(next) }
    }

    fun onRetry() = activeRemoteHost.enter(tvId)

    /** This is reached only from the explicit re-pair confirmation on Remote. */
    fun onConfirmRepair() {
        viewModelScope.launch { activeRemoteHost.confirmedPairAgain(tvId) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
        const val COMMAND_LATENCY_TRACE = "commandLatencyBudget"
        val nextTraceCookie = AtomicInteger()
    }
}
