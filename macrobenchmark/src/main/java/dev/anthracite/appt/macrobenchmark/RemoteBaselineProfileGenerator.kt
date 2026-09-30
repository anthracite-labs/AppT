package dev.anthracite.appt.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates the committed profile from AppT's real onboarding-to-Remote journey. */
@RunWith(AndroidJUnit4::class)
class RemoteBaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test
    fun remoteJourney() =
        baselineProfileRule.collect(packageName = RemoteJourney.PACKAGE_NAME) {
            with(RemoteJourney) { openReadyRemote() }
            checkNotNull(device.findObject(By.text(RemoteJourney.COMMAND_LABEL))).click()
            device.waitForIdle()
        }
}
