package dev.anthracite.appt.backup

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The backup guards for the saved pairing (Issue #91, docs/architecture/data.md): a television's
 * saved pairing secret and samsung-private device record are device-local, so nothing under this
 * app's private storage may reach cloud backup or device transfer. The manifest keeps `allowBackup`
 * false, and both the Android-12+ extraction rules and the pre-Android-12 rules stay fail-closed:
 * everything excluded, nothing included, so the samsung secret and device-record directories are
 * covered by omission today and a future slice must argue for any file it adds.
 */
class BackupGuardTest {
    private val manifest = File("src/main/AndroidManifest.xml")
    private val extractionRules = File("src/main/res/xml/data_extraction_rules.xml")
    private val backupRules = File("src/main/res/xml/backup_rules.xml")

    @Test
    fun backupExcludesAllTvState() {
        // The manifest opt-out is the primary control.
        val application = document(manifest).getElementsByTagName("application").item(0) as Element
        assertEquals(
            "allowBackup stays false",
            "false",
            application.getAttribute("android:allowBackup"),
        )
        assertEquals(
            "the Android-12+ extraction rules are wired",
            "@xml/data_extraction_rules",
            application.getAttribute("android:dataExtractionRules"),
        )
        assertEquals(
            "the pre-Android-12 rules are wired",
            "@xml/backup_rules",
            application.getAttribute("android:fullBackupContent"),
        )

        // Both rule sets exclude everything and include nothing: fail-closed for every path this
        // app owns, which includes the samsung secret and device-record directories.
        val extraction = document(extractionRules)
        assertEquals(
            "data-extraction-rules is the root",
            "data-extraction-rules",
            extraction.documentElement.tagName,
        )
        assertEquals(
            "no backup include exists in the extraction rules",
            0,
            extraction.getElementsByTagName("include").length,
        )
        val cloud = extraction.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = extraction.getElementsByTagName("device-transfer").item(0) as Element
        assertEquals(EXCLUDED_DOMAINS, excludes(cloud))
        assertEquals(EXCLUDED_DOMAINS, excludes(transfer))
        // The extraction surface never restricts an exclusion with a path attribute: an excluded
        // domain stands for the whole of it, at any depth.
        extractionExcludes(extraction).forEach { rule ->
            assertTrue(
                "the extraction rule for domain=${rule.getAttribute("domain")} must not carry a path",
                !rule.hasAttribute("path"),
            )
        }

        val legacy = document(backupRules)
        assertEquals(
            "full-backup-content is the root",
            "full-backup-content",
            legacy.documentElement.tagName,
        )
        assertEquals(
            "no backup include exists in the legacy rules",
            0,
            legacy.getElementsByTagName("include").length,
        )
        val legacyExcludes =
            (0 until legacy.getElementsByTagName("exclude").length)
                .map { legacy.getElementsByTagName("exclude").item(it) as Element }
                .map { it.getAttribute("domain") }
                .toSet()
        assertEquals(EXCLUDED_DOMAINS, legacyExcludes)
        // The legacy surface pins every exclusion to the root explicitly.
        legacyExcludes(legacy).forEach { rule ->
            assertEquals(
                "the full-backup rule for domain=${rule.getAttribute("domain")} must exclude the root",
                ".",
                rule.getAttribute("path"),
            )
        }
    }

    private fun document(file: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun excludes(section: Element): Set<String> = excludeRules(section).toDomainSet()

    private fun extractionExcludes(document: org.w3c.dom.Document): List<Element> =
        excludeRules(document.getElementsByTagName("cloud-backup").item(0) as Element) +
            excludeRules(document.getElementsByTagName("device-transfer").item(0) as Element)

    private fun legacyExcludes(document: org.w3c.dom.Document): List<Element> =
        excludeRules(document.getElementsByTagName("full-backup-content").item(0) as Element)

    private fun excludeRules(section: Element): List<Element> =
        (0 until section.getElementsByTagName("exclude").length).map {
            section.getElementsByTagName("exclude").item(it) as Element
        }

    private fun List<Element>.toDomainSet(): Set<String> = map { it.getAttribute("domain") }.toSet()

    private companion object {
        /** Every private-storage domain: excluded means nothing of this app can leave the */
        /** device. */
        val EXCLUDED_DOMAINS = setOf("root", "file", "database", "sharedpref", "external")
    }
}
