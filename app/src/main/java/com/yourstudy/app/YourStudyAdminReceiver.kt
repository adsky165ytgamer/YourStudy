package com.yourstudy.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class YourStudyAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) { AdminControl.refresh(context) }
    override fun onDisabled(context: Context, intent: Intent) { AdminControl.refresh(context) }
}
