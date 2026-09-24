package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvFailure
import java.io.IOException
import java.net.InetAddress
import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanPolicyTest {

    @Test
    fun onlyNonVpnWifiOrEthernetIsUsable() {
        assertEquals(LanPolicy.Kind.WiFi, LanPolicy.classify(isVpn = false, hasWifi = true, hasEthernet = false))
        assertEquals(
            LanPolicy.Kind.Ethernet,
            LanPolicy.classify(isVpn = false, hasWifi = false, hasEthernet = true),
        )
        assertNull("a VPN is never bypassed", LanPolicy.classify(isVpn = true, hasWifi = true, hasEthernet = false))
        assertNull("cellular only", LanPolicy.classify(isVpn = false, hasWifi = false, hasEthernet = false))
    }

    @Test
    fun onlyLocalIpv4AddressesAreContacted() {
        fun address(vararg octets: Int) = InetAddress.getByAddress(ByteArray(4) { octets[it].toByte() })
        assertTrue(LanPolicy.isLanAddress(address(192, 168, 1, 20)))
        assertTrue(LanPolicy.isLanAddress(address(10, 0, 0, 5)))
        assertTrue(LanPolicy.isLanAddress(address(172, 16, 4, 1)))
        assertTrue(LanPolicy.isLanAddress(address(169, 254, 3, 3)))
        assertFalse(LanPolicy.isLanAddress(address(8, 8, 8, 8)))
        assertFalse(LanPolicy.isLanAddress(address(127, 0, 0, 1)))
        assertFalse(LanPolicy.isLanAddress(address(239, 255, 255, 250)))
        assertFalse(LanPolicy.isLanAddress(InetAddress.getByName("::1")))
    }

    @Test
    fun blockedSocketsMeanLocalNetworkDenied() {
        assertEquals(TvFailure.LocalNetworkDenied, LanPolicy.failureFor(SecurityException("blocked")))
        assertEquals(
            TvFailure.LocalNetworkDenied,
            LanPolicy.failureFor(SocketException("sendto failed: EPERM (Operation not permitted)")),
        )
        assertEquals(
            TvFailure.LocalNetworkDenied,
            LanPolicy.failureFor(IOException("wrapped", SocketException("connect failed: EACCES (Permission denied)"))),
        )
        assertEquals(
            TvFailure.Unreachable,
            LanPolicy.failureFor(SocketException("sendto failed: ENETUNREACH (Network is unreachable)")),
        )
        assertEquals(TvFailure.Unreachable, LanPolicy.failureFor(IOException()))
        assertEquals(TvFailure.Unreachable, LanPolicy.failureFor(IllegalStateException("other")))
    }
}
