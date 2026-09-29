package dev.anthracite.appt.samsung.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Android-runtime half of the identifier-scrubbing contract (Issue #91 correction cycle).
 *
 * The scrubber's regexes must compile on Android's ICU-based engine, which rejects syntax the JVM
 * accepts: the physical failure was an `ExceptionInInitializerError` (a `PatternSyntaxException`)
 * thrown from this class's initializer on the device while every JVM run stayed green. This test
 * initializes the object and exercises the representative identifier cases — ordinary names,
 * UUID, IPv4 with port, MAC in both separators, IPv6, attached and spelled-out ports, and a URL —
 * on the device, so a JVM-only regex regression can never pass again.
 */
@RunWith(AndroidJUnit4::class)
class NameScrubberAndroidTest {
    @Test
    fun theScrubberInitializesAndScrubsIdentifiersOnAndroid() {
        // Ordinary names pass through untouched.
        assertEquals("Kitchen TV", NameScrubber.scrub("Kitchen TV"))
        // A UUID, with the protocol prefix.
        assertEquals("TV", NameScrubber.scrub("TV uuid:7c9e6679-7425-40de-944b-e07fc1f90ae7"))
        // IPv4 with a port; the leftover empty brackets are tidied away.
        assertEquals("TV", NameScrubber.scrub("TV (10.0.0.4:8001)"))
        // A MAC address, in either separator.
        assertEquals("TV", NameScrubber.scrub("TV ab:cd:ef:01:02:03"))
        assertEquals("TV", NameScrubber.scrub("TV ab-cd-ef-01-02-03"))
        // IPv6, including compression.
        assertEquals("TV", NameScrubber.scrub("TV fe80::1c9:26ff:fe34:8a5d"))
        // A port attached to a word, and a spelled-out port.
        assertEquals("Kitchen", NameScrubber.scrub("Kitchen:8001"))
        assertEquals("Kitchen", NameScrubber.scrub("Kitchen port 8002"))
        // A URL with whatever host and port it carries.
        assertEquals("TV", NameScrubber.scrub("TV http://10.0.0.4:8001/remote"))
    }
}
