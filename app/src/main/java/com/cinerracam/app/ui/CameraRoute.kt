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

    var quickSettingsOpen by remember { mutableStateOf(false) }
    var proSettingsOpen by remember { mutableStateOf(false) }

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
        quickSettingsOpen = quickSettingsOpen,
        proSettingsOpen = proSettingsOpen,
        onPreviewTextureReady = viewModel::onPreviewTextureReady,
        onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenQuickSettings = { quickSettingsOpen = true },
        onCloseQuickSettings = { quickSettingsOpen = false },
        onOpenProSettings = { proSettingsOpen = true },
        onCloseProSettings = { proSettingsOpen = false },
        onModeSelected = { mode ->
            hapticEngine.perform(CameraHapticEvent.MODE_SWITCH, state.hapticsIntensity)
            viewModel.onModeSelected(mode)
        },
        onPrimaryActionClick = {
            val event = when {
                state.mode == CaptureMode.PHOTO -> CameraHapticEvent.SHUTTER_TAP
                state.isRecording -> CameraHapticEvent.RECORD_STOP
                else -> CameraHapticEvent.RECORD_START
            }
            hapticEngine.perform(event, state.hapticsIntensity)
            viewModel.onPrimaryActionClick()
        },
        onRawSizeSelected = { option ->
            if (state.isRecording) {
                hapticEngine.perform(CameraHapticEvent.LOCK_WARNING, state.hapticsIntensity)
            } else {
                hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            }
            viewModel.onRawSizeSelected(option)
        },
        onPhotoResolutionSelected = { option ->
            if (state.isRecording) {
                hapticEngine.perform(CameraHapticEvent.LOCK_WARNING, state.hapticsIntensity)
            } else {
                hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            }
            viewModel.onPhotoResolutionSelected(option)
        },
        onVideoResolutionSelected = { option ->
            if (state.isRecording) {
                hapticEngine.perform(CameraHapticEvent.LOCK_WARNING, state.hapticsIntensity)
            } else {
                hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            }
            viewModel.onVideoResolutionSelected(option)
        },
        onAspectRatioSelected = { option ->
            if (state.isRecording) {
                hapticEngine.perform(CameraHapticEvent.LOCK_WARNING, state.hapticsIntensity)
            } else {
                hapticEngine.perform(CameraHapticEvent.PARAMETER_APPLY, state.hapticsIntensity)
            }
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
        onStressDurationChanged = viewModel::onStressDurationChanged,
        onHapticsIntensityChanged = viewModel::onHapticsIntensityChanged,
    )
}
