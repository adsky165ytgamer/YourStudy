package com.yourstudy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.Phase
import com.yourstudy.app.Session

@Composable
fun AppRoot() {
    val s by Session.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = s.phase == Phase.FOCUSING || s.phase == Phase.BREAK_5M) { /* no escape */ }
    Surface(color = MaterialTheme.colorScheme.background) {
        Crossfade(targetState = s.phase, animationSpec = spring(), label = "phase") { phase ->
            when (phase) {
                Phase.IDLE -> HomeShell()
                Phase.FOCUSING -> FocusScreen(s)
                Phase.BREAK_5M -> BreakScreen(s)
                Phase.COMPLETED -> CompletedScreen(s)
            }
        }
    }
}
