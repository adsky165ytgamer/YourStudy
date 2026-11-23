package com.yourstudy.app.ui

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Single motion token used for every container, tab and press animation. */
object Motion {
    fun <T> spec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)
}

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFB9A8FF), onPrimary = Color(0xFF2A1A66),
    primaryContainer = Color(0xFF41308A), onPrimaryContainer = Color(0xFFE6DEFF),
    surface = Color(0xFF14121C), background = Color(0xFF14121C),
)
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF5B45C9), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF1B0A5E),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun YourStudyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
