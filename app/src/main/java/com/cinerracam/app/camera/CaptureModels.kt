package com.cinerracam.app.camera

import android.hardware.camera2.CameraMetadata
import android.util.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val area: Long get() = width.toLong() * height.toLong()
    val aspectRatio: Double get() = width.toDouble() / height.toDouble()
    val aspectLabel: String get() = buildAspectLabel(width, height)

    fun toSize(): Size = Size(width, height)

    companion object {
        fun from(size: Size): RawSizeOption = RawSizeOption(size.width, size.height)
    }
}

data class ResolutionOption(
    val width: Int,
    val height: Int,
) {
    val label: String get() = "${width}x${height}"
    val area: Long get() = width.toLong() * height.toLong()
    val aspectRatio: Double get() = width.toDouble() / height.toDouble()
    val aspectLabel: String get() = buildAspectLabel(width, height)

    companion object {
        fun from(size: Size): ResolutionOption = ResolutionOption(size.width, size.height)
    }
}

data class AspectRatioOption(
    val longSide: Int,
    val shortSide: Int,
) {
    val label: String get() = "${longSide}:${shortSide}"
    val ratio: Double get() = longSide.toDouble() / shortSide.toDouble()

    companion object {
        fun fromResolution(width: Int, height: Int): AspectRatioOption {
            val long = max(width, height)
            val short = min(width, height)
            val divisor = gcd(long, short).coerceAtLeast(1)
            return AspectRatioOption(long / divisor, short / divisor)
        }
    }
}

enum class WhiteBalancePreset(
    val awbMode: Int,
    val label: String,
) {
    AUTO(CameraMetadata.CONTROL_AWB_MODE_AUTO, "Auto"),
    INCANDESCENT(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, "Incandescent"),
    FLUORESCENT(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, "Fluorescent"),
    WARM_FLUORESCENT(CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT, "Warm Fluor"),
    DAYLIGHT(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, "Daylight"),
    CLOUDY(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "Cloudy"),
    TWILIGHT(CameraMetadata.CONTROL_AWB_MODE_TWILIGHT, "Twilight"),
    SHADE(CameraMetadata.CONTROL_AWB_MODE_SHADE, "Shade"),
    ;

    companion object {
        fun fromAwbMode(awbMode: Int): WhiteBalancePreset? = entries.firstOrNull { it.awbMode == awbMode }
    }
}

data class CameraCapabilitiesSnapshot(
    val cameraId: String,
    val rawSizes: List<RawSizeOption>,
    val selectedRaw: RawSizeOption,
    val photoResolutions: List<ResolutionOption>,
    val selectedPhotoResolution: ResolutionOption?,
    val videoResolutions: List<ResolutionOption>,
    val selectedVideoResolution: ResolutionOption?,
    val aspectRatios: List<AspectRatioOption>,
    val selectedAspectRatio: AspectRatioOption?,
    val whiteBalanceOptions: List<WhiteBalancePreset>,
    val selectedWhiteBalance: WhiteBalancePreset,
    val exposureCompensationRange: IntRange,
    val exposureCompensationStep: Float,
    val exposureCompensationValue: Int,
    val isoRange: IntRange?,
    val selectedIso: Int?,
    val exposureTimeRangeNs: LongRange?,
    val selectedExposureTimeNs: Long?,
    val supportsManualSensor: Boolean,
    val manualSensorEnabled: Boolean,
    val supportsVideoStabilization: Boolean,
    val videoStabilizationEnabled: Boolean,
)

data class RecordingStats(
    val capturedFrames: Long = 0,
    val writtenFrames: Long = 0,
    val droppedFrames: Long = 0,
    val avgWriteMs: Double = 0.0,
    val queueHighWatermark: Int = 0,
    val elapsedSec: Int = 0,
    val writtenMb: Double = 0.0,
)

fun chooseClosestResolutionByAspect(
    options: List<ResolutionOption>,
    targetArea: Long,
    targetAspect: Double,
): ResolutionOption? {
    if (options.isEmpty()) {
        return null
    }

    return options.minByOrNull { option ->
        val aspectScore = abs(option.aspectRatio - targetAspect) * 10_000.0
        val areaScore = abs(option.area - targetArea).toDouble() / 1_000.0
        aspectScore + areaScore
    }
}

fun chooseClosestRawByAspect(
    options: List<RawSizeOption>,
    targetArea: Long,
    targetAspect: Double,
): RawSizeOption? {
    if (options.isEmpty()) {
        return null
    }

    return options.minByOrNull { option ->
        val aspectScore = abs(option.aspectRatio - targetAspect) * 10_000.0
        val areaScore = abs(option.area - targetArea).toDouble() / 1_000.0
        aspectScore + areaScore
    }
}

private fun buildAspectLabel(width: Int, height: Int): String {
    val long = max(width, height)
    val short = min(width, height)
    val divisor = gcd(long, short).coerceAtLeast(1)
    return "${long / divisor}:${short / divisor}"
}

private tailrec fun gcd(a: Int, b: Int): Int {
    return if (b == 0) a else gcd(b, a % b)
}
