package dev.anthracite.appt.discovery

/**
 * The last check before a television's name reaches a card (presentation.md: a card never shows an
 * address, port, UUID, or MAC). The samsung module already removes these from names; a name that
 * still carries anything identifier-shaped is dropped whole rather than repaired, and the card then
 * shows its generic "Samsung TV" label.
 */
internal object DisplayLabel {
    private const val HEX = "[0-9A-Fa-f]"

    private val IDENTIFIER =
        Regex(
            """\b\d{1,3}(?:\.\d{1,3}){3}\b|""" +
                """\b$HEX{8}-$HEX{4}-$HEX{4}-$HEX{4}-$HEX{12}\b|""" +
                """\b$HEX{2}(?:[:-]$HEX{2}){5}\b|""" +
                """$HEX{0,4}:$HEX{0,4}:[0-9A-Fa-f:]{0,40}\d|""" +
                """(?<=\S):\d{2,5}\b|(?i:\bport\s*\d{1,5}\b)|://"""
        )

    // Also reject IPv6 without decimal digits. Compression or all eight groups, with a hex
    // character, excludes a bare "::" or an ordinary colon-separated name like "A:B:C".
    private val IPV6_LITERAL =
        Regex(
            """(?<![\w:])(?=[0-9A-Fa-f:]{0,39}[0-9A-Fa-f])""" +
                """(?:(?=[0-9A-Fa-f:]{0,39}::)(?:$HEX{0,4}:){2,8}$HEX{0,4}|""" +
                """(?:$HEX{1,4}:){7}$HEX{1,4})(?![\w:])"""
        )

    fun of(name: String): String =
        if (IDENTIFIER.containsMatchIn(name) || IPV6_LITERAL.containsMatchIn(name)) "" else name
}
