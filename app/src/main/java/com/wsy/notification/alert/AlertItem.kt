package com.wsy.notification.alert

data class AlertItem(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val atMillis: Long = System.currentTimeMillis(),
)
