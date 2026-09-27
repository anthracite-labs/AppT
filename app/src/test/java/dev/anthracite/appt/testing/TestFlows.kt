package dev.anthracite.appt.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent

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
    backgroundScope.launch { state.collect {} }
}

/**
 * Runs everything the scheduler has pending at this moment, **including** the coroutines in
 * [TestScope.backgroundScope], and then whatever they made pending in turn.
 *
 * `advanceUntilIdle()` on its own does not drive those coroutines, and that is deliberate upstream
 * rather than a bug to work around. In kotlinx-coroutines 1.9.0 (`gradle/libs.versions.toml`),
 * `TestCoroutineScheduler.advanceUntilIdle()` is
 * `advanceUntilIdleOr { events.none(TestDispatchEvent<*>::isForeground) }`, and an event's
 * `isForeground` is `context[BackgroundWork] === null`. Everything `backgroundScope` launches
 * carries `BackgroundWork`, so `advanceUntilIdle` returns as soon as only background work is left —
 * which is exactly where these tests sit once they have published a session snapshot and are
 * waiting for the host's observer, or a route's `UiState` subscription, to consume it. The KDoc of
 * `TestScope.backgroundScope` states the same split: background coroutines "are run as usual when
 * using `advanceTimeBy` and `runCurrent`", while "`advanceUntilIdle` … will stop advancing the
 * virtual time once only the coroutines in this scope are left unprocessed".
 *
 * Hence the order: [runCurrent] executes the background hop, [advanceUntilIdle] then skips whatever
 * virtual time the remaining foreground work needs, and the second [runCurrent] picks up any
 * background work that foreground work scheduled on the way.
 *
 * [TestScopeSettleTest] pins this, so an upstream change to that contract fails a test here rather
 * than silently turning the session tests into assertions on stale snapshots.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle() {
    runCurrent()
    advanceUntilIdle()
    runCurrent()
}
