package com.wsy.notification.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wsy.notification.ui.LogScreen
import com.wsy.notification.ui.theme.NotificationTheme

class LogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.i("UI", "open log screen")
        enableEdgeToEdge()
        setContent {
            NotificationTheme {
                LogScreen(
                    onBack = { finish() },
                    onShare = {
                        try {
                            DebugLog.share(this)
                        } catch (error: Exception) {
                            DebugLog.e("Log", "share failed", error)
                            Toast.makeText(this, "分享失败：${error.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onCopy = {
                        val ok = DebugLog.copyToClipboard(this)
                        Toast.makeText(
                            this,
                            if (ok) "日志已复制，可粘贴发给我" else "复制失败",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onClear = {
                        DebugLog.clear()
                        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
