package com.yourstudy.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/** Enforces the allowlist during FOCUSING; does nothing in every other phase (incl. the break). Also feeds Well-Being. */
class GuardService : AccessibilityService() {
    private var lastBounce = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> { WellbeingTracker.onScroll(pkg); return }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> Unit
            else -> return
        }
        // Ignore popups/overlays of other windows; only real activity windows count.
        val cls = e.className?.toString().orEmpty()
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.")) return
        val always = alwaysAllowed()
        if (pkg !in always) WellbeingTracker.onForeground(pkg)

        val s = Session.ui.value
        if (s.phase != Phase.FOCUSING) return
        val rc = RemoteControl.config.value
        if (!rc.feature("guard_service").enabled || !rc.feature("focus_mode").strictGuard) return
        if (pkg == packageName || pkg in s.essential || pkg in always) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBounce < 500) return
        lastBounce = now
        WellbeingTracker.onBlocked()
        AdminControl.onBlockedAttempt(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun alwaysAllowed(): Set<String> {
        val ime = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        return (ime + listOf(
            "android", "com.android.systemui",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        )).toSet()
    }

    override fun onInterrupt() = Unit
}
