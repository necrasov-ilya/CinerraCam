package com.cinerracam.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinerracam.app.camera.CaptureMode

@Composable
fun CinerraCamApp(viewModel: RecorderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticEngine = rememberCameraHapticEngine()

    var fullSettingsOpen by remember { mutableStateOf(false) }
    var activeQuickControl by remember { mutableStateOf<QuickControl?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onCameraPermissionChanged,
    )

    LaunchedEffect(Unit) {
        if (!state.hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    CameraScaffold(
        state = state,
        fullSettingsOpen = fullSettingsOpen,
        activeQuickControl = activeQuickControl,
        onPreviewTextureReady = viewModel::onPreviewTextureReady,
        onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenFullSettings = {
            activeQuickControl = null
            fullSettingsOpen = true
        },
        onCloseFullSettings = { fullSettingsOpen = false },
        onQuickControlTap = { control ->
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            activeQuickControl = control
        },
        onModeSelected = { mode ->
            hapticEngine.perform(CameraHapticEvent.MODE_SWITCH, state.hapticsIntensity)
            activeQuickControl = null
            viewModel.onModeSelected(mode)
        },
        onPrimaryActionClick = {
            activeQuickControl = null
            val event = when {
                state.mode == CaptureMode.PHOTO -> CameraHapticEvent.SHUTTER_TAP
                state.isRecording -> CameraHapticEvent.RECORD_STOP
                else -> CameraHapticEvent.RECORD_START
            }
            hapticEngine.perform(event, state.hapticsIntensity)
            viewModel.onPrimaryActionClick()
        },
        onTouchToFocus = { normX, normY ->
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            viewModel.onTouchToFocus(normX, normY)
        },
        onRawSizeSelected = { option ->
            hapticEngine.perform(
                if (state.isRecording) CameraHapticEvent.LOCK_WARNING else CameraHapticEvent.PARAMETER_APPLY,
                state.hapticsIntensity,
            )
            viewModel.onRawSizeSelected(option)
        },
        onPhotoResolutionSelected = { option ->
            hapticEngine.perform(
                if (state.isRecording) CameraHapticEvent.LOCK_WARNING else CameraHapticEvent.PARAMETER_APPLY,
                state.hapticsIntensity,
            )
            viewModel.onPhotoResolutionSelected(option)
        },
        onVideoResolutionSelected = { option ->
            hapticEngine.perform(
                if (state.isRecording) CameraHapticEvent.LOCK_WARNING else CameraHapticEvent.PARAMETER_APPLY,
                state.hapticsIntensity,
            )
            viewModel.onVideoResolutionSelected(option)
        },
        onAspectRatioSelected = { option ->
            hapticEngine.perform(
                if (state.isRecording) CameraHapticEvent.LOCK_WARNING else CameraHapticEvent.PARAMETER_APPLY,
                state.hapticsIntensity,
            )
            viewModel.onAspectRatioSelected(option)
        },
        onFpsSelected = {
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            viewModel.onFpsSelected(it)
        },
        onWhiteBalanceSelected = {
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            viewModel.onWhiteBalanceSelected(it)
        },
        onVideoStabilizationChanged = {
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            viewModel.onVideoStabilizationChanged(it)
        },
        onExposureCompensationChanged = viewModel::onExposureCompensationChanged,
        onManualSensorEnabledChanged = viewModel::onManualSensorEnabledChanged,
        onIsoChanged = viewModel::onIsoChanged,
        onExposureTimeChanged = viewModel::onExposureTimeChanged,
        onFlashModeChanged = {
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            viewModel.onFlashModeChanged(it)
        },
        onAfModeChanged = {
            hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            viewModel.onAfModeChanged(it)
        },
        onZoomRatioChanged = viewModel::onZoomRatioChanged,
        onGridToggle = viewModel::onGridToggle,
        onStressDurationChanged = viewModel::onStressDurationChanged,
        onHapticsIntensityChanged = viewModel::onHapticsIntensityChanged,
    )
}
