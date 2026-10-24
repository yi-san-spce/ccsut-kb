package com.ccsut.kb

import android.app.Application
import com.ccsut.kb.util.KbClock

/** 全局初始化: 时间旅行偏移在进程冷启动 (闹钟触发/重启后) 也要恢复, 不能只靠 Activity.onCreate */
class KbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KbClock.init(this)
    }

    companion object {
        /** 前台 Activity 计数 (onStart++/onStop--): >0 即应用可见。
         *  单 Activity 应用, 保活服务用它区分前台(收通知)/后台(保活工作场景)。 */
        @Volatile
        var activityDepth: Int = 0
    }
}
