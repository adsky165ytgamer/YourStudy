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
        _log.value = readLog()
    }

    var essentials: Set<String>
        get() = (prefs.getStringSet("essentials", emptySet()) ?: emptySet()).toSet()
        set(v) { prefs.edit().putStringSet("essentials", v.toSet()).apply() }

    var minutes: Int
        get() = prefs.getInt("minutes", 25)
        set(v) { prefs.edit().putInt("minutes", v).apply() }

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
