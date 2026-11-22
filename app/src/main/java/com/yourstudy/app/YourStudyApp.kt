package com.yourstudy.app

import android.app.Application

class YourStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Pairing.init(this)
        android.util.Log.d("YourStudy", "originalLauncher=" + com.yourstudy.app.ui.realLauncher(this))
    }
}
