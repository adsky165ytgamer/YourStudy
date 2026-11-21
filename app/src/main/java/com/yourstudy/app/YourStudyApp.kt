package com.yourstudy.app

import android.app.Application

class YourStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
