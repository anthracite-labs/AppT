package dev.anthracite.appt.samsung.internal

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Loads `samsung/src/test/resources/samsung/fixtures/<case-id>/`.
 *
 * `trace.jsonl` is one JSON object per line:
 * * `{"kind":"ssdp","atMs":…,"host":…,"response":"<raw search response>"}` — inbound SSDP reply;
 * * `{"kind":"airplay","atMs":…,"host":…,"txt":{…}}` — inbound resolved AirPlay service;
 * * `{"kind":"device-info","host":…,"port":…,"delayMs":…,"document":{…}|"<raw body>"}` — what the
 *   host serves; a host with no entry never answers.
 *
 * An optional `"scan": n` limits a probe event to the n-th scan on the same transport.
 */
internal data class Fixture(
    val caseId: String,
    val probes: List<ProbeEvent>,
    val deviceInfo: Map<Pair<String, Int>, DeviceInfoEvent>,
) {
    sealed interface ProbeEvent {
        val atMs: Long
        val host: String
        val scan: Int?

        data class SsdpReply(
            override val atMs: Long,
            override val host: String,
            override val scan: Int?,
            val response: String,
        ) : ProbeEvent

        data class AirPlayService(
            override val atMs: Long,
            override val host: String,
            override val scan: Int?,
            val txt: Map<String, ByteArray?>,
        ) : ProbeEvent
    }

    data class DeviceInfoEvent(val delayMs: Long, val document: String)

    companion object {
        const val RESOURCE_ROOT = "/samsung/fixtures"

        fun load(caseId: String): Fixture {
            val resource =
                checkNotNull(
                    Fixture::class.java.getResource("$RESOURCE_ROOT/$caseId/trace.jsonl")
                ) {
                    "missing fixture $caseId"
                }
            val lines = resource.readText().lines().filter { it.isNotBlank() }
            val events = lines.map { Json.parseToJsonElement(it).jsonObject }
            val probes = events.mapNotNull(::probeEvent)
            val deviceInfo =
                events
                    .filter { it.string("kind") == "device-info" }
                    .associate { event ->
                        val document =
                            when (val value = event.getValue("document")) {
                                is JsonPrimitive -> value.content
                                else -> value.toString()
                            }
                        (event.string("host") to event.getValue("port").jsonPrimitive.int) to
                            DeviceInfoEvent(event.getValue("delayMs").jsonPrimitive.long, document)
                    }
            return Fixture(caseId, probes, deviceInfo)
        }

        /** Every fixture directory, for the provenance and redaction checks. */
        fun directories(): List<File> {
            val root =
                checkNotNull(Fixture::class.java.getResource(RESOURCE_ROOT)) { "no fixtures" }
            return File(root.toURI())
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .sortedBy { it.name }
        }

        private fun probeEvent(event: JsonObject): ProbeEvent? {
            val atMs = event["atMs"]?.jsonPrimitive?.long ?: return null
            val host = event.string("host")
            val scan = event["scan"]?.jsonPrimitive?.intOrNull
            return when (event.string("kind")) {
                "ssdp" -> ProbeEvent.SsdpReply(atMs, host, scan, event.string("response"))
                "airplay" ->
                    ProbeEvent.AirPlayService(
                        atMs,
                        host,
                        scan,
                        event.getValue("txt").jsonObject.mapValues { (_, value) ->
                            value.jsonPrimitive.content.encodeToByteArray()
                        },
                    )
                else -> error("unknown probe kind in fixture: ${event.string("kind")}")
            }
        }

        private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    }
}
