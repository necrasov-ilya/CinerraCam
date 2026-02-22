package com.cinerracam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinerracam.app.camera.WhiteBalancePreset
import kotlin.math.roundToInt

@Composable
fun QuickSettingsDrawer(
    state: RecorderUiState,
    onDismiss: () -> Unit,
    onOpenProSettings: () -> Unit,
    onFpsSelected: (Int) -> Unit,
    onWhiteBalanceSelected: (WhiteBalancePreset) -> Unit,
    onVideoStabilizationChanged: (Boolean) -> Unit,
    onExposureCompensationChanged: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraColors.SurfaceOverlay)
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp)
                .background(CameraColors.SurfaceElevated)
                .border(1.dp, CameraColors.Border)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Быстрые параметры",
                color = CameraColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = if (state.isRecording) {
                    "REC: доступны WB, EV, стабилизация и FPS"
                } else {
                    "До REC доступны все параметры"
                },
                color = CameraColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "FPS",
                color = CameraColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fpsOptions.forEach { fps ->
                    SettingChip(
                        label = fps.toString(),
                        selected = state.selectedFps == fps,
                        onClick = { onFpsSelected(fps) },
                    )
                }
            }

            Text(
                text = "Баланс белого",
                color = CameraColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.whiteBalanceOptions.take(4).forEach { option ->
                    SettingChip(
                        label = option.label,
                        selected = state.selectedWhiteBalance == option,
                        onClick = { onWhiteBalanceSelected(option) },
                    )
                }
            }
            if (state.whiteBalanceOptions.size > 4) {
                Text(
                    text = "Остальные WB доступны в Pro",
                    color = CameraColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (state.supportsVideoStabilization) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Стабилизация",
                        color = CameraColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = state.videoStabilizationEnabled,
                        onCheckedChange = onVideoStabilizationChanged,
                    )
                }
            }

            if (state.exposureCompensationRange.first != state.exposureCompensationRange.last) {
                Text(
                    text = "EV: ${state.exposureCompensationValue}",
                    color = CameraColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.exposureCompensationValue.toFloat(),
                    onValueChange = { onExposureCompensationChanged(it.roundToInt()) },
                    valueRange = state.exposureCompensationRange.first.toFloat()..state.exposureCompensationRange.last.toFloat(),
                )
            }

            Text(
                text = "Сохранение: ${state.savePathLabel}",
                color = CameraColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CameraColors.BorderStrong)
                    .clickable(onClick = onOpenProSettings)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Открыть Pro настройки",
                    color = CameraColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (selected) CameraColors.Accent else CameraColors.Surface)
            .border(1.dp, if (selected) CameraColors.Accent else CameraColors.BorderStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
