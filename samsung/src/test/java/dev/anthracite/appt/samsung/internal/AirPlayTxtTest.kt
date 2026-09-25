package dev.anthracite.appt.samsung.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPlayTxtTest {
    private fun txt(vararg entries: Pair<String, String?>) =
        entries.associate { (key, value) -> key to value?.encodeToByteArray() }

    @Test
    fun browsesOnlyTheAirPlayServiceType() {
        assertEquals("_airplay._tcp", AirPlayTxt.SERVICE_TYPE)
    }

    @Test
    fun keepsOnlyManufacturerSamsung() {
        assertTrue(AirPlayTxt.identifiesSamsung(txt("manufacturer" to "Samsung")))
        assertTrue(AirPlayTxt.identifiesSamsung(txt("Manufacturer" to " Samsung Electronics ")))
        assertFalse(AirPlayTxt.identifiesSamsung(txt("manufacturer" to "Other Audio Co")))
        assertFalse(AirPlayTxt.identifiesSamsung(txt("model" to "Samsung")))
        assertFalse(AirPlayTxt.identifiesSamsung(txt("manufacturer" to null)))
        assertFalse(AirPlayTxt.identifiesSamsung(emptyMap()))
    }
}
