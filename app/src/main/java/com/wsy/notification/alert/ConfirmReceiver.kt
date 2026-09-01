package com.wsy.notification.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlertForegroundService.confirm(context)
    }
}
