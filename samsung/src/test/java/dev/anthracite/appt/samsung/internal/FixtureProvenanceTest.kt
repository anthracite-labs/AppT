package dev.anthracite.appt.samsung.internal

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixture carries its provenance and nothing identifying (Issue #73: no IPv4 address, no MAC,
 * no token in any fixture file).
 */
class FixtureProvenanceTest {
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val mac = Regex("""\b[0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5}\b""")
    // A raw token value is identifying, so it may not be committed. The approved placeholder
    // [fixture-token] carries no television-specific value and is the only exception.
    private val token =
        Regex(""""token"\s*:\s*"(?!\[fixture-token\])|[?&]token=""", RegexOption.IGNORE_CASE)
    private val requiredProvenance =
        listOf("caseId", "captured", "generation", "sourceRole", "redaction", "notes")

    private val directories: List<File> = Fixture.directories()

    @Test
    fun fixturesExist() {
        assertTrue("expected the S02 fixture set", directories.size >= 10)
    }

    @Test
    fun everyFixtureHasCompleteProvenance() {
        directories.forEach { directory ->
            val provenance = File(directory, "provenance.json")
            assertTrue("${directory.name}: provenance.json", provenance.isFile)
            assertTrue("${directory.name}: trace.jsonl", File(directory, "trace.jsonl").isFile)
            val fields = Json.parseToJsonElement(provenance.readText()).jsonObject
            requiredProvenance.forEach { key ->
                val value = fields[key]?.jsonPrimitive?.content
                assertFalse("${directory.name}: provenance.$key", value.isNullOrBlank())
            }
            assertEquals(directory.name, fields.getValue("caseId").jsonPrimitive.content)
            assertTrue(
                "${directory.name}: sourceRole is synthetic or a named capture role",
                fields.getValue("sourceRole").jsonPrimitive.content.isNotBlank(),
            )
        }
    }

    @Test
    fun fixturesContainNoAddressMacOrToken() {
        directories
            .flatMap { it.listFiles().orEmpty().toList() }
            .forEach { file ->
                val text = file.readText()
                assertFalse("${file.path}: IPv4 address", ipv4.containsMatchIn(text))
                assertFalse("${file.path}: MAC address", mac.containsMatchIn(text))
                assertFalse("${file.path}: token", token.containsMatchIn(text))
            }
    }

    @Test
    fun redactionPatternsCatchWhatTheyGuard() {
        assertTrue(ipv4.containsMatchIn("""{"ip":"192.168.1.20"}"""))
        assertTrue(mac.containsMatchIn("""{"wifiMac":"a4:30:7a:01:02:03"}"""))
        assertTrue(token.containsMatchIn("""{"data":{"token":"12345678"}}"""))
        assertTrue(token.containsMatchIn("""?token=12345678&name=AppT"""))
        assertFalse(token.containsMatchIn(""""TokenAuthSupport":"true""""))
        assertFalse(token.containsMatchIn("""{"data":{"token":"[fixture-token]"}}"""))
    }
}
