package com.cinerracam.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresPermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class RawCameraController(
    private val context: Context,
    private val listener: Listener,
) {
    private data class PendingImage(
        val image: Image,
        val kind: PendingKind,
        val frameIndex: Long,
    )

    private enum class PendingKind {
        PHOTO,
        RECORD,
    }

    interface Listener {
        fun onCameraReady(cameraId: String, rawSizes: List<RawSizeOption>, selected: RawSizeOption)

        fun onStatus(message: String)

        fun onRecordingStateChanged(isRecording: Boolean, sessionLabel: String?)

        fun onStats(stats: RecordingStats)

        fun onLastSaved(uriString: String)

        fun onError(message: String, throwable: Throwable? = null)
    }

    private companion object {
        private const val MAX_RESULT_CACHE = 256
        private const val WRITE_QUEUE_CAPACITY = 3
        private const val RECOVERY_DELAY_MS = 450L
        private const val DEFAULT_RELATIVE_PATH = "DCIM/CinerraCam"
    }

    private val cameraManager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("cinerracam-camera-thread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val writerExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(WRITE_QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "cinerracam-dng-writer").apply { isDaemon = true }
        },
    )

    private var previewTextureView: TextureView? = null
    private var permissionGranted: Boolean = false
    private var appInForeground: Boolean = true

    private var selectedCameraId: String? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var availableRawSizes: List<RawSizeOption> = emptyList()
    private var selectedRawSize: RawSizeOption? = null

    private var targetFps: Int = 24

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var rawReader: ImageReader? = null

    private val resultByTimestamp = HashMap<Long, TotalCaptureResult>()
    private val resultOrder = ArrayDeque<Long>()
    private val imageByTimestamp = HashMap<Long, PendingImage>()
    private val imageOrder = ArrayDeque<Long>()

    private val isRecording = AtomicBoolean(false)
    private val frameCounter = AtomicLong(0)
    private val pendingPhotoCaptures = AtomicInteger(0)

    private var recordingMode: CaptureMode? = null
    private var recordingSessionLabel: String? = null
    private var recordingRelativePath: String = DEFAULT_RELATIVE_PATH
    private var recordingStartedRealtimeMs: Long = 0L
    private var recordingStoppedRealtimeMs: Long = 0L

    private var stressStopRunnable: Runnable? = null

    private val statsLock = Any()
    private var stats: RecordingStats = RecordingStats()
    private var totalWriteMs: Double = 0.0
    private var dropStatusCounter: Long = 0L
    private val recoveryRunnable = Runnable { openOrReconfigure() }

    init {
        writerExecutor.rejectedExecutionHandler = ThreadPoolExecutor.AbortPolicy()
    }

    fun bindPreviewTexture(textureView: TextureView) {
        val isSamePreview = previewTextureView === textureView
        previewTextureView = textureView

        if (!appInForeground || !permissionGranted || !textureView.isAvailable) {
            return
        }

        if (!isSamePreview || cameraDevice == null || captureSession == null) {
            cameraHandler.post { openOrReconfigure() }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        permissionGranted = granted
        if (granted && appInForeground) {
            cameraHandler.post { openOrReconfigure() }
        } else {
            cameraHandler.post { closeCameraSession() }
            postStatus("Разрешение камеры не выдано")
        }
    }

    fun onAppForegroundChanged(isForeground: Boolean) {
        appInForeground = isForeground
        cameraHandler.post {
            if (!isForeground) {
                stopRecordingInternal("Запись остановлена: приложение в фоне")
                closeCameraSession()
                return@post
            }

            if (permissionGranted) {
                openOrReconfigure()
            }
        }
    }

    fun setTargetFps(fps: Int) {
        targetFps = fps
        if (isRecording.get()) {
            cameraHandler.post { startRepeating(includeRaw = true) }
        }
    }

    fun setRawSize(option: RawSizeOption) {
        if (option == selectedRawSize) {
            return
        }
        selectedRawSize = option
        cameraHandler.post {
            if (isRecording.get()) {
                postStatus("Сначала остановите запись, затем меняйте RAW размер")
                return@post
            }
            if (cameraDevice != null) {
                configureSession()
            }
        }
    }

    fun takePhoto() {
        cameraHandler.post {
            val device = cameraDevice
            val session = captureSession
            val preview = previewSurface
            val rawSurface = rawReader?.surface

            if (device == null || session == null || preview == null || rawSurface == null) {
                postStatus("Камера не готова к съемке")
                return@post
            }

            pendingPhotoCaptures.incrementAndGet()
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(preview)
                    addTarget(rawSurface)
                    applyCommonCaptureControls(includeRaw = true)
                }.build()

                session.capture(request, captureCallback, cameraHandler)
                postStatus("Съемка RAW фото...")
            } catch (t: Throwable) {
                pendingPhotoCaptures.decrementAndGet()
                postError("Не удалось выполнить фото-съемку", t)
            }
        }
    }

    fun startRecording(mode: CaptureMode, stressDurationSec: Int) {
        cameraHandler.post {
            if (isRecording.get()) {
                return@post
            }

            if (cameraDevice == null || captureSession == null || rawReader == null) {
                postStatus("Камера не готова к записи")
                return@post
            }

            val stamp = timestampLabel()
            val sessionPrefix = when (mode) {
                CaptureMode.VIDEO -> "RAW_VIDEO"
                CaptureMode.STRESS -> "RAW_STRESS"
                CaptureMode.PHOTO -> "RAW_PHOTO"
            }

            recordingMode = mode
            recordingSessionLabel = "${sessionPrefix}_$stamp"
            recordingRelativePath = "$DEFAULT_RELATIVE_PATH/${recordingSessionLabel}"

            isRecording.set(true)
            frameCounter.set(0)
            resetStatsLocked()
            recordingStartedRealtimeMs = SystemClock.elapsedRealtime()
            recordingStoppedRealtimeMs = 0L

            postRecordingState(true, recordingSessionLabel)
            postStatus("Запись RAW начата: $recordingSessionLabel")

            startRepeating(includeRaw = true)

            if (mode == CaptureMode.STRESS) {
                val stopRunnable = Runnable {
                    stopRecordingInternal("Стресс-тест завершен")
                }
                stressStopRunnable = stopRunnable
                cameraHandler.postDelayed(stopRunnable, stressDurationSec.coerceAtLeast(1) * 1000L)
            }
        }
    }

    fun stopRecording() {
        cameraHandler.post {
            stopRecordingInternal("Запись остановлена")
        }
    }

    fun close() {
        cameraHandler.post {
            cameraHandler.removeCallbacks(recoveryRunnable)
            stopRecordingInternal("Камера остановлена")
            closeCameraSession()
        }

        writerExecutor.shutdownNow()
        cameraThread.quitSafely()
    }

    private fun stopRecordingInternal(statusMessage: String) {
        if (!isRecording.getAndSet(false)) {
            return
        }

        stressStopRunnable?.let { cameraHandler.removeCallbacks(it) }
        stressStopRunnable = null

        recordingStoppedRealtimeMs = SystemClock.elapsedRealtime()
        postRecordingState(false, recordingSessionLabel)
        postStatus(statusMessage)

        startRepeating(includeRaw = false)
        emitStats()
    }

    private fun openOrReconfigure() {
        val textureView = previewTextureView
        if (!appInForeground || !permissionGranted || textureView == null || !textureView.isAvailable) {
            return
        }

        if (selectedCameraId == null || cameraCharacteristics == null || availableRawSizes.isEmpty()) {
            val selected = selectRawCamera() ?: run {
                postError("Не найдена камера с RAW_SENSOR")
                return
            }

            selectedCameraId = selected.first
            cameraCharacteristics = selected.second

            val rawSizes = selected.second
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.RAW_SENSOR)
                ?.sortedByDescending { it.width * it.height }
                ?.map(RawSizeOption::from)
                .orEmpty()

            if (rawSizes.isEmpty()) {
                postError("Камера не отдает RAW размеры")
                return
            }

            availableRawSizes = rawSizes
            selectedRawSize = selectedRawSize ?: pickDefaultRawSize(rawSizes)

            postCameraReady(
                cameraId = selected.first,
                rawSizes = rawSizes,
                selected = requireNotNull(selectedRawSize),
            )
        }

        if (cameraDevice == null) {
            openCameraDevice()
        } else {
            configureSession()
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @SuppressLint("MissingPermission")
    private fun openCameraDevice() {
        val cameraId = selectedCameraId ?: return
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    configureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice == camera) {
                        cameraDevice = null
                    }
                    closeCameraSession()
                    postStatus("Камера отключена. Переподключаю...")
                    scheduleRecovery()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice == camera) {
                        cameraDevice = null
                    }
                    closeCameraSession()
                    postError(cameraErrorMessage(error))
                    scheduleRecovery()
                }
            }, cameraHandler)
        } catch (t: Throwable) {
            postError("Не удалось открыть камеру", t)
        }
    }

    private fun configureSession() {
        val device = cameraDevice ?: return
        val textureView = previewTextureView ?: return
        val rawSize = selectedRawSize?.toSize() ?: return

        try {
            captureSession?.close()
            captureSession = null

            rawReader?.close()
            rawReader = null

            previewSurface?.release()
            previewSurface = null

            val surfaceTexture = textureView.surfaceTexture ?: return
            surfaceTexture.setDefaultBufferSize(textureView.width.coerceAtLeast(1), textureView.height.coerceAtLeast(1))
            val preview = Surface(surfaceTexture)
            previewSurface = preview

            val readerMaxImages = WRITE_QUEUE_CAPACITY + 2
            val reader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, readerMaxImages)
            reader.setOnImageAvailableListener(rawImageListener, cameraHandler)
            rawReader = reader

            device.createCaptureSession(
                listOf(preview, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startRepeating(includeRaw = isRecording.get())
                        postStatus("Камера готова: RAW ${rawSize.width}x${rawSize.height}")
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        postError("Не удалось настроить capture session")
                    }
                },
                cameraHandler,
            )
        } catch (t: Throwable) {
            postError("Ошибка конфигурации камеры", t)
        }
    }

    private fun startRepeating(includeRaw: Boolean) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val preview = previewSurface ?: return

        try {
            val template = if (includeRaw) {
                CameraDevice.TEMPLATE_RECORD
            } else {
                CameraDevice.TEMPLATE_PREVIEW
            }

            val request = device.createCaptureRequest(template).apply {
                addTarget(preview)

                if (includeRaw) {
                    rawReader?.surface?.let { addTarget(it) }
                }

                applyCommonCaptureControls(includeRaw = includeRaw)
            }.build()

            session.setRepeatingRequest(request, captureCallback, cameraHandler)
        } catch (t: Throwable) {
            postError("Ошибка запуска preview/request", t)
        }
    }

    private fun closeCameraSession() {
        cameraHandler.removeCallbacks(recoveryRunnable)

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

        rawReader?.close()
        rawReader = null

        previewSurface?.release()
        previewSurface = null

        cameraDevice?.close()
        cameraDevice = null

        clearPendingBuffers()
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
            resultByTimestamp[ts] = result
            resultOrder.add(ts)
            processPendingPair(ts)
            trimPendingBuffers()
        }
    }

    private val rawImageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = try {
            reader.acquireNextImage()
        } catch (_: Throwable) {
            null
        } ?: return@OnImageAvailableListener

        val timestampNs = image.timestamp
        val isPhotoRequest = pendingPhotoCaptures.get() > 0 && !isRecording.get()

        if (!isPhotoRequest && !isRecording.get()) {
            image.close()
            return@OnImageAvailableListener
        }

        val pendingImage = if (isPhotoRequest) {
            pendingPhotoCaptures.decrementAndGet()
            PendingImage(
                image = image,
                kind = PendingKind.PHOTO,
                frameIndex = 0L,
            )
        } else {
            val index = frameCounter.incrementAndGet()
            onFrameCaptured()
            PendingImage(
                image = image,
                kind = PendingKind.RECORD,
                frameIndex = index,
            )
        }

        imageByTimestamp[timestampNs] = pendingImage
        imageOrder.add(timestampNs)
        processPendingPair(timestampNs)
        trimPendingBuffers()
    }

    private fun submitWriteTask(task: FrameWriteTask, dropReason: String): Boolean {
        return try {
            writerExecutor.execute(task)
            updateQueueHighWatermark(writerExecutor.queue.size)
            true
        } catch (_: RejectedExecutionException) {
            onFrameDropped(dropReason)
            false
        }
    }

    private inner class FrameWriteTask(
        private val image: Image,
        private val result: TotalCaptureResult,
        private val fileName: String,
        private val relativePath: String,
        private val updateRecordingStats: Boolean,
        private val frameIndex: Long,
    ) : Runnable {
        override fun run() {
            val characteristics = cameraCharacteristics
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
                    postStatus("Фото сохранено")
                }

                postLastSaved(saveResult.uri.toString())
            } catch (t: Throwable) {
                onFrameDropped("write_failed")
                postError("Ошибка записи DNG", t)
            } finally {
                image.close()
            }
        }
    }

    private fun onFrameCaptured() {
        synchronized(statsLock) {
            stats = stats.copy(
                capturedFrames = stats.capturedFrames + 1,
                elapsedSec = currentElapsedSecLocked(),
            )
        }
        emitStats()
    }

    private fun onFrameWritten(writeMs: Double, bytesWritten: Long, frameIndex: Long) {
        synchronized(statsLock) {
            totalWriteMs += writeMs
            val written = stats.writtenFrames + 1
            val writtenMb = stats.writtenMb + (bytesWritten.toDouble() / (1024.0 * 1024.0))

            stats = stats.copy(
                writtenFrames = written,
                avgWriteMs = if (written > 0) totalWriteMs / written else 0.0,
                writtenMb = writtenMb,
                elapsedSec = currentElapsedSecLocked(),
            )
        }

        if (frameIndex % 10L == 0L || frameIndex <= 3L) {
            emitStats()
        }
    }

    private fun onFrameDropped(reason: String) {
        synchronized(statsLock) {
            stats = stats.copy(
                droppedFrames = stats.droppedFrames + 1,
                elapsedSec = currentElapsedSecLocked(),
            )
        }

        dropStatusCounter += 1
        if (dropStatusCounter % 15L == 1L) {
            postStatus("Дропы кадров: $reason")
        }
        emitStats()
    }

    private fun updateQueueHighWatermark(queueSize: Int) {
        synchronized(statsLock) {
            stats = stats.copy(
                queueHighWatermark = max(stats.queueHighWatermark, queueSize),
                elapsedSec = currentElapsedSecLocked(),
            )
        }
        emitStats()
    }

    private fun resetStatsLocked() {
        synchronized(statsLock) {
            stats = RecordingStats()
            totalWriteMs = 0.0
            dropStatusCounter = 0L
        }
        emitStats()
    }

    private fun currentElapsedSecLocked(): Int {
        val start = recordingStartedRealtimeMs
        if (start <= 0L) {
            return stats.elapsedSec
        }

        val end = if (isRecording.get()) {
            SystemClock.elapsedRealtime()
        } else {
            if (recordingStoppedRealtimeMs > 0L) recordingStoppedRealtimeMs else SystemClock.elapsedRealtime()
        }

        return ((end - start).coerceAtLeast(0L) / 1000L).toInt()
    }

    private fun emitStats() {
        val snapshot = synchronized(statsLock) { stats }
        mainHandler.post {
            listener.onStats(snapshot)
        }
    }

    private fun processPendingPair(timestampNs: Long) {
        val pendingImage = imageByTimestamp[timestampNs] ?: return
        val captureResult = resultByTimestamp[timestampNs] ?: return

        imageByTimestamp.remove(timestampNs)
        resultByTimestamp.remove(timestampNs)
        imageOrder.remove(timestampNs)
        resultOrder.remove(timestampNs)

        when (pendingImage.kind) {
            PendingKind.PHOTO -> {
                val task = FrameWriteTask(
                    image = pendingImage.image,
                    result = captureResult,
                    fileName = "IMG_${timestampLabel()}.dng",
                    relativePath = "$DEFAULT_RELATIVE_PATH/Photos",
                    updateRecordingStats = false,
                    frameIndex = 0L,
                )
                if (!submitWriteTask(task, dropReason = "photo_write_queue_overflow")) {
                    pendingImage.image.close()
                }
            }

            PendingKind.RECORD -> {
                val task = FrameWriteTask(
                    image = pendingImage.image,
                    result = captureResult,
                    fileName = "frame_${pendingImage.frameIndex.toString().padStart(6, '0')}.dng",
                    relativePath = recordingRelativePath,
                    updateRecordingStats = true,
                    frameIndex = pendingImage.frameIndex,
                )
                if (!submitWriteTask(task, dropReason = "record_write_queue_overflow")) {
                    pendingImage.image.close()
                }
            }
        }
    }

    private fun trimPendingBuffers() {
        while (resultOrder.size > MAX_RESULT_CACHE) {
            val oldest = resultOrder.removeFirstOrNull() ?: break
            resultByTimestamp.remove(oldest)
        }

        while (imageOrder.size > MAX_RESULT_CACHE) {
            val oldest = imageOrder.removeFirstOrNull() ?: break
            val stale = imageByTimestamp.remove(oldest) ?: continue
            stale.image.close()
            if (stale.kind == PendingKind.RECORD) {
                onFrameDropped("stale_image_without_result")
            }
        }
    }

    private fun clearPendingBuffers() {
        imageByTimestamp.values.forEach { pending ->
            runCatching { pending.image.close() }
        }
        imageByTimestamp.clear()
        imageOrder.clear()
        resultByTimestamp.clear()
        resultOrder.clear()
    }

    private fun CaptureRequest.Builder.applyCommonCaptureControls(includeRaw: Boolean) {
        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

        if (supportsAwbAuto()) {
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            set(CaptureRequest.CONTROL_AWB_LOCK, false)
        }

        if (supportsAberrationHighQuality()) {
            set(
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY,
            )
        }

        val fpsRange = pickFpsRange(targetFps)
        if (fpsRange != null) {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }

        if (includeRaw && supportsLensShadingMapMode()) {
            set(
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON,
            )
        }
    }

    private fun supportsAwbAuto(): Boolean {
        val chars = cameraCharacteristics ?: return false
        val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: return false
        return awbModes.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)
    }

    private fun supportsAberrationHighQuality(): Boolean {
        val chars = cameraCharacteristics ?: return false
        val modes = chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) ?: return false
        return modes.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
    }

    private fun supportsLensShadingMapMode(): Boolean {
        val chars = cameraCharacteristics ?: return false
        val modes = chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES) ?: return false
        return modes.contains(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
    }

    private fun scheduleRecovery() {
        if (!permissionGranted || !appInForeground) {
            return
        }

        val textureView = previewTextureView ?: return
        if (!textureView.isAvailable) {
            return
        }

        cameraHandler.removeCallbacks(recoveryRunnable)
        cameraHandler.postDelayed(recoveryRunnable, RECOVERY_DELAY_MS)
    }

    private fun cameraErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE ->
                "Ошибка камеры: камера уже используется другим клиентом"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
                "Ошибка камеры: достигнут лимит одновременно открытых камер"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED ->
                "Ошибка камеры: доступ временно отключен политикой устройства"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE ->
                "Ошибка камеры: сбой устройства камеры"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE ->
                "Ошибка камеры: сбой системного camera service"
            else -> "Ошибка камеры: $errorCode"
        }
    }

    private fun pickFpsRange(target: Int): Range<Int>? {
        val chars = cameraCharacteristics ?: return null
        val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return null

        val exact = ranges.firstOrNull { it.lower <= target && it.upper >= target }
        if (exact != null) {
            return Range(target, target).takeIf { target in exact.lower..exact.upper } ?: exact
        }

        return ranges.maxByOrNull { it.upper }
    }

    private fun selectRawCamera(): Pair<String, CameraCharacteristics>? {
        val cameraIds = cameraManager.cameraIdList

        val candidates = cameraIds.mapNotNull { id ->
            runCatching { cameraManager.getCameraCharacteristics(id) }
                .getOrNull()
                ?.let { chars -> id to chars }
        }

        val withRaw = candidates.filter { (_, chars) ->
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        }

        val backRaw = withRaw.firstOrNull { (_, chars) ->
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

        return backRaw ?: withRaw.firstOrNull()
    }

    private fun pickDefaultRawSize(options: List<RawSizeOption>): RawSizeOption {
        val preferred = options.firstOrNull { it.width <= 1920 && it.height <= 1080 }
        return preferred ?: options.last()
    }

    private fun timestampLabel(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun postStatus(message: String) {
        mainHandler.post { listener.onStatus(message) }
    }

    private fun postLastSaved(uri: String) {
        mainHandler.post { listener.onLastSaved(uri) }
    }

    private fun postRecordingState(isRecording: Boolean, sessionLabel: String?) {
        mainHandler.post { listener.onRecordingStateChanged(isRecording, sessionLabel) }
    }

    private fun postCameraReady(cameraId: String, rawSizes: List<RawSizeOption>, selected: RawSizeOption) {
        mainHandler.post { listener.onCameraReady(cameraId, rawSizes, selected) }
    }

    private fun postError(message: String, throwable: Throwable? = null) {
        mainHandler.post { listener.onError(message, throwable) }
    }
}
