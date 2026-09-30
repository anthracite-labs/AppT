package dev.anthracite.appt.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.anthracite.appt.data.FakeTvProfileDao
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.MainDispatcherRule
import dev.anthracite.appt.testing.PREFERENCES_FILE_NAME
import dev.anthracite.appt.testing.settle
import dev.anthracite.appt.testing.subscribeTo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private fun store(): PreferenceStore {
        val file = File(folder.newFolder(), PREFERENCES_FILE_NAME)
        return PreferenceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + mainRule.dispatcher),
                produceFile = { file },
            )
        )
    }

    @Test
    fun writesAreOwnedByViewModelAndPointerModeIsCapabilityGated() =
        runTest(mainRule.dispatcher) {
            val preferences = store()
            val tvs = FakeSamsungTvs()
            val id = TvId(FakeSamsungTvs.LIVING_ROOM_ID)
            val host = ActiveRemoteHost(tvs, backgroundScope)
            host.enter(id)
            settle()
            val viewModel = SettingsViewModel(preferences, host, "0.1.0")
            subscribeTo(viewModel.state)
            settle()

            assertEquals("0.1.0", viewModel.state.value.appVersion)
            assertFalse(viewModel.state.value.pointerAvailable)
            viewModel.onHaptics(false)
            viewModel.onPhoneVolumeButtons(false)
            viewModel.onNavigationMode(NavigationMode.Pointer)
            settle()
            val saved = preferences.interaction.first {
                !it.hapticsEnabled && !it.volumeButtonsControlTv
            }
            assertFalse(saved.hapticsEnabled)
            assertFalse(saved.volumeButtonsControlTv)
            assertEquals(NavigationMode.Directional, saved.navigationMode)

            tvs.sessionFor(id)!!.ready(setOf(RemoteKey.Up), pointer = true)
            settle()
            assertTrue(viewModel.state.value.pointerAvailable)
            viewModel.onNavigationMode(NavigationMode.Pointer)
            settle()
            assertEquals(
                NavigationMode.Pointer,
                preferences.interaction.first { it.navigationMode == NavigationMode.Pointer }.navigationMode,
            )

            tvs.sessionFor(id)!!.publish(SessionState.Reconnecting, pointer = true)
            settle()
            assertFalse(
                "stale pointer evidence is hidden while reconnecting",
                viewModel.state.value.pointerAvailable,
            )
            assertEquals(NavigationMode.Directional, viewModel.state.value.effectiveNavigationMode)
        }
}
