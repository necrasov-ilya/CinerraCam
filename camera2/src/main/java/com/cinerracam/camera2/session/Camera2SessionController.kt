package com.cinerracam.camera2.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface RawFrameListener {
    fun onRawFrame(image: Image, result: TotalCaptureResult?, frameIndex: Long)

    fun onDroppedFrame(sensorTimestampNs: Long, reason: String)

    fun onError(throwable: Throwable)
}

data class SessionStartOptions(
    val cameraId: String,
    val rawSize: Size,
    val targetFps: Int,
    val maxImages: Int = 6,
)

class Camera2SessionController(context: Context) {
    private val cameraManager: CameraManager = context.getSystemService(CameraManager::class.java)

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cameraThread = HandlerThread("camera2-session-thread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val resultIndex = FrameResultIndex()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var frameIndex: Long = 0L

    @RequiresPermission(Manifest.permission.CAMERA)
    suspend fun start(options: SessionStartOptions, listener: RawFrameListener) {
        stop()

        withContext(Dispatchers.Default) {
            val device = openCamera(options.cameraId)
            cameraDevice = device

            val reader = ImageReader.newInstance(
                options.rawSize.width,
                options.rawSize.height,
                ImageFormat.RAW_SENSOR,
                options.maxImages,
            )
            imageReader = reader

            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireNextImage() ?: return@setOnImageAvailableListener
                val timestampNs = image.timestamp
                val result = resultIndex.take(timestampNs)

                frameIndex += 1
                listener.onRawFrame(image, result, frameIndex)
            }, cameraHandler)

            val session = createSession(device, listOf(reader.surface))
            captureSession = session

            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(reader.surface)

                val frameDurationNs = if (options.targetFps > 0) {
                    (1_000_000_000L / options.targetFps)
                } else {
                    0L
                }
                if (frameDurationNs > 0) {
                    set(CaptureRequest.SENSOR_FRAME_DURATION, frameDurationNs)
                }
            }.build()

            session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    val timestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                    resultIndex.put(result, timestampNs)
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure,
                ) {
                    listener.onDroppedFrame(
                        sensorTimestampNs = failure.frameNumber,
                        reason = "capture_failed",
                    )
                }
            }, cameraHandler)
        }
    }

    suspend fun stop() {
        withContext(Dispatchers.Default) {
            try {
                captureSession?.stopRepeating()
            } catch (_: Throwable) {
            }
            try {
                captureSession?.abortCaptures()
            } catch (_: Throwable) {
            }

            captureSession?.close()
            captureSession = null

            imageReader?.close()
            imageReader = null

            cameraDevice?.close()
            cameraDevice = null

            frameIndex = 0L
        }
    }

    fun close() {
        controllerScope.cancel()
        try {
            captureSession?.close()
        } catch (_: Throwable) {
        }
        try {
            imageReader?.close()
        } catch (_: Throwable) {
        }
        try {
            cameraDevice?.close()
        } catch (_: Throwable) {
        }
        cameraThread.quitSafely()
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(cameraId: String): CameraDevice {
        return suspendCancellableCoroutine { cont ->
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Camera disconnected: $cameraId"))
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Camera open error=$error for $cameraId"))
                    }
                }
            }, cameraHandler)
        }
    }

    private suspend fun createSession(
        device: CameraDevice,
        outputs: List<Surface>,
    ): CameraCaptureSession {
        return suspendCancellableCoroutine { cont ->
            device.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cont.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Failed to configure capture session"))
                    }
                }
            }, cameraHandler)
        }
    }
}
