package com.wsy.notification.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.wsy.notification.alert.AlertForegroundService
import com.wsy.notification.debug.DebugLog
import com.wsy.notification.listener.NotificationMonitorService
import com.wsy.notification.oem.PermissionChecker
import com.wsy.notification.prefs.MonitorPrefs

class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        DebugLog.i(
            "KeepAlive",
            "onReceive action=$action interactive=${pm.isInteractive} monitoring=${MonitorPrefs(context).monitoringEnabled}",
        )
        when (action) {
            ACTION_HEARTBEAT -> {
                if (MonitorPrefs(context).monitoringEnabled) {
                    AlertForegroundService.start(context)
                    NotificationListenerService.requestRebind(
                        android.content.ComponentName(context, NotificationMonitorService::class.java),
                    )
                }
                KeepAliveScheduler.schedule(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                if (MonitorPrefs(context).monitoringEnabled) {
                    AlertForegroundService.start(context)
                    NotificationListenerService.requestRebind(
                        android.content.ComponentName(context, NotificationMonitorService::class.java),
                    )
                }
            }
            Intent.ACTION_SCREEN_ON -> {
                DebugLog.writeSnapshot(context)
            }
        }
    }

    companion object {
        const val ACTION_HEARTBEAT = "com.wsy.notification.action.HEARTBEAT"
    }
}

object KeepAliveScheduler {
    private const val INTERVAL_MS = 45_000L
    private const val REQUEST_CODE = 21

    fun schedule(context: Context) {
        if (!MonitorPrefs(context).monitoringEnabled) {
            cancel(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                DebugLog.i("KeepAlive", "exact heartbeat in ${INTERVAL_MS}ms")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
                DebugLog.i("KeepAlive", "inexact heartbeat in ${INTERVAL_MS}ms exactAllowed=${PermissionChecker.canScheduleExactAlarms(context)}")
            }
        } catch (error: Exception) {
            DebugLog.e("KeepAlive", "schedule failed", error)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, KeepAliveReceiver::class.java).setAction(KeepAliveReceiver.ACTION_HEARTBEAT)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
