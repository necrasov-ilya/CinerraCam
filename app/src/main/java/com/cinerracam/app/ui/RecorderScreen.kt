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
import androidx.compose.material3.Switch
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
import com.cinerracam.app.camera.AspectRatioOption
import com.cinerracam.app.camera.CaptureMode
import com.cinerracam.app.camera.RawSizeOption
import com.cinerracam.app.camera.ResolutionOption
import com.cinerracam.app.camera.WhiteBalancePreset
import kotlin.math.roundToInt

private val AccentColor = Color(0xFFC92455)
private val SurfaceColor = Color(0xFF0A0A0A)
private val OverlayPanelColor = Color(0xCC141414)

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
            onPhotoResolutionSelected = viewModel::onPhotoResolutionSelected,
            onVideoResolutionSelected = viewModel::onVideoResolutionSelected,
            onAspectRatioSelected = viewModel::onAspectRatioSelected,
            onFpsSelected = viewModel::onFpsSelected,
            onWhiteBalanceSelected = viewModel::onWhiteBalanceSelected,
            onVideoStabilizationChanged = viewModel::onVideoStabilizationChanged,
            onExposureCompensationChanged = viewModel::onExposureCompensationChanged,
            onManualSensorEnabledChanged = viewModel::onManualSensorEnabledChanged,
            onIsoChanged = viewModel::onIsoChanged,
            onExposureTimeChanged = viewModel::onExposureTimeChanged,
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
    onPrimaryActionClick: () -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val headerHeight = 66.dp
        val footerHeight = 214.dp
        val viewfinderHeight = (maxHeight - headerHeight - footerHeight).coerceAtLeast(220.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            CameraHeader(
                state = state,
                onRequestCameraPermission = onRequestCameraPermission,
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
                onSettingsClick = { settingsVisible = true },
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
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CameraHeader(
    state: RecorderUiState,
    onRequestCameraPermission: () -> Unit,
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

        Box(
            modifier = Modifier
                .background(statusChip.background, RoundedCornerShape(100))
                .padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            Text(
                text = statusChip.label,
                color = statusChip.content,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Viewfinder(
    state: RecorderUiState,
    onPreviewTextureReady: (TextureView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
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
                text = "RAW ${state.selectedRawSize?.label ?: "-"} | ${state.selectedAspectRatio?.label ?: "-"}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Captured ${state.stats.capturedFrames}  Written ${state.stats.writtenFrames}  Drop ${state.stats.droppedFrames}",
                color = Color(0xFFD8D8D8),
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
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 12.dp),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(56.dp))

            ShutterButton(
                enabled = state.hasCameraPermission,
                isRecording = state.isRecording,
                label = actionLabel(state),
                onClick = onPrimaryActionClick,
            )

            ParamsButton(onClick = onSettingsClick)
        }
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
                    color = if (selected) Color.White else Color(0xFF7A7A7A),
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
                .size(94.dp)
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isRecording) 38.dp else 68.dp)
                    .background(
                        color = if (isRecording) AccentColor else Color.White,
                        shape = if (isRecording) RoundedCornerShape(11.dp) else CircleShape,
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
private fun ParamsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 42.dp)
            .border(1.dp, Color(0x88FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PARAM",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsOverlay(
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
                .align(Alignment.BottomEnd)
                .padding(start = 18.dp, end = 10.dp, bottom = 10.dp)
                .background(OverlayPanelColor)
                .border(1.dp, Color(0x55FFFFFF))
                .clickable(
                    interactionSource = swallowInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Capture settings",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            AspectSelector(
                options = state.aspectRatios,
                selected = state.selectedAspectRatio,
                onSelect = onAspectRatioSelected,
            )

            RawSizeSelector(
                options = state.rawSizes,
                selected = state.selectedRawSize,
                onSelect = onRawSizeSelected,
            )

            ResolutionSelector(
                title = "Photo",
                options = state.photoResolutions,
                selected = state.selectedPhotoResolution,
                onSelect = onPhotoResolutionSelected,
            )

            ResolutionSelector(
                title = "Video",
                options = state.videoResolutions,
                selected = state.selectedVideoResolution,
                onSelect = onVideoResolutionSelected,
            )

            Text("FPS", color = Color(0xFFD7D7D7), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fpsOptions.forEach { fps ->
                    SettingChip(
                        label = fps.toString(),
                        selected = state.selectedFps == fps,
                        onClick = { onFpsSelected(fps) },
                    )
                }
            }

            WhiteBalanceSelector(
                options = state.whiteBalanceOptions,
                selected = state.selectedWhiteBalance,
                onSelect = onWhiteBalanceSelected,
            )

            if (state.supportsVideoStabilization) {
                ToggleLine(
                    label = "Video stabilization",
                    checked = state.videoStabilizationEnabled,
                    onCheckedChange = onVideoStabilizationChanged,
                )
            }

            if (state.exposureCompensationRange.first != state.exposureCompensationRange.last) {
                Text(
                    text = "Exposure compensation: ${state.exposureCompensationValue}",
                    color = Color(0xFFD7D7D7),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.exposureCompensationValue.toFloat(),
                    onValueChange = { onExposureCompensationChanged(it.roundToInt()) },
                    valueRange = state.exposureCompensationRange.first.toFloat()..state.exposureCompensationRange.last.toFloat(),
                )
            }

            if (state.supportsManualSensor) {
                ToggleLine(
                    label = "Manual ISO/Exposure",
                    checked = state.manualSensorEnabled,
                    onCheckedChange = onManualSensorEnabledChanged,
                )

                if (state.manualSensorEnabled) {
                    state.isoRange?.let { range ->
                        val isoCurrent = state.selectedIso ?: range.first
                        Text(
                            text = "ISO: $isoCurrent",
                            color = Color(0xFFD7D7D7),
                            style = MaterialTheme.typography.bodySmall,
                        )
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

                        Text(
                            text = "Exposure: ${currentMs}ms",
                            color = Color(0xFFD7D7D7),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = currentMs.toFloat(),
                            onValueChange = { onExposureTimeChanged(it.roundToInt().toLong() * 1_000_000L) },
                            valueRange = minMs.toFloat()..maxMs.toFloat(),
                        )
                    }
                }
            }

            if (state.mode == CaptureMode.STRESS) {
                Text(
                    text = "Stress duration: ${state.stressDurationSec}s",
                    color = Color(0xFFD7D7D7),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.stressDurationSec.toFloat(),
                    onValueChange = { onStressDurationChanged(it.roundToInt()) },
                    valueRange = 5f..120f,
                )
            }

            Text(
                text = "Save: ${state.savePathLabel}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            state.lastSavedUri?.let { uri ->
                Text(
                    text = "Last: $uri",
                    color = Color(0xFFBEBEBE),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ToggleLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFFD7D7D7),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            .background(if (selected) AccentColor else Color.Transparent)
            .border(1.dp, if (selected) AccentColor else Color(0x66FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFDCDCDC),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AspectSelector(
    options: List<AspectRatioOption>,
    selected: AspectRatioOption?,
    onSelect: (AspectRatioOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Format", color = Color(0xFFD7D7D7), style = MaterialTheme.typography.bodyMedium)
        Box {
            SettingBox(text = selected?.label ?: "Select", onClick = { expanded = true }, enabled = options.isNotEmpty())
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

@Composable
private fun RawSizeSelector(
    options: List<RawSizeOption>,
    selected: RawSizeOption?,
    onSelect: (RawSizeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("RAW size", color = Color(0xFFD7D7D7), style = MaterialTheme.typography.bodyMedium)
        Box {
            SettingBox(text = selected?.label ?: "Select", onClick = { expanded = true }, enabled = options.isNotEmpty())
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.label} (${option.aspectLabel})") },
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

@Composable
private fun ResolutionSelector(
    title: String,
    options: List<ResolutionOption>,
    selected: ResolutionOption?,
    onSelect: (ResolutionOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("$title resolution", color = Color(0xFFD7D7D7), style = MaterialTheme.typography.bodyMedium)
        Box {
            SettingBox(
                text = selected?.let { "${it.label} (${it.aspectLabel})" } ?: "Select",
                onClick = { expanded = true },
                enabled = options.isNotEmpty(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.label} (${option.aspectLabel})") },
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

@Composable
private fun WhiteBalanceSelector(
    options: List<WhiteBalancePreset>,
    selected: WhiteBalancePreset,
    onSelect: (WhiteBalancePreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("White balance", color = Color(0xFFD7D7D7), style = MaterialTheme.typography.bodyMedium)
        Box {
            SettingBox(text = selected.label, onClick = { expanded = true }, enabled = options.isNotEmpty())
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

@Composable
private fun SettingBox(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .background(Color(0x22000000))
            .border(1.dp, Color(0x66FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color(0xFF808080),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private data class HeaderStatus(
    val label: String,
    val background: Color,
    val content: Color,
)

private fun buildHeaderStatus(state: RecorderUiState): HeaderStatus {
    if (!state.hasCameraPermission) {
        return HeaderStatus("NO PERMISSION", Color(0xFF333333), Color.White)
    }

    val lowered = state.statusMessage.lowercase()
    return when {
        lowered.contains("error") || lowered.contains("ошиб") -> HeaderStatus("ERROR", AccentColor, Color.White)
        state.isRecording -> HeaderStatus("REC", AccentColor, Color.White)
        lowered.contains("ready") || lowered.contains("готов") -> HeaderStatus("READY", Color(0xFF1C1C1C), Color.White)
        else -> HeaderStatus("INIT", Color(0xFF1C1C1C), Color(0xFFE0E0E0))
    }
}

private fun modeLabel(mode: CaptureMode): String {
    return when (mode) {
        CaptureMode.PHOTO -> "Photo"
        CaptureMode.VIDEO -> "Video RAW"
        CaptureMode.STRESS -> "Stress"
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
        return "No access"
    }

    return when (state.mode) {
        CaptureMode.PHOTO -> "Shutter"
        CaptureMode.VIDEO -> if (state.isRecording) "Stop" else "Record"
        CaptureMode.STRESS -> if (state.isRecording) "Stop test" else "Start test"
    }
}
