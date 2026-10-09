package com.ccsut.kb.util

import com.ccsut.kb.course
import com.ccsut.kb.data.Dataset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeksTest {

    private val start = "2026-09-07" // 周一

    @Test
    fun `开学当天与第一周内都是第1周`() {
        assertEquals(1, Weeks.currentWeek(start, 20, LocalDate.parse("2026-09-07")))
        assertEquals(1, Weeks.currentWeek(start, 20, LocalDate.parse("2026-09-13")))
    }

    @Test
    fun `第8天起进入第2周`() {
        assertEquals(2, Weeks.currentWeek(start, 20, LocalDate.parse("2026-09-14")))
        assertEquals(20, Weeks.currentWeek(start, 20, LocalDate.parse("2027-01-18")))
    }

    @Test
    fun `开学前与溢出都夹回边界`() {
        assertEquals(1, Weeks.currentWeek(start, 20, LocalDate.parse("2026-09-01")))
        assertEquals(20, Weeks.currentWeek(start, 20, LocalDate.parse("2027-06-01")))
    }

    @Test
    fun `日期非法回落第1周`() {
        assertEquals(1, Weeks.currentWeek("not-a-date", 20, LocalDate.parse("2026-09-07")))
    }

    @Test
    fun `weeks为0不抛异常夹回第1周`() {
        // 快照 JSON 显式写 weeks=0 曾使 coerceIn(1,0) 抛 IllegalArgumentException
        assertEquals(1, Weeks.currentWeek(start, 0, LocalDate.parse("2026-09-07")))
    }

    @Test
    fun `dateOf按第1周周一推算`() {
        val d = Dataset(startDate = start, weeks = 20, weekRanges = emptyList(), periods = emptyList(),
            colleges = emptyList(), classes = emptyMap(), version = 0, generatedAt = "", xnxq = "")
        assertEquals(LocalDate.parse("2026-09-15"), Weeks.dateOf(d, 2, 2))
        assertEquals(null, Weeks.dateOf(d.copy(startDate = "bad"), 2, 2))
    }

    @Test
    fun `md与cnDay`() {
        assertEquals("9.15", Weeks.md(LocalDate.parse("2026-09-15")))
        assertEquals("", Weeks.md(null))
        assertEquals("周一", Weeks.cnDay(1))
        assertEquals("周日", Weeks.cnDay(7))
        assertEquals("", Weeks.cnDay(8))
    }
}

class MergerTest {

    @Test
    fun `连续节次合并为一块`() {
        val blocks = Merger.blocksOf(listOf(course(jc = 1), course(jc = 2)), week = 3)
        assertEquals(1, blocks.size)
        assertEquals(1, blocks[0].startJc)
        assertEquals(2, blocks[0].span)
    }

    @Test
    fun `不连续节次拆两块`() {
        val blocks = Merger.blocksOf(listOf(course(jc = 1), course(jc = 3)), week = 3)
        assertEquals(2, blocks.size)
        assertEquals(listOf(1, 3), blocks.map { it.startJc })
    }

    @Test
    fun `不在本周的行被过滤`() {
        val blocks = Merger.blocksOf(listOf(course(jc = 1, ranges = listOf(1..2))), week = 3)
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `同槽位不同课各自成块`() {
        val blocks = Merger.blocksOf(
            listOf(course(jc = 1, kc = "高数"), course(jc = 1, kc = "英语")), week = 3)
        assertEquals(2, blocks.size)
    }

    @Test
    fun `融合块块头优先取被修改的行`() {
        // 单周修改的生效视图: 其余周原行(editId=null) + 该周副本(editId!=null) 同 key 共存,
        // 块头若取到原始行, 详情页判不出 editKind 导致还原按钮消失
        val blocks = Merger.blocksOf(
            listOf(
                course(jc = 1, editId = null),
                course(jc = 2, editId = "e1"),
            ),
            week = 3,
        )
        assertEquals(1, blocks.size)
        assertEquals("e1", blocks[0].course.editId)
        assertEquals(2, blocks[0].span)
        assertEquals(1, blocks[0].editSpan)
    }

    @Test
    fun `editSpan只数同id的行`() {
        val blocks = Merger.blocksOf(
            listOf(
                course(jc = 1, editId = null),
                course(jc = 2, editId = "e1"),
                course(jc = 3, editId = "e1"),
            ),
            week = 3,
        )
        assertEquals(1, blocks.size)
        assertEquals(3, blocks[0].span)
        assertEquals(2, blocks[0].editSpan)
    }

    @Test
    fun `块里混入其它修改记录时editSpan小于span`() {
        // 块头是 e1 (首个 edited 行), e2 的行不计入 editSpan —— 还原只作用于 e1 那一段
        val blocks = Merger.blocksOf(
            listOf(
                course(jc = 1, editId = null),
                course(jc = 2, editId = "e1"),
                course(jc = 3, editId = "e2"),
            ),
            week = 3,
        )
        assertEquals(1, blocks.size)
        assertEquals("e1", blocks[0].course.editId)
        assertEquals(3, blocks[0].span)
        assertEquals(1, blocks[0].editSpan)
    }

    @Test
    fun `跨节教室与教师汇总`() {
        // key 含 room/teacher, 同 session 各节本来就同值 → 单值汇总;
        // distinct/take(2) 是防御逻辑 (防 key 之外的同键混排), 不改变常规输出
        val blocks = Merger.blocksOf(
            listOf(
                course(jc = 1, room = "R1", teacher = "甲"),
                course(jc = 2, room = "R1", teacher = "甲"),
                course(jc = 3, room = "R1", teacher = "甲"),
            ),
            week = 3,
        )
        assertEquals(1, blocks.size)
        assertEquals("R1", blocks[0].rooms)
        assertEquals("甲", blocks[0].teachers)
    }
}
