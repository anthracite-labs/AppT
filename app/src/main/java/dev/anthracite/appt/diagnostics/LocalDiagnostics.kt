package dev.anthracite.appt.diagnostics

import dev.anthracite.appt.samsung.SessionSnapshot
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fixed event names: no producer can submit free text or a command payload. */
enum class AppDiagnosticName {
    RemoteInteraction,
    SettingsOpened,
}

private enum class RecordSource(val wireName: String) {
    APP("app"),
    SAMSUNG("samsung"),
}

/** In-memory representation is itself restricted to the accepted event schema. */
@ConsistentCopyVisibility
data class LocalDiagnosticEvent
internal constructor(
    val elapsedMs: Long,
    val source: String,
    val name: String,
    val fields: Map<String, String>,
)

/**
 * Bounded local recorder. Producers do a non-suspending channel offer only; one IO consumer owns
 * both rolling rings and the atomically replaced file.
 */
class LocalDiagnostics(
    private val directory: File,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queue =
        Channel<LocalDiagnosticEvent>(
            capacity = MAX_EVENTS,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
    private val mutableAppEvents = MutableStateFlow<List<LocalDiagnosticEvent>>(emptyList())
    private val mutableSamsungEvents = MutableStateFlow<List<LocalDiagnosticEvent>>(emptyList())
    private val mutableDurableEvents = MutableStateFlow<List<LocalDiagnosticEvent>>(emptyList())

    val appEvents: StateFlow<List<LocalDiagnosticEvent>> = mutableAppEvents.asStateFlow()
    val samsungEvents: StateFlow<List<LocalDiagnosticEvent>> = mutableSamsungEvents.asStateFlow()
    val durableEvents: StateFlow<List<LocalDiagnosticEvent>> = mutableDurableEvents.asStateFlow()

    init {
        scope.launch(dispatcher) {
            for (queuedEvent in queue) {
                val event = redact(queuedEvent)
                when (event.source) {
                    RecordSource.APP.wireName ->
                        mutableAppEvents.value = boundedAppend(mutableAppEvents.value, event)
                    RecordSource.SAMSUNG.wireName ->
                        mutableSamsungEvents.value =
                            boundedAppend(mutableSamsungEvents.value, event)
                }
                mutableDurableEvents.value =
                    fitDurable(boundedAppend(mutableDurableEvents.value, event))
                if (replaceFile(mutableDurableEvents.value).isFailure) continue
            }
        }
    }

    /** Synchronous, non-suspending offer. The allowlisted schema has no free-text argument. */
    fun recordApp(name: AppDiagnosticName, elapsedMs: Long): Boolean =
        queue
            .trySend(
                LocalDiagnosticEvent(
                    elapsedMs = elapsedMs.coerceAtLeast(0),
                    source = RecordSource.APP.wireName,
                    name = name.name,
                    fields = emptyMap(),
                )
            )
            .isSuccess

    /** Records only typed session enums and capability names, never identifiers or payloads. */
    fun recordSamsung(snapshot: SessionSnapshot, elapsedMs: Long): Boolean {
        val capabilities =
            buildList {
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
                    if (snapshot.capabilities.keys.isNotEmpty()) add("keys")
                }
                .sorted()
        val fields = buildMap {
            put("state", snapshot.state::class.simpleName.orEmpty())
            snapshot.repairReason?.let { put("repairReason", it.name) }
            put("capabilities", capabilities.joinToString(","))
        }
        return queue
            .trySend(
                LocalDiagnosticEvent(
                    elapsedMs = elapsedMs.coerceAtLeast(0),
                    source = RecordSource.SAMSUNG.wireName,
                    name = "SessionSnapshot",
                    fields = fields,
                )
            )
            .isSuccess
    }

    /**
     * Redaction and field allowlisting happen at the consumer boundary before either ring stores
     * it.
     */
    private fun redact(event: LocalDiagnosticEvent): LocalDiagnosticEvent =
        event.copy(
            fields =
                event.fields
                    .mapNotNull { (field, value) -> DiagnosticRedactor.safeField(field, value) }
                    .toMap()
        )

    private fun boundedAppend(current: List<LocalDiagnosticEvent>, event: LocalDiagnosticEvent) =
        (current + event).takeLast(MAX_EVENTS)

    private fun fitDurable(input: List<LocalDiagnosticEvent>): List<LocalDiagnosticEvent> {
        val events = input.toMutableList()
        while (events.isNotEmpty() && encode(events).encodeToByteArray().size > MAX_FILE_BYTES) {
            events.removeAt(0)
        }
        return events
    }

    private fun replaceFile(events: List<LocalDiagnosticEvent>): Result<Unit> {
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                return Result.failure(IOException("Unable to create diagnostics directory"))
            }
            val destination = File(directory, RECORD_FILE)
            val temporary = File(directory, "$RECORD_FILE.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(encode(events).encodeToByteArray())
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    return Result.failure(unsupported)
                }
            }
            return Result.success(Unit)
        } catch (failure: Exception) {
            return Result.failure(failure)
        }
    }

    private fun encode(events: List<LocalDiagnosticEvent>): String =
        JsonArray(
                events.map { event ->
                    buildJsonObject {
                        put("elapsedMs", event.elapsedMs)
                        put("source", event.source)
                        put("event", event.name)
                        put(
                            "fields",
                            JsonObject(
                                event.fields.mapValues { (_, value) -> JsonPrimitive(value) }
                            ),
                        )
                    }
                }
            )
            .toString()

    companion object {
        const val MAX_EVENTS = 200
        const val MAX_FILE_BYTES = 64 * 1024
        const val DIAGNOSTICS_DIRECTORY = "diagnostics/v1"
        const val RECORD_FILE = "rolling.json"
    }
}

/** Shared defense-in-depth redactor, applied before any arbitrary value could enter a record. */
object DiagnosticRedactor {
    private val forbiddenFields =
        setOf(
            "token",
            "pairingToken",
            "secret",
            "pin",
            "password",
            "certificate",
            "spki",
            "proof",
            "mac",
            "wifiMac",
            "ip",
            "address",
            "host",
            "ssid",
            "wifi",
            "command",
            "commandText",
            "text",
            "tvId",
            "friendlyName",
            "uid",
            "email",
            "username",
        )
    private val allowedFields =
        setOf("elapsedMs", "event", "source", "state", "failure", "repairReason", "capabilities")
    private val url = Regex("(?i)\\b(?:https?|wss?)://[^\\s\\\"]+")
    private val ipv4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val ipv6 = Regex("(?i)(?<![\\w])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]{0,4}(?![\\w])")
    private val mac = Regex("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b")
    private val email = Regex("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b")
    private val uuid = Regex("(?i)\\b[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\\b")

    fun safeField(
        field: String,
        value: String,
        secretValues: Set<String> = emptySet(),
    ): Pair<String, String>? {
        if (field in forbiddenFields || field !in allowedFields) return null
        var redacted = value.replace(url, "[url]")
        redacted = redacted.replace(ipv4, "[ip]").replace(ipv6, "[ip]")
        redacted = redacted.replace(mac, "[mac]")
        redacted = redacted.replace(email, "[account]")
        redacted = redacted.replace(uuid, "[id]")
        secretValues.filter(String::isNotBlank).forEach { secret ->
            redacted = redacted.replace(secret, "[redacted]")
        }
        return field to redacted
    }
}
