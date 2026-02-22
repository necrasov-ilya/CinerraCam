package com.cinerracam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun CameraTopBar(
    state: RecorderUiState,
    onRequestCameraPermission: () -> Unit,
    onOpenProSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = rememberStatusChip(state)

    Row(
        modifier = modifier
            .background(CameraColors.Surface)
            .clickable(enabled = !state.hasCameraPermission, onClick = onRequestCameraPermission)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CinerraCam",
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(status.background, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = status.text,
                    color = status.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            SquareIconButton(
                label = "SET",
                onClick = onOpenProSettings,
            )
        }
    }
}

@Composable
private fun SquareIconButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .border(width = 1.dp, color = CameraColors.BorderStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class StatusChip(
    val text: String,
    val background: Color,
    val content: Color,
)

@Composable
private fun rememberStatusChip(state: RecorderUiState): StatusChip {
    if (!state.hasCameraPermission) {
        return StatusChip("НЕТ ДОСТУПА", Color(0xFF202020), CameraColors.TextPrimary)
    }

    val lowered = state.statusMessage.lowercase()
    return when {
        state.isRecording -> StatusChip("REC", CameraColors.Accent, CameraColors.TextPrimary)
        lowered.contains("ошибка") || lowered.contains("error") ->
            StatusChip("ОШИБКА", CameraColors.Error, CameraColors.TextPrimary)
        lowered.contains("готов") || lowered.contains("ready") ->
            StatusChip("ГОТОВО", Color(0xFF1C1C1C), CameraColors.TextPrimary)
        else -> StatusChip("INIT", Color(0xFF1C1C1C), CameraColors.TextSecondary)
    }
}
