package com.cinerracam.camera2

import android.util.Size

data class RawCapability(
    val cameraId: String,
    val supportsRaw: Boolean,
    val rawOutputSizes: List<Size>,
    val minFrameDurationNsBySize: Map<Size, Long>,
    val stallDurationNsBySize: Map<Size, Long>,
    val maxRawStreams: Int,
)
