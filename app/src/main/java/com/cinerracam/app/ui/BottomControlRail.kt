package com.cinerracam.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinerracam.app.camera.CaptureMode

@Composable
fun BottomControlRail(
    state: RecorderUiState,
    onModeSelected: (CaptureMode) -> Unit,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CameraColors.Background)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShutterCluster(
            state = state,
            onClick = onPrimaryActionClick,
        )

        ModeSlider(
            selectedMode = state.mode,
            onModeSelected = onModeSelected,
        )
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

    val innerSize by animateDpAsState(
        targetValue = if (state.isRecording) 36.dp else 66.dp,
        label = "shutter-inner",
    )

    val innerColor by animateColorAsState(
        targetValue = if (state.isRecording) CameraColors.Recording else CameraColors.TextPrimary,
        label = "shutter-color",
    )

    val innerRadius by animateDpAsState(
        targetValue = if (state.isRecording) 10.dp else 33.dp,
        label = "shutter-radius",
    )

    Box(
        modifier = Modifier
            .scale(buttonScale)
            .size(82.dp)
            .border(3.dp, CameraColors.TextPrimary, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = state.hasCameraPermission, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .background(innerColor, RoundedCornerShape(innerRadius)),
        )
    }
}

@Composable
private fun ModeSlider(
    selectedMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
) {
    val modes = remember { listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.STRESS) }
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
    val density = LocalDensity.current
    val thresholdPx = with(density) { 48.dp.toPx() }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
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
                            val prev = (selectedIndex - 1).coerceAtLeast(0)
                            onModeSelected(modes[prev])
                        }
                    },
                    onDragEnd = { dragAccumulator = 0f },
                    onDragCancel = { dragAccumulator = 0f },
                )
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { mode ->
            val isSelected = mode == selectedMode
            val color by animateColorAsState(
                targetValue = if (isSelected) CameraColors.TextPrimary else CameraColors.TextMuted,
                label = "mode-color-${mode.name}",
            )

            Column(
                modifier = Modifier
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = modeLabel(mode),
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .width(24.dp)
                            .height(2.dp)
                            .background(CameraColors.Accent, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}

private fun modeLabel(mode: CaptureMode): String {
    return when (mode) {
        CaptureMode.PHOTO -> "Фото"
        CaptureMode.VIDEO -> "Видео"
        CaptureMode.STRESS -> "Тест"
    }
}
