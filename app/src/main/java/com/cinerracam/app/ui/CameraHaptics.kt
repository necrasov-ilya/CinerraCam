package com.cinerracam.app.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class CameraHapticEvent {
    MODE_SWITCH,
    SHUTTER_TAP,
    RECORD_START,
    RECORD_STOP,
    PARAMETER_APPLY,
    LOCK_WARNING,
    ERROR,
}

interface CameraHapticEngine {
    fun perform(event: CameraHapticEvent, intensity: HapticsIntensity)
}

@Composable
fun rememberCameraHapticEngine(): CameraHapticEngine {
    val context = LocalContext.current
    return remember(context) { AndroidCameraHapticEngine(context.applicationContext) }
}

private class AndroidCameraHapticEngine(
    context: Context,
) : CameraHapticEngine {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private var lastParameterApplyMs: Long = 0L
    private var lastModeSwitchMs: Long = 0L

    override fun perform(event: CameraHapticEvent, intensity: HapticsIntensity) {
        if (intensity == HapticsIntensity.OFF) {
            return
        }

        val vib = vibrator ?: return
        if (!vib.hasVibrator()) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (event == CameraHapticEvent.PARAMETER_APPLY && now - lastParameterApplyMs < 80L) {
            return
        }
        if (event == CameraHapticEvent.MODE_SWITCH && now - lastModeSwitchMs < 120L) {
            return
        }

        val effect = buildEffect(event, intensity)
        if (effect == null) {
            return
        }

        if (event == CameraHapticEvent.PARAMETER_APPLY) {
            lastParameterApplyMs = now
        }
        if (event == CameraHapticEvent.MODE_SWITCH) {
            lastModeSwitchMs = now
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(28L)
        }
    }

    private fun buildEffect(
        event: CameraHapticEvent,
        intensity: HapticsIntensity,
    ): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }

        val ampSoft = when (intensity) {
            HapticsIntensity.OFF -> 0
            HapticsIntensity.NORMAL -> 110
            HapticsIntensity.RICH -> 160
        }
        val ampStrong = when (intensity) {
            HapticsIntensity.OFF -> 0
            HapticsIntensity.NORMAL -> 170
            HapticsIntensity.RICH -> 255
        }

        return when (event) {
            CameraHapticEvent.MODE_SWITCH ->
                VibrationEffect.createOneShot(18L, ampSoft)

            CameraHapticEvent.SHUTTER_TAP ->
                VibrationEffect.createWaveform(longArrayOf(0L, 16L, 22L, 24L), intArrayOf(0, ampSoft, 0, ampStrong), -1)

            CameraHapticEvent.RECORD_START ->
                VibrationEffect.createWaveform(longArrayOf(0L, 22L, 30L, 34L), intArrayOf(0, ampStrong, 0, ampSoft), -1)

            CameraHapticEvent.RECORD_STOP ->
                VibrationEffect.createOneShot(28L, ampStrong)

            CameraHapticEvent.PARAMETER_APPLY ->
                VibrationEffect.createOneShot(12L, ampSoft)

            CameraHapticEvent.LOCK_WARNING ->
                VibrationEffect.createWaveform(longArrayOf(0L, 18L, 28L, 18L), intArrayOf(0, ampSoft, 0, ampSoft), -1)

            CameraHapticEvent.ERROR ->
                VibrationEffect.createWaveform(longArrayOf(0L, 26L, 28L, 26L), intArrayOf(0, ampStrong, 0, ampStrong), -1)
        }
    }
}
