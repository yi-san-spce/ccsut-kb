package com.ccsut.kb.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ccsut.kb.Prefs
import com.ccsut.kb.R
import com.ccsut.kb.util.DebugLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 课表提醒保活前台服务 (常驻通知): 国产 ROM 对「上滑划掉卡片」普遍按强制停止清理,
 * 已排的系统闹钟会被一并清空, 自启动无从生效。挂着前台服务时, 上滑通常只移除界面任务,
 * 进程与闹钟都存活 —— 这是代码侧对抗划掉清理的唯一有效手段 (锁定最近任务卡片是用户侧终极手段)。
 *
 * 生命周期: 提醒三开关任一开启即运行 (reschedule/cancel 里 sync), 全关即停;
 * START_STICKY: 进程被系统回收后自动拉起; 只从前台路径/BOOT_COMPLETED 启动
 * (后台启动 FGS 受限, 后台路径的 sync 失败静默跳过, 服务本就应在更早的前台时刻拉起)。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        enterForeground()
    }

    override fun onDestroy() {
        super.onDestroy()
        alive = false
        fgShown = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 已在前台展示过就不再重发通知: 用户清除/划掉后保持消失 (直到进程重建),
        // 否则每次打开 APP 都会重新冒出来
        if (!fgShown) enterForeground()
        return START_STICKY
    }

    private var fgShown = false

    private fun enterForeground() {
        val ctx = applicationContext
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                nm?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "后台提醒保活", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "维持课表提醒与闹钟的后台运行，可在系统设置里关闭"
                        setShowBadge(false)
                    },
                )
            }
            val next = Prefs.nextReminderAt(ctx)
            val whenText = if (next > 0)
                "下次提醒 " + DateTimeFormatter.ofPattern("M月d日 HH:mm")
                    .format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()))
            else "提醒待排程"
            val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle("课表提醒已就绪")
                .setContentText(whenText)
                // ongoing=false: 允许通知中心一键清除/左滑划掉 (13+); 清除不影响服务运行,
                // 且本服务存活期间不再重发 (见 onStartCommand)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            // 34+ 强制要求 FGS 类型; specialUse 是闹钟保活类应用的合规选择
            // (29-33 传 0 = 无类型, 平台不校验)
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
            fgShown = true
        }.onFailure {
            DebugLog.log("remind", "保活服务进前台失败: ${it.javaClass.simpleName}: ${it.message}")
            runCatching { stopSelf() }
        }
    }

    companion object {
        const val CHANNEL_ID = "keep_alive"
        const val NOTIF_ID = 3001

        /** 服务是否存活 (进程内): 决定 sync 用哪种启动方式 */
        private var alive = false

        /** 按三开关状态同步服务: 任一开启→运行, 全关→停。
         *  已存活时用普通 startService 送达 (不背 startForeground 义务, 已展示的通知不重发);
         *  冷启动才用 startForegroundService (onCreate 会立即进前台)。 */
        fun sync(ctx: Context) {
            val on = Prefs.reminderOn(ctx) || Prefs.afterClassOn(ctx) || Prefs.earlyOn(ctx)
            runCatching {
                if (on) {
                    if (alive) ctx.startService(Intent(ctx, KeepAliveService::class.java))
                    else ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
                } else ctx.stopService(Intent(ctx, KeepAliveService::class.java))
            }.onFailure {
                // 后台路径启动 FGS 受 31+ 限制: 静默跳过, 服务应在更早的前台时刻已拉起
                DebugLog.log("remind", "保活服务同步跳过: ${it.javaClass.simpleName}")
            }
        }
    }
}
