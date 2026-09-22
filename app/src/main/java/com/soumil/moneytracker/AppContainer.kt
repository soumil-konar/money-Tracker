package com.soumil.moneytracker

import android.content.Context
import com.soumil.moneytracker.ai.FinanceRagEngine
import com.soumil.moneytracker.ai.GeminiApiClient
import com.soumil.moneytracker.ai.OnDeviceAiEngine
import com.soumil.moneytracker.data.db.FinanceDatabase
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.data.local.HapticPreferences
import com.soumil.moneytracker.data.local.SetupPreferences
import com.soumil.moneytracker.data.repo.FinanceRepository
import com.soumil.moneytracker.parser.SmsParser
import com.soumil.moneytracker.ui.haptics.HapticFeedbackManager

class AppContainer(context: Context) {
    private val database = FinanceDatabase.create(context)
    private val parser = SmsParser()
    private val setupPreferences = SetupPreferences(context)
    val aiPreferences = AiPreferences(context)
    val hapticPreferences = HapticPreferences(context)
    val hapticManager = HapticFeedbackManager(context, hapticPreferences)
    val geminiApiClient = GeminiApiClient()
    val onDeviceAiEngine = OnDeviceAiEngine(context)

    val ragEngine = FinanceRagEngine(
        transactionDao = database.transactionDao(),
        embeddingDao = database.transactionEmbeddingDao(),
        geminiApiClient = geminiApiClient,
        onDeviceAiEngine = onDeviceAiEngine,
    )

    val repository = FinanceRepository(
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
        geminiApiClient = geminiApiClient,
        onDeviceAiEngine = onDeviceAiEngine,
    )
}

