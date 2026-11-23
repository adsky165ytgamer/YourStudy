package com.yourstudy.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.PairPhase
import com.yourstudy.app.Pairing
import com.yourstudy.app.Perms
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.Role
import com.yourstudy.app.Store

@Composable
private fun Big(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    PressPill(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { PillLabel(text) }
}

@Composable
private fun StatusCard(name: String?, connected: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Person, null, Modifier.size(32.dp))
            Column {
                Text(name ?: "Paired", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (connected) "Connected" else "Offline", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun PairTab(pad: PaddingValues) {
    val ctx = LocalContext.current
    val s by Pairing.ui.collectAsStateWithLifecycle()
    val peerLogs by Store.peerLogs.collectAsStateWithLifecycle()
    var role by remember { mutableStateOf(Store.role) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    var scanning by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val range = RemoteControl.minutesRange()
    var minutes by remember { mutableIntStateOf(Store.minutes.coerceIn(range)) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val needed = Perms.nearbyPerms(role == Role.PARENT)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        refresh++
        if (r.values.all { it }) pending?.invoke()
        pending = null
    }
    val bt = remember(refresh) { Perms.bluetoothOn(ctx) }
    val wifi = remember(refresh) { Perms.wifiOn(ctx) }
    fun gated(action: () -> Unit) {
        if (!Perms.hasAll(ctx, needed)) { pending = action; launcher.launch(needed) }
        else if (bt && wifi) action()
    }
    val unpaired = s.phase == PairPhase.UNPAIRED

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, pad.calculateTopPadding() + 16.dp, 20.dp, pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Family", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Role.SELF_STUDY to "Solo", Role.CHILD to "Child", Role.PARENT to "Parent").forEach { (r, label) ->
                    FilterChip(
                        selected = role == r, enabled = unpaired, shape = CircleShape, label = { Text(label) },
                        onClick = { role = r; Store.role = r; scanning = false; Pairing.cancel() },
                    )
                }
            }
        }
        if (role != Role.SELF_STUDY && (!bt || !wifi)) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!bt) AssistChip(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    label = { Text("Bluetooth off") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, shape = CircleShape,
                )
                if (!wifi) AssistChip(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    label = { Text("Wi-Fi off") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, shape = CircleShape,
                )
            }
        }

        if (role == Role.CHILD) {
            when (s.phase) {
                PairPhase.UNPAIRED -> item { Big("Pair") { gated { Pairing.startChildPairing() } } }
                PairPhase.ADVERTISING -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val bmp = remember(s.qr) { s.qr?.let { qrBitmap(it) } }
                        bmp?.let { Image(it, "QR code", Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)) }
                        TextButton(onClick = { Pairing.cancel() }) { Text("Cancel") }
                    }
                }
                PairPhase.PAIRED_LOCAL -> {
                    item { StatusCard(s.peerName, s.connected) }
                    item { Big("Reconnect") { gated { Pairing.startChildReconnect() } } }
                    item { TextButton(onClick = { confirmUnpair = true }) { Text("Unpair") } }
                }
                else -> Unit
            }
        }

        if (role == Role.PARENT) {
            when (s.phase) {
                PairPhase.UNPAIRED -> {
                    item { Big(if (scanning) "Scanning" else "Scan", enabled = !scanning) { gated { scanning = true } } }
                    if (scanning) item {
                        QrScanner(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(28.dp))) {
                            scanning = false; Pairing.startParentScan(it)
                        }
                    }
                }
                PairPhase.SCANNING, PairPhase.HANDSHAKE_VERIFYING -> item { TextButton(onClick = { Pairing.cancel() }) { Text("Cancel") } }
                PairPhase.PAIRED_LOCAL -> {
                    item { StatusCard(s.peerName, s.connected) }
                    if (s.connected) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PressPill(
                                    onClick = { minutes = (minutes - 5).coerceAtLeast(range.first) }, height = 52.dp,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                                Text(fmt(minutes * 60_000L), Modifier.weight(1f), style = timerStyle(36.sp))
                                PressPill(
                                    onClick = { minutes = (minutes + 5).coerceAtMost(range.last) }, height = 52.dp,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                        item { Big("Start on child") { Store.minutes = minutes; Pairing.sendStart(minutes) } }
                        item { TextButton(onClick = { Pairing.sendRules(minutes) }) { Text("Set default time") } }
                    } else item { Big("Reconnect") { gated { Pairing.startParentReconnect() } } }
                    items(peerLogs.take(20)) { e ->
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${e.focusedMs / 60_000}/${e.plannedMin}m", fontWeight = FontWeight.Bold)
                                Text(if (e.completed) "Done" else "Early")
                            }
                        }
                    }
                    item { TextButton(onClick = { confirmUnpair = true }) { Text("Unpair") } }
                }
                else -> Unit
            }
        }

        if (s.message.isNotEmpty()) item {
            Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = { Text("Pairing and synced logs are removed.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; Pairing.unpair() }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}
