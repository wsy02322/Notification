package com.wsy.notification.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wsy.notification.debug.DebugLog

class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DebugLog.i("Confirm", "notification action onReceive action=${intent?.action}")
        AlertForegroundService.confirm(context)
    }
}
