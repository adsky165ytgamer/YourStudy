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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Hold 1200 ms to fire once. Early release cancels and the fill springs back. */
@Composable
fun HoldPill(label: String, enabled: Boolean, onTrigger: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .height(64.dp)
            .clip(CircleShape)
            .background(if (enabled) cs.tertiaryContainer else cs.onSurface.copy(alpha = 0.12f))
            .drawBehind { if (enabled) drawRect(cs.tertiary.copy(alpha = 0.55f), size = Size(size.width * progress.value, size.height)) }
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
                    scope.launch { progress.animateTo(0f, spring(0.85f, 380f)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            label, style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (enabled) cs.onTertiaryContainer else cs.onSurface.copy(alpha = 0.38f),
        )
    }
}
