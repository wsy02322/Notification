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
    ): Boolean {
        if (content.packageName !in selectedPackages) return false
        if (keywords.isEmpty()) return true
        val haystack = content.haystack().foldCase()
        return keywords.any { keyword -> haystack.contains(keyword.foldCase()) }
    }

    private fun String.foldCase(): String = lowercase(Locale.ROOT)
}
