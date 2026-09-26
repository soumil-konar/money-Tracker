package com.moneytracker.app.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.local.ThemeAccent
import com.moneytracker.app.ui.haptics.LocalAppHaptics

/**
 * Settings Categories for organized, decluttered navigation.
 */
enum class SettingsCategory(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.Tune),
    APPEARANCE("Appearance", Icons.Outlined.Palette),
    DATA_SYNC("Data & Sync", Icons.Outlined.Sync),
    AI_ENGINE("AI Engine", Icons.Outlined.AutoAwesome),
    SECURITY_SYSTEM("Security & System", Icons.Outlined.Security),
}

@Composable
fun QuickStatusPill(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun getAccentPreviewColors(accent: ThemeAccent): List<Color> {
    return when (accent) {
        ThemeAccent.EXPRESSIVE -> listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8), Color(0xFF34D399))
        ThemeAccent.MONOCHROME -> listOf(Color(0xFF18181B), Color(0xFF71717A), Color(0xFFFAFAFA))
        ThemeAccent.CRIMSON -> listOf(Color(0xFFBE123C), Color(0xFFFB7185), Color(0xFFFFE4E6))
        ThemeAccent.OCEAN -> listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFFE0F2FE))
        ThemeAccent.SAGE -> listOf(Color(0xFF15803D), Color(0xFF86EFAC), Color(0xFFDCFCE7))
        ThemeAccent.AMBER -> listOf(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFFEF3C7))
    }
}

@Composable
fun ExclusionKeywordChip(
    keyword: String,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = {
                    haptics.tick()
                    onRemove(keyword)
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove keyword",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
