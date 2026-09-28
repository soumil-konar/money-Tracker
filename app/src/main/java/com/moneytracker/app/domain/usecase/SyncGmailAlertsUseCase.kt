package com.moneytracker.app.domain.usecase

import com.moneytracker.app.data.local.EmailPreferences
import com.moneytracker.app.email.EmailSyncManager

/**
 * Manages direct TLS IMAP socket execution, app password validation,
 * and batch transaction harvesting.
 */
class SyncGmailAlertsUseCase(
    private val emailPreferences: EmailPreferences,
    private val emailSyncManager: EmailSyncManager,
    private val ingestTransactionUseCase: IngestTransactionUseCase,
    private val reconcileLedgerUseCase: ReconcileLedgerUseCase,
) {

    suspend fun syncRecentEmails(maxMessages: Int = 30): Result<Int> {
        val email = emailPreferences.emailAddress.value
        val password = emailPreferences.appPassword.value
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalStateException("Gmail address or App Password is not configured."))
        }

        val result = emailSyncManager.fetchRecentBankAlerts(email, password, maxMessages)
        return result.fold(
            onSuccess = { messages ->
                var importedCount = 0
                val sortedMessages = messages.sortedBy { it.timestampMillis }
                sortedMessages.forEach { msg ->
                    val outcome = ingestTransactionUseCase.ingestMessage(
                        sender = msg.sender,
                        body = msg.body,
                        receivedAtMillis = msg.timestampMillis,
                    )
                    if (outcome == SmsIngestionOutcome.IMPORTED || outcome == SmsIngestionOutcome.REVIEW) {
                        importedCount++
                    }
                }
                ingestTransactionUseCase.deduplicateTransactions()
                reconcileLedgerUseCase.reconcileAccountsAndBalances()
                emailPreferences.updateSyncResult(
                    timestampMillis = System.currentTimeMillis(),
                    status = "Synced ${messages.size} alerts ($importedCount new)",
                )
                Result.success(importedCount)
            },
            onFailure = { error ->
                emailPreferences.updateSyncResult(
                    timestampMillis = System.currentTimeMillis(),
                    status = "Sync error: ${error.message ?: "Authentication failed"}",
                )
                Result.failure(error)
            },
        )
    }

    suspend fun testEmailCredentials(email: String, appPassword: String): Result<Boolean> {
        return emailSyncManager.testCredentials(email, appPassword)
    }
}
