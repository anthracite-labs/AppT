package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.ForgetResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The S04 saved-pairing contract, driven through the production adapter ([SamsungTvsImpl] and
 * [LiveSession]) against recorded fixtures (Issue #91, docs/architecture/testing.md).
 *
 * The injected token values exist only inside the test process: a committed fixture carries the
 * `[fixture-token]` placeholder, and every assertion that proves a token was or was not placed on
 * the wire uses a value the test staged itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SamsungTvsSavedPairingTest {
    private val secrets = InMemorySamsungStore()

    private val tvId = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e")

    private val television =
        ConfirmedTelevision(
            id = tvId,
            host = "[host-a]",
            tls = true,
            adoptedChannel = true,
            uuid = tvId.value,
            displayName = "Living Room TV",
        )

    /** The saved token a test stages; never a committed fixture value. */
    private val savedToken = "resume-token-value-injected-by-the-test"

    /** The SPKI the pairing was made under; the scripted television keeps presenting it. */
    private val savedPin = "aa".repeat(32)


    @Test
    fun tokenResumeSendsTheSavedTokenWithoutAnotherApprovalPrompt() = runTest {
        stageSavedPairing()
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("token-resume"),
                certificateIdentity = savedPin,
            )
        val observedStates = mutableListOf<SessionState>()
        val session = LiveSession(television, transport, this, secrets)
        backgroundScope.launch { session.snapshot.collect { observedStates.add(it.state) } }

        advanceUntilIdle()

        assertEquals(SessionState.Ready, session.snapshot.value.state)
        assertFalse(
            "a resumed television never sees another approval prompt",
            observedStates.contains(SessionState.AwaitingTvApproval),
        )
        assertEquals("exactly one connection attempt", 1, transport.connects.size)
        assertTrue("the saved token was presented", transport.attemptedTokens.single())
        assertTrue(
            "the token rode the resumed URL",
            transport.attemptedUrls.single().contains("token=$savedToken"),
        )
        assertEquals("the saved identity matched", 0, transport.identityMismatches)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aTokenTheTelevisionReissuesReplacesTheStoredOneAtomically() = runTest {
        stageSavedPairing()
        val rotated = "rotated-token-value-injected-by-the-test"
        val rotatedFrame = """{"event":"ms.channel.connect","data":{"token":"$rotated"}}"""
        val transport =
            ScriptedSessionTransport(
                script(SessionEvent.Frame(0, rotatedFrame)),
                certificateIdentity = savedPin,
            )
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.Ready, session.snapshot.value.state)
        val (storedId, stored) = secrets.savedSecrets.single()
        assertEquals(tvId, storedId)
        assertEquals("the rotated token is the stored one", rotated, stored.token)
        assertEquals("the pin is unchanged by a rotation", savedPin, stored.pin)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aFailedRotatedTokenSaveSurfacesSecretsUnavailableInsteadOfConnecting() = runTest {
        stageSavedPairing()
        val rotated = "rotated-token-value-injected-by-the-test"
        val rotatedFrame = """{"event":"ms.channel.connect","data":{"token":"$rotated"}}"""
        val transport =
            ScriptedSessionTransport(
                script(SessionEvent.Frame(0, rotatedFrame)),
                certificateIdentity = savedPin,
            )
        secrets.failWrites = true
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertNull(
            "the secrets surface carries no repair reason",
            session.snapshot.value.repairReason,
        )
        assertTrue("the failed rotation stored nothing new", secrets.savedSecrets.isEmpty())
        val stored = secrets.loadSecret(tvId)
        assertTrue("the original pairing is still stored", stored is StoredSecret.Available)
        assertEquals(
            "the stored token is unchanged by the failed rotation",
            savedToken,
            (stored as StoredSecret.Available).secret.token,
        )

        session.close()
        advanceUntilIdle()
    }


    @Test
    fun identityMismatchDoesNotSendToken() = runTest {
        stageSavedPairing()
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("identity-mismatch"),
                presentedPin = "bb".repeat(32),
            )
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.IdentityChanged, session.snapshot.value.repairReason)
        assertEquals(1, transport.identityMismatches)
        assertTrue("the saved-pin handshake refused the connection", transport.sockets.isEmpty())
        assertTrue(
            "the recorded connection attempt contains no URL and therefore no token",
            transport.attemptedUrls.isEmpty(),
        )
        assertTrue("nothing was written", transport.sent.isEmpty())
        assertEquals(1, transport.connects.size)
        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun noPlaintextTokenAfterTlsPairing() = runTest {
        stageSavedPairing()
        val plaintextTelevision = television.copy(tls = false)
        val transport = ScriptedSessionTransport(script(prompt()))
        val session = LiveSession(plaintextTelevision, transport, this, secrets)
        advanceTimeBy(10)
        runCurrent()

        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)
        assertFalse(
            "a TLS-paired television never receives its saved token over plaintext",
            transport.attemptedUrls.single().contains("token="),
        )
        assertFalse(transport.attemptedTokens.single())

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aPlaintextUuidIdentityChangeFailsClosedBeforeConnecting() = runTest {
        stageSavedPairing()
        val changedUuid = "11111111-2222-4333-8444-555555555555"
        val plaintextTelevision = television.copy(tls = false, uuid = changedUuid)
        val transport = ScriptedSessionTransport(script(prompt()))
        val session = LiveSession(plaintextTelevision, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.IdentityChanged, session.snapshot.value.repairReason)
        assertTrue(
            "no socket was opened for a changed plaintext identity",
            transport.sockets.isEmpty(),
        )
        assertTrue(transport.attemptedUrls.isEmpty())

        session.close()
        advanceUntilIdle()
    }


    @Test
    fun unauthorizedWithTokenYieldsTokenRejectedAndNoLoop() = runTest {
        stageSavedPairing()
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("unauthorized-with-token"),
                certificateIdentity = savedPin,
            )
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.TokenRejected, session.snapshot.value.repairReason)
        assertEquals("one attempt, and it stops", 1, transport.connects.size)
        assertTrue("the saved token was the one presented", transport.attemptedTokens.single())
        assertTrue(transport.sockets.single().closed)

        session.retryApproval()
        advanceTimeBy(60_000)
        advanceUntilIdle()
        assertEquals(1, transport.connects.size)
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)

        session.close()
        advanceUntilIdle()
    }


    @Test
    fun approvalPersistsTheTokenAndPinAtomicallyWithTheDeviceRecord() = runTest {
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("tls-approval-then-volume"),
                certificateIdentity = savedPin,
            )
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        assertEquals(1, secrets.savedSecrets.size)
        assertEquals(1, secrets.savedDevices.size)
        val (storedId, stored) = secrets.savedSecrets.single()
        assertEquals(tvId, storedId)
        assertEquals(
            "the approval token is persisted with the pin",
            "[fixture-token]",
            stored.token,
        )
        assertEquals("the pin persisted is the candidate SPKI", savedPin, stored.pin)
        val record = secrets.loadDevice(tvId)!!
        assertEquals(tvId.value, record.uuid)
        assertEquals("[host-a]", record.lastAddress)
        assertTrue(record.tls)
        assertTrue(record.stableIdentity)
        val recordJson = DeviceRecordJson.encode(record)
        assertFalse(recordJson.contains("token"))
        assertFalse(recordJson.contains(savedPin))

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aPersistFailureIsFailClosedAndNotReady() = runTest {
        secrets.failWrites = true
        val transport = ScriptedSessionTransport(SessionFixture.load("tls-approval-then-volume"))
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(null, session.snapshot.value.repairReason)
        assertTrue("nothing was persisted", secrets.savedSecrets.isEmpty())

        session.close()
        advanceUntilIdle()
    }


    @Test
    fun corruptSecretDoesNotResetPairing() = runTest {
        stageSavedPairing()
        secrets.corruptSecret(tvId)
        val transport = ScriptedSessionTransport(SessionFixture.load("token-resume"))
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(
            "no repair reason: this is the SecretsUnavailable surface",
            null,
            session.snapshot.value.repairReason,
        )
        assertTrue("no connect happens on unreadable saved material", transport.connects.isEmpty())
        assertTrue(transport.attemptedUrls.isEmpty())

        session.close()
        advanceUntilIdle()
    }


    @Test
    fun confirmRepairDiscardsTheSecretThenPairsAsNew() = runTest {
        stageSavedPairing()
        val changedIdentityPin = "cc".repeat(32)
        val mismatched =
            ScriptedSessionTransport(
                SessionFixture.load("identity-mismatch"),
                presentedPin = changedIdentityPin,
            )
        val repaired =
            ScriptedSessionTransport(
                SessionFixture.load("tls-approval-then-volume"),
                certificateIdentity = changedIdentityPin,
            )
        var attempt = 0
        val transport =
            object : SessionTransport {
                override suspend fun connect(
                    television: ConfirmedTelevision,
                    saved: PairingSecret?,
                ): ConnectionAttempt {
                    attempt += 1
                    return if (attempt == 1) {
                        mismatched.connect(television, saved)
                    } else {
                        repaired.connect(television, saved)
                    }
                }
            }
        val session = LiveSession(television, transport, this, secrets)

        advanceUntilIdle()
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.IdentityChanged, session.snapshot.value.repairReason)

        session.confirmRepair()
        advanceUntilIdle()

        assertEquals(listOf(tvId), secrets.discarded)
        assertFalse("the re-pair connects without any saved token", repaired.attemptedTokens.last())
        assertEquals(SessionState.Ready, session.snapshot.value.state)
        val persisted = secrets.savedSecrets.last().second
        assertEquals(changedIdentityPin, persisted.pin)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun confirmRepairIsIgnoredForApprovalReasonsAndWhileHealthy() = runTest {
        val denied = ScriptedSessionTransport(script(prompt(), SessionEvent.Close(30)))
        val session = LiveSession(television, denied, this, secrets)
        advanceUntilIdle()
        assertEquals(RepairReason.ApprovalDenied, session.snapshot.value.repairReason)

        session.confirmRepair()
        advanceUntilIdle()

        assertEquals(
            "a denial needs retryApproval, not the confirmed re-pair",
            SessionState.NeedsRepair,
            session.snapshot.value.state,
        )
        assertEquals(RepairReason.ApprovalDenied, session.snapshot.value.repairReason)
        assertTrue("nothing was discarded", secrets.discarded.isEmpty())
        assertEquals("no new attempt started", 1, denied.connects.size)

        session.close()
        advanceUntilIdle()

        val healthy =
            ScriptedSessionTransport(
                SessionFixture.load("tls-approval-then-volume"),
                certificateIdentity = savedPin,
            )
        val ready = LiveSession(television, healthy, this, secrets)
        advanceUntilIdle()
        assertEquals(SessionState.Ready, ready.snapshot.value.state)
        ready.confirmRepair()
        advanceUntilIdle()
        assertEquals(SessionState.Ready, ready.snapshot.value.state)
        assertTrue(secrets.discarded.isEmpty())
        ready.close()
        advanceUntilIdle()
    }


    @Test
    fun forgetRemovesSecretAndIsIdempotent() = runTest {
        stageSavedPairing()
        val tvs = implWith(ScriptedSessionTransport(script(prompt())))

        assertEquals(ForgetResult.Forgotten, tvs.forget(tvId))
        assertEquals(StoredSecret.Absent, secrets.loadSecret(tvId))
        assertEquals(null, secrets.loadDevice(tvId))
        assertEquals("a second forget stays safe", ForgetResult.Forgotten, tvs.forget(tvId))
        assertEquals(0, secrets.rememberedIds().size)
    }

    @Test
    fun aFailedForgetReturnsFailedAndKeepsTheRelationship() = runTest {
        stageSavedPairing()
        secrets.failDeletion = true
        val tvs = implWith(ScriptedSessionTransport(script(prompt())))

        assertEquals(ForgetResult.Failed, tvs.forget(tvId))
        assertTrue("the relationship remains", secrets.loadSecret(tvId) != StoredSecret.Absent)
    }


    @Test
    fun supersededConnectCannotResurrectSession() = runTest {
        val store = InMemorySamsungStore()
        val transportA = ScriptedSessionTransport(script(prompt(100), approved(6_000)))
        val transportB = ScriptedSessionTransport(script(prompt(100), approved(200)))
        var created = 0
        val confirmed = ConfirmedTelevisions()
        confirmed.record(television)
        val tvs =
            SamsungTvsImpl(
                newScan = { error("no scan in this test") },
                confirmed = confirmed,
                secrets = store,
                newSession = { tv, scope, generation ->
                    if (created++ == 0) {
                        LiveSession(tv, transportA, scope, store, generation)
                    } else {
                        LiveSession(tv, transportB, scope, store, generation)
                    }
                },
            )

        val superseded = tvs.open(tvId, this)
        runCurrent()
        val replacement = tvs.open(tvId, this)
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(SessionState.Ready, replacement.snapshot.value.state)
        assertNotEquals(SessionState.Ready, superseded.snapshot.value.state)
        assertEquals("only the replacement persisted", 1, store.savedSecrets.size)
        assertEquals("only the replacement persisted a device record", 1, store.savedDevices.size)

        superseded.close()
        replacement.close()
        advanceUntilIdle()
    }

    @Test
    fun aLateApprovalAfterForgetPersistsNothing() = runTest {
        val store = InMemorySamsungStore()
        val transport = ScriptedSessionTransport(script(prompt(100), approved(1_000)))
        val confirmed = ConfirmedTelevisions()
        confirmed.record(television)
        val tvs =
            SamsungTvsImpl(
                newScan = { error("no scan in this test") },
                confirmed = confirmed,
                secrets = store,
                newSession = { tv, scope, generation ->
                    LiveSession(tv, transport, scope, store, generation)
                },
            )
        val session = tvs.open(tvId, this)
        runCurrent()

        assertEquals(ForgetResult.Forgotten, tvs.forget(tvId))
        advanceTimeBy(2_000)
        advanceUntilIdle()

        assertNotEquals(SessionState.Ready, session.snapshot.value.state)
        assertTrue(store.savedSecrets.isEmpty())
        assertTrue(store.savedDevices.isEmpty())
        assertEquals(StoredSecret.Absent, store.loadSecret(tvId))

        session.close()
        advanceUntilIdle()
    }


    @Test
    fun processDeathDoesNotForceRepair() = runTest {
        stageSavedPairing()
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("token-resume"),
                certificateIdentity = savedPin,
            )
        val confirmed = ConfirmedTelevisions()
        val tvs =
            SamsungTvsImpl(
                newScan = { error("no scan in this test") },
                confirmed = confirmed,
                secrets = secrets,
                newSession = { tv, scope, generation ->
                    LiveSession(tv, transport, scope, secrets, generation)
                },
            )
        assertEquals(setOf(tvId), tvs.rememberedIds())

        val session = tvs.open(tvId, this)
        advanceUntilIdle()

        assertEquals(SessionState.Ready, session.snapshot.value.state)
        assertEquals("[host-a]", transport.connects.single().host)
        assertTrue(transport.attemptedTokens.single())

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun rememberedCardsComeFromTheDurableStore() = runTest {
        val freshEvents = scanWith(InMemorySamsungStore(), "ssdp-tizen-tv")
        val freshCard = freshEvents.filterIsInstance<DiscoveryEvent.Found>().single().tv
        assertEquals(false, freshCard.remembered)
        assertEquals(ControlAvailability.NeedsPairing, freshCard.availability)

        stageSavedPairing()
        val rememberedEvents = scanWith(secrets, "ssdp-tizen-tv")
        val rememberedCard = rememberedEvents.filterIsInstance<DiscoveryEvent.Found>().single().tv
        assertEquals(true, rememberedCard.remembered)
        assertEquals(ControlAvailability.ReadyToOpen, rememberedCard.availability)
        val cardText = rememberedCard.toString()
        assertFalse(cardText.contains("[host-a]"))
        assertFalse(cardText.contains("token"))
        assertFalse(cardText.contains(savedPin))
    }

    @Test
    fun aPlantedTokenNeverReachesTheDeviceRecordOrTheCallerSurface() = runTest {
        stageSavedPairing()
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("token-resume"),
                certificateIdentity = savedPin,
            )
        val session = LiveSession(television, transport, this, secrets)
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        val encoded = DeviceRecordJson.encode(secrets.loadDevice(tvId)!!)
        assertFalse(encoded.contains(savedToken))
        assertFalse(encoded.contains(savedPin))
        assertFalse(transport.sent.any { it.contains(savedToken) })
        assertFalse(session.snapshot.value.toString().contains(savedToken))

        session.close()
        advanceUntilIdle()
    }


    private fun script(vararg events: SessionEvent): SessionFixture =
        SessionFixture("script", events.toList(), emptyList())

    private fun prompt(atMs: Long = 0): SessionEvent.Frame =
        SessionEvent.Frame(atMs, """{"event":"ms.channel.unauthorized"}""")

    private fun approved(atMs: Long): SessionEvent.Frame =
        SessionEvent.Frame(
            atMs,
            """{"event":"ms.channel.connect","data":{"token":"[fixture-token]"}}""",
        )

    /** Stages the durable pairing a reopened process would find. */
    private fun stageSavedPairing() {
        secrets.saveRawSecret(tvId, PairingSecret(token = savedToken, pin = savedPin))
        secrets.saveRawDevice(
            tvId,
            SamsungDeviceRecord(
                uuid = tvId.value,
                lastAddress = "[host-a]",
                tls = true,
                adoptedChannel = true,
                displayName = "Living Room TV",
                stableIdentity = true,
            ),
        )
    }

    private fun implWith(transport: SessionTransport): SamsungTvs {
        val confirmed = ConfirmedTelevisions()
        confirmed.record(television)
        return SamsungTvsImpl(
            newScan = { error("no scan in this test") },
            confirmed = confirmed,
            secrets = secrets,
            newSession = { tv, scope, generation ->
                LiveSession(tv, transport, scope, secrets, generation)
            },
        )
    }

    private suspend fun TestScope.scanWith(
        store: SamsungSecretStore,
        caseId: String,
    ): List<DiscoveryEvent> {
        val confirmed = ConfirmedTelevisions()
        val tvs =
            SamsungTvsImpl(
                newScan = {
                    DiscoveryScan(
                        FixtureTransport(Fixture.load(caseId)),
                        confirmed,
                        store,
                        readDispatcher = Dispatchers.Unconfined,
                    )
                },
                confirmed = confirmed,
                secrets = store,
            )
        val events = mutableListOf<DiscoveryEvent>()
        launch { tvs.discover().toList(events) }
        advanceUntilIdle()
        return events
    }
}
