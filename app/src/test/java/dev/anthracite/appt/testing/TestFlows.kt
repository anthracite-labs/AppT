package dev.anthracite.appt.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/**
 * Keeps one collector alive on [state] for the rest of the test.
 *
 * A `StateFlow` built with `SharingStarted.WhileSubscribed` computes nothing until something
 * collects it, so a test that asserts on `state.value` would otherwise read the initial value and
 * pass or fail for the wrong reason. presentation.md deliberately shares each route's `UiState` for
 * five seconds, so the test subscribes rather than the production code switching to eager sharing.
 *
 * The collector is launched in [TestScope.backgroundScope], which `runTest` cancels when the test
 * finishes, so subscribing never becomes an unfinished coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.subscribeTo(state: StateFlow<T>) {
    backgroundScope.launch { state.collect { } }
}
