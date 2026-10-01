package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.CommandResult
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Debug-only bounded state for the approved five-interaction physical check. */
internal class DebugLatencyRun {
    private val samples = mutableListOf<Double>()
    private var warmedForNextMeasurement = false

    val canMeasure: Boolean
        get() = warmedForNextMeasurement && samples.size < REQUIRED_MEASUREMENTS

    val runCount: Int
        get() = samples.size

    fun clear() {
        samples.clear()
        warmedForNextMeasurement = false
    }

    /** One successful, unmeasured Volume Up command unlocks exactly one measured interaction. */
    fun recordWarmup(result: CommandResult): Boolean {
        if (samples.size >= REQUIRED_MEASUREMENTS) return false
        warmedForNextMeasurement = result == CommandResult.Accepted
        return warmedForNextMeasurement
    }

    /** Records only a successful local write and consumes the preceding warm-up. */
    fun recordMeasurement(result: CommandResult, elapsedMillis: Double): Boolean {
        if (!canMeasure) return false
        warmedForNextMeasurement = false
        if (result != CommandResult.Accepted || !elapsedMillis.isFinite() || elapsedMillis < 0.0) {
            return false
        }
        samples += elapsedMillis
        return true
    }

    /** A report exists only after precisely five successful warmed measurements. */
    fun report(
        candidateSha: String,
        phoneModel: String,
        androidVersion: String,
        apiLevel: Int,
    ): DebugLatencyReport? {
        if (samples.size != REQUIRED_MEASUREMENTS || !candidateSha.matches(SHA_PATTERN)) return null
        val boundedPhoneModel = phoneModel.singleLine(MAX_PHONE_MODEL_LENGTH)
        val boundedAndroidVersion = androidVersion.singleLine(MAX_ANDROID_VERSION_LENGTH)
        if (
            boundedPhoneModel.isBlank() ||
                boundedAndroidVersion.isBlank() ||
                apiLevel <= 0
        ) {
            return null
        }

        val sorted = samples.sorted()
        return DebugLatencyReport(
            candidateSha = candidateSha,
            phoneModel = boundedPhoneModel,
            androidVersion = boundedAndroidVersion,
            apiLevel = apiLevel,
            samplesMillis = samples.toList(),
            p50Millis = sorted[P50_NEAREST_RANK_INDEX],
            p95Millis = sorted[P95_NEAREST_RANK_INDEX],
        )
    }

    private fun String.singleLine(maxLength: Int): String =
        filterNot(Char::isISOControl).trim().take(maxLength)

    companion object {
        const val REQUIRED_MEASUREMENTS = 5
        private const val MAX_PHONE_MODEL_LENGTH = 80
        private const val MAX_ANDROID_VERSION_LENGTH = 32
        private const val P50_NEAREST_RANK_INDEX = 2
        private const val P95_NEAREST_RANK_INDEX = 4
        private val SHA_PATTERN = Regex("[0-9a-f]{40}")
    }
}

/** The bounded local report; no television or account identity is representable here. */
internal data class DebugLatencyReport(
    val candidateSha: String,
    val phoneModel: String,
    val androidVersion: String,
    val apiLevel: Int,
    val samplesMillis: List<Double>,
    val p50Millis: Double,
    val p95Millis: Double,
) {
    val runCount: Int
        get() = samplesMillis.size

    val p50Pass: Boolean
        get() = p50Millis <= P50_LIMIT_MILLIS

    fun clipboardText(): String =
        buildString {
            appendLine("Candidate SHA: $candidateSha")
            appendLine("Phone model: $phoneModel")
            appendLine("Android version / API: $androidVersion / $apiLevel")
            appendLine("Run count: $runCount")
            appendLine(
                "Latency samples (ms): " +
                    samplesMillis.joinToString(", ", transform = ::formatMillis)
            )
            appendLine("p50 (nearest rank, ms): ${formatMillis(p50Millis)}")
            appendLine("p95 (nearest rank, ms): ${formatMillis(p95Millis)}")
            appendLine(
                "Measurement: local UI command callback immediately before dispatch to " +
                    "session.command return after local socket write; TV acknowledgement and " +
                    "visible action excluded."
            )
            append("p50 <= 20 ms: ${if (p50Pass) "PASS" else "FAIL"}")
        }

    private fun formatMillis(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private companion object {
        const val P50_LIMIT_MILLIS = 20.0
    }
}

/**
 * Starts the clock synchronously in the click callback and ends it immediately after command
 * return.
 */
internal class DebugMeasuredCommand(private val nowNanos: () -> Long) {
    fun dispatch(
        scope: CoroutineScope,
        command: suspend () -> CommandResult,
        onComplete: (CommandResult, Double) -> Unit,
        onFailure: (Exception) -> Unit = {},
    ): Job {
        val startedAt = nowNanos()
        return scope.launch {
            try {
                val result = command()
                val completedAt = nowNanos()
                val elapsedMillis = (completedAt - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLI
                onComplete(result, elapsedMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onFailure(failure)
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
