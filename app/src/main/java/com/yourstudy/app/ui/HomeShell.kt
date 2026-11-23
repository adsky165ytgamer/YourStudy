package com.yourstudy.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.FocusScheduler
import com.yourstudy.app.FocusTag
import com.yourstudy.app.PlannerLaunch
import com.yourstudy.app.StudyPlanner
import com.yourstudy.app.Perms
import com.yourstudy.app.ScheduleLaunch
import com.yourstudy.app.Popups
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.Screens
import com.yourstudy.app.Session
import com.yourstudy.app.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun HomeShell() {
    val ctx = LocalContext.current
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    var scheduleOpen by rememberSaveable { mutableStateOf(false) }
    var plannerOpen by rememberSaveable { mutableStateOf(false) }
    var wellbeingOpen by rememberSaveable { mutableStateOf(false) }
    var distractionOpen by rememberSaveable { mutableStateOf(false) }
    var tagPicker by remember { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableIntStateOf(Store.minutes) }
    var selected by remember { mutableStateOf(Store.essentials) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val overlay = remember(refresh) { Perms.overlay(ctx) }
    val access = remember(refresh) { Perms.accessibility(ctx) }
    val notif = remember(refresh) { Perms.notifications(ctx) }
    val ready = overlay && access
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val focusCtl = cfg.feature("focus_mode")
    val startCtl = cfg.ui("btn_start_focus")
    val famCtl = cfg.feature("family_pairing")
    val range = RemoteControl.minutesRange()
    val lo = range.first
    val hi = range.last
    LaunchedEffect(lo, hi) { minutes = minutes.coerceIn(lo, hi) }

    val shown = if (tab == 3 && !famCtl.enabled) 0 else tab
    LaunchedEffect(shown, voiceOpen, scheduleOpen, plannerOpen, wellbeingOpen, distractionOpen) {
        Screens.current.value = when {
            voiceOpen -> "voice_notes"
            scheduleOpen -> "schedule"
            plannerOpen -> "planner"
            wellbeingOpen -> "wellbeing"
            distractionOpen -> "distraction"
            shown == 1 -> "tools"
            shown == 2 -> "log"
            shown == 3 -> "family"
            else -> "home"
        }
    }

    val apps by produceState(emptyList<AppEntry>(), Unit) { value = withContext(Dispatchers.IO) { essentialCandidates(ctx) } }
    val valid = if (apps.isEmpty()) selected else selected.filter { s -> apps.any { it.pkg == s } }.toSet()

    // Scheduled launch: land on Focus, apply the duration, auto-start only when everything allows it.
    val launchReq by ScheduleLaunch.request.collectAsStateWithLifecycle()
    LaunchedEffect(launchReq) {
        val r = launchReq ?: return@LaunchedEffect
        ScheduleLaunch.request.value = null
        tab = 0; voiceOpen = false; scheduleOpen = false; plannerOpen = false; wellbeingOpen = false; distractionOpen = false
        r.subjectId?.let { FocusTag.selectSubject(it) }
        val m = r.minutes.coerceIn(lo, hi)
        minutes = m
        val allowed = r.autoStart && ready && focusCtl.enabled && !focusCtl.locked &&
            !cfg.appLockdown.enabled && startCtl.interceptPopupId == null
        if (allowed) {
            Store.minutes = m
            Store.essentials = valid
            Session.start(ctx, m, valid)
        }
    }

    // Reminder tap: land on Focus with that task preselected.
    val plannerReq by PlannerLaunch.taskId.collectAsStateWithLifecycle()
    LaunchedEffect(plannerReq) {
        val id = plannerReq ?: return@LaunchedEffect
        PlannerLaunch.taskId.value = null
        StudyPlanner.tasks.value.firstOrNull { it.id == id }?.let { t ->
            FocusTag.selectTask(t); minutes = t.targetMinutes.coerceIn(lo, hi)
        }
        tab = 0; voiceOpen = false; scheduleOpen = false; plannerOpen = false; wellbeingOpen = false; distractionOpen = false
    }

    val tagTask by FocusTag.taskId.collectAsStateWithLifecycle()
    val tagSubject by FocusTag.subjectId.collectAsStateWithLifecycle()
    val planTasks by StudyPlanner.tasks.collectAsStateWithLifecycle()
    val planSubjects by StudyPlanner.subjects.collectAsStateWithLifecycle()
    val tagLabel = planTasks.firstOrNull { it.id == tagTask }?.title
        ?: planSubjects.firstOrNull { it.id == tagSubject }?.name ?: "No tag"
    val plannerOn = cfg.feature("study_planner").enabled && cfg.ui("section_study_planner").visible

    if (plannerOpen) {
        StudyPlannerScreen(
            onClose = { plannerOpen = false },
            onFocus = { t -> FocusTag.selectTask(t); minutes = t.targetMinutes.coerceIn(lo, hi); plannerOpen = false; tab = 0 },
        )
        return
    }
    if (wellbeingOpen) { WellbeingScreen(onClose = { wellbeingOpen = false }); return }
    if (distractionOpen) { DistractionScreen(onClose = { distractionOpen = false }); return }
    if (voiceOpen) {
        VoiceNotesScreen(duringFocus = false, onClose = { voiceOpen = false })
        return
    }
    if (scheduleOpen) {
        ScheduleScreen(onClose = { scheduleOpen = false })
        return
    }
    val schedules by FocusScheduler.schedules.collectAsStateWithLifecycle()
    val nextUp = remember(schedules, refresh, cfg) {
        if (cfg.feature("schedule").enabled) FocusScheduler.nextUpLabel() else null
    }

    Scaffold(
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                if (shown == 0 && startCtl.visible) {
                    PressPill(
                        onClick = {
                            val intercept = startCtl.interceptPopupId
                            if (intercept != null) Popups.show(intercept)
                            else if (!focusCtl.enabled || focusCtl.locked) Popups.notice("Focus", focusCtl.lockReason)
                            else {
                                Store.minutes = minutes
                                Store.essentials = valid
                                Session.start(ctx, minutes, valid)
                            }
                        },
                        enabled = ready && startCtl.enabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        PillLabel((startCtl.label ?: "Start") + " · ${minutes}m" + (startCtl.badge?.let { " · $it" } ?: ""))
                    }
                }
                NavigationBar {
                    NavigationBarItem(shown == 0, { tab = 0 }, { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Focus") })
                    NavigationBarItem(shown == 1, { tab = 1 }, { Icon(Icons.Default.Build, null) }, label = { Text("Tools") })
                    NavigationBarItem(shown == 2, { tab = 2 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Log") })
                    if (famCtl.enabled) NavigationBarItem(
                        selected = shown == 3,
                        onClick = { if (famCtl.locked) Popups.notice("Family", famCtl.lockReason) else tab = 3 },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("Family") },
                    )
                }
            }
        }
    ) { pad ->
        AnimatedContent(
            targetState = shown,
            transitionSpec = { fadeIn(Motion.spec()) togetherWith fadeOut(Motion.spec()) },
            label = "tab",
        ) { t ->
            when (t) {
                0 -> FocusTab(
                    pad, minutes, lo, hi, { minutes = it },
                    overlay, access, notif,
                    onOverlay = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) },
                    onAccess = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onNotif = { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    apps = apps, selected = valid,
                    next = nextUp, onSchedule = { requestSchedule(cfg) { scheduleOpen = true } },
                    tag = tagLabel, onTag = if (plannerOn) ({ tagPicker = true }) else null,
                    onToggle = { pkg ->
                        selected = if (pkg in valid) valid - pkg else valid + pkg
                        Store.essentials = selected
                    },
                )
                1 -> ToolsTab(
                    pad,
                    onVoice = { requestVoice(cfg, false) { voiceOpen = true } },
                    onSchedule = { requestSchedule(cfg) { scheduleOpen = true } },
                    onPlanner = { requestFeature(cfg, "study_planner", "section_study_planner", "Planner") { plannerOpen = true } },
                    onWellbeing = { requestFeature(cfg, "wellbeing_stats", "section_wellbeing", "Well-Being") { wellbeingOpen = true } },
                    onDistraction = { requestFeature(cfg, "device_admin", "section_device_admin", "Distraction") { distractionOpen = true } },
                )
                2 -> LogTab(pad)
                else -> PairTab(pad)
            }
        }
    }

    if (tagPicker) {
        TagPickerSheet(
            onDismiss = { tagPicker = false },
            onTask = { t -> FocusTag.selectTask(t); minutes = t.targetMinutes.coerceIn(lo, hi) },
        )
    }
}

@Composable
private fun FocusTab(
    pad: PaddingValues, minutes: Int, lo: Int, hi: Int, onMinutes: (Int) -> Unit,
    overlay: Boolean, access: Boolean, notif: Boolean,
    onOverlay: () -> Unit, onAccess: () -> Unit, onNotif: () -> Unit,
    apps: List<AppEntry>, selected: Set<String>, onToggle: (String) -> Unit,
    next: String?, onSchedule: () -> Unit,
    tag: String, onTag: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    var typing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    val progress by animateFloatAsState(if (hi > lo) (minutes - lo).toFloat() / (hi - lo) else 1f, Motion.spec(), label = "dur")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, pad.calculateTopPadding() + 16.dp, 20.dp, pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 14.dp,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                        Text(
                            fmt(minutes * 60_000L), style = timerStyle(52.sp),
                            modifier = Modifier.clip(CircleShape).clickable { typing = true }.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 45, 60).filter { it in lo..hi }.forEach { p ->
                            FilterChip(minutes == p, { onMinutes(p) }, { Text("${p}m") }, shape = CircleShape)
                        }
                    }
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = {
                            val m = it.roundToInt()
                            if (m != minutes) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMinutes(m) }
                        },
                        valueRange = lo.toFloat()..hi.toFloat(),
                    )
                }
            }
        }
        onTag?.let { open ->
            item {
                AssistChip(onClick = open, label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Default.Create, null) }, shape = CircleShape)
            }
        }
        next?.let { label ->
            item {
                AssistChip(onClick = onSchedule, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.DateRange, null) }, shape = CircleShape)
            }
        }
        if (!overlay || !access) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!overlay) AssistChip(onClick = onOverlay, label = { Text("Overlay") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, shape = CircleShape)
                if (!access) AssistChip(onClick = onAccess, label = { Text("Accessibility") }, leadingIcon = { Icon(Icons.Default.Warning, null) }, shape = CircleShape)
                if (!notif && Build.VERSION.SDK_INT >= 33) AssistChip(onClick = onNotif, label = { Text("Alerts") }, shape = CircleShape)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Badge { Text("${selected.size}") }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, shape = CircleShape,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
            )
        }
        when {
            apps.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() } }
            filtered.isEmpty() -> item { EmptyState(Icons.Default.Search, "No match") }
            else -> items(filtered, key = { it.pkg }) { app ->
                val on = app.pkg in selected
                val src = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(src)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            androidx.compose.animation.animateColorAsState(
                                if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                Motion.spec(), label = "sel",
                            ).value
                        )
                        .clickable(interactionSource = src, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle(app.pkg)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.pkg, 44.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Switch(checked = on, onCheckedChange = null)
                }
            }
        }
    }

    if (typing) {
        var text by remember { mutableStateOf(minutes.toString()) }
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("Minutes") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(4) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), suffix = { Text("min") },
                )
            },
            confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let { onMinutes(it.coerceIn(lo, hi)) }; typing = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToolsTab(
    pad: PaddingValues, onVoice: () -> Unit, onSchedule: () -> Unit,
    onPlanner: () -> Unit, onWellbeing: () -> Unit, onDistraction: () -> Unit,
) {
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val vUi = cfg.ui("section_voice_notes")
    val vF = cfg.feature("voice_notes")
    val sUi = cfg.ui("section_schedule")
    val sF = cfg.feature("schedule")
    val voiceShown = vUi.visible && vF.enabled
    val scheduleShown = sUi.visible && sF.enabled
    val pUi = cfg.ui("section_study_planner"); val pF = cfg.feature("study_planner")
    val wUi = cfg.ui("section_wellbeing"); val wF = cfg.feature("wellbeing_stats")
    val dUi = cfg.ui("section_device_admin"); val dF = cfg.feature("device_admin")
    val plannerShown = pUi.visible && pF.enabled
    val wellbeingShown = wUi.visible && wF.enabled
    val distractionShown = dUi.visible && dF.enabled
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp, pad.calculateTopPadding() + 16.dp, 20.dp, pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tools", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        if (!voiceShown && !scheduleShown && !plannerShown && !wellbeingShown && !distractionShown) EmptyState(Icons.Default.Build, "No tools")
        if (voiceShown) ToolCard(Icons.Default.Create, vUi.label ?: "Voice Notes", vUi.badge ?: vF.badge, vF.locked, vUi.enabled, onVoice)
        if (scheduleShown) ToolCard(Icons.Default.DateRange, sUi.label ?: "Schedule", sUi.badge ?: sF.badge, sF.locked, sUi.enabled, onSchedule)
        if (plannerShown) ToolCard(Icons.Default.Check, pUi.label ?: "Planner", pUi.badge ?: pF.badge, pF.locked, pUi.enabled, onPlanner)
        if (wellbeingShown) ToolCard(Icons.Default.Favorite, wUi.label ?: "Well-Being", wUi.badge ?: wF.badge, wF.locked, wUi.enabled, onWellbeing)
        if (distractionShown) ToolCard(Icons.Default.Warning, dUi.label ?: "Distraction", dUi.badge ?: dF.badge, dF.locked, dUi.enabled, onDistraction)
    }
}

@Composable
private fun ToolCard(icon: ImageVector, label: String, badge: String?, locked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(32.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            badge?.let { Badge { Text(it) } }
            if (locked) Icon(Icons.Default.Lock, "Locked")
        }
    }
}
