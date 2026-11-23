package com.yourstudy.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.Screens
import com.yourstudy.app.Session
import com.yourstudy.app.Store
import com.yourstudy.app.VoiceController
import com.yourstudy.app.VoiceNote
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Notes history + workspace. Reached from Tools; during focus only the Quick Note sheet is used. */
@Composable
fun VoiceNotesScreen(duringFocus: Boolean, onClose: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // null = list, "new" or a note id
    DisposableEffect(Unit) {
        val prev = Screens.current.value
        Screens.current.value = "voice_notes"
        onDispose { Screens.current.value = prev }
    }
    AnimatedContent(
        targetState = editing,
        transitionSpec = { fadeIn(Motion.spec()) togetherWith fadeOut(Motion.spec()) },
        label = "notes",
    ) { id ->
        if (id == null) NotesList(onNew = { editing = "new" }, onOpen = { editing = it }, onClose = onClose)
        else NoteWorkspace(
            noteId = id.takeIf { it != "new" }, duringFocus = duringFocus, compact = false, onDone = { editing = null },
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun NotesList(onNew: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val notes by Store.notes.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<VoiceNote?>(null) }
    val fmtDate = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Notes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        if (notes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { EmptyState(Icons.Default.Create, "No notes") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(notes, key = { it.id }) { n ->
                    Card(
                        onClick = { onOpen(n.id) }, shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    fmtDate.format(Date(n.updatedAt)) + " · " + n.languageTag + if (n.wasCapturedDuringFocus) " · focus" else "",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleting = n }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PressPill(onClick = onNew, modifier = Modifier.fillMaxWidth()) { PillLabel("New") }
        Spacer(Modifier.height(16.dp))
    }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete note?") },
            confirmButton = { TextButton(onClick = { Store.deleteNote(n.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Docked quick note for active focus/break sessions. Saves like any other note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NoteWorkspace(
            noteId = null, duringFocus = true, compact = true, onDone = onDismiss,
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        )
    }
}

private val LANGS = listOf(
    "en-IN" to "English (IN)", "en-US" to "English (US)", "hi-IN" to "Hindi", "te-IN" to "Telugu",
    "ta-IN" to "Tamil", "bn-IN" to "Bengali", "mr-IN" to "Marathi", "kn-IN" to "Kannada",
)

@Composable
private fun Waveform(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val lvl by animateFloatAsState(if (active) level else 0f, Motion.spec(), label = "level")
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.width(112.dp).height(48.dp)) {
        val n = 7
        val w = size.width / (n * 2 - 1)
        for (i in 0 until n) {
            val wave = if (active) 0.35f + 0.65f * abs(sin(phase * 2 * PI.toFloat() + i * 0.8f)) else 0.15f
            val h = (size.height * (0.15f + 0.85f * wave * (0.3f + 0.7f * lvl))).coerceAtMost(size.height)
            drawRoundRect(color, Offset(i * 2 * w, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

@Composable
fun NoteWorkspace(noteId: String?, duringFocus: Boolean, compact: Boolean, onDone: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val controller = remember { VoiceController(ctx) }
    val existing = remember { noteId?.let { id -> Store.notes.value.firstOrNull { it.id == id } } }
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    val created = remember { existing?.createdAt ?: System.currentTimeMillis() }
    val flagged = remember { existing?.wasCapturedDuringFocus ?: duringFocus }
    val focusStart = remember { existing?.focusSessionStart ?: if (duringFocus) Session.sessionStartedAt else null }
    var lastSaved by remember { mutableStateOf(existing?.content) }
    var deleted by remember { mutableStateOf(false) }

    fun save() {
        if (deleted) return
        val text = controller.combined.trim()
        if (text.isEmpty() || text == lastSaved) return
        lastSaved = text
        Store.saveNote(VoiceNote(id, text, controller.languageTag, created, System.currentTimeMillis(), flagged, focusStart))
    }
    fun finish() { controller.stop(); save(); onDone() }

    LaunchedEffect(Unit) { existing?.let { controller.load(it.content); controller.setLanguage(it.languageTag) } }
    LaunchedEffect(controller.committed) { delay(800); save() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stop(); save() }
    DisposableEffect(Unit) {
        val prev = Screens.current.value
        Screens.current.value = "voice_notes"
        onDispose { controller.destroy(); save(); Screens.current.value = prev }
    }
    BackHandler { finish() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) controller.start() else controller.message = "Microphone denied."
    }
    fun toggle() {
        if (controller.keepListening) { controller.stop(); save() }
        else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.start()
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val systemTag = remember { Locale.getDefault().toLanguageTag() }
    val langs = remember { if (LANGS.any { it.first == systemTag }) LANGS else listOf(systemTag to systemTag) + LANGS }
    val listening = controller.keepListening

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (compact) TextButton(onClick = { finish() }) { Text("Done") }
            else IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val text = controller.combined.trim()
                if (text.isNotEmpty()) {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("note", text))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Copy") }
            if (!duringFocus) IconButton(onClick = {
                val text = controller.combined.trim()
                if (text.isNotEmpty()) ctx.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Icon(Icons.Default.Share, "Share") }
            if (!compact && lastSaved != null) IconButton(onClick = {
                deleted = true; controller.stop(); Store.deleteNote(id); onDone()
            }) { Icon(Icons.Default.Delete, "Delete") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(langs) { (tag, label) ->
                FilterChip(controller.languageTag == tag, { controller.setLanguage(tag) }, { Text(label) }, shape = CircleShape)
            }
        }
        Spacer(Modifier.height(12.dp))
        val fieldMod = if (compact) Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp)
        else Modifier.weight(1f).fillMaxWidth()
        OutlinedTextField(
            value = controller.combined, onValueChange = controller::onManualTextEdited,
            modifier = fieldMod, shape = RoundedCornerShape(24.dp), placeholder = { Text("Speak or type") },
        )
        controller.message?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Waveform(listening, controller.level)
            PressPill(
                onClick = { toggle() }, modifier = Modifier.weight(1f),
                containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            ) { PillLabel(if (listening) "Stop" else "Record") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
