package com.soumil.moneytracker

import android.content.Context
import com.soumil.moneytracker.ai.FinanceRagEngine
import com.soumil.moneytracker.ai.GeminiApiClient
import com.soumil.moneytracker.ai.OnDeviceAiEngine
import com.soumil.moneytracker.data.db.FinanceDatabase
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.data.local.HapticPreferences
import com.soumil.moneytracker.data.local.SecurityPreferences
import com.soumil.moneytracker.data.local.SetupPreferences
import com.soumil.moneytracker.data.repo.FinanceRepository
import com.soumil.moneytracker.parser.SmsParser
import com.soumil.moneytracker.ui.haptics.HapticFeedbackManager

class AppContainer(context: Context) {
    private val database by lazy { FinanceDatabase.create(context) }
    private val parser by lazy { SmsParser() }
    private val setupPreferences by lazy { SetupPreferences(context) }
    val aiPreferences by lazy { AiPreferences(context) }
    val hapticPreferences by lazy { HapticPreferences(context) }
    val securityPreferences by lazy { SecurityPreferences(context) }
    val emailPreferences by lazy { com.soumil.moneytracker.data.local.EmailPreferences(context) }
    val emailSyncManager by lazy { com.soumil.moneytracker.email.EmailSyncManager() }
    val hapticManager by lazy { HapticFeedbackManager(context, hapticPreferences) }
    val geminiApiClient by lazy { GeminiApiClient() }
    val onDeviceAiEngine by lazy { OnDeviceAiEngine(context) }

    val ragEngine by lazy {
        FinanceRagEngine(
            transactionDao = database.transactionDao(),
            embeddingDao = database.transactionEmbeddingDao(),
            geminiApiClient = geminiApiClient,
            onDeviceAiEngine = onDeviceAiEngine,
        )
    }

    val repository by lazy {
        FinanceRepository(
            accountDao = database.accountDao(),
            budgetDao = database.budgetDao(),
            scheduledTransactionDao = database.scheduledTransactionDao(),
            subscriptionDao = database.subscriptionDao(),
            transactionDao = database.transactionDao(),
            embeddingDao = database.transactionEmbeddingDao(),
            ragEngine = ragEngine,
            parser = parser,
            setupPreferences = setupPreferences,
            aiPreferences = aiPreferences,
            hapticPreferences = hapticPreferences,
            securityPreferences = securityPreferences,
            emailPreferences = emailPreferences,
            emailSyncManager = emailSyncManager,
            geminiApiClient = geminiApiClient,
            onDeviceAiEngine = onDeviceAiEngine,
        )
    }
}

