package dev.anthracite.appt.samsung

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The instrumented half of the S04 secret-store proof (Issue #91, docs/architecture/data.md).
 *
 * The JVM suite in `:samsung` proves the envelope, the directory layout, the atomic replace, the
 * fail-closed decryption and the captured-log discipline against a local AES-GCM stand-in. What no
 * JVM can prove is the platform behaviour the production store is built on — that the Android
 * Keystore accepts exactly the key specification the store requests, that the key really is
 * non-exportable, and that an encrypt/decrypt round trip through it works on the device's actual
 * StrongBox or TEE implementation. This test proves that, on the device, against the same alias and
 * the same specification shape production uses (`appt.samsung.v1`, 256-bit AES-GCM, no user
 * authentication, randomized encryption, StrongBox preferred with a plain-TEE fallback).
 *
 * It uses a distinct test alias and deletes it afterwards, so the app's own saved pairings are
 * never touched.
 */
@RunWith(AndroidJUnit4::class)
class SamsungKeystoreContractTest {
    private val alias = "appt.samsung.v1.instrumented-test"

    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun generateKey(strongBox: Boolean) {
        val keystore = keystore()
        if (keystore.containsAlias(alias)) {
            keystore.deleteEntry(alias)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder =
            KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
        if (strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        generator.init(builder.build())
        generator.generateKey()
    }

    private fun key(): SecretKey = keystore().getKey(alias, null) as SecretKey

    @Test
    fun theProductionKeySpecRoundTripsOnThisDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var usedStrongBox = false
        try {
            try {
                generateKey(strongBox = true)
                usedStrongBox = true
            } catch (strongBoxUnavailable: Exception) {
                // The documented fallback: StrongBox where the device has it, plain TEE otherwise.
                generateKey(strongBox = false)
            }
            val stored = key()
            assertEquals("AES", stored.algorithm)
            assertEquals("AndroidKeyStore", stored.provider.name)

            val plaintext = context.packageName.toByteArray() + "round-trip".toByteArray()
            val encrypt = Cipher.getInstance("AES/GCM/NoPadding")
            encrypt.init(Cipher.ENCRYPT_MODE, stored)
            val sealed = encrypt.doFinal(plaintext)
            val iv = encrypt.iv
            assertEquals("GCM gives a 12-byte IV here, as the envelope format requires", 12, iv.size)
            assertFalse(
                "sealed output is not the plaintext",
                String(sealed).contains(String(plaintext)),
            )

            val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
            decrypt.init(Cipher.DECRYPT_MODE, stored, GCMParameterSpec(GCM_TAG_BITS, iv))
            assertArrayEquals(plaintext, decrypt.doFinal(sealed))
        } finally {
            keystore().deleteEntry(alias)
        }
    }

    @Test
    fun theKeyIsNotExportable() {
        try {
            try {
                generateKey(strongBox = true)
            } catch (strongBoxUnavailable: Exception) {
                generateKey(strongBox = false)
            }
            val stored = key()
            assertEquals(
                "a Keystore key reports its format as null: there are no bytes to walk away with",
                null,
                stored.format,
            )
            assertEquals("and no encoded form exists", null, stored.encoded)
        } finally {
            keystore().deleteEntry(alias)
        }
    }

    @Test
    fun aWrongTagFailsClosed() {
        try {
            try {
                generateKey(strongBox = true)
            } catch (strongBoxUnavailable: Exception) {
                generateKey(strongBox = false)
            }
            val stored = key()
            val encrypt = Cipher.getInstance("AES/GCM/NoPadding")
            encrypt.init(Cipher.ENCRYPT_MODE, stored)
            val sealed = encrypt.doFinal("payload".toByteArray())
            val forgedIv = encrypt.iv.copyOf().also { it[0] = (it[0] + 1).toByte() }
            val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
            decrypt.init(Cipher.DECRYPT_MODE, stored, GCMParameterSpec(GCM_TAG_BITS, forgedIv))
            val failed =
                try {
                    decrypt.doFinal(sealed)
                    false
                } catch (badTag: Exception) {
                    // AEADBadTagException and provider equivalents: the sealed blob did not verify.
                    true
                }
            assertTrue("a tampered blob must not decrypt", failed)
        } finally {
            keystore().deleteEntry(alias)
        }
    }

    private companion object {
        const val KEY_SIZE = 256
        const val GCM_TAG_BITS = 128
    }
}
