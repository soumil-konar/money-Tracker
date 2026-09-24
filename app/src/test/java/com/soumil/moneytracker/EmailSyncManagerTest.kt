package com.soumil.moneytracker

import com.soumil.moneytracker.email.EmailSyncManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSyncManagerTest {

    private val emailSyncManager = EmailSyncManager()

    @Test
    fun `sanitizeEmail trims and appends gmail domain when missing`() {
        assertEquals("user@gmail.com", emailSyncManager.sanitizeEmail("user@gmail.com"))
        assertEquals("user@gmail.com", emailSyncManager.sanitizeEmail("  user@gmail.com  "))
        assertEquals("my.account@gmail.com", emailSyncManager.sanitizeEmail("my.account"))
        assertEquals("", emailSyncManager.sanitizeEmail("   "))
    }

    @Test
    fun `sanitizeAppPassword cleans 16-char grouped app passwords and preserves arbitrary passwords`() {
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("abcd efgh ijkl mnop"))
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("ABCD EFGH IJKL MNOP"))
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("abcd-efgh-ijkl-mnop"))
        // Handles non-breaking spaces and zero-width spaces often copied from Google Account web UI
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("abcd\u00A0efgh\u00A0ijkl\u00A0mnop"))
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("  abcd\u200Befgh\u00A0ijkl-mnop  "))
        // Passwords of any length with numbers and symbols are preserved
        assertEquals("MySecret123!", emailSyncManager.sanitizeAppPassword("  MySecret123!  "))
        assertEquals("Simple8c", emailSyncManager.sanitizeAppPassword("Simple8c"))
        assertEquals("AnyLengthPasswordCanBeUsed#2026", emailSyncManager.sanitizeAppPassword("AnyLengthPasswordCanBeUsed#2026"))
    }

    @Test
    fun `escapeImapString escapes backslashes and double quotes`() {
        assertEquals("normal", emailSyncManager.escapeImapString("normal"))
        assertEquals("pass\\\\word", emailSyncManager.escapeImapString("pass\\word"))
        assertEquals("pass\\\"word", emailSyncManager.escapeImapString("pass\"word"))
    }

    @Test
    fun `parseImapError returns actionable guidance for authentication failures`() {
        val authErrorShort = emailSyncManager.parseImapError("A01 NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)", passwordLength = 8)
        assertTrue(
            "Should explain that 8-char standard password is blocked by Google",
            authErrorShort.contains("8 chars entered") && authErrorShort.contains("myaccount.google.com/apppasswords"),
        )

        val authError16 = emailSyncManager.parseImapError("A01 NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)", passwordLength = 16)
        assertTrue(
            "Should guide user to check App Password at myaccount.google.com/apppasswords",
            authError16.contains("App Password"),
        )

        val alertError = emailSyncManager.parseImapError("A01 NO [ALERT] Application-specific password required")
        assertTrue(
            "Should guide user to generate App Password in Google Account",
            alertError.contains("App Password"),
        )
    }

    @Test
    fun `testCredentials rejects blank inputs and allows passwords of any length`() = runBlocking {
        val resultBlankPassword = emailSyncManager.testCredentials("user@gmail.com", "   ")
        assertTrue("Blank password must fail immediately", resultBlankPassword.isFailure)
        assertTrue(resultBlankPassword.exceptionOrNull()?.message?.contains("Password cannot be blank") == true)

        val resultBlankEmail = emailSyncManager.testCredentials("", "myPassword123")
        assertTrue("Blank email must fail immediately", resultBlankEmail.isFailure)
        assertTrue(resultBlankEmail.exceptionOrNull()?.message?.contains("address cannot be blank") == true)
    }
}
