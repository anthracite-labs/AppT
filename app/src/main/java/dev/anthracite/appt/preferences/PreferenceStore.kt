package dev.anthracite.appt.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** The preferences file every typed key lives in (data.md#datastore). */
private const val PREFERENCES_NAME = "appt"

private val Context.appPreferences: DataStore<Preferences> by
    preferencesDataStore(name = PREFERENCES_NAME)

/**
 * Typed preference keys (data.md#datastore).
 *
 * Call sites never use a raw key string, and no key in this store holds a television secret, an
 * address, an account identifier, or sync metadata.
 *
 * The [DataStore] is a constructor parameter so a test can build the store over its own file, which
 * is what makes the "set only on an accepted command" rule observable without a shared flag.
 */
class PreferenceStore(private val store: DataStore<Preferences>) {

    /** Production: the application's own preferences file. */
    constructor(context: Context) : this(context.appPreferences)

    /**
     * True once a command has returned `Accepted` on a television (data.md).
     *
     * This is a socket-write proxy, not visible television action. sync.md#remote-entry-gate reads it
     * to decide whether the exempt first session has been used; setting it never shows account UI
     * and never interrupts the session that earned it.
     */
    val firstControlAchieved: Flow<Boolean> =
        store.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> preferences[FIRST_CONTROL_ACHIEVED] ?: false }

    /**
     * Records the first accepted command. Idempotent: a later accepted command writes the same
     * value, and nothing else in the app can set or clear it.
     */
    suspend fun setFirstControlAchieved() {
        try {
            store.edit { preferences -> preferences[FIRST_CONTROL_ACHIEVED] = true }
        } catch (ignored: IOException) {
            // A failed write must not interrupt the session that earned the command, and it must
            // not reach the caller as a crash. A later accepted command writes the same value
            // again, so the flag is retried rather than lost.
        }
    }

    private companion object {
        val FIRST_CONTROL_ACHIEVED = booleanPreferencesKey("firstControlAchieved")
    }
}
