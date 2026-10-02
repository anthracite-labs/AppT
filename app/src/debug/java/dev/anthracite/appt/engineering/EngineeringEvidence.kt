package dev.anthracite.appt.engineering

/**
 * The single stock-debug Engineering Verifier's typed evidence model
 * (docs/architecture/testing.md#single-stock-debug-engineering-verifier).
 *
 * Everything here is bounded and compile-time: a scenario is a fixed catalogue entry, a result is
 * one of three states, a note is one of three codes, and the report renders only these fields plus
 * the exact candidate SHA, bounded device metadata, a few counted app events and the S05 latency
 * numbers. There is deliberately no field, and no code path, that can carry a television address,
 * MAC, token, pin, certificate, purchase token, account identifier, entered text or any other
 * free-form payload off the device.
 */

/** Who produced the observed evidence. A SCRIPTED observation is never presented as real. */
internal enum class EngineeringEvidenceClass(val label: String) {
    SCRIPTED("SCRIPTED"),
    DEVICE("DEVICE"),
    PHYSICAL_TV("PHYSICAL TV"),
    EXTERNAL("EXTERNAL"),
}

/** The recorded outcome of one scenario. A pending external gate is not a pass. */
internal enum class EngineeringResult(val label: String) {
    PASS("PASS"),
    FAIL("FAIL"),
    PENDING_EXTERNAL("PENDING EXTERNAL"),
}

/**
 * The bounded note vocabulary. There is no free-text note, so an observation can never become a
 * payload, and the note only says how the observation was reached.
 */
internal enum class EngineeringNote(val label: String) {
    OBSERVED_AS_DOCUMENTED("Observed as documented"),
    OBSERVED_DIFFERENTLY("Observed, not as documented"),
    BLOCKED_ON_OWNER("Owner/provider action required"),
    ;

    companion object {
        /** The note a result carries; the record cannot say anything else. */
        fun defaultFor(result: EngineeringResult): EngineeringNote =
            when (result) {
                EngineeringResult.PASS -> OBSERVED_AS_DOCUMENTED
                EngineeringResult.FAIL -> OBSERVED_DIFFERENTLY
                EngineeringResult.PENDING_EXTERNAL -> BLOCKED_ON_OWNER
            }
    }
}

/** One guided device-side scenario of the S05–S17 program. */
internal data class EngineeringScenario(
    val id: String,
    val checkpoint: String,
    val title: String,
    val evidenceClass: EngineeringEvidenceClass,
    val guidance: String,
    /** Scenarios that need the normal app to hold a Ready television disable until it does. */
    val requiresReadyTelevision: Boolean = false,
)

/** One recorded observation of one scenario. */
internal data class EngineeringObservation(
    val scenarioId: String,
    val evidenceClass: EngineeringEvidenceClass,
    val result: EngineeringResult,
    val note: EngineeringNote,
)

/** Derived status of one checkpoint: PASS only when every scenario of it passed. */
internal enum class EngineeringCheckpointStatus(val label: String) {
    PASS("PASS"),
    FAIL("FAIL"),
    PENDING_EXTERNAL("PENDING EXTERNAL"),
    NOT_RECORDED("NOT RECORDED"),
}

internal data class EngineeringCheckpointSummary(
    val checkpoint: String,
    val status: EngineeringCheckpointStatus,
    val recorded: Int,
    val total: Int,
)

/** Counted app events the verifier observed; counts only, never an identifier. */
internal data class EngineeringCounters(
    val sessionsObserved: Int = 0,
    val readyTransitionsObserved: Int = 0,
    val reconnectionsObserved: Int = 0,
)

/** The S05 control-latency observation folded into this surface, once a run produced a report. */
internal data class EngineeringLatencySummary(
    val p50Millis: Double,
    val p95Millis: Double,
    val samples: Int,
    val passed: Boolean,
)

/** Every scenario the one bounded surface guides and records, in checkpoint order. */
internal object EngineeringScenarios {
    /** The S05 latency run folded into this surface; its report records the observation. */
    const val S05_LATENCY_ID = "s05.control-latency"

    val all: List<EngineeringScenario> =
        listOf(
            EngineeringScenario(
                id = "s05.control-latency",
                checkpoint = "S05",
                title = "Control latency, five warmed samples",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "On Remote with a Ready television, tap Volume up once for the warm-up and " +
                        "once per measured interaction, five times. The nearest-rank p50 must be " +
                        "at or under 20 ms.",
                requiresReadyTelevision = true,
            ),
            EngineeringScenario(
                id = "s06.activity-recreation",
                checkpoint = "S06",
                title = "Activity recreation keeps one session",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "With Remote open on a Ready television, rotate or resize the window. The " +
                        "session must survive, no second connection may open, and no gate prompt " +
                        "may appear.",
                requiresReadyTelevision = true,
            ),
            EngineeringScenario(
                id = "s06.background-return",
                checkpoint = "S06",
                title = "Background and quick return reuse the session",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Press Home, then return within about fifteen seconds: the same session is " +
                        "used, with no reconnect.",
                requiresReadyTelevision = true,
            ),
            EngineeringScenario(
                id = "s06.background-grace",
                checkpoint = "S06",
                title = "Grace closes the session after fifteen seconds",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Press Home and stay away for more than fifteen seconds: the session closes, " +
                        "and returning starts a new connection rather than reusing a socket.",
                requiresReadyTelevision = true,
            ),
            EngineeringScenario(
                id = "s06.lan-drop-recovery",
                checkpoint = "S06",
                title = "Real LAN drop recovers quietly",
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                guidance =
                    "On a real Samsung television, switch the phone's Wi-Fi off and on again " +
                        "while Remote is open: a quiet inline status, then recovery without a " +
                        "dialog.",
                requiresReadyTelevision = true,
            ),
            EngineeringScenario(
                id = "s06.physical-reconnect",
                checkpoint = "S06",
                title = "Real reconnect on the paired television",
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                guidance =
                    "On a real Samsung television, pair, send a command, interrupt the " +
                        "television's network, and confirm the session recovers without user " +
                        "action.",
                requiresReadyTelevision = true,
            ),
            EngineeringScenario(
                id = "s06.process-restart",
                checkpoint = "S06",
                title = "Process restart resumes the saved pairing",
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                guidance =
                    "Force-stop AppT and reopen on a television this phone already paired with: " +
                        "no new approval prompt appears.",
            ),
            EngineeringScenario(
                id = "s07.environment-isolation",
                checkpoint = "S07",
                title = "Three isolated environments deploy",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: dev, internal and production projects, rules and " +
                        "indexes deploy as versioned artifacts, and each build resolves only its " +
                        "own environment identifiers.",
            ),
            EngineeringScenario(
                id = "s07.signing-registration",
                checkpoint = "S07",
                title = "Signing and App Check registrations match the channel",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: the internal certificate is registered only in the " +
                        "internal project and the Play app-signing certificate only in " +
                        "production. Fingerprints are never written into this verifier.",
            ),
            EngineeringScenario(
                id = "s08.sign-in-handoff",
                checkpoint = "S08",
                title = "One real sign-in on a non-production environment",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Complete one real Google sign-in against the configured non-production " +
                        "environment and confirm the app path without a television ever being " +
                        "opened while the gate denies.",
            ),
            EngineeringScenario(
                id = "s08.functions-deploy-rollback",
                checkpoint = "S08",
                title = "Function deployment and rollback drill",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: production functions deploy with a traffic split " +
                        "from 0% and the rollback step is exercised once and recorded.",
            ),
            EngineeringScenario(
                id = "s08.trial-across-phones",
                checkpoint = "S08",
                title = "Trial follows the account across phones",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Sign in on a second phone: the same expiry comes back and that phone is " +
                        "marked trial-consumed.",
            ),
            EngineeringScenario(
                id = "s09.reopen-to-remote",
                checkpoint = "S09",
                title = "Reopen lands on the remembered television",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Reopen AppT with a remembered television: it lands on that Remote and " +
                        "starts connecting, through the licensing gate first.",
            ),
            EngineeringScenario(
                id = "s09.switch-and-forget",
                checkpoint = "S09",
                title = "Switch between televisions and forget one",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Use the switch sheet without a swipe gesture, then Forget one television " +
                        "behind its confirmation: it disappears from this phone, including its " +
                        "favourites, and the Active Remote is released through the normal path.",
            ),
            EngineeringScenario(
                id = "s10.scripted-billing-lifecycle",
                checkpoint = "S10",
                title = "Scripted Billing lifecycle over process death",
                evidenceClass = EngineeringEvidenceClass.SCRIPTED,
                guidance =
                    "With the explicitly selected debug Billing adapter, start a purchase, " +
                        "background or kill AppT while it is pending, reopen, and confirm one " +
                        "authoritative grant after the foreground requery. This stays SCRIPTED " +
                        "evidence, never provider truth.",
            ),
            EngineeringScenario(
                id = "s10.paid-path-drill",
                checkpoint = "S10",
                title = "Recorded paid-path drill on production",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: one real production purchase is verified and " +
                        "acknowledged, then refunded, exercising void and revocation, and " +
                        "leaving no entitlement behind.",
            ),
            EngineeringScenario(
                id = "s11.apps-and-text",
                checkpoint = "S11",
                title = "Launch an app and type with the phone keyboard",
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                guidance =
                    "On a real television, launch an app from the returned list and enter text " +
                        "with the phone keyboard when the television accepts it.",
            ),
            EngineeringScenario(
                id = "s11.favourites-edit",
                checkpoint = "S11",
                title = "Reorder a favourite",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Enter edit mode, reorder a favourite, and confirm the shelf and the More " +
                        "sheet still read one source.",
            ),
            EngineeringScenario(
                id = "s12.wake-attempt",
                checkpoint = "S12",
                title = "Recorded wake attempt",
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                guidance =
                    "On a real television that is off, with a stored MAC for the interface " +
                        "actually reached, press the power control and record whether the " +
                        "bounded wake burst woke it. An honest failure is a valid record.",
            ),
            EngineeringScenario(
                id = "s13.diagnostics-preview-clear",
                checkpoint = "S13",
                title = "Diagnostics preview, deliberate share, clear",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Open Privacy & Diagnostics, build a report, confirm every field is " +
                        "previewed before anything can leave the phone, share once deliberately, " +
                        "then clear local history.",
            ),
            EngineeringScenario(
                id = "s14.talkback-walkthrough",
                checkpoint = "S14",
                title = "TalkBack walkthrough of every route",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "With TalkBack on, complete Welcome, the explanation, discovery, approval, " +
                        "one command, the account gate, trial and purchase surfaces, settings " +
                        "and diagnostics.",
            ),
            EngineeringScenario(
                id = "s14.font-scale-and-windows",
                checkpoint = "S14",
                title = "Font scale and window sizes",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Repeat the walkthrough at 100% and 200% font scale and in compact, medium " +
                        "and expanded windows: nothing clips and control order is preserved.",
            ),
            EngineeringScenario(
                id = "s15.lifecycle-journeys",
                checkpoint = "S15",
                title = "Five guided lifecycle journeys",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Run the five documented journeys in order and keep each step trace and " +
                        "failure evidence.",
            ),
            EngineeringScenario(
                id = "s16.install-over-install",
                checkpoint = "S16",
                title = "Install over the previous artifact",
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                guidance =
                    "Install the source artifact with representative local state, then install " +
                        "this candidate over it: migration runs, and pairing and entitlement " +
                        "remain correct or fail through the documented recovery state.",
            ),
            EngineeringScenario(
                id = "s16.promotion-machinery",
                checkpoint = "S16",
                title = "Promotion machinery exists and stays off",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: the production-promotion workflow is a manual " +
                        "dispatch over the same uploaded bundle, staged rollout starts below " +
                        "100%, and it has not been run.",
            ),
            EngineeringScenario(
                id = "s17.physical-matrix-row",
                checkpoint = "S17",
                title = "One physical acceptance matrix row",
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                guidance =
                    "On a real Samsung television, record one matrix row from the exact-head " +
                        "debug APK: pair, command, reconnect, process-death resume, wake " +
                        "attempt, text, apps, pointer, and whether the pin survived reboot. " +
                        "Never record an address, MAC, token or account email.",
            ),
            EngineeringScenario(
                id = "s17.release-artifact-identity",
                checkpoint = "S17",
                title = "Distribution candidate derives from this source",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: the release artifact equivalence and signing " +
                        "evidence bind the distributable candidate to this same source revision.",
            ),
            EngineeringScenario(
                id = "s17.external-gates",
                checkpoint = "S17",
                title = "Human external gates",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Human decisions only: the final source-license choice and the Samsung " +
                        "vendor-terms review. The verifier can record the outcome but cannot " +
                        "make or approve either decision.",
            ),
            EngineeringScenario(
                id = "s17.paid-path-record",
                checkpoint = "S17",
                title = "Paid-path drill recorded before promotion",
                evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                guidance =
                    "Owner/provider record: the S10 production paid-path drill is complete, and " +
                        "production promotion has not been started by this checkpoint.",
            ),
        )

    val checkpoints: List<String> = all.map { it.checkpoint }.distinct()

    fun scenario(id: String): EngineeringScenario? = all.firstOrNull { it.id == id }

    fun forCheckpoint(checkpoint: String): List<EngineeringScenario> =
        all.filter { it.checkpoint == checkpoint }
}

/**
 * The bounded local record of verifier observations.
 *
 * The model cannot represent a forbidden value: an observation is a catalogue scenario id, its
 * declared evidence class, one of three results and one of three notes. The surface is one debug
 * screen, so the ledger locks only to keep a reader from seeing a half-updated map.
 */
internal class EngineeringLedger {
    private val recorded = linkedMapOf<String, EngineeringObservation>()

    /**
     * Stores one observation, or refuses one the model must never hold:
     * * an observation whose evidence class is not the scenario's declared class, so a DEVICE note
     *   can never stand in for PHYSICAL TV evidence; and
     * * a `PASS` for an `EXTERNAL` scenario, because a provider-console fact, a real purchase, a
     *   licence choice or a promotion is never a pass the verifier can produce. Those gates are
     *   recorded as `PENDING EXTERNAL`, and the program record carries the owner's evidence.
     */
    fun record(observation: EngineeringObservation): Boolean {
        val scenario = EngineeringScenarios.scenario(observation.scenarioId)
        val classMatches = scenario?.evidenceClass == observation.evidenceClass
        val ownerGateSelfApproved =
            observation.result == EngineeringResult.PASS &&
                scenario?.evidenceClass == EngineeringEvidenceClass.EXTERNAL
        if (!classMatches || ownerGateSelfApproved) return false
        synchronized(recorded) { recorded[observation.scenarioId] = observation }
        return true
    }

    fun observations(): List<EngineeringObservation> =
        synchronized(recorded) { recorded.values.toList() }

    fun observationFor(scenarioId: String): EngineeringObservation? =
        synchronized(recorded) { recorded[scenarioId] }

    fun isEmpty(): Boolean = synchronized(recorded) { recorded.isEmpty() }

    fun clear() = synchronized(recorded) { recorded.clear() }

    /** The per-checkpoint matrix derived from the recorded observations. */
    fun checkpointSummaries(): List<EngineeringCheckpointSummary> =
        EngineeringScenarios.checkpoints.map { checkpoint ->
            summarise(checkpoint, observations())
        }

    private fun summarise(
        checkpoint: String,
        observations: List<EngineeringObservation>,
    ): EngineeringCheckpointSummary {
        val scenarios = EngineeringScenarios.forCheckpoint(checkpoint)
        val byId = observations.associateBy(EngineeringObservation::scenarioId)
        val outcomes = scenarios.mapNotNull { byId[it.id]?.result }
        return EngineeringCheckpointSummary(
            checkpoint = checkpoint,
            status = statusOf(outcomes, scenarios.size),
            recorded = outcomes.size,
            total = scenarios.size,
        )
    }

    /** A checkpoint is PASS only when every scenario passed; any failure outranks a gate. */
    fun statusOf(
        outcomes: List<EngineeringResult>,
        total: Int,
    ): EngineeringCheckpointStatus =
        when {
            outcomes.any { it == EngineeringResult.FAIL } -> EngineeringCheckpointStatus.FAIL
            outcomes.any { it == EngineeringResult.PENDING_EXTERNAL } ->
                EngineeringCheckpointStatus.PENDING_EXTERNAL
            outcomes.size == total -> EngineeringCheckpointStatus.PASS
            else -> EngineeringCheckpointStatus.NOT_RECORDED
        }
}

/**
 * The bounded on-device text format for verifier evidence.
 *
 * One line per observation, `scenario-id<TAB>EVIDENCE-CLASS<TAB>RESULT`, all of them compile-time
 * tokens from the catalogue. Decoding drops any line that does not name a known scenario, or whose
 * class does not match the scenario's declared class, so a corrupted or hand-edited file can never
 * inject an observation or a payload.
 */
internal object EngineeringEvidenceFormat {
    const val RECORD_SEPARATOR = "\t"

    fun encode(observations: List<EngineeringObservation>): String =
        observations.joinToString("\n", transform = ::encodeLine)

    fun decode(text: String): List<EngineeringObservation> =
        text.lineSequence().mapNotNull(::decodeLine).distinctBy { it.scenarioId }.toList()

    private fun encodeLine(observation: EngineeringObservation): String =
        listOf(observation.scenarioId, observation.evidenceClass.name, observation.result.name)
            .joinToString(RECORD_SEPARATOR)

    private fun decodeLine(line: String): EngineeringObservation? {
        val parts = line.split(RECORD_SEPARATOR)
        if (parts.size != RECORD_FIELDS) return null
        val scenario = EngineeringScenarios.scenario(parts[0]) ?: return null
        val evidenceClass =
            EngineeringEvidenceClass.entries.firstOrNull { it.name == parts[1] } ?: return null
        val result = EngineeringResult.entries.firstOrNull { it.name == parts[2] } ?: return null
        if (scenario.evidenceClass != evidenceClass) return null
        return EngineeringObservation(
            scenarioId = scenario.id,
            evidenceClass = evidenceClass,
            result = result,
            note = EngineeringNote.defaultFor(result),
        )
    }

    private const val RECORD_FIELDS = 3
}

/** Renders the bounded, redacted engineering report an operator may explicitly copy or share. */
internal object EngineeringReport {
    val SHA_PATTERN = Regex("[0-9a-f]{40}")

    private const val MAX_PHONE_MODEL_LENGTH = 80
    private const val MAX_CHECKPOINT_ROWS = 20
    private const val MAX_OBSERVATION_ROWS = 40

    /**
     * The report text, or null when it would be dishonest to produce one: the candidate SHA must be
     * the full lowercase forty-character commit, and the device metadata must be usable.
     */
    fun text(
        candidateSha: String,
        phoneModel: String,
        apiLevel: Int,
        counters: EngineeringCounters,
        latency: EngineeringLatencySummary?,
        observations: List<EngineeringObservation>,
    ): String? {
        if (!candidateSha.matches(SHA_PATTERN)) return null
        val model = phoneModel.bounded()
        if (model.isBlank() || apiLevel <= 0) return null
        val ledger = EngineeringLedger()
        observations.forEach { observation -> ledger.record(observation) }
        return buildString {
            appendLine("AppT engineering verification report")
            appendLine("Candidate SHA: $candidateSha")
            appendLine("Phone model: $model")
            appendLine("API level: $apiLevel")
            appendLine("Sessions observed: ${counters.sessionsObserved}")
            appendLine("Ready transitions observed: ${counters.readyTransitionsObserved}")
            appendLine("Reconnection states observed: ${counters.reconnectionsObserved}")
            appendLine(latencyLine(latency))
            appendLine("Checkpoints:")
            ledger.checkpointSummaries().take(MAX_CHECKPOINT_ROWS).forEach { summary ->
                appendLine(
                    "  ${summary.checkpoint} ${summary.status.label} " +
                        "(${summary.recorded}/${summary.total} recorded)"
                )
            }
            appendLine("Observations:")
            if (observations.isEmpty()) {
                appendLine("  none recorded yet")
            } else {
                observations.take(MAX_OBSERVATION_ROWS).forEach { observation ->
                    appendLine(
                        "  ${observation.scenarioId} | ${observation.evidenceClass.label} | " +
                            "${observation.result.label} | ${observation.note.label}"
                    )
                }
            }
            append(
                "Boundary: this report holds no television address, MAC, token, pin, " +
                    "certificate, purchase token, account identifier, entered text or arbitrary " +
                    "payload, and it has not been uploaded anywhere."
            )
        }
    }

    private fun latencyLine(latency: EngineeringLatencySummary?): String =
        if (latency == null) {
            "S05 control latency: no bounded five-sample run yet"
        } else {
            val outcome = if (latency.passed) "PASS" else "FAIL"
            "S05 control latency: p50 ${latency.p50Millis.rounded()} ms / p95 " +
                "${latency.p95Millis.rounded()} ms over ${latency.samples} samples — $outcome"
        }

    private fun Double.rounded(): String = String.format(java.util.Locale.ROOT, "%.1f", this)

    private fun String.bounded(): String =
        filterNot(Char::isISOControl).trim().take(MAX_PHONE_MODEL_LENGTH)
}
