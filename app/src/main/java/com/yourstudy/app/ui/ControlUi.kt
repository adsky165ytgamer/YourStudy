package com.yourstudy.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.AppUpdateConfig
import com.yourstudy.app.Phase
import com.yourstudy.app.PopupConfig
import com.yourstudy.app.Popups
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.RemoteControlConfig
import com.yourstudy.app.Screens
import com.yourstudy.app.Store
import com.yourstudy.app.Updater
import kotlinx.coroutines.launch

/** Gate for every Voice Notes entry point (tools tile, quick note). */
fun requestVoice(cfg: RemoteControlConfig, duringFocus: Boolean, onOpen: () -> Unit) {
    val ui = cfg.ui("section_voice_notes")
    val f = cfg.feature("voice_notes")
    val intercept = ui.interceptPopupId
    when {
        intercept != null -> Popups.show(intercept)
        !f.enabled || f.locked -> Popups.notice("Voice Notes", f.lockReason)
        duringFocus && !f.allowDuringFocus -> Popups.notice("Voice Notes", "Unavailable during focus.")
        else -> onOpen()
    }
}

/** Root host: app lockdown, update prompt, lock notices, trigger popups. Never traps a running session. */
@Composable
fun ControlHost(phase: Phase) {
    val ctx = LocalContext.current
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val screen by Screens.current.collectAsStateWithLifecycle()
    val requested by Popups.requested.collectAsStateWithLifecycle()
    val launchSeen by Popups.launchSeen.collectAsStateWithLifecycle()
    val screenSeen by Popups.screenSeen.collectAsStateWithLifecycle()
    val updateDismissed by Popups.updateDismissed.collectAsStateWithLifecycle()
    LaunchedEffect(screen) { Popups.screenSeen.value = emptySet() }

    val idle = phase == Phase.IDLE || phase == Phase.COMPLETED

    val lock = cfg.appLockdown
    if (lock.enabled && idle) {
        AlertDialog(onDismissRequest = {}, title = { Text(lock.title) }, text = { Text(lock.message) }, confirmButton = {})
        return
    }

    val installed = remember { Updater.installedVersionCode(ctx) }
    val u = cfg.appUpdate
    val needs = u.enabled && u.latestVersionCode > installed
    val force = needs && (installed < u.minSupportedVersionCode || u.forceUpdate)
    if (needs && idle && (force || !updateDismissed)) {
        UpdateDialog(u, force) { Popups.updateDismissed.value = true }
        return
    }

    fun close(p: PopupConfig) {
        if (p.showOnce) Store.markPopupShown(p.id)
        Popups.launchSeen.value = Popups.launchSeen.value + p.id
        Popups.screenSeen.value = Popups.screenSeen.value + p.id
        if (Popups.requested.value?.id == p.id) Popups.requested.value = null
    }
    fun act(p: PopupConfig) {
        when (p.primaryAction) {
            "open_url" -> p.actionTarget?.takeIf { it.startsWith("http") }?.let {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            "trigger_update" -> Popups.updateDismissed.value = false
        }
    }

    val popup = requested ?: cfg.popups.firstOrNull { p ->
        p.enabled && !(p.showOnce && Store.popupShown(p.id)) && when (p.trigger) {
            "on_launch" -> p.id !in launchSeen && (p.targetScreen == "global" || p.targetScreen == screen)
            "on_screen_enter" -> p.id !in screenSeen && p.targetScreen == screen
            else -> false
        }
    }
    popup?.let { p ->
        AlertDialog(
            onDismissRequest = { if (p.dismissible) close(p) },
            title = { Text(p.title) },
            text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { act(p); close(p) }) { Text(p.primaryLabel) } },
            dismissButton = if (p.dismissible && p.primaryAction != "dismiss") {
                { TextButton(onClick = { close(p) }) { Text("Close") } }
            } else null,
        )
    }
}

@Composable
private fun UpdateDialog(u: AppUpdateConfig, force: Boolean, onLater: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!force && !busy) onLater() },
        title = { Text("Update" + if (u.versionName.isNotBlank()) " ${u.versionName}" else "") },
        text = {
            Column {
                if (u.changelog.isNotBlank()) Text(u.changelog)
                if (force) Text("Required.", modifier = Modifier.padding(top = 8.dp))
                if (busy) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                error = null
                if (!Updater.canInstall(ctx)) {
                    error = "Allow installs, then retry."
                    Updater.openInstallSettings(ctx)
                } else {
                    busy = true
                    scope.launch {
                        runCatching { Updater.download(ctx, u) { progress = it } }
                            .onSuccess { busy = false; Updater.install(ctx, it) }
                            .onFailure { busy = false; error = "Download failed: ${it.message}" }
                    }
                }
            }) { Text("Update") }
        },
        dismissButton = if (force) null else { { TextButton(enabled = !busy, onClick = onLater) { Text("Later") } } },
    )
}

fun requestSchedule(cfg: RemoteControlConfig, onOpen: () -> Unit) {
    val ui = cfg.ui("section_schedule")
    val f = cfg.feature("schedule")
    val intercept = ui.interceptPopupId
    when {
        intercept != null -> Popups.show(intercept)
        !f.enabled || f.locked -> Popups.notice("Schedule", f.lockReason)
        else -> onOpen()
    }
}
