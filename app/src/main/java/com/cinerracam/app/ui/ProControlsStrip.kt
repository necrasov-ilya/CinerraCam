package com.cinerracam.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinerracam.app.camera.AfMode
import com.cinerracam.app.camera.WhiteBalancePreset
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun ProControlsStrip(
    state: RecorderUiState,
    activeControl: QuickControl?,
    onControlTap: (QuickControl?) -> Unit,
    onExposureCompensationChanged: (Int) -> Unit,
    onIsoChanged: (Int?) -> Unit,
    onExposureTimeChanged: (Long?) -> Unit,
    onWhiteBalanceSelected: (WhiteBalancePreset) -> Unit,
    onAfModeChanged: (AfMode) -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(CameraColors.Surface.copy(alpha = 0.85f))) {
        AnimatedVisibility(
            visible = activeControl != null,
            enter = expandVertically() + fadeIn(CameraMotionSpec.d140),
            exit = shrinkVertically() + fadeOut(CameraMotionSpec.d90),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CameraColors.SurfaceCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                when (activeControl) {
                    QuickControl.EV -> EvPicker(state, onExposureCompensationChanged)
                    QuickControl.ISO -> IsoPicker(state, onIsoChanged)
                    QuickControl.SHUTTER_SPEED -> ShutterSpeedPicker(state, onExposureTimeChanged)
                    QuickControl.WB -> WbPicker(state, onWhiteBalanceSelected)
                    QuickControl.FOCUS_MODE -> AfPicker(state, onAfModeChanged)
                    null -> {}
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProControlItem(
                icon = "+/-",
                value = evDisplayValue(state.exposureCompensationValue, state.exposureCompensationStep),
                isActive = activeControl == QuickControl.EV,
                enabled = state.exposureCompensationRange.first != state.exposureCompensationRange.last,
                onClick = { onControlTap(if (activeControl == QuickControl.EV) null else QuickControl.EV) },
            )
            ProControlItem(
                icon = "ISO",
                value = state.selectedIso?.toString() ?: "A",
                isActive = activeControl == QuickControl.ISO,
                enabled = state.supportsManualSensor,
                onClick = { onControlTap(if (activeControl == QuickControl.ISO) null else QuickControl.ISO) },
            )
            ProControlItem(
                icon = "S",
                value = shutterDisplayValue(state.selectedExposureTimeNs),
                isActive = activeControl == QuickControl.SHUTTER_SPEED,
                enabled = state.supportsManualSensor,
                onClick = { onControlTap(if (activeControl == QuickControl.SHUTTER_SPEED) null else QuickControl.SHUTTER_SPEED) },
            )
            ProControlItem(
                icon = "WB",
                value = state.selectedWhiteBalance.label.take(5),
                isActive = activeControl == QuickControl.WB,
                enabled = true,
                onClick = { onControlTap(if (activeControl == QuickControl.WB) null else QuickControl.WB) },
            )
            ProControlItem(
                icon = "AF",
                value = state.selectedAfMode.label,
                isActive = activeControl == QuickControl.FOCUS_MODE,
                enabled = state.availableAfModes.size > 1,
                onClick = { onControlTap(if (activeControl == QuickControl.FOCUS_MODE) null else QuickControl.FOCUS_MODE) },
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CameraColors.Border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFullSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", fontSize = 18.sp, color = CameraColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun ProControlItem(
    icon: String,
    value: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val textColor = when {
        isActive -> CameraColors.AccentYellow
        enabled -> CameraColors.TextPrimary
        else -> CameraColors.TextMuted
    }
    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(text = icon, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = textColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EvPicker(state: RecorderUiState, onChange: (Int) -> Unit) {
    val range = state.exposureCompensationRange
    Column {
        Text(
            text = "Экспокоррекция: ${evDisplayValue(state.exposureCompensationValue, state.exposureCompensationStep)}",
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = state.exposureCompensationValue.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = accentSliderColors(),
        )
    }
}

@Composable
private fun IsoPicker(state: RecorderUiState, onChange: (Int?) -> Unit) {
    val range = state.isoRange ?: return
    val current = state.selectedIso ?: range.first
    Column {
        Text(
            text = "ISO: $current",
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = accentSliderColors(),
        )
    }
}

@Composable
private fun ShutterSpeedPicker(state: RecorderUiState, onChange: (Long?) -> Unit) {
    val range = state.exposureTimeRangeNs ?: return
    val currentNs = state.selectedExposureTimeNs ?: range.first
    val logMin = ln(range.first.toDouble().coerceAtLeast(1.0))
    val logMax = ln(range.last.toDouble().coerceAtLeast(2.0))
    val logCurrent = ln(currentNs.toDouble().coerceAtLeast(1.0))

    Column {
        Text(
            text = "Выдержка: ${shutterDisplayValue(currentNs)}",
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = logCurrent.toFloat(),
            onValueChange = { logVal ->
                val ns = kotlin.math.exp(logVal.toDouble()).toLong().coerceIn(range.first, range.last)
                onChange(ns)
            },
            valueRange = logMin.toFloat()..logMax.toFloat(),
            colors = accentSliderColors(),
        )
    }
}

@Composable
private fun WbPicker(state: RecorderUiState, onSelect: (WhiteBalancePreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Баланс белого", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.whiteBalanceOptions) { preset ->
                val selected = preset == state.selectedWhiteBalance
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) CameraColors.AccentYellow else CameraColors.Surface)
                        .border(1.dp, if (selected) CameraColors.AccentYellow else CameraColors.Border, RoundedCornerShape(6.dp))
                        .clickable { onSelect(preset) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = preset.label,
                        color = if (selected) CameraColors.Background else CameraColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun AfPicker(state: RecorderUiState, onSelect: (AfMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Режим фокуса", color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.availableAfModes.forEach { mode ->
                val selected = mode == state.selectedAfMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) CameraColors.AccentYellow else CameraColors.Surface)
                        .border(1.dp, if (selected) CameraColors.AccentYellow else CameraColors.Border, RoundedCornerShape(6.dp))
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) CameraColors.Background else CameraColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun accentSliderColors() = SliderDefaults.colors(
    thumbColor = CameraColors.AccentYellow,
    activeTrackColor = CameraColors.AccentYellow,
    inactiveTrackColor = CameraColors.Border,
)

private fun evDisplayValue(steps: Int, stepSize: Float): String {
    if (steps == 0) return "0"
    val ev = steps * stepSize
    return if (ev > 0) "+${"%.1f".format(ev)}" else "${"%.1f".format(ev)}"
}

private fun shutterDisplayValue(ns: Long?): String {
    if (ns == null) return "A"
    val seconds = ns / 1_000_000_000.0
    return if (seconds >= 1.0) {
        "${"%.1f".format(seconds)}\""
    } else {
        val frac = (1.0 / seconds).roundToInt()
        "1/$frac"
    }
}
