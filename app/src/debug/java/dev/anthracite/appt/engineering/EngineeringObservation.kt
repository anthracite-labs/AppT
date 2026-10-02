package dev.anthracite.appt.engineering

import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Counts what the normal, application-scoped Active Remote did while the verifier could observe it.
 *
 * This never owns a session: it only reads `ActiveRemoteHost.current` and counts three bounded
 * transitions. The counts are the numbers on the report; there is no identifier, address or state
 * text in the model, so nothing sensitive can be observed "for" a scenario. Counts live for the
 * process, so a configuration change or a quick trip to another screen does not reset them.
 */
internal object EngineeringObservationSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableCounters = MutableStateFlow(EngineeringCounters())
    private var started = false

    val counters: StateFlow<EngineeringCounters> = mutableCounters.asStateFlow()

    /** Starts the one observation when the verifier is first composed; later calls are no-ops. */
    fun ensureStarted(host: ActiveRemoteHost) {
        if (started) return
        started = true
        scope.launch {
            var lastSession: RemoteSession? = null
            var lastState: SessionState? = null
            var sessions = 0
            var ready = 0
            var reconnections = 0
            host.current.collect { active ->
                val session = active?.session
                if (session != null && session !== lastSession) sessions++
                lastSession = session
                val state = active?.snapshot?.state
                val enteredReady = state == SessionState.Ready && lastState != SessionState.Ready
                val enteredReconnecting =
                    state == SessionState.Reconnecting && lastState != SessionState.Reconnecting
                if (enteredReady) ready++
                if (enteredReconnecting) reconnections++
                lastState = state
                mutableCounters.value =
                    EngineeringCounters(
                        sessionsObserved = sessions,
                        readyTransitionsObserved = ready,
                        reconnectionsObserved = reconnections,
                    )
            }
        }
    }
}
