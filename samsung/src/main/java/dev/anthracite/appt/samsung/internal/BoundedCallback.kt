package dev.anthracite.appt.samsung.internal

import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits for one platform callback for at most [timeout], and withdraws the request whenever the
 * wait ends without a result.
 *
 * [start] issues the request, reports its outcome through the `deliver` callback it is given
 * (`null` for a failed request), and returns the action that withdraws the request. That action
 * runs exactly when the wait ends without an outcome: at [timeout], or when the caller is cancelled
 * (for example because the scan reached its bound). A callback that arrives after that is ignored.
 *
 * @return the delivered outcome, or `null` on failure or timeout.
 */
internal suspend fun <T : Any> awaitCallback(
    timeout: Duration,
    start: (deliver: (T?) -> Unit) -> () -> Unit,
): T? =
    withTimeoutOrNull(timeout) {
        suspendCancellableCoroutine { continuation ->
            val withdraw = start { outcome ->
                if (continuation.isActive) continuation.resume(outcome)
            }
            continuation.invokeOnCancellation { withdraw() }
        }
    }
