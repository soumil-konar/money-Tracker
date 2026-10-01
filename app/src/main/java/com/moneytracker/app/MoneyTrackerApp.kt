package com.moneytracker.app

import android.app.Application
import android.util.Log
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        appScope.launch(Dispatchers.IO) {
            try {
                val appNotificationManager: AppNotificationManager = get()
                appNotificationManager.createNotificationChannels()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize notification channels", e)
            }

            try {
                InsightsScheduler.schedule(this@MoneyTrackerApp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule background insights", e)
            }

            try {
                val repository: FinanceRepository = get()
                repository.bootstrap()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bootstrap finance repository", e)
            }
        }
    }

    companion object {
        private const val TAG = "MoneyTrackerApp"
    }
}
