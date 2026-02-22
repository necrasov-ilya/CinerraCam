package com.cinerracam.app.ui

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cinerracam.app.camera.AspectRatioOption
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.ResolutionOption
import com.cinerracam.app.camera.WhiteBalancePreset

@Composable
fun CameraScaffold(
    state: RecorderUiState,
    quickSettingsOpen: Boolean,
    proSettingsOpen: Boolean,
    onPreviewTextureReady: (TextureView) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenQuickSettings: () -> Unit,
    onCloseQuickSettings: () -> Unit,
    onOpenProSettings: () -> Unit,
    onCloseProSettings: () -> Unit,
    onModeSelected: (CaptureMode) -> Unit,
    onPrimaryActionClick: () -> Unit,
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
                onOpenProSettings = onOpenProSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
            )

            CameraPreviewPane(
                state = state,
                onPreviewTextureReady = onPreviewTextureReady,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            BottomControlRail(
                state = state,
                onModeSelected = onModeSelected,
                onPrimaryActionClick = onPrimaryActionClick,
                onOpenQuickSettings = onOpenQuickSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp),
            )
        }

        AnimatedVisibility(
            visible = quickSettingsOpen,
            enter = fadeIn(CameraMotionSpec.d140) + slideInHorizontally { full -> full },
            exit = fadeOut(CameraMotionSpec.d140) + slideOutHorizontally { full -> full },
        ) {
            QuickSettingsDrawer(
                state = state,
                onDismiss = onCloseQuickSettings,
                onOpenProSettings = onOpenProSettings,
                onFpsSelected = onFpsSelected,
                onWhiteBalanceSelected = onWhiteBalanceSelected,
                onVideoStabilizationChanged = onVideoStabilizationChanged,
                onExposureCompensationChanged = onExposureCompensationChanged,
            )
        }

        AnimatedVisibility(
            visible = proSettingsOpen,
            enter = fadeIn(CameraMotionSpec.d220) + slideInVertically { full -> full / 3 },
            exit = fadeOut(CameraMotionSpec.d140) + slideOutVertically { full -> full / 3 },
        ) {
            ProSettingsSheet(
                state = state,
                onDismiss = onCloseProSettings,
                onRawSizeSelected = onRawSizeSelected,
                onPhotoResolutionSelected = onPhotoResolutionSelected,
                onVideoResolutionSelected = onVideoResolutionSelected,
                onAspectRatioSelected = onAspectRatioSelected,
                onFpsSelected = onFpsSelected,
                onWhiteBalanceSelected = onWhiteBalanceSelected,
                onVideoStabilizationChanged = onVideoStabilizationChanged,
                onExposureCompensationChanged = onExposureCompensationChanged,
                onManualSensorEnabledChanged = onManualSensorEnabledChanged,
                onIsoChanged = onIsoChanged,
                onExposureTimeChanged = onExposureTimeChanged,
                onStressDurationChanged = onStressDurationChanged,
                onHapticsIntensityChanged = onHapticsIntensityChanged,
            )
        }
    }
}
