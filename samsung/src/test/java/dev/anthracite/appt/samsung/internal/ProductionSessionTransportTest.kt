package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Issue #79 revision 2: TLS stays on OkHttp; plaintext 8001 never reaches it.
 */
class ProductionSessionTransportTest {
    @Test
    fun tlsTelevisionsUseTheTlsAdapterAndPlaintextUsesTheRawSocketAdapter() = runBlocking {
        val tls = RecordingTransport()
        val plaintext = RecordingTransport()
        val transport = ProductionSessionTransport(tls, plaintext)
        val tlsTv = television(tls = true)
        val plainTv = television(tls = false)

        transport.connect(tlsTv)
        transport.connect(plainTv)

        assertEquals(listOf(tlsTv), tls.connected)
        assertEquals(listOf(plainTv), plaintext.connected)
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
            ProductionSessionTransport(tls, RecordingTransport()).connect(television(tls = true))

        assertSame(connection, returned)
    }

    private fun television(tls: Boolean) =
        ConfirmedTelevision(TvId("tv"), "127.0.0.1", tls = tls, adoptedChannel = true)

    private class RecordingTransport(private val connection: SessionConnection? = null) :
        SessionTransport {
        val connected = mutableListOf<ConfirmedTelevision>()

        override suspend fun connect(television: ConfirmedTelevision): SessionConnection? {
            connected += television
            return connection
        }
    }
}
