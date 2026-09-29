package dev.anthracite.appt.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * TEMPORARY S04 diagnostic (Issue #91 physical-hardware correction cycle): a debug-only flight
 * recorder for the unexplained discovery crash on the physical device. NOT product scope; removed
 * again before terminal S04 verification unless the control plane revises the contract.
 *
 * Properties the correction cycle demands, and how this object meets them:
 *
 *  * debug-only: enabled only when the installed build is debuggable
 *    ([ApplicationInfo.FLAG_DEBUGGABLE]). Every entry point returns immediately when disabled, so a
 *    release build behaves exactly as before and writes nothing.
 *  * local-only: events go to one app-private file under `filesDir/diagnostics`. No network, no
 *    analytics, no SDK, no logcat — and nothing leaves the device until the user explicitly
 *    shares the report through the debug-only export action.
 *  * bounded rolling storage: appends are capped; once the file passes [CAP_BYTES] the oldest half
 *    is dropped and the remainder rewritten atomically, so storage cannot grow with use.
 *  * crash-safe: the uncaught-exception record is written and flushed (`FileDescriptor.sync`)
 *    before the previous handler runs, so pre-crash events survive process death.
 *  * redacted by construction: events are enum names; a failure record carries only exception
 *    type names and stack frames of this codebase (`dev.anthracite.`), with file and line. No
 *    message, cause message, address, identifier, token, or frame of any other package is ever
 *    written. See [summarize].
 *
 * The file format is one line per record: `E <millis> <EVENT>` for lifecycle/discovery events,
 * `F <millis> <type> | frame | frame …` for failures, `C <millis> …` for the uncaught crash.
 */
internal object FlightRecorder {

    /** Allowlisted lifecycle and discovery events (no free-form values, ever). */
    enum class Phase {
        AppCreate,
        RouteWelcome,
        RouteLocalNetwork,
        RouteDiscovery,
        RoutePairing,
        RouteRemote,
        ScanStarted,
        ScanResumed,
        ScanStopped,
        ScanFailed,
        GateDenied,
        TvSelected,
    }

    /** Set by [install]; false means every entry point is a no-op. */
    internal var enabled: Boolean = false
        private set

    private var directory: File? = null

    private const val DIRECTORY_NAME = "diagnostics"
    private const val REPORT_NAME = "appt-flight.txt"
    private const val PARTIAL_SUFFIX = ".partial"

    /** Storage bounds: half a file of history at most, rewritten atomically. */
    internal const val CAP_BYTES = 48L * 1024L
    private const val TAIL_BYTES = 24 * 1024

    /** Redaction bounds: appt frames per cause, causes per record. */
    private const val FRAME_CAP = 10
    private const val CAUSE_DEPTH = 3
    private const val APPT_PREFIX = "dev.anthracite."

    /**
     * Installs the recorder when the build is debuggable: prepares the app-private directory and
     * wraps the default uncaught-exception handler so the crash record is written before the
     * system's own handling proceeds. Never runs twice.
     */
    fun install(application: Application) {
        val debuggable =
            (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        install(application = application, debuggable = debuggable)
    }

    /** The install decision, explicit for tests; production callers use [install]. */
    internal fun install(application: Application, debuggable: Boolean) {
        if (!debuggable) return
        if (Thread.getDefaultUncaughtExceptionHandler() is CrashForwarder) return
        enabled = true
        directory = File(application.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        Thread.setDefaultUncaughtExceptionHandler(
            CrashForwarder(previous = Thread.getDefaultUncaughtExceptionHandler()),
        )
        record(Phase.AppCreate)
    }

    /** Test seam: full reset, including unwinding this recorder's crash handler. */
    internal fun resetForTest() {
        enabled = false
        directory = null
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        if (handler is CrashForwarder) {
            Thread.setDefaultUncaughtExceptionHandler(handler.previous)
        }
    }

    /** Test seam: direct configuration. Production code only ever goes through [install]. */
    internal fun configure(active: Boolean, storage: File?) {
        enabled = active
        directory = storage
    }

    /** Records one allowlisted lifecycle or discovery event. A no-op unless debuggable. */
    fun record(phase: Phase) {
        if (!enabled) return
        write("E ${System.currentTimeMillis()} ${phase.name}")
    }

    /**
     * Records a caught operation failure: the exception type chain and this codebase's stack
     * frames. The exception itself keeps propagating in the caller; this only observes it.
     */
    fun recordFailure(throwable: Throwable) {
        if (!enabled) return
        write("F ${System.currentTimeMillis()} ${summarize(throwable)}")
    }

    /**
     * The share intent for the saved report, or null when there is nothing to share or the build
     * is not the debuggable diagnostic build. The report leaves the device only through this
     * explicit user-driven path.
     */
    fun exportIntent(context: Context): Intent? {
        if (!enabled) return null
        val file = reportFile() ?: return null
        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.debug.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The report file, or null when no report exists yet. */
    internal fun reportFile(): File? = directory?.let { dir ->
        File(dir, REPORT_NAME).takeIf { it.isFile }
    }

    private fun write(line: String) {
        val dir = directory ?: return
        try {
            synchronized(this) {
                rollIfNeeded(dir)
                FileOutputStream(File(dir, REPORT_NAME), true).use { output ->
                    output.write((line + "\n").encodeToByteArray())
                    output.fd.sync()
                }
            }
        } catch (ignored: IOException) {
            // Diagnostics must never become the failure. A closed or full store loses records.
        }
    }

    /** Keeps storage bounded: past [CAP_BYTES], keep only the newer [TAIL_BYTES]. */
    private fun rollIfNeeded(dir: File) {
        val file = File(dir, REPORT_NAME)
        if (file.length() <= CAP_BYTES) return
        val bytes = file.readBytes()
        val tail = bytes.copyOfRange(bytes.size - TAIL_BYTES, bytes.size)
        val firstBreak = tail.indexOf('\n'.code.toByte())
        val kept = if (firstBreak >= 0) tail.copyOfRange(firstBreak + 1, tail.size) else tail
        val partial = File(dir, REPORT_NAME + PARTIAL_SUFFIX)
        FileOutputStream(partial, false).use { output -> output.write(kept) }
        if (!partial.renameTo(file)) {
            file.delete()
            partial.renameTo(file)
        }
    }

    /**
     * The exception type chain with this codebase's frames — never a message, and never a frame
     * from another package, so no network or device data can reach the report through a throwable.
     */
    private fun summarize(throwable: Throwable): String {
        val parts = mutableListOf<String>()
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < CAUSE_DEPTH) {
            parts.add(cause.javaClass.name)
            cause.stackTrace
                .filter { it.className.startsWith(APPT_PREFIX) }
                .take(FRAME_CAP)
                .forEach { frame ->
                    val location = frame.fileName ?: "?"
                    val entry =
                        "${frame.className}.${frame.methodName}($location:${frame.lineNumber})"
                    parts.add("| $entry")
                }
            cause = cause.cause
            depth += 1
        }
        return parts.take(CAUSE_DEPTH * (FRAME_CAP + 1)).joinToString(" ")
    }

    /** Forwards the crash to this recorder's report, then to the system's own handler. */
    private class CrashForwarder(val previous: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                if (enabled) {
                    write("C ${System.currentTimeMillis()} ${summarize(throwable)}")
                }
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}
