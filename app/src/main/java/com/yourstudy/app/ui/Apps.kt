package com.yourstudy.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class AppEntry(val pkg: String, val label: String)

/** Escape hatches that can never be allowlisted. */
val BLOCKED_ESSENTIALS = setOf(
    "com.android.vending",
    "com.android.settings",
    "com.google.android.packageinstaller",
    "com.android.packageinstaller",
    "com.samsung.android.packageinstaller",
    "com.google.android.permissioncontroller",
    "com.android.permissioncontroller",
)

/** Real launchable apps straight from PackageManager. */
fun loadLaunchable(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val list = if (Build.VERSION.SDK_INT >= 33)
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
    return list.map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun essentialCandidates(ctx: Context): List<AppEntry> =
    loadLaunchable(ctx).filter { it.pkg !in BLOCKED_ESSENTIALS && it.pkg != ctx.packageName }

fun launchApp(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?.let { ctx.startActivity(it) }
}
