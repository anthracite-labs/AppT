package dev.anthracite.appt.diagnostics

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The temporary S04 flight recorder's contract (Issue #91 correction cycle): disabled on a release
 * build, bounded storage, and reports that carry exception types and this codebase's frames —
 * never a message, an address, an identifier, or any other package's frames.
 */
@RunWith(RobolectricTestRunner::class)
class FlightRecorderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var application: Application
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun rememberState() {
        application = RuntimeEnvironment.getApplication()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun restoreState() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        FlightRecorder.configure(enabled = false, directory = null)
    }

    @Test
    fun aNonDebuggableBuildRecordsNothing() {
        FlightRecorder.configure(enabled = false, directory = temporaryFolder.root)
        FlightRecorder.record(FlightRecorder.Phase.ScanStarted)
        FlightRecorder.recordFailure(RuntimeException("token=SECRET host=10.0.0.5"))
        assertNull("no report exists on a release build", FlightRecorder.reportFile())
        assertNull(
            "no share intent exists on a release build",
            FlightRecorder.exportIntent(application),
        )
        assertTrue(temporaryFolder.root.listFiles().isNullOrEmpty())
    }

    @Test
    fun installIsInertOnANonDebuggableBuild() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        application.applicationInfo.flags = application.applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE.inv()
        FlightRecorder.install(application)
        assertEquals(
            "the system handler is untouched",
            before,
            Thread.getDefaultUncaughtExceptionHandler(),
        )
        assertFalse(FlightRecorder.enabled)
    }

    @Test
    fun storageStaysBoundedUnderAnEventFlood() {
        FlightRecorder.configure(enabled = true, directory = temporaryFolder.root)
        repeat(EVENT_FLOOD) { FlightRecorder.record(FlightRecorder.Phase.ScanStarted) }
        val report = FlightRecorder.reportFile()
        assertTrue("the report exists", report != null && report.isFile)
        assertTrue(
            "the report stays inside the cap (${report!!.length()} bytes)",
            report.length() <= FlightRecorder.CAP_BYTES,
        )
        val lines = report.readLines()
        assertTrue("some events survive the roll (${lines.size})", lines.size in 1..EVENT_FLOOD)
    }

    @Test
    fun aFailureRecordCarriesTypesAndApptFramesOnly() {
        FlightRecorder.configure(enabled = true, directory = temporaryFolder.root)
        val hostile = RuntimeException("token=SECRET-123 host=10.0.0.5 uuid=abc")
        hostile.stackTrace =
            arrayOf(
                StackTraceElement(
                    "dev.anthracite.appt.samsung.internal.SsdpClient",
                    "replies",
                    "SsdpClient.kt",
                    42,
                ),
                StackTraceElement("java.lang.Thread", "run", "Thread.java", -2),
            )
        hostile.initCause(IllegalStateException("ssid=HomeWifi mac=AA:BB:CC:DD:EE:FF"))
        FlightRecorder.recordFailure(hostile)

        val report = FlightRecorder.reportFile()!!.readText()
        assertTrue("the exception type is recorded", report.contains("java.lang.RuntimeException"))
        assertTrue(
            "this codebase's frames are recorded",
            report.contains(
                "dev.anthracite.appt.samsung.internal.SsdpClient.replies(SsdpClient.kt:42)",
            ),
        )
        assertFalse("no message is ever written", report.contains("SECRET-123"))
        assertFalse("no address is ever written", report.contains("10.0.0.5"))
        assertFalse("no identifier is ever written", report.contains("uuid=abc"))
        assertFalse("no cause message is ever written", report.contains("HomeWifi"))
        assertFalse("no foreign frame is ever written", report.contains("java.lang.Thread.run"))
    }

    @Test
    fun anUncaughtCrashIsRecordedAndThenForwarded() {
        val forwarded = AtomicInteger(0)
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> forwarded.incrementAndGet() }
        application.applicationInfo.flags =
            application.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        FlightRecorder.install(application)
        assertTrue(FlightRecorder.enabled)

        val hostile = CancellationException("ssid=HomeWifi host=10.0.0.9")
        hostile.stackTrace =
            arrayOf(
                StackTraceElement(
                    "dev.anthracite.appt.discovery.DiscoveryViewModel",
                    "startScan",
                    "DiscoveryViewModel.kt",
                    118,
                ),
            )
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), hostile)

        assertEquals("the system's own handling still runs", 1, forwarded.get())
        val report = FlightRecorder.reportFile()!!.readText()
        assertTrue("the crash record is present", report.contains("C "))
        assertTrue(
            "the crash type is recorded",
            report.contains("kotlinx.coroutines.CancellationException"),
        )
        assertTrue(
            "the crash frame is recorded",
            report.contains("DiscoveryViewModel.startScan(DiscoveryViewModel.kt:118)"),
        )
        assertFalse("no crash message is written", report.contains("HomeWifi"))
        assertFalse("no crash address is written", report.contains("10.0.0.9"))
        assertTrue(
            "the lifecycle events precede the crash record",
            report.indexOf("E ") < report.indexOf("C "),
        )

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        FlightRecorder.install(application)
        assertEquals(
            "installing twice never double-wraps",
            handler,
            Thread.getDefaultUncaughtExceptionHandler(),
        )
    }

    @Test
    fun theShareIntentTargetsTheSavedReport() {
        FlightRecorder.configure(enabled = true, directory = temporaryFolder.root)
        FlightRecorder.record(FlightRecorder.Phase.RouteDiscovery)
        val intent: Intent? = FlightRecorder.exportIntent(application)
        assertTrue("the report is shared as text", intent?.type == "text/plain")
        assertTrue(
            "the grant flags let the receiver read the report",
            (intent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
        )
    }

    private companion object {
        const val EVENT_FLOOD = 3_000
    }
}
