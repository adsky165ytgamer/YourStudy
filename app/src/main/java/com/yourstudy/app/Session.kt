package com.yourstudy.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { IDLE, FOCUSING, BREAK_5M, COMPLETED }

data class SessionUi(
    val phase: Phase = Phase.IDLE,
    val plannedMs: Long = 0,
    val focusRemainingMs: Long = 0,
    val breakRemainingMs: Long = 0,
    val breakUsed: Boolean = false,
    val essential: Set<String> = emptySet(),
    val lastFocusedMs: Long = 0,
)

/**
 * Single source of truth. All timing uses the monotonic SystemClock.elapsedRealtime(),
 * so wall-clock changes can never shift a timer. Mutated on the main thread only.
 */
object Session {
    const val BREAK_MS = 5 * 60_000L

    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    /** True while MainActivity is on screen (the overlay pill hides itself then). */
    @Volatile var appVisible = false

    private var focusEndAt = 0L
    private var breakEndAt = 0L
    private var pausedFocusRemainingMs = 0L
    private var startedWall = 0L
    val sessionStartedAt: Long get() = startedWall

    fun start(ctx: Context, minutes: Int, packages: Set<String>) {
        val ms = minutes * 60_000L
        focusEndAt = SystemClock.elapsedRealtime() + ms
        startedWall = System.currentTimeMillis()
        _ui.value = SessionUi(Phase.FOCUSING, ms, ms, 0, false, packages)
        WellbeingTracker.onStart(packages)
        ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
    }

    fun startBreak() {
        val u = _ui.value
        if (u.phase != Phase.FOCUSING || u.breakUsed) return
        val now = SystemClock.elapsedRealtime()
        pausedFocusRemainingMs = (focusEndAt - now).coerceAtLeast(0)
        breakEndAt = now + BREAK_MS
        WellbeingTracker.onBreak(true)
        _ui.value = u.copy(
            phase = Phase.BREAK_5M, breakUsed = true,
            breakRemainingMs = BREAK_MS, focusRemainingMs = pausedFocusRemainingMs,
        )
    }

    fun endBreak() {
        val u = _ui.value
        if (u.phase != Phase.BREAK_5M) return
        focusEndAt = SystemClock.elapsedRealtime() + pausedFocusRemainingMs
        WellbeingTracker.onBreak(false)
        _ui.value = u.copy(
            phase = Phase.FOCUSING, breakRemainingMs = 0, focusRemainingMs = pausedFocusRemainingMs,
        )
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

    fun reset() { _ui.value = SessionUi() }

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
    }
}
