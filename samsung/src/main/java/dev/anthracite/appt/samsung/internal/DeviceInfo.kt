package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.ControlAvailability
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What discovery keeps from one device-info document. Everything else in the document (address,
 * Wi-Fi name, MAC, model, firmware, developer fields) is never read into this type, and the raw
 * document is neither logged nor stored (docs/architecture/protocol.md#device-info-handling).
 *
 * @property uuid the normalized television UUID, or null when the document carries none.
 * @property reportedHost the address the document says it was served from, used only to reject a
 *   document that does not belong to the probed candidate. Not retained past confirmation.
 */
internal data class DeviceInfo(
    val isTelevision: Boolean,
    val uuid: String?,
    val name: String,
    val availability: ControlAvailability,
    val reportedHost: String?,
)

/**
 * Bounded, tolerant device-info parser. Over-limit or malformed input is "unreadable", not a crash.
 */
internal object DeviceInfoParser {
    /** protocol.md#limits. */
    const val MAX_DEPTH: Int = 8

    /** protocol.md#limits: friendly name retained from the television, after trim. */
    const val MAX_NAME_CODE_POINTS: Int = 40

    /** Device-info `type` for a television. Soundbars, speakers and players report other types. */
    private val TELEVISION_TYPE = Regex("samsung\\s*smart\\s*tv", RegexOption.IGNORE_CASE)
    private const val TIZEN = "tizen"

    fun parse(document: String): DeviceInfo? {
        val root = parseRoot(document) ?: return null
        val device = root["device"] as? JsonObject
        val field = { key: String -> device.text(key) ?: root.text(key) }

        val uuid =
            sequenceOf(device.text("id"), device.text("duid"), device.text("udn"), root.text("id"))
                .firstNotNullOfOrNull(TvIdentity::normalize)
        return DeviceInfo(
            isTelevision = field("type")?.let(TELEVISION_TYPE::containsMatchIn) == true,
            uuid = uuid,
            name = sanitizeName(field("name")),
            availability = availabilityFor(field("OS")),
            reportedHost = device.text("ip")?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * protocol.md: "Non-Tizen explicit OS supports `Unsupported`". A television that states a
     * non-Tizen OS does not speak the adopted WebSocket control channel. An absent OS is not
     * evidence either way, so the adopted path is assumed and the card needs pairing. Discovery
     * never opens the control channel to find out: that is pairing, and S02 sends no key, text or
     * launch frame and no token.
     */
    private fun availabilityFor(os: String?): ControlAvailability =
        if (os.isNullOrBlank() || os.lowercase(Locale.ROOT).contains(TIZEN)) {
            ControlAvailability.NeedsPairing
        } else {
            ControlAvailability.Unsupported
        }

    /**
     * Removes control and invisible formatting characters (so a name cannot reorder or hide the
     * surrounding UI text), removes any address, port, UUID, or MAC ([NameScrubber]), trims, and
     * caps the result at [MAX_NAME_CODE_POINTS] code points without splitting a surrogate pair.
     * Identifiers are removed before the cap, so a cut can never leave part of one behind.
     */
    fun sanitizeName(raw: String?): String {
        if (raw == null) return ""
        val visible = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            if (
                !Character.isISOControl(codePoint) &&
                    Character.getType(codePoint) != Character.FORMAT.toInt()
            ) {
                visible.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        val trimmed = NameScrubber.scrub(visible.toString()).trim()
        val codePoints = trimmed.codePointCount(0, trimmed.length)
        return if (codePoints <= MAX_NAME_CODE_POINTS) {
            trimmed
        } else {
            trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_NAME_CODE_POINTS)).trimEnd()
        }
    }

    private fun parseRoot(document: String): JsonObject? {
        if (document.encodeToByteArray().size > MAX_DEVICE_INFO_BYTES) return null
        if (!withinDepth(document, MAX_DEPTH)) return null
        return try {
            Json.parseToJsonElement(document) as? JsonObject
        } catch (ignored: SerializationException) {
            null
        }
    }

    /**
     * Rejects documents nested deeper than [maxDepth] before they reach the JSON parser, so a
     * hostile responder cannot drive recursion depth.
     */
    fun withinDepth(document: String, maxDepth: Int): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (char in document) {
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                inString -> inString = char != '"'
                char == '"' -> inString = true
                char == '{' || char == '[' -> depth++
                char == '}' || char == ']' -> depth--
            }
            if (depth > maxDepth) return false
        }
        return true
    }

    /** A string field, tolerating the television reporting it as a number or boolean. */
    private fun JsonObject?.text(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
