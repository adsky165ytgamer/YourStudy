package com.yourstudy.app.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.yourstudy.app.Perms
import com.yourstudy.app.Session
import com.yourstudy.app.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun HomeShell(onOpenNotes: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(Store.minutes) }
    var selected by remember { mutableStateOf(Store.essentials) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val overlay = remember(refresh) { Perms.overlay(ctx) }
    val access = remember(refresh) { Perms.accessibility(ctx) }
    val notif = remember(refresh) { Perms.notifications(ctx) }
    val ready = overlay && access

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val apps by produceState(emptyList<AppEntry>(), Unit) {
        value = withContext(Dispatchers.IO) { essentialCandidates(ctx) }
    }
    // Drop essentials that are no longer installed / allowed.
    val validSelected = if (apps.isEmpty()) selected else selected.filter { s -> apps.any { it.pkg == s } }.toSet()

    Scaffold(
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                if (tab == 0) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (!ready) {
                            Text(
                                "Grant the two required permissions to start.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        PressPill(
                            onClick = {
                                Store.minutes = minutes
                                Store.essentials = validSelected
                                Session.start(ctx, minutes, validSelected)
                            },
                            enabled = ready,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Start focus · $minutes min",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Focus") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Log") },
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Person, null) }, label = { Text("Family") },
                    )
                    NavigationBarItem(
                        selected = false, onClick = onOpenNotes,
                        icon = { Icon(Icons.Default.Create, null) }, label = { Text("Notes") },
                    )
                }
            }
        }
    ) { pad ->
        if (tab == 1) {
            LogTab(pad)
        } else if (tab == 2) {
            PairTab(pad)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = pad.calculateTopPadding() + 16.dp,
                    bottom = pad.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text("Your Study", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    Text(
                        "Set your focus time and pick the apps you truly need.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { TimerCard(minutes) { minutes = it } }
                item {
                    PermissionCard(
                        overlay, access, notif,
                        onOverlay = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            )
                        },
                        onAccess = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onNotif = {
                            if (Build.VERSION.SDK_INT >= 33)
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
                item {
                    Text(
                        "Essential apps · ${validSelected.size} selected",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Play Store, Settings and installers are never allowed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (apps.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    items(apps, key = { it.pkg }) { app ->
                        val on = app.pkg in validSelected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selected = if (on) validSelected - app.pkg else validSelected + app.pkg
                                    Store.essentials = selected
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, 44.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Switch(checked = on, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(minutes: Int, onMinutes: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var typing by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(40.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FOCUS TIME", style = MaterialTheme.typography.labelLarge)
            Text(
                fmt(minutes * 60_000L),
                style = timerStyle(),
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { typing = true }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text("tap the time to type minutes", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.padding(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressPill(onClick = { onMinutes((minutes - 5).coerceAtLeast(1)) }, height = 56.dp) {
                    Text("−", style = MaterialTheme.typography.headlineMedium)
                }
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = {
                        val m = it.roundToInt()
                        if (m != minutes) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMinutes(m)
                        }
                    },
                    valueRange = 1f..240f,
                    modifier = Modifier.weight(1f),
                )
                PressPill(onClick = { onMinutes((minutes + 5).coerceAtMost(240)) }, height = 56.dp) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
    if (typing) {
        var text by remember { mutableStateOf(minutes.toString()) }
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("Focus minutes") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("min") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { onMinutes(it.coerceIn(1, 240)) }
                    typing = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PermissionCard(
    overlay: Boolean, access: Boolean, notif: Boolean,
    onOverlay: () -> Unit, onAccess: () -> Unit, onNotif: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PermRow("Display over other apps", "Shows the floating countdown pill", overlay, onOverlay)
            PermRow("Accessibility service", "Keeps you inside your essential apps", access, onAccess)
            if (Build.VERSION.SDK_INT >= 33) PermRow("Notifications (optional)", "Shows the session notification", notif, onNotif)
        }
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, action: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ok) Icon(Icons.Default.CheckCircle, "Granted", tint = MaterialTheme.colorScheme.primary)
        else FilledTonalButton(onClick = action) { Text("Grant") }
    }
}
