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

    fun bluetoothOn(c: Context) = c.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true

    @Suppress("DEPRECATION")
    fun wifiOn(c: Context) = c.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.isWifiEnabled == true

    fun nearbyPerms(parent: Boolean): Array<String> {
        val l = mutableListOf(
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
        )
        if (Build.VERSION.SDK_INT >= 33) l += android.Manifest.permission.NEARBY_WIFI_DEVICES
        else l += android.Manifest.permission.ACCESS_FINE_LOCATION
        if (parent) l += android.Manifest.permission.CAMERA
        return l.toTypedArray()
    }

    fun hasAll(c: Context, p: Array<String>) =
        p.all { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}
