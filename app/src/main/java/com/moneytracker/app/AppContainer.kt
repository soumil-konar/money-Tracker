package com.moneytracker.app

import android.content.Context
import com.moneytracker.app.ai.FinanceRagEngine
import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.data.local.HapticPreferences
import com.moneytracker.app.data.local.SecurityPreferences
import com.moneytracker.app.data.local.SetupPreferences
import com.moneytracker.app.data.repo.FinanceRepository
import com.moneytracker.app.parser.SmsParser
import com.moneytracker.app.ui.haptics.HapticFeedbackManager

class AppContainer(context: Context) {
    private val database by lazy { FinanceDatabase.create(context) }
    private val parser by lazy { SmsParser() }
    private val setupPreferences by lazy { SetupPreferences(context) }
    val aiPreferences by lazy { AiPreferences(context) }
    val hapticPreferences by lazy { HapticPreferences(context) }
    val securityPreferences by lazy { SecurityPreferences(context) }
    val emailPreferences by lazy { com.moneytracker.app.data.local.EmailPreferences(context) }
    val emailSyncManager by lazy { com.moneytracker.app.email.EmailSyncManager() }
    val exclusionPreferences by lazy { com.moneytracker.app.data.local.ExclusionPreferences(context) }
    val notificationPreferences by lazy { com.moneytracker.app.data.local.NotificationPreferences(context) }
    val themePreferences by lazy { com.moneytracker.app.data.local.ThemePreferences(context) }
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
            exclusionPreferences = exclusionPreferences,
            notificationPreferences = notificationPreferences,
            themePreferences = themePreferences,
            emailSyncManager = emailSyncManager,
            geminiApiClient = geminiApiClient,
            onDeviceAiEngine = onDeviceAiEngine,
            context = context.applicationContext,
        )
    }
}

