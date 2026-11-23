package com.yourstudy.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.FocusSchedule
import com.yourstudy.app.FocusScheduler
import com.yourstudy.app.RemoteControl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.yourstudy.app.StudyPlanner

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun hhmm(h: Int, m: Int) = String.format(Locale.getDefault(), "%02d:%02d", h, m)

private fun repeatText(s: FocusSchedule): String = when {
    s.dateMillis != null -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(s.dateMillis))
    s.days == 0 || s.days == FocusScheduler.ALL_DAYS -> "Daily"
    else -> DAY_NAMES.filterIndexed { i, _ -> s.days and (1 shl i) != 0 }.joinToString(" ")
}

@Composable
fun ScheduleScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val list by FocusScheduler.schedules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FocusSchedule?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val exactOk = remember(refresh) { FocusScheduler.canExact() }
    val fullOk = remember(refresh) { FocusScheduler.canFullScreen() }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        if (!exactOk || !fullOk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (!exactOk) AssistChip(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) },
                    label = { Text("Exact alarms") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, shape = CircleShape,
                )
                if (!fullOk) AssistChip(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))) },
                    label = { Text("Full screen") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, shape = CircleShape,
                )
            }
        }
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { EmptyState(Icons.Default.DateRange, "No schedules") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { s ->
                    ScheduleCard(
                        s, onEdit = { editing = s },
                        onToggle = { FocusScheduler.setEnabled(s.id, it) },
                        onDelete = { FocusScheduler.delete(s.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PressPill(
            onClick = {
                val now = Calendar.getInstance()
                editing = FocusSchedule(hour = now.get(Calendar.HOUR_OF_DAY), minute = now.get(Calendar.MINUTE))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { PillLabel("New") }
        Spacer(Modifier.height(16.dp))
    }

    editing?.let { e ->
        ScheduleEditor(e, onDismiss = { editing = null }, onSave = { FocusScheduler.upsert(it); editing = null })
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(s: FocusSchedule, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val subjects by StudyPlanner.subjects.collectAsStateWithLifecycle()
    val subject = subjects.firstOrNull { it.id == s.subjectId }?.name
    Surface(onClick = onEdit, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        hhmm(s.hour, s.minute),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                    )
                    Tag(if (s.autoStart) "Auto" else "Ready")
                }
                if (s.label.isNotBlank()) Text(s.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(repeatText(s)); Tag("${s.durationMinutes}m"); subject?.let { Tag(it) }
                }
            }
            Switch(checked = s.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun ScheduleEditor(initial: FocusSchedule, onDismiss: () -> Unit, onSave: (FocusSchedule) -> Unit) {
    val ctx = LocalContext.current
    val range = RemoteControl.minutesRange()
    var label by remember { mutableStateOf(initial.label) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var date by remember { mutableStateOf(initial.dateMillis) }
    var days by remember { mutableIntStateOf(if (initial.days == 0) FocusScheduler.ALL_DAYS else initial.days) }
    var duration by remember { mutableIntStateOf(initial.durationMinutes.coerceIn(range)) }
    var auto by remember { mutableStateOf(initial.autoStart) }
    var subjectId by remember { mutableStateOf(initial.subjectId) }
    val subjects by StudyPlanner.subjects.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(24) }, singleLine = true, shape = CircleShape,
                    placeholder = { Text("Label") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, DateFormat.is24HourFormat(ctx)).show()
                    },
                    shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                ) { Text(hhmm(hour, minute), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LETTERS.forEachIndexed { i, letter ->
                        val on = date == null && days and (1 shl i) != 0
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    date = null
                                    val next = days xor (1 shl i)
                                    days = if (next == 0) FocusScheduler.ALL_DAYS else next
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                letter, style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val now = Calendar.getInstance()
                            DatePickerDialog(ctx, { _, y, m, d ->
                                date = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        shape = CircleShape, modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(date?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "Date")
                    }
                    if (date != null) IconButton(onClick = { date = null }) { Icon(Icons.Default.Close, "Clear date") }
                }

                DurationStepper(duration, range, { duration = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(auto, { auto = true }, { Text("Auto-start") }, shape = CircleShape)
                    FilterChip(!auto, { auto = false }, { Text("Ready") }, shape = CircleShape)
                }
                if (subjects.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { sub ->
                        FilterChip(subjectId == sub.id, { subjectId = if (subjectId == sub.id) null else sub.id }, { Text(sub.name) }, shape = CircleShape)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(label = label.trim(), hour = hour, minute = minute, dateMillis = date,
                    days = days, durationMinutes = duration, autoStart = auto, enabled = true, subjectId = subjectId))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
