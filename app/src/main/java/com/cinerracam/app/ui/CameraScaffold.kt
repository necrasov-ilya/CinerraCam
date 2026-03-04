package com.cinerracam.app.ui

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cinerracam.app.camera.AfMode
import com.cinerracam.app.camera.AspectRatioOption
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.FlashMode
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.ResolutionOption
import com.cinerracam.app.camera.WhiteBalancePreset

@Composable
fun CameraScaffold(
    state: RecorderUiState,
    fullSettingsOpen: Boolean,
    activeQuickControl: QuickControl?,
    onPreviewTextureReady: (TextureView) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onCloseFullSettings: () -> Unit,
    onQuickControlTap: (QuickControl?) -> Unit,
    onModeSelected: (CaptureMode) -> Unit,
    onPrimaryActionClick: () -> Unit,
    onTouchToFocus: (Float, Float) -> Unit,
    onRawSizeSelected: (RawSizeOption) -> Unit,
    onPhotoResolutionSelected: (ResolutionOption) -> Unit,
    onVideoResolutionSelected: (ResolutionOption) -> Unit,
    onAspectRatioSelected: (AspectRatioOption) -> Unit,
    onFpsSelected: (Int) -> Unit,
    onWhiteBalanceSelected: (WhiteBalancePreset) -> Unit,
    onVideoStabilizationChanged: (Boolean) -> Unit,
    onExposureCompensationChanged: (Int) -> Unit,
    onManualSensorEnabledChanged: (Boolean) -> Unit,
    onIsoChanged: (Int?) -> Unit,
    onExposureTimeChanged: (Long?) -> Unit,
    onFlashModeChanged: (FlashMode) -> Unit,
    onAfModeChanged: (AfMode) -> Unit,
    onZoomRatioChanged: (Float) -> Unit,
    onGridToggle: () -> Unit,
    onStressDurationChanged: (Int) -> Unit,
    onHapticsIntensityChanged: (HapticsIntensity) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CameraTopBar(
                state = state,
                onRequestCameraPermission = onRequestCameraPermission,
                modifier = Modifier.fillMaxWidth(),
            )

            CameraPreviewPane(
                state = state,
                onPreviewTextureReady = onPreviewTextureReady,
                onTouchToFocus = onTouchToFocus,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            ProControlsStrip(
                state = state,
                activeControl = activeQuickControl,
                onControlTap = onQuickControlTap,
                onExposureCompensationChanged = onExposureCompensationChanged,
                onIsoChanged = onIsoChanged,
                onExposureTimeChanged = onExposureTimeChanged,
                onWhiteBalanceSelected = onWhiteBalanceSelected,
                onAfModeChanged = onAfModeChanged,
                onOpenFullSettings = onOpenFullSettings,
                modifier = Modifier.fillMaxWidth(),
            )

            BottomControlRail(
                state = state,
                onModeSelected = onModeSelected,
                onPrimaryActionClick = onPrimaryActionClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = fullSettingsOpen,
            enter = fadeIn(CameraMotionSpec.d220) + slideInVertically { full -> full / 4 },
            exit = fadeOut(CameraMotionSpec.d140) + slideOutVertically { full -> full / 4 },
        ) {
            FullSettingsSheet(
                state = state,
                onDismiss = onCloseFullSettings,
                onRawSizeSelected = onRawSizeSelected,
                onPhotoResolutionSelected = onPhotoResolutionSelected,
                onVideoResolutionSelected = onVideoResolutionSelected,
                onAspectRatioSelected = onAspectRatioSelected,
                onFpsSelected = onFpsSelected,
                onVideoStabilizationChanged = onVideoStabilizationChanged,
                onManualSensorEnabledChanged = onManualSensorEnabledChanged,
                onFlashModeChanged = onFlashModeChanged,
                onGridToggle = onGridToggle,
                onStressDurationChanged = onStressDurationChanged,
                onHapticsIntensityChanged = onHapticsIntensityChanged,
                onZoomRatioChanged = onZoomRatioChanged,
            )
        }
    }
}
