package com.ccsut.kb

import android.app.Application
import com.ccsut.kb.util.KbClock

/** 全局初始化: 时间旅行偏移在进程冷启动 (闹钟触发/重启后) 也要恢复, 不能只靠 Activity.onCreate */
class KbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KbClock.init(this)
    }
}
