package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 远程通知: 解析 + 选取规则 (排期窗口 / 版本定向 / 去重 / 优先级排序) */
class NoticeTest {

    private val NOW = LocalDateTime.of(2026, 10, 15, 12, 0, 0)

    private fun parse(json: String): List<RemoteNotice> =
        RemoteNotices.parse(org.json.JSONObject(json))

    private fun select(
        json: String,
        now: LocalDateTime = NOW,
        ver: String = "1.0.0",
        seen: Set<String> = emptySet(),
    ): RemoteNotice? = RemoteNotices.select(parse(json), now, ver, seen)

    @Test
    fun `数组形态完整字段解析`() {
        val n = parse(
            """{"notices":[{"id":"m1","title":"停课","message":"周三停课","url":"https://e.com/x",
                "once":false,"priority":"critical","startAt":"2026-10-01 00:00:00","endAt":"2026-10-31",
                "minVersion":"1.0.0","maxVersion":"2.0.0"}]}"""
        ).single()
        assertEquals("m1", n.id)
        assertEquals("停课", n.title)
        assertEquals("周三停课", n.message)
        assertEquals("https://e.com/x", n.url)
        assertEquals(false, n.once)
        assertEquals(true, n.critical)
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0, 0), n.startAt)
        assertEquals(LocalDateTime.of(2026, 10, 31, 23, 59, 59), n.endAt)   // 纯日期 = 含当天
        assertEquals("1.0.0", n.minVersion)
        assertEquals("2.0.0", n.maxVersion)
    }

    @Test
    fun `顶层track透传给每条公告且非法值丢弃`() {
        val list = parse(
            """{"track":"https://www.vercount.one/api/v2/log","notices":[{"id":"a","message":"m"},{"id":"b","message":"n"}]}"""
        )
        assertEquals(2, list.size)
        assertEquals("https://www.vercount.one/api/v2/log", list[0].track)
        assertEquals(list[0].track, list[1].track)
        // 非 https 丢弃 (与 url 同策略); 无 track 字段为 null
        assertNull(parse("""{"track":"http://evil.example","notices":[{"message":"m"}]}""").single().track)
        assertNull(parse("""{"notices":[{"message":"m"}]}""").single().track)
    }

    @Test
    fun `旧版单对象简写兼容且id由内容派生`() {
        val n = parse("""{"message":"内容","url":"ftp://x"}""").single()
        assertEquals("通知", n.title)          // title 缺省
        assertEquals("内容", n.message)
        assertEquals(null, n.url)             // 非法协议丢弃
        assertEquals(true, n.once)            // 默认弹一次
        assertEquals(false, n.critical)
        assertEquals(16, n.id.length)         // 内容派生 id
        // 相同内容派生同一 id, 改文案即换 id
        assertEquals(n.id, parse("""{"message":"内容"}""").single().id)
        assertNotEquals(n.id, parse("""{"message":"内容2"}""").single().id)
    }

    @Test
    fun `message缺失的条目跳过`() {
        assertTrue(parse("""{"notices":[{"title":"t"},{"message":"ok"}]}""").map { it.message } == listOf("ok"))
        assertTrue(parse("""{"title":"t"}""").isEmpty())
    }

    @Test
    fun `排期窗口闭区间过滤`() {
        val j = """{"notices":[{"id":"a","message":"m","startAt":"2026-10-10 00:00:00","endAt":"2026-10-20 23:59:59"}]}"""
        assertEquals("a", select(j, now = LocalDateTime.of(2026, 10, 10, 0, 0, 0))?.id)    // 命中起点
        assertEquals("a", select(j, now = LocalDateTime.of(2026, 10, 20, 23, 59, 59))?.id) // 命中终点
        assertNull(select(j, now = LocalDateTime.of(2026, 10, 9, 23, 59, 59)))
        assertNull(select(j, now = LocalDateTime.of(2026, 10, 21, 0, 0, 0)))
    }

    @Test
    fun `endAt纯日期含当天全天`() {
        val j = """{"notices":[{"id":"a","message":"m","endAt":"2026-10-15"}]}"""
        assertEquals("a", select(j, now = LocalDateTime.of(2026, 10, 15, 23, 59, 59))?.id)
        assertNull(select(j, now = LocalDateTime.of(2026, 10, 16, 0, 0, 0))?.id)
    }

    @Test
    fun `时间格式兼容空格分隔与带时区`() {
        val n = parse("""{"notices":[{"message":"m","startAt":"2026-10-01T08:30+08:00"}]}""").single()
        // +08:00 换算到测试机时区后再比较 (测试跑在本地时区, 换算结果确定性取决于机器, 只验证能解析出值)
        assertTrue(n.startAt != null)
        val n2 = parse("""{"notices":[{"message":"m","startAt":"2026-10-01 08:30"}]}""").single()
        assertEquals(LocalDateTime.of(2026, 10, 1, 8, 30), n2.startAt)
    }

    @Test
    fun `时间字段笔误不杀死整条通知`() {
        val n = parse("""{"notices":[{"message":"m","endAt":"明天"}]}""").single()
        assertEquals(null, n.endAt)
        assertEquals("m", n.message)
    }

    @Test
    fun `版本定向区间过滤`() {
        val max = """{"notices":[{"id":"a","message":"m","maxVersion":"1.0.0"}]}"""
        assertEquals("a", select(max, ver = "1.0.0")?.id)
        assertNull(select(max, ver = "1.0.1"))
        // 开发者版 (dev 后缀) 在同主版本里排最高, 不算 <= 1.0.0
        assertNull(select(max, ver = "1.0.0-dev"))
        val min = """{"notices":[{"id":"a","message":"m","minVersion":"2.0.0"}]}"""
        assertNull(select(min, ver = "1.0.0"))
        assertEquals("a", select(min, ver = "2.0.0")?.id)
    }

    @Test
    fun `link字段解析_缺省为null_url回退不受影响`() {
        val n = parse("""{"notices":[{"id":"a","message":"m","link":"personal-login","url":"https://e.com/x"}]}""").single()
        assertEquals("personal-login", n.link)
        assertEquals("https://e.com/x", n.url)   // 新版跳转失败/旧版本忽略 link 时走 url
        assertNull(parse("""{"notices":[{"message":"m"}]}""").single().link)
    }

    @Test
    fun `按钮文案可自定义且缺省确认取消`() {
        val n = parse("""{"notices":[{"message":"m","confirmText":"去频道看看","dismissText":"知道了"}]}""").single()
        assertEquals("去频道看看", n.confirmText)
        assertEquals("知道了", n.dismissText)
        val d = parse("""{"notices":[{"message":"m"}]}""").single()
        assertEquals("确认", d.confirmText)
        assertEquals("取消", d.dismissText)
    }

    @Test
    fun `selectAll命中多条时critical在前同级保持文件顺序`() {
        val list = parse(
            """{"notices":[
                {"id":"a","message":"m1"},
                {"id":"b","message":"m2","priority":"critical"},
                {"id":"c","message":"m3","startAt":"2099-01-01"},
                {"id":"d","message":"m4","priority":"critical"}]}"""
        )
        val picked = RemoteNotices.selectAll(list, NOW, "1.0.0", emptySet())
        // c 被排期过滤; critical 的 b/d 在前且保持文件顺序, 普通通知 a 在后
        assertEquals(listOf("b", "d", "a"), picked.map { it.id })
    }

    @Test
    fun `selectAll去重过滤已读`() {
        val list = parse("""{"notices":[{"id":"a","message":"m1"},{"id":"b","message":"m2","once":false}]}""")
        val picked = RemoteNotices.selectAll(list, NOW, "1.0.0", setOf("a"))
        assertEquals(listOf("b"), picked.map { it.id })
    }

    @Test
    fun `select等价于selectAll首条`() {
        val json = """{"notices":[{"id":"a","message":"m1"},{"id":"b","message":"m2","priority":"critical"}]}"""
        assertEquals(
            RemoteNotices.selectAll(parse(json), NOW, "1.0.0", emptySet()).firstOrNull()?.id,
            RemoteNotices.select(parse(json), NOW, "1.0.0", emptySet())?.id,
        )
    }

    @Test
    fun `once去重_once_false不受已读影响`() {
        val j = """{"notices":[{"id":"a","message":"m"},{"id":"b","message":"m2","once":false}]}"""
        assertEquals("b", select(j, seen = setOf("a"))?.id)       // a 已读被跳过, 轮到 b
        assertEquals("b", select(j, seen = setOf("a", "b"))?.id)  // b 是 once=false, 已读不影响
        assertEquals("a", select(j, seen = emptySet())?.id)       // 无已读, 文件顺序优先
    }

    @Test
    fun `优先级critical先行_同级保持文件顺序`() {
        val j = """{"notices":[
            {"id":"a","message":"m1","priority":"critical"},
            {"id":"b","message":"m2"},
            {"id":"c","message":"m3","priority":"critical"}]}"""
        // critical 两条里保持文件顺序 (a 在 c 前), 且压过普通条 b
        assertEquals("a", select(j)?.id)
        val j2 = """{"notices":[
            {"id":"b","message":"m2"},
            {"id":"a","message":"m1","priority":"critical"}]}"""
        assertEquals("a", select(j2)?.id)
    }

    @Test
    fun `全部不命中返回null`() {
        assertNull(select("""{"notices":[]}"""))
        assertNull(select("""{"notices":[{"id":"a","message":"m","startAt":"2027-01-01"}]}"""))
    }
}
