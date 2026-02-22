package com.cinerracam.core.model

data class Resolution(
    val width: Int,
    val height: Int,
) {
    val label: String = "${width}x${height}"
}

data class RecorderConfig(
    val cameraId: String,
    val resolution: Resolution,
    val targetFps: Int,
    val audioEnabled: Boolean,
    val outputUri: String,
    val compressionMode: CompressionMode,
)
