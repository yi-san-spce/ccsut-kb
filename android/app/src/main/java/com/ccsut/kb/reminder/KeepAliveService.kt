package com.ccsut.kb.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ccsut.kb.KbApp
import com.ccsut.kb.Prefs
import com.ccsut.kb.R
import com.ccsut.kb.util.DebugLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

/** 保活决策: 三开关状态 × 应用前后台 → 服务该做什么 (纯函数, 单测覆盖) */
internal enum class KeepAliveAction {
    /** 停掉服务 (提醒全关, 保活无意义) */
    STOP,
    /** 服务继续跑但收掉常驻通知 (App 在前台, 进程本就在前台, 常驻通知是噪音) */
    SUPPRESS,
    /** 确保服务运行且通知亮出 (后台 = 保活的工作场景) */
    SHOW,
}

internal fun decideKeepAlive(on: Boolean, foreground: Boolean): KeepAliveAction = when {
    !on -> KeepAliveAction.STOP
    foreground -> KeepAliveAction.SUPPRESS
    else -> KeepAliveAction.SHOW
}

/**
 * 课表提醒保活前台服务 (常驻通知): 国产 ROM 对「上滑划掉卡片」普遍按强制停止清理,
 * 已排的精确闹钟会被一并清空, 自启动无从生效。挂着前台服务时, 上滑通常只移除界面任务,
 * 进程与闹钟都存活 —— 这是代码侧对抗划掉清理的唯一有效手段 (锁定最近任务卡片是用户侧终极手段)。
 *
 * 生命周期: 提醒三开关任一开启即运行 (reschedule/cancel 里 sync), 全关即停;
 * START_STICKY: 进程被系统回收后自动拉起 (null intent → 自愈重排); 只从前台路径/BOOT_COMPLETED 启动
 * (后台启动 FGS 受限, 后台路径的 sync 失败静默跳过, 服务本就应在更早的前台时刻拉起)。
 *
 * **通知只在后台存在** (用户反馈: 打开 APP 就有一条划不掉的常驻通知):
 * App 前台时 [sync] 发 [ACTION_SUPPRESS] 收掉通知但服务继续跑 (watchdog/划卡对抗不回退),
 * 退后台 (MainActivity.onStop → sync) 再亮出; 用户左滑划掉经 deleteIntent 记入 [userDismissed],
 * 本进程内前台/后台怎么切换都不复活, 进程重启才重来。
 *
 * watchdog: 存活期间周期校验 Prefs.nextReminderAt —— 已过点超过阈值仍未被消费,
 * 说明排程链断了 (广播被吞/闹钟被省电策略清掉), 立即补发 + 重排自愈。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        userDismissed = false
        enterForeground()
        handler.postDelayed(watchdogTick, ReminderScheduler.WATCHDOG_CHECK_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        alive = false
        fgShown = false
        handler.removeCallbacks(watchdogTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SUPPRESS) {
            // 收掉通知但服务继续跑: App 在前台时进程本就在前台, 常驻通知纯属噪音;
            // 服务不停 → watchdog 自愈与「划卡不杀进程」的对抗能力不回退
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            fgShown = false
            return START_STICKY
        }
        // 已在展示就不重发; 用户左滑划掉过 (deleteIntent → userDismissed) 的本进程内也不复活
        if (!fgShown && !userDismissed) enterForeground()
        if (intent == null) {
            // START_STICKY 重建 (intent=null): 排程链随进程死亡丢过一轮, 自愈重排
            thread(name = "kb-keepalive-heal") {
                runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.reschedule(applicationContext) } }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 部分 ROM 划卡不走 force-stop: 任务没了但进程还在, 重排一次把链续上
        thread(name = "kb-keepalive-task") {
            runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.reschedule(applicationContext) } }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fgShown = false

    /** watchdog 周期检: 断链自愈 (补发容差内已到期事件 + 重排) */
    private val watchdogTick: Runnable = Runnable {
        thread(name = "kb-watchdog") { runCatching { watchdogCheck(applicationContext) } }
        handler.postDelayed(watchdogTick, ReminderScheduler.WATCHDOG_CHECK_MS)
    }

    private fun watchdogCheck(ctx: Context) {
        val on = Prefs.reminderOn(ctx) || Prefs.afterClassOn(ctx) || Prefs.earlyOn(ctx)
        if (!on) return
        val next = Prefs.nextReminderAt(ctx)
        if (next <= 0) return
        val staleMs = System.currentTimeMillis() - next
        if (staleMs > ReminderScheduler.WATCHDOG_STALE_MS) {
            DebugLog.log("remind", "watchdog: 下次提醒已过点 ${staleMs / 1000}s 未消费, 断链自愈 (补发+重排)")
            kotlinx.coroutines.runBlocking {
                ReminderScheduler.fire(ctx)
                ReminderScheduler.reschedule(ctx)
            }
        }
    }

    private fun enterForeground() {
        val ctx = applicationContext
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                nm?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "后台提醒保活", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "维持课表提醒的后台运行，可在系统设置里关闭"
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
                .setContentTitle("后台保活中")
                .setContentText(whenText)
                // ongoing=false: 允许通知中心一键清除/左滑划掉 (13+); 清除不影响服务运行
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                // 用户左滑划掉 → 记入 userDismissed, 本进程内不再复活 (前台/后台切换也不重发)
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        ctx, 0,
                        Intent(ctx, KeepAliveDismissReceiver::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
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

        /** 前台抑制意图 action: 收通知不停服务 */
        const val ACTION_SUPPRESS = "com.ccsut.kb.action.KEEPALIVE_SUPPRESS"

        /** 服务是否存活 (进程内): 决定 sync 用哪种启动方式 */
        private var alive = false

        /** 用户已手动划掉常驻通知 (deleteIntent): 本进程内前台/后台切换不再复活, 进程重启才重来 */
        private var userDismissed = false

        /** 用户划掉常驻通知 (KeepAliveDismissReceiver 回调) */
        fun onUserDismissed() {
            userDismissed = true
            DebugLog.log("remind", "保活通知被用户划掉, 本进程内不再恢复")
        }

        /**
         * 按三开关 × 前后台同步服务 (决策见 [decideKeepAlive]):
         * - 全关 → 停服务;
         * - App 前台 → 服务存活则发 SUPPRESS 收通知; 未存活则不启动 (前台不需要保活, 打开 APP 不再冒常驻通知);
         * - 后台 → 确保运行且通知亮出 (已存活用普通 startService 送达, 冷启动用 startForegroundService)。
         */
        fun sync(ctx: Context) {
            val on = Prefs.reminderOn(ctx) || Prefs.afterClassOn(ctx) || Prefs.earlyOn(ctx)
            val action = decideKeepAlive(on, KbApp.activityDepth > 0)
            runCatching {
                when (action) {
                    KeepAliveAction.STOP -> ctx.stopService(Intent(ctx, KeepAliveService::class.java))
                    KeepAliveAction.SUPPRESS ->
                        if (alive) ctx.startService(Intent(ctx, KeepAliveService::class.java).setAction(ACTION_SUPPRESS))
                    KeepAliveAction.SHOW ->
                        if (alive) ctx.startService(Intent(ctx, KeepAliveService::class.java))
                        else ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
                }
            }.onFailure {
                // 后台路径启动 FGS 受 31+ 限制: 静默跳过, 服务应在更早的前台时刻已拉起
                DebugLog.log("remind", "保活服务同步跳过: ${it.javaClass.simpleName}")
            }
        }
    }
}
