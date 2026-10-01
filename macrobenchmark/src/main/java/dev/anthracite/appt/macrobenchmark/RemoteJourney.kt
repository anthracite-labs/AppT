package dev.anthracite.appt.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** UI-only path shared by the profile generator and hosted command-path smoke benchmark. */
internal object RemoteJourney {
    const val PACKAGE_NAME = "dev.anthracite.appt"
    const val COMMAND_LABEL = "Volume up"
    const val BENCHMARK_TV_LABEL = "Benchmark TV"
    private const val SCREEN_TIMEOUT_MS = 15_000L
    private const val APPLICATION_ID = "dev.anthracite.appt"

    /** Starts from clean app-local state and follows the benchmark-only Ready Remote fixture. */
    fun MacrobenchmarkScope.openReadyRemote() {
        device.executeShellCommand("am force-stop $APPLICATION_ID")
        device.executeShellCommand("pm clear $APPLICATION_ID")
        pressHome()
        startActivityAndWait()
        clickText("Find my TV")
        clickText("Continue")
        clickText(BENCHMARK_TV_LABEL)

        check(device.wait(Until.hasObject(By.text(COMMAND_LABEL)), SCREEN_TIMEOUT_MS)) {
            "The deterministic benchmark Ready Remote did not appear."
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
