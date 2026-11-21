package com.yourstudy.app

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

object Perms {
    fun overlay(c: Context) = Settings.canDrawOverlays(c)

    fun accessibility(c: Context): Boolean {
        val cn = ComponentName(c, GuardService::class.java)
        val enabled = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    fun home(c: Context) = c.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    fun notifications(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
