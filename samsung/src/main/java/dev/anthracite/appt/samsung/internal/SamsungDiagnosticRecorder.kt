package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.RedactedDiagnosticReport
import dev.anthracite.appt.samsung.RedactedEvent
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.TvFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Samsung source schema has typed values only; no command, address, name, credential, or payload.
 */
internal class SamsungDiagnosticRecorder(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val queue = Channel<RedactedEvent>(200, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutableEvents = MutableStateFlow<List<RedactedEvent>>(emptyList())

    init {
        scope.launch {
            for (event in queue) {
                mutableEvents.value = (mutableEvents.value + event).takeLast(MAX_EVENTS)
            }
        }
    }

    fun session(snapshot: SessionSnapshot) {
        val capabilities =
            buildList {
                    snapshot.capabilities.keys.forEach { add(it.name) }
                    if (snapshot.capabilities.pointer) add("pointer")
                    if (snapshot.capabilities.textInput) add("textInput")
                    if (snapshot.capabilities.apps) add("apps")
                    if (snapshot.capabilities.powerOff) add("powerOff")
                    if (
                        snapshot.capabilities.powerOn ==
                            dev.anthracite.appt.samsung.PowerOn.Attemptable
                    ) {
                        add("powerOn")
                    }
                }
                .sorted()
        val fields = buildMap {
            put("state", snapshot.state::class.simpleName.orEmpty())
            snapshot.repairReason?.let { put("repairReason", it.name) }
            if (capabilities.isNotEmpty()) put("capabilities", capabilities.joinToString(","))
        }
        offer("session_state", fields)
    }

    fun commandWritten() = offer("command_written", emptyMap())

    fun commandUnavailable() =
        offer(
            "command_unavailable",
            mapOf("failure" to TvFailure.Unavailable::class.simpleName.orEmpty()),
        )

    fun commandRejected() =
        offer("key_rejected", mapOf("failure" to TvFailure.Rejected::class.simpleName.orEmpty()))

    fun report(): RedactedDiagnosticReport = RedactedDiagnosticReport(mutableEvents.value)

    private fun offer(name: String, fields: Map<String, String>) {
        queue.trySend(
            RedactedEvent(
                elapsedMs = (System.nanoTime() / NANOS_PER_MILLI).coerceAtLeast(0),
                name = name,
                fields = fields,
            )
        )
    }

    private companion object {
        const val MAX_EVENTS = 200
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
