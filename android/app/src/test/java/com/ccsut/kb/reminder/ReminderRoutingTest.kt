package com.ccsut.kb.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 调度路由纯函数: 事件进哪条链 / 迟到投递容差 */
class ReminderRoutingTest {

    @Test
    fun `闹钟模式开启时课前事件走响铃链`() {
        assertTrue(ReminderScheduler.goesToAlarmChain(0, alarmMode = true))
    }

    @Test
    fun `闹钟模式关闭时课前事件走通知链`() {
        assertFalse(ReminderScheduler.goesToAlarmChain(0, alarmMode = false))
    }

    @Test
    fun `放学与早八永远走通知链`() {
        assertFalse(ReminderScheduler.goesToAlarmChain(1, alarmMode = true))
        assertFalse(ReminderScheduler.goesToAlarmChain(2, alarmMode = true))
    }

    @Test
    fun `迟到投递容差_精确30分钟_降级60分钟`() {
        assertEquals(30L, ReminderScheduler.dueWindowMin(exact = true))
        assertEquals(60L, ReminderScheduler.dueWindowMin(exact = false))
    }

    // ---- 响铃迟到投递防护: setAlarmClock 定点准, 但广播投递可能被系统推迟 ----

    @Test
    fun `无排定时刻的测试响铃永不过期`() {
        // scheduleTestRing 等 dev 链路不带 EXTRA_AT, 按新鲜处理保持旧行为
        assertFalse(ReminderScheduler.ringExpired(0L, 15, System.currentTimeMillis()))
    }

    @Test
    fun `准点与提前投递不过期`() {
        val at = 1_780_000_000_000L
        assertFalse(ReminderScheduler.ringExpired(at, 15, at))                    // 排定时刻准点
        assertFalse(ReminderScheduler.ringExpired(at - 60_000, 15, at))          // 早到 1 分钟
        assertFalse(ReminderScheduler.ringExpired(at, 0, at))                     // 0 提前量=上课时刻准点
    }

    @Test
    fun `上课时刻已过容忍线不再响`() {
        val at = 1_780_000_000_000L
        val classStart = at + 15 * 60_000
        assertFalse(ReminderScheduler.ringExpired(at, 15, classStart + ReminderScheduler.RING_LATE_TOLERANCE_MS))
        assertTrue(ReminderScheduler.ringExpired(at, 15, classStart + ReminderScheduler.RING_LATE_TOLERANCE_MS + 1))
        assertTrue(ReminderScheduler.ringExpired(at, 15, classStart + 15 * 60_000))   // 上课 15 分钟后 = 用户报的 bug
    }

    @Test
    fun `上课时刻等于排定时刻加提前量`() {
        val at = 1_780_000_000_000L
        assertEquals(at + 15 * 60_000, ReminderScheduler.classStartMillis(at, 15))
        assertEquals(at, ReminderScheduler.classStartMillis(at, 0))
        assertEquals(0L, ReminderScheduler.classStartMillis(0L, 15))
    }

    @Test
    fun `剩余分钟随投递时刻实时修正`() {
        val at = 1_780_000_000_000L
        assertEquals(15, ReminderScheduler.remainingMinutes(at, 15, at))
        assertEquals(5, ReminderScheduler.remainingMinutes(at, 15, at + 10 * 60_000))    // 迟到 10 分钟投递
        assertEquals(1, ReminderScheduler.remainingMinutes(at, 15, at + 14 * 60_000 + 1)) // 差 59 秒, 向上取整
        assertEquals(0, ReminderScheduler.remainingMinutes(at, 15, at + 15 * 60_000))
        assertEquals(15, ReminderScheduler.remainingMinutes(0L, 15, 0L))                 // 无排定时刻退回提前量
    }
}
