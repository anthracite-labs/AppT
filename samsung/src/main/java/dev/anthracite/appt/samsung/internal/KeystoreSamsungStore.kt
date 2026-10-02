package dev.anthracite.appt.samsung.internal

import android.content.Context
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.TvId
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Thrown when the store itself cannot do its job (key unavailable, directory unavailable, rename
 * failed). The session surfaces this as `SecretsUnavailable`; it is never a reason to fall back to
 * plaintext (data.md#keystore-key-invalidation-rotation-and-recovery).
 */
internal class SecretStoreException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * The typed samsung-private device record serialization
 * (docs/architecture/data.md#samsung-private-device-record).
 *
 * Unknown members are ignored on read; missing ones fall back to safe defaults. The record carries
 * the format version and never carries the token or the pin: those share no type with this record
 * (data.md#structural-barriers).
 */
internal object DeviceRecordJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: SamsungDeviceRecord): String =
        buildJsonObject {
                put("version", record.version)
                record.uuid?.let { put("uuid", it) }
                put("lastAddress", record.lastAddress)
                put("tls", record.tls)
                put("adoptedChannel", record.adoptedChannel)
                record.displayName?.let { put("displayName", it) }
                put("stableIdentity", record.stableIdentity)
                put(
                    "rejectedKeys",
                    buildJsonArray {
                        record.rejectedKeys.sortedBy(RemoteKey::ordinal).forEach {
                            add(JsonPrimitive(it.name))
                        }
                    },
                )
            }
            .toString()

    fun parse(text: String): SamsungDeviceRecord? {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (ignored: IllegalArgumentException) {
                null
            }
        val version = (root?.get("version") as? JsonPrimitive)?.intOrNull
        val lastAddress = root?.textOrNull("lastAddress") ?: return null
        if (version != null && version > SamsungDeviceRecord.DEVICE_RECORD_VERSION) return null
        return SamsungDeviceRecord(
            version = version ?: SamsungDeviceRecord.DEVICE_RECORD_VERSION,
            uuid = root.textOrNull("uuid"),
            lastAddress = lastAddress,
            tls = root.boolean("tls") ?: false,
            adoptedChannel = root.boolean("adoptedChannel") ?: true,
            displayName = root.textOrNull("displayName"),
            stableIdentity = root.boolean("stableIdentity") ?: false,
            rejectedKeys =
                (root["rejectedKeys"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { value ->
                        (value as? JsonPrimitive)?.contentOrNull?.let { name ->
                            RemoteKey.entries.firstOrNull { it.name == name }
                        }
                    }
                    .toSet(),
        )
    }

    private fun JsonObject.textOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}

/**
 * The production [SamsungSecretStore]: Keystore-encrypted secret files and typed JSON device
 * records, both under `noBackupFilesDir` (docs/architecture/data.md#storage-classes).
 *
 * Everything the accepted contract pins is here:
 * * Secret location `noBackupFilesDir/samsung-secrets/v1/<tvId>`, device record
 *   `noBackupFilesDir/samsung-device/v1/<tvId>.json`. Both directories are excluded from backup and
 *   device transfer by the platform (`getNoBackupFilesDir`) and again by the app's fail-closed
 *   backup rules (data.md#backup-and-device-transfer).
 * * Keystore alias `appt.samsung.v1`, AES-GCM, non-exportable,
 *   `setUserAuthenticationRequired(false)` so reopening never demands a biometric prompt,
 *   randomized encryption, StrongBox when available with TEE fallback
 *   (data.md#samsung-secret-record).
 * * File layout: magic `APS1`, one format-version byte, the GCM IV, the GCM body.
 * * Write: encrypt, temporary file in the same directory, fsync, rename. A crash mid-write leaves
 *   the previous secret, and plaintext is never staged in `cacheDir` or anywhere else.
 * * An undecryptable file is never replaced with an empty pairing: it reads as
 *   [StoredSecret.Unavailable] and stays on disk until pairing succeeds again or `forget` removes
 *   it (data.md#samsung-secret-record).
 */
internal class KeystoreSamsungStore
private constructor(
    private val secretsDir: File,
    private val devicesDir: File,
    private val cipher: SecretCipher,
) : SamsungSecretStore {

    private val lock = Any()

    override fun loadSecret(tvId: TvId): StoredSecret =
        synchronized(lock) {
            val name = existingStoredName(tvId) ?: return@synchronized StoredSecret.Absent
            val directory =
                try {
                    ensureDirectory(secretsDir)
                } catch (ignored: SecretStoreException) {
                    return@synchronized StoredSecret.Unavailable
                }
            val file = File(directory, name)
            if (!file.exists()) return@synchronized StoredSecret.Absent
            val blob =
                try {
                    file.readBytes()
                } catch (ignored: IOException) {
                    return@synchronized StoredSecret.Unavailable
                }
            val secret =
                try {
                    parseEnvelope(blob)?.let(cipher::decode)
                } catch (ignored: GeneralSecurityException) {
                    null
                } catch (ignored: ProviderException) {
                    null
                } catch (ignored: IOException) {
                    null
                }
            secret?.let(StoredSecret::Available) ?: StoredSecret.Unavailable
        }

    override fun saveSecret(tvId: TvId, secret: PairingSecret) {
        synchronized(lock) {
            val blob =
                try {
                    ENVELOPE_MAGIC + byteArrayOf(ENVELOPE_VERSION) + cipher.encode(secret)
                } catch (e: GeneralSecurityException) {
                    throw SecretStoreException("keystore encryption failed", e)
                } catch (e: ProviderException) {
                    throw SecretStoreException("keystore encryption failed", e)
                }
            atomicWrite(secretFile(tvId), blob)
        }
    }

    override fun discardSecret(tvId: TvId) {
        synchronized(lock) {
            val name = existingStoredName(tvId) ?: return@synchronized
            deleteExisting(File(ensureDirectory(secretsDir), name))
        }
    }

    override fun loadDevice(tvId: TvId): SamsungDeviceRecord? =
        synchronized(lock) {
            val name = existingStoredName(tvId) ?: return@synchronized null
            val directory =
                try {
                    ensureDirectory(devicesDir)
                } catch (ignored: SecretStoreException) {
                    return@synchronized null
                }
            val file = File(directory, name + DEVICE_SUFFIX)
            if (!file.exists()) return@synchronized null
            try {
                DeviceRecordJson.parse(file.readBytes().decodeToString())
            } catch (ignored: IOException) {
                null
            }
        }

    override fun saveDevice(tvId: TvId, record: SamsungDeviceRecord) {
        synchronized(lock) {
            atomicWrite(deviceFile(tvId), DeviceRecordJson.encode(record).encodeToByteArray())
        }
    }

    override fun forget(tvId: TvId) {
        synchronized(lock) {
            val name = existingStoredName(tvId) ?: return@synchronized
            deleteExisting(File(ensureDirectory(secretsDir), name))
            deleteExisting(File(ensureDirectory(devicesDir), name + DEVICE_SUFFIX))
        }
    }

    override fun rememberedIds(): Set<TvId> =
        synchronized(lock) {
            val ids = mutableSetOf<TvId>()
            if (devicesDir.isDirectory) {
                devicesDir
                    .listFiles { file -> file.isFile && file.name.endsWith(DEVICE_SUFFIX) }
                    ?.forEach { file ->
                        val id = file.name.removeSuffix(DEVICE_SUFFIX)
                        if (TvIdentity.isValidStoredId(id)) ids += TvId(id)
                    }
            }
            ids
        }

    /**
     * Encrypt, write a temporary file in the same directory, fsync, rename
     * (data.md#samsung-secret-record). The rename is atomic within one directory, so a reader sees
     * either the previous file or the new one, never a hybrid.
     */
    private fun atomicWrite(target: File, bytes: ByteArray) {
        val parent =
            target.parentFile
                ?: throw SecretStoreException("no parent directory for ${target.name}")
        ensureDirectory(parent)
        val temporary = File.createTempFile(target.nameWithoutExtension, TEMP_SUFFIX, parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                throw SecretStoreException("atomic rename failed")
            }
        } finally {
            temporary.delete()
        }
    }

    private fun secretFile(tvId: TvId): File =
        File(ensureDirectory(secretsDir), storedFileName(tvId))

    private fun deviceFile(tvId: TvId): File =
        File(ensureDirectory(devicesDir), storedFileName(tvId) + DEVICE_SUFFIX)

    private fun ensureDirectory(directory: File): File {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw SecretStoreException("cannot create ${directory.name}")
        }
        return directory
    }

    private fun deleteExisting(file: File) {
        if (file.exists() && !file.delete()) {
            throw SecretStoreException("cannot delete ${file.name}")
        }
    }

    companion object {
        /** data.md: the magic at the head of every secret file. */
        internal val ENVELOPE_MAGIC = "APS1".toByteArray(Charsets.US_ASCII)

        /** The envelope version this writer owns. */
        internal const val ENVELOPE_VERSION: Byte = 1

        private const val SECRETS_DIR = "samsung-secrets/v1"

        private const val DEVICES_DIR = "samsung-device/v1"

        private const val DEVICE_SUFFIX = ".json"

        private const val TEMP_SUFFIX = ".tmp"

        /** The production store: real Keystore cipher, real `noBackupFilesDir` locations. */
        fun fromContext(context: Context): KeystoreSamsungStore {
            val application = context.applicationContext
            return KeystoreSamsungStore(
                secretsDir = File(application.noBackupFilesDir, SECRETS_DIR),
                devicesDir = File(application.noBackupFilesDir, DEVICES_DIR),
                cipher = AndroidKeystoreCipher(),
            )
        }

        /** Visible for the JVM file-behavior tests; never used by production callers. */
        internal fun forTesting(secretsDir: File, devicesDir: File, cipher: SecretCipher) =
            KeystoreSamsungStore(secretsDir, devicesDir, cipher)

        private fun storedFileName(tvId: TvId): String {
            require(TvIdentity.isValidStoredId(tvId.value)) { "unusable television id" }
            return tvId.value
        }

        /**
         * The file name for reads and deletes: an id this store could never have written names no
         * file, so those fail closed (absent/null/no-op) instead of throwing from a read path.
         * Writes keep the loud [storedFileName] require.
         */
        private fun existingStoredName(tvId: TvId): String? =
            tvId.value.takeIf { TvIdentity.isValidStoredId(it) }

        /**
         * The secret file envelope: magic `APS1`, one format-version byte, then the cipher output
         * (IV and GCM body). An unknown magic or version is an undecryptable file, not a crash.
         */
        private fun parseEnvelope(blob: ByteArray): ByteArray? =
            blob
                .takeIf { it.size > ENVELOPE_MAGIC.size + 1 }
                ?.takeIf { it.copyOfRange(0, ENVELOPE_MAGIC.size).contentEquals(ENVELOPE_MAGIC) }
                ?.takeIf { it[ENVELOPE_MAGIC.size] == ENVELOPE_VERSION }
                ?.let { it.copyOfRange(ENVELOPE_MAGIC.size + 1, it.size) }
    }
}
