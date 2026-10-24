package com.ccsut.kb.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

/** 保活决策: 三开关 × 前后台 → 服务动作 (前台只收通知、后台保活、全关停服务) */
class KeepAlivePolicyTest {

    @Test
    fun `提醒全关一律停服务`() {
        assertEquals(KeepAliveAction.STOP, decideKeepAlive(on = false, foreground = true))
        assertEquals(KeepAliveAction.STOP, decideKeepAlive(on = false, foreground = false))
    }

    @Test
    fun `前台只收通知不启服务`() {
        // 用户反馈: 打开 APP 不该冒常驻通知; SUPPRESS 对未启动的服务不做任何事
        assertEquals(KeepAliveAction.SUPPRESS, decideKeepAlive(on = true, foreground = true))
    }

    @Test
    fun `后台确保运行并亮通知`() {
        assertEquals(KeepAliveAction.SHOW, decideKeepAlive(on = true, foreground = false))
    }
}
