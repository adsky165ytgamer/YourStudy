package com.yourstudy.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.Session
import com.yourstudy.app.Store
import com.yourstudy.app.VoiceController
import com.yourstudy.app.VoiceNote
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Separate tool area: reachable during focus/break and after, never part of the allowed-apps list. */
@Composable
fun VoiceNotesScreen(duringFocus: Boolean, onClose: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // null = list, "new" or a note id
    val id = editing
    if (id == null) NotesList(onNew = { editing = "new" }, onOpen = { editing = it }, onClose = onClose)
    else NoteEditor(noteId = id.takeIf { it != "new" }, duringFocus = duringFocus, onDone = { editing = null })
}

@Composable
private fun NotesList(onNew: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val notes by Store.notes.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<VoiceNote?>(null) }
    val fmtDate = remember { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Voice notes", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            TextButton(onClick = onClose) { Text("Close") }
        }
        if (notes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("No notes yet. Start one and just talk.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(notes, key = { it.id }) { n ->
                    Card(
                        onClick = { onOpen(n.id) },
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    fmtDate.format(Date(n.updatedAt)) + " · " + n.languageTag +
                                        if (n.wasCapturedDuringFocus) " · during focus" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleting = n }) { Icon(Icons.Default.Delete, "Delete note") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PressPill(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("New voice note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { Store.deleteNote(n.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private val LANGS = listOf(
    "en-IN" to "English (India)", "en-US" to "English (US)", "hi-IN" to "Hindi", "te-IN" to "Telugu",
    "ta-IN" to "Tamil", "bn-IN" to "Bengali", "mr-IN" to "Marathi", "kn-IN" to "Kannada",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(noteId: String?, duringFocus: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val controller = remember { VoiceController(ctx) }
    val existing = remember { noteId?.let { id -> Store.notes.value.firstOrNull { it.id == id } } }
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    val created = remember { existing?.createdAt ?: System.currentTimeMillis() }
    val flagged = remember { existing?.wasCapturedDuringFocus ?: duringFocus }
    val focusStart = remember { existing?.focusSessionStart ?: if (duringFocus) Session.sessionStartedAt else null }
    var lastSaved by remember { mutableStateOf(existing?.content) }

    fun save() {
        val text = controller.combined.trim()
        if (text.isEmpty() || text == lastSaved) return
        lastSaved = text
        Store.saveNote(VoiceNote(id, text, controller.languageTag, created, System.currentTimeMillis(), flagged, focusStart))
    }

    LaunchedEffect(Unit) {
        existing?.let { controller.load(it.content); controller.setLanguage(it.languageTag) }
    }
    // Debounced autosave of committed text (finalized speech + manual edits).
    LaunchedEffect(controller.committed) { delay(800); save() }
    // The mic can't run in the background; stop cleanly so nothing restarts later.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stop(); save() }
    DisposableEffect(Unit) { onDispose { controller.destroy(); save() } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) controller.start() else controller.message = "Microphone permission denied. You can still type your note."
    }
    fun toggle() {
        if (controller.keepListening) { controller.stop(); save() }
        else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.start()
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    BackHandler { controller.stop(); save(); onDone() }

    val systemTag = remember { Locale.getDefault().toLanguageTag() }
    val langs = remember { if (LANGS.any { it.first == systemTag }) LANGS else listOf(systemTag to "System ($systemTag)") + LANGS }
    val listening = controller.keepListening

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { controller.stop(); save(); onDone() }) { Text("‹ Notes") }
            Spacer(Modifier.weight(1f))
            if (flagged) Text("During focus", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(langs) { (tag, label) ->
                FilterChip(selected = controller.languageTag == tag, onClick = { controller.setLanguage(tag) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = controller.combined,
            onValueChange = controller::onManualTextEdited,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Tap Start and speak, or type here.") },
        )
        Text(
            controller.message ?: if (listening) "Listening… keeps going through pauses until you tap End." else "Not recording",
            style = MaterialTheme.typography.labelLarge,
            color = if (controller.message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        PressPill(
            onClick = { toggle() },
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(if (listening) "End" else "Start", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}
