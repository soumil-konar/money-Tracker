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
    fun `sanitizeAppPassword removes spaces hyphens tabs and converts to lowercase`() {
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("abcd efgh ijkl mnop"))
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("ABCD EFGH IJKL MNOP"))
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword("abcd-efgh-ijkl-mnop"))
        assertEquals("abcdefghijklmnop", emailSyncManager.sanitizeAppPassword(" abcd\tefgh\nijkl\u00a0mnop "))
    }

    @Test
    fun `parseImapError returns actionable guidance for authentication failures`() {
        val authError = emailSyncManager.parseImapError("A01 NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)")
        assertTrue(
            "Should explain Google App Password and IMAP settings",
            authError.contains("Google App Password") && authError.contains("IMAP"),
        )

        val alertError = emailSyncManager.parseImapError("A01 NO [ALERT] Application-specific password required")
        assertTrue(
            "Should guide user to generate App Password in Google Account",
            alertError.contains("App Password"),
        )
    }

    @Test
    fun `testCredentials rejects non-16 character passwords immediately`() = runBlocking {
        val resultShort = emailSyncManager.testCredentials("user@gmail.com", "tooshort")
        assertTrue("Short password must fail immediately", resultShort.isFailure)
        assertTrue(resultShort.exceptionOrNull()?.message?.contains("16 letters") == true)

        val resultBlank = emailSyncManager.testCredentials("", "abcdefghijklmnop")
        assertTrue("Blank email must fail immediately", resultBlank.isFailure)
    }
}
