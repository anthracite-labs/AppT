package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.ControlAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoParserTest {
    private val uuid = "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

    private fun document(device: String, top: String = """"type":"Samsung SmartTV""""): String =
        """{"device":{$device},$top}"""

    @Test
    fun televisionTypeIsRequired() {
        val tv = DeviceInfoParser.parse(document(""""type":"Samsung SmartTV","id":"uuid:$uuid""""))
        assertTrue(tv!!.isTelevision)
        listOf("Samsung Speaker", "Samsung Soundbar", "Samsung Blu-ray Player", "").forEach { type
            ->
            val other =
                DeviceInfoParser.parse(document(""""type":"$type"""", top = """"type":"$type""""))
            assertFalse("$type is not a television", other!!.isTelevision)
        }
        assertFalse(DeviceInfoParser.parse("""{"name":"x"}""")!!.isTelevision)
    }

    @Test
    fun identityPrefersDeviceIdThenDuidThenUdnThenTopLevelId() {
        val other = "c4b3a291-0f1e-4d2c-9b8a-7f6e5d4c3b2a"
        fun uuidOf(device: String, top: String = """"type":"Samsung SmartTV"""") =
            DeviceInfoParser.parse(document(device, top))!!.uuid

        assertEquals(uuid, uuidOf(""""id":"uuid:$uuid","duid":"uuid:$other""""))
        assertEquals(uuid, uuidOf(""""id":"junk","duid":"uuid:$uuid","udn":"uuid:$other""""))
        assertEquals(uuid, uuidOf(""""udn":"uuid:${uuid.uppercase()}""""))
        assertEquals(uuid, uuidOf(""""name":"x"""", top = """"id":"uuid:$uuid""""))
        assertNull(uuidOf(""""id":"junk""""))
    }

    @Test
    fun nameIsTrimmedStrippedAndCappedAtFortyCharacters() {
        assertEquals("Living Room TV", DeviceInfoParser.sanitizeName("  Living Room TV \n"))
        assertEquals("AB", DeviceInfoParser.sanitizeName("A\u0000\u202EB"))
        assertEquals("", DeviceInfoParser.sanitizeName(null))
        assertEquals("", DeviceInfoParser.sanitizeName("   "))
        val long = "x".repeat(60)
        assertEquals(40, DeviceInfoParser.sanitizeName(long).length)
        // Forty code points, never half a surrogate pair.
        val emoji = "\uD83D\uDCFA".repeat(45)
        val capped = DeviceInfoParser.sanitizeName(emoji)
        assertEquals(40, capped.codePointCount(0, capped.length))
        assertFalse(Character.isHighSurrogate(capped.last()))
    }

    /** presentation.md/discovery.md: a label never shows an address, port, UUID, or MAC. */
    @Test
    fun namesNeverCarryAddressesPortsUuidsOrMacs() {
        val cases =
            mapOf(
                "192.0.2.20" to "",
                "Living Room (192.0.2.20)" to "Living Room",
                "Bedroom 192.0.2.20:8001" to "Bedroom",
                "Garage - 192.0.2.9" to "Garage",
                "uuid:3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e" to "",
                "Bedroom 3F2D1C0B-8A7E-4B5C-9D6E-1F2A3B4C5D6E" to "Bedroom",
                "Den 02:00:5E:00:53:01" to "Den",
                "Den 02-00-5e-00-53-01" to "Den",
                "Kitchen:8001" to "Kitchen",
                "Study port 8002" to "Study",
                "Lounge fe80::1c2b:3d4e" to "Lounge",
                "[2001:db8::7]:8002 Office" to "Office",
                "::ffff:192.0.2.1" to "",
                "Hall wss://tv.example:8002/api" to "Hall",
            )
        cases.forEach { (raw, expected) ->
            assertEquals(raw, expected, DeviceInfoParser.sanitizeName(raw))
        }
        val parsed = DeviceInfoParser.parse(document(""""name":"Living Room (192.0.2.20)""""))
        assertEquals("Living Room", parsed!!.name)
    }

    @Test
    fun ordinaryNamesAreLeftAlone() {
        listOf(
                "[TV] Samsung 7 Series (55)",
                "Samsung Q80 Series (65)",
                "Mum & Dad's TV",
                "Samsung 8 Series: 75",
                "Den :: Main",
            )
            .forEach { name -> assertEquals(name, DeviceInfoParser.sanitizeName(name)) }
    }

    @Test
    fun identifiersAreRemovedBeforeTheCapSoNoFragmentRemains() {
        // Capping first would leave "Big Living Room Television Set 192.0.2." on the card.
        assertEquals(
            "Big Living Room Television Set",
            DeviceInfoParser.sanitizeName("Big Living Room Television Set 192.0.2.200"),
        )
    }

    @Test
    fun nonTizenExplicitOsIsUnsupportedAndAbsentOsNeedsPairing() {
        fun availability(os: String?) =
            DeviceInfoParser.parse(
                    document(if (os == null) """"name":"x"""" else """"OS":"$os"""")
                )!!
                .availability
        assertEquals(ControlAvailability.NeedsPairing, availability("Tizen"))
        assertEquals(ControlAvailability.NeedsPairing, availability("TIZEN 6.5"))
        assertEquals(ControlAvailability.NeedsPairing, availability(null))
        assertEquals(ControlAvailability.Unsupported, availability("Orsay"))
    }

    @Test
    fun isSupportIsToleratedAsObjectOrString() {
        val asObject =
            """{"device":{"type":"Samsung SmartTV"},"isSupport":{"remote_available":"true"}}"""
        val asString =
            """{"device":{"type":"Samsung SmartTV"},"isSupport":"{\"remote_available\":\"true\"}"}"""
        assertTrue(DeviceInfoParser.parse(asObject)!!.isTelevision)
        assertTrue(DeviceInfoParser.parse(asString)!!.isTelevision)
    }

    @Test
    fun reportedHostIsReadForTheCandidateCheckOnly() {
        assertEquals(
            "[host-a]",
            DeviceInfoParser.parse(document(""""ip":"[host-a]""""))!!.reportedHost,
        )
        assertNull(DeviceInfoParser.parse(document(""""ip":"""""))!!.reportedHost)
    }

    @Test
    fun malformedOversizedOrTooDeepInputIsUnreadable() {
        assertNull(DeviceInfoParser.parse("<html></html>"))
        assertNull(DeviceInfoParser.parse("""["not","an","object"]"""))
        assertNull(DeviceInfoParser.parse("""{"device":"""))
        val oversized = """{"name":"${"x".repeat(MAX_DEVICE_INFO_BYTES)}"}"""
        assertNull(DeviceInfoParser.parse(oversized))
        val deep = "[".repeat(9) + "]".repeat(9)
        assertNull(DeviceInfoParser.parse("""{"a":$deep}"""))
    }

    @Test
    fun depthCheckIgnoresBracketsInsideStrings() {
        assertTrue(DeviceInfoParser.withinDepth("""{"a":"[[[[[[[[[[\"{{{{{{{{"}""", 2))
        assertTrue(DeviceInfoParser.withinDepth("[".repeat(8) + "]".repeat(8), 8))
        assertFalse(DeviceInfoParser.withinDepth("[".repeat(9) + "]".repeat(9), 8))
    }

    @Test
    fun nonStringValuesAreToleratedAndNullIsAbsent() {
        val info =
            DeviceInfoParser.parse(
                """{"device":{"type":"Samsung SmartTV","name":42,"OS":null}}"""
            )!!
        assertEquals("42", info.name)
        assertEquals(ControlAvailability.NeedsPairing, info.availability)
    }
}
