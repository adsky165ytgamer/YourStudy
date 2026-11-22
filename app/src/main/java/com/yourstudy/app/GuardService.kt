package com.yourstudy.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/** Enforces the allowlist during FOCUSING; does nothing in every other phase (incl. the break). */
class GuardService : AccessibilityService() {
    private var lastBounce = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val s = Session.ui.value
        if (s.phase != Phase.FOCUSING) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg in s.essential || pkg in alwaysAllowed()) return
        // Ignore popups/overlays of other windows; only real activity windows count.
        val cls = e.className?.toString().orEmpty()
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.")) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBounce < 500) return
        lastBounce = now
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
