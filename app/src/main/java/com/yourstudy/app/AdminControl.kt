package com.yourstudy.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device Admin wrapper: uninstall friction + optional screen lock on repeated bypass. Every call is permission-safe. */
object AdminControl {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    private val _lockOnBypass = MutableStateFlow(false)
    val lockOnBypass: StateFlow<Boolean> = _lockOnBypass.asStateFlow()

    private val attempts = ArrayDeque<Long>()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("yourstudy_admin", Context.MODE_PRIVATE)
    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun component(c: Context) = ComponentName(c.applicationContext, YourStudyAdminReceiver::class.java)

    fun init(c: Context) {
        _lockOnBypass.value = prefs(c).getBoolean("lock_on_bypass", false)
        refresh(c)
    }

    fun isAdminActive(c: Context): Boolean = runCatching { dpm(c).isAdminActive(component(c)) }.getOrDefault(false)

    fun refresh(c: Context) { _active.value = isAdminActive(c) }

    fun setLockOnBypass(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("lock_on_bypass", on).apply()
        _lockOnBypass.value = on
    }

    fun requestEnableAdmin(c: Context) {
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Blocks uninstall during study. Locks the screen on repeated bypass.")
        if (c !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }
    }

    /** Refused while a strict session runs, so protection can't be dropped mid-focus. */
    fun disableAdmin(c: Context): Boolean {
        val phase = Session.ui.value.phase
        val strict = RemoteControl.config.value.feature("focus_mode").strictGuard
        if (strict && (phase == Phase.FOCUSING || phase == Phase.BREAK_5M)) return false
        runCatching { dpm(c).removeActiveAdmin(component(c)) }
        refresh(c)
        return true
    }

    fun lockScreenNowIfPermitted(c: Context): Boolean {
        val f = RemoteControl.config.value.feature("device_admin")
        if (!f.enabled || f.locked || !f.allowAutoLockOnBypass) return false
        if (!_lockOnBypass.value || !isAdminActive(c)) return false
        return runCatching { dpm(c).lockNow() }.isSuccess
    }

    /** 3 blocked launches within 30 s during a strict focus session -> lock the screen. */
    fun onBlockedAttempt(c: Context) {
        val now = SystemClock.elapsedRealtime()
        attempts.addLast(now)
        while (attempts.isNotEmpty() && now - attempts.first() > 30_000) attempts.removeFirst()
        if (attempts.size >= 3) {
            attempts.clear()
            lockScreenNowIfPermitted(c)
        }
    }
}
