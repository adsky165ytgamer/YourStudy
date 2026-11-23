package com.yourstudy.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TaskPriority { LOW, MEDIUM, HIGH }

data class StudySubject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorToken: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class StudyChapter(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val title: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

data class StudyTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subjectId: String? = null,
    val chapterId: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDateMillis: Long? = null,
    val reminderAtMillis: Long? = null,
    val targetMinutes: Int = 25,
    val loggedMinutes: Int = 0,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Optional subject / task chosen on the Focus tab before a session starts. */
object FocusTag {
    val taskId = MutableStateFlow<String?>(null)
    val subjectId = MutableStateFlow<String?>(null)
    fun clear() { taskId.value = null; subjectId.value = null }
    fun selectTask(t: StudyTask) { taskId.value = t.id; subjectId.value = t.subjectId }
    fun selectSubject(id: String) { taskId.value = null; subjectId.value = id }
}

/** One pending "open this task on Focus" request (from a reminder notification). */
object PlannerLaunch { val taskId = MutableStateFlow<String?>(null) }

/** Offline-first planner. JSON in SharedPreferences, no cloud. */
object StudyPlanner {
    const val ACTION_REMIND = "com.yourstudy.app.ACTION_PLANNER_REMIND"
    const val EXTRA_TASK_ID = "extra_planner_task_id"
    private const val CHANNEL_ID = "study_planner_reminders"

    private lateinit var app: Context
    private val _subjects = MutableStateFlow<List<StudySubject>>(emptyList())
    private val _chapters = MutableStateFlow<List<StudyChapter>>(emptyList())
    private val _tasks = MutableStateFlow<List<StudyTask>>(emptyList())
    val subjects: StateFlow<List<StudySubject>> = _subjects.asStateFlow()
    val chapters: StateFlow<List<StudyChapter>> = _chapters.asStateFlow()
    val tasks: StateFlow<List<StudyTask>> = _tasks.asStateFlow()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        load()
        rearmAll()
    }

    private fun ensure(ctx: Context) { if (!::app.isInitialized) init(ctx) }
    private fun prefs() = app.getSharedPreferences("yourstudy_planner", Context.MODE_PRIVATE)
    private fun alarms() = app.getSystemService(AlarmManager::class.java)

    // ---- persistence ----
    private fun JSONObject.optNullLong(k: String): Long? = if (isNull(k)) null else optLong(k)
    private fun JSONObject.optNullStr(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }

    private fun load() {
        val p = prefs()
        _subjects.value = runCatching {
            val a = JSONArray(p.getString("subjects", "[]"))
            (0 until a.length()).map { val o = a.getJSONObject(it); StudySubject(o.getString("id"), o.getString("n"), o.optInt("c", 0), o.optLong("cr", 0L)) }
        }.getOrDefault(emptyList())
        _chapters.value = runCatching {
            val a = JSONArray(p.getString("chapters", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                StudyChapter(o.getString("id"), o.getString("s"), o.getString("t"), o.optBoolean("d", false), o.optNullLong("da"))
            }
        }.getOrDefault(emptyList())
        _tasks.value = runCatching {
            val a = JSONArray(p.getString("tasks", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                StudyTask(
                    o.getString("id"), o.getString("t"), o.optNullStr("s"), o.optNullStr("ch"),
                    runCatching { TaskPriority.valueOf(o.optString("p", "MEDIUM")) }.getOrDefault(TaskPriority.MEDIUM),
                    o.optNullLong("due"), o.optNullLong("rem"), o.optInt("tm", 25), o.optInt("lm", 0),
                    o.optBoolean("d", false), o.optLong("cr", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val sa = JSONArray(); _subjects.value.forEach { sa.put(JSONObject().put("id", it.id).put("n", it.name).put("c", it.colorToken).put("cr", it.createdAt)) }
        val ca = JSONArray(); _chapters.value.forEach {
            ca.put(JSONObject().put("id", it.id).put("s", it.subjectId).put("t", it.title).put("d", it.completed).put("da", it.completedAt ?: JSONObject.NULL))
        }
        val ta = JSONArray(); _tasks.value.forEach {
            ta.put(
                JSONObject().put("id", it.id).put("t", it.title).put("s", it.subjectId ?: JSONObject.NULL).put("ch", it.chapterId ?: JSONObject.NULL)
                    .put("p", it.priority.name).put("due", it.dueDateMillis ?: JSONObject.NULL).put("rem", it.reminderAtMillis ?: JSONObject.NULL)
                    .put("tm", it.targetMinutes).put("lm", it.loggedMinutes).put("d", it.completed).put("cr", it.createdAt)
            )
        }
        prefs().edit().putString("subjects", sa.toString()).putString("chapters", ca.toString()).putString("tasks", ta.toString()).apply()
    }

    // ---- subjects / chapters ----
    fun upsertSubject(s: StudySubject) { _subjects.value = _subjects.value.filter { it.id != s.id } + s; save() }

    fun deleteSubject(id: String) {
        _subjects.value = _subjects.value.filter { it.id != id }
        val gone = _chapters.value.filter { it.subjectId == id }.map { it.id }.toSet()
        _chapters.value = _chapters.value.filter { it.subjectId != id }
        _tasks.value = _tasks.value.map { if (it.subjectId == id || it.chapterId in gone) it.copy(subjectId = null, chapterId = null) else it }
        if (FocusTag.subjectId.value == id) FocusTag.subjectId.value = null
        save()
    }

    fun addChapter(subjectId: String, title: String) {
        if (title.isBlank()) return
        _chapters.value = _chapters.value + StudyChapter(subjectId = subjectId, title = title.trim())
        save()
    }

    fun toggleChapter(id: String) {
        _chapters.value = _chapters.value.map {
            if (it.id == id) it.copy(completed = !it.completed, completedAt = if (it.completed) null else System.currentTimeMillis()) else it
        }
        save()
    }

    fun deleteChapter(id: String) {
        _chapters.value = _chapters.value.filter { it.id != id }
        _tasks.value = _tasks.value.map { if (it.chapterId == id) it.copy(chapterId = null) else it }
        save()
    }

    // ---- tasks ----
    fun upsertTask(t: StudyTask) {
        _tasks.value = (_tasks.value.filter { it.id != t.id } + t).sortedByDescending { it.createdAt }
        save()
        arm(t)
    }

    fun setTaskDone(id: String, done: Boolean) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        upsertTask(t.copy(completed = done))
    }

    fun deleteTask(id: String) {
        cancel(id)
        _tasks.value = _tasks.value.filter { it.id != id }
        if (FocusTag.taskId.value == id) FocusTag.taskId.value = null
        save()
    }

    /** Called when a focus session ends: adds focused minutes to the linked task. */
    fun addLogged(taskId: String?, minutes: Int) {
        if (taskId == null || minutes <= 0) return
        _tasks.value = _tasks.value.map { if (it.id == taskId) it.copy(loggedMinutes = it.loggedMinutes + minutes) else it }
        save()
    }

    // ---- reminders ----
    fun rearmAll() { _tasks.value.forEach { arm(it) } }

    private fun pending(id: String) = PendingIntent.getBroadcast(
        app, id.hashCode(),
        Intent(app, PlannerReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancel(id: String) { runCatching { alarms().cancel(pending(id)) } }

    private fun arm(t: StudyTask) {
        val at = t.reminderAtMillis
        if (t.completed || at == null || at <= System.currentTimeMillis()) { cancel(t.id); return }
        runCatching {
            val am = alarms()
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(t.id))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(t.id))
        }
    }

    internal fun onReminder(ctx: Context, id: String) {
        ensure(ctx)
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (t.completed) return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Study Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_TASK_ID, id)
        val pi = PendingIntent.getActivity(app, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(t.title)
            .setContentText("${t.targetMinutes}m")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(id.hashCode(), n) } // permission denied: silently skipped
    }
}

class PlannerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(StudyPlanner.EXTRA_TASK_ID) ?: return
        StudyPlanner.onReminder(context, id)
    }
}
