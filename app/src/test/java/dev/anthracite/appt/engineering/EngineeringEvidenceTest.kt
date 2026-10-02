package dev.anthracite.appt.engineering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bounded shape of the one debug-only verifier's evidence model
 * (docs/architecture/testing.md#single-stock-debug-engineering-verifier).
 */
class EngineeringEvidenceTest {
    @Test
    fun reportRequiresTheExactCandidateSha() {
        val observations = listOf(observation("s06.activity-recreation", EngineeringResult.PASS))

        assertNull(
            "a local build has no candidate revision to bind",
            reportOf("local", observations),
        )
        assertNull(
            "an abbreviated or non-lowercase revision is not the exact candidate",
            reportOf("0123456789ABCDEF0123456789ABCDEF01234567", observations),
        )

        val report = reportOf(EXACT_SHA, observations)

        assertTrue(report != null)
        assertTrue(report!!.contains("Candidate SHA: $EXACT_SHA"))
    }

    @Test
    fun reportBoundsDeviceMetadataAndHasNoFreeFormField() {
        val hostileModel = "\u0000" + "A".repeat(200) + "\nsecond line"
        val report =
            EngineeringReport.text(
                candidateSha = EXACT_SHA,
                phoneModel = hostileModel,
                apiLevel = 34,
                counters =
                    EngineeringCounters(
                        sessionsObserved = 2,
                        readyTransitionsObserved = 1,
                        reconnectionsObserved = 3,
                    ),
                latency = null,
                observations = emptyList(),
            )

        assertTrue(report != null)
        val text = report!!
        assertTrue("control characters never reach the report", text.controlFree())
        assertTrue("the model is truncated to the bounded length", !text.contains("A".repeat(81)))
        assertTrue(text.contains("Sessions observed: 2"))
        assertTrue(text.contains("Reconnection states observed: 3"))
        assertTrue(text.contains("S05 control latency: no bounded five-sample run yet"))
    }

    @Test
    fun latencySummaryIsFoldedIntoTheReport() {
        val report =
            reportOf(
                EXACT_SHA,
                listOf(observation("s05.control-latency", EngineeringResult.PASS)),
                EngineeringLatencySummary(
                    p50Millis = 19.5,
                    p95Millis = 21.25,
                    samples = 5,
                    passed = true,
                ),
            )

        assertTrue(report != null)
        assertTrue(report!!.contains("p50 19.5 ms / p95 21.3 ms over 5 samples — PASS"))
    }

    @Test
    fun anExternalGateIsNeverPassedByTheVerifierAlone() {
        val scenario = EngineeringScenarios.scenario("s17.external-gates")!!
        val ledger = EngineeringLedger()

        assertFalse(
            "the verifier must not self-approve an owner decision",
            ledger.record(
                EngineeringObservation(
                    scenarioId = scenario.id,
                    evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                    result = EngineeringResult.PASS,
                    note = EngineeringNote.OBSERVED_AS_DOCUMENTED,
                )
            ),
        )
        assertTrue(
            "the verifier may record that the gate is still pending",
            ledger.record(
                EngineeringObservation(
                    scenarioId = scenario.id,
                    evidenceClass = EngineeringEvidenceClass.EXTERNAL,
                    result = EngineeringResult.PENDING_EXTERNAL,
                    note = EngineeringNote.BLOCKED_ON_OWNER,
                )
            ),
        )
    }

    @Test
    fun everyResultHasExactlyOneNote() {
        assertEquals(
            EngineeringNote.OBSERVED_AS_DOCUMENTED,
            EngineeringNote.defaultFor(EngineeringResult.PASS),
        )
        assertEquals(
            EngineeringNote.OBSERVED_DIFFERENTLY,
            EngineeringNote.defaultFor(EngineeringResult.FAIL),
        )
        assertEquals(
            EngineeringNote.BLOCKED_ON_OWNER,
            EngineeringNote.defaultFor(EngineeringResult.PENDING_EXTERNAL),
        )
    }

    @Test
    fun ledgerRefusesAnObservationThatDoesNotMatchItsScenarioClass() {
        val ledger = EngineeringLedger()
        val physicalScenario = EngineeringScenarios.scenario("s06.lan-drop-recovery")!!
        val mismatched =
            EngineeringObservation(
                scenarioId = physicalScenario.id,
                evidenceClass = EngineeringEvidenceClass.DEVICE,
                result = EngineeringResult.PASS,
                note = EngineeringNote.OBSERVED_AS_DOCUMENTED,
            )

        assertFalse(
            "a DEVICE observation cannot stand in for a PHYSICAL TV one",
            ledger.record(mismatched),
        )
        assertNull(ledger.observationFor(physicalScenario.id))

        val matching =
            EngineeringObservation(
                scenarioId = physicalScenario.id,
                evidenceClass = EngineeringEvidenceClass.PHYSICAL_TV,
                result = EngineeringResult.PASS,
                note = EngineeringNote.OBSERVED_AS_DOCUMENTED,
            )
        assertTrue(ledger.record(matching))
        assertEquals(1, ledger.observations().size)
    }

    @Test
    fun encodedEvidenceRoundTripsAndDropsAnythingUnexpected() {
        val observations =
            listOf(
                observation("s06.activity-recreation", EngineeringResult.PASS),
                observation("s06.lan-drop-recovery", EngineeringResult.PENDING_EXTERNAL),
            )

        assertEquals(
            observations,
            EngineeringEvidenceFormat.decode(EngineeringEvidenceFormat.encode(observations)),
        )
        assertTrue(EngineeringEvidenceFormat.decode("").isEmpty())
        assertTrue(EngineeringEvidenceFormat.decode("not-a-record").isEmpty())
        assertTrue(EngineeringEvidenceFormat.decode("unknown.scenario\tDEVICE\tPASS").isEmpty())
        assertTrue(
            "a record may not claim a class the scenario does not have",
            EngineeringEvidenceFormat.decode("s06.activity-recreation\tPHYSICAL_TV\tPASS").isEmpty(),
        )
        assertTrue(
            "a record may not invent a result",
            EngineeringEvidenceFormat.decode("s06.activity-recreation\tDEVICE\tMAYBE").isEmpty(),
        )
    }

    @Test
    fun checkpointStatusOnlyPassesWhenEveryScenarioPassed() {
        val ledger = EngineeringLedger()

        assertEquals(
            EngineeringCheckpointStatus.NOT_RECORDED,
            ledger.statusOf(emptyList(), total = 3),
        )
        assertEquals(
            EngineeringCheckpointStatus.PASS,
            ledger.statusOf(
                listOf(EngineeringResult.PASS, EngineeringResult.PASS, EngineeringResult.PASS),
                total = 3,
            ),
        )
        assertEquals(
            EngineeringCheckpointStatus.FAIL,
            ledger.statusOf(listOf(EngineeringResult.PASS, EngineeringResult.FAIL), total = 3),
        )
        assertEquals(
            EngineeringCheckpointStatus.PENDING_EXTERNAL,
            ledger.statusOf(
                listOf(EngineeringResult.PASS, EngineeringResult.PENDING_EXTERNAL),
                total = 3,
            ),
        )
    }

    @Test
    fun catalogueCoversEveryProgramCheckpoint() {
        assertEquals(
            listOf(
                "S05",
                "S06",
                "S07",
                "S08",
                "S09",
                "S10",
                "S11",
                "S12",
                "S13",
                "S14",
                "S15",
                "S16",
                "S17",
            ),
            EngineeringScenarios.checkpoints,
        )
        val ids = EngineeringScenarios.all.map { it.id }
        assertEquals("scenario ids are unique", ids.size, ids.distinct().size)
        assertTrue(
            "every scenario carries a title and guidance",
            EngineeringScenarios.all.all { it.title.isNotBlank() && it.guidance.isNotBlank() },
        )
    }

    private fun reportOf(
        sha: String,
        observations: List<EngineeringObservation>,
        latency: EngineeringLatencySummary? = null,
    ): String? =
        EngineeringReport.text(
            candidateSha = sha,
            phoneModel = "Pixel under test",
            apiLevel = 34,
            counters = EngineeringCounters(),
            latency = latency,
            observations = observations,
        )

    private fun observation(scenarioId: String, result: EngineeringResult): EngineeringObservation =
        EngineeringObservation(
            scenarioId = scenarioId,
            evidenceClass = EngineeringScenarios.scenario(scenarioId)!!.evidenceClass,
            result = result,
            note = EngineeringNote.defaultFor(result),
        )

    private fun String.controlFree(): Boolean =
        filterNot { it == '\n' || it == '\r' }.none(Char::isISOControl)

    private companion object {
        const val EXACT_SHA = "0123456789abcdef0123456789abcdef01234567"
    }
}
