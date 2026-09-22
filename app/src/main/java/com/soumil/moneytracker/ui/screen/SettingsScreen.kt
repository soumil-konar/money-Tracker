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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.local.AiEngineMode
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.PermissionBanner
import com.soumil.moneytracker.ui.components.SectionCard

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
    onUpdateApiKey: (String) -> Unit = {},
    onToggleAiEnabled: (Boolean) -> Unit = {},
    onSelectModel: (String) -> Unit = {},
    onSelectEngineMode: (AiEngineMode) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var keyInput by rememberSaveable(aiApiKey) { mutableStateOf(aiApiKey) }

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
                        text = "AI intelligence, permissions, privacy, and models",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                PermissionBanner(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = onRequestPermissions,
                    onImportRecentSms = onImportRecentSms,
                )
            }
        }

        item {
            MotionReveal(index = 3) {
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
