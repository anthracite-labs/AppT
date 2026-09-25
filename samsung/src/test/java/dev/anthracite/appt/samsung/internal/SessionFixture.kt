package dev.anthracite.appt.samsung.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * One replayed event on a session transport: an inbound frame the scripted adapter delivers, or the
 * end of the socket.
 *
 * @property atMs milliseconds after the socket opens, on the caller's virtual clock.
 */
internal sealed interface SessionEvent {
    val atMs: Long

    /** One inbound frame. [body] is the exact text the television sent. */
    data class Frame(override val atMs: Long, val body: String) : SessionEvent

    /** The television ended the socket. */
    data class Close(override val atMs: Long) : SessionEvent
}

/**
 * Loads `samsung/src/test/resources/samsung/fixtures/<case-id>/` for the session-transport adapter.
 *
 * `trace.jsonl` is one JSON object per line, in the shape docs/architecture/testing.md#fixtures
 * prescribes:
 * * `{"tMs":0,"dir":"in","kind":"ws-text","body":{…}}` — one inbound frame;
 * * `{"tMs":40,"dir":"in","kind":"ws-oversize","body":"[oversized-frame]"}` — a frame beyond the 64
 *   KiB parser limit, generated here rather than committed;
 * * `{"tMs":90,"dir":"in","kind":"ws-close","body":null}` — the socket ends;
 * * `{"tMs":400,"dir":"out","kind":"ws-text","body":{…}}` — the frame the session is expected to
 *   write. The test asserts the transport recorded exactly this.
 *
 * A committed token is always the placeholder `[fixture-token]`; a test that must prove a token was
 * or was not written injects a known one.
 */
internal data class SessionFixture(
    val caseId: String,
    val inbound: List<SessionEvent>,
    val expectedOutbound: List<Pair<Long, String>>,
) {
    companion object {
        private const val RESOURCE_ROOT = "/samsung/fixtures"

        fun load(caseId: String): SessionFixture {
            val resource =
                checkNotNull(
                    SessionFixture::class.java.getResource("$RESOURCE_ROOT/$caseId/trace.jsonl")
                ) {
                    "missing fixture $caseId"
                }
            val events =
                resource
                    .readText()
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { Json.parseToJsonElement(it).jsonObject }
            return SessionFixture(
                caseId = caseId,
                inbound = events.filter { it.string("dir") == "in" }.mapNotNull(::inboundEvent),
                expectedOutbound =
                    events
                        .filter { it.string("dir") == "out" && it.string("kind") == "ws-text" }
                        .map { it.long("tMs") to it.getValue("body").toString() },
            )
        }

        private fun inboundEvent(event: JsonObject): SessionEvent? {
            val atMs = event.long("tMs")
            return when (event.string("kind")) {
                "ws-text" -> SessionEvent.Frame(atMs, event.frameBody())
                "ws-oversize" -> SessionEvent.Frame(atMs, oversizedFrame())
                "ws-close" -> SessionEvent.Close(atMs)
                else -> null
            }
        }

        /** A frame one code point beyond the parser limit: malformed, not a crash. */
        private fun oversizedFrame(): String {
            val padding = "x".repeat(RemoteChannel.MAX_FRAME_CODE_POINTS + 1)
            return """{"event":"ms.channel.connect","data":{"token":"[fixture-token]","pad":"$padding"}}"""
        }

        private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

        /**
         * The exact frame text the television sent.
         *
         * An object body is compacted, so a well-formed frame is delivered byte-for-byte as the
         * channel would write it. A string body is delivered unescaped, which is how a deliberately
         * malformed frame — truncated, or a non-object root — is recorded without the fixture file
         * itself becoming invalid JSON.
         */
        private fun JsonObject.frameBody(): String {
            val body = getValue("body")
            return if (body is JsonPrimitive && body.isString) body.content else body.toString()
        }

        private fun JsonObject.long(key: String): Long =
            (getValue(key) as? JsonPrimitive)?.content?.toLong()
                ?: error("fixture event without $key")
    }
}
