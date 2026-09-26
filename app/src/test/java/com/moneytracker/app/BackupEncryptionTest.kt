package com.moneytracker.app

import com.moneytracker.app.backup.BackupManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class BackupEncryptionTest {

    @Test
    fun `test encrypt and decrypt round trip returns original plaintext`() {
        val originalText = "{\"app\":\"MoneyTracker\",\"accounts\":[{\"name\":\"HDFC Bank\",\"balance\":54200.50}]}"
        val plaintextBytes = originalText.toByteArray(Charsets.UTF_8)
        val passphrase = "SuperSecretStrongPassword123!"

        val encryptedBytes = BackupManager.encryptData(plaintextBytes, passphrase)

        // Ensure encrypted bytes contain the "MTBK" magic header
        assertEquals('M'.code.toByte(), encryptedBytes[0])
        assertEquals('T'.code.toByte(), encryptedBytes[1])
        assertEquals('B'.code.toByte(), encryptedBytes[2])
        assertEquals('K'.code.toByte(), encryptedBytes[3])
        assertEquals(1.toByte(), encryptedBytes[4]) // format version 1

        val decryptedBytes = BackupManager.decryptData(encryptedBytes, passphrase)
        assertArrayEquals(plaintextBytes, decryptedBytes)
        assertEquals(originalText, String(decryptedBytes, Charsets.UTF_8))
    }

    @Test
    fun `test decrypt with wrong passphrase fails with authentication error`() {
        val plaintext = "Sensitive financial records".toByteArray(Charsets.UTF_8)
        val encrypted = BackupManager.encryptData(plaintext, "CorrectPassword")

        assertThrows(GeneralSecurityException::class.java) {
            BackupManager.decryptData(encrypted, "WrongPassword")
        }
    }

    @Test
    fun `test decrypt with tampered ciphertext fails with authentication error`() {
        val plaintext = "Sensitive financial records".toByteArray(Charsets.UTF_8)
        val encrypted = BackupManager.encryptData(plaintext, "CorrectPassword")

        // Tamper with the last byte (part of ciphertext or authentication tag)
        val tampered = encrypted.clone()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            BackupManager.decryptData(tampered, "CorrectPassword")
        }
    }

    @Test
    fun `test decrypt with invalid magic header throws IllegalArgumentException`() {
        val plaintext = "Test data".toByteArray(Charsets.UTF_8)
        val encrypted = BackupManager.encryptData(plaintext, "Password")

        val invalidMagic = encrypted.clone()
        invalidMagic[0] = 'Z'.code.toByte()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupManager.decryptData(invalidMagic, "Password")
        }
        assertTrue(ex.message!!.contains("header magic mismatch"))
    }

    @Test
    fun `test decrypt with truncated file throws IllegalArgumentException`() {
        val tinyBytes = byteArrayOf(1, 2, 3)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupManager.decryptData(tinyBytes, "Password")
        }
        assertTrue(ex.message!!.contains("size too small"))
    }
}
