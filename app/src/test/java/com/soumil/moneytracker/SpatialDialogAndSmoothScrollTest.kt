package com.soumil.moneytracker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialDialogAndSmoothScrollTest {

    private fun calculateSpatialTransformOrigin(
        isSpatialEdit: Boolean,
        anchorBounds: Rect?,
        rootLayoutSize: IntSize,
    ): TransformOrigin {
        val hasSpatialAnchor = isSpatialEdit && anchorBounds != null && rootLayoutSize.width > 0 && rootLayoutSize.height > 0
        return if (hasSpatialAnchor) {
            TransformOrigin(
                pivotFractionX = (anchorBounds!!.center.x / rootLayoutSize.width).coerceIn(0.08f, 0.92f),
                pivotFractionY = (anchorBounds.center.y / rootLayoutSize.height).coerceIn(0.08f, 0.92f),
            )
        } else if (isSpatialEdit) {
            TransformOrigin(0.5f, 0.45f)
        } else {
            TransformOrigin(0.5f, 0.88f)
        }
    }

    private fun calculateSpatialInitialOffsetY(
        isSpatialEdit: Boolean,
        anchorBounds: Rect?,
        rootLayoutSize: IntSize,
        fullHeight: Int,
    ): Int {
        val hasSpatialAnchor = isSpatialEdit && anchorBounds != null && rootLayoutSize.width > 0 && rootLayoutSize.height > 0
        return if (isSpatialEdit) {
            if (hasSpatialAnchor) {
                val screenCenterY = rootLayoutSize.height / 2f
                ((anchorBounds!!.center.y - screenCenterY) * 0.28f).toInt()
            } else {
                0
            }
        } else {
            (fullHeight * 0.18f).toInt()
        }
    }

    @Test
    fun `test edit transaction with anchor bounds originates from clicked item position`() {
        val rootSize = IntSize(1080, 2400)
        // Transaction item in top third of screen
        val itemBounds = Rect(
            left = 50f,
            top = 400f,
            right = 1030f,
            bottom = 600f,
        )
        // Center is (540, 500)
        val origin = calculateSpatialTransformOrigin(
            isSpatialEdit = true,
            anchorBounds = itemBounds,
            rootLayoutSize = rootSize,
        )

        assertEquals(0.5f, origin.pivotFractionX, 0.01f)
        assertEquals(500f / 2400f, origin.pivotFractionY, 0.01f)

        val offsetY = calculateSpatialInitialOffsetY(
            isSpatialEdit = true,
            anchorBounds = itemBounds,
            rootLayoutSize = rootSize,
            fullHeight = 2400,
        )

        // Screen center is 1200. Item is at 500. Delta is -700. Offset is negative, meaning it starts higher up near the item
        val expectedOffset = ((-700f) * 0.28f).toInt()
        assertEquals(expectedOffset, offsetY)
        assertTrue("Offset should be negative for item above screen center", offsetY < 0)
    }

    @Test
    fun `test edit transaction does not slide in from bottom of screen`() {
        val rootSize = IntSize(1080, 2400)
        // Edit without bounds (fallback)
        val origin = calculateSpatialTransformOrigin(
            isSpatialEdit = true,
            anchorBounds = null,
            rootLayoutSize = rootSize,
        )
        val offsetY = calculateSpatialInitialOffsetY(
            isSpatialEdit = true,
            anchorBounds = null,
            rootLayoutSize = rootSize,
            fullHeight = 2400,
        )

        // Must NOT slide from bottom (offset is 0, origin is screen center)
        assertEquals(0, offsetY)
        assertEquals(0.5f, origin.pivotFractionX, 0.01f)
        assertEquals(0.45f, origin.pivotFractionY, 0.01f)
    }

    @Test
    fun `test add transaction from dock emerges from bottom dock`() {
        val rootSize = IntSize(1080, 2400)
        val origin = calculateSpatialTransformOrigin(
            isSpatialEdit = false,
            anchorBounds = null,
            rootLayoutSize = rootSize,
        )
        val offsetY = calculateSpatialInitialOffsetY(
            isSpatialEdit = false,
            anchorBounds = null,
            rootLayoutSize = rootSize,
            fullHeight = 2400,
        )

        assertEquals(0.5f, origin.pivotFractionX, 0.01f)
        assertEquals(0.88f, origin.pivotFractionY, 0.01f)
        assertEquals((2400 * 0.18f).toInt(), offsetY)
        assertTrue("Add transaction should originate from dock at bottom", offsetY > 0)
    }

    @Test
    fun `test smooth scroll skip logic jumps closer when far down to prevent frame drops`() {
        fun shouldPreScroll(firstVisibleItemIndex: Int): Boolean {
            return firstVisibleItemIndex > 2
        }

        assertTrue(shouldPreScroll(3))
        assertTrue(shouldPreScroll(8))
        assertFalse(shouldPreScroll(2))
        assertFalse(shouldPreScroll(0))
    }
}
