package dev.anthracite.appt.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.anthracite.appt.BuildConfig
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Engineering-only physical checkpoint, compiled only into the stock debug variant. */
@Composable
internal fun DebugS05VerifierAction(snapshot: ActiveRemoteSnapshot?, host: ActiveRemoteHost) {
    val ready =
        snapshot?.takeIf {
            host.current.value?.session === it.session &&
                it.snapshot.state == SessionState.Ready &&
                RemoteKey.VolumeUp in it.snapshot.capabilities.keys
        } ?: return
    val controller =
        remember(ready.session) { DebugS05ControllerRegistry.controllerFor(ready.session) }
    val context = LocalContext.current
    val hasExactBuildSha = BuildConfig.APPT_BUILD_SHA.matches(Regex("[0-9a-f]{40}"))

    Column(
        modifier = Modifier.fillMaxWidth().testTag(VERIFIER_ENTRY_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("S05 physical verifier")
        when (controller.phase) {
            DebugS05Phase.Inactive -> {
                if (hasExactBuildSha) {
                    Text("Uses the active Ready session and the normal Volume up control below.")
                    OutlinedButton(onClick = controller::start) { Text("Start verification") }
                } else {
                    Text(
                        "Exact build SHA unavailable. Install the exact-head hosted debug APK " +
                            "to report evidence."
                    )
                }
            }
            DebugS05Phase.AwaitingWarmup ->
                Text("Tap the normal Volume up control once for an unmeasured warm-up.")
            DebugS05Phase.AwaitingMeasurement ->
                Text(
                    "Warm-up complete. Tap the normal Volume up control for measured interaction " +
                        "${controller.sampleCount + 1} of ${DebugLatencyRun.REQUIRED_MEASUREMENTS}."
                )
            DebugS05Phase.BusyWarmup -> Text("Unmeasured warm-up in progress.")
            DebugS05Phase.BusyMeasurement -> Text("Measured Volume up interaction in progress.")
            DebugS05Phase.Failed -> {
                Text(
                    "${controller.sampleCount} of five successful measurements. " +
                        "No report was produced."
                )
                OutlinedButton(onClick = controller::restart) { Text("Restart verification") }
            }
            DebugS05Phase.Complete -> {
                val report = controller.report
                if (report == null) {
                    Text("The bounded result could not be formed. No report is available.")
                } else {
                    Text(
                        "Five interactions complete. p50 ${report.p50Millis} ms — " +
                            "${if (report.p50Pass) "PASS" else "FAIL"}."
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { copyReport(context, report) },
                            modifier = Modifier.testTag(COPY_RESULT_TAG),
                        ) {
                            Text("Copy verification result")
                        }
                        OutlinedButton(onClick = controller::restart) { Text("New run") }
                    }
                }
            }
        }
        if (
            controller.phase != DebugS05Phase.Inactive &&
                controller.phase != DebugS05Phase.BusyWarmup &&
                controller.phase != DebugS05Phase.BusyMeasurement &&
                controller.phase != DebugS05Phase.Complete &&
                controller.phase != DebugS05Phase.Failed
        ) {
            Text(
                "Measured interactions: ${controller.sampleCount} of " +
                    DebugLatencyRun.REQUIRED_MEASUREMENTS
            )
            Text("TV acknowledgement and visible action are outside the measurement.")
        }
    }
}

/**
 * Debug variant intercepts only an armed on-screen Volume up click; all other commands stay normal.
 */
internal fun DebugS05VerifierCommand(
    command: TvCommand,
    host: ActiveRemoteHost,
    tvId: TvId,
    scope: CoroutineScope,
    fallback: (TvCommand) -> Unit,
) {
    if (command !is TvCommand.Tap || command.key != RemoteKey.VolumeUp) {
        fallback(command)
        return
    }
    val session = host.current.value?.takeIf { it.tvId == tvId }?.session
    if (session == null) {
        fallback(command)
        return
    }
    val controller = DebugS05ControllerRegistry.existing(session)
    if (controller == null || !controller.isActive) {
        fallback(command)
        return
    }
    controller.onNormalVolumeUp(command, host, tvId, session, scope)
}

internal enum class DebugS05Phase {
    Inactive,
    AwaitingWarmup,
    AwaitingMeasurement,
    BusyWarmup,
    BusyMeasurement,
    Failed,
    Complete,
}

/** Run state is debug-only and never retains a session; the actual owner stays ActiveRemoteHost. */
internal class DebugS05Controller {
    private val run = DebugLatencyRun()
    private val measuredCommand = DebugMeasuredCommand(SystemClock::elapsedRealtimeNanos)

    var phase by mutableStateOf(DebugS05Phase.Inactive)
        private set

    var sampleCount by mutableIntStateOf(0)
        private set

    var report by mutableStateOf<DebugLatencyReport?>(null)
        private set

    val isActive: Boolean
        get() =
            phase == DebugS05Phase.AwaitingWarmup ||
                phase == DebugS05Phase.AwaitingMeasurement ||
                phase == DebugS05Phase.BusyWarmup ||
                phase == DebugS05Phase.BusyMeasurement

    fun start() {
        run.clear()
        sampleCount = 0
        report = null
        phase = DebugS05Phase.AwaitingWarmup
    }

    fun restart() = start()

    fun onNormalVolumeUp(
        command: TvCommand,
        host: ActiveRemoteHost,
        tvId: TvId,
        session: RemoteSession,
        scope: CoroutineScope,
    ) {
        if (!sameReadySession(host, tvId, session)) {
            phase = DebugS05Phase.Failed
            return
        }
        when (phase) {
            DebugS05Phase.AwaitingWarmup -> {
                phase = DebugS05Phase.BusyWarmup
                scope.launch {
                    try {
                        val result =
                            if (sameReadySession(host, tvId, session)) {
                                session.command(command)
                            } else {
                                CommandResult.Rejected(TvFailure.Unavailable)
                            }
                        phase =
                            if (run.recordWarmup(result)) {
                                DebugS05Phase.AwaitingMeasurement
                            } else {
                                DebugS05Phase.Failed
                            }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        phase = DebugS05Phase.Failed
                    }
                }
            }
            DebugS05Phase.AwaitingMeasurement -> {
                phase = DebugS05Phase.BusyMeasurement
                measuredCommand.dispatch(
                    scope = scope,
                    command = {
                        if (sameReadySession(host, tvId, session)) {
                            session.command(command)
                        } else {
                            CommandResult.Rejected(TvFailure.Unavailable)
                        }
                    },
                    onComplete = { result, elapsedMillis ->
                        if (!run.recordMeasurement(result, elapsedMillis)) {
                            phase = DebugS05Phase.Failed
                        } else {
                            sampleCount = run.runCount
                            if (sampleCount == DebugLatencyRun.REQUIRED_MEASUREMENTS) {
                                report =
                                    run.report(
                                        candidateSha = BuildConfig.APPT_BUILD_SHA,
                                        phoneModel = Build.MODEL.orEmpty(),
                                        androidVersion = Build.VERSION.RELEASE.orEmpty(),
                                        apiLevel = Build.VERSION.SDK_INT,
                                    )
                                phase =
                                    if (report == null) DebugS05Phase.Failed
                                    else DebugS05Phase.Complete
                            } else {
                                phase = DebugS05Phase.AwaitingWarmup
                            }
                        }
                    },
                    onFailure = { phase = DebugS05Phase.Failed },
                )
            }
            else -> Unit
        }
    }

    private fun sameReadySession(
        host: ActiveRemoteHost,
        tvId: TvId,
        session: RemoteSession,
    ): Boolean {
        val current = host.current.value ?: return false
        val snapshot = session.snapshot.value
        return current.tvId == tvId &&
            current.session === session &&
            current.snapshot.state == SessionState.Ready &&
            snapshot.state == SessionState.Ready &&
            RemoteKey.VolumeUp in snapshot.capabilities.keys
    }
}

/** Weak keys avoid creating a second session owner; controllers do not hold their session key. */
internal object DebugS05ControllerRegistry {
    private val controllers = java.util.WeakHashMap<RemoteSession, DebugS05Controller>()

    @Synchronized
    fun controllerFor(session: RemoteSession): DebugS05Controller =
        controllers.getOrPut(session) { DebugS05Controller() }

    @Synchronized fun existing(session: RemoteSession): DebugS05Controller? = controllers[session]

    /** The folded-in latency observation for one session, or null when no run produced one. */
    @Synchronized fun reportFor(session: RemoteSession): DebugLatencyReport? =
        controllers[session]?.report
}

private fun copyReport(context: Context, report: DebugLatencyReport) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, report.clipboardText()))
}

private const val VERIFIER_ENTRY_TAG = "appt:s05-physical-verifier-entry"
private const val COPY_RESULT_TAG = "appt:s05-physical-verifier-copy"
private const val CLIP_LABEL = "AppT verification result"
