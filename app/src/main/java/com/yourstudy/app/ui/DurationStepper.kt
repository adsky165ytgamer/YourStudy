package com.yourstudy.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Always-visible duration control: -5 / readout / +5, -1 / +1 fine steps, tap readout to type, optional quick jumps. */
@Composable
fun DurationStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    quick: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    var typing by remember { mutableStateOf(false) }
    fun set(v: Int) {
        val n = v.coerceIn(range)
        if (n != value) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(n) }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { set(value - 5) }, enabled = value > range.first, modifier = Modifier.size(48.dp)) {
                Text("−5", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
            }
            Text(
                "$value min",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.clip(CircleShape).clickable { typing = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            FilledTonalIconButton(onClick = { set(value + 5) }, enabled = value < range.last, modifier = Modifier.size(48.dp)) {
                Text("+5", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { set(value - 1) }, enabled = value > range.first, label = { Text("−1") }, shape = CircleShape)
            AssistChip(onClick = { set(value + 1) }, enabled = value < range.last, label = { Text("+1") }, shape = CircleShape)
        }
        if (quick) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60, 90).filter { it in range }.forEach { p ->
                    FilterChip(selected = value == p, onClick = { set(p) }, label = { Text("${p}m") }, shape = CircleShape)
                }
            }
        }
    }

    if (typing) {
        var text by remember { mutableStateOf(value.toString()) }
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("Minutes") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("${range.first}–${range.last}") },
                )
            },
            confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let { set(it) }; typing = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } },
        )
    }
}
