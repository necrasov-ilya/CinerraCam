package com.cinerracam.app.ui

import android.app.Application
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import com.cinerracam.app.camera.AspectRatioOption
import com.cinerracam.app.camera.CameraCapabilitiesSnapshot
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.RawCameraController
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.RecordingStats
import com.cinerracam.app.camera.ResolutionOption
import com.cinerracam.app.camera.WhiteBalancePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecorderUiState(
    val hasCameraPermission: Boolean = false,
    val cameraId: String? = null,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val statusMessage: String = "Нужно разрешение на камеру",
    val rawSizes: List<RawSizeOption> = emptyList(),
    val selectedRawSize: RawSizeOption? = null,
    val photoResolutions: List<ResolutionOption> = emptyList(),
    val selectedPhotoResolution: ResolutionOption? = null,
    val videoResolutions: List<ResolutionOption> = emptyList(),
    val selectedVideoResolution: ResolutionOption? = null,
    val aspectRatios: List<AspectRatioOption> = emptyList(),
    val selectedAspectRatio: AspectRatioOption? = null,
    val fpsOptions: List<Int> = listOf(12, 24, 30),
    val selectedFps: Int = 24,
    val stressDurationSec: Int = 20,
    val isRecording: Boolean = false,
    val sessionLabel: String? = null,
    val savePathLabel: String = "DCIM/CinerraCam",
    val stats: RecordingStats = RecordingStats(),
    val lastSavedUri: String? = null,
    val whiteBalanceOptions: List<WhiteBalancePreset> = listOf(WhiteBalancePreset.AUTO),
    val selectedWhiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val exposureCompensationRange: IntRange = 0..0,
    val exposureCompensationStep: Float = 0f,
    val exposureCompensationValue: Int = 0,
    val supportsManualSensor: Boolean = false,
    val manualSensorEnabled: Boolean = false,
    val isoRange: IntRange? = null,
    val selectedIso: Int? = null,
    val exposureTimeRangeNs: LongRange? = null,
    val selectedExposureTimeNs: Long? = null,
    val supportsVideoStabilization: Boolean = false,
    val videoStabilizationEnabled: Boolean = false,
    val previewDebugInfo: String = "",
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.NORMAL,
)

class RecorderViewModel(application: Application) : AndroidViewModel(application), RawCameraController.Listener {

    private val controller = RawCameraController(
        context = application.applicationContext,
        listener = this,
    )

    private val mutableState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = mutableState.asStateFlow()

    private inline fun reduce(crossinline transform: (RecorderUiState) -> RecorderUiState) {
        mutableState.update { current -> transform(current) }
    }

    fun onCameraPermissionChanged(granted: Boolean) {
        reduce {
            it.copy(
                hasCameraPermission = granted,
                statusMessage = if (granted) "Инициализация камеры..." else "Нужно разрешение на камеру",
            )
        }
        controller.onPermissionChanged(granted)
    }

    fun onPreviewTextureReady(textureView: TextureView) {
        controller.bindPreviewTexture(textureView)
    }

    fun onAppForegroundChanged(isForeground: Boolean) {
        controller.onAppForegroundChanged(isForeground)
    }

    fun onModeSelected(mode: CaptureMode) {
        if (uiState.value.isRecording) {
            reduce { it.copy(statusMessage = "Остановите запись перед сменой режима") }
            return
        }

        reduce { it.copy(mode = mode) }
        controller.onModeChanged(mode)
    }

    fun onRawSizeSelected(option: RawSizeOption) {
        if (uiState.value.isRecording) {
            reduce { it.copy(statusMessage = "Остановите запись перед сменой RAW-размера") }
            return
        }

        reduce { it.copy(selectedRawSize = option) }
        controller.setRawSize(option)
    }

    fun onPhotoResolutionSelected(option: ResolutionOption) {
        if (uiState.value.isRecording) {
            reduce { it.copy(statusMessage = "Остановите запись перед сменой разрешения фото") }
            return
        }

        reduce { it.copy(selectedPhotoResolution = option) }
        controller.setPhotoResolution(option)
    }

    fun onVideoResolutionSelected(option: ResolutionOption) {
        if (uiState.value.isRecording) {
            reduce { it.copy(statusMessage = "Остановите запись перед сменой разрешения видео") }
            return
        }

        reduce { it.copy(selectedVideoResolution = option) }
        controller.setVideoResolution(option)
    }

    fun onAspectRatioSelected(option: AspectRatioOption) {
        if (uiState.value.isRecording) {
            reduce { it.copy(statusMessage = "Остановите запись перед сменой формата кадра") }
            return
        }

        reduce { it.copy(selectedAspectRatio = option) }
        controller.setAspectRatio(option)
    }

    fun onFpsSelected(fps: Int) {
        reduce { it.copy(selectedFps = fps) }
        controller.setTargetFps(fps)
    }

    fun onWhiteBalanceSelected(preset: WhiteBalancePreset) {
        reduce { it.copy(selectedWhiteBalance = preset) }
        controller.setWhiteBalancePreset(preset)
    }

    fun onVideoStabilizationChanged(enabled: Boolean) {
        reduce { it.copy(videoStabilizationEnabled = enabled) }
        controller.setVideoStabilizationEnabled(enabled)
    }

    fun onExposureCompensationChanged(value: Int) {
        reduce { it.copy(exposureCompensationValue = value) }
        controller.setExposureCompensation(value)
    }

    fun onManualSensorEnabledChanged(enabled: Boolean) {
        reduce { it.copy(manualSensorEnabled = enabled) }
        controller.setManualSensorEnabled(enabled)
    }

    fun onIsoChanged(iso: Int?) {
        reduce { it.copy(selectedIso = iso) }
        controller.setManualIso(iso)
    }

    fun onExposureTimeChanged(exposureTimeNs: Long?) {
        reduce { it.copy(selectedExposureTimeNs = exposureTimeNs) }
        controller.setManualExposureTimeNs(exposureTimeNs)
    }

    fun onStressDurationChanged(seconds: Int) {
        reduce { it.copy(stressDurationSec = seconds.coerceIn(5, 120)) }
    }

    fun onHapticsIntensityChanged(intensity: HapticsIntensity) {
        reduce { it.copy(hapticsIntensity = intensity) }
    }

    fun onPrimaryActionClick() {
        val state = uiState.value
        when (state.mode) {
            CaptureMode.PHOTO -> controller.takePhoto()
            CaptureMode.VIDEO -> if (state.isRecording) controller.stopRecording() else {
                controller.startRecording(CaptureMode.VIDEO, state.stressDurationSec)
            }
            CaptureMode.STRESS -> if (state.isRecording) controller.stopRecording() else {
                controller.startRecording(CaptureMode.STRESS, state.stressDurationSec)
            }
        }
    }

    override fun onCameraReady(snapshot: CameraCapabilitiesSnapshot) {
        reduce {
            it.copy(
                cameraId = snapshot.cameraId,
                rawSizes = snapshot.rawSizes,
                selectedRawSize = snapshot.selectedRaw,
                photoResolutions = snapshot.photoResolutions,
                selectedPhotoResolution = snapshot.selectedPhotoResolution,
                videoResolutions = snapshot.videoResolutions,
                selectedVideoResolution = snapshot.selectedVideoResolution,
                aspectRatios = snapshot.aspectRatios,
                selectedAspectRatio = snapshot.selectedAspectRatio,
                whiteBalanceOptions = snapshot.whiteBalanceOptions,
                selectedWhiteBalance = snapshot.selectedWhiteBalance,
                exposureCompensationRange = snapshot.exposureCompensationRange,
                exposureCompensationStep = snapshot.exposureCompensationStep,
                exposureCompensationValue = snapshot.exposureCompensationValue,
                isoRange = snapshot.isoRange,
                selectedIso = snapshot.selectedIso,
                exposureTimeRangeNs = snapshot.exposureTimeRangeNs,
                selectedExposureTimeNs = snapshot.selectedExposureTimeNs,
                supportsManualSensor = snapshot.supportsManualSensor,
                manualSensorEnabled = snapshot.manualSensorEnabled,
                supportsVideoStabilization = snapshot.supportsVideoStabilization,
                videoStabilizationEnabled = snapshot.videoStabilizationEnabled,
                statusMessage = "Камера готова: ${snapshot.cameraId}",
            )
        }
    }

    override fun onStatus(message: String) {
        reduce { it.copy(statusMessage = message) }
    }

    override fun onRecordingStateChanged(isRecording: Boolean, sessionLabel: String?) {
        reduce {
            it.copy(
                isRecording = isRecording,
                sessionLabel = sessionLabel,
            )
        }
    }

    override fun onStats(stats: RecordingStats) {
        reduce { it.copy(stats = stats) }
    }

    override fun onLastSaved(uriString: String) {
        reduce { it.copy(lastSavedUri = uriString) }
    }

    override fun onPreviewDebug(info: String) {
        reduce { it.copy(previewDebugInfo = info) }
    }

    override fun onError(message: String, throwable: Throwable?) {
        val fullMessage = buildString {
            append(message)
            val details = throwable?.message
            if (!details.isNullOrBlank()) {
                append(": ")
                append(details)
            }
        }
        reduce { it.copy(statusMessage = fullMessage) }
    }

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }
}
