package com.cinerracam.benchmark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManifestMetrics(
    @SerialName("framesCaptured")
    val framesCaptured: Long,
    @SerialName("framesWritten")
    val framesWritten: Long,
    @SerialName("framesDropped")
    val framesDropped: Long,
    @SerialName("avgWriteMs")
    val avgWriteMs: Double,
)

@Serializable
data class SessionManifest(
    @SerialName("cameraId")
    val cameraId: String,
    @SerialName("resolution")
    val resolution: String,
    @SerialName("targetFps")
    val targetFps: Int,
    @SerialName("startedAtEpochMs")
    val startedAtEpochMs: Long,
    @SerialName("finishedAtEpochMs")
    val finishedAtEpochMs: Long,
    @SerialName("metrics")
    val metrics: ManifestMetrics,
)
