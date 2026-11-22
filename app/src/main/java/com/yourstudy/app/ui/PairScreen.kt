package com.yourstudy.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import com.yourstudy.app.Role
import com.yourstudy.app.Store

@Composable
private fun Big(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    PressPill(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var minutes by remember { mutableIntStateOf(Store.minutes) }
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Family", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text(
                "Pair a parent and a child phone directly. No account, no server.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Role.SELF_STUDY to "Self study", Role.CHILD to "Child", Role.PARENT to "Parent").forEach { (r, label) ->
                    FilterChip(
                        selected = role == r, enabled = unpaired,
                        onClick = { role = r; Store.role = r; scanning = false; Pairing.cancel() },
                        label = { Text(label) },
                    )
                }
            }
            if (!unpaired) Text("Unpair to change role.", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if (role != Role.SELF_STUDY && (!bt || !wifi)) item {
            Card(shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Turn on Bluetooth and Wi-Fi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Pairing needs both. Nearby does not switch them on for you.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!bt) FilledTonalButton(onClick = {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                        }) { Text("Bluetooth") }
                        if (!wifi) FilledTonalButton(onClick = {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        }) { Text("Wi-Fi") }
                    }
                }
            }
        }

        if (role == Role.SELF_STUDY) item {
            Text("Personal mode: no paired phone needed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (role == Role.CHILD) {
            when (s.phase) {
                PairPhase.UNPAIRED -> item { Big("Pair with parent") { gated { Pairing.startChildPairing() } } }
                PairPhase.ADVERTISING -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val bmp = remember(s.qr) { s.qr?.let { qrBitmap(it) } }
                        bmp?.let {
                            Image(it, "Pairing QR code", Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)).background(Color.White))
                        }
                        TextButton(onClick = { Pairing.cancel() }) { Text("Cancel") }
                    }
                }
                PairPhase.PAIRED_LOCAL -> {
                    item { StatusCard(s.peerName, s.connected) }
                    item { Big("Reconnect to parent") { gated { Pairing.startChildReconnect() } } }
                    item { TextButton(onClick = { confirmUnpair = true }) { Text("Unpair") } }
                }
                else -> Unit
            }
        }

        if (role == Role.PARENT) {
            when (s.phase) {
                PairPhase.UNPAIRED -> {
                    item { Big(if (scanning) "Scanning…" else "Scan child's QR code") { gated { scanning = true } } }
                    if (scanning) item {
                        QrScanner(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(28.dp))) {
                            scanning = false; Pairing.startParentScan(it)
                        }
                    }
                }
                PairPhase.SCANNING, PairPhase.HANDSHAKE_VERIFYING ->
                    item { TextButton(onClick = { Pairing.cancel() }) { Text("Cancel") } }
                PairPhase.PAIRED_LOCAL -> {
                    item { StatusCard(s.peerName, s.connected) }
                    if (s.connected) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PressPill(onClick = { minutes = (minutes - 5).coerceAtLeast(1) }, height = 56.dp) { Text("−", style = MaterialTheme.typography.headlineMedium) }
                                Text(fmt(minutes * 60_000L), style = timerStyle(36.sp), modifier = Modifier.weight(1f))
                                PressPill(onClick = { minutes = (minutes + 5).coerceAtMost(240) }, height = 56.dp) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                            }
                        }
                        item { Big("Start session on child") { Store.minutes = minutes; Pairing.sendStart(minutes) } }
                        item { TextButton(onClick = { Pairing.sendRules(minutes) }) { Text("Send as child's default time") } }
                    } else item { Big("Reconnect to child") { gated { Pairing.startParentReconnect() } } }
                    item { Text("Child's sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (peerLogs.isEmpty()) item {
                        Text("Nothing synced yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(peerLogs.take(30)) { e ->
                        Card(shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${e.focusedMs / 60_000} / ${e.plannedMin} min", fontWeight = FontWeight.Bold)
                                Text(if (e.completed) "Completed" else "Ended early")
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
            title = { Text("Unpair this phone?") },
            text = { Text("The saved pairing and synced logs are removed. You will need to scan a new QR code to pair again.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; Pairing.unpair() }) { Text("Unpair") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusCard(name: String?, connected: Boolean) {
    Card(shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp)) {
            Text(name ?: "Paired device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (connected) "Connected" else "Paired · not nearby right now")
        }
    }
}
