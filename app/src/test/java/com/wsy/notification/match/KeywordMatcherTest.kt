package com.wsy.notification.match

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeywordMatcherTest {

    private fun content(
        pkg: String = MonitoredApp.WECHAT.packageName,
        appLabel: String = "微信",
        title: String = "张三",
        text: String = "晚上吃饭",
    ) = NotificationContent(pkg, appLabel, title, text)

    @Test
    fun emptySelectionNeverMatches() {
        val ok = KeywordMatcher.matches(content(), emptySet(), emptyList())
        assertThat(ok).isFalse()
    }

    @Test
    fun unselectedPackageNeverMatches() {
        val ok = KeywordMatcher.matches(
            content(),
            setOf(MonitoredApp.XIANYU.packageName),
            emptyList(),
        )
        assertThat(ok).isFalse()
    }

    @Test
    fun emptyKeywordsMatchAllFromSelectedApp() {
        val ok = KeywordMatcher.matches(
            content(),
            setOf(MonitoredApp.WECHAT.packageName),
            emptyList(),
        )
        assertThat(ok).isTrue()
    }

    @Test
    fun keywordMatchesTitleCaseInsensitive() {
        val keywords = KeywordMatcher.parseKeywords("WECHAT, hello")
        val ok = KeywordMatcher.matches(
            content(appLabel = "WeChat", title = "Hello World", text = "x"),
            setOf(MonitoredApp.WECHAT.packageName),
            keywords,
        )
        assertThat(ok).isTrue()
    }

    @Test
    fun keywordMatchesBody() {
        val keywords = KeywordMatcher.parseKeywords("已付款")
        val ok = KeywordMatcher.matches(
            content(
                pkg = MonitoredApp.XIANYU.packageName,
                appLabel = "闲鱼",
                title = "买家",
                text = "订单已付款，请发货",
            ),
            setOf(MonitoredApp.XIANYU.packageName),
            keywords,
        )
        assertThat(ok).isTrue()
    }

    @Test
    fun multipleKeywordsAreOr() {
        val keywords = KeywordMatcher.parseKeywords("红包\n已付款")
        val miss = KeywordMatcher.matches(
            content(text = "你好"),
            setOf(MonitoredApp.WECHAT.packageName),
            keywords,
        )
        val hit = KeywordMatcher.matches(
            content(text = "发来一个红包"),
            setOf(MonitoredApp.WECHAT.packageName),
            keywords,
        )
        assertThat(miss).isFalse()
        assertThat(hit).isTrue()
    }

    @Test
    fun parseSplitsChineseAndEnglishSeparators() {
        val keywords = KeywordMatcher.parseKeywords("  张三，李四, 王五、赵六；钱七\n孙八  ")
        assertThat(keywords).containsExactly("张三", "李四", "王五", "赵六", "钱七", "孙八")
            .inOrder()
    }

    @Test
    fun parseIgnoresBlankTokens() {
        val keywords = KeywordMatcher.parseKeywords(",,\n  ，")
        assertThat(keywords).isEmpty()
    }

    @Test
    fun diagnoseExplainsSkipAndHit() {
        val selected = setOf(MonitoredApp.WECHAT.packageName)
        assertThat(
            KeywordMatcher.diagnose(content(), setOf(MonitoredApp.XIANYU.packageName), emptyList()),
        ).startsWith("SKIP")
        assertThat(
            KeywordMatcher.diagnose(content(), selected, emptyList()),
        ).startsWith("HIT")
        assertThat(
            KeywordMatcher.diagnose(content(text = "已付款"), selected, listOf("已付款")),
        ).contains("HIT keyword=")
        assertThat(
            KeywordMatcher.diagnose(
                content(title = "最近收到2条未读消息", text = ""),
                selected,
                listOf("喜欢打包的墩墩"),
            ),
        ).contains("generic-unread")
        assertThat(
            KeywordMatcher.isGenericUnreadSummary(
                content(title = "最近收到2条未读消息", text = ""),
            ),
        ).isTrue()
    }
}

class DedupTrackerTest {
    @Test
    fun sameKeyAndFingerprintIsDuplicate() {
        val tracker = DedupTracker()
        assertThat(tracker.isNew("k1", "a")).isTrue()
        assertThat(tracker.isNew("k1", "a")).isFalse()
    }

    @Test
    fun sameKeyChangedContentIsNew() {
        val tracker = DedupTracker()
        assertThat(tracker.isNew("k1", "a")).isTrue()
        assertThat(tracker.isNew("k1", "b")).isTrue()
    }
}

class NotificationTextExtractorTest {
    @Test
    fun combinesUniqueParts() {
        val body = NotificationTextExtractor.combineBody(
            text = "你好",
            bigText = "你好\n第二行",
            subText = null,
            infoText = "",
            summaryText = "你好",
            textLines = listOf("第二行", "第三行"),
            messages = listOf("张三: 你好"),
            ticker = "微信",
        )
        assertThat(body).contains("你好")
        assertThat(body).contains("第二行")
        assertThat(body).contains("第三行")
        assertThat(body).contains("张三: 你好")
        assertThat(body).contains("微信")
    }

    @Test
    fun titleFallsBackToTicker() {
        val title = NotificationTextExtractor.combineTitle(null, null, "会话")
        assertThat(title).isEqualTo("会话")
    }
}
