package com.cinerracam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinerracam.core.model.RecorderState

@Composable
fun CinerraCamApp(viewModel: RecorderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme {
        RecorderScreen(
            state = uiState,
            onRecordClick = viewModel::onRecordClick,
            onAudioToggle = viewModel::onAudioEnabledChange,
            onFakeToggle = viewModel::onFakeDataModeChange,
        )
    }
}

@Composable
private fun RecorderScreen(
    state: RecorderUiState,
    onRecordClick: () -> Unit,
    onAudioToggle: (Boolean) -> Unit,
    onFakeToggle: (Boolean) -> Unit,
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF081B2E),
            Color(0xFF163E63),
            Color(0xFF1E5F8E),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "CinerraCam",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "DNG-first prototype | API 26+ | Vivo target",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFDCEEFF),
            )

            StatusCard(state)
            MetricsCard(state)
            ControlsCard(
                state = state,
                onAudioToggle = onAudioToggle,
                onFakeToggle = onFakeToggle,
            )

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = onRecordClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.recorderState is RecorderState.Recording) {
                        Color(0xFFB3261E)
                    } else {
                        Color(0xFF1C8653)
                    },
                ),
            ) {
                Text(
                    text = if (state.recorderState is RecorderState.Recording) "STOP" else "REC",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: RecorderUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xBB0B253D)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Capture Status", color = Color.White, style = MaterialTheme.typography.titleMedium)

            StatusLine(label = "Sensor", value = state.sensorStatus)
            StatusLine(label = "Mode", value = state.targetResolution.label + " @ ${state.targetFps}fps")
            StatusLine(label = "Recorder", value = state.recorderState.toUiLabel())
        }
    }
}

@Composable
private fun MetricsCard(state: RecorderUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xBB102A44)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live Metrics", color = Color.White, style = MaterialTheme.typography.titleMedium)

            MetricLine("Captured", state.metrics.framesCaptured.toString())
            MetricLine("Written", state.metrics.framesWritten.toString())
            MetricLine("Dropped", state.metrics.framesDropped.toString(), highlight = state.metrics.framesDropped > 0)
            MetricLine("Avg write", "%.2f ms".format(state.metrics.avgWriteMs))
            MetricLine("Queue HWM", state.metrics.queueHighWatermark.toString())
        }
    }
}

@Composable
private fun ControlsCard(
    state: RecorderUiState,
    onAudioToggle: (Boolean) -> Unit,
    onFakeToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xBB133857)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Controls", color = Color.White, style = MaterialTheme.typography.titleMedium)

            ToggleRow("Audio Timeline", state.audioEnabled, onAudioToggle)
            ToggleRow("Fake-data mode", state.fakeDataMode, onFakeToggle)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color(0xFFE7F0F8), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFBBD7EF), modifier = Modifier.width(96.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, color = Color.White)
    }
}

@Composable
private fun MetricLine(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFD8EAFB))
        Text(
            text = value,
            color = if (highlight) Color(0xFFFF8A80) else Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun RecorderState.toUiLabel(): String = when (this) {
    is RecorderState.Idle -> "Idle"
    is RecorderState.Preparing -> "Preparing"
    is RecorderState.Recording -> "Recording"
    is RecorderState.Stopping -> "Stopping"
    is RecorderState.Error -> "Error: $message"
}
