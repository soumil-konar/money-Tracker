package com.moneytracker.app

import android.app.Application
import com.moneytracker.app.worker.InsightsScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneyTrackerApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        InsightsScheduler.schedule(this)
        applicationScope.launch {
            container.repository.bootstrap()
        }
    }
}

