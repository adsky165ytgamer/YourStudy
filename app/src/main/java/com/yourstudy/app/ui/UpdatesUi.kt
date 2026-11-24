package com.yourstudy.app.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.FocusTileService
import com.yourstudy.app.Math1CheckState
import com.yourstudy.app.Math1UpdateClient
import com.yourstudy.app.Popups
import com.yourstudy.app.R
import com.yourstudy.app.RemoteControl
import com.yourstudy.app.Updater

/** Manual "Check for Updates": bypasses the 6 h background cooldown. Results show on the card; an update opens the normal dialog. */
@Composable
fun UpdatesCard() {
    val ctx = LocalContext.current
    val state by Math1UpdateClient.state.collectAsStateWithLifecycle()
    val installed = remember { Updater.installedVersionCode(ctx) }
    val name = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    val checking = state is Math1CheckState.Checking
    val status = when (val st = state) {
        Math1CheckState.Idle -> "v$name"
        Math1CheckState.Checking -> "Checking"
        is Math1CheckState.Success ->
            st.stable?.takeIf { it.versionCode.toLong() > installed }?.let { "v${it.versionName} available" } ?: "Up to date · v$name"
        is Math1CheckState.Failure -> if (st.retryAfterSeconds != null) "Retry in ${st.retryAfterSeconds}s" else "Check failed"
    }
    ToolCard(Icons.Default.Refresh, "Updates", status, false, !checking) {
        Popups.updateDismissed.value = false // let a found update show even if "Later" was tapped before
        Math1UpdateClient.checkNowAsync(ctx)
    }
}

/** Offers to add the Quick Settings tile (Android 13+); older versions add it from the panel's edit mode. */
@Composable
fun QuickTileCard() {
    val ctx = LocalContext.current
    val cfg by RemoteControl.config.collectAsStateWithLifecycle()
    val f = cfg.feature("quick_tile")
    if (!f.enabled || Build.VERSION.SDK_INT < 33) return
    ToolCard(Icons.Default.Add, "Quick tile", f.badge, f.locked, true) {
        if (f.locked) Popups.notice("Quick tile", f.lockReason)
        else ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(ctx, FocusTileService::class.java), "Focus",
            AndroidIcon.createWithResource(ctx, R.drawable.ic_tile), ctx.mainExecutor,
        ) { }
    }
}
