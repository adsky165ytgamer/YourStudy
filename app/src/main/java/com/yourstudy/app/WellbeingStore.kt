package com.yourstudy.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A focus or break stretch, for the daily ring. */
data class Block(val start: Long, val end: Long, val focus: Boolean)

/** One calendar day. Durations are kept in ms; *Minutes getters give the minute view. */
data class DayStats(
    val productiveMs: Long = 0,
    val breakMs: Long = 0,
    val wastedMs: Long = 0,
    val restMs: Long = 0,
    val attempts: Int = 0,
    val doomScrolls: Int = 0,
    val sessions: Int = 0,
    val subjectMs: Map<String, Long> = emptyMap(),
    val appFocusMs: Map<String, Long> = emptyMap(),
    val appWastedMs: Map<String, Long> = emptyMap(),
    val doomApps: Map<String, Int> = emptyMap(),
    val blocks: List<Block> = emptyList(),
) {
    val productiveFocusMinutes: Int get() = (productiveMs / 60_000).toInt()
    val breakTotalMinutes: Int get() = (breakMs / 60_000).toInt()
    val breakWastedScreenMinutes: Int get() = (wastedMs / 60_000).toInt()
    val breakRestMinutes: Int get() = (restMs / 60_000).toInt()
    val distractionAttempts: Int get() = attempts
    val doomScrollCount: Int get() = doomScrolls
}

/** Daily buckets keyed YYYY-MM-DD, 30 days kept on-device. */
object WellbeingStore {
    private const val KEEP_DAYS = 30L
    private lateinit var prefs: SharedPreferences
    private val _days = MutableStateFlow<Map<String, DayStats>>(emptyMap())
    val days: StateFlow<Map<String, DayStats>> = _days.asStateFlow()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("yourstudy_wellbeing", Context.MODE_PRIVATE)
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS).toString()
        val map = HashMap<String, DayStats>()
        val edit = prefs.edit()
        prefs.all.keys.toList().forEach { k ->
            if (!k.startsWith("d_")) return@forEach
            val day = k.removePrefix("d_")
            if (day < cutoff) edit.remove(k)
            else decode(prefs.getString(k, null))?.let { map[day] = it }
        }
        edit.apply()
        _days.value = map
    }

    fun dayKey(ms: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun get(day: String): DayStats = _days.value[day] ?: DayStats()

    fun update(day: String, f: (DayStats) -> DayStats) {
        if (!::prefs.isInitialized) return
        val n = f(get(day))
        _days.value = _days.value + (day to n)
        prefs.edit().putString("d_$day", encode(n)).apply()
    }

    private fun mapL(m: Map<String, Long>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }
    private fun mapI(m: Map<String, Int>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }
    private fun readL(o: JSONObject?): Map<String, Long> =
        if (o == null) emptyMap() else o.keys().asSequence().associateWith { o.optLong(it, 0L) }
    private fun readI(o: JSONObject?): Map<String, Int> =
        if (o == null) emptyMap() else o.keys().asSequence().associateWith { o.optInt(it, 0) }

    private fun encode(d: DayStats): String = JSONObject()
        .put("p", d.productiveMs).put("b", d.breakMs).put("w", d.wastedMs).put("r", d.restMs)
        .put("a", d.attempts).put("d", d.doomScrolls).put("n", d.sessions)
        .put("sm", mapL(d.subjectMs)).put("af", mapL(d.appFocusMs)).put("aw", mapL(d.appWastedMs)).put("da", mapI(d.doomApps))
        .put("bl", JSONArray().also { a -> d.blocks.forEach { a.put(JSONArray().put(it.start).put(it.end).put(if (it.focus) 1 else 0)) } })
        .toString()

    private fun decode(s: String?): DayStats? = runCatching {
        val o = JSONObject(s ?: return null)
        val bl = o.optJSONArray("bl")
        DayStats(
            o.optLong("p"), o.optLong("b"), o.optLong("w"), o.optLong("r"),
            o.optInt("a"), o.optInt("d"), o.optInt("n"),
            readL(o.optJSONObject("sm")), readL(o.optJSONObject("af")), readL(o.optJSONObject("aw")), readI(o.optJSONObject("da")),
            if (bl == null) emptyList() else (0 until bl.length()).map {
                val x = bl.getJSONArray(it); Block(x.getLong(0), x.getLong(1), x.getInt(2) == 1)
            },
        )
    }.getOrNull()
}
