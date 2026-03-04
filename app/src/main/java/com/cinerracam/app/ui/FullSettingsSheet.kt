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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinerracam.app.camera.AspectRatioOption
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.FlashMode
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.ResolutionOption
import kotlin.math.roundToInt

@Composable
fun FullSettingsSheet(
    state: RecorderUiState,
    onDismiss: () -> Unit,
    onRawSizeSelected: (RawSizeOption) -> Unit,
    onPhotoResolutionSelected: (ResolutionOption) -> Unit,
    onVideoResolutionSelected: (ResolutionOption) -> Unit,
    onAspectRatioSelected: (AspectRatioOption) -> Unit,
    onFpsSelected: (Int) -> Unit,
    onVideoStabilizationChanged: (Boolean) -> Unit,
    onManualSensorEnabledChanged: (Boolean) -> Unit,
    onFlashModeChanged: (FlashMode) -> Unit,
    onGridToggle: () -> Unit,
    onStressDurationChanged: (Int) -> Unit,
    onHapticsIntensityChanged: (HapticsIntensity) -> Unit,
    onZoomRatioChanged: (Float) -> Unit,
) {
    val lockState = buildRecordingLockState(state.isRecording)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraColors.Background.copy(alpha = 0.92f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Настройки",
                    color = CameraColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "✕",
                    color = CameraColors.TextSecondary,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                )
            }

            SettingsSection(title = "Кадр") {
                SettingsHint("Формат определяет соотношение сторон и область захвата сенсора.")
                SettingsDropdown(
                    title = "Формат кадра",
                    value = state.selectedAspectRatio?.label ?: "-",
                    enabled = lockState.aspectRatio.enabled,
                    lockReason = lockState.aspectRatio.lockReason,
                    entries = state.aspectRatios.map { it.label to { onAspectRatioSelected(it) } },
                )
                SettingsHint("RAW — полный объём данных сенсора без сжатия для максимальной пост-обработки.")
                SettingsDropdown(
                    title = "RAW разрешение",
                    value = state.selectedRawSize?.let { "${it.label} (${it.aspectLabel})" } ?: "-",
                    enabled = lockState.resolution.enabled,
                    lockReason = lockState.resolution.lockReason,
                    entries = state.rawSizes.map { o -> "${o.label} (${o.aspectLabel})" to { onRawSizeSelected(o) } },
                )
                SettingsDropdown(
                    title = "Фото разрешение",
                    value = state.selectedPhotoResolution?.let { "${it.label} (${it.aspectLabel})" } ?: "-",
                    enabled = lockState.resolution.enabled,
                    lockReason = lockState.resolution.lockReason,
                    entries = state.photoResolutions.map { o -> "${o.label} (${o.aspectLabel})" to { onPhotoResolutionSelected(o) } },
                )
                SettingsDropdown(
                    title = "Видео разрешение",
                    value = state.selectedVideoResolution?.let { "${it.label} (${it.aspectLabel})" } ?: "-",
                    enabled = lockState.resolution.enabled,
                    lockReason = lockState.resolution.lockReason,
                    entries = state.videoResolutions.map { o -> "${o.label} (${o.aspectLabel})" to { onVideoResolutionSelected(o) } },
                )
            }

            SettingsSection(title = "Съёмка") {
                SettingsHint("FPS — целевая частота кадров для видеозаписи RAW.")
                Text("FPS", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.fpsOptions.forEach { fps ->
                        SettingsToggleChip(label = fps.toString(), selected = state.selectedFps == fps, onClick = { onFpsSelected(fps) })
                    }
                }

                if (state.hasFlashUnit) {
                    SettingsHint("Вспышка работает в связке с AE. В ручном режиме используется TORCH.")
                    Text("Вспышка", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableFlashModes.forEach { mode ->
                            SettingsToggleChip(label = mode.label, selected = state.selectedFlashMode == mode, onClick = { onFlashModeChanged(mode) })
                        }
                    }
                }

                if (state.supportsVideoStabilization) {
                    SettingsHint("Электронная стабилизация кропает по краям, OIS работает оптически.")
                    SettingsSwitch(label = "Стабилизация видео", checked = state.videoStabilizationEnabled, onCheckedChange = onVideoStabilizationChanged)
                }

                if (state.supportsManualSensor) {
                    SettingsHint("Включает ручное управление ISO и выдержкой. AE отключается.")
                    SettingsSwitch(
                        label = "Ручной ISO / Выдержка",
                        checked = state.manualSensorEnabled,
                        onCheckedChange = onManualSensorEnabledChanged,
                        enabled = lockState.manualSensor.enabled,
                        lockReason = lockState.manualSensor.lockReason,
                    )
                }

                if (state.maxZoomRatio > 1f) {
                    SettingsHint("Цифровой зум кропает область сенсора. Качество снижается с увеличением.")
                    Text(
                        "Зум: ${"%.1f".format(state.zoomRatio)}x",
                        color = CameraColors.TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = state.zoomRatio,
                        onValueChange = onZoomRatioChanged,
                        valueRange = 1f..state.maxZoomRatio,
                    )
                }
            }

            SettingsSection(title = "Интерфейс") {
                SettingsSwitch(label = "Сетка 3×3", checked = state.showGrid, onCheckedChange = { onGridToggle() })
                SettingsHint("Вибрация имитирует тактильное ощущение затвора и переключений камеры.")
                Text("Вибрация", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticsIntensity.entries.forEach { option ->
                        SettingsToggleChip(label = option.name, selected = state.hapticsIntensity == option, onClick = { onHapticsIntensityChanged(option) })
                    }
                }
            }

            if (state.mode == CaptureMode.STRESS) {
                SettingsSection(title = "Стресс-тест") {
                    SettingsHint("Автоматический тест записи с заданной длительностью. Проверяет стабильность пайплайна.")
                    Text("Длительность: ${state.stressDurationSec} сек", color = CameraColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                    Slider(value = state.stressDurationSec.toFloat(), onValueChange = { onStressDurationChanged(it.roundToInt()) }, valueRange = 5f..120f)
                }
            }

            SettingsSection(title = "Информация") {
                Text("Сохранение: ${state.savePathLabel}", color = CameraColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                state.cameraId?.let { Text("Камера: $it", color = CameraColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                state.lastSavedUri?.let { Text("Последний файл: $it", color = CameraColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CameraColors.SurfaceCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = CameraColors.Accent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        color = CameraColors.TextMuted,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    lockReason: String? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (enabled) CameraColors.TextPrimary else CameraColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CameraColors.Accent,
                    checkedTrackColor = CameraColors.Accent.copy(alpha = 0.3f),
                ),
            )
        }
        if (!enabled && !lockReason.isNullOrBlank()) {
            Text(lockReason, color = CameraColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
