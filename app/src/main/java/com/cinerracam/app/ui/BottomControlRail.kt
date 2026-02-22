package com.cinerracam.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cinerracam.app.camera.CaptureMode

@Composable
fun BottomControlRail(
    state: RecorderUiState,
    onModeSelected: (CaptureMode) -> Unit,
    onPrimaryActionClick: () -> Unit,
    onOpenQuickSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CameraColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BottomModeStrip(
            selectedMode = state.mode,
            onModeSelected = onModeSelected,
        )

        Text(
            text = state.statusMessage,
            color = CameraColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(54.dp))

            ShutterCluster(
                state = state,
                onClick = onPrimaryActionClick,
            )

            ParamButton(onClick = onOpenQuickSettings)
        }
    }
}

@Composable
private fun BottomModeStrip(
    selectedMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
) {
    val modes = remember { listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.STRESS) }
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val indicatorOffset by animateIntOffsetAsState(
        targetValue = IntOffset(selectedIndex * 96, 0),
        label = "mode-strip-indicator",
    )

    Box(
        modifier = Modifier
            .width(288.dp)
            .pointerInput(selectedMode) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        if (dragAccumulator <= -thresholdPx) {
                            dragAccumulator = 0f
                            val next = (selectedIndex + 1).coerceAtMost(modes.lastIndex)
                            onModeSelected(modes[next])
                        } else if (dragAccumulator >= thresholdPx) {
                            dragAccumulator = 0f
                            val previous = (selectedIndex - 1).coerceAtLeast(0)
                            onModeSelected(modes[previous])
                        }
                    },
                    onDragEnd = { dragAccumulator = 0f },
                    onDragCancel = { dragAccumulator = 0f },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .offset { indicatorOffset }
                .width(96.dp)
                .background(CameraColors.Accent, RoundedCornerShape(2.dp))
                .align(Alignment.BottomStart)
                .size(width = 96.dp, height = 2.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            modes.forEach { mode ->
                val selected = mode == selectedMode
                Text(
                    text = modeLabel(mode),
                    color = if (selected) CameraColors.TextPrimary else CameraColors.TextMuted,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ShutterCluster(
    state: RecorderUiState,
    onClick: () -> Unit,
) {
    val targetScale = if (state.isRecording) 0.92f else 1f
    val buttonScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (state.isRecording) CameraMotionSpec.shutterSpring else CameraMotionSpec.d140,
        label = "shutter-scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .scale(buttonScale)
                .size(94.dp)
                .border(4.dp, CameraColors.TextPrimary, CircleShape)
                .clickable(enabled = state.hasCameraPermission, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (state.isRecording) 40.dp else 70.dp)
                    .background(
                        color = if (state.isRecording) CameraColors.Accent else CameraColors.TextPrimary,
                        shape = if (state.isRecording) RoundedCornerShape(12.dp) else CircleShape,
                    ),
            )
        }

        Text(
            text = actionLabel(state),
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ParamButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 46.dp)
            .border(1.dp, CameraColors.BorderStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "PARAM",
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
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

private fun actionLabel(state: RecorderUiState): String {
    if (!state.hasCameraPermission) {
        return "Нет доступа"
    }

    return when (state.mode) {
        CaptureMode.PHOTO -> "Фото"
        CaptureMode.VIDEO -> if (state.isRecording) "Стоп" else "Запись"
        CaptureMode.STRESS -> if (state.isRecording) "Стоп тест" else "Старт тест"
    }
}
