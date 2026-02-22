package com.cinerracam.app.camera.internal

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.os.SystemClock
import com.cinerracam.app.camera.CameraStorage

internal class FrameWriteTask(
    private val context: Context,
    private val image: Image,
    private val result: TotalCaptureResult,
    private val fileName: String,
    private val relativePath: String,
    private val updateRecordingStats: Boolean,
    private val frameIndex: Long,
    private val getCameraCharacteristics: () -> CameraCharacteristics?,
    private val onFrameWritten: (writeMs: Double, bytesWritten: Long, frameIndex: Long) -> Unit,
    private val onFrameDropped: (reason: String) -> Unit,
    private val onPhotoSaved: () -> Unit,
    private val onLastSaved: (String) -> Unit,
    private val onError: (String, Throwable?) -> Unit,
) : Runnable {
    override fun run() {
        val characteristics = getCameraCharacteristics()
        if (characteristics == null) {
            image.close()
            onFrameDropped("missing_camera_characteristics")
            return
        }

        val startedNs = SystemClock.elapsedRealtimeNanos()

        try {
            val saveResult = CameraStorage.saveDng(
                context = context,
                displayName = fileName,
                relativePath = relativePath,
            ) { stream ->
                DngCreator(characteristics, result).use { creator ->
                    creator.writeImage(stream, image)
                }
            }

            val writeMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0

            if (updateRecordingStats) {
                onFrameWritten(writeMs, saveResult.bytesWritten, frameIndex)
            } else {
                onPhotoSaved()
            }

            onLastSaved(saveResult.uri.toString())
        } catch (t: Throwable) {
            onFrameDropped("write_failed")
            onError("Ошибка записи DNG", t)
        } finally {
            image.close()
        }
    }
}
