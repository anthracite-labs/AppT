package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RejectedKeyEvidenceTest {
    private val tvId = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e")
    private val television =
        ConfirmedTelevision(tvId, "[fixture-host]", tls = true, adoptedChannel = true)
    private val store = InMemorySamsungStore()

    @Test
    fun explicitTelevisionRejectionRemovesAndPersistsOnlyThatKey() = runTest {
        val transport = ScriptedSessionTransport(SessionFixture.load("tls-approval-then-volume"))
        val live =
            LiveSession(
                television,
                transport,
                this,
                store,
                capabilityDispatcher = Dispatchers.Unconfined,
            )
        advanceUntilIdle()
        assertEquals(SessionState.Ready, live.snapshot.value.state)
        assertTrue(RemoteKey.VolumeUp in live.snapshot.value.capabilities.keys)

        transport.nextCommandWriteResult = CommandWriteResult.TelevisionRejected
        assertEquals(
            CommandResult.Rejected(TvFailure.Rejected),
            live.command(TvCommand.Tap(RemoteKey.VolumeUp)),
        )
        assertFalse(RemoteKey.VolumeUp in live.snapshot.value.capabilities.keys)
        assertTrue(RemoteKey.VolumeDown in live.snapshot.value.capabilities.keys)
        assertEquals(setOf(RemoteKey.VolumeUp), store.loadDevice(tvId)?.rejectedKeys)
        live.close()
        advanceUntilIdle()

        val reopenedTransport = ScriptedSessionTransport(SessionFixture.load("token-resume"))
        val reopened =
            LiveSession(
                television,
                reopenedTransport,
                this,
                store,
                capabilityDispatcher = Dispatchers.Unconfined,
            )
        advanceUntilIdle()
        assertEquals(SessionState.Ready, reopened.snapshot.value.state)
        assertFalse(RemoteKey.VolumeUp in reopened.snapshot.value.capabilities.keys)
        assertEquals(CommandResult.Accepted, reopened.command(TvCommand.Tap(RemoteKey.VolumeUp)))
        assertFalse(
            "a local socket write is not positive TV evidence",
            RemoteKey.VolumeUp in reopened.snapshot.value.capabilities.keys,
        )
        assertEquals(setOf(RemoteKey.VolumeUp), store.loadDevice(tvId)?.rejectedKeys)
        reopened.close()
        advanceUntilIdle()
    }

    @Test
    fun localWriteRefusalNeverBecomesNegativeCapabilityEvidence() = runTest {
        val transport = ScriptedSessionTransport(SessionFixture.load("tls-approval-then-volume"))
        val live =
            LiveSession(
                television,
                transport,
                this,
                store,
                capabilityDispatcher = Dispatchers.Unconfined,
            )
        advanceUntilIdle()
        transport.nextCommandWriteResult = CommandWriteResult.LocalRefused

        assertEquals(
            CommandResult.Rejected(TvFailure.Unavailable),
            live.command(TvCommand.Tap(RemoteKey.VolumeUp)),
        )
        assertTrue(RemoteKey.VolumeUp in live.snapshot.value.capabilities.keys)
        assertTrue(store.loadDevice(tvId)?.rejectedKeys.orEmpty().isEmpty())
        live.close()
        advanceUntilIdle()
    }
}
