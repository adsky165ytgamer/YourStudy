package com.yourstudy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourstudy.app.ui.AppRoot
import com.yourstudy.app.ui.YourStudyTheme

/** Doubles as the HOME launcher (see manifest). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YourStudyTheme { AppRoot() } }
    }

    override fun onResume() { super.onResume(); Session.appVisible = true }
    override fun onPause() { super.onPause(); Session.appVisible = false }
}
