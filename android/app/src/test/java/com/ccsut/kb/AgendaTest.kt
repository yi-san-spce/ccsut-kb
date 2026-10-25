package com.ccsut.kb

import com.ccsut.kb.data.Period
import com.ccsut.kb.widget.Agenda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 小组件议程纯函数: 下一次课扫描 / 按天分组 (空态底行与 S 档单行的数据源) */
class AgendaTest {

    // 学期 2026-10-26 (周一) 开学, 16 周 → 周日 10-25 在学期前
    private val start = "2026-10-26"
    private val periods = listOf(
        Period(1, "8:20", "9:50"),
        Period(2, "10:00", "11:30"),
        Period(3, "14:00", "15:30"),
        Period(4, "15:40", "17:10"),
    )

    @Test
    fun todayEmpty_nextClassIsFirstClassedDay() {
        val today = LocalDate.parse("2026-10-25") // 周日, 学期前最后一天
        val courses = listOf(course(day = 1, jc = 1, djs = 2))
        val next = Agenda.nextClass(courses, periods, start, 16, today)
        assertNotNull(next)
        assertEquals(LocalDate.parse("2026-10-26"), next!!.date)
        assertEquals("周一(10/26)", next.label)
        assertEquals("8:20", next.entries.first().tStart)
        assertEquals("高等数学", next.entries.first().block.course.kc)
    }

    @Test
    fun weekendGap_skipsToMonday() {
        val today = LocalDate.parse("2026-10-30") // 周五: 今天有课但 nextClass 只看明天起
        val courses = listOf(
            course(day = 5, jc = 1),
            course(kc = "大学英语", day = 1, jc = 3),
        )
        val next = Agenda.nextClass(courses, periods, start, 16, today)
        assertEquals(LocalDate.parse("2026-11-02"), next!!.date)
        assertTrue(next.entries.isNotEmpty())
    }

    @Test
    fun termOver_returnsNull() {
        // 16 周学期 2027-02-14 结束
        val today = LocalDate.parse("2027-02-20")
        val courses = listOf(course(day = 1, jc = 1))
        assertNull(Agenda.nextClass(courses, periods, start, 16, today))
    }

    @Test
    fun groups_skipEmptyDaysAndLabel() {
        val today = LocalDate.parse("2026-10-28") // 周三: 周三/周五有课, 周四空
        val courses = listOf(
            course(kc = "数据结构", day = 3, jc = 1),
            course(kc = "大学英语", day = 5, jc = 3),
        )
        val g = Agenda.groups(courses, periods, start, 16, today, maxDays = 5)
        assertEquals(2, g.size)
        assertTrue(g[0].label.startsWith("今天"))
        assertTrue(g[1].label.startsWith("周五"))
        assertEquals(1, g[1].entries.size)
    }

    @Test
    fun entriesOfDay_mergesContiguousAndSortsByTime() {
        // 同一门课 jc3/jc4 两行合并为一块; 与 jc1 的课按开始时间排序
        val courses = listOf(
            course(kc = "高数", day = 1, jc = 3, ranges = listOf(1..2)),
            course(kc = "高数", day = 1, jc = 4, ranges = listOf(1..2)),
            course(kc = "体育", day = 1, jc = 1, ranges = listOf(1..2)),
        )
        val es = Agenda.entriesOfDay(courses, periods, 1, 1)
        assertEquals(2, es.size)
        assertEquals("体育", es[0].block.course.kc)
        assertEquals("8:20", es[0].tStart)
        assertEquals("高数", es[1].block.course.kc)
        assertEquals("14:00", es[1].tStart)
        // 连堂块跨 jc3-4: 结束时刻取末节 (jc4) 的 end
        assertEquals("17:10", es[1].tEnd)
    }
}
