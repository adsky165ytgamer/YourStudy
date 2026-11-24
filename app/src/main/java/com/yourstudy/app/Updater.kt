package com.yourstudy.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    /** SHA-256 of the official `yourstudy` release signing certificate. */
    const val OFFICIAL_SIGNER_SHA256 = "1cd0da51dbb06bf33a1714490005eddb17ad0e43e199d009f85939c84716025c"

    fun installedVersionCode(ctx: Context): Long = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun download(ctx: Context, u: AppUpdateConfig, onProgress: (Float) -> Unit): File =
        download(ctx, u.apkUrl, u.sha256, u.latestVersionCode, onProgress)

    suspend fun download(
        ctx: Context,
        downloadUrl: String,
        expectedSha256: String?,
        expectedVersionCode: Long,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(downloadUrl.startsWith("https://")) { "Update URL must be https" }
        require(expectedVersionCode in 1L..Int.MAX_VALUE.toLong()) { "Invalid update version code" }
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "YourStudy-$expectedVersionCode.apk")
        val c = URL(downloadUrl).openConnection() as HttpURLConnection
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
            if (!expectedSha256.isNullOrBlank()) {
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(expectedSha256.trim(), ignoreCase = true)) { f.delete(); error("Checksum mismatch") }
            }
            val archive = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
                ?: error("Downloaded file is not a valid APK")
            check(archive.packageName == ctx.packageName) { "Downloaded APK belongs to a different application" }
            val downloadedCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else {
                @Suppress("DEPRECATION") archive.versionCode.toLong()
            }
            check(downloadedCode == expectedVersionCode) { "Downloaded APK version does not match MATH1 metadata" }
            check(downloadedCode > installedVersionCode(ctx)) { "Downloaded APK is not newer than the installed version" }
            verifySigner(ctx, f)
        } finally { c.disconnect() }
        f
    }

    /** Rejects (and deletes) any APK whose signing certificate is not the official release key. */
    private fun verifySigner(ctx: Context, f: File) {
        val signers = ctx.packageManager
            .getPackageArchiveInfo(f.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?.signingInfo?.apkContentsSigners
        val ok = signers != null && signers.any { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }.equals(OFFICIAL_SIGNER_SHA256, ignoreCase = true)
        }
        if (!ok) { f.delete(); error("APK is not signed with the official YourStudy key") }
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
