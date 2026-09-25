package dev.anthracite.appt.samsung.internal

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The scripted [SessionTransport] adapter (docs/architecture/modules.md#internal-seams-inside-samsung,
 * testing.md#fake-adapters).
 *
 * It replays fixture frames with their delays, can end the socket, can refuse the connection, and
 * carries a generated test certificate identity. It records every frame the session writes and every
 * socket it opened, which is how the one-socket command rule is proven.
 */
internal class ScriptedSessionTransport(
    private val fixture: SessionFixture,
    private val certificateIdentity: String? = null,
    private val connectDelayMs: Long = 0,
    private val refusesConnection: Boolean = false,
) : SessionTransport {

    /** Every frame the session wrote, in order. */
    val sent = mutableListOf<String>()

    /** Every socket this transport opened. A command must never add a second one. */
    val sockets = mutableListOf<ScriptedSessionConnection>()

    /** Every television this transport was asked to reach, in order. */
    val connects = mutableListOf<ConfirmedTelevision>()

    override suspend fun connect(television: ConfirmedTelevision): SessionConnection? {
        connects += television
        if (connectDelayMs > 0) delay(connectDelayMs)
        if (refusesConnection) return null
        val connection = ScriptedSessionConnection(fixture, certificateIdentity, sent)
        sockets += connection
        return connection
    }
}

private class ScriptedSessionConnection(
    private val fixture: SessionFixture,
    override val certificateIdentity: String?,
    private val sent: MutableList<String>,
) : SessionConnection {

    var closed = false
        private set

    override val frames: Flow<String> = flow {
        var now = 0L
        for (event in fixture.inbound) {
            delay(event.atMs - now)
            now = event.atMs
            when (event) {
                is SessionEvent.Frame -> emit(event.body)
                is SessionEvent.Close -> return@flow
            }
        }
        // The script is exhausted and the television has not ended the session: the socket stays
        // open, as a live one does, until the holder releases it.
        awaitCancellation()
    }

    override suspend fun send(frame: String): Boolean {
        sent += frame
        return true
    }

    override fun close() {
        closed = true
    }
}
