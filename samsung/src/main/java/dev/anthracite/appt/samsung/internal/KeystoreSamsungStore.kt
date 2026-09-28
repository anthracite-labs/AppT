package dev.anthracite.appt.samsung.internal

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.anthracite.appt.samsung.TvId
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Randomized encryption of one secret payload (docs/architecture/data.md#samsung-secret-record).
 *
 * Production is [KeystoreSamsungStore.AndroidKeystoreCipher]. The seam exists so the file,
 * atomicity, corruption and idempotence behavior of the store is provable on the JVM with a local
 * AES-GCM cipher, while the real Android Keystore round trip is proven by the instrumented test on
 * a managed device (docs/architecture/testing.md).
 */
internal interface SecretCipher {
    /**
     * Encrypts [plaintext]. Returns the IV followed by the GCM body and authentication tag; every
     * call must randomize, so encrypting the same plaintext twice yields different bytes.
     */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Decrypts the [encrypt] output shape. Throws when the key or the ciphertext is unusable. */
    fun decrypt(blob: ByteArray): ByteArray
}

internal fun SecretCipher.encode(secret: PairingSecret): ByteArray = encrypt(secret.encodedPayload())

internal fun SecretCipher.decode(blob: ByteArray): PairingSecret? = decrypt(blob).decodedPayload()

private const val PAIRING_PAYLOAD_VERSION = 1

/** The payload codec is file-private: `PairingSecret` never exposes its serialized shape. */
private fun PairingSecret.encodedPayload(): ByteArray =
    buildJsonObject {
            put("version", PAIRING_PAYLOAD_VERSION)
            token?.let { put("token", it) }
            pin?.let { put("pin", it) }
        }
        .toString()
        .encodeToByteArray()

private fun ByteArray.decodedPayload(): PairingSecret? {
    val root =
        try {
            Json.parseToJsonElement(decodeToString()).jsonObject
        } catch (ignored: IllegalArgumentException) {
            return null
        }
    val version = (root["version"] as? JsonPrimitive)?.intOrNull
    if (version != PAIRING_PAYLOAD_VERSION) return null
    val token = (root["token"] as? JsonPrimitive)?.contentOrNull
    val pin = (root["pin"] as? JsonPrimitive)?.contentOrNull
    if (token == null && pin == null) return null
    return PairingSecret(token = token, pin = pin)
}

/**
 * The saved pairing credential for one television (docs/architecture/data.md#samsung-secret-record).
 *
 * The payload is the pairing token and the SHA-256 SPKI pin, and nothing else. MAC, address and
 * UUID deliberately do not share this type: a serializer for the samsung-private device record
 * cannot sweep the token along with them, and Room, DataStore and licensing code never see this
 * type at all (data.md#structural-barriers).
 *
 * @property token the approval token the television issued, or null when it has issued none yet.
 * @property pin the SHA-256 of the certificate SubjectPublicKeyInfo this television presented when
 *   the pairing was approved, or null for a pairing made on the plaintext channel, whose security
 *   identity is the protocol UUID instead (docs/architecture/connection.md#security-identity).
 */
internal data class PairingSecret(val token: String?, val pin: String?)

/** The result of reading one television's saved secret. */
internal sealed interface StoredSecret {
    /** No saved pairing exists for this id. First contact applies. */
    data object Absent : StoredSecret

    /** The saved pairing was decrypted successfully. */
    data class Available(val secret: PairingSecret) : StoredSecret

    /**
     * A saved pairing exists but could not be decrypted (Keystore key invalidated, undecryptable
     * file). This is `SecretsUnavailable`: no plaintext fallback, no token transmission, and never
     * an empty pairing that pretends the television was never paired
     * (docs/architecture/connection.md#security-identity).
     */
    data object Unavailable : StoredSecret
}

/**
 * The samsung-private device record for one television
 * (docs/architecture/data.md#samsung-private-device-record).
 *
 * It is how a remembered television is reopened at its last address after process death, and how
 * address changes are remembered, without putting an address, MAC, UUID or display name into Room.
 * It never contains the token or the pin. The file carries a format version; unknown members are
 * ignored on read and missing ones fall back to safe defaults
 * (data.md#samsung-private-file-migration).
 *
 * The capability evidence this slice can honestly write is the adopted control path itself: the
 * TLS flag, the adopted-channel flag, and whether the television exposes a stable identity. The
 * rest of the record's documented members — rejected keys, wake-failure count, model, firmware,
 * MAC — are written by the slices that produce and read that evidence (S11, S12, S13); the format
 * version and `ignoreUnknownKeys` are exactly what lets those members arrive without a migration.
 *
 * @property uuid the television's normalized protocol UUID, or null when it exposes none. The
 *   plaintext channel's saved security identity (connection.md#security-identity).
 * @property lastAddress the address the television last answered on. Internal only.
 * @property tls true when the pairing was approved on the adopted TLS remote channel, so a saved
 *   pin exists or is expected; false when the pairing rides the plaintext fallback.
 * @property adoptedChannel true when the television speaks the adopted control channel at all.
 * @property displayName the candidate display name from device-info, scrubbed, or null.
 * @property stableIdentity true when the television exposed a protocol UUID.
 */
internal data class SamsungDeviceRecord(
    val uuid: String?,
    val lastAddress: String,
    val tls: Boolean,
    val adoptedChannel: Boolean,
    val displayName: String?,
    val stableIdentity: Boolean,
    val version: Int = DEVICE_RECORD_VERSION,
) {
    companion object {
        /** data.md#samsung-private-file-migration: the file format this writer owns. */
        const val DEVICE_RECORD_VERSION = 1
    }
}

/**
 * The durable Samsung pairing store seam (docs/architecture/data.md#storage-classes).
 *
 * Production is [KeystoreSamsungStore]; the scripted test adapter is in-memory, with a mode that
 * fails decryption (docs/architecture/testing.md#fake-adapters). This is internal to the `samsung`
 * module: `app` has no API that accepts the ciphertext type and does not build these paths
 * (data.md#samsung-secret-record).
 */
internal interface SamsungSecretStore {
    /**
     * Reads one television's saved secret. A missing file is [StoredSecret.Absent]; an
     * undecryptable one is [StoredSecret.Unavailable], never an exception and never a fabricated
     * empty pairing.
     */
    fun loadSecret(tvId: TvId): StoredSecret

    /**
     * Encrypts and atomically replaces the saved secret (temporary file in the same directory,
     * fsync, rename). Plaintext is never staged outside this call.
     */
    fun saveSecret(tvId: TvId, secret: PairingSecret)

    /**
     * Deletes the saved secret for one television. Used by the explicit `confirmRepair` re-pair.
     * The samsung-private device record is kept: address continuity is not approval material.
     * Idempotent. Throws only when deletion genuinely failed, so the caller stays fail-closed.
     */
    fun discardSecret(tvId: TvId)

    /** Reads one television's samsung-private device record, or null when there is none. */
    fun loadDevice(tvId: TvId): SamsungDeviceRecord?

    /** Atomically replaces one television's samsung-private device record. */
    fun saveDevice(tvId: TvId, record: SamsungDeviceRecord)

    /**
     * Removes this phone's saved Samsung relationship for one television: the secret and the
     * samsung-private device record. Idempotent. Throws only when deletion genuinely failed.
     */
    fun forget(tvId: TvId)

    /** The ids that have a samsung-private record. Not a UI list and not an account concept. */
    fun rememberedIds(): Set<TvId>
}

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
            }
            .toString()

    fun parse(text: String): SamsungDeviceRecord? {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (ignored: IllegalArgumentException) {
                return null
            }
        val lastAddress = root.textOrNull("lastAddress") ?: return null
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        if (version != null && version > SamsungDeviceRecord.DEVICE_RECORD_VERSION) return null
        return SamsungDeviceRecord(
            version = version ?: SamsungDeviceRecord.DEVICE_RECORD_VERSION,
            uuid = root.textOrNull("uuid"),
            lastAddress = lastAddress,
            tls = root.boolean("tls") ?: false,
            adoptedChannel = root.boolean("adoptedChannel") ?: true,
            displayName = root.textOrNull("displayName"),
            stableIdentity = root.boolean("stableIdentity") ?: false,
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
 *   `setUserAuthenticationRequired(false)` so reopening never demands a biometric prompt, randomized
 *   encryption, StrongBox when available with TEE fallback (data.md#samsung-secret-record).
 * * File layout: magic `APS1`, one format-version byte, the GCM IV, the GCM body.
 * * Write: encrypt, temporary file in the same directory, fsync, rename. A crash mid-write leaves
 *   the previous secret, and plaintext is never staged in `cacheDir` or anywhere else.
 * * An undecryptable file is never replaced with an empty pairing: it reads as
 *   [StoredSecret.Unavailable] and stays on disk until pairing succeeds again or `forget` removes
 *   it (data.md#samsung-secret-record).
 */
internal class KeystoreSamsungStore private constructor(
    private val secretsDir: File,
    private val devicesDir: File,
    private val cipher: SecretCipher,
) : SamsungSecretStore {

    private val lock = Any()

    override fun loadSecret(tvId: TvId): StoredSecret =
        synchronized(lock) {
            val file = secretFile(tvId)
            if (!file.exists()) return@synchronized StoredSecret.Absent
            val blob =
                try {
                    file.readBytes()
                } catch (ignored: IOException) {
                    // An unreadable file is undecryptable as far as this caller can claim.
                    return@synchronized StoredSecret.Unavailable
                }
            val secret =
                try {
                    parseEnvelope(blob)?.let(cipher::decode)
                } catch (ignored: Exception) {
                    // Keystore key invalidated, device lock change, corrupt body: fail closed.
                    null
                }
            secret?.let(StoredSecret::Available) ?: StoredSecret.Unavailable
        }

    override fun saveSecret(tvId: TvId, secret: PairingSecret) {
        synchronized(lock) {
            val blob = ENVELOPE_MAGIC + byteArrayOf(ENVELOPE_VERSION) + cipher.encode(secret)
            atomicWrite(secretFile(tvId), blob)
        }
    }

    override fun discardSecret(tvId: TvId) {
        synchronized(lock) { deleteExisting(secretFile(tvId)) }
    }

    override fun loadDevice(tvId: TvId): SamsungDeviceRecord? =
        synchronized(lock) {
            val file = deviceFile(tvId)
            if (!file.exists()) return@synchronized null
            try {
                DeviceRecordJson.parse(file.readBytes().decodeToString())
            } catch (ignored: IOException) {
                // data.md#corruption-recovery: a device record is re-created from the next
                // discovery or device-info read; it is never worth failing a session over.
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
            deleteExisting(secretFile(tvId))
            deleteExisting(deviceFile(tvId))
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
        ensureDirectory(target.parentFile)
        val temporary =
            File.createTempFile(target.nameWithoutExtension, TEMP_SUFFIX, target.parentFile)
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

    /**
     * The Android Keystore cipher behind alias `appt.samsung.v1`
     * (docs/architecture/data.md#samsung-secret-record).
     *
     * The key is non-exportable, does not require user authentication, and every encryption is
     * randomized. StrongBox is requested first and the TEE is the fallback, which is the accepted
     * generation order: a device that later loses StrongBox still decrypts through the normal key,
     * because the key stays in the hardware that generated it.
     */
    internal class AndroidKeystoreCipher(private val alias: String = KEYSTORE_ALIAS) : SecretCipher {

        private val lock = Any()

        override fun encrypt(plaintext: ByteArray): ByteArray =
            synchronized(lock) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val iv = cipher.iv
                check(iv.size == IV_BYTES) { "unexpected GCM IV length" }
                iv + cipher.doFinal(plaintext)
            }

        override fun decrypt(blob: ByteArray): ByteArray =
            synchronized(lock) {
                if (blob.size <= IV_BYTES) throw SecretStoreException("ciphertext too short")
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_BYTES),
                )
                cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            }

        private fun key(): SecretKey {
            val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
            return generateKey(keystore)
        }

        private fun generateKey(keystore: KeyStore): SecretKey {
            if (keystore.containsAlias(alias)) {
                // An alias entry that is not a usable SecretKey is a Keystore-level fault; surface
                // it as unavailable rather than silently overwriting security material.
                throw SecretStoreException("keystore alias exists but is not a usable secret key")
            }
            val generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            try {
                generator.init(spec(strongBox = true))
                return generator.generateKey()
            } catch (ignored: InvalidAlgorithmParameterException) {
                // data.md: StrongBox where available, otherwise TEE.
            }
            generator.init(spec(strongBox = false))
            return generator.generateKey()
        }

        private fun spec(strongBox: Boolean): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .apply {
                    if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setIsStrongBoxBacked(true)
                    }
                }
                .setBlockMode(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()

        internal companion object {
            /** data.md: the Keystore alias for the Samsung pairing secret. */
            const val KEYSTORE_ALIAS = "appt.samsung.v1"
        }
    }

    companion object {
        /** data.md: the magic at the head of every secret file. */
        internal val ENVELOPE_MAGIC = "APS1".toByteArray(Charsets.US_ASCII)

        /** The envelope version this writer owns. */
        internal const val ENVELOPE_VERSION: Byte = 1

        internal const val IV_BYTES = 12

        internal const val GCM_TAG_BITS = 128

        private const val KEY_SIZE_BITS = 256

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"

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
         * The secret file envelope: magic `APS1`, one format-version byte, then the cipher output
         * (IV and GCM body). An unknown magic or version is an undecryptable file, not a crash.
         */
        private fun parseEnvelope(blob: ByteArray): ByteArray? {
            if (blob.size <= ENVELOPE_MAGIC.size + 1) return null
            if (!blob.copyOfRange(0, ENVELOPE_MAGIC.size).contentEquals(ENVELOPE_MAGIC)) return null
            if (blob[ENVELOPE_MAGIC.size] != ENVELOPE_VERSION) return null
            return blob.copyOfRange(ENVELOPE_MAGIC.size + 1, blob.size)
        }
    }
}
