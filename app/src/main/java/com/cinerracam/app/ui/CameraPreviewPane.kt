package com.cinerracam.app.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreviewPane(
    state: RecorderUiState,
    onPreviewTextureReady: (TextureView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(CameraColors.Background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            onPreviewTextureReady(this@apply)
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            onPreviewTextureReady(this@apply)
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }

                    if (isAvailable) {
                        onPreviewTextureReady(this)
                    }
                }
            },
            update = { view ->
                if (view.isAvailable) {
                    onPreviewTextureReady(view)
                }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(CameraColors.SurfaceOverlay)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "RAW ${state.selectedRawSize?.label ?: "-"} | ${state.selectedAspectRatio?.label ?: "-"}",
                color = CameraColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Cap ${state.stats.capturedFrames}  Wr ${state.stats.writtenFrames}  Drop ${state.stats.droppedFrames}",
                color = CameraColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Avg ${"%.2f".format(state.stats.avgWriteMs)} ms  Q ${state.stats.queueHighWatermark}",
                color = CameraColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AnimatedVisibility(
            visible = state.previewDebugInfo.isNotBlank(),
            enter = fadeIn(CameraMotionSpec.d140),
            exit = fadeOut(CameraMotionSpec.d90),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Text(
                text = state.previewDebugInfo,
                color = CameraColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(CameraColors.SurfaceOverlay)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
