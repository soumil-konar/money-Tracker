package com.soumil.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumil.moneytracker.ui.MainViewModel
import com.soumil.moneytracker.ui.navigation.MoneyTrackerRoot
import com.soumil.moneytracker.ui.theme.MoneyTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val repository = (application as MoneyTrackerApp).container.repository
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(repository),
            )

            MoneyTrackerTheme {
                MoneyTrackerRoot(viewModel = mainViewModel)
            }
        }
    }
}
