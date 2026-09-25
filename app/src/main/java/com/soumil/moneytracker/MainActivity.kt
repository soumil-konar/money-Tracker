package com.soumil.moneytracker

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumil.moneytracker.ui.MainViewModel
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics
import com.soumil.moneytracker.ui.navigation.MoneyTrackerRoot
import com.soumil.moneytracker.ui.security.AppLockScreen
import com.soumil.moneytracker.ui.security.BiometricAuthHelper
import com.soumil.moneytracker.ui.theme.MoneyTrackerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val container = (application as MoneyTrackerApp).container
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(container.repository),
            )
            val isBiometricEnabled by container.securityPreferences.isBiometricEnabled.collectAsStateWithLifecycle()
            var isAppLocked by remember { mutableStateOf(container.securityPreferences.isBiometricEnabled.value) }

            fun triggerUnlock() {
                BiometricAuthHelper.authenticate(
                    activity = this@MainActivity,
                    onSuccess = {
                        container.hapticManager.success()
                        isAppLocked = false
                    },
                    onFailed = {
                        container.hapticManager.warning()
                    },
                    onError = { _, _ ->
                        container.hapticManager.warning()
                    },
                )
            }

            DisposableEffect(isBiometricEnabled) {
                if (!isBiometricEnabled) {
                    isAppLocked = false
                    return@DisposableEffect onDispose {}
                }

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            if (isBiometricEnabled && isAppLocked) {
                                triggerUnlock()
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            if (isBiometricEnabled) {
                                isAppLocked = true
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
                        MoneyTrackerRoot(viewModel = mainViewModel)
                    }
                }
            }
        }
    }
}
