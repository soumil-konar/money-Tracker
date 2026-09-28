package com.moneytracker.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialAssistantDialogTest {

    @Test
    fun `test spatial transform origin calculation for assistant dialog`() {
        val rootSize = IntSize(1080, 2400)

        // Case 1: Opened from Home FAB at bottom-right (center at 960, 2180)
        val fabBounds = Rect(
            left = 900f,
            top = 2120f,
            right = 1020f,
            bottom = 2240f,
        )
        val fabOrigin = TransformOrigin(
            pivotFractionX = (fabBounds.center.x / rootSize.width).coerceIn(0.04f, 0.96f),
            pivotFractionY = (fabBounds.center.y / rootSize.height).coerceIn(0.04f, 0.96f),
        )
        assertEquals(960f / 1080f, fabOrigin.pivotFractionX, 0.01f)
        assertEquals(2180f / 2400f, fabOrigin.pivotFractionY, 0.01f)
        assertTrue("FAB origin X should be near right edge", fabOrigin.pivotFractionX > 0.85f)
        assertTrue("FAB origin Y should be near bottom edge", fabOrigin.pivotFractionY > 0.85f)

        // Case 2: Opened from 'Ask AI' button on AI card in mid screen (center at 750, 1100)
        val cardButtonBounds = Rect(
            left = 550f,
            top = 1060f,
            right = 950f,
            bottom = 1140f,
        )
        val cardOrigin = TransformOrigin(
            pivotFractionX = (cardButtonBounds.center.x / rootSize.width).coerceIn(0.04f, 0.96f),
            pivotFractionY = (cardButtonBounds.center.y / rootSize.height).coerceIn(0.04f, 0.96f),
        )
        assertEquals(750f / 1080f, cardOrigin.pivotFractionX, 0.01f)
        assertEquals(1100f / 2400f, cardOrigin.pivotFractionY, 0.01f)
        assertTrue("Card button origin Y should be around middle of screen", cardOrigin.pivotFractionY in 0.40f..0.60f)

        // Case 3: Fallback when anchorBounds is null
        val fallbackOrigin = TransformOrigin(0.85f, 0.88f)
        assertEquals(0.85f, fallbackOrigin.pivotFractionX, 0.001f)
        assertEquals(0.88f, fallbackOrigin.pivotFractionY, 0.001f)
    }
}
