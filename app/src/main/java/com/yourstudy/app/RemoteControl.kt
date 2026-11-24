package com.yourstudy.app

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppLockdownConfig(val enabled: Boolean = false, val title: String = "Temporarily unavailable", val message: String = "")

data class AppUpdateConfig(
    val enabled: Boolean = false,
    val latestVersionCode: Long = 0,
    val minSupportedVersionCode: Long = 0,
    val versionName: String = "",
    val forceUpdate: Boolean = false,
    val changelog: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
)

data class FeatureControl(
    val enabled: Boolean = true,
    val locked: Boolean = false,
    val lockReason: String = "This feature is temporarily unavailable.",
    val badge: String? = null,
    val allowDuringFocus: Boolean = true,
    val minMinutes: Int = 1,
    val maxMinutes: Int = 240,
    val strictGuard: Boolean = true,
    val doomScrollThreshold: Int = 12,
    val doomScrollWindowSec: Int = 45,
    val allowAutoLockOnBypass: Boolean = true,
    val minBreakMinutes: Int = 1,
    val maxBreakMinutes: Int = 30,
)

data class UiControlOverride(
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val label: String? = null,
    val subtitle: String? = null,
    val badge: String? = null,
    val interceptPopupId: String? = null,
)

data class PopupConfig(
    val id: String,
    val enabled: Boolean = false,
    val trigger: String = "on_launch",
    val targetScreen: String = "global",
    val dismissible: Boolean = true,
    val showOnce: Boolean = true,
    val title: String = "",
    val message: String = "",
    val primaryLabel: String = "OK",
    val primaryAction: String = "dismiss",
    val actionTarget: String? = null,
)

data class RemoteControlConfig(
    val schemaVersion: Int = 1,
    val appLockdown: AppLockdownConfig = AppLockdownConfig(),
    val appUpdate: AppUpdateConfig = AppUpdateConfig(),
    val features: Map<String, FeatureControl> = emptyMap(),
    val uiControls: Map<String, UiControlOverride> = emptyMap(),
    val popups: List<PopupConfig> = emptyList(),
) {
    fun feature(name: String) = features[name] ?: FeatureControl()
    fun ui(id: String) = uiControls[id] ?: UiControlOverride()
}

/** Current screen key for popup targeting: home, log, family, focus, break, completed, voice_notes. */
object Screens { val current = MutableStateFlow("home") }

/** Popups requested by interceptors / lock notices (shown regardless of trigger). */
object Popups {
    val requested = MutableStateFlow<PopupConfig?>(null)
    val launchSeen = MutableStateFlow<Set<String>>(emptySet())
    val screenSeen = MutableStateFlow<Set<String>>(emptySet())
    val updateDismissed = MutableStateFlow(false)

    fun show(id: String) {
        val p = RemoteControl.config.value.popups.firstOrNull { it.id == id && it.enabled }
        if (p != null) requested.value = p else notice("Notice", "This action is currently unavailable.")
    }

    fun notice(title: String, message: String) {
        requested.value = PopupConfig(id = "local_notice", enabled = true, showOnce = false, title = title, message = message)
    }
}

/**
 * Local-first control plane. Cached config (or safe defaults) loads instantly; the remote fetch is best-effort.
 * It only tunes behaviour that is already compiled into the app; it never loads code.
 */
object RemoteControl {
    /** Host control.json somewhere reachable over https (GitHub raw, Pages, your server) and put the URL here. */
    const val CONTROL_URL = "https://raw.githubusercontent.com/adsky165ytgamer/YourStudy/main/control-plane/control.json"

    private val _config = MutableStateFlow(RemoteControlConfig())
    val config: StateFlow<RemoteControlConfig> = _config.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var fetching = false

    fun init() { Store.controlJson?.let { raw -> parse(raw)?.let { _config.value = it } } }

    fun refresh(force: Boolean = false) {
        if (CONTROL_URL.isBlank() || !CONTROL_URL.startsWith("https://") || fetching) return
        fetching = true
        scope.launch {
            try {
                val raw = runCatching { fetch() }.getOrNull() ?: return@launch
                val cfg = parse(raw) ?: return@launch // malformed payload: keep what we have
                Store.controlJson = raw
                _config.value = cfg
            } finally { fetching = false }
        }
    }

    fun minutesRange(): IntRange {
        val f = _config.value.feature("focus_mode")
        val lo = f.minMinutes.coerceIn(1, 1440)
        return lo..f.maxMinutes.coerceIn(lo, 1440)
    }

    /** Allowed custom break lengths (minutes), from focus_mode.minBreakMinutes / maxBreakMinutes. */
    fun breakRange(): IntRange {
        val f = _config.value.feature("focus_mode")
        val lo = f.minBreakMinutes.coerceIn(1, 120)
        return lo..f.maxBreakMinutes.coerceIn(lo, 120)
    }

    private fun fetch(): String? {
        val bust = CONTROL_URL + (if ('?' in CONTROL_URL) "&" else "?") + "t=" + System.currentTimeMillis()
        val c = URL(bust).openConnection() as HttpURLConnection
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        c.connectTimeout = 8000; c.readTimeout = 8000
        return try {
            if (c.responseCode != 200) null
            else c.inputStream.use { it.readNBytes(262_144).toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)

    private fun parse(raw: String): RemoteControlConfig? = runCatching {
        val o = JSONObject(raw)
        val lock = o.optJSONObject("appLockdown")?.let {
            AppLockdownConfig(it.optBoolean("enabled", false), it.str("title") ?: "Temporarily unavailable", it.str("message") ?: "")
        } ?: AppLockdownConfig()
        val up = o.optJSONObject("appUpdate")?.let {
            AppUpdateConfig(
                it.optBoolean("enabled", false), it.optLong("latestVersionCode", 0), it.optLong("minSupportedVersionCode", 0),
                it.str("versionName") ?: "", it.optBoolean("forceUpdate", false), it.str("changelog") ?: "",
                it.str("apkUrl") ?: "", it.str("sha256") ?: "",
            )
        } ?: AppUpdateConfig()
        val feats = HashMap<String, FeatureControl>()
        o.optJSONObject("features")?.let { f ->
            f.keys().forEach { k ->
                val x = f.optJSONObject(k) ?: return@forEach
                val d = FeatureControl()
                feats[k] = FeatureControl(
                    x.optBoolean("enabled", d.enabled), x.optBoolean("locked", d.locked), x.str("lockReason") ?: d.lockReason,
                    x.str("badge"), x.optBoolean("allowDuringFocus", d.allowDuringFocus),
                    x.optInt("minMinutes", d.minMinutes), x.optInt("maxMinutes", d.maxMinutes),
                    x.optBoolean("strictGuard", d.strictGuard),
                    x.optInt("doomScrollThreshold", d.doomScrollThreshold), x.optInt("doomScrollWindowSec", d.doomScrollWindowSec),
                    x.optBoolean("allowAutoLockOnBypass", d.allowAutoLockOnBypass),
                    x.optInt("minBreakMinutes", d.minBreakMinutes), x.optInt("maxBreakMinutes", d.maxBreakMinutes),
                )
            }
        }
        val uis = HashMap<String, UiControlOverride>()
        o.optJSONObject("uiControls")?.let { u ->
            u.keys().forEach { k ->
                val x = u.optJSONObject(k) ?: return@forEach
                uis[k] = UiControlOverride(
                    x.optBoolean("visible", true), x.optBoolean("enabled", true),
                    x.str("label"), x.str("subtitle"), x.str("badge"), x.str("interceptPopupId"),
                )
            }
        }
        val pops = ArrayList<PopupConfig>()
        o.optJSONArray("popups")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val id = x.str("id")?.takeIf { it.isNotBlank() } ?: continue
                pops += PopupConfig(
                    id, x.optBoolean("enabled", false), x.str("trigger") ?: "on_launch", x.str("targetScreen") ?: "global",
                    x.optBoolean("dismissible", true), x.optBoolean("showOnce", true), x.str("title") ?: "",
                    x.str("message") ?: "", x.str("primaryLabel") ?: "OK", x.str("primaryAction") ?: "dismiss",
                    x.str("actionTarget"),
                )
            }
        }
        RemoteControlConfig(o.optInt("schemaVersion", 1), lock, up, feats, uis, pops)
    }.getOrNull()
}
