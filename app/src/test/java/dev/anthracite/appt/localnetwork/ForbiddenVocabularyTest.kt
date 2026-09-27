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
 * Issue #73 / discovery.md#permission-gate and Issue #79 / presentation.md#pairing: the
 * explanations, Pairing, and Remote all use ordinary language, never protocol, discovery-mechanism,
 * port, address, or television-security vocabulary.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ForbiddenVocabularyTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val forbidden =
        Regex(
            "\\b(ssdp|mdns|m-search|multicast|port|protocol|ip|ipv4|ipv6|address|uuid|mac|upnp|dial|nsd|" +
                "bonjour|airplay|http|https|websocket|socket|tcp|udp|lan|router|subnet|" +
                "cert|certificate|tls|token|pin|spki|wss|ws|url|host|serial|firmware|api key|apikey|" +
                "pairing code|pairing token|spki pin|handshake|encrypt|encrypted|encryption)\\b",
            RegexOption.IGNORE_CASE,
        )

    private fun userFacingStrings() =
        R.string::class.java.fields.filter {
            it.name.startsWith("localnetwork_") ||
                it.name.startsWith("discovery_") ||
                it.name.startsWith("pairing_") ||
                it.name.startsWith("remote_")
        }

    @Test
    fun userFacingCopyUsesOrdinaryLanguage() {
        val strings = userFacingStrings()
        assertTrue("expected the S02 and S03 strings", strings.size > 20)
        strings.forEach { field ->
            val text = context.getString(field.getInt(null))
            assertFalse(
                "${field.name} uses technical vocabulary: \"$text\"",
                forbidden.containsMatchIn(text),
            )
        }
    }

    /** The Remote key names are the wire vocabulary, so they must never reach the copy. */
    @Test
    fun theRemoteCopyNeverNamesAKeyOnTheWire() {
        val copy =
            userFacingStrings()
                .filter { it.name.startsWith("remote_") }
                .joinToString(" ") { context.getString(it.getInt(null)) }
        listOf(
                "KEY_VOLUP",
                "KEY_VOLDOWN",
                "KEY_UP",
                "KEY_ENTER",
                "KEY_POWEROFF",
                "ms.remote.control",
            )
            .forEach {
                assertFalse("the copy names a wire key: $it", copy.contains(it, ignoreCase = true))
            }
    }

    @Test
    fun theVocabularyCheckCatchesTechnicalWords() {
        listOf(
                "Allow multicast",
                "Enter the IP address",
                "port 8001",
                "Using SSDP",
                "Open a TLS connection to the certificate",
                "Waiting for the pairing token",
                "Enter the 4-digit PIN",
                "Connect to the SPKI pin",
                "The wss endpoint",
            )
            .forEach { assertTrue(it, forbidden.containsMatchIn(it)) }
        assertFalse(
            forbidden.containsMatchIn("Make sure your TV is on the same Wi-Fi as this phone.")
        )
        assertFalse(
            forbidden.containsMatchIn(
                "Approve AppT on Living Room TV. Look for the message on your television and choose Allow."
            )
        )
    }
}
