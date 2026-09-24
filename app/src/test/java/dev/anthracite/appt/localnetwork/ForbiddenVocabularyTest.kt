package dev.anthracite.appt.localnetwork

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Issue #73 / discovery.md#permission-gate: the explanation and Discovery use ordinary language,
 * never protocol, discovery-mechanism, port, or address vocabulary.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ForbiddenVocabularyTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val forbidden =
        Regex(
            "\\b(ssdp|mdns|m-search|multicast|port|protocol|ip|ipv4|ipv6|address|uuid|mac|upnp|dial|nsd|" +
                "bonjour|airplay|http|https|websocket|socket|tcp|udp|lan|router|subnet)\\b",
            RegexOption.IGNORE_CASE,
        )

    @Test
    fun localNetworkAndDiscoveryCopyUsesOrdinaryLanguage() {
        val strings =
            R.string::class.java.fields.filter {
                it.name.startsWith("localnetwork_") || it.name.startsWith("discovery_")
            }
        assertTrue("expected the S02 strings", strings.size > 10)
        strings.forEach { field ->
            val text = context.getString(field.getInt(null))
            assertFalse(
                "${field.name} uses technical vocabulary: \"$text\"",
                forbidden.containsMatchIn(text),
            )
        }
    }

    @Test
    fun theVocabularyCheckCatchesTechnicalWords() {
        listOf("Allow multicast", "Enter the IP address", "port 8001", "Using SSDP").forEach {
            assertTrue(it, forbidden.containsMatchIn(it))
        }
        assertFalse(
            forbidden.containsMatchIn("Make sure your TV is on the same Wi-Fi as this phone.")
        )
    }
}
