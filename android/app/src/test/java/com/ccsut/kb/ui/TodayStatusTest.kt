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

    // ---- 非本周周概要 ----

    private fun dayBlock(day: Int, startJc: Int, span: Int = 2, kc: String = "高等数学") =
        Block(course(kc = kc, day = day), startJc, span)

    @Test
    fun `周概要_空周给假期文案`() {
        val r = TodayStatus.weekSummary(emptyList(), periods)
        assertEquals("整周没有课", r.title)
        assertEquals(TodayStatus.Tone.CALM, r.tone)
        assertTrue("sub=${r.sub}", r.sub!!.contains("假期"))
    }

    @Test
    fun `周概要_标题给节数与门数_不含周数`() {
        // 同一门课两个块 (周一/周三) → 4 节 1 门课
        val blocks = listOf(dayBlock(1, 1), dayBlock(3, 1), dayBlock(3, 3, 2, "大学物理"))
        val r = TodayStatus.weekSummary(blocks, periods)
        assertEquals("共 6 节 · 2 门课", r.title)
        assertTrue("title=${r.title}", !r.title.contains("周"))
    }

    @Test
    fun `周概要_副行只报偏离常态_常规满工作日周只给最早与最忙`() {
        // 周一/三/四/五有课, 周末没课 (常态不说); 周二空闲是偏离 → 点名
        val blocks = listOf(
            dayBlock(1, 1), dayBlock(3, 1), dayBlock(4, 1), dayBlock(4, 3), dayBlock(5, 5),
        )
        val r = TodayStatus.weekSummary(blocks, periods)
        assertEquals("最早 8:20 · 周二没课 · 最忙周四 4 节", r.sub)
    }

    @Test
    fun `周概要_调休周末有课要点名`() {
        // 工作日排满 + 周六有课 (调休): 周末有课值得提醒
        val blocks = (1..5).map { dayBlock(it, 1) } + dayBlock(6, 5, kc = "调休课")
        val r = TodayStatus.weekSummary(blocks, periods)
        assertEquals("最早 8:20 · 周末有课 · 最忙周一 2 节", r.sub)
    }

    @Test
    fun `周概要_只有周末有课`() {
        val blocks = listOf(dayBlock(6, 5, kc = "调休课"), dayBlock(7, 5, kc = "调休课"))
        val r = TodayStatus.weekSummary(blocks, periods)
        assertEquals("最早 14:00 · 只有周末有课", r.sub)
    }

    @Test
    fun `周概要_工作日空闲一到两天点名_三天以上改说仅有课日`() {
        // 空闲两天: 点名; 有课日 1,4,5 → 空闲 2,3 三天 → 改说"仅…有课"
        val two = TodayStatus.weekSummary(listOf(dayBlock(1, 1), dayBlock(4, 1), dayBlock(4, 3), dayBlock(5, 5)), periods)
        assertEquals("最早 8:20 · 周二、周三没课 · 最忙周四 4 节", two.sub)
        val three = TodayStatus.weekSummary(listOf(dayBlock(1, 1), dayBlock(4, 1)), periods)
        assertEquals("最早 8:20 · 仅周一、周四有课", three.sub)
    }

    @Test
    fun `周概要_作息脏数据安全降级`() {
        // 起始节次超出作息 → 最早开课缺失但不崩溃, 其余要点照常
        val blocks = listOf(
            Block(course(kc = "A", day = 1), 99, 2),
            dayBlock(3, 1), dayBlock(4, 1),
        )
        val r = TodayStatus.weekSummary(blocks, periods)
        assertTrue("sub=${r.sub}", r.sub!!.contains("最忙"))
    }
}
