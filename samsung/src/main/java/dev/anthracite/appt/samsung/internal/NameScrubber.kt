package dev.anthracite.appt.samsung.internal

/**
 * Removes network and hardware identifiers from a television's friendly name, so a label never
 * shows an address, port, UUID, or MAC (discovery.md and presentation.md: cards never show them).
 * Owners can type any name into the set, and some firmware adds an address or port to it.
 *
 * A name without identifiers is returned unchanged. When something was removed, leftover empty
 * brackets, repeated spaces, and separators at either end are tidied away; the result may be empty,
 * and the UI then shows its generic "Samsung TV" label.
 */
internal object NameScrubber {
    /**
     * Only this many leading characters are scrubbed. It keeps the regex work small and linear for
     * a name field that can be as large as the whole device-info bound, and still leaves far more
     * than the 40 characters a label can show.
     */
    const val MAX_SCRUBBED_CHARS: Int = 200

    private const val HEX = "[0-9A-Fa-f]"
    private const val DOTTED_QUAD = """\d{1,3}(?:\.\d{1,3}){3}"""

    /** Applied in order: URLs and UUIDs first, so their parts are not matched piecemeal. */
    private val IDENTIFIERS: List<Regex> =
        listOf(
            // A URL of any scheme, including whatever host and port it carries.
            Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://\S*"""),
            // A UUID, optionally prefixed "uuid:".
            Regex("""(?i)\b(?:uuid:)?$HEX{8}-$HEX{4}-$HEX{4}-$HEX{4}-$HEX{12}\b"""),
            // IPv6 with a decimal digit, including compression or a trailing dotted quad.
            Regex(
                """(?<![\w:])(?=[\w:.]{0,45}\d)(?:$HEX{0,4}:){2,7}(?:$DOTTED_QUAD|$HEX{1,4})?""" +
                    """(?![\w:])"""
            ),
            // Hex-only IPv6: compression or all eight groups, and at least one hex character.
            // A bare "::" and a non-address name such as "A:B:C" are not identifiers.
            // Scrub IPv6 before MACs so a MAC-shaped prefix cannot leave an address fragment.
            Regex(
                """(?<![\w:])(?=[0-9A-Fa-f:]{0,39}[0-9A-Fa-f])""" +
                    """(?:(?=[0-9A-Fa-f:]{0,39}::)(?:$HEX{0,4}:){2,8}$HEX{0,4}|""" +
                    """(?:$HEX{1,4}:){7}$HEX{1,4})(?![\w:])"""
            ),
            // A MAC address, colon or hyphen separated.
            Regex("""\b$HEX{2}(?:[:-]$HEX{2}){5}\b"""),
            // An IPv4 address, with an optional port.
            Regex("""\b$DOTTED_QUAD(?::\d{1,5})?\b"""),
            // A port attached to a word ("Kitchen:8001") or spelled out ("port 8002").
            Regex("""(?<=\S):\d{2,5}\b"""),
            Regex("""(?i)\bport\s*\d{1,5}\b"""),
        )

    private val EMPTY_BRACKETS = Regex("""\(\s*\)|\[\s*]|\{\s*}|<\s*>""")
    private val REPEATED_SPACE = Regex("""\s{2,}""")
    private val EDGE_SEPARATORS = Regex("""^[\s\-_:;,.@/|]+|[\s\-_:;,.@/|]+$""")

    fun scrub(name: String): String {
        val bounded = name.take(MAX_SCRUBBED_CHARS)
        val scrubbed =
            IDENTIFIERS.fold(bounded) { text, identifier -> identifier.replace(text, " ") }
        if (scrubbed == bounded) return bounded
        return scrubbed
            .replace(EMPTY_BRACKETS, " ")
            .replace(REPEATED_SPACE, " ")
            .replace(EDGE_SEPARATORS, "")
    }
}
