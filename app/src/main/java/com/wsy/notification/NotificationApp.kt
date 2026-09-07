package com.wsy.notification

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.wsy.notification.debug.DebugLog

class NotificationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = NotificationManagerCompat.from(this)

        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            getString(R.string.channel_monitor_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.channel_monitor_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            getString(R.string.channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_alert_desc)
            setSound(null, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(monitor)
        manager.createNotificationChannel(alert)
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor_idle_v2"
        const val CHANNEL_ALERT = "monitor_alert"
        const val NOTIFICATION_ID = 1001
    }
}
