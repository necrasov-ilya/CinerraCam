package com.cinerracam.app.ui

import android.app.Application
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.RawCameraController
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.RecordingStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecorderUiState(
    val hasCameraPermission: Boolean = false,
    val cameraId: String? = null,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val statusMessage: String = "Нужен доступ к камере",
    val rawSizes: List<RawSizeOption> = emptyList(),
    val selectedRawSize: RawSizeOption? = null,
    val fpsOptions: List<Int> = listOf(12, 24, 30),
    val selectedFps: Int = 24,
    val stressDurationSec: Int = 20,
    val isRecording: Boolean = false,
    val sessionLabel: String? = null,
    val savePathLabel: String = "DCIM/CinerraCam",
    val stats: RecordingStats = RecordingStats(),
    val lastSavedUri: String? = null,
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
                statusMessage = if (granted) "Камера инициализируется..." else "Нужен доступ к камере",
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
            mutableState.update { it.copy(statusMessage = "Остановите запись перед сменой режима") }
            return
        }

        mutableState.update { it.copy(mode = mode) }
    }

    fun onRawSizeSelected(option: RawSizeOption) {
        if (uiState.value.isRecording) {
            mutableState.update { it.copy(statusMessage = "Остановите запись перед сменой RAW размера") }
            return
        }

        mutableState.update { it.copy(selectedRawSize = option) }
        controller.setRawSize(option)
    }

    fun onFpsSelected(fps: Int) {
        mutableState.update { it.copy(selectedFps = fps) }
        controller.setTargetFps(fps)
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

    override fun onCameraReady(cameraId: String, rawSizes: List<RawSizeOption>, selected: RawSizeOption) {
        mutableState.update {
            it.copy(
                cameraId = cameraId,
                rawSizes = rawSizes,
                selectedRawSize = selected,
                statusMessage = "Камера готова: $cameraId",
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
