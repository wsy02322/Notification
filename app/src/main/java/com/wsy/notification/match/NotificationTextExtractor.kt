package com.wsy.notification.match

/** 把通知 extras 里常见的标题/正文拼成可搜索文本，纯 Kotlin 便于单测。 */
object NotificationTextExtractor {
    fun combineTitle(title: String?, ticker: String?): String {
        val t = title?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return ticker?.trim().orEmpty()
    }

    fun combineBody(
        text: String?,
        bigText: String?,
        subText: String?,
        infoText: String?,
        summaryText: String?,
        textLines: List<String>,
        messages: List<String>,
        ticker: String?,
    ): String {
        val parts = linkedSetOf<String>()
        fun add(value: String?) {
            val v = value?.trim().orEmpty()
            if (v.isNotEmpty()) parts += v
        }
        add(text)
        add(bigText)
        add(subText)
        add(infoText)
        add(summaryText)
        textLines.forEach { add(it) }
        messages.forEach { add(it) }
        add(ticker)
        return parts.joinToString("\n")
    }
}
