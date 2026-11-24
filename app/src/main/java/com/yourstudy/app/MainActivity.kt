package com.yourstudy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourstudy.app.ui.AppRoot
import com.yourstudy.app.ui.YourStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YourStudyTheme { AppRoot() } }
        if (savedInstanceState == null) { handleSchedule(intent); handlePlanner(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSchedule(intent)
        handlePlanner(intent)
    }

    /** Scheduled launch: publish one request; the idle home screen switches to Focus and may auto-start. */
    private fun handleSchedule(i: Intent?) {
        if (i?.getStringExtra(FocusScheduler.EXTRA_TARGET_TAB) != "focus") return
        ScheduleLaunch.request.value = LaunchReq(
            scheduleId = i.getStringExtra(FocusScheduler.EXTRA_SCHEDULE_ID).orEmpty(),
            minutes = i.getIntExtra(FocusScheduler.EXTRA_DURATION_MIN, 25),
            autoStart = i.getBooleanExtra(FocusScheduler.EXTRA_AUTO_START, true),
            subjectId = i.getStringExtra(FocusScheduler.EXTRA_SUBJECT_ID),
        )
        i.removeExtra(FocusScheduler.EXTRA_TARGET_TAB) // rotation must not replay it
    }

    /** Task reminder tap: preselect that task on the Focus tab. */
    private fun handlePlanner(i: Intent?) {
        val id = i?.getStringExtra(StudyPlanner.EXTRA_TASK_ID) ?: return
        PlannerLaunch.taskId.value = id
        i.removeExtra(StudyPlanner.EXTRA_TASK_ID)
    }

    override fun onResume() { super.onResume(); Session.appVisible = true; RemoteControl.refresh(); Session.ensureService(this) }
    override fun onPause() { super.onPause(); Session.appVisible = false }
}
