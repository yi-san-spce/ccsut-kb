package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UserEdits 的纯周次工具: 拖拽落点占用判定 / 单周拆分 / 周次文本 */
class UserEditsHelpersTest {

    @Test
    fun `rangesOverlap相交判定`() {
        val a = listOf(1..4)
        val b = listOf(5..8)
        assertFalse(UserEdits.rangesOverlap(a, b))
        assertTrue(UserEdits.rangesOverlap(a, listOf(4..6)))     // 首尾相接算相交
        assertTrue(UserEdits.rangesOverlap(listOf(1..2, 10..12), listOf(11..13)))
        assertFalse(UserEdits.rangesOverlap(listOf(1..2), listOf(3..5, 8..9)))
    }

    @Test
    fun `minusWeek中段扣除拆成两段`() {
        assertEquals(listOf(1..4, 6..16), UserEdits.minusWeek(listOf(1..16), 5))
    }

    @Test
    fun `minusWeek边界扣除`() {
        assertEquals(listOf(2..16), UserEdits.minusWeek(listOf(1..16), 1))
        assertEquals(listOf(1..15), UserEdits.minusWeek(listOf(1..16), 16))
        assertEquals(emptyList<IntRange>(), UserEdits.minusWeek(listOf(5..5), 5))
    }

    @Test
    fun `minusWeek不含该周原样返回`() {
        val src = listOf(1..4, 8..12)
        assertEquals(src, UserEdits.minusWeek(src, 6))
    }

    @Test
    fun `zcOf离散周次合并成区间文本`() {
        val (text, ranges) = UserEdits.zcOf(setOf(1, 2, 3, 5, 9, 10))
        assertEquals("1-3,5,9-10", text)
        assertEquals(listOf(1..3, 5..5, 9..10), ranges)
    }

    @Test
    fun `zcText与zcOf同格式`() {
        assertEquals("1-4,6", UserEdits.zcText(listOf(1..4, 6..6)))
    }
}
