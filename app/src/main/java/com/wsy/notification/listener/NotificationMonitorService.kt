package com.wsy.notification.listener

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wsy.notification.alert.AlertForegroundService
import com.wsy.notification.alert.AlertItem
import com.wsy.notification.debug.DebugLog
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
        DebugLog.i("Listener", "connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        DebugLog.w("Listener", "disconnected, requestRebind")
        requestRebind(ComponentName(this, NotificationMonitorService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        val selected = prefs.selectedPackages
        val interesting = notification.packageName in selected
        if (!prefs.monitoringEnabled) {
            if (interesting) DebugLog.i("Listener", "skip ${notification.packageName} monitoring=off")
            return
        }
        if (notification.isOngoing) {
            if (interesting) DebugLog.i("Listener", "skip ${notification.packageName} ongoing")
            return
        }
        val flags = notification.notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            if (interesting) DebugLog.i("Listener", "skip ${notification.packageName} group-summary")
            return
        }

        val content = extract(notification)
        if (content == null) {
            DebugLog.w("Listener", "skip ${notification.packageName} extract=null")
            return
        }
        if (!dedup.isNew(notification.key, content.fingerprint())) {
            if (interesting) DebugLog.i("Listener", "skip ${content.packageName} dedup key=${notification.key}")
            return
        }

        val keywords = KeywordMatcher.parseKeywords(prefs.keywordsRaw)
        val reason = KeywordMatcher.diagnose(content, selected, keywords)
        if (interesting || reason.startsWith("HIT")) {
            DebugLog.i(
                "Listener",
                "$reason pkg=${content.packageName} label='${content.appLabel}' " +
                    "title='${content.title}' text='${content.text}'",
            )
        }
        if (!reason.startsWith("HIT")) return

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
}
