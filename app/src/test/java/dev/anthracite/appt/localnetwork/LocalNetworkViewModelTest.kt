package dev.anthracite.appt.localnetwork

import dev.anthracite.appt.AppSettingsLauncher
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.testing.FakePermissionGate
import dev.anthracite.appt.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalNetworkViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val gate = FakePermissionGate()
    private var settingsOpened = 0
    // Created lazily, inside each test, after the rule has installed the Main dispatcher.
    private val viewModel by lazy {
        LocalNetworkViewModel(gate, AppSettingsLauncher { settingsOpened++ })
    }

    @Test
    fun explainsFirstThenContinueGrants() =
        runTest(mainRule.dispatcher) {
            assertEquals(
                LocalNetworkUiState(LocalNetworkPhase.Explain, canOpenAppSettings = false),
                viewModel.state.value,
            )
            viewModel.onContinue()
            runCurrent()
            assertEquals(
                LocalNetworkUiState(LocalNetworkPhase.Granted, canOpenAppSettings = false),
                viewModel.state.value,
            )
            assertEquals(1, gate.acknowledgeCalls)
        }

    @Test
    fun deniedOffersRetryAndSettings() =
        runTest(mainRule.dispatcher) {
            gate.reportDenied()
            runCurrent()
            assertEquals(
                LocalNetworkUiState(LocalNetworkPhase.Denied, canOpenAppSettings = true),
                viewModel.state.value,
            )

            viewModel.onOpenSettings()
            assertEquals(1, settingsOpened)

            viewModel.onRetry()
            runCurrent()
            assertEquals(LocalNetworkPhase.Granted, viewModel.state.value.phase)
        }
}
