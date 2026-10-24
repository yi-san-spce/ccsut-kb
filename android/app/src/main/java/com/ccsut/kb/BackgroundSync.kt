package com.ccsut.kb

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ccsut.kb.data.BuildVersion
import com.ccsut.kb.data.Channels
import com.ccsut.kb.data.Releases
import com.ccsut.kb.data.RemoteNotices
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.UpdateChannels
import com.ccsut.kb.data.Updater
import com.ccsut.kb.reminder.ReminderScheduler
import com.ccsut.kb.util.DebugLog
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.concurrent.thread

/**
 * 后台消息通道（v1.2.0-beta.8 起替代已移除的保活前台服务）: 零常驻、零新增依赖,
 * 进程被杀后由系统闹钟拉起。两件事:
 *
 *  - **提醒兜底**: 每条已排提醒在「到点 + [HEAL_DELAY_MS]」处多挂一枚精确兜底闹钟。触发时用
 *    「投递时间戳」([Prefs.lastFiredAt], ReminderReceiver 成功处理后写入) 区分链路完好与断链 ——
 *    正常投递过的静默退出, 被吞的补发 + 重排。旧 watchdog (保活服务 60s 轮询) 无法做这个区分,
 *    提醒正常发出 5 分钟后会误判断链重发一次; 本设计一并修掉。
 *
 *  - **消息检查**: [SYNC_INTERVAL_MS] 非精确循环闹钟, 拉远程通知 / 课表数据清单 / 软件新版本,
 *    与已通知状态比对, 有变化才发通知 (夜间静默不拉网)。替代「只有打开 App 才知道」的时效空白,
 *    公告类消息分钟级到达不追求, 45 分钟粒度足够。
 *
 * 强停边界 (与现状一致): 划卡被 ROM 按 force-stop 清理时闹钟全灭, 靠电池白名单/自启动引导 +
 * 开机(MY_PACKAGE_REPLACED/BOOT → reschedule 重排兜底) + 打开 App 自愈。
 */
object BackgroundSync {

    internal const val ACTION_HEAL = "com.ccsut.kb.action.BACKGROUND_HEAL"
    internal const val ACTION_PERIODIC = "com.ccsut.kb.action.BACKGROUND_SYNC"

    /** 兜底闹钟相对提醒时刻的延迟 = 断链判定阈值 (与旧 watchdog 的 STALE 等效) */
    internal const val HEAL_DELAY_MS = 5 * 60_000L

    /** 消息检查循环周期 (setInexactRepeating; 对公告/数据更新 45 分钟粒度足够, Doze 下自动合并省电) */
    internal const val SYNC_INTERVAL_MS = 45 * 60_000L

    /** 消息通知渠道 (与更新提醒 update_notify 分开, 便于用户分别管理)。
     *  v2: 渠道重要性建后不可改 —— 首版 DEFAULT 只进通知栏不弹横幅, 用户要求后台弹窗, 换 ID 迁移 */
    const val CHANNEL_MESSAGES = "bg_messages_v2"

    /** beta.8 首发的 DEFAULT 渠道: 升 v2 后清理防僵尸 */
    const val LEGACY_CHANNEL_MESSAGES = "bg_messages"

    /** 已移除的保活前台服务的通知渠道 id: 升级后系统不会自动删, 启动时清理防僵尸渠道 */
    const val LEGACY_KEEP_ALIVE_CHANNEL = "keep_alive"

    private const val NOTIF_DATA = 4001

    /** requestCode: 0..LOOKAHEAD-1 段被提醒槽位占用, 兜底错开到 2xxx 段 */
    private const val PI_HEAL_BASE = 2000
    private const val PI_PERIODIC = 2010

    // ---- 武装 ----

    /** 进程启动即武装周期检查 (幂等, 同 requestCode 覆盖): Application.onCreate 调用,
     *  冷启动/开机广播/闹钟拉起进程都会走到 */
    fun armPeriodic(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SYNC_INTERVAL_MS,
            SYNC_INTERVAL_MS,
            periodicPi(ctx),
        )
    }

    /**
     * 按已排提醒时刻武装兜底闹钟 (reschedule 尾部调用); times 为空 = 全部撤销 (提醒全关/无课)。
     * 槽位与提醒槽一一对应, reschedule 幂等重排时同 requestCode 覆盖。
     */
    internal fun armHealAlarms(ctx: Context, atTimes: List<Long>) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val exact = am.canScheduleExactAlarms()
        for (slot in 0 until ReminderScheduler.LOOKAHEAD) {
            val pi = healPi(ctx, slot)
            val at = atTimes.getOrNull(slot) ?: run { am.cancel(pi); continue }
            val triggerAt = at + HEAL_DELAY_MS
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun healPi(ctx: Context, slot: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, PI_HEAL_BASE + slot,
        Intent(ctx, BackgroundSyncReceiver::class.java).setAction(ACTION_HEAL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun periodicPi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, PI_PERIODIC,
        Intent(ctx, BackgroundSyncReceiver::class.java).setAction(ACTION_PERIODIC),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ---- 提醒兜底 ----

    /**
     * 断链判定 (纯函数, 单测覆盖): 提醒已过点超过 [HEAL_DELAY_MS] 且投递时间戳早于该提醒时刻
     * (2 秒钟差容忍投递/排程的时钟抖动) → 判定被吞。
     * nextReminderAt 在提醒正常触发后要到下次 reschedule 才前移, 用 lastFiredAt 对齐才能区分
     * 「已投递但账面未更新」与「真断链」。
     */
    internal fun healNeeded(now: Long, nextReminderAt: Long, lastFiredAt: Long): Boolean {
        if (nextReminderAt <= 0) return false
        if (now - nextReminderAt <= HEAL_DELAY_MS) return false
        return lastFiredAt < nextReminderAt - 2_000L
    }

    /** 兜底检查 (后台线程): 断链则补发 + 重排 (重排尾部会重排兜底闹钟, 链自动续上) */
    internal fun healCheck(ctx: Context) {
        val on = Prefs.reminderOn(ctx) || Prefs.afterClassOn(ctx) || Prefs.earlyOn(ctx)
        if (!on) return
        if (!healNeeded(System.currentTimeMillis(), Prefs.nextReminderAt(ctx), Prefs.lastFiredAt(ctx))) return
        DebugLog.log("remind", "后台兜底: 提醒已过点未投递, 补发 + 重排")
        kotlinx.coroutines.runBlocking {
            runCatching { ReminderScheduler.fire(ctx) }
                .onFailure { DebugLog.log("remind", "兜底补发失败: ${it.message}") }
            runCatching { ReminderScheduler.reschedule(ctx) }
                .onFailure { DebugLog.log("remind", "兜底重排失败: ${it.message}") }
        }
    }

    // ---- 消息检查 ----

    /** 周期消息检查 (后台线程): 兜底判定在接收器里先行; 这里只管消息。夜间静默不拉网。 */
    internal fun messageCheck(ctx: Context) {
        val hour = LocalTime.now().hour
        if (hour >= 23 || hour < 7) return
        checkNotices(ctx)
        checkDataVersion(ctx)
        checkApkVersion(ctx)
    }

    /** 远程通知: 命中排期/定向/去重的前几条发系统通知 (每条 id 只在后台提醒一次, 弹窗已读记录独立) */
    private fun checkNotices(ctx: Context) {
        val notices = runCatching { RemoteNotices.fetch(ctx) }.getOrElse { return }
        if (notices.isEmpty()) return
        val blocked = RemoteNotices.seenIds(ctx) + Prefs.bgNotifiedIds(ctx)
        val hits = RemoteNotices.selectAll(notices, LocalDateTime.now(), BuildVersion.DISPLAY, blocked)
        for (hit in hits.take(3)) {
            postMessage(
                ctx, id = ("bgn${hit.id}").hashCode(),
                title = hit.title, text = hit.message,
            )
            Prefs.markBgNotified(ctx, hit.id)
        }
    }

    /** 课表数据更新: 清单 version 高于本地且未提醒过才发 (不后台套用, 打开 App 时照常自动热更) */
    private fun checkDataVersion(ctx: Context) {
        if (Repo.dataset == null) runCatching { Repo.load(ctx) }   // 闹钟拉起的冷进程: 先读盘上数据集
        val current = Repo.dataset?.version ?: return              // 无数据用户不打扰
        val m = runCatching { Updater.fetchManifest(ctx) }.getOrNull() ?: return
        if (m.version > current && m.version != Prefs.bgDataNotifiedVersion(ctx)) {
            postMessage(
                ctx, id = NOTIF_DATA,
                title = "课表数据有更新",
                text = "检测到新数据 v${m.version}，打开长工课表通自动套用",
            )
            Prefs.setBgDataNotifiedVersion(ctx, m.version)
        }
    }

    /** 软件新版本: 复用更新通知 (setOnlyAlertOnce, 循环检查不重复响; 划掉跳过逻辑同前台) */
    private fun checkApkVersion(ctx: Context) {
        if (!Prefs.autoUpdateOn(ctx)) return
        val latest = runCatching {
            Releases.fetchLatest(ctx, UpdateChannels.source(ctx), Channels.current(ctx))
        }.getOrNull() ?: return
        if (Releases.relation(latest) > 0 && latest.versionName != Prefs.updateDismissedVersion(ctx)) {
            UpdateNotifier.notifyAvailable(ctx, latest)
        }
    }

    private fun postMessage(ctx: Context, id: Int, title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.log("update", "后台消息「$title」缺 POST_NOTIFICATIONS 权限, 未发")
            return
        }
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val tapPi = PendingIntent.getActivity(
            ctx, 8,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            id,
            NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(tapPi)
                .build(),
        )
        DebugLog.log("update", "后台消息已发: $title")
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_MESSAGES) != null) return
        nm.deleteNotificationChannel(LEGACY_CHANNEL_MESSAGES)   // 一次性清掉旧 DEFAULT 渠道
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "消息提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "远程通知、课表数据与新版本的后台横幅提醒"
            },
        )
    }
}

/** 兜底/周期闹钟的接收器: 显式 PendingIntent 指名 (Manifest 不挂 filter), 进程被杀后由系统拉起 */
class BackgroundSyncReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = goAsync()
        thread(name = "kb-bg-sync") {
            try {
                runCatching { BackgroundSync.healCheck(ctx) }
                if (intent.action == BackgroundSync.ACTION_PERIODIC) {
                    runCatching { BackgroundSync.messageCheck(ctx) }
                        .onFailure { DebugLog.log("update", "后台消息检查失败: ${it.message}") }
                }
            } finally {
                result.finish()
            }
        }
    }
}
