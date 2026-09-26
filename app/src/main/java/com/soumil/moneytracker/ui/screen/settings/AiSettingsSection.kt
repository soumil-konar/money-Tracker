package com.soumil.moneytracker.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.local.AiEngineMode
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsSection(
    aiApiKey: String,
    isAiEnabled: Boolean,
    selectedModel: String,
    engineMode: AiEngineMode,
    deviceAiStatus: String,
    isPixel9Ready: Boolean,
    aiTestStatus: String?,
    onUpdateApiKey: (String) -> Unit,
    onToggleAiEnabled: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEngineMode: (AiEngineMode) -> Unit,
    onTestAiConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    var keyInput by rememberSaveable(aiApiKey) { mutableStateOf(aiApiKey) }

    SectionCard(
        title = "AI Intelligence & Engine",
        subtitle = "Tensor G4 TPU hardware acceleration & optional Gemini cloud augmentation",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // TPU Status Pill
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
                            fontWeight = FontWeight.Bold,
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

            // Engine Mode Selection
            Text(
                text = "AI Engine Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiEngineMode.values().forEach { mode ->
                    FilterChip(
                        selected = engineMode == mode,
                        onClick = {
                            haptics.selection()
                            onSelectEngineMode(mode)
                        },
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Cloud AI Augmentation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud AI Augmentation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (engineMode == AiEngineMode.ON_DEVICE_ONLY) {
                            "Inactive: On-Device Only mode is selected. No data sent to cloud."
                        } else {
                            "Augments on-device parsing with Gemini Flash Lite for high complexity queries."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isAiEnabled && engineMode != AiEngineMode.ON_DEVICE_ONLY,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onToggleAiEnabled(it)
                    },
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
                        onClick = {
                            haptics.click()
                            onUpdateApiKey(keyInput)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save Key")
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            onTestAiConnection()
                        },
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
                        shape = RoundedCornerShape(14.dp),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiPreferences.AVAILABLE_MODELS.forEach { (modelId, label) ->
                        FilterChip(
                            selected = selectedModel == modelId,
                            onClick = {
                                haptics.selection()
                                onSelectModel(modelId)
                            },
                            label = { Text(label) },
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Why Flash Lite?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
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
