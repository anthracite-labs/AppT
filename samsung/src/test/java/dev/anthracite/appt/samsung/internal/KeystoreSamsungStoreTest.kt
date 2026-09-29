package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.security.GeneralSecurityException
import java.security.Key
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The durable secret store, against the real envelope, directory and JSON formats
 * (docs/architecture/data.md), with the Keystore itself replaced by an AES-GCM [SecretCipher] test
 * double. The Android Keystore round trip is proven by the instrumented test in `:app`'s
 * androidTest source set; this suite proves everything that is plain JVM file and failure
 * discipline: atomicity, fail-closed decryption, no token or pin outside the secret file, and
 * samsung code producing no captured log output.
 */
class KeystoreSamsungStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val tvId = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e")
    private val mintedId = TvId(TvIdentity.MINTED_PREFIX + "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e")

    private val secret = PairingSecret(token = "planted-token-value", pin = "planted-pin-value")

    private val record =
        SamsungDeviceRecord(
            uuid = tvId.value,
            lastAddress = "10.0.0.0",
            tls = true,
            adoptedChannel = true,
            displayName = "Living Room TV",
            stableIdentity = true,
        )

    private fun newStore(cipher: SecretCipher = LocalAesGcmCipher()): KeystoreSamsungStore =
        KeystoreSamsungStore.forTesting(
            secretsDir = File(temporaryFolder.root, "samsung-secrets/v1"),
            devicesDir = File(temporaryFolder.root, "samsung-device/v1"),
            cipher = cipher,
        )

    // --- persistence -------------------------------------------------------------------

    @Test
    fun aSavedSecretSurvivesAProcessRestart() {
        val store = newStore()
        store.saveSecret(tvId, secret)
        store.saveDevice(tvId, record)

        // A "new process": a fresh store instance over the same directories.
        val reopened = newStore()
        assertEquals(StoredSecret.Available(secret), reopened.loadSecret(tvId))
        assertEquals(record, reopened.loadDevice(tvId))
        assertEquals(setOf(tvId), reopened.rememberedIds())
    }

    @Test
    fun aMintedIdPersistsAndLoadsUnderTheSameRules() {
        val store = newStore()
        store.saveSecret(mintedId, secret)
        assertEquals(StoredSecret.Available(secret), newStore().loadSecret(mintedId))
    }

    // --- envelope and atomicity ----------------------------------------------------------

    @Test
    fun theSecretFileIsEnvelopeSealedAndLeavesNoTemporaryFiles() {
        val store = newStore()
        store.saveSecret(tvId, secret)

        val files = secretFiles()
        assertEquals("one file per television", 1, files.size)
        val bytes = files.single().readBytes()
        assertEquals("the magic is APS1", "APS1", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("the envelope version is 1", 1, bytes[4].toInt())
        assertFalse(
            "the body is GCM-sealed, not plaintext",
            String(bytes, Charsets.US_ASCII).contains("planted"),
        )
        assertTrue("a fresh IV every save", bytes.copyOfRange(5, 5 + 12).any { it != 0.toByte() })
        // No temporary files remain beside the secret: the directory holds exactly it.
        val secretsDirectory = File(temporaryFolder.root, "samsung-secrets/v1")
        assertEquals(
            "no temporary files remain",
            listOf(files.single().name),
            secretsDirectory.list()?.toList(),
        )
    }

    @Test
    fun aSecondSaveReplacesTheFileAtomically() {
        val store = newStore()
        store.saveSecret(tvId, secret)
        val rotated = PairingSecret(token = "rotated-token-value", pin = secret.pin)
        store.saveSecret(tvId, rotated)

        assertEquals("no backup or temp litter", 1, secretFiles().size)
        assertEquals(StoredSecret.Available(rotated), newStore().loadSecret(tvId))
    }

    // --- unknown ids and keystore faults: fail closed, never a crash ------------------------

    @Test
    fun anUnknownIdReadsFailClosedInsteadOfThrowing() {
        val store = newStore()
        val unknown = TvId("not-a-stored-id")
        // samsung-interface.md#open: an unknown id moves to Unreachable, which needs these reads
        // to answer, not to throw. Writes keep the loud guard.
        assertEquals(StoredSecret.Absent, store.loadSecret(unknown))
        assertNull(store.loadDevice(unknown))
        store.forget(unknown)
        assertThrows(IllegalArgumentException::class.java) { store.saveSecret(unknown, secret) }
        assertThrows(IllegalArgumentException::class.java) { store.saveDevice(unknown, record) }
    }

    @Test
    fun aKeystoreEncryptionFailureSurfacesAsTheStoreFailureType() {
        val store = newStore(FailingKeystoreCipher(GeneralSecurityException("key invalidated")))
        val thrown =
            assertThrows(SecretStoreException::class.java) { store.saveSecret(tvId, secret) }
        assertTrue(thrown.cause is GeneralSecurityException)
    }

    @Test
    fun aKeystoreRuntimeFailureSurfacesAsTheStoreFailureType() {
        // AndroidKeyStore signals keystore-side faults at runtime with ProviderException.
        val store = newStore(FailingKeystoreCipher(ProviderException("keystore fault")))
        assertThrows(SecretStoreException::class.java) { store.saveSecret(tvId, secret) }
    }

    @Test
    fun aKeystoreDecryptRuntimeFailureSurfacesUnavailable() {
        val good = newStore()
        good.saveSecret(tvId, secret)
        val failing = newStore(FailingKeystoreCipher(ProviderException("keystore fault")))
        assertEquals(StoredSecret.Unavailable, failing.loadSecret(tvId))
    }

    // --- fail-closed decryption ------------------------------------------------------------

    @Test
    fun aCorruptSecretFileSurfacesUnavailableAndNeverResetsThePairing() {
        val store = newStore()
        store.saveSecret(tvId, secret)
        store.saveDevice(tvId, record)
        secretFiles().single().writeBytes("not-an-envelope".toByteArray())

        val reopened = newStore()
        assertEquals(StoredSecret.Unavailable, reopened.loadSecret(tvId))
        // Fail-closed, not fail-open: no invented empty pairing, and the device record survives.
        assertEquals(record, reopened.loadDevice(tvId))

        // Forget is the only way out, and it removes both records.
        reopened.forget(tvId)
        assertEquals(StoredSecret.Absent, reopened.loadSecret(tvId))
        assertNull(reopened.loadDevice(tvId))
    }

    @Test
    fun anUnknownEnvelopeVersionOrMagicSurfacesUnavailable() {
        val store = newStore()
        store.saveSecret(tvId, secret)

        val file = secretFiles().single()
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf().also { it[4] = 9 }) // unknown version
        assertEquals(StoredSecret.Unavailable, newStore().loadSecret(tvId))

        file.writeBytes(bytes.copyOf().also { it[0] = 'X'.code.toByte() }) // unknown magic
        assertEquals(StoredSecret.Unavailable, newStore().loadSecret(tvId))
    }

    @Test
    fun anUndecryptableKeystoreSurfacesUnavailable() {
        val store = newStore()
        store.saveSecret(tvId, secret)

        // The same directories, but the Keystore can no longer decrypt (invalidated key).
        val reopened = newStore(LocalAesGcmCipher(keyId = "invalidated-keystore"))
        assertEquals(StoredSecret.Unavailable, reopened.loadSecret(tvId))
    }

    // --- device record -----------------------------------------------------------------

    @Test
    fun theDeviceRecordCarriesNoTokenOrPinOnDisk() {
        val store = newStore()
        store.saveSecret(tvId, secret)
        store.saveDevice(tvId, record)

        val deviceFiles =
            File(temporaryFolder.root, "samsung-device/v1")
                .walkTopDown()
                .filter { it.isFile }
                .toList()
        assertEquals(1, deviceFiles.size)
        val text = deviceFiles.single().readText()
        assertFalse(text.contains("planted-token-value"))
        assertFalse(text.contains("planted-pin-value"))
        assertFalse(text.lowercase().contains("token"))
        assertFalse(text.lowercase().contains("pin"))
    }

    @Test
    fun aDeviceRecordFromAnUnknownVersionIsIgnored() {
        val store = newStore()
        store.saveDevice(tvId, record)
        val file = File(temporaryFolder.root, "samsung-device/v1/${tvId.value}.json")
        // DeviceRecordJson writes compact JSON, so the rewrite targets the exact emitted form.
        file.writeText(file.readText().replace("\"version\":1", "\"version\":2"))

        assertNull(newStore().loadDevice(tvId))
    }

    @Test
    fun aCorruptDeviceRecordIsIgnoredAndRecreatedByTheNextDiscoverySave() {
        val store = newStore()
        store.saveDevice(tvId, record)
        val file = File(temporaryFolder.root, "samsung-device/v1/${tvId.value}.json")
        file.writeText("{not json")

        assertNull(newStore().loadDevice(tvId))
        // The next approval or discovery write recreates it; nothing wedges.
        val reopened = newStore()
        reopened.saveDevice(tvId, record)
        assertEquals(record, reopened.loadDevice(tvId))
    }

    // --- forget -------------------------------------------------------------------------

    @Test
    fun forgetRemovesEverythingAndIsIdempotent() {
        val store = newStore()
        store.saveSecret(tvId, secret)
        store.saveDevice(tvId, record)

        store.forget(tvId)
        store.forget(tvId)
        assertEquals(StoredSecret.Absent, store.loadSecret(tvId))
        assertNull(store.loadDevice(tvId))
        assertEquals(0, store.rememberedIds().size)
        assertEquals(
            "both directories are empty",
            0,
            temporaryFolder.root.walkTopDown().filter { it.isFile }.count(),
        )
    }

    @Test
    fun forgetNeverTouchesAnotherTelevision() {
        val other = TvId("aaaa1111-2222-4333-8444-555555555555")
        val store = newStore()
        // Both televisions are completely remembered: secret and samsung-private device record.
        store.saveSecret(tvId, secret)
        store.saveDevice(tvId, record)
        store.saveSecret(other, secret)
        store.saveDevice(other, record.copy(uuid = other.value))

        store.forget(tvId)
        assertEquals(StoredSecret.Available(secret), store.loadSecret(other))
        assertEquals(setOf(other), store.rememberedIds())
    }

    // --- captured log check ---------------------------------------------------------------

    @Test
    fun samsungStoreOperationsProduceNoCapturedLogOutput() {
        val stdout = System.out
        val stderr = System.err
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured))
        System.setErr(PrintStream(captured))
        try {
            val store = newStore()
            store.saveSecret(tvId, secret)
            assertEquals(StoredSecret.Available(secret), store.loadSecret(tvId))
            store.saveDevice(tvId, record)
            store.loadDevice(tvId)
            store.discardSecret(tvId)
            store.forget(tvId)
        } finally {
            System.out.flush()
            System.err.flush()
            System.setOut(stdout)
            System.setErr(stderr)
        }
        assertEquals(
            "samsung module code never calls android.util.Log or prints: the planted token and pin " +
                "must be absent from anything a captured log could show",
            "",
            captured.toString().trim(),
        )
        assertFalse(captured.toString().contains("planted-token-value"))
        assertFalse(captured.toString().contains("planted-pin-value"))
    }

    // --- the local cipher double -----------------------------------------------------------

    @Test
    fun theLocalCipherRoundTripsIndependently() {
        val cipher = LocalAesGcmCipher()
        val blob = cipher.encrypt("payload".toByteArray())
        assertArrayEquals("payload".toByteArray(), cipher.decrypt(blob))
        assertFalse(
            "fresh IVs per call",
            blob.contentEquals(cipher.encrypt("payload".toByteArray())),
        )
    }

    private fun secretFiles(): List<File> =
        File(temporaryFolder.root, "samsung-secrets/v1").walkTopDown().filter { it.isFile }.toList()

    /**
     * A JVM AES-GCM cipher standing in for the Android Keystore: the same 256-bit AES-GCM shape.
     * Like the real Keystore, the key outlives any store instance, so a reopened store decrypts
     * what a previous one sealed; a different [keyId] models an invalidated or replaced key.
     */
    /** Stands in for a Keystore that refuses its side of the cipher contract. */
    private class FailingKeystoreCipher(private val thrown: Exception) : SecretCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = throw thrown

        override fun decrypt(blob: ByteArray): ByteArray = throw thrown
    }

    private class LocalAesGcmCipher(private val keyId: String = DEVICE_KEYSTORE) : SecretCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val body = cipher(Cipher.ENCRYPT_MODE, iv).doFinal(plaintext)
            return iv + body
        }

        override fun decrypt(blob: ByteArray): ByteArray {
            val iv = blob.copyOfRange(0, 12)
            return cipher(Cipher.DECRYPT_MODE, iv).doFinal(blob.copyOfRange(12, blob.size))
        }

        private fun key(): Key = KEYS.computeIfAbsent(keyId) { generateNewKey() }

        private fun cipher(
            mode: Int,
            iv: ByteArray,
        ): Cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, key(), GCMParameterSpec(128, iv))
            }

        companion object {
            /** The id every ordinary store instance shares, like the device's one Keystore. */
            const val DEVICE_KEYSTORE = "device-keystore"

            private val KEYS = java.util.concurrent.ConcurrentHashMap<String, Key>()

            private fun generateNewKey(): Key =
                KeyGenerator.getInstance("AES")
                    .apply { init(256, SecureRandom()) }
                    .generateKey()
        }
    }
}
