package com.wsy.notification.match

/**
 * 微信等会反复刷新同一条通知。相同 key + 相同内容视为重复。
 */
class DedupTracker(private val maxSize: Int = 200) {
    private val last = object : LinkedHashMap<String, String>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxSize
    }

    /** @return true 表示这是新通知或内容已变化，应当再跑匹配。 */
    @Synchronized
    fun isNew(key: String, fingerprint: String): Boolean {
        if (last[key] == fingerprint) return false
        last[key] = fingerprint
        return true
    }

    @Synchronized
    fun clear() {
        last.clear()
    }
}
