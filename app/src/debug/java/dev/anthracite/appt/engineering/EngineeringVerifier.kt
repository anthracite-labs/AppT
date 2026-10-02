package dev.anthracite.appt.engineering

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.anthracite.appt.AppTApplication
import dev.anthracite.appt.BuildConfig
import dev.anthracite.appt.remote.DebugS05ControllerRegistry
import dev.anthracite.appt.samsung.SessionState
import java.io.File

/** Test tags for the single debug-only engineering surface. */
internal object EngineeringTestTags {
    const val ENTRY = "appt:engineering-entry"
    const val SURFACE = "appt:engineering-surface"
    const val SHA = "appt:engineering-sha"
    const val COUNTERS = "appt:engineering-counters"
    const val COPY = "appt:engineering-copy"
    const val SHARE = "appt:engineering-share"
    const val CLEAR = "appt:engineering-clear"

    fun scenario(id: String): String = "appt:engineering-scenario-$id"

    fun record(scenarioId: String, result: EngineeringResult): String =
        "appt:engineering-record-$scenarioId-${result.name}"
}

/**
 * The one debug-only verifier controller: the local ledger, its bounded file, and the report text.
 *
 * It owns no session and reaches no network. The only evidence it can hold is a catalogue scenario
 * id, the scenario's declared evidence class, one of three results and one of three notes.
 */
internal class EngineeringVerifierController(private val application: AppTApplication) {
    private val store =
        EngineeringStore(File(application.noBackupFilesDir, EngineeringStore.DIRECTORY))
    private val ledger = EngineeringLedger()
    private var observationsState by mutableStateOf(loadAndPrime())

    val observations: List<EngineeringObservation>
        get() = observationsState

    val candidateSha: String
        get() = BuildConfig.APPT_BUILD_SHA

    val exactSha: Boolean
        get() = candidateSha.matches(EngineeringReport.SHA_PATTERN)

    fun observationFor(scenarioId: String): EngineeringObservation? =
        observationsState.firstOrNull { it.scenarioId == scenarioId }

    fun summaryFor(checkpoint: String): EngineeringCheckpointSummary {
        val scenarios = EngineeringScenarios.forCheckpoint(checkpoint)
        val outcomes = scenarios.mapNotNull { ledger.observationFor(it.id)?.result }
        return EngineeringCheckpointSummary(
            checkpoint = checkpoint,
            status = ledger.statusOf(outcomes, scenarios.size),
            recorded = outcomes.size,
            total = scenarios.size,
        )
    }

    /** Records one observation; a refused record also means the file was not touched. */
    fun record(scenario: EngineeringScenario, result: EngineeringResult): Boolean {
        val observation =
            EngineeringObservation(
                scenarioId = scenario.id,
                evidenceClass = scenario.evidenceClass,
                result = result,
                note = EngineeringNote.defaultFor(result),
            )
        if (!ledger.record(observation)) return false
        observationsState = ledger.observations()
        return store.save(observationsState)
    }

    fun clear() {
        ledger.clear()
        observationsState = emptyList()
        store.clear()
    }

    fun report(counters: EngineeringCounters, latency: EngineeringLatencySummary?): String? =
        EngineeringReport.text(
            candidateSha = candidateSha,
            phoneModel = Build.MODEL.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            counters = counters,
            latency = latency,
            observations = observationsState,
        )

    private fun loadAndPrime(): List<EngineeringObservation> {
        store.load().forEach { observation -> ledger.record(observation) }
        return ledger.observations()
    }
}

/**
 * The single stock-debug Engineering Verifier entry, reachable from any top-level app state because
 * the running Activity composes it beside the graph. Opening it never navigates, never opens a
 * session and never bypasses the Remote-entry gate; scenarios that need a Ready television stay
 * disabled until the normal app reaches one.
 */
@Composable
internal fun EngineeringVerifierEntry(application: AppTApplication, modifier: Modifier = Modifier) {
    EngineeringObservationSource.ensureStarted(application.activeRemoteHost)
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { open = true },
        modifier = modifier.testTag(EngineeringTestTags.ENTRY),
    ) {
        Text("Engineering")
    }
    if (open) EngineeringVerifierDialog(application, onDismiss = { open = false })
}

@Composable
private fun EngineeringVerifierDialog(application: AppTApplication, onDismiss: () -> Unit) {
    val controller = remember { EngineeringVerifierController(application) }
    val active by application.activeRemoteHost.current.collectAsState()
    val counters by EngineeringObservationSource.counters.collectAsState()
    val latencyReport = active?.session?.let(DebugS05ControllerRegistry::reportFor)
    val latency =
        latencyReport?.let {
            EngineeringLatencySummary(
                p50Millis = it.p50Millis,
                p95Millis = it.p95Millis,
                samples = it.runCount,
                passed = it.p50Pass,
            )
        }
    val televisionReady = active?.snapshot?.state == SessionState.Ready

    // The S05 run is folded in: its bounded report becomes the S05 observation as soon as one
    // exists, and it can never be recorded from a build without the exact candidate SHA.
    LaunchedEffect(latencyReport, controller) {
        if (latencyReport != null && controller.exactSha) {
            val scenario = EngineeringScenarios.scenario(EngineeringScenarios.S05_LATENCY_ID)
            val result =
                if (latencyReport.p50Pass) {
                    EngineeringResult.PASS
                } else {
                    EngineeringResult.FAIL
                }
            scenario?.let { controller.record(it, result) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag(EngineeringTestTags.SURFACE)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("AppT engineering verification", style = MaterialTheme.typography.titleMedium)
                val shaText =
                    if (controller.exactSha) {
                        "Candidate SHA: ${controller.candidateSha}"
                    } else {
                        "Exact candidate SHA unavailable. Install the exact-head hosted debug " +
                            "APK to record evidence."
                    }
                Text(
                    shaText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(EngineeringTestTags.SHA),
                )
                val countersText =
                    "Observed: ${counters.sessionsObserved} sessions, " +
                        "${counters.readyTransitionsObserved} Ready transitions, " +
                        "${counters.reconnectionsObserved} reconnect states"
                Text(
                    countersText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(EngineeringTestTags.COUNTERS),
                )
                if (controller.exactSha) {
                    ReportActions(controller, counters, latency)
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    EngineeringScenarios.checkpoints.forEach { checkpoint ->
                        item(key = "checkpoint-$checkpoint") {
                            CheckpointHeader(controller.summaryFor(checkpoint))
                        }
                        items(
                            items = EngineeringScenarios.forCheckpoint(checkpoint),
                            key = { scenario -> scenario.id },
                        ) { scenario ->
                            ScenarioRow(
                                scenario = scenario,
                                observation = controller.observationFor(scenario.id),
                                televisionReady = televisionReady,
                                recordingEnabled = controller.exactSha,
                                onRecord = { result -> controller.record(scenario, result) },
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ReportActions(
    controller: EngineeringVerifierController,
    counters: EngineeringCounters,
    latency: EngineeringLatencySummary?,
) {
    val context = LocalContext.current
    val report = controller.report(counters, latency)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { report?.let { copyReport(context, it) } },
            enabled = report != null,
            modifier = Modifier.testTag(EngineeringTestTags.COPY),
        ) {
            Text("Copy report")
        }
        OutlinedButton(
            onClick = { report?.let { shareReport(context, it) } },
            enabled = report != null,
            modifier = Modifier.testTag(EngineeringTestTags.SHARE),
        ) {
            Text("Share report")
        }
        OutlinedButton(
            onClick = controller::clear,
            modifier = Modifier.testTag(EngineeringTestTags.CLEAR),
        ) {
            Text("Clear record")
        }
    }
}

@Composable
private fun CheckpointHeader(summary: EngineeringCheckpointSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(summary.checkpoint, style = MaterialTheme.typography.titleSmall)
        val progress = "${summary.status.label} (${summary.recorded}/${summary.total})"
        Text(progress, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ScenarioRow(
    scenario: EngineeringScenario,
    observation: EngineeringObservation?,
    televisionReady: Boolean,
    recordingEnabled: Boolean,
    onRecord: (EngineeringResult) -> Unit,
) {
    val readyDependentGap = scenario.requiresReadyTelevision && !televisionReady
    val ownerGate = scenario.evidenceClass == EngineeringEvidenceClass.EXTERNAL
    val recorded = observation?.result?.label ?: "not recorded"
    val guidanceText =
        if (readyDependentGap) {
            "Requires a Ready television: open Remote on the normal app first."
        } else {
            scenario.guidance
        }
    val passTag = EngineeringTestTags.record(scenario.id, EngineeringResult.PASS)
    val failTag = EngineeringTestTags.record(scenario.id, EngineeringResult.FAIL)
    val pendingTag = EngineeringTestTags.record(scenario.id, EngineeringResult.PENDING_EXTERNAL)
    val recordable = !readyDependentGap && recordingEnabled
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag(EngineeringTestTags.scenario(scenario.id)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val heading = "${scenario.id} — ${scenario.title}"
        Text(heading, style = MaterialTheme.typography.bodyMedium)
        val evidenceLine = "Evidence: ${scenario.evidenceClass.label} — $recorded"
        Text(evidenceLine, style = MaterialTheme.typography.labelSmall)
        Text(guidanceText, style = MaterialTheme.typography.bodySmall)
        if (ownerGate) {
            Text(
                "External gate: record pending here; a pass is attached to the program record " +
                    "only with the owner's evidence.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onRecord(EngineeringResult.PASS) },
                enabled = recordable && !ownerGate,
                modifier = Modifier.testTag(passTag),
            ) {
                Text("PASS")
            }
            OutlinedButton(
                onClick = { onRecord(EngineeringResult.FAIL) },
                enabled = recordable,
                modifier = Modifier.testTag(failTag),
            ) {
                Text("FAIL")
            }
            OutlinedButton(
                onClick = { onRecord(EngineeringResult.PENDING_EXTERNAL) },
                enabled = recordable,
                modifier = Modifier.testTag(pendingTag),
            ) {
                Text("Pending external")
            }
        }
    }
}

private fun copyReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, report))
}

private fun shareReport(context: Context, report: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
    context.startActivity(
        Intent.createChooser(send, CLIP_LABEL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private const val CLIP_LABEL = "AppT engineering report"
