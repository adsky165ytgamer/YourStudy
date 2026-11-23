package com.yourstudy.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** days: bit 0 = Monday ... bit 6 = Sunday. dateMillis set = one-off on that date (days ignored). */
data class FocusSchedule(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val dateMillis: Long? = null,
    val days: Int = FocusScheduler.ALL_DAYS,
    val durationMinutes: Int = 25,
    val autoStart: Boolean = true,
    val enabled: Boolean = true,
    val subjectId: String? = null,
)

data class LaunchReq(
    val scheduleId: String, val minutes: Int, val autoStart: Boolean,
    val subjectId: String? = null, val nonce: Long = System.nanoTime(),
)

/** Pending "jump to Focus" request, consumed by the idle home screen. */
object ScheduleLaunch { val request = MutableStateFlow<LaunchReq?>(null) }

object FocusScheduler {
    const val ALL_DAYS = 127
    const val ACTION_FIRE = "com.yourstudy.app.ACTION_FIRE_SCHEDULE"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_TARGET_TAB = "extra_target_tab"
    const val EXTRA_DURATION_MIN = "extra_duration_min"
    const val EXTRA_AUTO_START = "extra_auto_start"
    const val EXTRA_SUBJECT_ID = "extra_subject_id"
    private const val CHANNEL_ID = "focus_schedule"

    private lateinit var app: Context
    private val _schedules = MutableStateFlow<List<FocusSchedule>>(emptyList())
    val schedules: StateFlow<List<FocusSchedule>> = _schedules.asStateFlow()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        _schedules.value = read()
        rearmAll()
    }

    private fun ensure(ctx: Context) { if (!::app.isInitialized) init(ctx) }
    private fun prefs() = app.getSharedPreferences("yourstudy_schedules", Context.MODE_PRIVATE)
    private fun alarms() = app.getSystemService(AlarmManager::class.java)

    private fun read(): List<FocusSchedule> = runCatching {
        val a = JSONArray(prefs().getString("list", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            FocusSchedule(
                o.getString("id"), o.optString("label", ""), o.getInt("hour"), o.getInt("minute"),
                if (o.isNull("date")) null else o.getLong("date"),
                o.optInt("days", ALL_DAYS), o.optInt("dur", 25), o.optBoolean("auto", true), o.optBoolean("on", true),
                if (o.isNull("sub")) null else o.optString("sub").takeIf { s -> s.isNotEmpty() },
            )
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<FocusSchedule>) {
        _schedules.value = list
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("label", it.label).put("hour", it.hour).put("minute", it.minute)
                .put("date", it.dateMillis ?: JSONObject.NULL).put("days", it.days).put("dur", it.durationMinutes)
                .put("auto", it.autoStart).put("on", it.enabled).put("sub", it.subjectId ?: JSONObject.NULL))
        }
        prefs().edit().putString("list", a.toString()).apply()
    }

    fun upsert(s: FocusSchedule) {
        write((_schedules.value.filter { it.id != s.id } + s).sortedBy { it.hour * 60 + it.minute })
        if (s.enabled) arm(s) else cancel(s.id)
    }

    fun setEnabled(id: String, on: Boolean) {
        _schedules.value.firstOrNull { it.id == id }?.let { upsert(it.copy(enabled = on)) }
    }

    fun delete(id: String) { cancel(id); write(_schedules.value.filterNot { it.id == id }) }

    fun rearmAll() { _schedules.value.forEach { if (it.enabled) arm(it) else cancel(it.id) } }

    fun canExact() = alarms().canScheduleExactAlarms()
    fun canFullScreen() = Build.VERSION.SDK_INT < 34 || app.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun nextTrigger(s: FocusSchedule, now: Long = System.currentTimeMillis()): Long? {
        fun at(c: Calendar): Long {
            c.set(Calendar.HOUR_OF_DAY, s.hour); c.set(Calendar.MINUTE, s.minute)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        s.dateMillis?.let { d -> return at(Calendar.getInstance().apply { timeInMillis = d }).takeIf { it > now } }
        val mask = if (s.days == 0) ALL_DAYS else s.days
        for (i in 0..7) {
            val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, i) }
            val bit = 1 shl ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Mon = 0 ... Sun = 6
            if (mask and bit != 0) { val t = at(c); if (t > now) return t }
        }
        return null
    }

    fun nextUpLabel(): String? {
        val (_, t) = _schedules.value.filter { it.enabled }.mapNotNull { s -> nextTrigger(s)?.let { s to it } }
            .minByOrNull { it.second } ?: return null
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val same = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        val hm = String.format(Locale.getDefault(), "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return if (same) "Next $hm" else "Next " + SimpleDateFormat("EEE", Locale.getDefault()).format(c.time) + " " + hm
    }

    private fun pending(id: String) = PendingIntent.getBroadcast(
        app, id.hashCode(),
        Intent(app, ScheduleReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_SCHEDULE_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arm(s: FocusSchedule) {
        val at = nextTrigger(s) ?: run { cancel(s.id); return }
        val am = alarms()
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(s.id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(s.id))
    }

    private fun cancel(id: String) { alarms().cancel(pending(id)) }

    internal fun onFired(ctx: Context, id: String) {
        ensure(ctx)
        val s = _schedules.value.firstOrNull { it.id == id } ?: return
        if (!s.enabled) return
        if (s.dateMillis != null) write(_schedules.value.map { if (it.id == id) it.copy(enabled = false) else it })
        else arm(s) // next occurrence
        if (!RemoteControl.config.value.feature("schedule").enabled) return
        if (Session.ui.value.phase != Phase.IDLE) return // never hijack a running session
        launchFocus(s)
    }

    private fun launchFocus(s: FocusSchedule) {
        val launch = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_TARGET_TAB, "focus").putExtra(EXTRA_SCHEDULE_ID, s.id)
            .putExtra(EXTRA_DURATION_MIN, s.durationMinutes).putExtra(EXTRA_AUTO_START, s.autoStart)
            .apply { s.subjectId?.let { putExtra(EXTRA_SUBJECT_ID, it) } }
        runCatching { app.startActivity(launch) }

        // Fallback for a locked or background-restricted phone: tapping or unlocking lands on Focus.
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Focus schedule", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(app, s.id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Focus · ${s.durationMinutes}m")
            .apply { if (s.label.isNotBlank()) setContentText(s.label) }
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { nm.notify(s.id.hashCode(), n) }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(FocusScheduler.EXTRA_SCHEDULE_ID) ?: return
        FocusScheduler.onFired(context, id)
    }
}

/** Re-arms alarms after reboot, app update, or time/timezone changes. */
class BootScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusScheduler.init(context)
        StudyPlanner.init(context)
    }
}
