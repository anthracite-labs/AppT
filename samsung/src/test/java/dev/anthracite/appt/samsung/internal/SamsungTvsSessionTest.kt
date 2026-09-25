package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The S03 session contract, driven through the production adapter ([SamsungTvsImpl] and
 * [LiveSession]) against recorded fixtures (Issue #79, docs/architecture/testing.md).
 *
 * Time is virtual, so the 45-second approval wait is really 45 seconds of virtual time. Every
 * session here is released with [RemoteSession.close]; `runTest` fails if one were left running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SamsungTvsSessionTest {
    private val television =
        ConfirmedTelevision(
            TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"),
            "[host-a]",
            tls = true,
            adoptedChannel = true,
        )

    private val unauthorizedFrame = """{"event":"ms.channel.unauthorized"}"""
    private val connectedFrame =
        """{"event":"ms.channel.connect","data":{"token":"[fixture-token]"}}"""

    private fun prompt(atMs: Long = 0): SessionEvent.Frame =
        SessionEvent.Frame(atMs, unauthorizedFrame)

    private fun approved(atMs: Long = 0): SessionEvent.Frame =
        SessionEvent.Frame(atMs, connectedFrame)

    private fun ends(atMs: Long): SessionEvent.Close = SessionEvent.Close(atMs)

    private fun script(vararg events: SessionEvent): SessionFixture =
        SessionFixture("script", events.toList(), emptyList())

    /** One session on the scripted adapter, launched in the test's own scope. */
    private fun TestScope.session(
        fixture: SessionFixture,
        refusesConnection: Boolean = false,
        approvalWait: Duration = 45.seconds,
    ): Pair<LiveSession, ScriptedSessionTransport> {
        val transport = ScriptedSessionTransport(fixture, refusesConnection = refusesConnection)
        return LiveSession(television, transport, this, approvalWait) to transport
    }

    private fun productionSources(): List<File> {
        val root = File("src/main")
        val from = root.absolutePath
        assertTrue("expected to run from the samsung module, ran from $from", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** A `SamsungTvs` whose scan reads [caseId] and whose sessions use [newSession]. */
    private fun samsungTvs(
        caseId: String,
        newSession: (ConfirmedTelevision, CoroutineScope) -> RemoteSession,
    ): SamsungTvs {
        val confirmed = ConfirmedTelevisions()
        return SamsungTvsImpl(
            newScan = { DiscoveryScan(FixtureTransport(Fixture.load(caseId)), confirmed) },
            confirmed = confirmed,
            newSession = newSession,
        )
    }

    private fun cloudArtifacts(): List<String> =
        System.getProperty("java.class.path").orEmpty().split(File.pathSeparator).filter { entry ->
            FORBIDDEN_CLOUD_ARTIFACTS.any { entry.lowercase().contains(it) }
        }

    private fun linesMatching(source: File, pattern: Regex): List<String> =
        source
            .readLines()
            .mapIndexed { index, line -> "${source.path}:${index + 1}" to line }
            .filter { (_, line) -> pattern.containsMatchIn(line) }
            .map { (at, _) -> at }

    // --- first contact ---------------------------------------------------------------

    @Test
    fun firstContactPromptsForApprovalThenReachesReady() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))

        advanceTimeBy(10)
        runCurrent()
        val prompting = session.snapshot.value.state
        assertEquals(SessionState.AwaitingTvApproval, prompting)

        advanceTimeBy(300)
        runCurrent()
        val ready = session.snapshot.value.state
        assertEquals(SessionState.Ready, ready)
        val adopted = transport.connects.single()
        assertTrue("the adopted TLS channel was preferred", adopted.tls)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun tlsApprovalThenVolumeWritesTheVolumeFrameOnTheSameSocket() = runTest {
        val fixture = SessionFixture.load("tls-approval-then-volume")
        val (session, transport) = session(fixture)
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        advanceUntilIdle()

        assertEquals(CommandResult.Accepted, result)
        val expected = fixture.expectedOutbound.map { it.second }
        assertEquals("the fixture's volume frame", expected, transport.sent)
        assertEquals("one socket for approval and command", 1, transport.sockets.size)
        assertEquals("one connection attempt", 1, transport.connects.size)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun commandNeverOpensASecondSocket() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceUntilIdle()
        val socketsBefore = transport.sockets.size

        val results = (1..6).map { session.command(TvCommand.Tap(RemoteKey.VolumeDown)) }
        advanceUntilIdle()

        assertEquals(socketsBefore, transport.sockets.size)
        assertEquals(6, transport.sent.size)
        assertEquals(List(6) { CommandResult.Accepted }, results)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun commandsBeforeReadyAreRejectedAndWriteNothing() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceTimeBy(10)
        runCurrent()
        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        advanceUntilIdle()

        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        assertEquals("nothing is queued for later replay", emptyList<String>(), transport.sent)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun commandsBeforeThePromptAreRejectedAndWriteNothing() = runTest {
        val (session, transport) = session(script(prompt(2_000)))
        advanceTimeBy(100)
        runCurrent()
        assertEquals(SessionState.Connecting, session.snapshot.value.state)

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        advanceUntilIdle()

        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        assertEquals(emptyList<String>(), transport.sent)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aRefusedSocketIsUnreachableAndOpensNoConnectionForACommand() = runTest {
        val (session, transport) =
            session(SessionFixture.load("tls-approval-then-volume"), refusesConnection = true)
        advanceUntilIdle()
        assertEquals(SessionState.Unreachable, session.snapshot.value.state)

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        assertEquals(emptyList<String>(), transport.sent)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aDeniedApprovalNeedsRepairAndCanBeRetried() = runTest {
        val (session, transport) = session(script(prompt(), ends(30)))
        advanceUntilIdle()
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.ApprovalDenied, session.snapshot.value.repairReason)

        // `advanceUntilIdle` would run the approval wait out on the fresh attempt as well, so the
        // retry is observed on the virtual clock instead.
        session.retryApproval()
        advanceTimeBy(10)
        runCurrent()
        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)
        assertEquals("the retry costs exactly one socket", 2, transport.sockets.size)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun anUnansweredApprovalTimesOutAndCanBeRetried() = runTest {
        // The first attempt is never answered. The second one is, inside its own approval wait:
        // each attempt replays its own script from the moment its socket opens.
        val transport =
            ScriptedSessionTransport(script(prompt()), script(prompt(), approved(10_000)))
        val session = LiveSession(television, transport, this)
        advanceTimeBy(46_000)
        advanceUntilIdle()
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.ApprovalTimedOut, session.snapshot.value.repairReason)
        // The unanswered prompt gives up the socket it was waiting on.
        assertEquals(listOf(true), transport.sockets.map { it.closed })

        session.retryApproval()
        advanceTimeBy(10)
        runCurrent()
        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)

        // The television answers this time, on the fresh attempt the retry started. Running the
        // clock out is safe here: the answer cancels the approval wait before it expires.
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)
        assertEquals("the retry costs exactly one socket", 2, transport.sockets.size)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun anUnansweredApprovalDoesNotLeaveTheAttemptCollecting() = runTest {
        val (session, transport) = session(script(prompt(), approved(60_000)))
        advanceTimeBy(46_000)
        advanceUntilIdle()
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)

        // The retry must not be swallowed by an attempt that is still collecting a socket nobody
        // is using: the loop has to be free to start a fresh one.
        session.retryApproval()
        advanceTimeBy(10)
        runCurrent()
        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)
        assertEquals(2, transport.sockets.size)
        assertEquals(listOf(true, false), transport.sockets.map { it.closed })

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun retryApprovalWaitsForExpiredAttemptCleanup() = runTest {
        val cleanupBarrier = CompletableDeferred<Unit>()
        val transport =
            ScriptedSessionTransport(script(prompt()), cancellationBarrier = cleanupBarrier)
        val session = LiveSession(television, transport, this, approvalWait = 1.seconds)

        runCurrent()
        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(1, transport.sockets.size)

        // Request the retry while the canceled collector is deliberately still in its finally
        // block. The old implementation published Connecting here, which let that collector's
        // onConnectionLost callback overwrite the retry with Unreachable.
        session.retryApproval()
        runCurrent()
        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(1, transport.sockets.size)

        cleanupBarrier.complete(Unit)
        runCurrent()

        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)
        assertEquals("the retry starts only after old-attempt cleanup", 2, transport.sockets.size)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun retryApprovalOutsideRepairChangesNothing() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        session.retryApproval()
        advanceUntilIdle()

        assertEquals(SessionState.Ready, session.snapshot.value.state)
        assertEquals(1, transport.sockets.size)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun closingTheSessionReleasesTheSocketAndDiscardsTransientApprovalMaterial() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        session.close()
        advanceUntilIdle()

        assertEquals(SessionState.Closed, session.snapshot.value.state)
        assertTrue(transport.sockets.single().closed)
        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        assertEquals("nothing was written after close", emptyList<String>(), transport.sent)
    }

    // --- containment -----------------------------------------------------------------

    @Test
    fun malformedFrameDoesNotEscapeSession() = runTest {
        val (session, transport) = session(SessionFixture.load("malformed-frame"))
        advanceTimeBy(70)
        runCurrent()
        val dropped = session.snapshot.value.state
        assertEquals("the malformed frames were dropped", SessionState.AwaitingTvApproval, dropped)

        advanceTimeBy(120)
        runCurrent()
        assertEquals(SessionState.Ready, session.snapshot.value.state)
        val result = session.command(TvCommand.Tap(RemoteKey.Enter))
        advanceUntilIdle()

        assertEquals(CommandResult.Accepted, result)
        assertEquals("the session survived every malformed frame", 1, transport.sockets.size)
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun anUnknownEventIsIgnoredAndTheSessionContinues() = runTest {
        val unknown = SessionEvent.Frame(0, """{"event":"ms.something.else"}""")
        val (session, _) = session(script(unknown, prompt()))
        advanceTimeBy(10)
        runCurrent()
        assertEquals(SessionState.AwaitingTvApproval, session.snapshot.value.state)
        session.close()
        advanceUntilIdle()
    }

    // --- the command path and the cloud ---------------------------------------------

    @Test
    fun cloudAbsenceDoesNotBlockCommand() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeDown))
        advanceUntilIdle()

        assertEquals(CommandResult.Accepted, result)
        assertEquals(1, transport.sockets.size)
        assertEquals("no cloud artifact on the classpath", emptyList<String>(), cloudArtifacts())

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun noBackendCallOnCommandPath() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceUntilIdle()
        val socketsBefore = transport.sockets.size
        val connectsBefore = transport.connects.size

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        advanceUntilIdle()

        assertEquals(CommandResult.Accepted, result)
        assertEquals(socketsBefore, transport.sockets.size)
        assertEquals(connectsBefore, transport.connects.size)
        assertEquals("the only frame is the command", 1, transport.sent.size)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun samsungGraphExcludesFirebase() {
        // The Gradle guard resolves the real graph; this asserts the module's source never names a
        // cloud, telemetry or entitlement participant at all.
        val forbidden =
            Regex(
                """\b(firebase|gms|crashlytics|analytics|billing|entitlement|telemetry)\b""",
                RegexOption.IGNORE_CASE,
            )
        val offenders = productionSources().flatMap { linesMatching(it, forbidden) }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun transientApprovalTokenIsNotPersisted() = runTest {
        val (session, transport) = session(SessionFixture.load("tls-approval-then-volume"))
        advanceUntilIdle()
        assertEquals(SessionState.Ready, session.snapshot.value.state)

        // The channel carried the encoded client name and no token on first contact.
        val url = RemoteChannel.remoteUrl(transport.connects.single())
        assertFalse("first contact sends no token", url.contains("token"))
        // Nothing the session wrote carried the token back out.
        assertFalse(transport.sent.any { it.contains("fixture-token") })
        // The caller-visible snapshot carries no credential of any kind.
        assertFalse(session.snapshot.value.toString().lowercase().contains("token"))

        // S03 has no durable store to write one to.
        val stores =
            Regex("""\b(RoomDatabase|DataStore|SharedPreferences|FileOutputStream|Keystore)\b""")
        val offenders = productionSources().flatMap { linesMatching(it, stores) }
        assertEquals(emptyList<String>(), offenders)

        session.close()
        advanceUntilIdle()
    }

    // --- the seam and the private endpoint ------------------------------------------

    @Test
    fun aSelectedTvIdReachesThePrivateEndpointWithoutExposingIt() = runTest {
        val opened = mutableListOf<ConfirmedTelevision>()
        val transport = ScriptedSessionTransport(script(prompt()))
        val tvs =
            samsungTvs("ssdp-tizen-tv") { tv, scope ->
                opened += tv
                LiveSession(tv, transport, scope)
            }
        val events = mutableListOf<DiscoveryEvent>()
        launch { tvs.discover().toList(events) }
        advanceUntilIdle()

        val card = events.filterIsInstance<DiscoveryEvent.Found>().single().tv
        assertEquals(ControlAvailability.NeedsPairing, card.availability)

        val session = tvs.open(card.id, this)
        advanceUntilIdle()

        assertEquals("the opaque id is enough to reach the television", 1, opened.size)
        assertEquals(card.id, opened.single().id)
        assertTrue("the private evidence selects the TLS channel", opened.single().tls)
        assertEquals("[host-a]", opened.single().host)
        val cardText = card.toString()
        assertFalse(cardText.contains("[host-a]"))
        assertFalse(cardText.contains("8002"))
        assertFalse(cardText.contains("token"))

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun openOnAnUnknownIdIsUnreachableAndOpensNoSocket() = runTest {
        val tvs = SamsungTvsImpl(newScan = { error("an unknown id must not start a scan") })
        val session = tvs.open(TvId("never-discovered"), this)
        advanceUntilIdle()

        assertEquals(SessionState.Unreachable, session.snapshot.value.state)
        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        session.close()
    }

    @Test
    fun openOnAnUnsupportedTelevisionOpensNoSocket() = runTest {
        val opened = mutableListOf<ConfirmedTelevision>()
        val tvs =
            samsungTvs("unsupported-no-keys") { tv, scope ->
                opened += tv
                LiveSession(tv, ScriptedSessionTransport(script(prompt())), scope)
            }
        val events = mutableListOf<DiscoveryEvent>()
        launch { tvs.discover().toList(events) }
        advanceUntilIdle()

        val card = events.filterIsInstance<DiscoveryEvent.Found>().single().tv
        assertEquals(ControlAvailability.Unsupported, card.availability)

        val session = tvs.open(card.id, this)
        advanceUntilIdle()

        assertEquals("no session for an unsupported television", 0, opened.size)
        assertEquals(SessionState.Unsupported, session.snapshot.value.state)
        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        session.close()
    }

    private companion object {
        /** Cloud, telemetry and entitlement artifacts `:samsung` must never depend on. */
        val FORBIDDEN_CLOUD_ARTIFACTS =
            listOf("firebase", "play-services", "crashlytics", "google-analytics", "billing")
    }
}
