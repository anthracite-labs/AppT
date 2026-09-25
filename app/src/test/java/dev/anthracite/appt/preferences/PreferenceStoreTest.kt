package dev.anthracite.appt.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import dev.anthracite.appt.testing.PREFERENCES_FILE_NAME

/** data.md#datastore: `firstControlAchieved` is a typed key, off until a command is accepted. */
class PreferenceStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(): PreferenceStore {
        val file = File(folder.newFolder(), PREFERENCES_FILE_NAME)
        return PreferenceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { file },
            )
        )
    }

    @Test
    fun firstControlAchievedStartsFalse() = runTest {
        assertEquals(false, store().firstControlAchieved.first())
    }

    @Test
    fun settingFirstControlAchievedIsIdempotent() = runTest {
        val store = store()
        store.setFirstControlAchieved()
        store.setFirstControlAchieved()
        assertEquals(true, store.firstControlAchieved.first())
    }

    @Test
    fun aCorruptPreferencesFileReadsAsDefaults() = runTest {
        // data.md#corruption-recovery: "a corrupt preferences file is replaced with defaults", and
        // the user's television state in Room is unaffected. Nothing here may throw to the caller.
        val file = File(folder.newFolder(), PREFERENCES_FILE_NAME)
        file.writeBytes(ByteArray(32) { 0x7f })
        val store =
            PreferenceStore(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = { file },
                )
            )

        assertEquals(false, store.firstControlAchieved.first())
    }
}
