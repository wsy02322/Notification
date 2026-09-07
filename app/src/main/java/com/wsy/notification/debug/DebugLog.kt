package com.wsy.notification.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.wsy.notification.BuildConfig
import com.wsy.notification.oem.PermissionChecker
import com.wsy.notification.prefs.MonitorPrefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val TAG = "NotifyWatch"
    private const val MAX_BYTES = 400 * 1024
    private const val KEEP_BYTES = 250 * 1024

    @Volatile
    private var app: Context? = null
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        app = context.applicationContext
        file().parentFile?.mkdirs()
        i("App", "process start version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
        writeSnapshot(context.applicationContext)
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun w(tag: String, msg: String) = write("W", tag, msg, null)

    fun e(tag: String, msg: String, error: Throwable? = null) = write("E", tag, msg, error)

    fun writeSnapshot(context: Context) {
        val prefs = MonitorPrefs(context)
        i(
            "Snapshot",
            "device brand=${Build.BRAND} manufacturer=${Build.MANUFACTURER} " +
                "model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} " +
                "listener=${PermissionChecker.isNotificationListenerEnabled(context)} " +
                "postNotify=${PermissionChecker.canPostNotifications(context)} " +
                "batteryIgnore=${PermissionChecker.isIgnoringBatteryOptimizations(context)} " +
                "fullScreen=${PermissionChecker.canUseFullScreenIntent(context)} " +
                "monitoring=${prefs.monitoringEnabled} " +
                "apps=${prefs.selectedPackages} " +
                "keywords='${prefs.keywordsRaw.replace("\n", " | ")}'",
        )
    }

    fun readAll(): String = synchronized(lock) {
        val f = file()
        if (!f.exists()) return "（还没有日志）"
        f.readText()
    }

    fun clear() {
        synchronized(lock) {
            val f = file()
            if (f.exists()) f.writeText("")
        }
        i("Log", "cleared by user")
    }

    fun copyToClipboard(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("notifywatch-log", readAll()))
            true
        } catch (error: Exception) {
            e("Log", "copy failed", error)
            false
        }
    }

    fun share(context: Context) {
        writeSnapshot(context)
        val src = file()
        if (!src.exists()) {
            i("Log", "share requested but file missing")
        }
        val shareDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val shareFile = File(shareDir, "notifywatch-test-log.txt")
        synchronized(lock) {
            src.copyTo(shareFile, overwrite = true)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "消息监控测试日志")
            putExtra(Intent.EXTRA_TEXT, "测完请把这个日志文件发回，用于排查。")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出测试日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun write(level: String, tag: String, msg: String, error: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(' ')
            append(msg.take(500))
            if (error != null) {
                append(" :: ")
                append(error.javaClass.simpleName)
                append(": ")
                append(error.message)
            }
        }
        when (level) {
            "W" -> Log.w(TAG, "$tag $msg", error)
            "E" -> Log.e(TAG, "$tag $msg", error)
            else -> Log.i(TAG, "$tag $msg")
        }
        val ctx = app ?: return
        synchronized(lock) {
            try {
                val f = File(ctx.filesDir, "logs/debug.log")
                f.parentFile?.mkdirs()
                f.appendText(line + "\n")
                rotateIfNeeded(f)
            } catch (_: Exception) {
            }
        }
    }

    private fun file(): File {
        val ctx = app ?: throw IllegalStateException("DebugLog not initialized")
        return File(ctx.filesDir, "logs/debug.log")
    }

    private fun rotateIfNeeded(file: File) {
        if (file.length() <= MAX_BYTES) return
        val text = file.readText()
        val trimmed = if (text.length > KEEP_BYTES) text.takeLast(KEEP_BYTES) else text
        val cut = trimmed.indexOf('\n')
        file.writeText(if (cut >= 0) trimmed.substring(cut + 1) else trimmed)
    }
}
