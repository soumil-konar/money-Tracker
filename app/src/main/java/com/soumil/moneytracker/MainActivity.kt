package com.soumil.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumil.moneytracker.ui.MainViewModel
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics
import com.soumil.moneytracker.ui.navigation.MoneyTrackerRoot
import com.soumil.moneytracker.ui.theme.MoneyTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val container = (application as MoneyTrackerApp).container
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(container.repository),
            )

            MoneyTrackerTheme {
                CompositionLocalProvider(LocalAppHaptics provides container.hapticManager) {
                    MoneyTrackerRoot(viewModel = mainViewModel)
                }
            }
        }
    }
}
