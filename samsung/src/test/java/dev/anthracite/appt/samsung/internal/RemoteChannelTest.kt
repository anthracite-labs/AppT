package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.TvId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire shape stays inside `:samsung` (docs/architecture/protocol.md; samsung-interface.md: "app
 * tests ... do not assert `KEY_*` or ports" — those assertions belong here).
 */
class RemoteChannelTest {
    private val tlsTelevision =
        ConfirmedTelevision(TvId("uuid-tls"), "[host-a]", tls = true, adoptedChannel = true)
    private val plaintextTelevision =
        ConfirmedTelevision(TvId("uuid-plain"), "[host-b]", tls = false, adoptedChannel = true)

    @Test
    fun clientNameIsExactlyAppTEncodedByTheModule() {
        assertEquals("AppT", RemoteChannel.CLIENT_NAME)
        assertEquals("QXBwVA==", RemoteChannel.CLIENT_NAME_ENCODED)
    }

    @Test
    fun remoteUrlPrefersTlsAndCarriesNoTokenOnFirstContact() {
        val url = RemoteChannel.remoteUrl(tlsTelevision)
        assertTrue("the TLS channel is preferred", url.startsWith("wss://[host-a]:8002/"))
        assertTrue("the adopted channel path", url.contains("/api/v2/channels/samsung.remote.control"))
        assertTrue("the encoded client name", url.endsWith("?name=QXBwVA%3D%3D"))
        assertFalse("first contact sends no saved token", url.contains("token"))
    }

    @Test
    fun remoteUrlBracketsIpv6Authorities() {
        val ipv6 =
            ConfirmedTelevision(
                TvId("uuid-ipv6"),
                "feee::face",
                tls = true,
                adoptedChannel = true,
            )

        val url = RemoteChannel.remoteUrl(ipv6)

        assertTrue(url.startsWith("wss://[feee::face]:8002/"))
        assertTrue(url.endsWith("?name=QXBwVA%3D%3D"))
    }

    @Test
    fun remoteUrlUsesThePlaintextFallbackOnlyWhenEvidenceSelectsIt() {
        val url = RemoteChannel.remoteUrl(plaintextTelevision)
        assertTrue(url.startsWith("ws://[host-b]:8001/"))
        assertFalse(url.contains("token"))
    }

    @Test
    fun tapFrameUsesTheDocumentedRemoteControlShape() {
        val expected =
            """
            {"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"KEY_VOLUP","Option":"false","TypeOfRemote":"SendRemoteKey"}}
            """
                .trimIndent()
        assertEquals(expected, RemoteChannel.tapFrame(RemoteKey.VolumeUp))
    }

    @Test
    fun everyCallerKeyHasAnInternalName() {
        // The mapping is total: a caller key with no internal name would be a rejected command, not a
        // silent no-op.
        RemoteKey.entries.forEach { key ->
            assertTrue("no internal name for $key", RemoteChannel.tapFrame(key).contains("KEY_"))
        }
    }

    @Test
    fun parseEventReadsTheEventNameAndTheTransientToken() {
        val event =
            RemoteChannel.parseEvent(
                """{"event":"ms.channel.connect","data":{"token":"[fixture-token]"}}"""
            )
        assertEquals(RemoteChannel.EVENT_CONNECT, event?.name)
        assertEquals("[fixture-token]", event?.token)
    }

    @Test
    fun parseEventReadsATokenFromTheClientAttributes() {
        val event = RemoteChannel.parseEvent("""{"event":"ms.channel.connect","data":"[fixture-token]"}""")
        assertEquals("[fixture-token]", event?.token)
    }

    @Test
    fun parseEventContainsMalformedAndOversizedFrames() {
        // Broken JSON, a non-object root, a missing event and over-limit input are all malformed
        // frames: dropped, never thrown.
        assertNull(RemoteChannel.parseEvent("""{"event":"ms.channel.connect","data":{"""))
        assertNull(RemoteChannel.parseEvent("[1,2,3]"))
        assertNull(RemoteChannel.parseEvent("""{"data":{"token":"[fixture-token]"}}"""))
        assertNull(RemoteChannel.parseEvent("not json at all"))
        assertNull(
            RemoteChannel.parseEvent("x".repeat(RemoteChannel.MAX_FRAME_CODE_POINTS + 1))
        )
    }

    @Test
    fun parseEventIgnoresUnknownEventsRatherThanFailing() {
        val event = RemoteChannel.parseEvent("""{"event":"ms.something.else","data":{}}""")
        assertEquals("ms.something.else", event?.name)
        // The session machine only acts on the four documented events.
        assertFalse(event?.name in SESSION_EVENTS)
        val numeric = RemoteChannel.parseEvent("""{"event":42}""")
        assertFalse(numeric?.name in SESSION_EVENTS)
    }

    @Test
    fun parseEventRejectsDeeperThanTheJsonDepthLimit() {
        val depth = RemoteChannel.MAX_JSON_DEPTH + 1
        val deep = "{\"a\":".repeat(depth) + "1" + "}".repeat(depth)
        assertNull(RemoteChannel.parseEvent(deep))
    }

    private companion object {
        val SESSION_EVENTS =
            setOf(
                RemoteChannel.EVENT_CONNECT,
                RemoteChannel.EVENT_UNAUTHORIZED,
                RemoteChannel.EVENT_TIME_OUT,
                RemoteChannel.EVENT_CLIENT_DISCONNECT,
            )
    }
}
