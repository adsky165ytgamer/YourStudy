@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.yourstudy.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.FocusTag
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.StudyChapter
import com.yourstudy.app.StudyPlanner
import com.yourstudy.app.StudySubject
import com.yourstudy.app.StudyTask
import com.yourstudy.app.TaskPriority
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SUBJECT_COLORS = listOf(
    0xFF6750A4, 0xFF0B7A75, 0xFFB3261E, 0xFF8A5100, 0xFF2E6A00, 0xFF005AC1, 0xFF9C27B0, 0xFF5D5F5F,
).map { Color(it) }

internal fun subjectColor(token: Int): Color = SUBJECT_COLORS[token.mod(SUBJECT_COLORS.size)]

private enum class PlannerView { TASKS, SUBJECTS }
private enum class Filter { TODAY, ALL, HIGH, DONE }

private fun endOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.timeInMillis

@Composable
private fun MetaPill(text: String, container: Color, content: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium,
    )
}

@Composable
fun StudyPlannerScreen(onClose: () -> Unit, onFocus: (StudyTask) -> Unit) {
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val tasks by StudyPlanner.tasks.collectAsStateWithLifecycle()
    val subjects by StudyPlanner.subjects.collectAsStateWithLifecycle()
    val chapters by StudyPlanner.chapters.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(PlannerView.TASKS) }
    var filter by rememberSaveable { mutableStateOf(Filter.TODAY) }
    var subjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<StudyTask?>(null) }
    var addSubject by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    val ui = cfg.ui("section_study_planner")
    val badge = ui.badge ?: cfg.feature("study_planner").badge
    val now = remember(tasks) { System.currentTimeMillis() }
    val today = remember(tasks) { endOfToday() }
    val shown = remember(tasks, filter, subjectFilter) {
        tasks.filter { t ->
            when {
                subjectFilter != null -> t.subjectId == subjectFilter
                filter == Filter.TODAY -> !t.completed && (t.dueDateMillis == null || t.dueDateMillis <= today)
                filter == Filter.HIGH -> !t.completed && t.priority == TaskPriority.HIGH
                filter == Filter.DONE -> t.completed
                else -> true
            }
        }.sortedWith(
            compareBy<StudyTask> { it.completed }.thenByDescending { it.priority.ordinal }.thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
        )
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(ui.label ?: "Planner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            badge?.let { Badge { Text(it) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(view == PlannerView.TASKS, { view = PlannerView.TASKS }, { Text("Tasks") }, shape = CircleShape)
            FilterChip(view == PlannerView.SUBJECTS, { view = PlannerView.SUBJECTS }, { Text("Subjects") }, shape = CircleShape)
        }

        if (view == PlannerView.TASKS) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Filter.TODAY to "Today", Filter.ALL to "All", Filter.HIGH to "High Priority", Filter.DONE to "Done").forEach { (f, label) ->
                    FilterChip(subjectFilter == null && filter == f, { filter = f; subjectFilter = null }, { Text(label) }, shape = CircleShape)
                }
                subjects.forEach { s ->
                    FilterChip(subjectFilter == s.id, { subjectFilter = if (subjectFilter == s.id) null else s.id }, { Text(s.name) }, shape = CircleShape)
                }
                FilterChip(false, { addSubject = true }, { Text("+ Subject") }, shape = CircleShape)
            }
            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { EmptyState(Icons.Default.Check, "No tasks") }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(shown, key = { it.id }) { t ->
                        TaskCard(
                            t, subjects.firstOrNull { it.id == t.subjectId }, now,
                            onToggle = { StudyPlanner.setTaskDone(t.id, it) },
                            onEdit = { editing = t },
                            onFocus = { onFocus(t) },
                        )
                    }
                }
            }
        } else {
            if (subjects.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { EmptyState(Icons.Default.DateRange, "No subjects") }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(subjects, key = { it.id }) { s -> SubjectCard(s, chapters.filter { it.subjectId == s.id }) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        PressPill(
            onClick = { if (view == PlannerView.TASKS) editing = StudyTask(title = "", subjectId = subjectFilter) else addSubject = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            PillLabel(if (view == PlannerView.TASKS) "Task" else "Subject")
        }
        Spacer(Modifier.height(16.dp))
    }

    editing?.let { t -> TaskSheet(t, isNew = tasks.none { it.id == t.id }, onDismiss = { editing = null }) }
    if (addSubject) SubjectDialog(onDismiss = { addSubject = false })
}

@Composable
private fun TaskCard(t: StudyTask, subject: StudySubject?, now: Long, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onFocus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pop = remember { Animatable(1f) }
    LaunchedEffect(t.completed) {
        if (t.completed) { pop.animateTo(1.3f, spring(0.4f, 600f)); pop.animateTo(1f, spring(0.5f, 400f)) }
    }
    val overdue = t.dueDateMillis != null && !t.completed && t.dueDateMillis < now
    Surface(onClick = onEdit, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = t.completed, onCheckedChange = onToggle,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
            Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                    color = if (t.completed) cs.onSurfaceVariant else cs.onSurface,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    subject?.let { MetaPill(it.name, subjectColor(it.colorToken).copy(alpha = 0.22f)) }
                    MetaPill(
                        when (t.priority) { TaskPriority.HIGH -> "HIGH"; TaskPriority.MEDIUM -> "MED"; TaskPriority.LOW -> "LOW" },
                        if (t.priority == TaskPriority.HIGH) cs.tertiaryContainer else cs.surfaceContainerHighest,
                    )
                    t.dueDateMillis?.let {
                        MetaPill(
                            SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it)),
                            if (overdue) cs.errorContainer else cs.surfaceContainerHighest,
                            if (overdue) cs.onErrorContainer else cs.onSurface,
                        )
                    }
                    MetaPill("${t.loggedMinutes}/${t.targetMinutes}m", cs.secondaryContainer, cs.onSecondaryContainer)
                }
            }
            if (!t.completed) IconButton(onClick = onFocus) { Icon(Icons.Default.PlayArrow, "Focus") }
        }
    }
}

@Composable
private fun SubjectCard(s: StudySubject, chapters: List<StudyChapter>) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var newChapter by remember { mutableStateOf("") }
    val done = chapters.count { it.completed }
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh, modifier = Modifier.animateContentSize(Motion.spec())) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(subjectColor(s.colorToken)))
                Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaPill("$done/${chapters.size}", cs.secondaryContainer, cs.onSecondaryContainer)
                IconButton(onClick = { StudyPlanner.deleteSubject(s.id) }) { Icon(Icons.Default.Delete, "Delete") }
            }
            LinearProgressIndicator(
                progress = { if (chapters.isEmpty()) 0f else done.toFloat() / chapters.size },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = subjectColor(s.colorToken), trackColor = cs.surfaceContainerHighest,
            )
            if (open) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                    chapters.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(c.completed, { StudyPlanner.toggleChapter(c.id) })
                            Text(
                                c.title, Modifier.weight(1f),
                                textDecoration = if (c.completed) TextDecoration.LineThrough else null,
                                color = if (c.completed) cs.onSurfaceVariant else cs.onSurface,
                            )
                            IconButton(onClick = { StudyPlanner.deleteChapter(c.id) }) { Icon(Icons.Default.Close, "Delete chapter") }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newChapter, onValueChange = { newChapter = it.take(60) }, singleLine = true, shape = CircleShape,
                            placeholder = { Text("Chapter") }, modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { StudyPlanner.addChapter(s.id, newChapter); newChapter = "" },
                            enabled = newChapter.isNotBlank(),
                        ) { Icon(Icons.Default.Add, "Add chapter") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, singleLine = true, shape = CircleShape,
                    placeholder = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUBJECT_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c).clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { if (color == i) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                StudyPlanner.upsertSubject(StudySubject(name = name.trim(), colorToken = color)); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TaskSheet(initial: StudyTask, isNew: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val subjects by StudyPlanner.subjects.collectAsStateWithLifecycle()
    val chapters by StudyPlanner.chapters.collectAsStateWithLifecycle()
    val range = RemoteControl.minutesRange()
    var title by remember { mutableStateOf(initial.title) }
    var subjectId by remember { mutableStateOf(initial.subjectId) }
    var chapterId by remember { mutableStateOf(initial.chapterId) }
    var priority by remember { mutableStateOf(initial.priority) }
    var due by remember { mutableStateOf(initial.dueDateMillis) }
    var reminder by remember { mutableStateOf(initial.reminderAtMillis) }
    var target by remember { mutableIntStateOf(initial.targetMinutes.coerceIn(range)) }
    val fmtDate = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val fmtDateTime = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(80) }, singleLine = true, shape = CircleShape,
                placeholder = { Text("Title") }, modifier = Modifier.fillMaxWidth(),
            )
            if (subjects.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { s ->
                        FilterChip(subjectId == s.id, { subjectId = if (subjectId == s.id) null else s.id; chapterId = null }, { Text(s.name) }, shape = CircleShape)
                    }
                }
            }
            val subjectChapters = chapters.filter { it.subjectId == subjectId }
            if (subjectChapters.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjectChapters.forEach { c ->
                        FilterChip(chapterId == c.id, { chapterId = if (chapterId == c.id) null else c.id }, { Text(c.title) }, shape = CircleShape)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskPriority.LOW to "Low", TaskPriority.MEDIUM to "Medium", TaskPriority.HIGH to "High").forEach { (p, l) ->
                    FilterChip(priority == p, { priority = p }, { Text(l) }, shape = CircleShape)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = due ?: System.currentTimeMillis() }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            due = Calendar.getInstance().apply { set(y, m, d, 23, 59, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    shape = CircleShape, modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)); Spacer(Modifier.size(8.dp))
                    Text(due?.let { fmtDate.format(Date(it)) } ?: "Deadline")
                }
                if (due != null) IconButton(onClick = { due = null }) { Icon(Icons.Default.Close, "Clear deadline") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = reminder ?: System.currentTimeMillis() }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            TimePickerDialog(ctx, { _, h, min ->
                                reminder = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(ctx)).show()
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    shape = CircleShape, modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(16.dp)); Spacer(Modifier.size(8.dp))
                    Text(reminder?.let { fmtDateTime.format(Date(it)) } ?: "Reminder")
                }
                if (reminder != null) IconButton(onClick = { reminder = null }) { Icon(Icons.Default.Close, "Clear reminder") }
            }
            DurationStepper(target, range, { target = it }, quick = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) IconButton(onClick = { StudyPlanner.deleteTask(initial.id); onDismiss() }) { Icon(Icons.Default.Delete, "Delete") }
                PressPill(
                    onClick = {
                        StudyPlanner.upsertTask(
                            initial.copy(
                                title = title.trim(), subjectId = subjectId, chapterId = chapterId, priority = priority,
                                dueDateMillis = due, reminderAtMillis = reminder, targetMinutes = target,
                            )
                        )
                        onDismiss()
                    },
                    enabled = title.isNotBlank(), height = 56.dp, modifier = Modifier.weight(1f),
                ) { PillLabel("Save") }
            }
        }
    }
}

/** Focus-tab picker: choose a subject or an open task (sets the minutes to its target). */
@Composable
fun TagPickerSheet(onDismiss: () -> Unit, onTask: (StudyTask) -> Unit) {
    val subjects by StudyPlanner.subjects.collectAsStateWithLifecycle()
    val tasks by StudyPlanner.tasks.collectAsStateWithLifecycle()
    val open = tasks.filter { !it.completed }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(FocusTag.taskId.value == null && FocusTag.subjectId.value == null, { FocusTag.clear(); onDismiss() }, { Text("No tag") }, shape = CircleShape)
            if (subjects.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { s ->
                        FilterChip(
                            FocusTag.taskId.value == null && FocusTag.subjectId.value == s.id,
                            { FocusTag.selectSubject(s.id); onDismiss() }, { Text(s.name) }, shape = CircleShape,
                        )
                    }
                }
            }
            open.forEach { t ->
                Surface(onClick = { onTask(t); onDismiss() }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        MetaPill("${t.targetMinutes}m", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
