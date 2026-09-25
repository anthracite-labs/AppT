package dev.anthracite.appt.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The real DAO against a real in-memory database (data.md#room). */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class TvProfileDaoTest {
    private lateinit var database: AppTDatabase
    private lateinit var dao: TvProfileDao

    @Before
    fun openDatabase() {
        val context = RuntimeEnvironment.getApplication()
        database =
            Room.inMemoryDatabaseBuilder(context, AppTDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.tvProfileDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anUpsertIsInsertThenReplace() = runTest {
        val id = "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"
        dao.upsert(profile(id, name = "Living Room TV", createdAt = 10L))
        assertNull(dao.find(id)?.lastOpenedAt)

        dao.upsert(profile(id, name = "Den TV", createdAt = 10L, lastOpenedAt = 20L))

        val row = dao.observe(id).first()
        assertEquals("Den TV", row?.friendlyName)
        assertEquals(20L, row?.lastOpenedAt)
        assertEquals(10L, row?.createdAt)
    }

    @Test
    fun observeEmitsTheRowAndNullForAnUnknownId() = runTest {
        assertNull(dao.observe("never-seen").first())
        dao.upsert(profile("local-7c1e", name = "Guest TV", createdAt = 1L))
        assertEquals("Guest TV", dao.observe("local-7c1e").first()?.friendlyName)
        assertNull(dao.find("never-seen"))
    }

    @Test
    fun theDatabaseIsNamedAndVersionedAsTheArchitectureSays() {
        assertEquals("appt.db", AppTDatabase.NAME)
        assertEquals(1, AppTDatabase.VERSION)
    }

    private fun profile(id: String, name: String?, createdAt: Long, lastOpenedAt: Long? = null) =
        TvProfile(
            tvId = id,
            friendlyName = name,
            nameSource = NameSource.TV,
            stableIdentity = id.startsWith("3f"),
            createdAt = createdAt,
            lastOpenedAt = lastOpenedAt,
        )
}
