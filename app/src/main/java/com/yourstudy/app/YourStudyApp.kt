package com.yourstudy.app

import android.app.Application

class YourStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Pairing.init(this)
        RemoteControl.init()
        FocusScheduler.init(this)
        StudyPlanner.init(this)
        WellbeingStore.init(this)
        WellbeingTracker.init(this)
        AdminControl.init(this)
        FocusMusic.init(this)
        Math1UpdateClient.restoreCached(this)
        Session.restore(this)
        RemoteControl.refresh(force = true)
        android.util.Log.d("YourStudy", "originalLauncher=" + com.yourstudy.app.ui.realLauncher(this))
    }
}
