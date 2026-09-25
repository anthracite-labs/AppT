package dev.anthracite.appt.samsung.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvIdentityTest {
    private val bare = "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"

    @Test
    fun normalizationStripsPrefixAndLowercases() {
        assertEquals(bare, TvIdentity.normalize(bare))
        assertEquals(bare, TvIdentity.normalize("uuid:$bare"))
        assertEquals(bare, TvIdentity.normalize("UUID:${bare.uppercase()}"))
        assertEquals(bare, TvIdentity.normalize("  uuid:$bare  "))
    }

    @Test
    fun anythingButACanonicalUuidIsNotAnIdentity() {
        listOf(
                null,
                "",
                "uuid:",
                "not-a-uuid",
                "3f2d1c0b8a7e4b5c9d6e1f2a3b4c5d6e",
                "{$bare}",
                "$bare::urn:samsung.com:device:RemoteControlReceiver:1",
                "00000000-0000-0000-0000-000000000000",
            )
            .forEach { assertNull("'$it' must not normalize", TvIdentity.normalize(it)) }
    }

    @Test
    fun mintedIdsAreFreshAndCarryNoTelevisionData() {
        val first = TvIdentity.mint()
        val second = TvIdentity.mint()
        assertNotEquals(first, second)
        assertTrue(first.startsWith("local-"))
        // A minted id is never mistaken for a television-supplied UUID.
        assertNull(TvIdentity.normalize(first))
    }
}
