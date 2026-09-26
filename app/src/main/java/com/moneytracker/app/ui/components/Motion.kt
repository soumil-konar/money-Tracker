package com.moneytracker.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val RevealStepMillis = 55
private const val RevealDurationMillis = 420

@Composable
fun MotionReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = RevealDurationMillis,
                delayMillis = index.coerceAtLeast(0) * RevealStepMillis,
                easing = FastOutSlowInEasing,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = RevealDurationMillis,
                delayMillis = index.coerceAtLeast(0) * RevealStepMillis,
                easing = FastOutSlowInEasing,
            ),
            initialOffsetY = { offset -> maxOf(offset / 6, 36) },
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        content = content,
    )
}

fun Modifier.trackerAnimateContent(): Modifier {
    return animateContentSize(
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessLow,
        ),
    )
}

@Composable
fun rememberRevealProgress(
    targetValue: Float,
    delayMillis: Int = 0,
    durationMillis: Int = 700,
): Float {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(targetValue, delayMillis) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        revealed = true
    }
    val animatedValue by animateFloatAsState(
        targetValue = if (revealed) targetValue else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "trackerRevealProgress",
    )
    return animatedValue
}
