package com.yourstudy.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Direct APK self-update. Updates only install if signed with the same keystore as the installed build. */
object Updater {
    fun installedVersionCode(ctx: Context): Long =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode }.getOrDefault(0L)

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun download(ctx: Context, u: AppUpdateConfig, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(u.apkUrl.startsWith("https://")) { "Update URL must be https" }
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "YourStudy-${u.latestVersionCode}.apk")
        val c = URL(u.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
        try {
            check(c.responseCode == 200) { "Server returned ${c.responseCode}" }
            val total = c.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { i ->
                f.outputStream().use { o ->
                    val buf = ByteArray(32_768); var read = 0L
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
            if (u.sha256.isNotBlank()) {
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(u.sha256.trim(), ignoreCase = true)) { f.delete(); error("Checksum mismatch") }
            }
        } finally { c.disconnect() }
        f
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
