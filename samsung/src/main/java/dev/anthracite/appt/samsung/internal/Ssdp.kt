package dev.anthracite.appt.samsung.internal

import java.util.Locale

/**
 * SSDP message building and response classification for the two V1 search targets
 * (docs/architecture/discovery.md#probes). Pure; the socket side is [SsdpClient].
 *
 * Only these two targets are ever searched. There is no `ssdp:all` census and no other brand's
 * search target.
 */
internal object Ssdp {
    const val REMOTE_CONTROL_RECEIVER: String = "urn:samsung.com:device:RemoteControlReceiver:1"
    const val DIAL: String = "urn:dial-multiscreen-org:service:dial:1"

    /** Probe order: RemoteControlReceiver first, then DIAL. */
    val SEARCH_TARGETS: List<String> = listOf(REMOTE_CONTROL_RECEIVER, DIAL)

    /** Seconds a responder may wait before answering; keeps every answer well inside the bound. */
    private const val MAX_WAIT_SECONDS = 2

    private const val SAMSUNG = "samsung"
    private val OK_STATUS = Regex("^HTTP/1\\.[01] 200\\b.*")

    /** An `M-SEARCH` for [searchTarget]. [hostHeader] is the multicast group and port. */
    fun mSearch(searchTarget: String, hostHeader: String): ByteArray {
        require(searchTarget in SEARCH_TARGETS) { "not a V1 search target" }
        return buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: ").append(hostHeader).append("\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: ").append(MAX_WAIT_SECONDS).append("\r\n")
                append("ST: ").append(searchTarget).append("\r\n")
                append("\r\n")
            }
            .encodeToByteArray()
    }

    /**
     * Classifies one search response.
     * * RemoteControlReceiver responses are Samsung by definition of the search target.
     * * DIAL responses are kept only when the response already identifies Samsung (its `SERVER`
     *   header); every other DIAL device (streaming sticks, other brands) is dropped here.
     * * Anything else, including a response for a target AppT did not search, is dropped.
     */
    fun classify(response: String): Probe? {
        val headers = parseHeaders(response) ?: return null
        val searchTarget = headers["ST"]
        return when {
            searchTarget.equals(REMOTE_CONTROL_RECEIVER, ignoreCase = true) ->
                Probe.RemoteControlReceiver
            searchTarget.equals(DIAL, ignoreCase = true) &&
                headers["SERVER"].orEmpty().lowercase(Locale.ROOT).contains(SAMSUNG) ->
                Probe.DialSamsung
            else -> null
        }
    }

    /** Header names upper-cased; null unless the status line is a 200 response. */
    private fun parseHeaders(response: String): Map<String, String>? {
        val lines = response.split("\r\n", "\n")
        if (!OK_STATUS.matches(lines.first().trim())) return null
        return lines
            .drop(1)
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null
                else
                    line.substring(0, colon).trim().uppercase(Locale.ROOT) to
                        line.substring(colon + 1).trim()
            }
            .toMap()
    }
}
