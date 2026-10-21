package com.ccsut.kb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 课程颜色注册表: 持久化只增不减 —— 删除/恢复课程不得重排其余课程的颜色 */
class CourseColorsRegistryTest {

    @Test
    fun `删除课程后其余课程的槽位不变`() {
        val initial = CourseColors.mergeSeedOrder(emptyList(), listOf("数学", "英语", "物理"))
        // 与旧版分配一致的初始化: 按码位排序后按序占槽 (数 < 物 < 英)
        assertEquals(listOf("数学", "物理", "英语"), initial)

        val afterDelete = CourseColors.mergeSeedOrder(initial, listOf("数学", "物理"))
        // 删除"英语"后: 表保持不变, "数学""物理"槽位不动 → 颜色不变
        assertEquals(initial, afterDelete)
    }

    @Test
    fun `新增课程追加到表尾_既有课程不动`() {
        val initial = listOf("英语", "数学", "物理")
        val afterAdd = CourseColors.mergeSeedOrder(initial, listOf("数学", "物理", "化学"))
        assertEquals(listOf("英语", "数学", "物理", "化学"), afterAdd)
    }

    @Test
    fun `恢复被删除的课程回到原来的颜色槽位`() {
        val initial = listOf("英语", "数学", "物理")
        // "英语"被删过又恢复: 注册表里它从未被移除, 回到原槽位
        val afterRestore = CourseColors.mergeSeedOrder(initial, listOf("英语", "数学", "物理"))
        assertEquals(initial, afterRestore)
    }

    @Test
    fun `空注册表时按当前课程排序初始化`() {
        val out = CourseColors.mergeSeedOrder(emptyList(), listOf("物理", "数学"))
        assertEquals(listOf("数学", "物理"), out)
    }

    @Test
    fun `注册表已有条目时新顺序不参与重排`() {
        // 旧版 bug 复现: 生效列表顺序变化不应引起重排
        val persisted = listOf("数学", "物理", "英语")
        val out = CourseColors.mergeSeedOrder(persisted, listOf("英语", "数学"))
        assertEquals(persisted, out)
    }
}
