package dev.anthracite.appt.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * data.md#room: the exported `appt.db` schema is committed, so a forbidden column name is a build
 * failure rather than a review comment.
 *
 * The forbidden list is owned by data.md and diagnostics.md: any column named like a token, pin,
 * secret, certificate, MAC, address, host, SSID, Wi-Fi name, command, or text. Version 1 has one
 * entity, so there is no migration yet; `roomMigrationEveryVersion` starts to apply at version 2.
 */
class TvProfileSchemaTest {
    private val schemaRoot: File = File("schemas").takeIf { it.isDirectory } ?: File("app/schemas")

    /**
     * data.md#room's forbidden column names. `wifi` is matched as a prefix so `wifiName` and
     * `wifi_name` are caught too, and the same for `certificate`/`cert`.
     */
    private val forbidden =
        Regex(
            """\b(token|pin|secret|certificat\w*|mac|address|host|ssid|wifi\w*|command|text)\b""",
            RegexOption.IGNORE_CASE,
        )

    @Test
    fun theExportedSchemaForVersionOneIsCommitted() {
        val schema = schemaFile(1)
        assertTrue("expected a committed schema at " + schema.path, schema.isFile)
        val database = schema.database()
        assertEquals("1", database.getValue("version").jsonPrimitive.content)
        assertEquals(1, database.getValue("entities").jsonArray.size)
    }

    @Test
    fun schemaContainsNoForbiddenColumn() {
        val entities = schemaFile(1).database().getValue("entities").jsonArray
        entities.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            val columns = entity.jsonObject.columnNames()
            assertTrue("$table: expected at least one column", columns.isNotEmpty())
            columns.forEach { column ->
                assertFalse(
                    "$table.$column is a forbidden column name",
                    forbidden.containsMatchIn(column),
                )
            }
        }
    }

    @Test
    fun theTvProfileColumnsAreExactlyTheDocumentedSet() {
        val entities = schemaFile(1).database().getValue("entities").jsonArray
        assertEquals(1, entities.size)
        assertEquals(
            listOf(
                "createdAt",
                "friendlyName",
                "lastOpenedAt",
                "nameSource",
                "stableIdentity",
                "tvId",
            ),
            entities.single().jsonObject.columnNames().sorted(),
        )
    }

    /**
     * Room names each column twice; the field path is the Kotlin one and the column name is SQL.
     */
    private fun JsonObject.columnNames(): List<String> =
        getValue("fields").jsonArray.map { field ->
            val entry = field.jsonObject
            (entry["fieldPath"] ?: entry.getValue("columnName")).jsonPrimitive.content
        }

    private fun schemaFile(version: Int): File {
        val directory =
            schemaRoot.listFiles().orEmpty().singleOrNull {
                it.isDirectory && File(it, "$version.json").isFile
            } ?: error("no exported schema for version $version under " + schemaRoot.path)
        return File(directory, "$version.json")
    }

    private fun File.database(): JsonObject =
        Json.parseToJsonElement(readText()).jsonObject.getValue("database").jsonObject
}
