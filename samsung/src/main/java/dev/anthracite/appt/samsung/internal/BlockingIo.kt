package dev.anthracite.appt.samsung.internal

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs blocking socket work on [dispatcher] and calls [onCancel] (which closes the socket) as soon
 * as the caller is cancelled.
 *
 * Blocking socket calls do not observe coroutine cancellation, so without this a device-info read
 * or an SSDP receive would keep a cancelled scan alive until its socket timeout, pushing `Finished`
 * past the bound and holding the multicast lock longer than the scan. Closing the socket makes the
 * blocked call fail immediately, and that failure is reported as the cancellation it is rather than
 * as a network error.
 */
internal suspend fun <T> blockingIo(
    dispatcher: CoroutineDispatcher,
    onCancel: () -> Unit,
    block: () -> T,
): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val watcher =
        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) onCancel()
            }
        }
    try {
        withContext(dispatcher) { block() }
    } catch (failed: IOException) {
        // After cancellation, the failure is the closed socket: surface the cancellation instead.
        ensureActive()
        throw failed
    } finally {
        finished.set(true)
        watcher.cancel()
    }
}
