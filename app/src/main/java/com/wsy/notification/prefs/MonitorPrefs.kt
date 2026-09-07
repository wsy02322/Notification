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

    fun recordHit(packageName: String, keyword: String) {
        prefs.edit()
            .putString(KEY_HIT_PKG, packageName)
            .putString(KEY_HIT_KEYWORD, keyword)
            .putLong(KEY_HIT_AT, System.currentTimeMillis())
            .apply()
    }

    fun recentHitKeyword(packageName: String, windowMs: Long = 30 * 60 * 1000L): String? {
        if (prefs.getString(KEY_HIT_PKG, "") != packageName) return null
        val at = prefs.getLong(KEY_HIT_AT, 0L)
        if (at <= 0L || System.currentTimeMillis() - at > windowMs) return null
        return prefs.getString(KEY_HIT_KEYWORD, null)?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PREFS_NAME = "monitor_prefs"
        private const val KEY_PACKAGES = "selected_packages"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_MONITORING = "monitoring_enabled"
        private const val KEY_HIT_PKG = "last_hit_pkg"
        private const val KEY_HIT_KEYWORD = "last_hit_keyword"
        private const val KEY_HIT_AT = "last_hit_at"
    }
}
