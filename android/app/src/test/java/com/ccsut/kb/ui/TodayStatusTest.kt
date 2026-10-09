package com.ccsut.kb.ui

import com.ccsut.kb.course
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 今日状态栏状态机: 各时段分支与色调 */
class TodayStatusTest {

    // 1-8 节作息 (与教务一致的无前导零格式)
    private val periods = listOf(
        Period(1, "8:20", "9:05"), Period(2, "9:10", "9:55"),
        Period(3, "10:15", "11:00"), Period(4, "11:05", "11:50"),
        Period(5, "14:00", "14:45"), Period(6, "14:50", "15:35"),
        Period(7, "16:00", "16:45"), Period(8, "16:50", "17:35"),
    )

    private fun block(startJc: Int, span: Int, kc: String = "高等数学", room: String = "A101") =
        Block(course(kc = kc, room = room), startJc, span, rooms = room)

    private val today = LocalDate.of(2026, 10, 7) // 周三

    @Test
    fun `上课中显示剩余时间与教室`() {
        val r = TodayStatus.compute(listOf(block(1, 2)), periods, nowMin = 8 * 60 + 30, dayOfWeek = 3, tomorrowEarly = null, today = today)
        assertEquals(TodayStatus.Tone.ACTIVE, r.tone)
        assertEquals("正在上 高等数学", r.title)
        assertTrue("sub=${r.sub}", r.sub!!.contains("1 小时 25 分") && r.sub!!.contains("A101"))
    }

    @Test
    fun `临近上课45分钟内倒计时`() {
        // 第二节 9:10 前的 9:57
        val r = TodayStatus.compute(listOf(block(1, 2), block(3, 2, "大学物理")), periods, nowMin = 9 * 60 + 57, dayOfWeek = 3, tomorrowEarly = null, today = today)
        assertEquals(TodayStatus.Tone.ACTIVE, r.tone)
        assertTrue("title=${r.title}", r.title.startsWith("18 分钟后要上课啦"))
    }

    @Test
    fun `午间长课间报下节与时长`() {
        // 上午课上完(11:50 结束), 下一节 14:00; 12:00 → 2 小时后
        val r = TodayStatus.compute(listOf(block(1, 4), block(5, 2, "数据结构")), periods, nowMin = 12 * 60, dayOfWeek = 3, tomorrowEarly = null, today = today)
        assertTrue("title=${r.title}", r.title.contains("2 小时"))
        assertTrue("sub=${r.sub}", r.sub!!.contains("数据结构"))
    }

    @Test
    fun `早八前报今日节数与第一节`() {
        val r = TodayStatus.compute(listOf(block(1, 2), block(5, 2)), periods, nowMin = 7 * 60, dayOfWeek = 3, tomorrowEarly = null, today = today)
        assertTrue("title=${r.title}", r.title.contains("共 4 节课"))
        assertTrue("sub=${r.sub}", r.sub!!.contains("第一节 8:20"))
    }

    @Test
    fun `满课走专属文案池`() {
        val r = TodayStatus.compute(listOf(block(1, 4), block(5, 4)), periods, nowMin = 7 * 60, dayOfWeek = 3, tomorrowEarly = null, today = today)
        assertTrue("sub=${r.sub}", r.sub!!.contains("共 8 节课"))
    }

    @Test
    fun `放学后周五与工作日文案不同`() {
        val r3 = TodayStatus.compute(listOf(block(1, 2)), periods, nowMin = 18 * 60, dayOfWeek = 3, tomorrowEarly = null, today = today)
        val r5 = TodayStatus.compute(listOf(block(1, 2)), periods, nowMin = 18 * 60, dayOfWeek = 5, tomorrowEarly = null, today = today)
        assertEquals(TodayStatus.Tone.JOY, r3.tone)
        assertEquals(TodayStatus.Tone.JOY, r5.tone)
        assertTrue(r3.title != r5.title)
    }

    @Test
    fun `晚间提示明天早八`() {
        val r = TodayStatus.compute(emptyList(), periods, nowMin = 21 * 60 + 30, dayOfWeek = 3, tomorrowEarly = block(1, 2, "大学英语"), today = today)
        assertEquals(TodayStatus.Tone.NIGHT, r.tone)
        // 文案池按天取模, 两条分别为「明天有早八…」/「明早的课…」
        assertTrue("title=${r.title}", r.title.contains("早八") || r.title.contains("明早"))
    }

    @Test
    fun `周末全天无课`() {
        val r = TodayStatus.compute(emptyList(), periods, nowMin = 10 * 60, dayOfWeek = 6, tomorrowEarly = null, today = today)
        assertEquals(TodayStatus.Tone.JOY, r.tone)
    }

    @Test
    fun `作息缺节的块被安全过滤不崩溃`() {
        // 只给了第 1-2 节作息, 第 5 节的块应被过滤而非抛异常
        val r = TodayStatus.compute(listOf(block(5, 2)), periods.take(2), nowMin = 10 * 60, dayOfWeek = 3, tomorrowEarly = null, today = today)
        assertTrue(r.title.isNotBlank())
    }
}
