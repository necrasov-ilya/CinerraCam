package com.cinerracam.storage.dng

import kotlinx.serialization.Serializable

@Serializable
data class ManifestDropEntry(
    val frameIndex: Long,
    val sensorTimestampNs: Long,
    val reason: String,
)

@Serializable
data class ManifestMetrics(
    val framesCaptured: Long,
    val framesWritten: Long,
    val framesDropped: Long,
    val avgWriteMs: Double,
)

@Serializable
data class SessionManifest(
    val formatVersion: Int,
    val cameraId: String,
    val resolution: String,
    val targetFps: Int,
    val audioEnabled: Boolean,
    val compressionMode: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val metrics: ManifestMetrics,
    val drops: List<ManifestDropEntry>,
)
