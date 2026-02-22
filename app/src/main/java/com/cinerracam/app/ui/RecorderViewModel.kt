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
    val statusMessage: String = "Need camera permission",
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
)

class RecorderViewModel(application: Application) : AndroidViewModel(application), RawCameraController.Listener {

    private val controller = RawCameraController(
        context = application.applicationContext,
        listener = this,
    )

    private val mutableState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = mutableState.asStateFlow()

    fun onCameraPermissionChanged(granted: Boolean) {
        mutableState.update {
            it.copy(
                hasCameraPermission = granted,
                statusMessage = if (granted) "Initializing camera..." else "Need camera permission",
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
            mutableState.update { it.copy(statusMessage = "Stop recording before mode switch") }
            return
        }

        mutableState.update { it.copy(mode = mode) }
        controller.onModeChanged(mode)
    }

    fun onRawSizeSelected(option: RawSizeOption) {
        if (uiState.value.isRecording) {
            mutableState.update { it.copy(statusMessage = "Stop recording before RAW size switch") }
            return
        }

        mutableState.update { it.copy(selectedRawSize = option) }
        controller.setRawSize(option)
    }

    fun onPhotoResolutionSelected(option: ResolutionOption) {
        if (uiState.value.isRecording) {
            mutableState.update { it.copy(statusMessage = "Stop recording before photo resolution switch") }
            return
        }

        mutableState.update { it.copy(selectedPhotoResolution = option) }
        controller.setPhotoResolution(option)
    }

    fun onVideoResolutionSelected(option: ResolutionOption) {
        if (uiState.value.isRecording) {
            mutableState.update { it.copy(statusMessage = "Stop recording before video resolution switch") }
            return
        }

        mutableState.update { it.copy(selectedVideoResolution = option) }
        controller.setVideoResolution(option)
    }

    fun onAspectRatioSelected(option: AspectRatioOption) {
        if (uiState.value.isRecording) {
            mutableState.update { it.copy(statusMessage = "Stop recording before format switch") }
            return
        }

        mutableState.update { it.copy(selectedAspectRatio = option) }
        controller.setAspectRatio(option)
    }

    fun onFpsSelected(fps: Int) {
        mutableState.update { it.copy(selectedFps = fps) }
        controller.setTargetFps(fps)
    }

    fun onWhiteBalanceSelected(preset: WhiteBalancePreset) {
        mutableState.update { it.copy(selectedWhiteBalance = preset) }
        controller.setWhiteBalancePreset(preset)
    }

    fun onVideoStabilizationChanged(enabled: Boolean) {
        mutableState.update { it.copy(videoStabilizationEnabled = enabled) }
        controller.setVideoStabilizationEnabled(enabled)
    }

    fun onExposureCompensationChanged(value: Int) {
        mutableState.update { it.copy(exposureCompensationValue = value) }
        controller.setExposureCompensation(value)
    }

    fun onManualSensorEnabledChanged(enabled: Boolean) {
        mutableState.update { it.copy(manualSensorEnabled = enabled) }
        controller.setManualSensorEnabled(enabled)
    }

    fun onIsoChanged(iso: Int?) {
        mutableState.update { it.copy(selectedIso = iso) }
        controller.setManualIso(iso)
    }

    fun onExposureTimeChanged(exposureTimeNs: Long?) {
        mutableState.update { it.copy(selectedExposureTimeNs = exposureTimeNs) }
        controller.setManualExposureTimeNs(exposureTimeNs)
    }

    fun onStressDurationChanged(seconds: Int) {
        mutableState.update { it.copy(stressDurationSec = seconds.coerceIn(5, 120)) }
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
        mutableState.update {
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
                statusMessage = "Camera ready: ${snapshot.cameraId}",
            )
        }
    }

    override fun onStatus(message: String) {
        mutableState.update { it.copy(statusMessage = message) }
    }

    override fun onRecordingStateChanged(isRecording: Boolean, sessionLabel: String?) {
        mutableState.update {
            it.copy(
                isRecording = isRecording,
                sessionLabel = sessionLabel,
            )
        }
    }

    override fun onStats(stats: RecordingStats) {
        mutableState.update { it.copy(stats = stats) }
    }

    override fun onLastSaved(uriString: String) {
        mutableState.update { it.copy(lastSavedUri = uriString) }
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
        mutableState.update { it.copy(statusMessage = fullMessage) }
    }

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }
}
