package com.yourstudy.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(
    val startedAt: Long,
    val plannedMin: Int,
    val focusedMs: Long,
    val breakUsed: Boolean,
    val completed: Boolean,
)

/** Real local persistence: essentials, last duration, and the study log. */
object Store {
    private lateinit var prefs: SharedPreferences
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("your_study", Context.MODE_PRIVATE)
        _log.value = readLog(); loadPeerLogs(); loadNotes()
    }

    var essentials: Set<String>
        get() = (prefs.getStringSet("essentials", emptySet()) ?: emptySet()).toSet()
        set(v) { prefs.edit().putStringSet("essentials", v.toSet()).apply() }

    var minutes: Int
        get() = prefs.getInt("minutes", 25)
        set(v) { prefs.edit().putInt("minutes", v).apply() }

    var role: Role
        get() = runCatching { Role.valueOf(prefs.getString("role", "SELF_STUDY")!!) }.getOrDefault(Role.SELF_STUDY)
        set(v) { prefs.edit().putString("role", v.name).apply() }

    val deviceId: String
        get() = prefs.getString("did", null)
            ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("did", it).apply() }

    var peerId: String?
        get() = prefs.getString("peerId", null)
        set(v) { prefs.edit().putString("peerId", v).apply() }
    var peerName: String?
        get() = prefs.getString("peerName", null)
        set(v) { prefs.edit().putString("peerName", v).apply() }
    var secret: String?
        get() = prefs.getString("secret", null)
        set(v) { prefs.edit().putString("secret", v).apply() }

    fun unpair() {
        prefs.edit().remove("peerId").remove("peerName").remove("secret").remove("pending").remove("peerlogs").apply()
        _peerLogs.value = emptyList()
    }

    private val _peerLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val peerLogs: StateFlow<List<LogEntry>> = _peerLogs.asStateFlow()
    private fun enc(l: List<LogEntry>) = JSONArray().also { a -> l.forEach { a.put(it.toJson()) } }.toString()
    private fun dec(s: String?): List<LogEntry> = runCatching {
        val a = JSONArray(s ?: "[]"); (0 until a.length()).map { logFromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun pendingLogs() = dec(prefs.getString("pending", null))
    fun queueLog(e: LogEntry) { prefs.edit().putString("pending", enc(pendingLogs() + e)).apply() }
    fun clearPending() { prefs.edit().remove("pending").apply() }
    fun addPeerLogs(l: List<LogEntry>) {
        val merged = (l + _peerLogs.value).distinctBy { it.startedAt }.sortedByDescending { it.startedAt }
        _peerLogs.value = merged
        prefs.edit().putString("peerlogs", enc(merged)).apply()
    }
    private fun loadPeerLogs() { _peerLogs.value = dec(prefs.getString("peerlogs", null)) }

    // ---- Remote control cache + one-time popup tracking ----
    var controlJson: String?
        get() = prefs.getString("control", null)
        set(v) { prefs.edit().putString("control", v).apply() }
    fun popupShown(id: String) = id in (prefs.getStringSet("shown", emptySet()) ?: emptySet())
    fun markPopupShown(id: String) {
        prefs.edit().putStringSet("shown", (prefs.getStringSet("shown", emptySet()) ?: emptySet()) + id).apply()
    }

    // ---- v6: break length, focus music, session snapshot, update-check state ----
    var breakMinutes: Int
        get() = prefs.getInt("breakMin", 5)
        set(v) { prefs.edit().putInt("breakMin", v).apply() }

    var musicQueue: List<String>
        get() = runCatching {
            val a = JSONArray(prefs.getString("musicQ", "[]")); (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        set(v) { prefs.edit().putString("musicQ", JSONArray(v).toString()).apply() }

    var musicAutoPlay: Boolean
        get() = prefs.getBoolean("musicAuto", true)
        set(v) { prefs.edit().putBoolean("musicAuto", v).apply() }

    /** Active focus/break session, so process death does not silently end a session. */
    var sessionSnapshot: String?
        get() = prefs.getString("sessionSnap", null)
        set(v) { prefs.edit().apply { if (v == null) remove("sessionSnap") else putString("sessionSnap", v) }.apply() }

    /** Wall-clock time of the last MATH1 attempt, persisted so the 6 h cooldown survives restarts. */
    var lastUpdateCheck: Long
        get() = prefs.getLong("updChk", 0L)
        set(v) { prefs.edit().putLong("updChk", v).apply() }

    var updateCache: String?
        get() = prefs.getString("updCache", null)
        set(v) { prefs.edit().apply { if (v == null) remove("updCache") else putString("updCache", v) }.apply() }

    // ---- Voice notes (offline, local) ----
    private val _notes = MutableStateFlow<List<VoiceNote>>(emptyList())
    val notes: StateFlow<List<VoiceNote>> = _notes.asStateFlow()

    var voiceLang: String
        get() = prefs.getString("vlang", null) ?: java.util.Locale.getDefault().toLanguageTag()
        set(v) { prefs.edit().putString("vlang", v).apply() }

    private fun loadNotes() {
        _notes.value = runCatching {
            val a = JSONArray(prefs.getString("notes", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                VoiceNote(
                    o.getString("id"), o.getString("c"), o.getString("l"), o.getLong("cr"), o.getLong("up"),
                    o.getBoolean("f"), if (o.has("fs")) o.getLong("fs") else null,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistNotes() {
        val a = JSONArray()
        _notes.value.forEach {
            a.put(JSONObject().put("id", it.id).put("c", it.content).put("l", it.languageTag)
                .put("cr", it.createdAt).put("up", it.updatedAt).put("f", it.wasCapturedDuringFocus)
                .also { o -> it.focusSessionStart?.let { s -> o.put("fs", s) } })
        }
        prefs.edit().putString("notes", a.toString()).apply()
    }

    fun saveNote(n: VoiceNote) {
        _notes.value = (_notes.value.filter { it.id != n.id } + n).sortedByDescending { it.updatedAt }
        persistNotes()
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filter { it.id != id }
        persistNotes()
    }

    fun addLog(e: LogEntry) {
        val list = listOf(e) + _log.value
        _log.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("s", it.startedAt).put("p", it.plannedMin)
                    .put("f", it.focusedMs).put("b", it.breakUsed).put("c", it.completed)
            )
        }
        prefs.edit().putString("log", arr.toString()).apply()
    }

    private fun readLog(): List<LogEntry> = runCatching {
        val a = JSONArray(prefs.getString("log", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            LogEntry(o.getLong("s"), o.getInt("p"), o.getLong("f"), o.getBoolean("b"), o.getBoolean("c"))
        }
    }.getOrDefault(emptyList())
}

enum class Role { SELF_STUDY, CHILD, PARENT }

fun LogEntry.toJson(): JSONObject =
    JSONObject().put("s", startedAt).put("p", plannedMin).put("f", focusedMs).put("b", breakUsed).put("c", completed)

fun logFromJson(o: JSONObject) =
    LogEntry(o.getLong("s"), o.getInt("p"), o.getLong("f"), o.getBoolean("b"), o.getBoolean("c"))

data class VoiceNote(
    val id: String,
    val content: String,
    val languageTag: String,
    val createdAt: Long,
    val updatedAt: Long,
    val wasCapturedDuringFocus: Boolean,
    val focusSessionStart: Long?,
)
