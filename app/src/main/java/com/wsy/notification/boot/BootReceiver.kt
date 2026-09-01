package com.wsy.notification.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wsy.notification.alert.AlertForegroundService
import com.wsy.notification.prefs.MonitorPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (MonitorPrefs(context).monitoringEnabled) {
            AlertForegroundService.start(context)
        }
    }
}
