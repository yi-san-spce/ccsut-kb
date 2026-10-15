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
}
