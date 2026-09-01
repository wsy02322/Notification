package com.wsy.notification.match

enum class MonitoredApp(
    val packageName: String,
    val displayName: String,
    val required: Boolean,
) {
    WECHAT("com.tencent.mm", "微信", true),
    XIANYU("com.taobao.idlefish", "闲鱼", true),
    XIAOHONGSHU("com.xingin.xhs", "小红书", false),
    DOUYIN("com.ss.android.ugc.aweme", "抖音", false);

    companion object {
        val all = entries
        val defaultSelected: Set<String> =
            entries.filter { it.required }.map { it.packageName }.toSet()

        fun displayNameOf(packageName: String): String =
            entries.firstOrNull { it.packageName == packageName }?.displayName ?: packageName
    }
}
