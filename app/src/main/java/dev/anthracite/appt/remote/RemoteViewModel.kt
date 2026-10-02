package dev.anthracite.appt.remote

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
        combine(activeRemoteHost.current, name, preferenceStore.interaction) { current, label, prefs
                ->
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
        beginCommandTrace(traceCookie)
        diagnostics?.recordApp(
            AppDiagnosticName.RemoteInteraction,
            (System.nanoTime() / NANOS_PER_MILLI).coerceAtLeast(0),
        )
        viewModelScope.launch {
            var traceEnded = false
            try {
                val held =
                    activeRemoteHost.current.value?.takeIf { it.tvId == tvId } ?: return@launch
                val result = held.session.command(command)
                endCommandTrace(traceCookie)
                traceEnded = true
                if (result == CommandResult.Accepted) preferenceStore.setFirstControlAchieved()
            } finally {
                if (!traceEnded) endCommandTrace(traceCookie)
            }
        }
    }

    /**
     * Tracing is optional instrumentation; a missing platform implementation must not block input.
     */
    private inline fun traceSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {} catch (_: LinkageError) {}
    }

    private fun beginCommandTrace(cookie: Int) = traceSafely {
        Trace.beginAsyncSection(COMMAND_LATENCY_TRACE, cookie)
    }

    private fun endCommandTrace(cookie: Int) = traceSafely {
        Trace.endAsyncSection(COMMAND_LATENCY_TRACE, cookie)
    }

    /**
     * Handles one Android hardware-volume event. The caller installs this only while Remote is
     * started; capability and preference gates are rechecked for every event. A repeat is consumed
     * without creating an artificial burst of television commands.
     */
    fun onHardwareVolumeKey(key: RemoteKey, action: Int, repeatCount: Int): Boolean {
        val current = activeRemoteHost.current.value?.takeIf { it.tvId == tvId } ?: return false
        val canControl =
            state.value.volumeButtonsControlTv &&
                current.snapshot.state == SessionState.Ready &&
                key in current.snapshot.capabilities.keys
        if (!canControl) return false
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
        const val NANOS_PER_MILLI = 1_000_000L
        const val COMMAND_LATENCY_TRACE = "commandLatencyBudget"
        val nextTraceCookie = AtomicInteger()
    }
}
