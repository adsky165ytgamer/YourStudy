package com.yourstudy.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yourstudy.app.ui.fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Quick Settings tile. Idle: tap starts a focus session (via MainActivity so permissions and
 * control.json gating are checked in one place). Focus: tap opens the app; the tile can never end a
 * session. Break: tap resumes focus early. The tile only updates while the panel is visible.
 */
class FocusTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch { Session.ui.map { it.phase }.distinctUntilChanged().collect { render() } }
            s.launch { while (isActive) { delay(1000); render() } }
        }
    }

    override fun onStopListening() {
        scope?.cancel(); scope = null
        super.onStopListening()
    }

    override fun onDestroy() { scope?.cancel(); scope = null; super.onDestroy() }

    private fun available(): Boolean {
        val rc = RemoteControl.config.value
        return rc.feature("quick_tile").enabled && rc.feature("focus_mode").let { it.enabled && !it.locked } && !rc.appLockdown.enabled
    }

    private fun render() {
        val tile = qsTile ?: return
        val u = Session.ui.value
        val (state, sub) = when {
            !available() -> Tile.STATE_UNAVAILABLE to "Off"
            u.phase == Phase.FOCUSING -> Tile.STATE_ACTIVE to fmt(u.focusRemainingMs)
            u.phase == Phase.BREAK_5M -> Tile.STATE_ACTIVE to fmt(u.breakRemainingMs)
            u.phase == Phase.COMPLETED -> Tile.STATE_INACTIVE to "Done"
            else -> Tile.STATE_INACTIVE to "${Store.minutes}m"
        }
        tile.state = state
        tile.label = if (u.phase == Phase.BREAK_5M) "Break" else getString(R.string.tile_label)
        tile.subtitle = sub
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!available()) return
        val act: () -> Unit = {
            when (Session.ui.value.phase) {
                Phase.BREAK_5M -> { Session.endBreak(); render() }
                Phase.IDLE -> openApp(autoStart = true)
                else -> openApp(autoStart = false)
            }
        }
        if (isLocked) unlockAndRun { act() } else act()
    }

    private fun openApp(autoStart: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (autoStart) intent
            .putExtra(FocusScheduler.EXTRA_TARGET_TAB, "focus")
            .putExtra(FocusScheduler.EXTRA_DURATION_MIN, Store.minutes)
            .putExtra(FocusScheduler.EXTRA_AUTO_START, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, if (autoStart) 1 else 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
