package com.yourstudy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.DayStats
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.StudyPlanner
import com.yourstudy.app.WellbeingStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Breakdown { SUBJECTS, PRODUCTIVE, WASTED }

internal fun durText(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun appLabel(ctx: android.content.Context, pkg: String): String =
    runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

@Composable
fun WellbeingScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val days by WellbeingStore.days.collectAsStateWithLifecycle()
    val subjects by StudyPlanner.subjects.collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(Breakdown.SUBJECTS) }
    BackHandler(onBack = onClose)

    val date = LocalDate.now().minusDays(offset.toLong())
    val d: DayStats = days[date.toString()] ?: DayStats()
    val ui = cfg.ui("section_wellbeing")
    val badge = ui.badge ?: cfg.feature("wellbeing_stats").badge
    val dateLabel = (if (offset == 0) "Today" else if (offset == 1) "Yesterday" else date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))) +
        " (" + date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())) + ")"

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(ui.label ?: "Well-Being", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            badge?.let { Badge { Text(it) } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { if (offset < 29) offset++ }, enabled = offset < 29) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                    }
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                    FilledTonalIconButton(onClick = { if (offset > 0) offset-- }, enabled = offset > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                    }
                }
            }
            item { DayRing(d, date) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val topSubject = d.subjectMs.maxByOrNull { it.value }?.key?.let { id -> subjects.firstOrNull { it.id == id }?.name }
                    Card(
                        Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Focus", style = MaterialTheme.typography.labelLarge)
                            Text("${d.productiveFocusMinutes}m", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Black)
                            Text("${d.sessions} done", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
                            topSubject?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    Card(
                        Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Break", style = MaterialTheme.typography.labelLarge)
                            Text("${d.breakTotalMinutes}m", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Black)
                            Text(
                                "Waste ${d.breakWastedScreenMinutes}m · Rest ${d.breakRestMinutes}m",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1,
                            )
                            val total = (d.wastedMs + d.restMs).coerceAtLeast(1L)
                            Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.onTertiaryContainer.copy(alpha = 0.15f))) {
                                if (d.wastedMs > 0) Box(Modifier.weight(d.wastedMs.toFloat() / total).fillMaxSize().background(cs.error))
                                if (d.restMs > 0) Box(Modifier.weight(d.restMs.toFloat() / total).fillMaxSize().background(cs.primary))
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val topDoom = d.doomApps.maxByOrNull { it.value }?.key
                    StatPill("Doom Scrolls", d.doomScrollCount.toString(), topDoom?.let { appLabel(ctx, it) }, Modifier.weight(1f))
                    StatPill("Blocked Exits", d.distractionAttempts.toString(), null, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == Breakdown.SUBJECTS, { tab = Breakdown.SUBJECTS }, { Text("Subjects") }, shape = CircleShape)
                    FilterChip(tab == Breakdown.PRODUCTIVE, { tab = Breakdown.PRODUCTIVE }, { Text("Productive Apps") }, shape = CircleShape)
                    FilterChip(tab == Breakdown.WASTED, { tab = Breakdown.WASTED }, { Text("Wasted Apps") }, shape = CircleShape)
                }
            }
            val rows: List<Triple<String, Long, String?>> = when (tab) {
                Breakdown.SUBJECTS -> d.subjectMs.map { (id, ms) -> Triple(subjects.firstOrNull { it.id == id }?.name ?: "Other", ms, null) }
                Breakdown.PRODUCTIVE -> d.appFocusMs.map { (p, ms) -> Triple(appLabel(ctx, p), ms, p) }
                Breakdown.WASTED -> d.appWastedMs.map { (p, ms) -> Triple(appLabel(ctx, p), ms, p) }
            }.sortedByDescending { it.second }
            if (rows.isEmpty()) item { EmptyState(Icons.Default.Check, "No data") }
            else {
                val max = rows.first().second.coerceAtLeast(1L)
                rows.forEachIndexed { i, (label, ms, pkg) ->
                    item(key = "${tab.name}$i") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (pkg != null) AppIcon(pkg, 36.dp) else Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(durText(ms), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                                }
                                LinearProgressIndicator(
                                    progress = { ms.toFloat() / max }, modifier = Modifier.fillMaxWidth(),
                                    color = if (tab == Breakdown.WASTED) cs.error else cs.primary, trackColor = cs.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, sub: String?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 24-hour ring: focus blocks vs break blocks placed at their clock position; productive minutes in the middle. */
@Composable
private fun DayRing(d: DayStats, date: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val focusColor = cs.primary
    val breakColor = cs.tertiary
    val track = cs.surfaceContainerHighest
    val dayStart = remember(date) { date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 22.dp.toPx()
                val arc = Size(this.size.width - sw, this.size.height - sw)
                val tl = Offset(sw / 2, sw / 2)
                drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
                d.blocks.forEach { b ->
                    val a = ((b.start - dayStart) / 86_400_000f).coerceIn(0f, 1f)
                    val z = ((b.end - dayStart) / 86_400_000f).coerceIn(0f, 1f)
                    val sweep = ((z - a) * 360f).coerceAtLeast(2f)
                    drawArc(if (b.focus) focusColor else breakColor, -90f + a * 360f, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Butt))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.productiveFocusMinutes}", style = timerStyle(56.sp))
                Text("min", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}
