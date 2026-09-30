package dev.anthracite.appt.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The preferences file every typed key lives in (data.md#datastore). */
private const val PREFERENCES_NAME = "appt"

private val Context.appPreferences: DataStore<Preferences> by
    preferencesDataStore(name = PREFERENCES_NAME)

/** Device-local navigation preference. Pointer can be selected only with live pointer evidence. */
enum class NavigationMode {
    Directional,
    Pointer,
}

/** The interaction settings consumed as one immutable Remote/ViewModel input. */
data class InteractionPreferences(
    val hapticsEnabled: Boolean = true,
    val volumeButtonsControlTv: Boolean = true,
    val navigationMode: NavigationMode = NavigationMode.Directional,
)

/**
 * Typed preference keys (data.md#datastore).
 *
 * Call sites never use raw key strings, and no key in this store holds a television secret, an
 * address, an account identifier, or sync metadata. UI writes remain ViewModel-owned.
 */
internal enum class FirstControlWriteProbeEvent {
    AcceptedCommandResult,
    WriteStarted,
    WriteCompleted,
    WriteCancelled,
    WriteIOException,
    WriteUnexpectedFailure,
    PreferencesReadIOException,
    StoreReadBackFalse,
    StoreReadBackTrue,
    FlowEmittedFalse,
    FlowEmittedTrue,
}

class PreferenceStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.appPreferences)

    /** Temporary, internal probe for the first-control integration reproducer. */
    @Volatile internal var firstControlWriteProbe: ((FirstControlWriteProbeEvent) -> Unit)? = null

    internal fun reportFirstControlWriteProbe(event: FirstControlWriteProbeEvent) {
        firstControlWriteProbe?.invoke(event)
    }

    private val preferences: Flow<Preferences> =
        store.data.catch { cause ->
            if (cause is IOException) {
                reportFirstControlWriteProbe(FirstControlWriteProbeEvent.PreferencesReadIOException)
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }

    val firstControlAchieved: Flow<Boolean> =
        preferences.map { values ->
            val achieved = values[FIRST_CONTROL_ACHIEVED] ?: false
            reportFirstControlWriteProbe(
                if (achieved) FirstControlWriteProbeEvent.FlowEmittedTrue
                else FirstControlWriteProbeEvent.FlowEmittedFalse
            )
            achieved
        }

    val interaction: Flow<InteractionPreferences> =
        preferences.map { values ->
            InteractionPreferences(
                hapticsEnabled = values[HAPTICS_ENABLED] ?: true,
                volumeButtonsControlTv = values[VOLUME_BUTTONS_CONTROL_TV] ?: true,
                navigationMode =
                    values[NAVIGATION_MODE]
                        ?.let { saved -> NavigationMode.entries.firstOrNull { it.name == saved } }
                        ?: NavigationMode.Directional,
            )
        }

    suspend fun setFirstControlAchieved() {
        reportFirstControlWriteProbe(FirstControlWriteProbeEvent.WriteStarted)
        try {
            store.edit { it[FIRST_CONTROL_ACHIEVED] = true }
            reportFirstControlWriteProbe(FirstControlWriteProbeEvent.WriteCompleted)
            reportFirstControlWriteProbe(
                if (store.data.first()[FIRST_CONTROL_ACHIEVED] == true) {
                    FirstControlWriteProbeEvent.StoreReadBackTrue
                } else {
                    FirstControlWriteProbeEvent.StoreReadBackFalse
                }
            )
        } catch (cancelled: CancellationException) {
            reportFirstControlWriteProbe(FirstControlWriteProbeEvent.WriteCancelled)
            throw cancelled
        } catch (ignored: IOException) {
            reportFirstControlWriteProbe(FirstControlWriteProbeEvent.WriteIOException)
            // A failed local milestone write cannot interrupt a live TV command.
        } catch (failure: Throwable) {
            reportFirstControlWriteProbe(FirstControlWriteProbeEvent.WriteUnexpectedFailure)
            throw failure
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) = update { it[HAPTICS_ENABLED] = enabled }

    suspend fun setVolumeButtonsControlTv(enabled: Boolean) =
        update { it[VOLUME_BUTTONS_CONTROL_TV] = enabled }

    suspend fun setNavigationMode(mode: NavigationMode) =
        update { it[NAVIGATION_MODE] = mode.name }

    private suspend fun update(change: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit(change)
        } catch (ignored: IOException) {
            // Preferences are best-effort local interaction state; a failed write is not a TV fault.
        }
    }

    private companion object {
        val FIRST_CONTROL_ACHIEVED = booleanPreferencesKey("firstControlAchieved")
        val HAPTICS_ENABLED = booleanPreferencesKey("hapticsEnabled")
        val VOLUME_BUTTONS_CONTROL_TV = booleanPreferencesKey("volumeButtonsControlTv")
        val NAVIGATION_MODE = stringPreferencesKey("navigationMode")
    }
}
