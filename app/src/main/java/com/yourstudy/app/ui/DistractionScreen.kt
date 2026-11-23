package com.yourstudy.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.AdminControl
import com.yourstudy.app.Perms
import com.yourstudy.app.Popups
import com.yourstudy.app.RemoteControl

/** Permission / protection status rows: Overlay, Accessibility, Device Admin. */
@Composable
fun DistractionScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val adminOn by AdminControl.active.collectAsStateWithLifecycle()
    val lockOn by AdminControl.lockOnBypass.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++; AdminControl.refresh(ctx) }
    BackHandler(onBack = onClose)

    val overlay = remember(refresh) { Perms.overlay(ctx) }
    val access = remember(refresh) { Perms.accessibility(ctx) }
    val f = cfg.feature("device_admin")
    val ui = cfg.ui("section_device_admin")

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(ui.label ?: "Distraction", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            (ui.badge ?: f.badge)?.let { Badge { Text(it) } }
        }
        StatusRow("Overlay", overlay, "On", "Off") {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        StatusRow("Accessibility", access, "On", "Off") { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (f.enabled) {
            StatusRow("Admin", adminOn, "Active", "Off") {
                when {
                    f.locked -> Popups.notice("Admin", f.lockReason)
                    adminOn -> if (!AdminControl.disableAdmin(ctx)) Popups.notice("Admin", "Locked during focus.")
                    else -> AdminControl.requestEnableAdmin(ctx)
                }
            }
            if (adminOn && f.allowAutoLockOnBypass && !f.locked) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Lock on bypass", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Switch(checked = lockOn, onCheckedChange = { AdminControl.setLockOnBypass(ctx, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, on: Boolean, onText: String, offText: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(shape = CircleShape, color = if (on) cs.primaryContainer else cs.errorContainer) {
                Text(
                    if (on) onText else offText,
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = if (on) cs.onPrimaryContainer else cs.onErrorContainer,
                )
            }
        }
    }
}
