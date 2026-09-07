package com.wsy.notification.match

data class NotificationContent(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
) {
    fun fingerprint(): String = "$title\n$text"

    fun haystack(): String = listOf(appLabel, title, text)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}
