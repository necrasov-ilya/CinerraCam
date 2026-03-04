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
    FOCUS_ACQUIRED,
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
        if (intensity == HapticsIntensity.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val now = SystemClock.elapsedRealtime()
        if (event == CameraHapticEvent.PARAMETER_APPLY && now - lastParameterApplyMs < 60L) return
        if (event == CameraHapticEvent.MODE_SWITCH && now - lastModeSwitchMs < 100L) return

        val effect = buildEffect(event, intensity) ?: return

        if (event == CameraHapticEvent.PARAMETER_APPLY) lastParameterApplyMs = now
        if (event == CameraHapticEvent.MODE_SWITCH) lastModeSwitchMs = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(28L)
        }
    }

    private fun buildEffect(event: CameraHapticEvent, intensity: HapticsIntensity): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val ampLight = when (intensity) {
            HapticsIntensity.OFF -> 0
            HapticsIntensity.NORMAL -> 80
            HapticsIntensity.RICH -> 130
        }
        val ampMedium = when (intensity) {
            HapticsIntensity.OFF -> 0
            HapticsIntensity.NORMAL -> 130
            HapticsIntensity.RICH -> 190
        }
        val ampHeavy = when (intensity) {
            HapticsIntensity.OFF -> 0
            HapticsIntensity.NORMAL -> 190
            HapticsIntensity.RICH -> 255
        }

        return when (event) {
            CameraHapticEvent.MODE_SWITCH ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 8, 6, 10),
                    intArrayOf(0, ampMedium, 0, ampLight),
                    -1,
                )

            CameraHapticEvent.SHUTTER_TAP ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 6, 4, 8, 6, 12, 4, 10),
                    intArrayOf(0, ampHeavy, 0, ampMedium, 0, ampHeavy, 0, ampLight),
                    -1,
                )

            CameraHapticEvent.RECORD_START ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 14, 10, 20, 8, 14),
                    intArrayOf(0, ampHeavy, 0, ampMedium, 0, ampLight),
                    -1,
                )

            CameraHapticEvent.RECORD_STOP ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 10, 8, 18),
                    intArrayOf(0, ampMedium, 0, ampHeavy),
                    -1,
                )

            CameraHapticEvent.PARAMETER_APPLY ->
                VibrationEffect.createOneShot(8L, ampLight)

            CameraHapticEvent.LOCK_WARNING ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 14, 20, 14),
                    intArrayOf(0, ampMedium, 0, ampMedium),
                    -1,
                )

            CameraHapticEvent.ERROR ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 20, 16, 20, 16, 20),
                    intArrayOf(0, ampHeavy, 0, ampHeavy, 0, ampHeavy),
                    -1,
                )

            CameraHapticEvent.FOCUS_ACQUIRED ->
                VibrationEffect.createOneShot(6L, ampLight)
        }
    }
}
