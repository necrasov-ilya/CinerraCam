package com.cinerracam.core.model

data class FramePacket(
    val imageRef: Any,
    val sensorTimestampNs: Long,
    val captureMetadataRef: Any,
    val frameIndex: Long,
)
