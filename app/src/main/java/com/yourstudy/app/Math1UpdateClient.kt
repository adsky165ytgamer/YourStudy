package com.yourstudy.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * MATH1 live-release checker. MATH1 only discovers public GitHub release metadata;
 * APK download, checksum verification, and installation remain local responsibilities.
 */
data class Math1Release(
    val versionName: String,
    val versionCode: Int,
    val vVersion: String?,
    val releaseNotes: String,
    val downloadUrl: String,
    val sha256: String?,
    val sizeBytes: Long?,
    val publishedAt: String?,
)

sealed interface Math1CheckState {
    data object Idle : Math1CheckState
    data object Checking : Math1CheckState
    data class Success(val stable: Math1Release?, val beta: Math1Release?) : Math1CheckState
    data class Failure(
        val httpStatus: Int,
        val code: String,
        val message: String,
        val retryAfterSeconds: Long? = null,
    ) : Math1CheckState
}

object Math1UpdateClient {
    const val BASE_URL = "https://math1.adsky165ytgamer.workers.dev"
    const val CHECK_URL = "$BASE_URL/api/v1/update/check"
    const val TARGET_REPOSITORY = "adsky165ytgamer/YourStudy"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MIN_RETRY_INTERVAL_MS = 6 * 60 * 60 * 1_000L

    private val _state = MutableStateFlow<Math1CheckState>(Math1CheckState.Idle)
    val state: StateFlow<Math1CheckState> = _state.asStateFlow()
    private var lastAttemptAt = 0L
    private var checking = false

    suspend fun refresh(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (checking || (!force && now - lastAttemptAt < MIN_RETRY_INTERVAL_MS)) return
        checking = true
        lastAttemptAt = now
        _state.value = Math1CheckState.Checking
        try {
            _state.value = check(ctx)
        } finally {
            checking = false
        }
    }

    private suspend fun check(ctx: Context): Math1CheckState = withContext(Dispatchers.IO) {
        val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val installed = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
        require(installed in 0L..Int.MAX_VALUE.toLong()) { "Installed version code is outside MATH1's supported range." }
        require(Build.VERSION.SDK_INT in 1..100) { "Device SDK is outside MATH1's supported range." }

        val body = JSONObject()
            .put("repo", TARGET_REPOSITORY)
            .put("versionCode", installed.toInt())
            .put("deviceSdk", Build.VERSION.SDK_INT)
            .apply { packageInfo.versionName?.takeIf { it.isNotBlank() }?.let { put("versionName", it) } }
            .toString()

        val connection = (URL(CHECK_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseText = runCatching {
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }.getOrDefault("")
            if (status !in 200..299) return@withContext parseFailure(status, connection.getHeaderField("Retry-After"), responseText)
            parseSuccess(responseText)
        } catch (e: Exception) {
            Math1CheckState.Failure(0, "NETWORK_ERROR", e.message ?: "Could not reach the MATH1 update service.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFailure(status: Int, retryAfterHeader: String?, raw: String): Math1CheckState {
        val error = runCatching { JSONObject(raw).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code")?.takeIf { it.isNotBlank() } ?: "HTTP_$status"
        val message = error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "MATH1 update check failed (HTTP $status)."
        val retryAfter = if (status == 503) retryAfterHeader?.toLongOrNull()?.coerceAtLeast(1L) ?: 60L else null
        return Math1CheckState.Failure(status, code, message, retryAfter)
    }

    private fun parseSuccess(raw: String): Math1CheckState {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return Math1CheckState.Failure(200, "INVALID_RESPONSE", "MATH1 returned invalid JSON.")
        }
        if (!root.optBoolean("success", false)) {
            val error = root.optJSONObject("error")
            return Math1CheckState.Failure(
                200,
                error?.optString("code") ?: "INVALID_RESPONSE",
                error?.optString("message") ?: "MATH1 returned an unsuccessful response.",
            )
        }
        return Math1CheckState.Success(parseRelease(root.optJSONObject("stable")), parseRelease(root.optJSONObject("beta")))
    }

    private fun parseRelease(value: JSONObject?): Math1Release? {
        if (value == null || !value.optBoolean("updateAvailable", false)) return null
        val versionName = value.optString("versionName").takeIf { it.isNotBlank() } ?: return null
        val downloadUrl = value.optString("downloadUrl").takeIf { it.startsWith("https://") } ?: return null
        val versionCode = value.optInt("versionCode", -1)
        if (versionCode < 0) return null
        val notes = value.optString("releaseNotes", "")
            .replace(Regex("<[^>]*>"), "")
            .trim()
            .take(2000)
        return Math1Release(
            versionName = versionName,
            versionCode = versionCode,
            vVersion = value.optString("vVersion").takeIf { it.isNotBlank() },
            releaseNotes = notes,
            downloadUrl = downloadUrl,
            sha256 = value.optString("sha256").takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) },
            sizeBytes = if (value.has("sizeBytes") && !value.isNull("sizeBytes")) value.optLong("sizeBytes") else null,
            publishedAt = value.optString("publishedAt").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}
