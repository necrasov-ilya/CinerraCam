package com.cinerracam.camera2

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size

class Camera2CapabilityProbe(context: Context) {
    private val cameraManager: CameraManager = context.getSystemService(CameraManager::class.java)

    fun probeAll(): List<RawCapability> = cameraManager.cameraIdList.map(::probeCamera)

    fun probeCamera(cameraId: String): RawCapability {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val sizes = map
            ?.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.toList()
            ?.sortedByDescending { it.width * it.height }
            .orEmpty()

        val minBySize = mutableMapOf<Size, Long>()
        val stallBySize = mutableMapOf<Size, Long>()

        sizes.forEach { size ->
            minBySize[size] = map?.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR, size) ?: 0L
            stallBySize[size] = map?.getOutputStallDuration(ImageFormat.RAW_SENSOR, size) ?: 0L
        }

        val maxRawStreams = chars.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW) ?: 0

        return RawCapability(
            cameraId = cameraId,
            supportsRaw = supportsRaw,
            rawOutputSizes = sizes,
            minFrameDurationNsBySize = minBySize,
            stallDurationNsBySize = stallBySize,
            maxRawStreams = maxRawStreams,
        )
    }
}
