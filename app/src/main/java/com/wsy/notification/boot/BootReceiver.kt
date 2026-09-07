package com.wsy.notification.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wsy.notification.alert.AlertForegroundService
import com.wsy.notification.debug.DebugLog
import com.wsy.notification.prefs.MonitorPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        DebugLog.i("Boot", "onReceive action=$action")
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val enabled = MonitorPrefs(context).monitoringEnabled
        DebugLog.i("Boot", "monitoringEnabled=$enabled")
        if (enabled) {
            AlertForegroundService.start(context)
        }
    }
}
