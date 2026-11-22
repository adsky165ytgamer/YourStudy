package com.yourstudy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import com.yourstudy.app.Session
import com.yourstudy.app.SessionUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FocusScreen(s: SessionUi, onOpenNotes: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppEntry>(), s.essential) {
        value = withContext(Dispatchers.IO) { loadLaunchable(ctx).filter { it.pkg in s.essential } }
    }
    val fraction = if (s.plannedMs > 0) (s.focusRemainingMs.toFloat() / s.plannedMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FOCUSING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 16.dp,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                strokeCap = StrokeCap.Round,
            )
            Text(fmt(s.focusRemainingMs), style = timerStyle(56.sp))
        }
        Spacer(Modifier.height(16.dp))
        if (apps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text(
                    "No essential apps selected.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(88.dp), Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { items(apps, key = { it.pkg }) { AppTile(it) } }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onOpenNotes, modifier = Modifier.fillMaxWidth()) { Text("Voice notes") }
        Spacer(Modifier.height(8.dp))
        HoldPill(
            label = if (s.breakUsed) "Break used" else "Hold to take a 5-min break",
            enabled = !s.breakUsed,
            onTrigger = { Session.startBreak() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun BreakScreen(s: SessionUi, onOpenNotes: () -> Unit) {
    val ctx = LocalContext.current
    var giveUp by remember { mutableStateOf(false) }
    val apps by produceState(emptyList<AppEntry>(), Unit) {
        value = withContext(Dispatchers.IO) { loadLaunchable(ctx) }
    }
    val fraction = (s.breakRemainingMs.toFloat() / Session.BREAK_MS).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BREAK · FULL ACCESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                strokeCap = StrokeCap.Round,
            )
            Text(fmt(s.breakRemainingMs), style = timerStyle(44.sp))
        }
        Text(
            "Focus resumes automatically · ${fmt(s.focusRemainingMs)} left",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        LazyVerticalGrid(
            GridCells.Adaptive(88.dp), Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(apps, key = { it.pkg }) { AppTile(it) } }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onOpenNotes, modifier = Modifier.fillMaxWidth()) { Text("Voice notes") }
        Spacer(Modifier.height(8.dp))
        PressPill(onClick = { Session.endBreak() }, modifier = Modifier.fillMaxWidth()) {
            Text("End break & resume focus", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { giveUp = true }) { Text("Give up this session") }
    }

    if (giveUp) {
        AlertDialog(
            onDismissRequest = { giveUp = false },
            title = { Text("Give up this session?") },
            text = { Text("It will be logged as ended early and all restrictions stop.") },
            confirmButton = { TextButton(onClick = { giveUp = false; Session.giveUp() }) { Text("Give up") } },
            dismissButton = { TextButton(onClick = { giveUp = false }) { Text("Keep going") } },
        )
    }
}

@Composable
fun CompletedScreen(s: SessionUi) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Session complete", style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("${s.lastFocusedMs / 60_000}", style = timerStyle(120.sp), color = MaterialTheme.colorScheme.primary)
        Text("minutes focused", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (s.breakUsed) "You used your 5-minute break." else "No break needed. Nice.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        PressPill(onClick = { Session.reset() }, modifier = Modifier.fillMaxWidth()) {
            Text("Finish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
