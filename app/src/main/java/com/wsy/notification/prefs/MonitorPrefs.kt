package com.wsy.notification.prefs

import android.content.Context
import com.wsy.notification.match.MonitoredApp

class MonitorPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedPackages: Set<String>
        get() = prefs.getStringSet(KEY_PACKAGES, MonitoredApp.defaultSelected)
            ?.toSet()
            ?: MonitoredApp.defaultSelected
        set(value) {
            prefs.edit().putStringSet(KEY_PACKAGES, value).apply()
        }

    var keywordsRaw: String
        get() = prefs.getString(KEY_KEYWORDS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_KEYWORDS, value).apply()
        }

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MONITORING, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "monitor_prefs"
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_MONITORING = "monitoring_enabled"
    }
}
