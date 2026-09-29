package dev.anthracite.appt.samsung.internal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
        (keystore.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return generateKey(keystore)
    }

    private fun generateKey(keystore: KeyStore): SecretKey {
        if (keystore.containsAlias(alias)) {
            // An alias entry that is not a usable SecretKey is a Keystore-level fault; surface
            // it as unavailable rather than silently overwriting security material.
            throw SecretStoreException("keystore alias exists but is not a usable secret key")
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        try {
            generator.init(spec(strongBox = true))
            return generator.generateKey()
        } catch (ignored: StrongBoxUnavailableException) {
            // data.md: StrongBox where available, otherwise TEE. The platform signals a missing
            // security enclave with StrongBoxUnavailableException (a ProviderException subclass),
            // so catching only InvalidAlgorithmParameterException never triggered this fallback.
        } catch (ignored: InvalidAlgorithmParameterException) {
            // A spec this module's own builder produced cannot be the fault; fall back too.
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
                // minSdk 29 is past P, so the StrongBox API is always present and the SDK_INT
                // guard lint flags as obsolete is dead code. Hardware availability is still
                // runtime-negotiated: a device without StrongBox throws
                // InvalidAlgorithmParameterException here and the TEE fallback generates the key.
                if (strongBox) {
                    setIsStrongBoxBacked(true)
                }
            }
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()

    internal companion object {
        /** data.md: the Keystore alias for the Samsung pairing secret. */
        const val KEYSTORE_ALIAS = "appt.samsung.v1"

        /** The envelope's GCM IV width, shared with the sealed-file format. */
        internal const val IV_BYTES = 12

        internal const val GCM_TAG_BITS = 128

        private const val KEY_SIZE_BITS = 256

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
