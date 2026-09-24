package dev.anthracite.appt.samsung.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpTest {

    @Test
    fun onlyTheTwoV1SearchTargetsAreSearched() {
        assertEquals(
            listOf(
                "urn:samsung.com:device:RemoteControlReceiver:1",
                "urn:dial-multiscreen-org:service:dial:1",
            ),
            Ssdp.SEARCH_TARGETS,
        )
        assertThrows(IllegalArgumentException::class.java) { Ssdp.mSearch("ssdp:all", "[group]") }
        assertThrows(IllegalArgumentException::class.java) {
            Ssdp.mSearch("urn:schemas-upnp-org:device:MediaRenderer:1", "[group]")
        }
    }

    @Test
    fun mSearchIsAWellFormedDiscoverRequest() {
        val message = Ssdp.mSearch(Ssdp.REMOTE_CONTROL_RECEIVER, "[group]:1900").decodeToString()
        assertTrue(message.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue("HOST: [group]:1900\r\n" in message)
        assertTrue("MAN: \"ssdp:discover\"\r\n" in message)
        assertTrue("MX: 2\r\n" in message)
        assertTrue("ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n" in message)
        assertTrue(message.endsWith("\r\n\r\n"))
        assertFalse("ssdp:all" in message)
    }

    @Test
    fun remoteControlReceiverRepliesAreCandidates() {
        val reply =
            "HTTP/1.1 200 OK\r\nst: urn:samsung.com:device:RemoteControlReceiver:1\r\nSERVER: x\r\n\r\n"
        assertEquals(Probe.RemoteControlReceiver, Ssdp.classify(reply))
    }

    @Test
    fun dialRepliesAreCandidatesOnlyWhenTheyIdentifySamsung() {
        fun dial(server: String) =
            "HTTP/1.1 200 OK\r\nST: urn:dial-multiscreen-org:service:dial:1\r\nSERVER: $server\r\n\r\n"
        assertEquals(Probe.DialSamsung, Ssdp.classify(dial("SHP, UPnP/1.0, Samsung UPnP SDK/1.0")))
        assertNull(Ssdp.classify(dial("Linux/4.4 UPnP/1.0 Cast/1.0")))
        assertNull(
            Ssdp.classify("HTTP/1.1 200 OK\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n")
        )
    }

    @Test
    fun otherRepliesAreDropped() {
        assertNull(
            Ssdp.classify("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\nSERVER: Samsung\r\n\r\n")
        )
        assertNull(
            Ssdp.classify("HTTP/1.1 404 Not Found\r\nST: ${Ssdp.REMOTE_CONTROL_RECEIVER}\r\n\r\n")
        )
        assertNull(
            Ssdp.classify("NOTIFY * HTTP/1.1\r\nNT: ${Ssdp.REMOTE_CONTROL_RECEIVER}\r\n\r\n")
        )
        assertNull(Ssdp.classify(""))
        assertNull(Ssdp.classify("garbage"))
    }
}
