package com.moneytracker.app

import android.content.Context
import com.moneytracker.app.ai.AiCoreNanoManager
import com.moneytracker.app.ai.FinanceRagEngine
import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.data.local.CategoryPreferences
import com.moneytracker.app.data.local.EmailPreferences
import com.moneytracker.app.data.local.ExclusionPreferences
import com.moneytracker.app.data.local.HapticPreferences
import com.moneytracker.app.data.local.NotificationPreferences
import com.moneytracker.app.data.local.ReviewPreferences
import com.moneytracker.app.data.local.SecurityPreferences
import com.moneytracker.app.data.local.SetupPreferences
import com.moneytracker.app.data.local.ThemePreferences
import com.moneytracker.app.data.local.UserPreferences
import com.moneytracker.app.data.network.NetworkConnectivityObserver
import com.moneytracker.app.data.repo.FinanceRepository
import com.moneytracker.app.domain.usecase.DetectTransferPairsUseCase
import com.moneytracker.app.domain.usecase.GenerateSpendingInsightsUseCase
import com.moneytracker.app.domain.usecase.IngestTransactionUseCase
import com.moneytracker.app.domain.usecase.ReconcileLedgerUseCase
import com.moneytracker.app.domain.usecase.SyncGmailAlertsUseCase
import com.moneytracker.app.email.EmailSyncManager
import com.moneytracker.app.notification.AppNotificationManager
import com.moneytracker.app.parser.SmsParser
import com.moneytracker.app.ui.haptics.HapticFeedbackManager
import com.moneytracker.app.ui.review.ReviewPromptManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Backward compatibility facade backed by Koin Dependency Injection.
 * All dependencies are resolved on-demand through Koin.
 */
class AppContainer(context: Context) : KoinComponent {
    val database: FinanceDatabase by inject()
    val parser: SmsParser by inject()
    val setupPreferences: SetupPreferences by inject()
    val aiPreferences: AiPreferences by inject()
    val hapticPreferences: HapticPreferences by inject()
    val securityPreferences: SecurityPreferences by inject()
    val emailPreferences: EmailPreferences by inject()
    val emailSyncManager: EmailSyncManager by inject()
    val exclusionPreferences: ExclusionPreferences by inject()
    val notificationPreferences: NotificationPreferences by inject()
    val themePreferences: ThemePreferences by inject()
    val userPreferences: UserPreferences by inject()
    val categoryPreferences: CategoryPreferences by inject()
    val reviewPreferences: ReviewPreferences by inject()
    val appNotificationManager: AppNotificationManager by inject()
    val networkConnectivityObserver: NetworkConnectivityObserver by inject()
    val hapticManager: HapticFeedbackManager by inject()
    val geminiApiClient: GeminiApiClient by inject()
    val aiCoreNanoManager: AiCoreNanoManager by inject()
    val onDeviceAiEngine: OnDeviceAiEngine by inject()
    val reviewPromptManager: ReviewPromptManager by inject()
    val ragEngine: FinanceRagEngine by inject()
    val repository: FinanceRepository by inject()

    // Domain UseCases
    val reconcileLedgerUseCase: ReconcileLedgerUseCase by inject()
    val detectTransferPairsUseCase: DetectTransferPairsUseCase by inject()
    val ingestTransactionUseCase: IngestTransactionUseCase by inject()
    val generateSpendingInsightsUseCase: GenerateSpendingInsightsUseCase by inject()
    val syncGmailAlertsUseCase: SyncGmailAlertsUseCase by inject()
}
