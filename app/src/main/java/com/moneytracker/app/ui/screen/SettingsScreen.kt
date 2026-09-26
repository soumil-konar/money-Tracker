package com.moneytracker.app.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.local.AiEngineMode
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.data.local.BiometricLockTimeout
import com.moneytracker.app.data.local.HapticIntensity
import com.moneytracker.app.data.local.ThemeAccent
import com.moneytracker.app.data.local.ThemeMode
import com.moneytracker.app.ui.components.AddExclusionKeywordDialog
import com.moneytracker.app.ui.components.MotionReveal
import com.moneytracker.app.ui.haptics.LocalAppHaptics
import com.moneytracker.app.ui.screen.settings.AboutDeviceSection
import com.moneytracker.app.ui.screen.settings.AiSettingsSection
import com.moneytracker.app.ui.screen.settings.BackupAndDataSection
import com.moneytracker.app.ui.screen.settings.EmailSyncSection
import com.moneytracker.app.ui.screen.settings.ExclusionKeywordsSection
import com.moneytracker.app.ui.screen.settings.NotificationListenerSection
import com.moneytracker.app.ui.screen.settings.QuickStatusPill
import com.moneytracker.app.ui.screen.settings.SecuritySettingsSection
import com.moneytracker.app.ui.screen.settings.SettingsCategory
import com.moneytracker.app.ui.screen.settings.ThemeAndHapticsSection

// Export SettingsCategory for backward compatibility with external imports if any
typealias SettingsCategory = com.moneytracker.app.ui.screen.settings.SettingsCategory

@Composable
fun SettingsScreen(
    smsPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onImportRecentSms: () -> Unit,
    aiApiKey: String = "",
    isAiEnabled: Boolean = true,
    selectedModel: String = AiPreferences.DEFAULT_MODEL,
    engineMode: AiEngineMode = AiEngineMode.AUTO_PIXEL_FIRST,
    deviceAiStatus: String = "Google Tensor G4 TPU Ready",
    isPixel9Ready: Boolean = true,
    aiTestStatus: String? = null,
    isHapticEnabled: Boolean = true,
    hapticIntensity: HapticIntensity = HapticIntensity.BALANCED,
    isBiometricEnabled: Boolean = false,
    isBiometricAvailable: Boolean = true,
    biometricTimeout: BiometricLockTimeout = BiometricLockTimeout.IMMEDIATELY,
    onSelectBiometricTimeout: (BiometricLockTimeout) -> Unit = {},
    isNotificationListenerEnabled: Boolean = true,
    isNotificationPermissionGranted: Boolean = false,
    isGmailMonitoringEnabled: Boolean = true,
    isPaymentAppsMonitoringEnabled: Boolean = true,
    isBankAppsMonitoringEnabled: Boolean = true,
    notificationLastCapturedTimestamp: Long = 0L,
    notificationLastCapturedPackage: String? = null,
    notificationCapturedCount: Int = 0,
    onOpenNotificationSettings: () -> Unit = {},
    onToggleNotificationListener: (Boolean) -> Unit = {},
    onToggleGmailMonitoring: (Boolean) -> Unit = {},
    onTogglePaymentAppsMonitoring: (Boolean) -> Unit = {},
    onToggleBankAppsMonitoring: (Boolean) -> Unit = {},
    isExclusionFilterEnabled: Boolean = true,
    excludedKeywords: Set<String> = emptySet(),
    onToggleExclusionFilter: (Boolean) -> Unit = {},
    onAddExclusionKeyword: (String) -> Unit = {},
    onRemoveExclusionKeyword: (String) -> Unit = {},
    onResetExclusionKeywords: () -> Unit = {},
    isEmailSyncEnabled: Boolean = false,
    emailAddress: String = "",
    emailAppPassword: String = "",
    emailLastSyncTimestamp: Long = 0L,
    emailLastSyncStatus: String? = null,
    isEmailSyncing: Boolean = false,
    emailTestStatus: String? = null,
    onTestEmailConnection: (String, String) -> Unit = { _, _ -> },
    onUpdateApiKey: (String) -> Unit = {},
    onToggleAiEnabled: (Boolean) -> Unit = {},
    onSelectModel: (String) -> Unit = {},
    onSelectEngineMode: (AiEngineMode) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onToggleHapticEnabled: (Boolean) -> Unit = {},
    onSelectHapticIntensity: (HapticIntensity) -> Unit = {},
    onToggleBiometricEnabled: (Boolean) -> Unit = {},
    onToggleEmailSync: (Boolean) -> Unit = {},
    onUpdateEmailCredentials: (String, String) -> Unit = { _, _ -> },
    onClearEmailCredentials: () -> Unit = {},
    onSyncRecentEmails: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeAccent: ThemeAccent = ThemeAccent.EXPRESSIVE,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onSelectThemeAccent: (ThemeAccent) -> Unit = {},
    onRequestExportBackup: () -> Unit = {},
    onRequestRestoreBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.ALL) }
    var showAddKeywordDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddKeywordDialog) {
        AddExclusionKeywordDialog(
            onDismiss = { showAddKeywordDialog = false },
            onConfirm = { keyword ->
                onAddExclusionKeyword(keyword)
                showAddKeywordDialog = false
            },
        )
    }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = statusBarInset + 16.dp,
            bottom = navBarInset + 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header & Quick Status Strip
        item {
            MotionReveal(index = 0) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Personalize theming, configure AI engines, sync bank alerts & secure your ledger.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Hero At-A-Glance Status Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickStatusPill(
                            label = if (isBiometricEnabled) "Biometric Lock Active" else "Biometrics Off",
                            icon = Icons.Outlined.Fingerprint,
                            isActive = isBiometricEnabled,
                            onClick = {
                                haptics.click()
                                selectedCategory = SettingsCategory.SECURITY_SYSTEM
                            },
                        )
                        QuickStatusPill(
                            label = if (isNotificationListenerEnabled || isEmailSyncEnabled) "Alert Ingestion Active" else "Sync Inactive",
                            icon = Icons.Outlined.Sync,
                            isActive = isNotificationListenerEnabled || isEmailSyncEnabled,
                            onClick = {
                                haptics.click()
                                selectedCategory = SettingsCategory.DATA_SYNC
                            },
                        )
                        QuickStatusPill(
                            label = if (engineMode == AiEngineMode.ON_DEVICE_ONLY) "Private Offline AI" else "Tensor G4 + Cloud AI",
                            icon = Icons.Outlined.Memory,
                            isActive = true,
                            onClick = {
                                haptics.click()
                                selectedCategory = SettingsCategory.AI_ENGINE
                            },
                        )
                    }

                    // Modern Category Filter Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    haptics.selection()
                                    selectedCategory = category
                                },
                                label = { Text(category.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 1. APPEARANCE & ACCENTS
        // ==========================================
        if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.APPEARANCE) {
            item {
                MotionReveal(index = 1) {
                    ThemeAndHapticsSection(
                        themeMode = themeMode,
                        themeAccent = themeAccent,
                        onSelectThemeMode = onSelectThemeMode,
                        onSelectThemeAccent = onSelectThemeAccent,
                        isHapticEnabled = isHapticEnabled,
                        hapticIntensity = hapticIntensity,
                        onToggleHapticEnabled = onToggleHapticEnabled,
                        onSelectHapticIntensity = onSelectHapticIntensity,
                    )
                }
            }
        }

        // ==========================================
        // 2. AI ENGINE & INTELLIGENCE
        // ==========================================
        if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.AI_ENGINE) {
            item {
                MotionReveal(index = 2) {
                    AiSettingsSection(
                        aiApiKey = aiApiKey,
                        isAiEnabled = isAiEnabled,
                        selectedModel = selectedModel,
                        engineMode = engineMode,
                        deviceAiStatus = deviceAiStatus,
                        isPixel9Ready = isPixel9Ready,
                        aiTestStatus = aiTestStatus,
                        onUpdateApiKey = onUpdateApiKey,
                        onToggleAiEnabled = onToggleAiEnabled,
                        onSelectModel = onSelectModel,
                        onSelectEngineMode = onSelectEngineMode,
                        onTestAiConnection = onTestAiConnection,
                    )
                }
            }
        }

        // ==========================================
        // 3. DATA INGESTION & SYNC
        // ==========================================
        if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.DATA_SYNC) {
            item {
                MotionReveal(index = 3) {
                    NotificationListenerSection(
                        smsPermissionGranted = smsPermissionGranted,
                        onRequestPermissions = onRequestPermissions,
                        onImportRecentSms = onImportRecentSms,
                        isNotificationListenerEnabled = isNotificationListenerEnabled,
                        isNotificationPermissionGranted = isNotificationPermissionGranted,
                        isGmailMonitoringEnabled = isGmailMonitoringEnabled,
                        isPaymentAppsMonitoringEnabled = isPaymentAppsMonitoringEnabled,
                        isBankAppsMonitoringEnabled = isBankAppsMonitoringEnabled,
                        notificationLastCapturedTimestamp = notificationLastCapturedTimestamp,
                        notificationLastCapturedPackage = notificationLastCapturedPackage,
                        notificationCapturedCount = notificationCapturedCount,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onToggleNotificationListener = onToggleNotificationListener,
                        onToggleGmailMonitoring = onToggleGmailMonitoring,
                        onTogglePaymentAppsMonitoring = onTogglePaymentAppsMonitoring,
                        onToggleBankAppsMonitoring = onToggleBankAppsMonitoring,
                    )
                }
            }

            item {
                MotionReveal(index = 4) {
                    EmailSyncSection(
                        isEmailSyncEnabled = isEmailSyncEnabled,
                        emailAddress = emailAddress,
                        emailAppPassword = emailAppPassword,
                        emailLastSyncTimestamp = emailLastSyncTimestamp,
                        emailLastSyncStatus = emailLastSyncStatus,
                        isEmailSyncing = isEmailSyncing,
                        emailTestStatus = emailTestStatus,
                        onToggleEmailSync = onToggleEmailSync,
                        onUpdateEmailCredentials = onUpdateEmailCredentials,
                        onTestEmailConnection = onTestEmailConnection,
                        onClearEmailCredentials = onClearEmailCredentials,
                        onSyncRecentEmails = onSyncRecentEmails,
                    )
                }
            }

            item {
                MotionReveal(index = 5) {
                    ExclusionKeywordsSection(
                        isExclusionFilterEnabled = isExclusionFilterEnabled,
                        excludedKeywords = excludedKeywords,
                        onToggleExclusionFilter = onToggleExclusionFilter,
                        onRemoveExclusionKeyword = onRemoveExclusionKeyword,
                        onResetExclusionKeywords = onResetExclusionKeywords,
                        onOpenAddKeywordDialog = { showAddKeywordDialog = true },
                    )
                }
            }
        }

        // ==========================================
        // 4. SECURITY & SYSTEM
        // ==========================================
        if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.SECURITY_SYSTEM) {
            item {
                MotionReveal(index = 6) {
                    BackupAndDataSection(
                        onRequestExportBackup = onRequestExportBackup,
                        onRequestRestoreBackup = onRequestRestoreBackup,
                    )
                }
            }

            item {
                MotionReveal(index = 7) {
                    SecuritySettingsSection(
                        isBiometricEnabled = isBiometricEnabled,
                        isBiometricAvailable = isBiometricAvailable,
                        biometricTimeout = biometricTimeout,
                        onToggleBiometricEnabled = onToggleBiometricEnabled,
                        onSelectBiometricTimeout = onSelectBiometricTimeout,
                    )
                }
            }

            item {
                MotionReveal(index = 8) {
                    AboutDeviceSection()
                }
            }
        }
    }
}
