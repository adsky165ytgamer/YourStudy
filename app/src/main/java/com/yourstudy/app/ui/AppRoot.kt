package com.yourstudy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.Phase
import com.yourstudy.app.Session

@Composable
fun AppRoot() {
    val s by Session.ui.collectAsStateWithLifecycle()
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = s.phase == Phase.FOCUSING || s.phase == Phase.BREAK_5M) { /* no escape */ }
    Surface(color = MaterialTheme.colorScheme.background) {
        if (notesOpen) {
            VoiceNotesScreen(
                duringFocus = s.phase == Phase.FOCUSING || s.phase == Phase.BREAK_5M,
                onClose = { notesOpen = false },
            )
        } else {
            Crossfade(targetState = s.phase, animationSpec = spring(), label = "phase") { phase ->
                when (phase) {
                    Phase.IDLE -> HomeShell(onOpenNotes = { notesOpen = true })
                    Phase.FOCUSING -> FocusScreen(s, onOpenNotes = { notesOpen = true })
                    Phase.BREAK_5M -> BreakScreen(s, onOpenNotes = { notesOpen = true })
                    Phase.COMPLETED -> CompletedScreen(s)
                }
            }
        }
    }
}
