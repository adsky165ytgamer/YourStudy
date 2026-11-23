package com.yourstudy.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.Session
import com.yourstudy.app.SessionUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun QuickNoteButton(onOpen: () -> Unit) {
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val ui = cfg.ui("section_voice_notes")
    val f = cfg.feature("voice_notes")
    if (!ui.visible || !f.enabled || !f.allowDuringFocus) return
    PressPill(
        onClick = { requestVoice(cfg, true, onOpen) },
        enabled = ui.enabled,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(Icons.Default.Create, null, Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        PillLabel("Note")
    }
}

@Composable
fun FocusScreen(s: SessionUi) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf(false) }
    val apps by produceState(emptyList<AppEntry>(), s.essential) {
        value = withContext(Dispatchers.IO) { loadLaunchable(ctx).filter { it.pkg in s.essential } }
    }
    val target = if (s.plannedMs > 0) (s.focusRemainingMs.toFloat() / s.plannedMs).coerceIn(0f, 1f) else 0f
    val fraction by animateFloatAsState(target, Motion.spec(), label = "ring")

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction }, modifier = Modifier.fillMaxSize(), strokeWidth = 16.dp,
                trackColor = MaterialTheme.colorScheme.secondaryContainer, strokeCap = StrokeCap.Round,
            )
            Text(fmt(s.focusRemainingMs), style = timerStyle(56.sp))
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(88.dp), Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(apps, key = { it.pkg }) { AppTile(it) } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickNoteButton { note = true }
            HoldPill(
                label = if (s.breakUsed) "Break used" else "Hold for break",
                enabled = !s.breakUsed, onTrigger = { Session.startBreak() }, modifier = Modifier.weight(1f),
            )
        }
    }
    if (note) QuickNoteSheet { note = false }
}

@Composable
fun BreakScreen(s: SessionUi) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf(false) }
    var giveUp by remember { mutableStateOf(false) }
    val apps by produceState(emptyList<AppEntry>(), Unit) { value = withContext(Dispatchers.IO) { loadLaunchable(ctx) } }
    val target = (s.breakRemainingMs.toFloat() / Session.BREAK_MS).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, Motion.spec(), label = "ring")

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction }, modifier = Modifier.fillMaxSize(), strokeWidth = 14.dp,
                color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                strokeCap = StrokeCap.Round,
            )
            Text(fmt(s.breakRemainingMs), style = timerStyle(44.sp))
        }
        Text(
            "Focus " + fmt(s.focusRemainingMs), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp),
        )
        LazyVerticalGrid(
            GridCells.Adaptive(88.dp), Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(apps, key = { it.pkg }) { AppTile(it) } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickNoteButton { note = true }
            PressPill(onClick = { Session.endBreak() }, modifier = Modifier.weight(1f)) { PillLabel("Resume") }
        }
        TextButton(onClick = { giveUp = true }) { Text("End session") }
    }
    if (note) QuickNoteSheet { note = false }
    if (giveUp) {
        AlertDialog(
            onDismissRequest = { giveUp = false },
            title = { Text("End session?") },
            text = { Text("Logged as ended early.") },
            confirmButton = { TextButton(onClick = { giveUp = false; Session.giveUp() }) { Text("End") } },
            dismissButton = { TextButton(onClick = { giveUp = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CompletedScreen(s: SessionUi) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Done", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("${s.lastFocusedMs / 60_000}", style = timerStyle(120.sp), color = MaterialTheme.colorScheme.primary)
        Text("min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        PressPill(onClick = { Session.reset() }, modifier = Modifier.fillMaxWidth()) { PillLabel("Finish") }
    }
}
