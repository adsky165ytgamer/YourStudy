package com.yourstudy.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun fmt(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** Heavy display type with tabular figures so digits never jitter. */
@Composable
fun timerStyle(size: TextUnit = 88.sp): TextStyle =
    MaterialTheme.typography.displayLarge.copy(
        fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum",
    )

/** Pill that springs smaller and morphs toward a softer rounded rectangle while pressed. */
@Composable
fun PressPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    height: Dp = 72.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    body: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.45f, 380f), label = "scale")
    val corner by animateDpAsState(if (pressed) 18.dp else height / 2, spring(0.6f, 420f), label = "corner")
    val haptic = LocalHapticFeedback.current
    val bg = if (enabled) containerColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fg = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .combinedClickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
            )
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { body() }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    bmp?.let { Image(it, null, Modifier.size(size)) } ?: Spacer(Modifier.size(size))
}

@Composable
fun AppTile(app: AppEntry) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { launchApp(ctx, app.pkg) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.pkg, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
        )
    }
}
