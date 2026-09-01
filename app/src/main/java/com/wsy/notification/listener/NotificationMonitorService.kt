package com.wsy.notification.listener

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.wsy.notification.alert.AlertForegroundService
import com.wsy.notification.alert.AlertItem
import com.wsy.notification.match.DedupTracker
import com.wsy.notification.match.KeywordMatcher
import com.wsy.notification.match.NotificationContent
import com.wsy.notification.match.NotificationTextExtractor
import com.wsy.notification.prefs.MonitorPrefs

class NotificationMonitorService : NotificationListenerService() {

    private val dedup = DedupTracker()
    private val prefs by lazy { MonitorPrefs(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected, requesting rebind")
        requestRebind(ComponentName(this, NotificationMonitorService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (!prefs.monitoringEnabled) return
        if (notification.packageName == packageName) return
        if (notification.isOngoing) return
        val flags = notification.notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val content = extract(notification) ?: return
        if (!dedup.isNew(notification.key, content.fingerprint())) return

        val keywords = KeywordMatcher.parseKeywords(prefs.keywordsRaw)
        if (!KeywordMatcher.matches(content, prefs.selectedPackages, keywords)) return

        AlertForegroundService.postMatch(
            this,
            AlertItem(
                packageName = content.packageName,
                appLabel = content.appLabel,
                title = content.title,
                text = content.text,
            ),
        )
    }

    private fun extract(sbn: StatusBarNotification): NotificationContent? {
        val n = sbn.notification ?: return null
        val extras = n.extras
        val title = NotificationTextExtractor.combineTitle(
            extras.charSeq(Notification.EXTRA_TITLE),
            n.tickerText?.toString(),
        )
        val body = NotificationTextExtractor.combineBody(
            text = extras.charSeq(Notification.EXTRA_TEXT),
            bigText = extras.charSeq(Notification.EXTRA_BIG_TEXT),
            subText = extras.charSeq(Notification.EXTRA_SUB_TEXT),
            infoText = extras.charSeq(Notification.EXTRA_INFO_TEXT),
            summaryText = extras.charSeq(Notification.EXTRA_SUMMARY_TEXT),
            textLines = extras.charSeqArray(Notification.EXTRA_TEXT_LINES),
            messages = extras.messagingTexts(),
            ticker = n.tickerText?.toString(),
        )
        val appLabel = appLabelOf(sbn.packageName)
        return NotificationContent(
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            text = body,
        )
    }

    private fun appLabelOf(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun Bundle.charSeq(key: String): String? =
        getCharSequence(key)?.toString()

    private fun Bundle.charSeqArray(key: String): List<String> =
        getCharSequenceArray(key)?.map { it.toString() }.orEmpty()

    private fun Bundle.messagingTexts(): List<String> {
        val array = getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        val out = mutableListOf<String>()
        for (item in array) {
            val bundle = item as? Bundle ?: continue
            val sender = bundle.getCharSequence("sender")?.toString().orEmpty()
            val text = bundle.getCharSequence("text")?.toString().orEmpty()
            val line = listOf(sender, text).filter { it.isNotBlank() }.joinToString(": ")
            if (line.isNotBlank()) out += line
        }
        return out
    }

    companion object {
        private const val TAG = "NotifyWatch"
    }
}
