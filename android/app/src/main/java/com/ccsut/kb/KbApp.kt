package com.ccsut.kb

import android.app.Application
import com.ccsut.kb.util.KbClock

/** 全局初始化: 时间旅行偏移在进程冷启动 (闹钟触发/重启后) 也要恢复, 不能只靠 Activity.onCreate */
class KbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KbClock.init(this)
        // 后台消息通道的周期检查: 进程每次冷启动 (用户打开/开机广播/闹钟拉起) 都幂等重排一次;
        // 闹钟不跨重启, BOOT/MY_PACKAGE_REPLACED → Application.onCreate 正好覆盖重装后的重排
        runCatching { BackgroundSync.armPeriodic(this) }
        // 保活前台服务已移除: 清掉老设备上的「后台提醒保活」渠道, 免得系统设置里留僵尸项
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                ?.deleteNotificationChannel(BackgroundSync.LEGACY_KEEP_ALIVE_CHANNEL)
        }
    }
}
