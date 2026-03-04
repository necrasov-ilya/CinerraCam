package com.cinerracam.app.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun CameraPreviewPane(
    state: RecorderUiState,
    onPreviewTextureReady: (TextureView) -> Unit,
    onTouchToFocus: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }
    val focusRingAlpha by animateFloatAsState(
        targetValue = if (showFocusRing) 1f else 0f,
        animationSpec = CameraMotionSpec.d280,
        label = "focus-ring-alpha",
    )
    val focusRingScale by animateFloatAsState(
        targetValue = if (showFocusRing) 1f else 1.5f,
        animationSpec = CameraMotionSpec.focusRingSpring,
        label = "focus-ring-scale",
    )

    LaunchedEffect(focusTouchPoint) {
        if (focusTouchPoint != null) {
            showFocusRing = true
            delay(1200)
            showFocusRing = false
        }
    }

    Box(modifier = modifier.background(CameraColors.Background)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val normX = offset.x / size.width
                        val normY = offset.y / size.height
                        focusTouchPoint = offset
                        onTouchToFocus(normX, normY)
                    }
                },
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                            onPreviewTextureReady(this@apply)
                        }
                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {
                            onPreviewTextureReady(this@apply)
                        }
                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
                    }
                    if (isAvailable) onPreviewTextureReady(this)
                }
            },
            update = { view ->
                if (view.isAvailable) onPreviewTextureReady(view)
            },
        )

        if (state.showGrid) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        focusTouchPoint?.let { point ->
            val sizePx = with(LocalDensity.current) { 64.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - sizePx / 2).toInt(),
                            (point.y - sizePx / 2).toInt(),
                        )
                    }
                    .size(64.dp)
                    .alpha(focusRingAlpha)
                    .drawWithContent {
                        drawCircle(
                            color = CameraColors.FocusRing,
                            radius = size.minDimension / 2 * focusRingScale,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    },
            )
        }

        InfoBadge(state = state, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
    }
}

@Composable
private fun InfoBadge(state: RecorderUiState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.hasCameraPermission,
        enter = fadeIn(CameraMotionSpec.d140),
        exit = fadeOut(CameraMotionSpec.d90),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .background(CameraColors.SurfaceOverlay, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RAW ${state.selectedRawSize?.label ?: "-"}",
                    color = CameraColors.AccentYellow,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = state.selectedAspectRatio?.label ?: "-",
                    color = CameraColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (state.zoomRatio > 1f) {
                    Text(
                        text = "${"%.1f".format(state.zoomRatio)}x",
                        color = CameraColors.AccentYellow,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (state.isRecording || state.stats.capturedFrames > 0) {
                Text(
                    text = "Cap ${state.stats.capturedFrames}  Wr ${state.stats.writtenFrames}  Drop ${state.stats.droppedFrames}  Avg ${"%.1f".format(state.stats.avgWriteMs)}ms  Q ${state.stats.queueHighWatermark}",
                    color = CameraColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 0.5f.dp.toPx()
        val color = CameraColors.GridLine
        drawLine(color, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth)
        drawLine(color, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), strokeWidth)
        drawLine(color, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth)
        drawLine(color, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), strokeWidth)
    }
}
