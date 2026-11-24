package com.yourstudy.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.FocusMusic
import com.yourstudy.app.RemoteControl

/**
 * Full-screen backdrop: the current track's cover, blurred once by the GPU and dimmed by a scrim,
 * with content colour forced to white so timers and controls stay legible on any artwork.
 * With no cover it is transparent and costs nothing.
 */
@Composable
fun CoverBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val music by FocusMusic.ui.collectAsStateWithLifecycle()
    val cover = music.cover
    val dim by animateFloatAsState(if (cover != null) 1f else 0f, Motion.spec(), label = "dim")
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = cover, animationSpec = Motion.spec(), label = "cover") { c ->
            if (c != null) Image(
                c, null, Modifier.fillMaxSize().blur(18.dp, BlurredEdgeTreatment.Rectangle), contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = dim }
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.75f))))
        )
        CompositionLocalProvider(LocalContentColor provides if (cover != null) Color.White else LocalContentColor.current) {
            content()
        }
    }
}

private enum class Glyph { Play, Pause, Prev, Next }

@Composable
private fun TransportButton(glyph: Glyph, description: String, onClick: () -> Unit, dimen: Dp = 48.dp) {
    val src = remember { MutableInteractionSource() }
    val color = LocalContentColor.current
    Box(
        Modifier.size(dimen).pressScale(src).clip(CircleShape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(dimen * 0.42f)) {
            val w = this.size.width
            val h = this.size.height
            when (glyph) {
                Glyph.Play -> drawPath(Path().apply { moveTo(w * 0.15f, 0f); lineTo(w, h / 2); lineTo(w * 0.15f, h); close() }, color)
                Glyph.Pause -> {
                    drawRoundRect(color, Offset(w * 0.12f, 0f), Size(w * 0.3f, h), CornerRadius(w * 0.08f))
                    drawRoundRect(color, Offset(w * 0.58f, 0f), Size(w * 0.3f, h), CornerRadius(w * 0.08f))
                }
                Glyph.Prev -> {
                    drawRoundRect(color, Offset(0f, 0f), Size(w * 0.16f, h), CornerRadius(w * 0.05f))
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.28f, h / 2); lineTo(w, h); close() }, color)
                }
                Glyph.Next -> {
                    drawRoundRect(color, Offset(w * 0.84f, 0f), Size(w * 0.16f, h), CornerRadius(w * 0.05f))
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.72f, h / 2); lineTo(0f, h); close() }, color)
                }
            }
        }
    }
}

/** Compact player: title, artist, previous / play-pause / next. Hidden when music is off or empty. */
@Composable
fun MusicBar(modifier: Modifier = Modifier) {
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val m by FocusMusic.ui.collectAsStateWithLifecycle()
    val f = cfg.feature("focus_music")
    AnimatedVisibility(
        visible = f.enabled && !f.locked && m.queueSize > 0,
        modifier = modifier,
        enter = fadeIn(Motion.spec()) + expandVertically(Motion.spec()),
        exit = fadeOut(Motion.spec()) + shrinkVertically(Motion.spec()),
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.Black.copy(alpha = 0.35f), contentColor = Color.White) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.title ?: "Music", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = m.error ?: m.artist
                    if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.75f))
                }
                if (m.queueSize > 1) TransportButton(Glyph.Prev, "Previous track", { FocusMusic.previous() })
                AnimatedContent(
                    targetState = m.playing,
                    transitionSpec = { (fadeIn(Motion.spec()) + scaleIn(Motion.spec(), 0.7f)) togetherWith (fadeOut(Motion.spec()) + scaleOut(Motion.spec(), 0.7f)) },
                    label = "playPause",
                ) { playing ->
                    TransportButton(if (playing) Glyph.Pause else Glyph.Play, if (playing) "Pause" else "Play", { FocusMusic.toggle() }, 56.dp)
                }
                if (m.queueSize > 1) TransportButton(Glyph.Next, "Next track", { FocusMusic.next() })
            }
        }
    }
}

/** Setup-screen row: choose tracks, auto-play with focus, preview. Follows focus_music / section_focus_music. */
@Composable
fun MusicRow() {
    val ctx = LocalContext.current
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val m by FocusMusic.ui.collectAsStateWithLifecycle()
    val f = cfg.feature("focus_music")
    val ui = cfg.ui("section_focus_music")
    if (!f.enabled || !ui.visible) return
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) FocusMusic.setQueue(ctx, uris)
    }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.spec()),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(ui.label ?: "Focus music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (m.queueSize == 0) "No track" else m.title ?: "${m.queueSize} tracks",
                        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                (ui.badge ?: f.badge)?.let { Badge { Text(it) } }
                TextButton(
                    enabled = ui.enabled,
                    onClick = { requestFeature(cfg, "focus_music", "section_focus_music", "Music") { picker.launch(arrayOf("audio/*")) } },
                ) { Text(if (m.queueSize == 0) "Choose" else "Change") }
                if (m.queueSize > 0) IconButton(onClick = { FocusMusic.clear(ctx) }) { Icon(Icons.Default.Close, "Remove music") }
            }
            if (m.queueSize > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Play with focus", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = m.autoPlay, onCheckedChange = { FocusMusic.setAutoPlay(it) })
                }
                MusicBar(Modifier.fillMaxWidth())
            }
        }
    }
}
