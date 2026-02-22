package com.cinerracam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinerracam.app.camera.AspectRatioOption
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.ResolutionOption
import com.cinerracam.app.camera.WhiteBalancePreset
import kotlin.math.roundToInt

@Composable
fun ProSettingsSheet(
    state: RecorderUiState,
    onDismiss: () -> Unit,
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
    val lockState = buildRecordingLockState(state.isRecording)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraColors.SurfaceOverlay)
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CameraColors.Surface)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pro настройки",
                    color = CameraColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Закрыть",
                    color = CameraColors.Accent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            SettingsDropdown(
                title = "Формат кадра",
                value = state.selectedAspectRatio?.label ?: "-",
                enabled = lockState.aspectRatio.enabled,
                lockReason = lockState.aspectRatio.lockReason,
                entries = state.aspectRatios.map { it.label to { onAspectRatioSelected(it) } },
            )

            SettingsDropdown(
                title = "RAW разрешение",
                value = state.selectedRawSize?.let { "${it.label} (${it.aspectLabel})" } ?: "-",
                enabled = lockState.resolution.enabled,
                lockReason = lockState.resolution.lockReason,
                entries = state.rawSizes.map { option ->
                    "${option.label} (${option.aspectLabel})" to { onRawSizeSelected(option) }
                },
            )

            SettingsDropdown(
                title = "Фото разрешение",
                value = state.selectedPhotoResolution?.let { "${it.label} (${it.aspectLabel})" } ?: "-",
                enabled = lockState.resolution.enabled,
                lockReason = lockState.resolution.lockReason,
                entries = state.photoResolutions.map { option ->
                    "${option.label} (${option.aspectLabel})" to { onPhotoResolutionSelected(option) }
                },
            )

            SettingsDropdown(
                title = "Видео разрешение",
                value = state.selectedVideoResolution?.let { "${it.label} (${it.aspectLabel})" } ?: "-",
                enabled = lockState.resolution.enabled,
                lockReason = lockState.resolution.lockReason,
                entries = state.videoResolutions.map { option ->
                    "${option.label} (${option.aspectLabel})" to { onVideoResolutionSelected(option) }
                },
            )

            Text("FPS", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fpsOptions.forEach { fps ->
                    SettingsToggleChip(
                        label = fps.toString(),
                        selected = state.selectedFps == fps,
                        onClick = { onFpsSelected(fps) },
                    )
                }
            }

            SettingsDropdown(
                title = "Баланс белого",
                value = state.selectedWhiteBalance.label,
                enabled = true,
                lockReason = null,
                entries = state.whiteBalanceOptions.map { option ->
                    option.label to { onWhiteBalanceSelected(option) }
                },
            )

            if (state.supportsVideoStabilization) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Стабилизация", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.videoStabilizationEnabled,
                        onCheckedChange = onVideoStabilizationChanged,
                    )
                }
            }

            if (state.exposureCompensationRange.first != state.exposureCompensationRange.last) {
                Text("EV: ${state.exposureCompensationValue}", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.exposureCompensationValue.toFloat(),
                    onValueChange = { onExposureCompensationChanged(it.roundToInt()) },
                    valueRange = state.exposureCompensationRange.first.toFloat()..state.exposureCompensationRange.last.toFloat(),
                )
            }

            if (state.supportsManualSensor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Manual ISO/Выдержка", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        if (!lockState.manualSensor.enabled) {
                            Text(
                                text = lockState.manualSensor.lockReason.orEmpty(),
                                color = CameraColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Switch(
                        checked = state.manualSensorEnabled,
                        onCheckedChange = onManualSensorEnabledChanged,
                        enabled = lockState.manualSensor.enabled,
                    )
                }

                if (state.manualSensorEnabled && lockState.manualSensor.enabled) {
                    state.isoRange?.let { range ->
                        val isoCurrent = state.selectedIso ?: range.first
                        Text("ISO: $isoCurrent", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = isoCurrent.toFloat(),
                            onValueChange = { onIsoChanged(it.roundToInt()) },
                            valueRange = range.first.toFloat()..range.last.toFloat(),
                        )
                    }

                    state.exposureTimeRangeNs?.let { range ->
                        val minMs = (range.first / 1_000_000L).coerceAtLeast(1L)
                        val maxMs = (range.last / 1_000_000L).coerceAtLeast(minMs + 1L)
                        val currentNs = state.selectedExposureTimeNs ?: range.first
                        val currentMs = (currentNs / 1_000_000L).coerceIn(minMs, maxMs)

                        Text("Выдержка: ${currentMs}ms", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = currentMs.toFloat(),
                            onValueChange = { onExposureTimeChanged(it.roundToInt().toLong() * 1_000_000L) },
                            valueRange = minMs.toFloat()..maxMs.toFloat(),
                        )
                    }
                }
            }

            if (state.mode == CaptureMode.STRESS) {
                Text("Длительность теста: ${state.stressDurationSec} сек", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.stressDurationSec.toFloat(),
                    onValueChange = { onStressDurationChanged(it.roundToInt()) },
                    valueRange = 5f..120f,
                )
            }

            Text("Интенсивность вибрации", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HapticsIntensity.entries.forEach { option ->
                    SettingsToggleChip(
                        label = option.name,
                        selected = state.hapticsIntensity == option,
                        onClick = { onHapticsIntensityChanged(option) },
                    )
                }
            }

            Text("Сохранение: ${state.savePathLabel}", color = CameraColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            state.lastSavedUri?.let { uri ->
                Text("Последний файл: $uri", color = CameraColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
