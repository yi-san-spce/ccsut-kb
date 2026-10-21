package com.ccsut.kb.ui

import com.ccsut.kb.course
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.util.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 编辑会话纯逻辑: 虚影生命周期 / 回退按钮生存期 / 时间周次变更判定 */
class EditSessionTest {

    private fun block(
        day: Int = 1, startJc: Int = 1, span: Int = 2,
        ranges: List<IntRange> = listOf(1..16), editId: String? = null,
    ): Block = Block(course(day = day, jc = startJc, djs = span, ranges = ranges, editId = editId), startJc, span)

    private fun ghost(
        id: String = "g1", opId: String = "op1",
        bornAt: Long = 0L, ttl: Long = EditSession.GHOST_TTL_MS,
        ranges: List<IntRange> = listOf(1..16), kind: Int = EditSession.GHOST_DELETED,
    ) = Ghost(
        id = id, opId = opId, block = block(ranges = ranges), kind = kind,
        priorEdit = null, bornAt = bornAt, ttlMs = ttl,
    )

    // ---- 虚影生命周期 ----

    @Test
    fun `虚影在生存期内不销毁`() {
        val g = ghost(bornAt = 1_000L)
        assertEquals(listOf(g), EditSession.prune(listOf(g), nowMs = 1_000L + EditSession.GHOST_TTL_MS - 1))
    }

    @Test
    fun `虚影到点销毁`() {
        val g = ghost(bornAt = 1_000L)
        assertTrue(EditSession.prune(listOf(g), nowMs = 1_000L + EditSession.GHOST_TTL_MS).isEmpty())
    }

    @Test
    fun `混合虚影只销毁到期的`() {
        val alive = ghost(id = "a", opId = "opa", bornAt = 5_000L)
        val dead = ghost(id = "d", opId = "opd", bornAt = 0L)
        val out = EditSession.prune(listOf(alive, dead), nowMs = EditSession.GHOST_TTL_MS)
        assertEquals(listOf(alive), out)
    }

    @Test
    fun `无到期虚影时 prune 返回原列表`() {
        val g = ghost(bornAt = 1_000L)
        val out = EditSession.prune(listOf(g), nowMs = 500L)
        assertEquals(g, out.single())   // 同一实例: 未到期不产生新列表
    }

    @Test
    fun `nextExpiryIn 给出最近到期毫秒数_无虚影为null`() {
        val soon = ghost(bornAt = 0L, ttl = 5_000L)
        val later = ghost(bornAt = 0L, ttl = 9_000L)
        assertEquals(1L, EditSession.nextExpiryIn(listOf(soon, later), nowMs = 5_000L))
        assertEquals(3_000L, EditSession.nextExpiryIn(listOf(soon, later), nowMs = 2_000L))
        assertNull(EditSession.nextExpiryIn(emptyList(), nowMs = 0L))
    }

    // ---- 虚影周次覆盖 ----

    @Test
    fun `虚影按原课块周次区间覆盖各周页面`() {
        val g = ghost(ranges = listOf(1..8, 10..12))
        assertTrue(g.coversWeek(1))
        assertTrue(g.coversWeek(8))
        assertFalse(g.coversWeek(9))
        assertTrue(g.coversWeek(12))
        assertFalse(g.coversWeek(13))
    }

    // ---- 时间/周次变更判定 (移位虚影触发条件) ----

    private val oldBlock = block(day = 3, startJc = 5, span = 2, ranges = listOf(1..16))
    private val oldForm = UserEdits.Form(kc = "课", teacher = "", room = "", day = 3, startJc = 5, span = 2, zc = "1-16", ranges = listOf(1..16))

    @Test
    fun `只改名称教师教室不算时间周次变更`() {
        val f = oldForm.copy(kc = "新课名", teacher = "李老师", room = "B202")
        assertFalse(EditSession.touchedTimeOrWeek(f, oldBlock))
    }

    @Test
    fun `改星期或节次或节数算时间变更`() {
        assertTrue(EditSession.touchedTimeOrWeek(oldForm.copy(day = 4), oldBlock))
        assertTrue(EditSession.touchedTimeOrWeek(oldForm.copy(startJc = 6), oldBlock))
        assertTrue(EditSession.touchedTimeOrWeek(oldForm.copy(span = 3), oldBlock))
    }

    @Test
    fun `改周次区间算周次变更`() {
        assertTrue(EditSession.touchedTimeOrWeek(oldForm.copy(ranges = listOf(1..8)), oldBlock))
    }

    // ---- 回退按钮生存期 (以常量语义为准: 最后一次操作后保留一段时间) ----

    @Test
    fun `生存期常量为正且虚影长于按钮`() {
        assertTrue(EditSession.UNDO_VISIBLE_MS > 0)
        assertTrue(EditSession.GHOST_TTL_MS > EditSession.UNDO_VISIBLE_MS)
    }
}
