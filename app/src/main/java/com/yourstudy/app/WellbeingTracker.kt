package com.yourstudy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Keeps productive focus time and break time on two separate scales, and counts doom-scroll episodes.
 * Fed by Session (phase changes), GuardService (foreground app, scrolls, blocked launches) and the screen state.
 * Main-thread only. Never touches the session itself, so a failure here can't end or reset one.
 */
object WellbeingTracker {
    private enum class Mode { NONE, FOCUS, BREAK }

    private val DOOM_APPS = setOf(
        "com.google.android.youtube", "com.instagram.android", "com.reddit.frontpage", "com.facebook.katana",
        "com.facebook.lite", "com.snapchat.android", "com.twitter.android", "com.zhiliaoapp.musically",
    )
    private const val COOLDOWN_MS = 60_000L
    private const val FLUSH_EVERY_MS = 15_000L

    private lateinit var app: Context
    private var mode = Mode.NONE
    private var lastTs = 0L
    private var segStart = 0L
    private var pkg: String? = null
    private var essential: Set<String> = emptySet()
    private var launcherPkg: String? = null
    private var screenOn = true

    /** Captured at session start so the link survives tag changes mid-session. */
    private var taskId: String? = null
    private var subjectId: String? = null

    private val scrolls = ArrayDeque<Long>()
    private var scrollPkg: String? = null
    private var lastEpisodeAt = -COOLDOWN_MS

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            flush()
            screenOn = i.action != Intent.ACTION_SCREEN_OFF
        }
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        screenOn = app.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        runCatching {
            ContextCompat.registerReceiver(
                app, screenReceiver,
                IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun tracking() = ::app.isInitialized && RemoteControl.config.value.feature("wellbeing_stats").enabled

    fun onStart(essentialPkgs: Set<String>) {
        taskId = FocusTag.taskId.value
        subjectId = FocusTag.subjectId.value
        if (!::app.isInitialized) return
        essential = essentialPkgs
        launcherPkg = runCatching { com.yourstudy.app.ui.realLauncher(app)?.packageName }.getOrNull()
        pkg = app.packageName
        val now = System.currentTimeMillis()
        lastTs = now; segStart = now
        mode = if (tracking()) Mode.FOCUS else Mode.NONE
    }

    fun onBreak(start: Boolean) {
        if (mode == Mode.NONE) return
        val now = System.currentTimeMillis()
        flush(now); closeSegment(now)
        mode = if (start) Mode.BREAK else Mode.FOCUS
        segStart = now
    }

    fun onFinish(focusedMs: Long, completed: Boolean) {
        val now = System.currentTimeMillis()
        if (mode != Mode.NONE) {
            flush(now); closeSegment(now)
            if (completed) WellbeingStore.update(WellbeingStore.dayKey(now)) { it.copy(sessions = it.sessions + 1) }
        }
        mode = Mode.NONE
        StudyPlanner.addLogged(taskId, (focusedMs / 60_000).toInt())
        taskId = null; subjectId = null
    }

    /** Called from Session.tick(); cheap. Persists progress so a killed process loses at most ~15 s. */
    fun tick() {
        if (mode != Mode.NONE && System.currentTimeMillis() - lastTs >= FLUSH_EVERY_MS) flush()
    }

    fun onForeground(p: String) {
        if (mode == Mode.NONE) return
        flush()
        pkg = p
    }

    fun onBlocked() {
        if (!tracking()) return
        WellbeingStore.update(WellbeingStore.dayKey()) { it.copy(attempts = it.attempts + 1) }
    }

    fun onScroll(p: String, nowElapsed: Long = SystemClock.elapsedRealtime()) {
        if (!tracking() || p !in DOOM_APPS) return
        val s = Session.ui.value
        if (s.phase == Phase.FOCUSING && p in s.essential) return // study context
        if (nowElapsed - lastEpisodeAt < COOLDOWN_MS) return
        val f = RemoteControl.config.value.feature("wellbeing_stats")
        val threshold = f.doomScrollThreshold.coerceIn(3, 100)
        val windowMs = f.doomScrollWindowSec.coerceIn(5, 600) * 1000L
        if (scrollPkg != p) { scrolls.clear(); scrollPkg = p }
        scrolls.addLast(nowElapsed)
        while (scrolls.isNotEmpty() && nowElapsed - scrolls.first() > windowMs) scrolls.removeFirst()
        if (scrolls.size >= threshold) {
            lastEpisodeAt = nowElapsed
            scrolls.clear()
            WellbeingStore.update(WellbeingStore.dayKey()) {
                it.copy(doomScrolls = it.doomScrolls + 1, doomApps = it.doomApps + (p to ((it.doomApps[p] ?: 0) + 1)))
            }
        }
    }

    private fun Map<String, Long>.plusMs(k: String, v: Long): Map<String, Long> = this + (k to ((this[k] ?: 0L) + v))

    private fun flush(now: Long = System.currentTimeMillis()) {
        if (mode == Mode.NONE) return
        val dt = (now - lastTs).coerceIn(0L, 5 * 60_000L) // ignore clock jumps
        lastTs = now
        if (dt == 0L) return
        val cur = pkg ?: app.packageName
        val own = cur == app.packageName
        WellbeingStore.update(WellbeingStore.dayKey(now - dt)) { d ->
            when (mode) {
                Mode.FOCUS ->
                    if (screenOn && (own || cur in essential)) d.copy(
                        productiveMs = d.productiveMs + dt,
                        subjectMs = subjectId?.let { d.subjectMs.plusMs(it, dt) } ?: d.subjectMs,
                        appFocusMs = d.appFocusMs.plusMs(cur, dt),
                    ) else d
                Mode.BREAK -> {
                    val wasted = screenOn && !own && cur != launcherPkg && cur !in essential
                    d.copy(
                        breakMs = d.breakMs + dt,
                        wastedMs = d.wastedMs + if (wasted) dt else 0L,
                        restMs = d.restMs + if (wasted) 0L else dt,
                        appWastedMs = if (wasted) d.appWastedMs.plusMs(cur, dt) else d.appWastedMs,
                    )
                }
                Mode.NONE -> d
            }
        }
    }

    private fun closeSegment(now: Long) {
        if (mode == Mode.NONE || now - segStart < 1000) return
        val b = Block(segStart, now, mode == Mode.FOCUS)
        WellbeingStore.update(WellbeingStore.dayKey(segStart)) { it.copy(blocks = (it.blocks + b).takeLast(400)) }
    }
}
