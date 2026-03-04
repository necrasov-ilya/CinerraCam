package com.cinerracam.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresPermission
import com.cinerracam.app.camera.internal.RawFramePipeline
import com.cinerracam.app.camera.internal.RawFramePipelineListener
import com.cinerracam.camera2.preview.PreviewSizeSelectionInput
import com.cinerracam.camera2.preview.PreviewSizeSelector
import com.cinerracam.camera2.preview.PreviewViewportCalculator
import com.cinerracam.camera2.preview.PreviewViewportInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class RawCameraController(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onCameraReady(snapshot: CameraCapabilitiesSnapshot)

        fun onStatus(message: String)

        fun onRecordingStateChanged(isRecording: Boolean, sessionLabel: String?)

        fun onStats(stats: RecordingStats)

        fun onLastSaved(uriString: String)

        fun onError(message: String, throwable: Throwable? = null)

        fun onPreviewDebug(info: String) = Unit
    }

    private companion object {
        private const val RECOVERY_DELAY_MS = 450L
        private const val FOREGROUND_REOPEN_DELAY_MS = 140L
        private const val DEFAULT_RELATIVE_PATH = "DCIM/CinerraCam"
        private const val RAW_READER_MAX_IMAGES = 6
    }

    private val cameraManager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("cinerracam-camera-thread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val previewViewportCalculator = PreviewViewportCalculator()
    private val previewSizeSelector = PreviewSizeSelector()

    private val framePipeline = RawFramePipeline(
        context = context,
        listener = object : RawFramePipelineListener {
            override fun onStatus(message: String) = postStatus(message)

            override fun onStats(stats: RecordingStats) {
                mainHandler.post { listener.onStats(stats) }
            }

            override fun onLastSaved(uri: String) = postLastSaved(uri)

            override fun onError(message: String, throwable: Throwable?) = postError(message, throwable)
        },
        getCameraCharacteristics = { cameraCharacteristics },
        isRecordingProvider = { isRecording.get() },
        recordingRelativePathProvider = { recordingRelativePath },
        elapsedSecProvider = ::currentElapsedSec,
        timestampLabelProvider = ::timestampLabel,
    )

    private var previewTextureView: TextureView? = null
    private var permissionGranted: Boolean = false
    private var appInForeground: Boolean = true

    private var selectedCameraId: String? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var availableRawSizes: List<RawSizeOption> = emptyList()
    private var selectedRawSize: RawSizeOption? = null
    private var availablePhotoResolutions: List<ResolutionOption> = emptyList()
    private var availableVideoResolutions: List<ResolutionOption> = emptyList()
    private var availableAspectRatios: List<AspectRatioOption> = emptyList()
    private var availablePreviewSizes: List<Size> = emptyList()
    private var selectedPhotoResolution: ResolutionOption? = null
    private var selectedVideoResolution: ResolutionOption? = null
    private var selectedAspectRatio: AspectRatioOption? = null

    private var availableWhiteBalancePresets: List<WhiteBalancePreset> = listOf(WhiteBalancePreset.AUTO)
    private var selectedWhiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO

    private var exposureCompensationRange: IntRange = 0..0
    private var exposureCompensationStep: Float = 0f
    private var exposureCompensationValue: Int = 0
    private var isoRange: IntRange? = null
    private var exposureTimeRangeNs: LongRange? = null
    private var selectedIso: Int? = null
    private var selectedExposureTimeNs: Long? = null

    private var supportsManualSensorControls: Boolean = false
    private var manualSensorEnabled: Boolean = false
    private var supportsVideoStabilization: Boolean = false
    private var videoStabilizationEnabled: Boolean = false

    private var hasFlashUnit: Boolean = false
    private var availableFlashModes: List<FlashMode> = listOf(FlashMode.OFF)
    private var selectedFlashMode: FlashMode = FlashMode.OFF
    private var availableAfModes: List<AfMode> = listOf(AfMode.CONTINUOUS_PICTURE)
    private var selectedAfMode: AfMode = AfMode.CONTINUOUS_PICTURE
    private var maxZoomRatio: Float = 1f
    private var zoomRatio: Float = 1f
    private var sensorArraySize: Rect? = null

    private var selectedPreviewSize: Size? = null
    private var currentMode: CaptureMode = CaptureMode.PHOTO
    private var targetFps: Int = 24

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var rawReader: ImageReader? = null

    private val isRecording = AtomicBoolean(false)

    private var recordingMode: CaptureMode? = null
    private var recordingSessionLabel: String? = null
    private var recordingRelativePath: String = DEFAULT_RELATIVE_PATH
    private var recordingStartedRealtimeMs: Long = 0L
    private var recordingStoppedRealtimeMs: Long = 0L

    private var stressStopRunnable: Runnable? = null
    private var previewDebugInfo: String = ""

    private val recoveryRunnable = Runnable { openOrReconfigure() }

    fun bindPreviewTexture(textureView: TextureView) {
        val isSamePreview = previewTextureView === textureView
        previewTextureView = textureView

        if (!appInForeground || !permissionGranted || !textureView.isAvailable) {
            return
        }

        if (isSamePreview) {
            selectedPreviewSize?.let { previewSize ->
                applyPreviewTransform(textureView, previewSize)
            }
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
            cameraHandler.post {
                stopRecordingInternal("Запись остановлена: камера недоступна")
                closeCameraSession()
            }
            postStatus("Разрешение камеры не выдано")
        }
    }

    fun onAppForegroundChanged(isForeground: Boolean) {
        appInForeground = isForeground
        cameraHandler.post {
            cameraHandler.removeCallbacks(recoveryRunnable)

            if (!isForeground) {
                stopRecordingInternal("Запись остановлена: приложение в фоне")
                closeCameraSession()
                return@post
            }

            if (!permissionGranted) {
                return@post
            }

            closeCameraSession()
            cameraHandler.postDelayed(recoveryRunnable, FOREGROUND_REOPEN_DELAY_MS)
        }
    }

    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceIn(1, 240)
        cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
    }

    fun setFlashMode(mode: FlashMode) {
        if (!hasFlashUnit && mode != FlashMode.OFF) return
        selectedFlashMode = mode
        cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
        postCapabilitiesSnapshot()
    }

    fun setAfMode(mode: AfMode) {
        if (mode !in availableAfModes) return
        selectedAfMode = mode
        cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
        postCapabilitiesSnapshot()
    }

    fun setZoomRatio(ratio: Float) {
        zoomRatio = ratio.coerceIn(1f, maxZoomRatio)
        cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
        postCapabilitiesSnapshot()
    }

    fun triggerAutoFocus(normX: Float, normY: Float) {
        cameraHandler.post {
            val device = cameraDevice ?: return@post
            val session = captureSession ?: return@post
            val preview = previewSurface ?: return@post

            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(preview)
                    if (isRecording.get()) rawReader?.surface?.let { addTarget(it) }
                    applyCommonCaptureControls(includeRaw = isRecording.get())
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

                    val rect = sensorArraySize
                    if (rect != null) {
                        val areaW = (rect.width() * 0.1f).toInt().coerceAtLeast(1)
                        val areaH = (rect.height() * 0.1f).toInt().coerceAtLeast(1)
                        val cx = (normX * rect.width()).toInt().coerceIn(areaW / 2, rect.width() - areaW / 2)
                        val cy = (normY * rect.height()).toInt().coerceIn(areaH / 2, rect.height() - areaH / 2)
                        val focusArea = android.hardware.camera2.params.MeteringRectangle(
                            cx - areaW / 2, cy - areaH / 2, areaW, areaH, 1000,
                        )
                        set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusArea))
                        set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(focusArea))
                    }
                }.build()
                session.capture(request, captureCallback, cameraHandler)
            } catch (t: Throwable) {
                postError("Ошибка автофокуса", t)
            }
        }
    }

    fun onModeChanged(mode: CaptureMode) {
        currentMode = mode
        if (isRecording.get()) {
            return
        }

        val previousRawSize = selectedRawSize
        val preferredResolution = when (mode) {
            CaptureMode.PHOTO -> selectedPhotoResolution
            CaptureMode.VIDEO, CaptureMode.STRESS -> selectedVideoResolution
        }
        if (preferredResolution != null) {
            adaptRawSizeForResolution(preferredResolution)
        }

        val rawSizeChanged = previousRawSize != selectedRawSize
        cameraHandler.post {
            if (cameraDevice != null && captureSession != null) {
                if (rawSizeChanged) {
                    configureSession()
                } else {
                    startRepeating(includeRaw = false)
                }
            }
        }
        postCapabilitiesSnapshot()
    }

    fun setRawSize(option: RawSizeOption) {
        if (isRecording.get()) {
            postStatus("RAW размер можно менять только до старта записи")
            return
        }

        if (option == selectedRawSize) {
            return
        }
        selectedRawSize = option
        selectedAspectRatio = AspectRatioOption.fromResolution(option.width, option.height)
        postCapabilitiesSnapshot()
        cameraHandler.post {
            if (cameraDevice != null) {
                configureSession()
            }
        }
    }

    fun setPhotoResolution(option: ResolutionOption) {
        if (isRecording.get()) {
            postStatus("Разрешение фото можно менять только до старта записи")
            return
        }

        if (option == selectedPhotoResolution) {
            return
        }
        selectedPhotoResolution = option
        selectedAspectRatio = AspectRatioOption.fromResolution(option.width, option.height)
        adaptRawSizeForResolution(option)
        postCapabilitiesSnapshot()
        cameraHandler.post {
            if (cameraDevice != null) {
                configureSession()
            }
        }
    }

    fun setVideoResolution(option: ResolutionOption) {
        if (isRecording.get()) {
            postStatus("Разрешение видео можно менять только до старта записи")
            return
        }

        if (option == selectedVideoResolution) {
            return
        }
        selectedVideoResolution = option
        selectedAspectRatio = AspectRatioOption.fromResolution(option.width, option.height)
        adaptRawSizeForResolution(option)
        postCapabilitiesSnapshot()
        cameraHandler.post {
            if (cameraDevice != null) {
                configureSession()
            }
        }
    }

    fun setAspectRatio(option: AspectRatioOption) {
        if (isRecording.get()) {
            postStatus("Формат кадра можно менять только до старта записи")
            return
        }

        if (option == selectedAspectRatio) {
            return
        }
        selectedAspectRatio = option

        val defaultArea = option.longSide.toLong() * option.shortSide.toLong()
        selectedRawSize = chooseClosestRawByAspect(
            options = availableRawSizes,
            targetArea = selectedRawSize?.area ?: defaultArea,
            targetAspect = option.ratio,
        ) ?: selectedRawSize
        selectedPhotoResolution = chooseClosestResolutionByAspect(
            options = availablePhotoResolutions,
            targetArea = selectedPhotoResolution?.area ?: defaultArea,
            targetAspect = option.ratio,
        )
        selectedVideoResolution = chooseClosestResolutionByAspect(
            options = availableVideoResolutions,
            targetArea = selectedVideoResolution?.area ?: defaultArea,
            targetAspect = option.ratio,
        )

        postCapabilitiesSnapshot()
        cameraHandler.post {
            if (cameraDevice != null) {
                configureSession()
            }
        }
    }

    fun setVideoStabilizationEnabled(enabled: Boolean) {
        videoStabilizationEnabled = enabled && supportsVideoStabilization
        cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
        postCapabilitiesSnapshot()
    }

    fun setWhiteBalancePreset(preset: WhiteBalancePreset) {
        val actual = availableWhiteBalancePresets.firstOrNull { it == preset } ?: return
        if (actual == selectedWhiteBalance) {
            return
        }
        selectedWhiteBalance = actual
        cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
        postCapabilitiesSnapshot()
    }

    fun setExposureCompensation(value: Int) {
        exposureCompensationValue = value.coerceIn(
            exposureCompensationRange.first,
            exposureCompensationRange.last,
        )
        if (!manualSensorEnabled) {
            cameraHandler.post { startRepeating(includeRaw = isRecording.get()) }
        }
        postCapabilitiesSnapshot()
    }

    fun setManualSensorEnabled(enabled: Boolean) {
        if (isRecording.get()) {
            postStatus("Ручной ISO/выдержка доступны только до старта записи")
            return
        }
        manualSensorEnabled = enabled && supportsManualSensorControls
        cameraHandler.post { startRepeating(includeRaw = false) }
        postCapabilitiesSnapshot()
    }

    fun setManualIso(iso: Int?) {
        if (isRecording.get()) {
            postStatus("ISO можно менять только до старта записи")
            return
        }
        val range = isoRange
        selectedIso = if (iso == null || range == null) null else iso.coerceIn(range.first, range.last)
        if (manualSensorEnabled) {
            cameraHandler.post { startRepeating(includeRaw = false) }
        }
        postCapabilitiesSnapshot()
    }

    fun setManualExposureTimeNs(exposureTimeNs: Long?) {
        if (isRecording.get()) {
            postStatus("Выдержку можно менять только до старта записи")
            return
        }
        val range = exposureTimeRangeNs
        selectedExposureTimeNs = if (exposureTimeNs == null || range == null) {
            null
        } else {
            exposureTimeNs.coerceIn(range.first, range.last)
        }
        if (manualSensorEnabled) {
            cameraHandler.post { startRepeating(includeRaw = false) }
        }
        postCapabilitiesSnapshot()
    }

    fun takePhoto() {
        cameraHandler.post {
            val device = cameraDevice
            val session = captureSession
            val rawSurface = rawReader?.surface

            if (device == null || session == null || rawSurface == null) {
                postStatus("Камера не готова к съемке")
                return@post
            }

            framePipeline.markPhotoCaptureRequested()
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(rawSurface)
                    applyCommonCaptureControls(includeRaw = true)
                }.build()

                session.capture(request, captureCallback, cameraHandler)
                postStatus("Съемка RAW фото...")
            } catch (t: Throwable) {
                framePipeline.rollbackPhotoCaptureRequest()
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
            framePipeline.resetRecordingStats()
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
            framePipeline.close()
        }
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
        recordingMode = null

        startRepeating(includeRaw = false)
        framePipeline.emitCurrentStats()
    }

    private fun currentElapsedSec(): Int {
        val start = recordingStartedRealtimeMs
        if (start <= 0L) {
            return 0
        }
        val end = if (isRecording.get()) {
            SystemClock.elapsedRealtime()
        } else {
            if (recordingStoppedRealtimeMs > 0L) recordingStoppedRealtimeMs else SystemClock.elapsedRealtime()
        }
        return ((end - start).coerceAtLeast(0L) / 1000L).toInt()
    }

    private fun openOrReconfigure() {
        val textureView = previewTextureView
        if (!appInForeground || !permissionGranted || textureView == null || !textureView.isAvailable) {
            return
        }

        if (!ensureCapabilitiesLoaded()) {
            return
        }

        if (cameraDevice == null) {
            openCameraDevice()
        } else {
            configureSession()
        }
    }

    private fun ensureCapabilitiesLoaded(): Boolean {
        if (selectedCameraId != null && cameraCharacteristics != null && availableRawSizes.isNotEmpty()) {
            return true
        }

        val selected = selectRawCamera() ?: run {
            postError("Не найдена камера с RAW_SENSOR")
            return false
        }

        selectedCameraId = selected.first
        val chars = selected.second
        cameraCharacteristics = chars

        val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSizes = streamMap
            ?.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.sortedByDescending { it.width * it.height }
            ?.map(RawSizeOption::from)
            .orEmpty()

        if (rawSizes.isEmpty()) {
            postError("Камера не отдает RAW размеры")
            return false
        }

        availableRawSizes = rawSizes
        selectedRawSize = selectedRawSize ?: pickDefaultRawSize(rawSizes)
        availablePhotoResolutions = streamMap
            ?.getOutputSizes(ImageFormat.JPEG)
            ?.sortedByDescending { it.width * it.height }
            ?.map(ResolutionOption::from)
            .orEmpty()
        availableVideoResolutions = streamMap
            ?.getOutputSizes(MediaRecorder::class.java)
            ?.sortedByDescending { it.width * it.height }
            ?.map(ResolutionOption::from)
            .orEmpty()

        if (availableVideoResolutions.isEmpty()) {
            availableVideoResolutions = availablePhotoResolutions
        }

        availablePreviewSizes = streamMap
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.sortedByDescending { it.width * it.height }
            .orEmpty()

        selectedPhotoResolution = selectedPhotoResolution ?: availablePhotoResolutions.firstOrNull()
        selectedVideoResolution = selectedVideoResolution ?: availableVideoResolutions.firstOrNull()

        availableAspectRatios = deriveAspectRatios(rawSizes, availablePhotoResolutions, availableVideoResolutions)
        selectedAspectRatio = selectedAspectRatio
            ?: selectedRawSize?.let { AspectRatioOption.fromResolution(it.width, it.height) }
            ?: availableAspectRatios.firstOrNull()

        availableWhiteBalancePresets = chars
            .get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.toList()
            ?.mapNotNull(WhiteBalancePreset::fromAwbMode)
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOf(WhiteBalancePreset.AUTO) }
        selectedWhiteBalance = availableWhiteBalancePresets.firstOrNull { it == WhiteBalancePreset.AUTO }
            ?: availableWhiteBalancePresets.first()

        val aeRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        exposureCompensationRange = aeRange?.lower?.let { lower ->
            aeRange.upper.let { upper -> lower..upper }
        } ?: (0..0)
        exposureCompensationStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f
        exposureCompensationValue = 0

        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        supportsManualSensorControls = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)

        isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { it.lower..it.upper }
        exposureTimeRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { it.lower..it.upper }
        selectedIso = isoRange?.first
        selectedExposureTimeNs = exposureTimeRangeNs?.let { (it.first + it.last) / 2L }
        manualSensorEnabled = false

        val supportsDigitalStab = chars
            .get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            ?: false
        val supportsOis = chars
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            ?: false
        supportsVideoStabilization = supportsDigitalStab || supportsOis
        videoStabilizationEnabled = supportsVideoStabilization

        hasFlashUnit = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        availableFlashModes = buildList {
            add(FlashMode.OFF)
            if (hasFlashUnit) {
                add(FlashMode.AUTO)
                add(FlashMode.ON)
                add(FlashMode.TORCH)
            }
        }
        selectedFlashMode = FlashMode.OFF

        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        availableAfModes = AfMode.entries.filter { it.camera2Value in afModes }
            .ifEmpty { listOf(AfMode.CONTINUOUS_PICTURE) }
        selectedAfMode = availableAfModes.firstOrNull { it == AfMode.CONTINUOUS_PICTURE }
            ?: availableAfModes.first()

        sensorArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        maxZoomRatio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 1f
        } else {
            chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        }
        zoomRatio = 1f

        postCapabilitiesSnapshot()
        return true
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
        } catch (security: SecurityException) {
            postError("Нет доступа к камере", security)
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
            val previewSize = pickPreviewSize(
                viewWidth = textureView.width.coerceAtLeast(1),
                viewHeight = textureView.height.coerceAtLeast(1),
            )
            selectedPreviewSize = previewSize
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val preview = Surface(surfaceTexture)
            previewSurface = preview
            applyPreviewTransform(textureView, previewSize)

            val reader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, RAW_READER_MAX_IMAGES)
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
                        scheduleRecovery()
                    }
                },
                cameraHandler,
            )
        } catch (t: Throwable) {
            postError("Ошибка конфигурации камеры", t)
            scheduleRecovery()
        }
    }

    private fun startRepeating(includeRaw: Boolean) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val preview = previewSurface ?: return

        try {
            val template = if (includeRaw) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
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
            scheduleRecovery()
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
        selectedPreviewSize = null

        cameraDevice?.close()
        cameraDevice = null

        framePipeline.clearPendingBuffers()
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            framePipeline.onCaptureResult(result)
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure,
        ) {
            framePipeline.reportDrop("capture_failed")
        }
    }

    private val rawImageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = try {
            reader.acquireNextImage()
        } catch (_: Throwable) {
            null
        } ?: return@OnImageAvailableListener

        framePipeline.onRawImage(image)
    }

    private fun adaptRawSizeForResolution(target: ResolutionOption) {
        val adapted = chooseClosestRawByAspect(
            options = availableRawSizes,
            targetArea = target.area,
            targetAspect = target.aspectRatio,
        ) ?: return

        if (adapted == selectedRawSize) {
            return
        }

        selectedRawSize = adapted
        selectedAspectRatio = AspectRatioOption.fromResolution(adapted.width, adapted.height)
    }

    private fun pickPreviewSize(viewWidth: Int, viewHeight: Int): Size {
        return previewSizeSelector.select(
            PreviewSizeSelectionInput(
                availableSizes = availablePreviewSizes,
                viewportWidth = viewWidth,
                viewportHeight = viewHeight,
                targetCaptureAspectRatio = selectedAspectRatio?.ratio ?: selectedRawSize?.aspectRatio,
                targetCaptureArea = null,
            ),
        )
    }

    private fun applyPreviewTransform(textureView: TextureView, previewSize: Size) {
        textureView.post {
            val viewWidth = textureView.width
            val viewHeight = textureView.height
            if (viewWidth <= 0 || viewHeight <= 0) {
                return@post
            }

            val chars = cameraCharacteristics
            val sensorOrientation = chars?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val lensFacing = chars?.get(CameraCharacteristics.LENS_FACING)
            val displayRotation = textureView.display?.rotation ?: Surface.ROTATION_0

            val transform = previewViewportCalculator.calculate(
                PreviewViewportInput(
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    bufferSize = previewSize,
                    sensorOrientation = sensorOrientation,
                    displayRotation = displayRotation,
                    lensFacing = lensFacing,
                    fillCenterCrop = true,
                ),
            )
            textureView.setTransform(transform.matrix)

            val crop = transform.cropRectInBuffer
            val debugLine = "Preview ${previewSize.width}x${previewSize.height} | rot=${transform.sensorToDisplayRotationDegrees} | crop=${crop.left.toInt()},${crop.top.toInt()},${crop.right.toInt()},${crop.bottom.toInt()}"
            if (debugLine != previewDebugInfo) {
                previewDebugInfo = debugLine
                postPreviewDebug(debugLine)
            }
        }
    }

    private fun deriveAspectRatios(
        raw: List<RawSizeOption>,
        photo: List<ResolutionOption>,
        video: List<ResolutionOption>,
    ): List<AspectRatioOption> {
        val all = mutableListOf<AspectRatioOption>()
        raw.forEach { all += AspectRatioOption.fromResolution(it.width, it.height) }
        photo.forEach { all += AspectRatioOption.fromResolution(it.width, it.height) }
        video.forEach { all += AspectRatioOption.fromResolution(it.width, it.height) }
        return all.distinctBy { it.label }.sortedByDescending { it.ratio }
    }

    private fun postCapabilitiesSnapshot() {
        val cameraId = selectedCameraId
        val selectedRaw = selectedRawSize
        if (cameraId == null || selectedRaw == null) {
            return
        }

        val snapshot = CameraCapabilitiesSnapshot(
            cameraId = cameraId,
            rawSizes = availableRawSizes,
            selectedRaw = selectedRaw,
            photoResolutions = availablePhotoResolutions,
            selectedPhotoResolution = selectedPhotoResolution,
            videoResolutions = availableVideoResolutions,
            selectedVideoResolution = selectedVideoResolution,
            aspectRatios = availableAspectRatios,
            selectedAspectRatio = selectedAspectRatio,
            whiteBalanceOptions = availableWhiteBalancePresets,
            selectedWhiteBalance = selectedWhiteBalance,
            exposureCompensationRange = exposureCompensationRange,
            exposureCompensationStep = exposureCompensationStep,
            exposureCompensationValue = exposureCompensationValue,
            isoRange = isoRange,
            selectedIso = selectedIso,
            exposureTimeRangeNs = exposureTimeRangeNs,
            selectedExposureTimeNs = selectedExposureTimeNs,
            supportsManualSensor = supportsManualSensorControls,
            manualSensorEnabled = manualSensorEnabled,
            supportsVideoStabilization = supportsVideoStabilization,
            videoStabilizationEnabled = videoStabilizationEnabled,
            availableFlashModes = availableFlashModes,
            selectedFlashMode = selectedFlashMode,
            availableAfModes = availableAfModes,
            selectedAfMode = selectedAfMode,
            maxZoomRatio = maxZoomRatio,
            zoomRatio = zoomRatio,
            hasFlashUnit = hasFlashUnit,
        )

        mainHandler.post {
            listener.onCameraReady(snapshot)
        }
    }

    private fun CaptureRequest.Builder.applyCommonCaptureControls(includeRaw: Boolean) {
        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        set(CaptureRequest.CONTROL_AF_MODE, selectedAfMode.camera2Value)
        set(CaptureRequest.CONTROL_AWB_LOCK, false)
        set(CaptureRequest.CONTROL_AE_LOCK, false)
        set(CaptureRequest.CONTROL_CAPTURE_INTENT, if (includeRaw) {
            if (isRecording.get()) CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD
            else CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE
        } else {
            CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW
        })

        applyWhiteBalance()
        applyExposureAndIso()
        applyStabilization()
        applyFlash()
        applyZoom()

        set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)

        val fpsRange = pickFpsRange(targetFps)
        if (fpsRange != null) {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }

        if (supportsLensShadingMapMode()) {
            set(
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON,
            )
        }
    }

    private fun CaptureRequest.Builder.applyWhiteBalance() {
        val available = availableWhiteBalancePresets
        val selected = available.firstOrNull { it == selectedWhiteBalance } ?: WhiteBalancePreset.AUTO
        set(CaptureRequest.CONTROL_AWB_MODE, selected.awbMode)
    }

    private fun CaptureRequest.Builder.applyExposureAndIso() {
        val manual = !isRecording.get() &&
            manualSensorEnabled &&
            supportsManualSensorControls &&
            selectedIso != null &&
            selectedExposureTimeNs != null

        if (manual) {
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            set(CaptureRequest.SENSOR_SENSITIVITY, selectedIso)
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, selectedExposureTimeNs)
            return
        }

        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        val compensation = exposureCompensationValue.coerceIn(
            exposureCompensationRange.first,
            exposureCompensationRange.last,
        )
        set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, compensation)
    }

    private fun CaptureRequest.Builder.applyStabilization() {
        val chars = cameraCharacteristics ?: return
        val shouldEnable = videoStabilizationEnabled && currentMode != CaptureMode.PHOTO

        val videoModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val canUseVideoStab = videoModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        if (canUseVideoStab) {
            set(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (shouldEnable) CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            )
        }

        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val canUseOis = oisModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        if (canUseOis) {
            val oisOn = shouldEnable && !canUseVideoStab
            set(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (oisOn) CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                else CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF,
            )
        }
    }

    private fun CaptureRequest.Builder.applyFlash() {
        if (!hasFlashUnit) return
        when (selectedFlashMode) {
            FlashMode.OFF -> {
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            FlashMode.ON -> {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
            }
            FlashMode.AUTO -> {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }
            FlashMode.TORCH -> {
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
        }
    }

    private fun CaptureRequest.Builder.applyZoom() {
        if (zoomRatio <= 1f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        } else {
            val rect = sensorArraySize ?: return
            val cropW = (rect.width() / zoomRatio).toInt()
            val cropH = (rect.height() / zoomRatio).toInt()
            val left = (rect.width() - cropW) / 2
            val top = (rect.height() - cropH) / 2
            set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + cropW, top + cropH))
        }
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
                "Ошибка камеры: доступ временно отключен системой (код 3)"
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

        val exact = ranges.filter { target in it.lower..it.upper }
        if (exact.isNotEmpty()) {
            return exact.minByOrNull {
                val span = it.upper - it.lower
                span * 10 + abs(it.upper - target)
            }
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

    private fun postPreviewDebug(info: String) {
        mainHandler.post { listener.onPreviewDebug(info) }
    }

    private fun postError(message: String, throwable: Throwable? = null) {
        mainHandler.post { listener.onError(message, throwable) }
    }
}
