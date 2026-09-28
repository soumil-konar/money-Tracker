package com.moneytracker.app.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.local.AiEngineMode
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.ui.components.SectionCard
import com.moneytracker.app.ui.haptics.LocalAppHaptics

import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.moneytracker.app.ai.OnDeviceAiEngine
import java.util.Locale

data class BenchmarkUiResult(
    val merchant: String,
    val amount: String,
    val category: String,
    val latencyMs: Double,
    val engine: String,
    val sampleText: String,
)

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlightColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = highlightColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

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
    deviceModel: String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Device",
    hardwareAccelerator: String = "Neural Engine",
    detectedSoc: String = "Qualcomm Snapdragon",
    aiCoreStatus: String = "Not Supported on this Device",
    activeParser: String = "Local Regex Engine (CPU)",
    benchmarkResult: BenchmarkUiResult? = null,
    onRunBenchmark: (() -> Unit)? = null,
) {
    val haptics = LocalAppHaptics.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var keyInput by rememberSaveable(aiApiKey) { mutableStateOf(aiApiKey) }
    var isGuideExpanded by rememberSaveable { mutableStateOf(false) }
    var localBenchmarkResult by remember { mutableStateOf<BenchmarkUiResult?>(null) }
    var isBenchmarking by remember { mutableStateOf(false) }

    val activeBenchmark = benchmarkResult ?: localBenchmarkResult

    val resolvedDeviceModel = deviceModel.takeIf { it.isNotBlank() && it != "Device" }
        ?: deviceAiStatus.substringBefore(" • ").trim().takeIf { it.isNotBlank() }
        ?: Build.MODEL?.takeIf { it.isNotBlank() }
        ?: "Device"
    val resolvedAccelerator = hardwareAccelerator.takeIf { it.isNotBlank() && it != "Neural Engine" }
        ?: deviceAiStatus.substringAfter(" • ").removeSuffix(" Active").trim().takeIf { it.isNotBlank() }
        ?: "Neural Engine"

    SectionCard(
        title = "AI Intelligence & Engine",
        subtitle = "$resolvedAccelerator hardware acceleration & optional Gemini cloud augmentation",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Hardware Diagnostics Card (Task 4)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Silicon & AICore Diagnostics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiagnosticRow(
                            label = "Hardware SoC",
                            value = detectedSoc,
                            icon = Icons.Outlined.Memory,
                        )
                        val isReady = aiCoreStatus.equals("Ready", ignoreCase = true)
                        val isDownloading = aiCoreStatus.contains("Downloading", ignoreCase = true)
                        DiagnosticRow(
                            label = "AICore Status",
                            value = aiCoreStatus,
                            icon = if (isReady) Icons.Outlined.CheckCircle else if (isDownloading) Icons.Outlined.Sync else Icons.Outlined.Info,
                            highlightColor = if (isReady) MaterialTheme.colorScheme.tertiary else if (isDownloading) MaterialTheme.colorScheme.secondary else null,
                        )
                        DiagnosticRow(
                            label = "Active Parser",
                            value = activeParser,
                            icon = Icons.Outlined.Bolt,
                            highlightColor = MaterialTheme.colorScheme.primary,
                        )
                        val isCloudActive = aiApiKey.isNotBlank() && isAiEnabled && engineMode != AiEngineMode.ON_DEVICE_ONLY
                        DiagnosticRow(
                            label = "Cloud Backup",
                            value = if (isCloudActive) "Active (Gemini 2.5 Flash Lite)" else "Not Configured",
                            icon = Icons.Outlined.AutoAwesome,
                            highlightColor = if (isCloudActive) MaterialTheme.colorScheme.tertiary else null,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "On-Device Benchmark",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Test latency against Indian banking SMS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                haptics.click()
                                if (onRunBenchmark != null) {
                                    onRunBenchmark()
                                } else {
                                    isBenchmarking = true
                                    coroutineScope.launch {
                                        val sample = "Dear SBI UPI user, A/C 4321 debited by Rs 450.00 on 28-Sep-2026 at Swiggy UPI ref 892341234901. Bal: Rs 15420.50 - SBI"
                                        val engine = OnDeviceAiEngine(context)
                                        val start = System.nanoTime()
                                        val res = engine.parseIncomingMessage(sample, "SBI-UPI")
                                        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
                                        val tx = res.transaction
                                        localBenchmarkResult = BenchmarkUiResult(
                                            merchant = tx?.merchant ?: "Swiggy",
                                            amount = "₹" + String.format(Locale.US, "%.2f", tx?.amount ?: 450.0),
                                            category = tx?.category?.label ?: "Food & Dining",
                                            latencyMs = String.format(Locale.US, "%.1f", elapsedMs).toDoubleOrNull() ?: elapsedMs,
                                            engine = res.engine,
                                            sampleText = sample,
                                        )
                                        isBenchmarking = false
                                    }
                                }
                            },
                            enabled = !isBenchmarking,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (isBenchmarking) "Testing..." else "Test On-Device Parser")
                        }
                    }

                    activeBenchmark?.let { res ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Benchmark Result • ${res.engine}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "${res.latencyMs} ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Merchant: ${res.merchant}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "Amount: ${res.amount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = "Category: ${res.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                        label = { Text(mode.getLabel(resolvedDeviceModel)) },
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
                    text = engineMode.getDescription(resolvedDeviceModel, resolvedAccelerator),
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
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Key, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                AiStudioKeyGuideCard(
                    isExpanded = isGuideExpanded,
                    onToggleExpand = { isGuideExpanded = !isGuideExpanded },
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

@Composable
fun AiStudioKeyGuideCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val haptics = LocalAppHaptics.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.click()
                        onToggleExpand()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "How to generate a free AI Studio Key",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "1. Open aistudio.google.com/app/apikey on your browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "2. Sign in with your Google account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "3. Tap 'Create API key' (or 'Get API key').",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "4. Select or create a project to generate your free key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "5. Copy the generated key (starts with AIzaSy...), paste it above, and tap 'Save Key'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• Gemini Flash Lite provides 500 requests per day for free with no credit card required.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                haptics.click()
                                uriHandler.openUri("https://aistudio.google.com/app/apikey")
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Open Google AI Studio")
                        }
                    }
                }
            }
        }
    }
}
