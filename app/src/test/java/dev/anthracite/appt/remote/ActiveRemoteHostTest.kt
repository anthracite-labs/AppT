package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * lifecycle.md: `ActiveRemoteHost` owns the one live session, so no screen calls `SamsungTvs.open`,
 * a configuration change re-attaches instead of re-opening, and the last release starts the
 * 15-second grace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveRemoteHostTest {
    private val tvs = FakeSamsungTvs()
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)
    private val bedroom = TvId(FakeSamsungTvs.OLDER_ID)

    private fun TestScope.host() = ActiveRemoteHost(tvs, this)

    @Test
    fun enterOpensOneSessionForOneTelevision() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()

        assertEquals(listOf(livingRoom), tvs.openedIds)
        assertEquals(livingRoom, host.current.value?.tvId)
        assertEquals(SessionState.Connecting, host.current.value?.session?.state)
    }

    @Test
    fun enteringTheSameTelevisionAgainOpensNoSecondSession() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()
        host.enter(livingRoom)
        advanceUntilIdle()

        assertEquals("no second open, so no second socket", listOf(livingRoom), tvs.openedIds)
        assertFalse("the first session is not replaced", tvs.sessionFor(livingRoom)!!.closed)
    }

    @Test
    fun enteringAnotherTelevisionClosesTheFirst() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()
        host.enter(bedroom)
        advanceUntilIdle()

        assertTrue(tvs.sessionFor(livingRoom)!!.closed)
        assertEquals(listOf(livingRoom, bedroom), tvs.openedIds)
        assertEquals(bedroom, host.current.value?.tvId)
    }

    @Test
    fun theSnapshotFollowsTheSessionWithoutPolling() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()

        tvs.sessionFor(livingRoom)!!.ready()
        advanceUntilIdle()

        assertEquals(SessionState.Ready, host.current.value?.session?.state)
        assertEquals(RemoteKey.entries.toSet(), host.current.value?.session?.capabilities?.keys)
    }

    @Test
    fun aCommandIsSentOnTheRetainedSession() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready()
        session.nextResult = CommandResult.Accepted
        advanceUntilIdle()

        val result = host.current.value!!.session.command(TvCommand.Tap(RemoteKey.VolumeUp))

        assertEquals(CommandResult.Accepted, result)
        assertEquals(listOf(TvCommand.Tap(RemoteKey.VolumeUp)), session.commands)
    }

    @Test
    fun aDeniedEntryOpensNothing() = runTest {
        val host = ActiveRemoteHost(tvs, this) { false }
        host.enter(livingRoom)
        advanceUntilIdle()

        assertEquals("no session, no socket", emptyList<TvId>(), tvs.openedIds)
        assertNull(host.current.value)
    }

    @Test
    fun theLastReleaseStartsGraceAndTheSessionSurvivesIt() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()
        host.retain("pairing")
        host.release("pairing")

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals("grace keeps the session", livingRoom, host.current.value?.tvId)
        assertFalse(tvs.sessionFor(livingRoom)!!.closed)

        advanceTimeBy(2)
        runCurrent()
        assertTrue("grace elapsed closes the session", tvs.sessionFor(livingRoom)!!.closed)
        assertNull(host.current.value)
    }

    @Test
    fun returningInsideGraceKeepsTheSameSession() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()
        host.retain("remote")
        host.release("remote")
        advanceTimeBy(5_000)

        host.retain("remote")
        advanceTimeBy(20_000)
        runCurrent()

        assertFalse("returning inside grace reuses the session", tvs.sessionFor(livingRoom)!!.closed)
        assertEquals(listOf(livingRoom), tvs.openedIds)
    }

    @Test
    fun oneOwnerReleasingDoesNotStartGrace() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()
        host.retain("pairing")
        host.retain("remote")
        host.release("pairing")

        advanceTimeBy(30_000)
        runCurrent()

        assertFalse(tvs.sessionFor(livingRoom)!!.closed)
        assertEquals(1, tvs.openedIds.size)
    }

    @Test
    fun closeIsImmediateAndIdempotent() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()

        host.close()
        advanceUntilIdle()
        host.close()
        advanceUntilIdle()

        assertTrue(tvs.sessionFor(livingRoom)!!.closed)
        assertNull(host.current.value)
    }

    @Test
    fun aReleasedHostHoldsNothingToSendThrough() = runTest {
        val host = host()
        host.enter(livingRoom)
        advanceUntilIdle()

        host.close()
        advanceUntilIdle()

        assertNull("nothing routes a command through a closed host", host.current.value)
        assertTrue(tvs.sessionFor(livingRoom)!!.closed)
    }
}
