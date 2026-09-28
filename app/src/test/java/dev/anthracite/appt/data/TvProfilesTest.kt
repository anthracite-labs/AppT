package dev.anthracite.appt.data

import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** data.md#ownership-rules: remembering a television is one upsert, and it keeps what it must. */
class TvProfilesTest {
    private val dao = FakeTvProfileDao()
    private var now = 1_000L
    private val profiles = TvProfiles(dao) { now }

    private val livingRoom = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e")

    @Test
    fun selectingACardWritesOneProfileRowNamedAfterTheTelevision() = runTest {
        profiles.rememberSelected(livingRoom, "Living Room TV", stableIdentity = true)

        val row = dao.current().single()
        assertEquals(livingRoom.value, row.tvId)
        assertEquals("Living Room TV", row.friendlyName)
        assertEquals(NameSource.TV, row.nameSource)
        assertTrue(row.stableIdentity)
        assertEquals(1_000L, row.createdAt)
        assertNull("a newly remembered television has never been opened", row.lastOpenedAt)
    }

    @Test
    fun aBlankLabelLeavesTheNameNull() = runTest {
        profiles.rememberSelected(livingRoom, "", stableIdentity = true)

        assertNull(dao.current().single().friendlyName)
    }

    @Test
    fun aLongNameIsCappedAtFortyCharacters() = runTest {
        profiles.rememberSelected(livingRoom, "A".repeat(80), stableIdentity = true)

        assertEquals(40, dao.current().single().friendlyName?.length)
    }

    @Test
    fun reselectingTheSameTelevisionKeepsOneRowAndItsCreationTime() = runTest {
        profiles.rememberSelected(livingRoom, "Living Room TV", stableIdentity = true)
        now = 9_000L
        profiles.rememberSelected(livingRoom, "Living Room TV", stableIdentity = true)

        val rows = dao.current()
        assertEquals("one row per television", 1, rows.size)
        assertEquals("the original creation time survives", 1_000L, rows.single().createdAt)
    }

    @Test
    fun aUserEditedNameIsNeverOverwrittenByTheTelevision() = runTest {
        profiles.rememberSelected(livingRoom, "Living Room TV", stableIdentity = true)
        dao.upsert(
            dao.current().single().copy(friendlyName = "Den TV", nameSource = NameSource.USER)
        )

        profiles.rememberSelected(livingRoom, "Samsung 7 Series", stableIdentity = true)

        val row = dao.current().single()
        assertEquals(NameSource.USER, row.nameSource)
        assertEquals("Den TV", row.friendlyName)
    }

    @Test
    fun markingOpenedWritesLastOpenedAtAndNothingElse() = runTest {
        profiles.rememberSelected(livingRoom, "Living Room TV", stableIdentity = true)
        now = 5_000L
        profiles.markOpened(livingRoom)

        val row = dao.current().single()
        assertEquals(5_000L, row.lastOpenedAt)
        assertEquals(1_000L, row.createdAt)
        assertEquals(NameSource.TV, row.nameSource)
    }

    @Test
    fun markingOpenedAnUnrememberedTelevisionWritesNothing() = runTest {
        profiles.markOpened(TvId("never-selected"))

        assertTrue(dao.upserted.isEmpty())
        assertTrue(dao.current().isEmpty())
    }

    @Test
    fun aMintedIdentityIsRecordedAsUnstable() = runTest {
        profiles.rememberSelected(TvId("local-7c1e"), "Guest TV", stableIdentity = false)

        assertTrue(!dao.current().single().stableIdentity)
    }
}
