package com.ccsut.kb.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 通知链纯函数: 投递容差 / 周次计算（学期开始前不误发） */
class ReminderRoutingTest {

    @Test
    fun `迟到投递容差_精确30分钟_降级60分钟`() {
        assertEquals(30L, ReminderScheduler.dueWindowMin(exact = true))
        assertEquals(60L, ReminderScheduler.dueWindowMin(exact = false))
    }

    // ---- 周次计算: 学期开始前不得误发「假期提醒」 ----
    // 真实 bug: Int 除法向零截断, 开学前 1-6 天 between=-1..-6 除以 7 得 0, +1 后=第 1 周,
    // 会用第 1 周课表在开学前的日期上误排提醒 (用户假期里收到「还有 15 分钟上课」)

    @Test
    fun `学期开始当天与当周内算第1周`() {
        val start = LocalDate.of(2026, 9, 7)  // 周一
        assertEquals(1, ReminderScheduler.weekOf(start, start))
        assertEquals(1, ReminderScheduler.weekOf(start, LocalDate.of(2026, 9, 13)))  // 周日
        assertEquals(2, ReminderScheduler.weekOf(start, LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun `学期开始前6天返回0_修复负数截断`() {
        val start = LocalDate.of(2026, 9, 7)  // 周一
        assertEquals(0, ReminderScheduler.weekOf(start, LocalDate.of(2026, 9, 6)))   // 前一天
        assertEquals(0, ReminderScheduler.weekOf(start, LocalDate.of(2026, 9, 1)))   // 前 6 天
        assertEquals(0, ReminderScheduler.weekOf(start, LocalDate.of(2026, 8, 31)))  // 前 7 天
    }

    // ---- 多事件前视 / 后台兜底阈值 ----

    @Test
    fun `前视排程与兜底阈值`() {
        assertEquals(3, ReminderScheduler.LOOKAHEAD)
        assertTrue(com.ccsut.kb.BackgroundSync.HEAL_DELAY_MS > 0)
    }
}
