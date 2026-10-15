package com.ccsut.kb.data

import com.ccsut.kb.util.Merger
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

    // ---- applyEdits: 生效视图合成 (拖拽产生的编辑序列) ----

    private fun course(jc: Int, kc: String = "高数", day: Int = 1, zc: String = "1-16") = Course(
        kc = kc, teacher = "张三", room = "A101", day = day, jc = jc, djs = ((jc + 1) / 2),
        zc = zc, ranges = listOf(1..16), fx = "", jxb = "j", jxbzc = "", type = 1,
    )

    private fun match(kc: String = "高数", day: Int = 1, s: Int = 1, e: Int = 2, zc: String = "1-16") =
        UserEdits.Match(kc = kc, fx = "", day = day, startJc = s, endJc = e, zc = zc)

    @Test
    fun `单周拖拽_原始行扣周保留_该周输出副本`() {
        val pristine = listOf(course(1), course(2))
        val edit = UserEdits.Edit(
            id = "e1", bjid = "b", kind = 1, week = 3,
            match = match(), delta = UserEdits.Delta(day = 2, startJc = 3),
        )
        val out = UserEdits.applyEdits(pristine, listOf(edit))
        // 其余周原位保留 (1-2, 4-16), 编辑行不残留
        val kept = out.filter { it.editId == null }
        assertEquals(listOf(1, 2), kept.map { it.jc })
        kept.forEach { assertEquals(listOf(1..2, 4..16), it.ranges) }
        // 第 3 周副本: 移到周二第 3-4 节, 仅第 3 周生效
        val copy = out.filter { it.editId == "e1" }
        assertEquals(listOf(3, 4), copy.map { it.jc })
        copy.forEach {
            assertEquals(2, it.day)
            assertEquals(listOf(3..3), it.ranges)
        }
    }

    @Test
    fun `同锚点多条单周编辑_各自认领各自的周`() {
        val pristine = listOf(course(1), course(2))
        val edits = listOf(
            UserEdits.Edit(
                id = "e1", bjid = "b", kind = 1, week = 3,
                match = match(), delta = UserEdits.Delta(day = 2, startJc = 3),
            ),
            // 第二次拖拽(第 4 周): 与 e1 同锚点 —— 旧实现里它的模板行被 e1 独占, 整条被静默丢弃
            UserEdits.Edit(
                id = "e2", bjid = "b", kind = 1, week = 4,
                match = match(), delta = UserEdits.Delta(day = 5, startJc = 7),
            ),
        )
        val result = UserEdits.applyEdits(pristine, edits)
        // 基础行同时扣除第 3、4 周
        result.filter { it.editId == null }.forEach {
            assertEquals(listOf(1..2, 5..16), it.ranges)
        }
        // 两周的副本都在
        assertEquals(listOf(3, 4), result.filter { it.editId == "e1" }.map { it.jc })
        assertEquals(listOf(7, 8), result.filter { it.editId == "e2" }.map { it.jc })
        assertEquals(5, result.filter { it.editId == "e2" }.first().day)
    }

    @Test
    fun `全周修改与单周编辑共存_全周输出扣除被认领周`() {
        val pristine = listOf(course(1), course(2))
        val edits = listOf(
            // 全周改名 (未动位置, ranges 未拆)
            UserEdits.Edit(
                id = "e1", bjid = "b", kind = 1, week = null,
                match = match(), delta = UserEdits.Delta(kc = "高等数学"),
            ),
            // 异常路径产生的单周编辑 (全周修改在场时又拖了单周)
            UserEdits.Edit(
                id = "e2", bjid = "b", kind = 1, week = 3,
                match = match(), delta = UserEdits.Delta(day = 2, startJc = 3),
            ),
        )
        val result = UserEdits.applyEdits(pristine, edits)
        // 全周行不再覆盖第 3 周 (否则与单周副本双重输出, 同槽两张卡)
        val fullWeek = result.filter { it.editId == "e1" }
        assertEquals(2, fullWeek.size)
        fullWeek.forEach {
            assertEquals("高等数学", it.kc)
            assertFalse(it.ranges.any { r -> 3 in r })
        }
        assertEquals(listOf(3, 4), result.filter { it.editId == "e2" }.map { it.jc })
    }

    @Test
    fun `全周修改改节数_按新节数展开成长块`() {
        // 编辑表单把 2 节课改成 4 节: delta.span=4, 应输出 jc 1-4 共 4 行 (同 key 连续 → 合并成长卡)
        val pristine = listOf(course(1), course(2))
        val edit = UserEdits.Edit(
            id = "e1", bjid = "b", kind = 1, week = null,
            match = match(), delta = UserEdits.Delta(span = 4),
        )
        val result = UserEdits.applyEdits(pristine, listOf(edit))
        val rows = result.filter { it.editId == "e1" }
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.jc })
        // 渲染层按行数定卡高, 这里锁定行数即锁定长卡
        assertEquals(4, Merger.blocksOf(result, week = 3).single().span)
    }

    @Test
    fun `单周修改改节数_该周副本按新节数展开`() {
        // 拖拽产生的单周修改再进编辑表单改节数: 副本从 2 节变 5 节, 只在该周生效
        val pristine = listOf(course(1), course(2))
        val edit = UserEdits.Edit(
            id = "e1", bjid = "b", kind = 1, week = 3,
            match = match(), delta = UserEdits.Delta(span = 5),
        )
        val result = UserEdits.applyEdits(pristine, listOf(edit))
        // 其余周原位保留
        result.filter { it.editId == null }.forEach {
            assertEquals(listOf(1..2, 4..16), it.ranges)
        }
        // 第 3 周副本 5 行 → 5 节长卡
        val copy = result.filter { it.editId == "e1" }
        assertEquals(listOf(1, 2, 3, 4, 5), copy.map { it.jc })
        copy.forEach { assertEquals(listOf(3..3), it.ranges) }
        assertEquals(5, Merger.blocksOf(result, week = 3).single().span)
        // 其他周不受影响: 仍是原 2 节
        assertEquals(2, Merger.blocksOf(result, week = 4).single().span)
    }
}
