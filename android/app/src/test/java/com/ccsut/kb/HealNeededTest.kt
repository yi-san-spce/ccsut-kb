package com.ccsut.kb

import com.ccsut.kb.BackgroundSync.HEAL_DELAY_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 后台兜底断链判定: nextReminderAt 在提醒正常触发后要到下次 reschedule 才前移,
 * 必须靠投递时间戳 (lastFiredAt) 区分「已投递但账面未更新」与「真被吞」——
 * 旧保活 watchdog 缺这一层, 提醒正常发出 5 分钟后会误判重发。
 */
class HealNeededTest {

    private val next = 1_000_000_000_000L

    @Test
    fun `未安排提醒不兜底`() {
        assertFalse(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS * 10, nextReminderAt = 0L, lastFiredAt = 0L))
    }

    @Test
    fun `过点未超阈值不兜底`() {
        // 主闹钟可能只是被推迟几分钟, 还在路上
        assertFalse(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS, nextReminderAt = next, lastFiredAt = 0L))
        assertFalse(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS - 1, nextReminderAt = next, lastFiredAt = 0L))
    }

    @Test
    fun `正常投递过不兜底_修复旧watchdog误判`() {
        // 提醒在到点时刻正常触发: lastFiredAt ≈ next, 兜底闹钟到点必须静默退出
        assertFalse(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS + 1, nextReminderAt = next, lastFiredAt = next))
        // 迟到几秒投递也算完好 (2 秒钟差容忍)
        assertFalse(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS + 1, nextReminderAt = next, lastFiredAt = next - 2_000L))
    }

    @Test
    fun `过点超阈值且未投递判断链`() {
        assertTrue(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS + 1, nextReminderAt = next, lastFiredAt = 0L))
        // 投递时间戳来自更早的一条提醒: 对本条而言就是被吞
        assertTrue(
            BackgroundSync.healNeeded(
                now = next + HEAL_DELAY_MS + 1, nextReminderAt = next, lastFiredAt = next - 3_000L,
            )
        )
    }

    @Test
    fun `排程已前移到未来事件不兜底`() {
        // 兜底闹钟可能在 reschedule 重排后晚触发: 账面 next 已指向更晚事件, now-next 为负
        val later = next + HEAL_DELAY_MS * 10
        assertFalse(BackgroundSync.healNeeded(now = next + HEAL_DELAY_MS, nextReminderAt = later, lastFiredAt = next))
    }
}
