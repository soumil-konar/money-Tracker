package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.PermissionBanner
import com.soumil.moneytracker.ui.components.SectionCard

@Composable
fun SettingsScreen(
    smsPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onImportRecentSms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MotionReveal(index = 0) {
                Column {
                    Text(text = "Settings", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "Permissions, privacy, and background behavior",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 1) {
                PermissionBanner(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = onRequestPermissions,
                    onImportRecentSms = onImportRecentSms,
                )
            }
        }

        item {
            MotionReveal(index = 2) {
                SectionCard(
                    title = "Privacy",
                    subtitle = "Local-only by default",
                ) {
                    Text(
                        text = "Transactions, subscription suggestions, and budgets stay on-device in Room. There is no sign-in or cloud sync in this version.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 3) {
                SectionCard(
                    title = "Battery behavior",
                    subtitle = "No persistent background service",
                ) {
                    Text(
                        text = "New SMS messages are parsed only when Android delivers them. A once-daily worker checks for recurring-payment suggestions and then exits.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 4) {
                SectionCard(
                    title = "Play Store note",
                    subtitle = "Sensitive SMS permissions",
                ) {
                    Text(
                        text = "Apps using READ_SMS and RECEIVE_SMS face Play policy review. For production distribution, permission justification and policy compliance need extra work.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

