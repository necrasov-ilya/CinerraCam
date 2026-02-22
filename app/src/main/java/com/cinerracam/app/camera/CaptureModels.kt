package com.cinerracam.app.camera

import android.util.Size

enum class CaptureMode {
    PHOTO,
    VIDEO,
    STRESS,
}

data class RawSizeOption(
    val width: Int,
    val height: Int,
) {
    val label: String get() = "${width}x${height}"

    fun toSize(): Size = Size(width, height)

    companion object {
        fun from(size: Size): RawSizeOption = RawSizeOption(size.width, size.height)
    }
}

data class RecordingStats(
    val capturedFrames: Long = 0,
    val writtenFrames: Long = 0,
    val droppedFrames: Long = 0,
    val avgWriteMs: Double = 0.0,
    val queueHighWatermark: Int = 0,
    val elapsedSec: Int = 0,
    val writtenMb: Double = 0.0,
)
