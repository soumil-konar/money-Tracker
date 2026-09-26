package com.soumil.moneytracker.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides AES-256-GCM hardware-backed encrypted storage for sensitive credentials
 * (such as API keys and email app passwords) with graceful fallback and automatic migration
 * from legacy plaintext preferences.
 */
class SecurePreferencesHelper(
    private val context: Context,
    private val prefsName: String,
) {
    private val rawPrefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "money_tracker_master_key"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val ENCRYPTED_PREFIX = "enc:v1:"
    }

    private val isKeyStoreAvailable: Boolean by lazy {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            true
        }.getOrDefault(false)
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        if (!isKeyStoreAvailable) return null
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        }.getOrNull()
    }

    fun encryptString(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val secretKey = getOrCreateSecretKey()
        if (secretKey == null) {
            // In environments where AndroidKeyStore is unavailable (e.g. JVM unit tests), store directly
            return plaintext
        }

        return runCatching {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val byteBuffer = ByteBuffer.allocate(iv.size + ciphertext.size)
            byteBuffer.put(iv)
            byteBuffer.put(ciphertext)

            ENCRYPTED_PREFIX + Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        }.getOrDefault(plaintext)
    }

    fun decryptString(storedValue: String): String {
        if (storedValue.isBlank()) return ""
        if (!storedValue.startsWith(ENCRYPTED_PREFIX)) {
            // Plaintext value (e.g. legacy stored or test environment)
            return storedValue
        }

        val secretKey = getOrCreateSecretKey() ?: return storedValue.removePrefix(ENCRYPTED_PREFIX)
        val payload = storedValue.removePrefix(ENCRYPTED_PREFIX)

        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            if (bytes.size < IV_SIZE_BYTES + 16) return storedValue

            val byteBuffer = ByteBuffer.wrap(bytes)
            val iv = ByteArray(IV_SIZE_BYTES)
            byteBuffer.get(iv)
            val ciphertext = ByteArray(byteBuffer.remaining())
            byteBuffer.get(ciphertext)

            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun getSecureString(key: String, defaultValue: String = ""): String {
        val rawValue = rawPrefs.getString(key, null) ?: return defaultValue
        val decrypted = decryptString(rawValue)

        // Automatic migration: if stored in plaintext, re-encrypt it
        if (rawValue.isNotBlank() && !rawValue.startsWith(ENCRYPTED_PREFIX) && isKeyStoreAvailable) {
            putSecureString(key, decrypted)
        }

        return decrypted.ifBlank { defaultValue }
    }

    fun putSecureString(key: String, value: String) {
        val encrypted = encryptString(value)
        rawPrefs.edit().putString(key, encrypted).apply()
    }

    fun remove(key: String) {
        rawPrefs.edit().remove(key).apply()
    }
}
