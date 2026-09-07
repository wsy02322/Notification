package com.wsy.notification.alert

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.wsy.notification.MainActivity
import com.wsy.notification.NotificationApp
import com.wsy.notification.R
import com.wsy.notification.debug.DebugLog
import com.wsy.notification.keepalive.KeepAliveScheduler
import android.service.notification.NotificationListenerService
import com.wsy.notification.listener.NotificationMonitorService
import com.wsy.notification.oem.PermissionChecker

class AlertForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var keepAlivePlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var alerting = false
    private var screenReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val retry400 = Runnable { launchAlertActivity("retry-400") }
    private val retry1200 = Runnable { launchAlertActivity("retry-1200") }
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            DebugLog.i(
                "KeepAlive",
                "tick interactive=${pm.isInteractive} alerting=$alerting " +
                    "keepAlive=${keepAlivePlayer?.isPlaying == true} wake=${wakeLock?.isHeld == true}",
            )
            acquireMonitorWakeLock()
            if (!alerting) startKeepAliveAudio()
            KeepAliveScheduler.schedule(this@AlertForegroundService)
            mainHandler.postDelayed(this, 20_000)
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            DebugLog.i("KeepAlive", "screen $action interactive=${pm.isInteractive}")
            if (action == Intent.ACTION_SCREEN_OFF) {
                acquireMonitorWakeLock()
                startAsForeground(if (alerting) alertNotification() else idleNotification())
                if (!alerting) startKeepAliveAudio()
                NotificationListenerService.requestRebind(
                    android.content.ComponentName(this@AlertForegroundService, NotificationMonitorService::class.java),
                )
            }
        }
    }

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
        DebugLog.i("Alert", "service onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLog.i("Alert", "onStartCommand action=${intent?.action} flags=$flags")
        when (intent?.action) {
            ACTION_STOP -> {
                confirmAndStopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_CONFIRM -> confirmAlert()
            ACTION_MATCH -> onMatch(itemFromIntent(intent))
            ACTION_TEST -> onMatch(testItem())
            ACTION_HEARTBEAT -> startIdleForeground()
            else -> startIdleForeground()
        }
        return START_STICKY
    }

    fun onMatch(item: AlertItem) {
        DebugLog.i(
            "Alert",
            "onMatch label='${item.appLabel}' title='${item.title}' text='${item.text}' alreadyAlerting=$alerting",
        )
        AlertState.add(item)
        startAlerting()
    }

    private fun startIdleForeground() {
        acquireMonitorWakeLock()
        registerScreenReceiver()
        startHeartbeat()
        KeepAliveScheduler.schedule(this)
        if (alerting) {
            DebugLog.i("Alert", "keepalive while still alerting")
            startAsForeground(alertNotification())
            return
        }
        startKeepAliveAudio()
        DebugLog.i("Alert", "idle foreground notification")
        startAsForeground(idleNotification())
    }

    private fun startAlerting() {
        if (!alerting) {
            alerting = true
            stopKeepAliveAudio()
            acquireMonitorWakeLock()
            startSound()
            startVibration()
            DebugLog.i("Alert", "start looping sound+vibrate")
        }
        startAsForeground(alertNotification())
        maybeLaunchAlertActivity()
    }

    private fun confirmAlert() {
        DebugLog.i("Alert", "confirm alerting=$alerting queued=${AlertState.snapshot().size}")
        mainHandler.removeCallbacks(retry400)
        mainHandler.removeCallbacks(retry1200)
        if (!alerting && AlertState.snapshot().isEmpty()) {
            startAsForeground(idleNotification())
            return
        }
        stopSoundAndVibration()
        alerting = false
        AlertState.clear()
        startKeepAliveAudio()
        startAsForeground(idleNotification())
    }

    private fun confirmAndStopMonitoring() {
        DebugLog.i("Alert", "stop monitoring service")
        mainHandler.removeCallbacks(retry400)
        mainHandler.removeCallbacks(retry1200)
        mainHandler.removeCallbacks(heartbeatRunnable)
        KeepAliveScheduler.cancel(this)
        unregisterScreenReceiver()
        stopSoundAndVibration()
        stopKeepAliveAudio()
        releaseMonitorWakeLock()
        alerting = false
        AlertState.clear()
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .addAction(R.drawable.ic_stat_notify, getString(R.string.action_confirm), confirm)
            .setDeleteIntent(confirm)

        if (Build.VERSION.SDK_INT < 34 || PermissionChecker.canUseFullScreenIntent(this)) {
            builder.setFullScreenIntent(fullScreen, true)
            DebugLog.i("Alert", "alert notification with fullScreenIntent")
        } else {
            DebugLog.w("Alert", "alert notification without fullScreenIntent (permission off)")
        }
        return builder.build()
    }

    private fun maybeLaunchAlertActivity() {
        val inForeground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        val canFsi = PermissionChecker.canUseFullScreenIntent(this)
        DebugLog.i("Alert", "launch AlertActivity inForeground=$inForeground canFsi=$canFsi")
        wakeScreen()
        launchAlertActivity("immediate")
        mainHandler.removeCallbacks(retry400)
        mainHandler.removeCallbacks(retry1200)
        mainHandler.postDelayed(retry400, 400)
        mainHandler.postDelayed(retry1200, 1200)
    }

    private fun launchAlertActivity(reason: String) {
        if (!alerting) return
        val intent = Intent(this, AlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val opts = activityStartOptions()
        DebugLog.i("Alert", "startActivity $reason opts=${opts != null}")
        try {
            if (opts != null) startActivity(intent, opts) else startActivity(intent)
        } catch (e: Exception) {
            DebugLog.e("Alert", "startActivity $reason failed", e)
        }
    }

    private fun activityStartOptions(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            val options = ActivityOptions.makeBasic()
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            options.toBundle()
        } catch (e: Exception) {
            DebugLog.w("Alert", "ActivityOptions unavailable: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (screenWakeLock?.isHeld != true) {
                screenWakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "notifywatch:screen",
                ).apply {
                    setReferenceCounted(false)
                    acquire(8_000)
                }
            }
            DebugLog.i("Alert", "screen wake isInteractive=${pm.isInteractive}")
        } catch (e: Exception) {
            DebugLog.e("Alert", "screen wake failed", e)
        }
    }

    private fun startSound() {
        if (mediaPlayer != null) return
        try {
            val player = MediaPlayer.create(this, R.raw.alert_loop)
            if (player == null) {
                DebugLog.e("Alert", "MediaPlayer.create returned null")
                return
            }
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
            DebugLog.i("Alert", "sound started")
        } catch (e: Exception) {
            DebugLog.e("Alert", "Unable to start alert sound", e)
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
        releaseScreenWakeLock()
    }

    private fun startKeepAliveAudio() {
        if (alerting) return
        if (keepAlivePlayer?.isPlaying == true) return
        try {
            keepAlivePlayer?.release()
            val player = MediaPlayer.create(this, R.raw.silence) ?: run {
                DebugLog.e("KeepAlive", "silence MediaPlayer.create returned null")
                return
            }
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            player.start()
            keepAlivePlayer = player
            DebugLog.i("KeepAlive", "silent media started")
        } catch (e: Exception) {
            DebugLog.e("KeepAlive", "silent media failed", e)
        }
    }

    private fun stopKeepAliveAudio() {
        try {
            keepAlivePlayer?.run {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        }
        keepAlivePlayer = null
    }

    private fun startHeartbeat() {
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.post(heartbeatRunnable)
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        screenReceiverRegistered = false
    }

    private fun acquireMonitorWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (wakeLock?.isHeld == true) return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "notifywatch:monitor").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
        DebugLog.i("KeepAlive", "monitor wakeLock acquired interactive=${pm.isInteractive}")
    }

    private fun releaseMonitorWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun releaseScreenWakeLock() {
        try {
            if (screenWakeLock?.isHeld == true) screenWakeLock?.release()
        } catch (_: Exception) {
        }
        screenWakeLock = null
    }

    private fun activityPending(cls: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(this, cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options = activityStartOptions()
        return if (options != null) {
            PendingIntent.getActivity(this, requestCode, intent, pendingFlags(), options)
        } else {
            PendingIntent.getActivity(this, requestCode, intent, pendingFlags())
        }
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
        DebugLog.w("Alert", "service onDestroy")
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.removeCallbacks(retry400)
        mainHandler.removeCallbacks(retry1200)
        KeepAliveScheduler.cancel(this)
        unregisterScreenReceiver()
        stopSoundAndVibration()
        stopKeepAliveAudio()
        releaseMonitorWakeLock()
        AlertState.clear()
        instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.wsy.notification.action.START"
        const val ACTION_STOP = "com.wsy.notification.action.STOP"
        const val ACTION_MATCH = "com.wsy.notification.action.MATCH"
        const val ACTION_CONFIRM = "com.wsy.notification.action.CONFIRM"
        const val ACTION_TEST = "com.wsy.notification.action.TEST"
        const val ACTION_HEARTBEAT = "com.wsy.notification.action.HEARTBEAT_SERVICE"

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
            DebugLog.i("Alert", "start() requested instance=${instance != null}")
            val intent = Intent(context, AlertForegroundService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            DebugLog.i("Alert", "stop() requested instance=${instance != null}")
            val intent = Intent(context, AlertForegroundService::class.java).setAction(ACTION_STOP)
            instance?.confirmAndStopMonitoring() ?: run {
                try {
                    context.startService(intent)
                } catch (error: Exception) {
                    DebugLog.e("Alert", "stop() startService failed", error)
                    context.stopService(Intent(context, AlertForegroundService::class.java))
                }
            }
        }

        fun confirm(context: android.content.Context) {
            DebugLog.i("Alert", "confirm() requested instance=${instance != null}")
            instance?.confirmAlert() ?: run {
                context.startService(
                    Intent(context, AlertForegroundService::class.java).setAction(ACTION_CONFIRM),
                )
            }
        }

        fun test(context: android.content.Context) {
            DebugLog.i("Alert", "test() requested instance=${instance != null}")
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
                    DebugLog.e("Alert", "Cannot start alert service from background", e)
                }
            }
        }
    }
}
