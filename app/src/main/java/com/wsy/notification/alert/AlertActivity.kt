package com.wsy.notification.alert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wsy.notification.debug.DebugLog
import com.wsy.notification.ui.AlertScreen
import com.wsy.notification.ui.theme.NotificationTheme

class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.i("UI", "AlertActivity onCreate")
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        enableEdgeToEdge()
        setContent {
            NotificationTheme {
                val alerting by AlertState.isAlerting.collectAsStateWithLifecycle()
                val items by AlertState.items.collectAsStateWithLifecycle()
                LaunchedEffect(alerting) {
                    if (!alerting) {
                        kotlinx.coroutines.delay(400)
                        if (!AlertState.isAlerting.value) finish()
                    }
                }
                AlertScreen(
                    items = items,
                    onConfirm = {
                        DebugLog.i("UI", "AlertActivity confirm button")
                        AlertForegroundService.confirm(this@AlertActivity)
                        finish()
                    },
                )
            }
        }
    }
}
