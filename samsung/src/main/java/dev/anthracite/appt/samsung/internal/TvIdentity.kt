package dev.anthracite.appt.samsung.internal

import java.util.Locale
import java.util.UUID

/**
 * Television identity rules (docs/architecture/discovery.md#identity).
 *
 * A `TvId` is either the television's own protocol UUID, normalized, or an id minted on this
 * phone. It is never derived from a MAC, an address, a model, or a name.
 */
internal object TvIdentity {
    private val CANONICAL_UUID =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private const val UUID_PREFIX = "uuid:"
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"
    private const val MINTED_PREFIX = "local-"

    /**
     * Strips a leading `uuid:`, lowercases, and requires a canonical UUID. Returns null for
     * anything else, including the nil UUID, which identifies nothing.
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        val bare =
            if (trimmed.startsWith(UUID_PREFIX, ignoreCase = true)) {
                trimmed.substring(UUID_PREFIX.length)
            } else {
                trimmed
            }
        val lower = bare.lowercase(Locale.ROOT)
        return lower.takeIf { CANONICAL_UUID.matches(it) && it != NIL_UUID }
    }

    /**
     * Mints a device-local id for a confirmed television that exposes no stable UUID. It carries
     * no information about the television, so it cannot leak one.
     */
    fun mint(): String = MINTED_PREFIX + UUID.randomUUID()
}
