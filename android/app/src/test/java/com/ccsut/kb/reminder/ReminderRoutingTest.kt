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
        assertFalse(ReminderScheduler.ringDeliveryExpired(0L, 15, System.currentTimeMillis()))
    }

    @Test
    fun `准点与提前投递不过期`() {
        val at = 1_780_000_000_000L
        assertFalse(ReminderScheduler.ringExpired(at, 15, at))                    // 排定时刻准点
        assertFalse(ReminderScheduler.ringExpired(at - 60_000, 15, at))          // 早到 1 分钟
        assertFalse(ReminderScheduler.ringExpired(at, 0, at))                     // 0 提前量=上课时刻准点
        assertFalse(ReminderScheduler.ringDeliveryExpired(at, 15, at))           // 投递侧: 课前 15 分钟照常
    }

    @Test
    fun `投递侧_已到上课时刻就整条放弃_上课后零响铃`() {
        val at = 1_780_000_000_000L
        val classStart = at + 15 * 60_000
        assertFalse(ReminderScheduler.ringDeliveryExpired(at, 15, classStart - 1))   // 差 1 毫秒上课, 照常拉响
        assertTrue(ReminderScheduler.ringDeliveryExpired(at, 15, classStart))        // 已上课 0 分钟 = 用户报的 bug
        assertTrue(ReminderScheduler.ringDeliveryExpired(at, 15, classStart + 30_000))   // 上课 30 秒后投递
        assertTrue(ReminderScheduler.ringDeliveryExpired(at, 15, classStart + 15 * 60_000))  // 上课 15 分钟后投递
    }

    @Test
    fun `响铃页侧_拉起容忍线内照响_过线静默`() {
        val at = 1_780_000_000_000L
        val classStart = at + 15 * 60_000
        // 容忍线只吸收全屏意图的秒级拉起延迟: 投递准点 (课前三五分钟) 时页面打开晚了几秒仍要响
        assertFalse(ReminderScheduler.ringExpired(at, 15, classStart - 1))
        assertFalse(ReminderScheduler.ringExpired(at, 15, classStart + ReminderScheduler.RING_PAGE_GRACE_MS))
        assertTrue(ReminderScheduler.ringExpired(at, 15, classStart + ReminderScheduler.RING_PAGE_GRACE_MS + 1))
        assertTrue(ReminderScheduler.ringExpired(at, 15, classStart + 15 * 60_000))   // 上课 15 分钟后打开 = 静默退出
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
