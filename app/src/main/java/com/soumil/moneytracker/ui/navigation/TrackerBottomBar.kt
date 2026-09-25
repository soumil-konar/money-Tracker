package com.soumil.moneytracker.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

/**
 * A floating, pill-shaped glassmorphic navigation dock inspired by modern fintech designs.
 *
 * Features:
 * - Master floating card with frosted glassmorphism, specular rim highlights, and ambient glow.
 * - Top action pill ("Add transaction") matching the capture action button in reference UI.
 * - Inner pill frame enclosing 4 navigation destinations (Home, Transactions, More, Settings).
 * - Expressive Material 3 sliding glass capsule indicator with spring physics.
 * - Expressive icon scale bounce, morph between filled/outlined icons, and tactile squish feedback.
 */
@Composable
fun TrackerBottomBar(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    onAddTransaction: (() -> Unit)? = null,
) {
    val haptics = LocalAppHaptics.current
    val isDark = isSystemInDarkTheme()

    val destinations = bottomDestinations
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.let {
        if (it >= 0) it else 0
    }

    // Styling tokens for single sleek floating pill dock
    val pillShape = CircleShape

    val surfaceBaseColor = if (isDark) {
        Color(0xFF14161C).copy(alpha = 0.88f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.92f)
    }

    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.24f else 0.55f),
            Color.White.copy(alpha = if (isDark) 0.05f else 0.15f),
        ),
    )

    val specularBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.12f else 0.35f),
            Color.Transparent,
            Color.Black.copy(alpha = if (isDark) 0.18f else 0.04f),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 1. Ambient theme glow beneath the floating pill bar (inspired by UI_2.png)
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(30.dp)
                .offset(y = 8.dp)
                .blur(28.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.30f else 0.16f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )

        // 2. Main Glassmorphic Dock Pill (Single container - no outer double border, compact width)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 308.dp)
                .fillMaxWidth(0.80f)
                .shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.40f),
                )
                .clip(pillShape)
                .background(surfaceBaseColor)
                .background(specularBrush)
                .border(BorderStroke(1.dp, rimBrush), pillShape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            val totalWidth = maxWidth
            val tabWidth = totalWidth / destinations.size

            // Material 3 Expressive animated sliding indicator position
            val animatedIndex by animateFloatAsState(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dockIndicatorOffset",
            )

            // Gliding active capsule indicator
            val indicatorShape = CircleShape
            val indicatorBgColor = if (isDark) {
                Color(0xFF323640).copy(alpha = 0.88f)
            } else {
                Color.White.copy(alpha = 0.95f)
            }
            val indicatorRimBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.30f else 0.65f),
                    Color.White.copy(alpha = if (isDark) 0.08f else 0.20f),
                ),
            )

            Box(
                modifier = Modifier
                    .offset(x = tabWidth * animatedIndex)
                    .width(tabWidth)
                    .height(48.dp)
                    .clip(indicatorShape)
                    .background(indicatorBgColor)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.10f))
                    .border(BorderStroke(0.8.dp, indicatorRimBrush), indicatorShape)
                    .shadow(
                        elevation = 4.dp,
                        shape = indicatorShape,
                        ambientColor = Color.Black.copy(alpha = 0.20f),
                        spotColor = Color.Black.copy(alpha = 0.25f),
                    ),
            )

            // 4 Navigation Tab Items
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    val isSelected = index == selectedIndex
                    DockTabItem(
                        destination = destination,
                        selected = isSelected,
                        onClick = { onNavigate(destination) },
                        isDark = isDark,
                        modifier = Modifier.width(tabWidth),
                    )
                }
            }
        }
    }
}

/**
 * Individual navigation tab with Material 3 Expressive spring physics, icon morph, and tactile squish.
 */
@Composable
private fun DockTabItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Expressive tactile squish when tapped
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "dockItemPressScale",
    )

    // Expressive icon bounce when selected
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 0.94f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dockItemIconScale",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) Color.White else MaterialTheme.colorScheme.primary
        } else {
            if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        },
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dockItemColor",
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.selection()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.icon,
                contentDescription = destination.label,
                tint = contentColor,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = destination.label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
