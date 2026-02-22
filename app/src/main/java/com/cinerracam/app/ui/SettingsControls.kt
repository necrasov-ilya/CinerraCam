package com.cinerracam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDropdown(
    title: String,
    value: String,
    enabled: Boolean,
    lockReason: String?,
    entries: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, color = CameraColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
        if (!enabled && !lockReason.isNullOrBlank()) {
            Text(text = lockReason, color = CameraColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CameraColors.SurfaceElevated)
                .border(1.dp, if (enabled) CameraColors.BorderStrong else CameraColors.Border)
                .clickable(enabled = enabled, onClick = { expanded = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text = value, color = if (enabled) CameraColors.TextPrimary else CameraColors.TextMuted)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            action()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (selected) CameraColors.Accent else CameraColors.Surface)
            .border(1.dp, if (selected) CameraColors.Accent else CameraColors.BorderStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = CameraColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
