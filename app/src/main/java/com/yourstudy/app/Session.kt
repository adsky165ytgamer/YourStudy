package com.yourstudy.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** BREAK_5M is the break phase for any configured length (name kept for source compatibility). */
enum class Phase { IDLE, FOCUSING, BREAK_5M, COMPLETED }

data class SessionUi(
    val phase: Phase = Phase.IDLE,
    val plannedMs: Long = 0,
    val focusRemainingMs: Long = 0,
    val breakRemainingMs: Long = 0,
    val breakUsed: Boolean = false,
    val essential: Set<String> = emptySet(),
    val lastFocusedMs: Long = 0,
    val breakTotalMs: Long = Session.DEFAULT_BREAK_MS,
)

/**
 * Single source of truth. All timing uses the monotonic SystemClock.elapsedRealtime(),
 * so wall-clock changes can never shift a timer. Mutated on the main thread only.
 * An active session is snapshotted so it survives process death (see [restore]).
 */
object Session {
    const val DEFAULT_BREAK_MS = 5 * 60_000L
    const val BREAK_MS = DEFAULT_BREAK_MS

    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    /** True while MainActivity is on screen (the overlay pill hides itself then). */
    @Volatile var appVisible = false

    private var focusEndAt = 0L
    private var breakEndAt = 0L
    private var pausedFocusRemainingMs = 0L
    private var startedWall = 0L
    val sessionStartedAt: Long get() = startedWall

    fun start(ctx: Context, minutes: Int, packages: Set<String>, breakMinutes: Int = Store.breakMinutes) {
        val ms = minutes * 60_000L
        val breakMs = breakMinutes.coerceIn(RemoteControl.breakRange()) * 60_000L
        focusEndAt = SystemClock.elapsedRealtime() + ms
        startedWall = System.currentTimeMillis()
        _ui.value = SessionUi(Phase.FOCUSING, ms, ms, 0, false, packages, breakTotalMs = breakMs)
        WellbeingTracker.onStart(packages)
        persist()
        ensureService(ctx)
    }

    fun startBreak() {
        val u = _ui.value
        if (u.phase != Phase.FOCUSING || u.breakUsed) return
        val now = SystemClock.elapsedRealtime()
        pausedFocusRemainingMs = (focusEndAt - now).coerceAtLeast(0)
        breakEndAt = now + u.breakTotalMs
        WellbeingTracker.onBreak(true)
        _ui.value = u.copy(
            phase = Phase.BREAK_5M, breakUsed = true,
            breakRemainingMs = u.breakTotalMs, focusRemainingMs = pausedFocusRemainingMs,
        )
        persist()
    }

    fun endBreak() {
        val u = _ui.value
        if (u.phase != Phase.BREAK_5M) return
        focusEndAt = SystemClock.elapsedRealtime() + pausedFocusRemainingMs
        WellbeingTracker.onBreak(false)
        _ui.value = u.copy(
            phase = Phase.FOCUSING, breakRemainingMs = 0, focusRemainingMs = pausedFocusRemainingMs,
        )
        persist()
    }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        WellbeingTracker.tick()
        when (_ui.value.phase) {
            Phase.FOCUSING -> {
                val rem = focusEndAt - now
                if (rem <= 0) finish(completed = true) else _ui.update { it.copy(focusRemainingMs = rem) }
            }
            Phase.BREAK_5M -> {
                val rem = breakEndAt - now
                if (rem <= 0) endBreak() else _ui.update { it.copy(breakRemainingMs = rem) }
            }
            else -> Unit
        }
    }

    /** Safety valve, only reachable from the break screen. Logged as ended early. */
    fun giveUp() {
        if (_ui.value.phase == Phase.IDLE || _ui.value.phase == Phase.COMPLETED) return
        finish(completed = false)
    }

    fun reset() { _ui.value = SessionUi(); persist() }

    /** Starts the timer service if a session is active. Safe to call repeatedly; never throws. */
    fun ensureService(ctx: Context) {
        val p = _ui.value.phase
        if (p != Phase.FOCUSING && p != Phase.BREAK_5M) return
        // Background starts can be refused (ForegroundServiceStartNotAllowedException); the next
        // MainActivity.onResume retries while the app is visible.
        runCatching { ctx.startForegroundService(Intent(ctx, OverlayService::class.java)) }
    }

    private fun finish(completed: Boolean) {
        val u = _ui.value
        val remaining = if (u.phase == Phase.FOCUSING)
            (focusEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) else pausedFocusRemainingMs
        val focused = (u.plannedMs - remaining).coerceIn(0, u.plannedMs)
        val entry = LogEntry(startedWall, (u.plannedMs / 60_000).toInt(), focused, u.breakUsed, completed)
        Store.addLog(entry)
        WellbeingTracker.onFinish(focused, completed)
        Pairing.onLog(entry)
        _ui.value = if (completed)
            u.copy(phase = Phase.COMPLETED, focusRemainingMs = 0, breakRemainingMs = 0, lastFocusedMs = focused)
        else SessionUi()
        persist()
    }

    // ------------------------------------------------------------ process-death safety

    private fun persist() {
        val u = _ui.value
        if (u.phase != Phase.FOCUSING && u.phase != Phase.BREAK_5M) { Store.sessionSnapshot = null; return }
        val nowE = SystemClock.elapsedRealtime()
        val nowW = System.currentTimeMillis()
        val activeEndE = if (u.phase == Phase.FOCUSING) focusEndAt else breakEndAt
        Store.sessionSnapshot = JSONObject()
            .put("phase", u.phase.name).put("planned", u.plannedMs).put("breakTotal", u.breakTotalMs)
            .put("breakUsed", u.breakUsed).put("essential", JSONArray(u.essential.toList()))
            .put("focusEndE", focusEndAt).put("breakEndE", breakEndAt).put("paused", pausedFocusRemainingMs)
            .put("savedE", nowE).put("endW", nowW + (activeEndE - nowE)).put("startedWall", startedWall)
            .toString()
    }

    /**
     * Rebuilds an active session after the process was killed. If the device did not reboot the saved
     * monotonic deadlines are still valid; after a reboot the monotonic clock restarted, so the wall-clock
     * deadline is converted instead. An already-expired session is finalised immediately.
     */
    fun restore(ctx: Context) {
        if (_ui.value.phase != Phase.IDLE) return
        val raw = Store.sessionSnapshot ?: return
        val o = runCatching { JSONObject(raw) }.getOrNull()
        val phase = o?.let { runCatching { Phase.valueOf(it.getString("phase")) }.getOrNull() }
        if (o == null || (phase != Phase.FOCUSING && phase != Phase.BREAK_5M)) { Store.sessionSnapshot = null; return }

        val nowE = SystemClock.elapsedRealtime()
        val rebooted = nowE < o.getLong("savedE")
        val activeEndE = if (rebooted) nowE + (o.getLong("endW") - System.currentTimeMillis())
        else if (phase == Phase.FOCUSING) o.getLong("focusEndE") else o.getLong("breakEndE")
        pausedFocusRemainingMs = o.getLong("paused")
        startedWall = o.getLong("startedWall")
        if (phase == Phase.FOCUSING) focusEndAt = activeEndE else breakEndAt = activeEndE
        val essential = o.getJSONArray("essential").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        _ui.value = SessionUi(
            phase = phase, plannedMs = o.getLong("planned"),
            focusRemainingMs = if (phase == Phase.FOCUSING) (focusEndAt - nowE).coerceAtLeast(0) else pausedFocusRemainingMs,
            breakRemainingMs = if (phase == Phase.BREAK_5M) (breakEndAt - nowE).coerceAtLeast(0) else 0,
            breakUsed = o.getBoolean("breakUsed"), essential = essential, breakTotalMs = o.getLong("breakTotal"),
        )
        WellbeingTracker.onStart(essential)
        if (phase == Phase.BREAK_5M) WellbeingTracker.onBreak(true)
        tick() // finalises an expired session right away so the guard never blocks on a dead timer
        ensureService(ctx)
    }
}
