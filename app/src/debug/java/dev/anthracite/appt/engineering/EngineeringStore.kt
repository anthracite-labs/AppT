package dev.anthracite.appt.engineering

import java.io.File
import java.io.IOException

/**
 * The bounded local evidence file. Engineering-only: it lives under the platform no-backup
 * directory, is rewritten only when the operator records or clears an observation, and is never
 * uploaded. Only the compile-time tokens of [EngineeringEvidenceFormat] can be written, so the file
 * itself cannot hold a forbidden value either.
 */
internal class EngineeringStore(private val directory: File) {
    fun load(): List<EngineeringObservation> {
        val file = file()
        if (!file.isFile) return emptyList()
        return try {
            EngineeringEvidenceFormat.decode(file.readText())
        } catch (_: IOException) {
            emptyList()
        }
    }

    fun save(observations: List<EngineeringObservation>): Boolean {
        if (!directory.isDirectory && !directory.mkdirs()) return false
        return try {
            file().writeText(EngineeringEvidenceFormat.encode(observations))
            true
        } catch (_: IOException) {
            false
        }
    }

    fun clear() {
        file().delete()
    }

    private fun file(): File = File(directory, FILE_NAME)

    companion object {
        const val DIRECTORY = "engineering-verification"
        const val FILE_NAME = "engineering-evidence.tsv"
    }
}
