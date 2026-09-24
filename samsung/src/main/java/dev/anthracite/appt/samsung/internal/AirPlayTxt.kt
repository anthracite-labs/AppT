package dev.anthracite.appt.samsung.internal

/**
 * AirPlay TXT-record classification. Samsung soundbars and speakers also advertise `_airplay._tcp`,
 * so this only makes a host a candidate; device-info still decides television versus speaker.
 */
internal object AirPlayTxt {
    const val SERVICE_TYPE: String = "_airplay._tcp"
    private const val MANUFACTURER = "manufacturer"
    private const val SAMSUNG = "samsung"

    fun identifiesSamsung(attributes: Map<String, ByteArray?>): Boolean =
        attributes.entries.any { (key, value) ->
            key.equals(MANUFACTURER, ignoreCase = true) &&
                value?.decodeToString()?.trim()?.startsWith(SAMSUNG, ignoreCase = true) == true
        }
}
