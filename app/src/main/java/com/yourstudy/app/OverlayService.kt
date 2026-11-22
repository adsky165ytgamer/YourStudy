package com.yourstudy.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.yourstudy.app.ui.fmt
import com.yourstudy.app.ui.launchRealLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Owns the timer loop, the draggable floating countdown pill, and the auto-return to the launcher. */
class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var pill: TextView? = null
    private lateinit var wm: WindowManager
    private var lastPhase = Phase.IDLE

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 220 }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "Study session", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Your Study")
            .setContentText("Focus session in progress")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            ).build()
        ServiceCompat.startForeground(
            this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
        )
        wm = getSystemService(WindowManager::class.java)
        if (loop == null) {
            lastPhase = Session.ui.value.phase
            loop = scope.launch { while (isActive) { step(); delay(250) } }
        }
        return START_NOT_STICKY
    }

    private fun step() {
        Session.tick()
        val u = Session.ui.value
        val p = u.phase
        if (p == Phase.FOCUSING || p == Phase.BREAK_5M) {
            if (Session.appVisible) hidePill() else showPill(u)
        }
        val breakEnded = lastPhase == Phase.BREAK_5M && p == Phase.FOCUSING
        val justCompleted = p == Phase.COMPLETED && lastPhase != Phase.COMPLETED
        if (lastPhase == Phase.FOCUSING && p == Phase.BREAK_5M) { if (!launchRealLauncher(this)) openLauncher() }
        if (breakEnded || justCompleted) openLauncher()
        lastPhase = p
        if (p == Phase.IDLE || p == Phase.COMPLETED) { hidePill(); stopSelf() }
    }

    private fun showPill(u: SessionUi) {
        val brk = u.phase == Phase.BREAK_5M
        val tv = pill ?: createPill().also {
            try { wm.addView(it, lp); pill = it } catch (e: Exception) { return }
        }
        tv.text = (if (brk) "Break  " else "Focus  ") + fmt(if (brk) u.breakRemainingMs else u.focusRemainingMs)
        (tv.background as GradientDrawable).setColor(
            getColor(if (brk) android.R.color.system_accent3_200 else android.R.color.system_accent1_200)
        )
    }

    private fun hidePill() {
        pill?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        pill = null
    }

    private fun createPill() = TextView(this).apply {
        textSize = 20f
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        setTextColor(getColor(android.R.color.system_accent1_900))
        setPadding(dp(22), dp(12), dp(22), dp(12))
        background = GradientDrawable().apply { cornerRadius = 1000f }
        elevation = dp(6).toFloat()
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var ix = 0; var iy = 0; var sx = 0f; var sy = 0f; var moved = false
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = lp.x; iy = lp.y; sx = e.rawX; sy = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) { lp.x = ix + dx; lp.y = iy + dy; wm.updateViewLayout(v, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onPillTap(); true }
                else -> false
            }
        }
    }

    private fun onPillTap() {
        if (Session.ui.value.phase == Phase.BREAK_5M) Session.endBreak() // ends the break early
        openLauncher()
    }

    private fun openLauncher() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        hidePill()
        scope.cancel()
        super.onDestroy()
    }

    private companion object { const val CH = "session" }
}
