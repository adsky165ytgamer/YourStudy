package com.yourstudy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.yourstudy.app.Phase
import com.yourstudy.app.Math1UpdateClient
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.ScheduleLaunch
import com.yourstudy.app.Screens
import com.yourstudy.app.Session
import kotlinx.coroutines.delay

@Composable
fun AppRoot() {
    val s by Session.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BackHandler(enabled = s.phase == Phase.FOCUSING || s.phase == Phase.BREAK_5M) { /* no escape */ }
    // A schedule never touches a running or just-finished session.
    val req by ScheduleLaunch.request.collectAsStateWithLifecycle()
    LaunchedEffect(req, s.phase) { if (req != null && s.phase != Phase.IDLE) ScheduleLaunch.request.value = null }

    // No throttle: pull control.json every 30 s while the app is on screen.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                RemoteControl.refresh()
                Math1UpdateClient.refresh(ctx)
                delay(30_000)
            }
        }
    }
    LaunchedEffect(s.phase) {
        when (s.phase) {
            Phase.FOCUSING -> Screens.current.value = "focus"
            Phase.BREAK_5M -> Screens.current.value = "break"
            Phase.COMPLETED -> Screens.current.value = "completed"
            Phase.IDLE -> Unit // HomeShell sets the tab screen on entry
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = s.phase,
            transitionSpec = { fadeIn(Motion.spec()) togetherWith fadeOut(Motion.spec()) },
            label = "phase",
        ) { phase ->
            when (phase) {
                Phase.IDLE -> HomeShell()
                Phase.FOCUSING -> FocusScreen(s)
                Phase.BREAK_5M -> BreakScreen(s)
                Phase.COMPLETED -> CompletedScreen(s)
            }
        }
        ControlHost(s.phase)
    }
}
