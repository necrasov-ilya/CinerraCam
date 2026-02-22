package com.cinerracam.camera2.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewSizeSelectorTest {
    private val selector = PreviewSizeSelector()

    @Test
    fun picksClosestAspectAndEnoughArea() {
        val selected = selector.selectDimensions(
            PreviewDimensionSelectionInput(
                availableSizes = listOf(
                    PreviewDimension(1280, 720),   // 16:9
                    PreviewDimension(1920, 1080),  // 16:9
                    PreviewDimension(1440, 1080),  // 4:3
                ),
                viewportWidth = 1080,
                viewportHeight = 1920,
                targetCaptureAspectRatio = 16.0 / 9.0,
                targetCaptureArea = 1920L * 1080L,
            ),
        )

        assertEquals(PreviewDimension(1920, 1080), selected)
    }

    @Test
    fun avoidsTinyPreviewWhenViewportIsLarge() {
        val selected = selector.selectDimensions(
            PreviewDimensionSelectionInput(
                availableSizes = listOf(
                    PreviewDimension(640, 480),
                    PreviewDimension(1280, 720),
                    PreviewDimension(2560, 1440),
                ),
                viewportWidth = 1440,
                viewportHeight = 3120,
                targetCaptureAspectRatio = 16.0 / 9.0,
                targetCaptureArea = 2560L * 1440L,
            ),
        )

        assertEquals(PreviewDimension(2560, 1440), selected)
    }
}
