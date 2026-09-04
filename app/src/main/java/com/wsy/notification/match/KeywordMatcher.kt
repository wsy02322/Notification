package com.wsy.notification.match

import java.util.Locale

object KeywordMatcher {
    private val separators = Regex("[\n,，、;；]+")

    fun parseKeywords(raw: String): List<String> =
        raw.split(separators)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * App 必须在勾选列表中。关键词为空则该 App 每条都命中；
     * 否则任一关键词出现在 App 名、发送人或正文中即命中（不区分大小写）。
     */
    fun matches(
        content: NotificationContent,
        selectedPackages: Set<String>,
        keywords: List<String>,
    ): Boolean = diagnose(content, selectedPackages, keywords).startsWith("HIT")

    fun diagnose(
        content: NotificationContent,
        selectedPackages: Set<String>,
        keywords: List<String>,
    ): String {
        if (content.packageName !in selectedPackages) {
            return "SKIP package=${content.packageName} not-selected"
        }
        if (keywords.isEmpty()) return "HIT empty-keywords"
        val haystack = content.haystack().foldCase()
        val hit = keywords.firstOrNull { keyword -> haystack.contains(keyword.foldCase()) }
        return if (hit != null) {
            "HIT keyword='$hit'"
        } else {
            "SKIP no-keyword keywords=$keywords"
        }
    }

    private fun String.foldCase(): String = lowercase(Locale.ROOT)
}
