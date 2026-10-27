package com.ccsut.kb.ui

import com.ccsut.kb.data.Course
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.Merger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 课前全屏聚焦状态机: 窗口判定 / 上课中优先 / 脏作息防御 / 去重键 */
class PreClassTest {

    private val today = LocalDate.of(2026, 10, 27)   // 周二

    private val periods = listOf(
        Period(1, "8:20", "9:05"),
        Period(2, "9:15", "10:00"),
        Period(3, "10:20", "11:05"),
        Period(4, "11:15", "12:00"),
        Period(5, "14:00", "14:45"),
        Period(6, "14:55", "15:40"),
    )

    private fun course(kc: String, jc: Int, day: Int = 2, room: String = "A101", fx: String = "") =
        Course(kc, "王老师", room, day, jc, 1, "1-16", listOf(1..16), fx, "", "", 1)

    private fun block(kc: String, jc: Int, span: Int = 1, room: String = "A101", fx: String = "") =
        Block(course(kc, jc, room = room, fx = fx), jc, span, room, "王老师")

    @Test
    fun `距下节课在窗口内_返回BEFORE`() {
        val st = PreClass.compute(listOf(block("高数", 1)), periods, nowMin = 8 * 60, windowMin = 20, today = today)
        assertEquals(PreClass.Kind.BEFORE, st?.kind)
        assertEquals(20, st?.mins)
        assertEquals(8 * 60 + 20, st?.startMin)
    }

    @Test
    fun `刚好等于窗口_算触发_超一分钟不算`() {
        assertEquals(PreClass.Kind.BEFORE, PreClass.compute(listOf(block("高数", 1)), periods, 8 * 60, 20, today)?.kind)
        assertNull(PreClass.compute(listOf(block("高数", 1)), periods, 7 * 60 + 59, 20, today))
    }

    @Test
    fun `上课中_返回CURRENT且分钟为距下课`() {
        val st = PreClass.compute(listOf(block("高数", 1)), periods, 8 * 60 + 30, 20, today)
        assertEquals(PreClass.Kind.CURRENT, st?.kind)
        assertEquals(35, st?.mins)
    }

    @Test
    fun `上课中优先于课间下一节`() {
        val blocks = listOf(block("高数", 1), block("英语", 3, span = 2))
        // 8:30 正在上高数, 即使下一节英语已在窗口内也返回 CURRENT
        val st = PreClass.compute(blocks, periods, 8 * 60 + 30, 60, today)
        assertEquals(PreClass.Kind.CURRENT, st?.kind)
        assertEquals("高数", st?.block?.course?.kc)
    }

    @Test
    fun `课间取最近的下一节`() {
        val blocks = listOf(block("高数", 1), block("英语", 3), block("体育", 5))
        val st = PreClass.compute(blocks, periods, 9 * 60 + 30, 60, today)
        assertEquals(PreClass.Kind.BEFORE, st?.kind)
        assertEquals("英语", st?.block?.course?.kc)
        assertEquals(50, st?.mins)
    }

    @Test
    fun `脏作息的块被跳过`() {
        val dirty = listOf(Period(1, "", "9:05")) + periods.drop(1)
        val st = PreClass.compute(listOf(block("高数", 1), block("英语", 3)), dirty, 9 * 60 + 30, 60, today)
        assertEquals("英语", st?.block?.course?.kc)
    }

    @Test
    fun `无课或窗口外返回null`() {
        assertNull(PreClass.compute(emptyList(), periods, 9 * 60, 20, today))
        assertNull(PreClass.compute(listOf(block("高数", 5)), periods, 9 * 60, 20, today))
    }

    @Test
    fun `去重键按日期区分_同一实例稳定`() {
        val b = block("高数", 1)
        val k1 = PreClass.keyOf(b, today)
        val k2 = PreClass.keyOf(b, today.plusDays(7))
        assertNotEquals(k1, k2)
        assertEquals(k1, PreClass.keyOf(b, today))
    }

    @Test
    fun `连堂块按合并后时刻计算`() {
        val merged = Merger.blocksOf(listOf(course("高数", 3), course("高数", 4)), 1)
        // 10:00 距 10:20 开课 20 分钟 → BEFORE, 时刻取合并块 10:20–12:00
        val st = PreClass.compute(merged, periods, 10 * 60, 60, today)
        assertEquals(PreClass.Kind.BEFORE, st?.kind)
        assertEquals(10 * 60 + 20, st?.startMin)
        assertEquals(12 * 60, st?.endMin)
        // 连堂进行中: 剩余到 12:00
        val cur = PreClass.compute(merged, periods, 10 * 60 + 30, 60, today)
        assertEquals(PreClass.Kind.CURRENT, cur?.kind)
        assertEquals(90, cur?.mins)
    }
}
