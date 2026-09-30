package dev.anthracite.appt.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** UI-only path shared by the profile generator and the physical command-latency benchmark. */
internal object RemoteJourney {
    const val PACKAGE_NAME = "dev.anthracite.appt"
    const val COMMAND_LABEL = "Volume up"
    private const val SCREEN_TIMEOUT_MS = 15_000L
    private const val TV_APPROVAL_TIMEOUT_MS = 120_000L

    /** Finds the intended physical Samsung TV, waits for approval if needed, and reaches Ready. */
    fun MacrobenchmarkScope.openReadyRemote() {
        pressHome()
        startActivityAndWait()
        clickText("Find my TV")
        clickText("Continue")

        val tvCard = device.wait(Until.findObject(By.clickable(true)), SCREEN_TIMEOUT_MS)
        checkNotNull(tvCard) {
            "No controllable television card appeared. Keep the target Samsung TV on the same network."
        }.click()

        check(
            device.wait(Until.hasObject(By.text(COMMAND_LABEL)), TV_APPROVAL_TIMEOUT_MS)
        ) {
            "Remote did not reach Ready. Approve AppT on the television and retry the benchmark."
        }
        device.waitForIdle()
    }

    fun MacrobenchmarkScope.clickText(text: String) {
        val selector = By.text(text)
        check(device.wait(Until.hasObject(selector), SCREEN_TIMEOUT_MS)) {
            "Expected AppT screen action was not visible: $text"
        }
        checkNotNull(device.findObject(selector)).click()
    }
}
