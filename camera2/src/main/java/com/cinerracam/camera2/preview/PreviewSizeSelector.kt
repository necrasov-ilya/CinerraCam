package com.cinerracam.camera2.preview

import android.util.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PreviewDimension(
    val width: Int,
    val height: Int,
)

data class PreviewSizeSelectionInput(
    val availableSizes: List<Size>,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val targetCaptureAspectRatio: Double?,
    val targetCaptureArea: Long?,
)

data class PreviewDimensionSelectionInput(
    val availableSizes: List<PreviewDimension>,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val targetCaptureAspectRatio: Double?,
    val targetCaptureArea: Long?,
)

class PreviewSizeSelector {
    fun select(input: PreviewSizeSelectionInput): Size {
        val selected = selectDimensions(
            PreviewDimensionSelectionInput(
                availableSizes = input.availableSizes.map { PreviewDimension(it.width, it.height) },
                viewportWidth = input.viewportWidth,
                viewportHeight = input.viewportHeight,
                targetCaptureAspectRatio = input.targetCaptureAspectRatio,
                targetCaptureArea = input.targetCaptureArea,
            ),
        )
        return Size(selected.width, selected.height)
    }

    fun selectDimensions(input: PreviewDimensionSelectionInput): PreviewDimension {
        val sizes = input.availableSizes
        if (sizes.isEmpty()) {
            return PreviewDimension(
                width = input.viewportWidth.coerceAtLeast(1),
                height = input.viewportHeight.coerceAtLeast(1),
            )
        }

        val viewportWidth = input.viewportWidth.coerceAtLeast(1)
        val viewportHeight = input.viewportHeight.coerceAtLeast(1)
        val viewportArea = viewportWidth.toLong() * viewportHeight.toLong()
        val targetArea = input.targetCaptureArea ?: viewportArea
        val targetAspect = input.targetCaptureAspectRatio ?: normalizeAspectRatio(viewportWidth, viewportHeight)

        return sizes.minByOrNull { size ->
            val sizeAspect = normalizeAspectRatio(size.width, size.height)
            val aspectPenalty = abs(sizeAspect - targetAspect) * 12_000.0

            val sizeArea = size.width.toLong() * size.height.toLong()
            val underfillPenalty = if (sizeArea < viewportArea) {
                800.0 + ((viewportArea - sizeArea).toDouble() / viewportArea.toDouble()) * 400.0
            } else {
                0.0
            }

            val areaPenalty = abs(sizeArea - targetArea).toDouble() / 1_000_000.0
            aspectPenalty + underfillPenalty + areaPenalty
        } ?: sizes.first()
    }

    private fun normalizeAspectRatio(width: Int, height: Int): Double {
        val longSide = max(width, height).coerceAtLeast(1)
        val shortSide = min(width, height).coerceAtLeast(1)
        return longSide.toDouble() / shortSide.toDouble()
    }
}
