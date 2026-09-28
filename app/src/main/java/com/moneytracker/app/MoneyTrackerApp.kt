package com.moneytracker.app

import android.app.Application
import com.moneytracker.app.data.repo.FinanceRepository
import com.moneytracker.app.di.appModules
import com.moneytracker.app.notification.AppNotificationManager
import com.moneytracker.app.worker.InsightsScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MoneyTrackerApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MoneyTrackerApp)
            workManagerFactory()
            modules(appModules)
        }
        val appNotificationManager: AppNotificationManager = get()
        appNotificationManager.createNotificationChannels()
        InsightsScheduler.schedule(this)
        applicationScope.launch {
            val repository: FinanceRepository = get()
            repository.bootstrap()
        }
    }
}
