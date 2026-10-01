package com.moneytracker.app.di

import androidx.work.WorkerParameters
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
import com.moneytracker.app.ui.MainViewModel
import com.moneytracker.app.ui.haptics.HapticFeedbackManager
import com.moneytracker.app.ui.review.ReviewPromptManager
import com.moneytracker.app.widget.BalanceWidgetProvider
import com.moneytracker.app.widget.BudgetWidgetProvider
import com.moneytracker.app.worker.DailyInsightsWorker
import com.moneytracker.app.worker.EmailSyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val databaseModule = module {
    single { FinanceDatabase.create(androidContext()) }
    single { get<FinanceDatabase>().accountDao() }
    single { get<FinanceDatabase>().budgetDao() }
    single { get<FinanceDatabase>().scheduledTransactionDao() }
    single { get<FinanceDatabase>().subscriptionDao() }
    single { get<FinanceDatabase>().transactionDao() }
    single { get<FinanceDatabase>().transactionEmbeddingDao() }
}

val preferencesModule = module {
    single { SetupPreferences(androidContext()) }
    single { AiPreferences(androidContext()) }
    single { HapticPreferences(androidContext()) }
    single { SecurityPreferences(androidContext()) }
    single { EmailPreferences(androidContext()) }
    single { ExclusionPreferences(androidContext()) }
    single { NotificationPreferences(androidContext()) }
    single { ThemePreferences(androidContext()) }
    single { UserPreferences(androidContext()) }
    single { CategoryPreferences(androidContext()) }
    single { ReviewPreferences(androidContext()) }
}

val coreServicesModule = module {
    single { SmsParser() }
    single { EmailSyncManager() } // TLS socket client
    single { GeminiApiClient() }
    single { AiCoreNanoManager(androidContext()) }
    single { OnDeviceAiEngine(androidContext(), get()) }
    single {
        FinanceRagEngine(
            transactionDao = get(),
            embeddingDao = get(),
            geminiApiClient = get(),
            onDeviceAiEngine = get(),
        )
    }
    single { AppNotificationManager(androidContext()) }
    single { NetworkConnectivityObserver(androidContext()) }
    single { HapticFeedbackManager(androidContext(), get()) }
    single { ReviewPromptManager(androidContext(), get(), get()) }
}

val useCaseModule = module {
    single {
        ReconcileLedgerUseCase(
            database = get(),
            transactionDao = get(),
            accountDao = get(),
            onWidgetUpdate = {
                com.moneytracker.app.widget.SafeSpendGlanceWidget.updateAllWidgets(androidContext())
                BalanceWidgetProvider.updateAllWidgets(androidContext())
                BudgetWidgetProvider.updateAllWidgets(androidContext())
            },
        )
    }
    single {
        DetectTransferPairsUseCase(
            transactionDao = get(),
            accountDao = get(),
        )
    }
    single {
        IngestTransactionUseCase(
            transactionDao = get(),
            accountDao = get(),
            scheduledTransactionDao = get(),
            parser = get(),
            exclusionPreferences = get(),
            notificationPreferences = get(),
            aiPreferences = get(),
            onDeviceAiEngine = get(),
            geminiApiClient = get(),
            reconcileLedgerUseCase = get(),
            detectTransferPairsUseCase = get(),
        )
    }
    single {
        GenerateSpendingInsightsUseCase(
            transactionDao = get(),
            budgetDao = get(),
            scheduledTransactionDao = get(),
            aiPreferences = get(),
            onDeviceAiEngine = get(),
            geminiApiClient = get(),
        )
    }
    single {
        SyncGmailAlertsUseCase(
            emailPreferences = get(),
            emailSyncManager = get(),
            ingestTransactionUseCase = get(),
            reconcileLedgerUseCase = get(),
        )
    }
}

val repositoryModule = module {
    single {
        FinanceRepository(
            accountDao = get(),
            budgetDao = get(),
            scheduledTransactionDao = get(),
            subscriptionDao = get(),
            transactionDao = get(),
            embeddingDao = get(),
            ragEngine = get(),
            parser = get(),
            setupPreferences = get(),
            aiPreferences = get(),
            hapticPreferences = get(),
            securityPreferences = get(),
            emailPreferences = get(),
            exclusionPreferences = get(),
            notificationPreferences = get(),
            themePreferences = get(),
            emailSyncManager = get(),
            geminiApiClient = get(),
            onDeviceAiEngine = get(),
            context = androidContext(),
            categoryPreferences = get(),
            userPreferences = get(),
            reconcileLedgerUseCase = get(),
            detectTransferPairsUseCase = get(),
            ingestTransactionUseCase = get(),
            generateSpendingInsightsUseCase = get(),
            syncGmailAlertsUseCase = get(),
        )
    }
}

val viewModelModule = module {
    viewModel {
        MainViewModel(
            repository = get(),
            connectivityObserver = get(),
            ingestTransactionUseCase = get(),
            reconcileLedgerUseCase = get(),
            detectTransferPairsUseCase = get(),
            generateSpendingInsightsUseCase = get(),
            syncGmailAlertsUseCase = get(),
        )
    }
}

val workerModule = module {
    worker { (workerParams: WorkerParameters) ->
        DailyInsightsWorker(
            appContext = androidContext(),
            params = workerParams,
            repository = get(),
            notificationManager = get(),
            generateSpendingInsightsUseCase = get(),
        )
    }
    worker { (workerParams: WorkerParameters) ->
        EmailSyncWorker(
            appContext = androidContext(),
            params = workerParams,
            emailPrefs = get(),
            syncAlertsUseCase = get(),
        )
    }
}

val appModules = listOf(
    databaseModule,
    preferencesModule,
    coreServicesModule,
    useCaseModule,
    repositoryModule,
    viewModelModule,
    workerModule,
)
