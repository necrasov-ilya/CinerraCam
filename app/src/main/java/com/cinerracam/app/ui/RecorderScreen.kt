package com.cinerracam.app.ui

import android.Manifest
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.RawSizeOption

private val AccentColor = Color(0xFFC92455)
private val SurfaceColor = Color(0xFF0A0A0A)
private val OverlayPanelColor = Color(0xCC111111)

@Composable
fun CinerraCamApp(viewModel: RecorderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onCameraPermissionChanged,
    )

    LaunchedEffect(Unit) {
        if (!state.hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    MaterialTheme {
        RecorderScreen(
            state = state,
            onPreviewTextureReady = viewModel::onPreviewTextureReady,
            onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onModeSelected = viewModel::onModeSelected,
            onRawSizeSelected = viewModel::onRawSizeSelected,
            onFpsSelected = viewModel::onFpsSelected,
            onStressDurationChanged = viewModel::onStressDurationChanged,
            onPrimaryActionClick = viewModel::onPrimaryActionClick,
        )
    }
}

@Composable
private fun RecorderScreen(
    state: RecorderUiState,
    onPreviewTextureReady: (TextureView) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onModeSelected: (CaptureMode) -> Unit,
    onRawSizeSelected: (RawSizeOption) -> Unit,
    onFpsSelected: (Int) -> Unit,
    onStressDurationChanged: (Int) -> Unit,
    onPrimaryActionClick: () -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val headerHeight = 66.dp
        val footerHeight = 188.dp
        val viewfinderHeight = (maxHeight - headerHeight - footerHeight).coerceAtLeast(220.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            CameraHeader(
                state = state,
                onRequestCameraPermission = onRequestCameraPermission,
                onSettingsClick = { settingsVisible = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
            )

            Viewfinder(
                state = state,
                onPreviewTextureReady = onPreviewTextureReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewfinderHeight),
            )

            CameraFooter(
                state = state,
                onModeSelected = onModeSelected,
                onPrimaryActionClick = onPrimaryActionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight),
            )
        }

        if (settingsVisible) {
            SettingsOverlay(
                state = state,
                onDismiss = { settingsVisible = false },
                onRawSizeSelected = onRawSizeSelected,
                onFpsSelected = onFpsSelected,
                onStressDurationChanged = onStressDurationChanged,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CameraHeader(
    state: RecorderUiState,
    onRequestCameraPermission: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusChip = buildHeaderStatus(state)

    Row(
        modifier = modifier
            .background(SurfaceColor)
            .clickable(enabled = !state.hasCameraPermission, onClick = onRequestCameraPermission)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "CinerraCam",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(statusChip.background, RoundedCornerShape(100))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = statusChip.label,
                    color = statusChip.content,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .border(1.dp, Color(0x66FFFFFF))
                    .clickable(onClick = onSettingsClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "SET",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

}

@Composable
private fun Viewfinder(
    state: RecorderUiState,
    onPreviewTextureReady: (TextureView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: android.graphics.SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            onPreviewTextureReady(this@apply)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: android.graphics.SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            onPreviewTextureReady(this@apply)
                        }

                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true

                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                    }

                    if (isAvailable) {
                        onPreviewTextureReady(this)
                    }
                }
            },
            update = { _ -> },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0x70000000))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "RAW ${state.selectedRawSize?.label ?: "-"} @ ${state.selectedFps}fps",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Captured ${state.stats.capturedFrames} | Written ${state.stats.writtenFrames} | Drop ${state.stats.droppedFrames}",
                color = Color(0xFFDDDDDD),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CameraFooter(
    state: RecorderUiState,
    onModeSelected: (CaptureMode) -> Unit,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SwipeModeSelector(
            selectedMode = state.mode,
            onModeSelected = onModeSelected,
        )

        Text(
            text = state.statusMessage,
            color = Color(0xFFCFCFCF),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )

        ShutterButton(
            enabled = state.hasCameraPermission,
            isRecording = state.isRecording,
            label = actionLabel(state),
            onClick = onPrimaryActionClick,
        )
    }
}

@Composable
private fun SwipeModeSelector(
    selectedMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
) {
    val modes = remember { listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.STRESS) }
    val density = LocalDensity.current
    val switchThresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(selectedMode) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount

                        if (dragAccumulator <= -switchThresholdPx) {
                            dragAccumulator = 0f
                            onModeSelected(nextMode(selectedMode, modes))
                        } else if (dragAccumulator >= switchThresholdPx) {
                            dragAccumulator = 0f
                            onModeSelected(previousMode(selectedMode, modes))
                        }
                    },
                    onDragEnd = { dragAccumulator = 0f },
                    onDragCancel = { dragAccumulator = 0f },
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { mode ->
            val selected = mode == selectedMode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .clickable { onModeSelected(mode) },
            ) {
                Text(
                    text = modeLabel(mode),
                    color = if (selected) Color.White else Color(0xFF7B7B7B),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )

                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(42.dp)
                        .height(2.dp)
                        .background(if (selected) AccentColor else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    isRecording: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isRecording) 36.dp else 66.dp)
                    .background(
                        color = if (isRecording) AccentColor else Color.White,
                        shape = if (isRecording) RoundedCornerShape(10.dp) else CircleShape,
                    ),
            )
        }

        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsOverlay(
    state: RecorderUiState,
    onDismiss: () -> Unit,
    onRawSizeSelected: (RawSizeOption) -> Unit,
    onFpsSelected: (Int) -> Unit,
    onStressDurationChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swallowInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 12.dp, start = 22.dp)
                .background(OverlayPanelColor)
                .border(1.dp, Color(0x60FFFFFF))
                .clickable(
                    interactionSource = swallowInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Настройки",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            RawSizeSelector(
                options = state.rawSizes,
                selected = state.selectedRawSize,
                onSelect = onRawSizeSelected,
            )

            Text(
                text = "FPS",
                color = Color(0xFFD7D7D7),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fpsOptions.forEach { fps ->
                    SettingToggleButton(
                        label = fps.toString(),
                        selected = state.selectedFps == fps,
                        onClick = { onFpsSelected(fps) },
                    )
                }
            }

            if (state.mode == CaptureMode.STRESS) {
                Text(
                    text = "Длительность теста: ${state.stressDurationSec} сек",
                    color = Color(0xFFD7D7D7),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.stressDurationSec.toFloat(),
                    onValueChange = { onStressDurationChanged(it.toInt()) },
                    valueRange = 5f..120f,
                )
            }

            Text(
                text = "Сохранение: ${state.savePathLabel}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )

            state.sessionLabel?.let { session ->
                Text(
                    text = "Сессия: $session",
                    color = Color(0xFFCFCFCF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.lastSavedUri?.let { uri ->
                Text(
                    text = "Последний файл: $uri",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SettingToggleButton(
                    label = "Закрыть",
                    selected = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SettingToggleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (selected) AccentColor else Color.Transparent)
            .border(1.dp, if (selected) AccentColor else Color(0x66FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFDDDDDD),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun RawSizeSelector(
    options: List<RawSizeOption>,
    selected: RawSizeOption?,
    onSelect: (RawSizeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "RAW",
            color = Color(0xFFD7D7D7),
            style = MaterialTheme.typography.bodyMedium,
        )

        Box {
            Box(
                modifier = Modifier
                    .background(Color(0x22000000))
                    .border(1.dp, Color(0x66FFFFFF))
                    .clickable(enabled = options.isNotEmpty()) { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = selected?.label ?: "Выбрать",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

private data class HeaderStatus(
    val label: String,
    val background: Color,
    val content: Color,
)

private fun buildHeaderStatus(state: RecorderUiState): HeaderStatus {
    if (!state.hasCameraPermission) {
        return HeaderStatus(
            label = "Нет доступа",
            background = Color(0xFF2A2A2A),
            content = Color.White,
        )
    }

    val lowered = state.statusMessage.lowercase()
    return when {
        lowered.contains("ошиб") || lowered.contains("error") -> HeaderStatus(
            label = "Ошибка",
            background = AccentColor,
            content = Color.White,
        )

        state.isRecording -> HeaderStatus(
            label = "REC",
            background = AccentColor,
            content = Color.White,
        )

        lowered.contains("готов") -> HeaderStatus(
            label = "Готово",
            background = Color(0xFF1D1D1D),
            content = Color.White,
        )

        else -> HeaderStatus(
            label = "Подготовка",
            background = Color(0xFF1D1D1D),
            content = Color(0xFFE2E2E2),
        )
    }
}

private fun modeLabel(mode: CaptureMode): String {
    return when (mode) {
        CaptureMode.PHOTO -> "Фото"
        CaptureMode.VIDEO -> "Видео RAW"
        CaptureMode.STRESS -> "Тест"
    }
}

private fun nextMode(current: CaptureMode, modes: List<CaptureMode>): CaptureMode {
    val index = modes.indexOf(current).takeIf { it >= 0 } ?: return current
    return modes[(index + 1).coerceAtMost(modes.lastIndex)]
}

private fun previousMode(current: CaptureMode, modes: List<CaptureMode>): CaptureMode {
    val index = modes.indexOf(current).takeIf { it >= 0 } ?: return current
    return modes[(index - 1).coerceAtLeast(0)]
}

private fun actionLabel(state: RecorderUiState): String {
    if (!state.hasCameraPermission) {
        return "Нет доступа"
    }

    return when (state.mode) {
        CaptureMode.PHOTO -> "Спуск"
        CaptureMode.VIDEO -> if (state.isRecording) "Стоп" else "Запись"
        CaptureMode.STRESS -> if (state.isRecording) "Стоп тест" else "Старт тест"
    }
}
