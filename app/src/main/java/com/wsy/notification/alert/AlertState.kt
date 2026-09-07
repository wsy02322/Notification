package com.wsy.notification.alert

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlertState {
    private val itemsInternal = MutableStateFlow<List<AlertItem>>(emptyList())
    private val alertingInternal = MutableStateFlow(false)

    val items: StateFlow<List<AlertItem>> = itemsInternal.asStateFlow()
    val isAlerting: StateFlow<Boolean> = alertingInternal.asStateFlow()

    @Synchronized
    fun add(item: AlertItem) {
        itemsInternal.value = itemsInternal.value + item
        alertingInternal.value = true
    }

    @Synchronized
    fun snapshot(): List<AlertItem> = itemsInternal.value

    @Synchronized
    fun clear() {
        itemsInternal.value = emptyList()
        alertingInternal.value = false
    }
}
