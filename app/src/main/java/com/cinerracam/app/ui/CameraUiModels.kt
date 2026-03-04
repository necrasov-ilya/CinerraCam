package com.cinerracam.app.ui

enum class HapticsIntensity {
    OFF,
    NORMAL,
    RICH,
}

enum class QuickControl {
    EV,
    ISO,
    SHUTTER_SPEED,
    WB,
    FOCUS_MODE,
}

data class ControlAvailability(
    val enabled: Boolean,
    val lockReason: String? = null,
)

data class RecordingLockState(
    val resolution: ControlAvailability,
    val aspectRatio: ControlAvailability,
    val manualSensor: ControlAvailability,
)

fun buildRecordingLockState(isRecording: Boolean): RecordingLockState {
    if (!isRecording) {
        val unlocked = ControlAvailability(enabled = true, lockReason = null)
        return RecordingLockState(
            resolution = unlocked,
            aspectRatio = unlocked,
            manualSensor = unlocked,
        )
    }

    return RecordingLockState(
        resolution = ControlAvailability(false, "Блокируется во время REC"),
        aspectRatio = ControlAvailability(false, "Блокируется во время REC"),
        manualSensor = ControlAvailability(false, "Блокируется во время REC"),
    )
}
