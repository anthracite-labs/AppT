package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Issue #79 revision 2: TLS stays on OkHttp; plaintext 8001 never reaches it. */
class ProductionSessionTransportTest {
    @Test
    fun tlsTelevisionsUseTheTlsAdapterAndPlaintextUsesTheRawSocketAdapter() = runBlocking {
        val tls = RecordingTransport()
        val plaintext = RecordingTransport()
        val transport = ProductionSessionTransport(tls, plaintext)
        val tlsTv = television(tls = true)
        val plainTv = television(tls = false)

        transport.connect(tlsTv, saved = null)
        transport.connect(plainTv, saved = null)

        assertEquals(listOf(tlsTv), tls.connected)
        assertEquals(listOf(plainTv), plaintext.connected)
    }

    @Test
    fun theSavedPairingTravelsWithTheAttemptSoTheTlsAdapterCanEnforceThePin() = runBlocking {
        val saved = PairingSecret(token = "the-saved-token", pin = "the-saved-pin")
        val tls = RecordingTransport()

        ProductionSessionTransport(tls, RecordingTransport())
            .connect(television(tls = true), saved = saved)

        assertEquals(listOf(saved), tls.savedPairings.single())
    }

    @Test
    fun aSuccessfulConnectReturnsTheAdapterConnection() = runBlocking {
        val connection =
            object : SessionConnection {
                override val frames: Flow<String> = emptyFlow()
                override val certificateIdentity: String? = null

                override suspend fun send(frame: String): Boolean = false

                override fun close() = Unit
            }
        val tls = RecordingTransport(connection)
        val returned =
            ProductionSessionTransport(tls, RecordingTransport())
                .connect(television(tls = true), saved = null)

        assertEquals(ConnectionAttempt.Opened(connection), returned)
    }

    private fun television(tls: Boolean) =
        ConfirmedTelevision(TvId("tv"), "127.0.0.1", tls = tls, adoptedChannel = true)

    private class RecordingTransport(private val connection: SessionConnection? = null) :
        SessionTransport {
        val connected = mutableListOf<ConfirmedTelevision>()
        val savedPairings = mutableListOf<PairingSecret?>()

        override suspend fun connect(
            television: ConfirmedTelevision,
            saved: PairingSecret?,
        ): ConnectionAttempt {
            connected += television
            savedPairings += saved
            return connection?.let(ConnectionAttempt::Opened) ?: ConnectionAttempt.Unreachable
        }
    }
}
