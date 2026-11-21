package com.yourstudy.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Deterministic hold: fills 0 -> 100% over 1200 ms; early release cancels; completing fires once. */
@Composable
fun HoldPill(label: String, enabled: Boolean, onTrigger: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val track = if (enabled) cs.tertiaryContainer else cs.onSurface.copy(alpha = 0.12f)
    val fill = cs.tertiary.copy(alpha = 0.55f)
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(track)
            .drawBehind { if (enabled) drawRect(fill, size = Size(size.width * progress.value, size.height)) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(1200, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTrigger()
                    }
                    waitForUpOrCancellation()
                    if (job.isActive) job.cancel()
                    scope.launch { progress.animateTo(0f, spring()) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = if (enabled) cs.onTertiaryContainer else cs.onSurface.copy(alpha = 0.38f),
        )
    }
}
