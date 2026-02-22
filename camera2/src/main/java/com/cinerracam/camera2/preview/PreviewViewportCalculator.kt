package com.cinerracam.camera2.preview

import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.view.Surface
import kotlin.math.max
import kotlin.math.min

data class PreviewViewportInput(
    val viewWidth: Int,
    val viewHeight: Int,
    val bufferSize: Size,
    val sensorOrientation: Int,
    val displayRotation: Int,
    val lensFacing: Int?,
    val fillCenterCrop: Boolean = true,
)

data class PreviewViewportTransform(
    val matrix: Matrix,
    val cropRectInBuffer: RectF,
    val sensorToDisplayRotationDegrees: Int,
    val rotatedBufferSize: Size,
)

class PreviewViewportCalculator {
    fun calculate(input: PreviewViewportInput): PreviewViewportTransform {
        val viewWidth = max(1, input.viewWidth)
        val viewHeight = max(1, input.viewHeight)
        val bufferWidth = max(1, input.bufferSize.width)
        val bufferHeight = max(1, input.bufferSize.height)

        val sensorToDisplayRotation = computeSensorToDisplayRotationDegrees(
            sensorOrientation = input.sensorOrientation,
            displayRotation = input.displayRotation,
            lensFacing = input.lensFacing,
        )

        val matrix = Matrix()
        matrix.postTranslate(-bufferWidth / 2f, -bufferHeight / 2f)

        if (input.lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f)
        }

        matrix.postRotate(sensorToDisplayRotation.toFloat())

        val mappedBounds = RectF(
            -bufferWidth / 2f,
            -bufferHeight / 2f,
            bufferWidth / 2f,
            bufferHeight / 2f,
        )
        matrix.mapRect(mappedBounds)

        val rotatedWidth = mappedBounds.width().coerceAtLeast(1f)
        val rotatedHeight = mappedBounds.height().coerceAtLeast(1f)

        val scale = if (input.fillCenterCrop) {
            max(viewWidth / rotatedWidth, viewHeight / rotatedHeight)
        } else {
            min(viewWidth / rotatedWidth, viewHeight / rotatedHeight)
        }

        matrix.postScale(scale, scale)
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f)

        val cropRectInBuffer = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
        val inverse = Matrix()
        if (matrix.invert(inverse)) {
            inverse.mapRect(cropRectInBuffer, RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat()))
            cropRectInBuffer.left = cropRectInBuffer.left.coerceIn(0f, bufferWidth.toFloat())
            cropRectInBuffer.top = cropRectInBuffer.top.coerceIn(0f, bufferHeight.toFloat())
            cropRectInBuffer.right = cropRectInBuffer.right.coerceIn(0f, bufferWidth.toFloat())
            cropRectInBuffer.bottom = cropRectInBuffer.bottom.coerceIn(0f, bufferHeight.toFloat())
        }

        val rotatedSize = if (sensorToDisplayRotation % 180 == 0) {
            Size(bufferWidth, bufferHeight)
        } else {
            Size(bufferHeight, bufferWidth)
        }

        return PreviewViewportTransform(
            matrix = matrix,
            cropRectInBuffer = cropRectInBuffer,
            sensorToDisplayRotationDegrees = sensorToDisplayRotation,
            rotatedBufferSize = rotatedSize,
        )
    }

    private fun computeSensorToDisplayRotationDegrees(
        sensorOrientation: Int,
        displayRotation: Int,
        lensFacing: Int?,
    ): Int {
        val displayDegrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        return if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation + displayDegrees) % 360
        } else {
            (sensorOrientation - displayDegrees + 360) % 360
        }
    }
}
