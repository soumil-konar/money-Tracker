package com.soumil.moneytracker.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.local.HapticIntensity
import com.soumil.moneytracker.data.local.ThemeAccent
import com.soumil.moneytracker.data.local.ThemeMode
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@Composable
fun ThemeAndHapticsSection(
    themeMode: ThemeMode,
    themeAccent: ThemeAccent,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectThemeAccent: (ThemeAccent) -> Unit,
    isHapticEnabled: Boolean,
    hapticIntensity: HapticIntensity,
    onToggleHapticEnabled: (Boolean) -> Unit,
    onSelectHapticIntensity: (HapticIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // App Appearance & Accents Card
        SectionCard(
            title = "App Appearance & Accents",
            subtitle = "Material 3 Expressive theming & prebuilt curated color accents",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Theme Mode (System, Dark, Light)
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

                // Material 3 Expressive Status Banner
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

                // Accent Palette Selector Grid
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

        // Haptic Feedback Card
        SectionCard(
            title = "Tactile Haptics & Vibration",
            subtitle = "Fine-tuned rich haptic feedback for buttons, clicks, navigation & errors",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Haptic Feedback",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Provides physical tactile sensations on taps, swipes, and transaction interactions.",
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Text(
                        text = "Vibration Intensity",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HapticIntensity.entries.forEach { intensity ->
                            FilterChip(
                                selected = hapticIntensity == intensity,
                                onClick = {
                                    haptics.selection()
                                    onSelectHapticIntensity(intensity)
                                },
                                label = { Text(intensity.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Vibration,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
