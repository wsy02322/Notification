package com.wsy.notification.alert

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.wsy.notification.MainActivity
import com.wsy.notification.NotificationApp
import com.wsy.notification.R
import com.wsy.notification.oem.PermissionChecker

class AlertForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alerting = false

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                confirmAndStopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_CONFIRM -> confirmAlert()
            ACTION_MATCH -> onMatch(itemFromIntent(intent))
            ACTION_TEST -> onMatch(testItem())
            else -> startIdleForeground()
        }
        return START_STICKY
    }

    fun onMatch(item: AlertItem) {
        AlertState.add(item)
        startAlerting()
    }

    private fun startIdleForeground() {
        if (!alerting) {
            startAsForeground(idleNotification())
        }
    }

    private fun startAlerting() {
        if (!alerting) {
            alerting = true
            acquireWakeLock()
            startSound()
            startVibration()
        }
        startAsForeground(alertNotification())
        maybeLaunchAlertActivity()
    }

    private fun confirmAlert() {
        if (!alerting && AlertState.snapshot().isEmpty()) {
            startAsForeground(idleNotification())
            return
        }
        stopSoundAndVibration()
        alerting = false
        AlertState.clear()
        startAsForeground(idleNotification())
    }

    private fun confirmAndStopMonitoring() {
        stopSoundAndVibration()
        alerting = false
        AlertState.clear()
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationApp.NOTIFICATION_ID, notification, type)
    }

    private fun idleNotification(): Notification {
        val open = activityPending(MainActivity::class.java, REQUEST_OPEN)
        return NotificationCompat.Builder(this, NotificationApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun alertNotification(): Notification {
        val items = AlertState.snapshot()
        val latest = items.lastOrNull()
        val title = if (items.size <= 1) {
            getString(R.string.alert_notification_title)
        } else {
            getString(R.string.alert_notification_title_multi, items.size)
        }
        val text = latest?.let { item ->
            listOf(item.appLabel, item.title, item.text)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { getString(R.string.alert_notification_text) }
        } ?: getString(R.string.alert_notification_text)

        val confirm = PendingIntent.getBroadcast(
            this,
            REQUEST_CONFIRM,
            Intent(this, ConfirmReceiver::class.java).setAction(ACTION_CONFIRM),
            pendingFlags(),
        )
        val fullScreen = activityPending(AlertActivity::class.java, REQUEST_FULLSCREEN)

        val builder = NotificationCompat.Builder(this, NotificationApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .addAction(R.drawable.ic_stat_notify, getString(R.string.action_confirm), confirm)
            .setDeleteIntent(confirm)

        if (Build.VERSION.SDK_INT < 34 || PermissionChecker.canUseFullScreenIntent(this)) {
            builder.setFullScreenIntent(fullScreen, true)
        }
        return builder.build()
    }

    private fun maybeLaunchAlertActivity() {
        val inForeground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        val canFsi = PermissionChecker.canUseFullScreenIntent(this)
        if (!inForeground && !canFsi) return
        val intent = Intent(this, AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to launch alert activity", e)
        }
    }

    private fun startSound() {
        if (mediaPlayer != null) return
        try {
            val player = MediaPlayer.create(this, R.raw.alert_loop) ?: return
            player.isLooping = true
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start alert sound", e)
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 700, 350, 700, 350)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopSoundAndVibration() {
        try {
            mediaPlayer?.run {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            vibrator.cancel()
        } catch (_: Exception) {
        }
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "notifywatch:alert").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun activityPending(cls: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(this, cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun itemFromIntent(intent: Intent): AlertItem = AlertItem(
        packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
        appLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
        text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
    )

    private fun testItem(): AlertItem = AlertItem(
        packageName = packageName,
        appLabel = getString(R.string.app_name),
        title = getString(R.string.test_alert_title),
        text = getString(R.string.test_alert_text),
    )

    override fun onDestroy() {
        stopSoundAndVibration()
        AlertState.clear()
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotifyWatch"
        const val ACTION_START = "com.wsy.notification.action.START"
        const val ACTION_STOP = "com.wsy.notification.action.STOP"
        const val ACTION_MATCH = "com.wsy.notification.action.MATCH"
        const val ACTION_CONFIRM = "com.wsy.notification.action.CONFIRM"
        const val ACTION_TEST = "com.wsy.notification.action.TEST"

        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"

        private const val REQUEST_OPEN = 11
        private const val REQUEST_CONFIRM = 12
        private const val REQUEST_FULLSCREEN = 13

        @Volatile
        var instance: AlertForegroundService? = null
            private set

        fun start(context: android.content.Context) {
            val intent = Intent(context, AlertForegroundService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, AlertForegroundService::class.java).setAction(ACTION_STOP)
            instance?.confirmAndStopMonitoring() ?: run {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                    context.stopService(Intent(context, AlertForegroundService::class.java))
                }
            }
        }

        fun confirm(context: android.content.Context) {
            instance?.confirmAlert() ?: run {
                context.startService(
                    Intent(context, AlertForegroundService::class.java).setAction(ACTION_CONFIRM),
                )
            }
        }

        fun test(context: android.content.Context) {
            val intent = Intent(context, AlertForegroundService::class.java).setAction(ACTION_TEST)
            if (instance != null) {
                instance?.onStartCommand(intent, 0, 0)
            } else {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        fun postMatch(context: android.content.Context, item: AlertItem) {
            instance?.onMatch(item) ?: run {
                val intent = Intent(context, AlertForegroundService::class.java).apply {
                    action = ACTION_MATCH
                    putExtra(EXTRA_PACKAGE, item.packageName)
                    putExtra(EXTRA_LABEL, item.appLabel)
                    putExtra(EXTRA_TITLE, item.title)
                    putExtra(EXTRA_TEXT, item.text)
                }
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot start alert service from background", e)
                }
            }
        }
    }
}
