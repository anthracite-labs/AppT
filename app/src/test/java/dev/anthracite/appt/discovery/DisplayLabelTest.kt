package dev.anthracite.appt.discovery

import dev.anthracite.appt.testing.FakeSamsungTvs
import org.junit.Assert.assertEquals
import org.junit.Test

/** A card label never carries an address, port, UUID, or MAC (presentation.md#discovery). */
class DisplayLabelTest {

    @Test
    fun aNameCarryingAnIdentifierIsDroppedWhole() {
        listOf(
                "Den 192.0.2.20",
                "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e",
                "Den 02:00:5e:00:53:01",
                "Lounge fe80::1",
                "feee::face",
                "Den (feee::face)",
                "::cafe",
                "::a:b:c:d:e:f:a",
                "a:b:c:d:e:f:a::",
                "aaaa:bbbb:cccc:dddd:eeee:ffff:aaaa:bbbb",
                "aa:bb:cc:dd:ee:ff:aa:bb",
                "Kitchen:8001",
                "Study Port 8002",
                "Hall wss://tv",
            )
            .forEach { name -> assertEquals(name, "", DisplayLabel.of(name)) }
    }

    @Test
    fun ordinaryNamesAreKept() {
        listOf(
                "Living Room TV",
                "[TV] Samsung 7 Series (55)",
                "Samsung 8 Series: 75",
                "Den :: Main",
                "::",
                "A:B:C TV",
                "Mum & Dad's TV",
            )
            .forEach { name -> assertEquals(name, DisplayLabel.of(name)) }
    }

    @Test
    fun cardsGetTheCheckedLabel() {
        listOf("Den 192.0.2.20", "Den feee::face").forEach { name ->
            val cards = DiscoveryUiState.Initial.reduce(FakeSamsungTvs.found(name = name)).cards
            assertEquals(name, listOf(""), cards.map { it.label })
        }
    }
}
