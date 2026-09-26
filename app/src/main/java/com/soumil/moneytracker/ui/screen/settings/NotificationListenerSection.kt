package com.soumil.moneytracker.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.ui.asDateTime
import com.soumil.moneytracker.ui.components.PermissionBanner
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationListenerSection(
    smsPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onImportRecentSms: () -> Unit,
    isNotificationListenerEnabled: Boolean,
    isNotificationPermissionGranted: Boolean,
    isGmailMonitoringEnabled: Boolean,
    isPaymentAppsMonitoringEnabled: Boolean,
    isBankAppsMonitoringEnabled: Boolean,
    notificationLastCapturedTimestamp: Long,
    notificationLastCapturedPackage: String?,
    notificationCapturedCount: Int,
    onOpenNotificationSettings: () -> Unit,
    onToggleNotificationListener: (Boolean) -> Unit,
    onToggleGmailMonitoring: (Boolean) -> Unit,
    onTogglePaymentAppsMonitoring: (Boolean) -> Unit,
    onToggleBankAppsMonitoring: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // SMS Auto-Tracking Section
        SectionCard(
            title = "SMS Auto-Tracking",
            subtitle = "Automated local parsing for bank and UPI transaction SMS alerts",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PermissionBanner(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = onRequestPermissions,
                    onImportRecentSms = onImportRecentSms,
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "100% On-Device Privacy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "SMS messages are analyzed entirely on your device. No financial text or sender data is ever sent to external cloud servers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Notification Listener Section
        SectionCard(
            title = "Real-Time Notification Ingestion",
            subtitle = "Instant alert ingestion from Gmail, GPay, PhonePe, Paytm & bank apps",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Notification Ingestion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Catches push notifications the instant they appear in your status bar. 0% battery drain & no Google passwords required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isNotificationListenerEnabled,
                        onCheckedChange = {
                            haptics.toggle(it)
                            onToggleNotificationListener(it)
                        },
                    )
                }

                if (isNotificationListenerEnabled) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isNotificationPermissionGranted) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = if (isNotificationPermissionGranted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (isNotificationPermissionGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isNotificationPermissionGranted) "Android System Access Active" else "Notification Access Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = if (isNotificationPermissionGranted) {
                                        "Listening for financial push alerts securely on-device with zero network latency."
                                    } else {
                                        "Tap below to grant Money Tracker permission to read notifications in Android Settings."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (!isNotificationPermissionGranted) {
                        Button(
                            onClick = {
                                haptics.click()
                                onOpenNotificationSettings()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Grant System Notification Access")
                        }
                    }

                    Text(
                        text = "Targeted Alert Sources",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = isGmailMonitoringEnabled,
                            onClick = {
                                haptics.tick()
                                onToggleGmailMonitoring(!isGmailMonitoringEnabled)
                            },
                            label = { Text("Gmail Alerts") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.MailOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        FilterChip(
                            selected = isPaymentAppsMonitoringEnabled,
                            onClick = {
                                haptics.tick()
                                onTogglePaymentAppsMonitoring(!isPaymentAppsMonitoringEnabled)
                            },
                            label = { Text("UPI (GPay, PhonePe, Paytm, CRED)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        FilterChip(
                            selected = isBankAppsMonitoringEnabled,
                            onClick = {
                                haptics.tick()
                                onToggleBankAppsMonitoring(!isBankAppsMonitoringEnabled)
                            },
                            label = { Text("Bank Mobile Apps") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    if (notificationCapturedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "$notificationCapturedCount alerts recorded in real-time" +
                                        (notificationLastCapturedPackage?.let { " • Last from ${it.substringAfterLast('.')} at ${notificationLastCapturedTimestamp.asDateTime()}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
