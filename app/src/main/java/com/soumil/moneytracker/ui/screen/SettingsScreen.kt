package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.soumil.moneytracker.data.local.ThemeAccent
import com.soumil.moneytracker.data.local.ThemeMode
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.local.AiEngineMode
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.data.local.HapticIntensity
import com.soumil.moneytracker.ui.asDateTime
import com.soumil.moneytracker.ui.components.AddExclusionKeywordDialog
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.PermissionBanner
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    var keyInput by rememberSaveable(aiApiKey) { mutableStateOf(aiApiKey) }
    var emailInput by rememberSaveable(emailAddress) { mutableStateOf(emailAddress) }
    var passwordInput by rememberSaveable(emailAppPassword) { mutableStateOf(emailAppPassword) }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
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
        item {
            MotionReveal(index = 0) {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI intelligence, theme styling, permissions, and security",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 1) {
                SectionCard(
                    title = "App Appearance & Accents",
                    subtitle = "Material 3 Expressive theming & prebuilt curated color accents",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 1. Theme Mode (System, Dark, Light)
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val isSelected = themeMode == mode
                                val icon = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            haptics.click()
                                            onSelectThemeMode(mode)
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = mode.label,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = when (mode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.DARK -> "Dark"
                                                ThemeMode.LIGHT -> "Light"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Material 3 Expressive Status Banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (themeAccent.isExpressive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (themeAccent.isExpressive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (themeAccent.isExpressive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (themeAccent.isExpressive) Icons.Outlined.AutoAwesome else Icons.Outlined.Palette,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (themeAccent.isExpressive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = if (themeAccent.isExpressive) "Material 3 Expressive Active" else "Curated Accent Active",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (themeAccent.isExpressive) Color(0xFF10B981).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Text(
                                                text = if (themeAccent.isExpressive) "M3 Expressive" else "M3 Expressive Disabled",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (themeAccent.isExpressive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (themeAccent.isExpressive) {
                                            "Expressive springy shape scales, high-chroma tonal dynamics, and vibrant container surfaces enabled."
                                        } else {
                                            "Selecting prebuilt accents (${themeAccent.label}) disables Material 3 Expressive and activates tailored color tokens."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // 3. Accent Palette Selector Grid
                        Text(
                            text = "Color Accents & Theming",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeAccent.entries.chunked(2).forEach { rowAccents ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowAccents.forEach { accent ->
                                        val isSelected = themeAccent == accent
                                        val previewColors = getAccentPreviewColors(accent)

                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    haptics.click()
                                                    onSelectThemeAccent(accent)
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) previewColors.first() else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            ),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                                        previewColors.forEach { color ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .clip(CircleShape)
                                                                    .background(color)
                                                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                                            )
                                                        }
                                                    }

                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.CheckCircle,
                                                            contentDescription = "Selected",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = previewColors.first(),
                                                        )
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = accent.label,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    Text(
                                                        text = if (accent.isExpressive) "Expressive" else "Disables M3",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (accent.isExpressive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (rowAccents.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = 1) {
                SectionCard(
                    title = "Pixel 9 On-Device AI",
                    subtitle = "Tensor G4 TPU hardware acceleration & offline intelligence",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deviceAiStatus,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = if (isPixel9Ready) {
                                            "Tensor G4 TPU active: Sub-millisecond parsing & 100% offline private assistant."
                                        } else {
                                            "On-device fallback active: zero network latency & local vector embeddings."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Text(
                            text = "AI Engine Mode",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AiEngineMode.values().forEach { mode ->
                                FilterChip(
                                    selected = engineMode == mode,
                                    onClick = { onSelectEngineMode(mode) },
                                    label = { Text(mode.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                AiEngineMode.AUTO_PIXEL_FIRST -> Icons.Outlined.Bolt
                                                AiEngineMode.ON_DEVICE_ONLY -> Icons.Outlined.Security
                                                AiEngineMode.CLOUD_ONLY -> Icons.Outlined.AutoAwesome
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = engineMode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = 2) {
                SectionCard(
                    title = "Cloud AI (Google AI Studio)",
                    subtitle = "Gemini Flash Lite & cloud enhancement",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud AI Augmentation",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = if (engineMode == AiEngineMode.ON_DEVICE_ONLY) {
                                        "Inactive: On-Device Only mode is selected. No data is sent to cloud."
                                    } else {
                                        "Augments on-device parsing with Gemini Flash Lite for high complexity queries."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isAiEnabled && engineMode != AiEngineMode.ON_DEVICE_ONLY,
                                onCheckedChange = onToggleAiEnabled,
                                enabled = engineMode != AiEngineMode.ON_DEVICE_ONLY,
                            )
                        }

                        if (engineMode != AiEngineMode.ON_DEVICE_ONLY) {
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                label = { Text("Google AI Studio API Key") },
                                placeholder = { Text("AQ.Ab8...") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Key, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { onUpdateApiKey(keyInput) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Save Key")
                                }
                                OutlinedButton(
                                    onClick = onTestAiConnection,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Test Connection")
                                }
                            }

                            if (aiTestStatus != null) {
                                val isSuccess = aiTestStatus.startsWith("Connected", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSuccess) {
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = aiTestStatus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Model Selection",
                                style = MaterialTheme.typography.titleMedium,
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AiPreferences.AVAILABLE_MODELS.forEach { (modelId, label) ->
                                    FilterChip(
                                        selected = selectedModel == modelId,
                                        onClick = { onSelectModel(modelId) },
                                        label = { Text(label) },
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Why Flash Lite?",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Gemini 3.5 Flash Lite gives you 500 Requests Per Day (vs only 20 RPD on standard Flash) and ultra-low latency, making it ideal for daily SMS receipt tracking.",
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

        item {
            MotionReveal(index = 2) {
                SectionCard(
                    title = "Real-Time Notification Listener",
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

        item {
            MotionReveal(index = 2) {
                SectionCard(
                    title = "Transaction Exclusion Filters",
                    subtitle = "Custom keywords & merchants to automatically skip (No hardcoding)",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Keyword Filtering",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Skip recording alerts matching keywords below (e.g., Steam purchases, Epic Games, refunds, OTPs).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isExclusionFilterEnabled,
                                onCheckedChange = {
                                    haptics.toggle(it)
                                    onToggleExclusionFilter(it)
                                },
                            )
                        }

                        if (isExclusionFilterEnabled) {
                            Text(
                                text = "Active Exclusion Rules (${excludedKeywords.size})",
                                style = MaterialTheme.typography.titleSmall,
                            )

                            if (excludedKeywords.isEmpty()) {
                                Text(
                                    text = "No exclusion keywords configured. All incoming alerts will be processed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    excludedKeywords.sorted().forEach { keyword ->
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                haptics.tick()
                                                onRemoveExclusionKeyword(keyword)
                                            },
                                            label = { Text(keyword) },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = "Remove $keyword",
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.click()
                                        showAddKeywordDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Add Keyword")
                                }

                                TextButton(
                                    onClick = {
                                        haptics.click()
                                        onResetExclusionKeywords()
                                    },
                                ) {
                                    Text("Reset Defaults")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = 3) {
                SectionCard(
                    title = "Google Email Alerts (100% On-Device)",
                    subtitle = "Direct TLS IMAP fetch for bank transaction emails & balance alerts",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Email Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Direct TLS fetch from imap.gmail.com:993 for bank transaction alerts (HDFC, ICICI, SBI, Axis, Kotak, CRED).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isEmailSyncEnabled,
                                onCheckedChange = onToggleEmailSync,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "100% On-Device & Private",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = "Your credentials and emails are processed strictly inside this app. No data is sent to any intermediary server. Generate an App Password in your Google Account security settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (isEmailSyncEnabled) {
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Gmail Address") },
                                placeholder = { Text("your.name@gmail.com") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.MailOutline, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Gmail Password / App Password") },
                                placeholder = { Text("Enter your password") },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            haptics.tick()
                                            isPasswordVisible = !isPasswordVisible
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        haptics.click()
                                        onUpdateEmailCredentials(emailInput.trim(), passwordInput.trim())
                                    },
                                    enabled = emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Save Credentials")
                                }

                                OutlinedButton(
                                    onClick = {
                                        haptics.click()
                                        onTestEmailConnection(emailInput.trim(), passwordInput.trim())
                                    },
                                    enabled = emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Test Connection")
                                }

                                if (emailAddress.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            haptics.warning()
                                            emailInput = ""
                                            passwordInput = ""
                                            onClearEmailCredentials()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Clear credentials",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }

                            if (!emailTestStatus.isNullOrBlank()) {
                                val isSuccess = emailTestStatus.startsWith("Success", ignoreCase = true) ||
                                    emailTestStatus.startsWith("Connected", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSuccess) {
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = emailTestStatus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onSyncRecentEmails,
                                enabled = emailAddress.isNotBlank() && emailAppPassword.isNotBlank() && !isEmailSyncing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isEmailSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Connecting & Syncing...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Sync Recent Bank Emails")
                                }
                            }

                            if (emailLastSyncTimestamp > 0L) {
                                val isSuccess = emailLastSyncStatus?.let {
                                    !it.startsWith("Sync error", ignoreCase = true) &&
                                    !it.startsWith("Failed", ignoreCase = true) &&
                                    !it.startsWith("Error", ignoreCase = true)
                                } ?: false
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSuccess) {
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Column {
                                            Text(
                                                text = "Last synced: ${emailLastSyncTimestamp.asDateTime()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            if (!emailLastSyncStatus.isNullOrBlank()) {
                                                Text(
                                                    text = emailLastSyncStatus,
                                                    style = MaterialTheme.typography.labelSmall,
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
            }
        }

        item {
            MotionReveal(index = 4) {
                SectionCard(
                    title = "Tactile & Haptic Feedback",
                    subtitle = "Dynamic vibrations scaled for your device's linear actuator",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Haptics",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Tactile physical feedback on button clicks, tabs, and ledger actions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isHapticEnabled,
                                onCheckedChange = {
                                    haptics.toggle(it)
                                    onToggleHapticEnabled(it)
                                },
                            )
                        }

                        if (isHapticEnabled) {
                            Text(
                                text = "Vibration Intensity",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HapticIntensity.values().forEach { intensity ->
                                    FilterChip(
                                        selected = hapticIntensity == intensity,
                                        onClick = {
                                            onSelectHapticIntensity(intensity)
                                            haptics.selection()
                                        },
                                        label = { Text(intensity.label) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Vibration,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Interactive Preview",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = { haptics.tick() }) {
                                    Text("Tick")
                                }
                                OutlinedButton(onClick = { haptics.click() }) {
                                    Text("Click")
                                }
                                OutlinedButton(onClick = { haptics.success() }) {
                                    Text("Success")
                                }
                                OutlinedButton(onClick = { haptics.warning() }) {
                                    Text("Warning")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = 4) {
                SectionCard(
                    title = "App Security",
                    subtitle = "Biometric lock & credential protection",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric App Lock",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBiometricAvailable) {
                                    "Require fingerprint, face, or device PIN to open Money Tracker."
                                } else {
                                    "Biometric hardware not available or not configured on this device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            enabled = isBiometricAvailable,
                            onCheckedChange = { enabled ->
                                onToggleBiometricEnabled(enabled)
                                haptics.toggle(enabled)
                            },
                        )
                    }
                }
            }
        }

        item {
            MotionReveal(index = 5) {
                PermissionBanner(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = onRequestPermissions,
                    onImportRecentSms = onImportRecentSms,
                )
            }
        }

        item {
            MotionReveal(index = 5) {
                SectionCard(
                    title = "Privacy & Local-First",
                    subtitle = "Your financial data stays on your device",
                ) {
                    Text(
                        text = "All accounts, budgets, and transactions are stored locally in Room. When AI parsing is enabled, individual transaction SMS snippets are analyzed via Google AI Studio API over encrypted HTTPS to extract structured data.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 4) {
                SectionCard(
                    title = "Battery & Network",
                    subtitle = "Zero continuous background battery drain",
                ) {
                    Text(
                        text = "The app has no background polling services. SMS messages are processed only when Android's broadcast receiver wakes the app. A 24-hour WorkManager job checks for recurring subscriptions.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 5) {
                SectionCard(
                    title = "Play Store Compliance",
                    subtitle = "Sensitive SMS permissions",
                ) {
                    Text(
                        text = "Apps requesting READ_SMS and RECEIVE_SMS must provide user consent, permission justification, and comply with Play Store sensitive permission guidelines.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun getAccentPreviewColors(accent: ThemeAccent): List<Color> {
    return when (accent) {
        ThemeAccent.EXPRESSIVE -> listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8), Color(0xFF34D399))
        ThemeAccent.MONOCHROME -> listOf(Color(0xFF18181B), Color(0xFF71717A), Color(0xFFFAFAFA))
        ThemeAccent.CRIMSON -> listOf(Color(0xFFBE123C), Color(0xFFFB7185), Color(0xFFFFE4E6))
        ThemeAccent.OCEAN -> listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFFE0F2FE))
        ThemeAccent.SAGE -> listOf(Color(0xFF15803D), Color(0xFF86EFAC), Color(0xFFDCFCE7))
        ThemeAccent.AMBER -> listOf(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFFEF3C7))
    }
}

