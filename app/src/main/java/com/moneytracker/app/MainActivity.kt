package com.moneytracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneytracker.app.ui.MainViewModel
import com.moneytracker.app.ui.haptics.LocalAppHaptics
import com.moneytracker.app.ui.navigation.MoneyTrackerRoot
import com.moneytracker.app.ui.security.AppLockScreen
import com.moneytracker.app.ui.security.BiometricAuthHelper
import com.moneytracker.app.data.local.BiometricLockTimeout
import com.moneytracker.app.ui.theme.MoneyTrackerTheme
import com.moneytracker.app.widget.BalanceWidgetProvider

class MainActivity : FragmentActivity() {

    private var openAddTransactionOnLaunch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openAddTransactionOnLaunch = intent?.getBooleanExtra(
            BalanceWidgetProvider.EXTRA_OPEN_ADD_TRANSACTION,
            false,
        ) ?: false
        enableEdgeToEdge()

        setContent {
            val container = (application as MoneyTrackerApp).container
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(container.repository),
            )
            val isBiometricEnabled by container.securityPreferences.isBiometricEnabled.collectAsStateWithLifecycle()
            val biometricTimeout by container.securityPreferences.biometricTimeout.collectAsStateWithLifecycle()
            var isAppLocked by rememberSaveable { mutableStateOf(container.securityPreferences.isBiometricEnabled.value) }
            var lastStopTimestamp by rememberSaveable { mutableStateOf(0L) }

            fun triggerUnlock() {
                BiometricAuthHelper.authenticate(
                    activity = this@MainActivity,
                    onSuccess = {
                        container.hapticManager.success()
                        isAppLocked = false
                        lastStopTimestamp = 0L
                    },
                    onFailed = {
                        container.hapticManager.warning()
                    },
                    onError = { _, _ ->
                        container.hapticManager.warning()
                    },
                )
            }

            DisposableEffect(isBiometricEnabled, biometricTimeout) {
                if (!isBiometricEnabled) {
                    isAppLocked = false
                    return@DisposableEffect onDispose {}
                }

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            if (isBiometricEnabled) {
                                if (isAppLocked) {
                                    triggerUnlock()
                                } else if (lastStopTimestamp > 0L) {
                                    val elapsed = System.currentTimeMillis() - lastStopTimestamp
                                    val timeoutMillis = biometricTimeout.seconds * 1000L
                                    if (elapsed >= timeoutMillis) {
                                        isAppLocked = true
                                        triggerUnlock()
                                    }
                                }
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            if (isBiometricEnabled) {
                                lastStopTimestamp = System.currentTimeMillis()
                                if (biometricTimeout == BiometricLockTimeout.IMMEDIATELY) {
                                    isAppLocked = true
                                }
                            }
                        }
                        else -> {}
                    }
                }

                val lifecycle = this@MainActivity.lifecycle
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            val themeMode by container.themePreferences.themeMode.collectAsStateWithLifecycle()
            val themeAccent by container.themePreferences.themeAccent.collectAsStateWithLifecycle()

            MoneyTrackerTheme(
                themeMode = themeMode,
                themeAccent = themeAccent,
            ) {
                CompositionLocalProvider(LocalAppHaptics provides container.hapticManager) {
                    if (isAppLocked) {
                        AppLockScreen(onUnlockClick = { triggerUnlock() })
                    } else {
                        MoneyTrackerRoot(
                            viewModel = mainViewModel,
                            initialOpenAddTransaction = openAddTransactionOnLaunch,
                            onConsumeOpenAddTransaction = { openAddTransactionOnLaunch = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(BalanceWidgetProvider.EXTRA_OPEN_ADD_TRANSACTION, false)) {
            openAddTransactionOnLaunch = true
        }
    }
}
