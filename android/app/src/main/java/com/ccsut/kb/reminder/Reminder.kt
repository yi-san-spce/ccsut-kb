package com.ccsut.kb.reminder

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
import com.ccsut.kb.MainActivity
import com.ccsut.kb.Prefs
import com.ccsut.kb.R
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.DebugLog
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.TimeParse
import com.ccsut.kb.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

/**
 * 贴心提醒（通知链）: 课前提醒 / 放学小结 / 早八前夜 三类通知。
 * 闹钟模式已移除（v1.2.0-beta.6 起），本链是唯一提醒路径 —— 可靠性优先：
 *  - 多事件前视: 一次排接下来至多 [LOOKAHEAD] 条（独立 requestCode），单条被 ROM 吞掉仍有接力
 *  - 系统自愈入口: 开机/包更新/时间变化/精确闹钟权限变化 → reschedule + 补发容差内已到期事件
 *  - 后台兜底 (BackgroundSync): 每条已排提醒在到点 +5min 处多挂一枚精确兜底闹钟,
 *    用投递时间戳区分「已投递」与「被吞」, 被吞的补发 + 重排（替代旧保活服务的 60s watchdog）
 *  - 全部丢弃/降级路径留痕（DebugLog + Prefs.notifDrops 计数），开发者面板可诊断
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.ccsut.kb.ACTION_CLASS_REMINDER"
    const val ACTION_DEV_TEST = "com.ccsut.kb.ACTION_DEV_TEST"

    // 通道: 重要度/声音一经创建不可改（Android 官方约定），分级行为靠新 ID。
    // CHANNEL_CLASS 沿用历史 ID —— 老用户已固化的设置不丢。
    const val CHANNEL_CLASS = "class_reminder"   // 课前提醒: HIGH 响铃横幅
    const val CHANNEL_AFTER = "after_class_v2"   // 放学小结: LOW 无声（下课后用户多半在看手机）
    const val CHANNEL_EARLY = "early_class_v2"   // 早八前夜: HIGH 响铃横幅
    const val NOTIF_TEST = 1001

    /** 多事件前视条数: 一次排接下来 N 条，单条广播被吞仍接力 */
    internal const val LOOKAHEAD = 3

    private const val TYPE_CLASS = 0   // 课前提醒
    private const val TYPE_AFTER = 1   // 放学小结
    private const val TYPE_EARLY = 2   // 早八前夜

    /** 早八前夜提醒时刻 */
    private val EARLY_AT: LocalTime = LocalTime.of(21, 30)

    /** 投递容差(分钟): 精确闹钟也可能被推迟几分钟, 降级闹钟 (Doze) 可达 15 分钟以上 */
    internal fun dueWindowMin(exact: Boolean): Long = if (exact) 30L else 60L

    /** 周次(1 基): day 早于学期开始返回 0; floorDiv 防「开学前 1-6 天被 Int 截断算成第 1 周」误发提醒 */
    internal fun weekOf(start: LocalDate, day: LocalDate): Int =
        if (day.isBefore(start)) 0 else (ChronoUnit.DAYS.between(start, day).floorDiv(7L).toInt() + 1)

    /** 通知链排程槽: 每条独立 requestCode, 一次可同时挂 LOOKAHEAD 条接力闹钟 */
    private fun firePi(ctx: Context, slot: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1000 + slot,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun channelOf(type: Int): String = when (type) {
        TYPE_AFTER -> CHANNEL_AFTER
        TYPE_EARLY -> CHANNEL_EARLY
        else -> CHANNEL_CLASS
    }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_CLASS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CLASS, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "上课前的到点提醒（响铃横幅）"
                    enableVibration(true)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_AFTER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_AFTER, "放学小结", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "一天课程结束后的安静小结（默认无声，可在系统设置里调高）"
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_EARLY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_EARLY, "早八前夜", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "明早真有早八时，前一晚 21:30 提醒早睡（响铃横幅）"
                    enableVibration(true)
                },
            )
        }
    }

    private data class Ev(
        val type: Int,
        val at: LocalDateTime,
        val block: Block? = null,          // CLASS/EARLY 关联课块
        val classStart: LocalDateTime? = null,
    )

    /** 某一天会发生的提醒事件; classOn=false 时不算课前事件 (放学/早八照常) */
    private fun dayEvents(
        snap: ClassCache.Snapshot,
        start: LocalDate,
        day: LocalDate,
        lead: Int,
        classOn: Boolean,
        afterOn: Boolean,
        earlyOn: Boolean,
    ): List<Ev> {
        val week = weekOf(start, day)
        if (week < 1 || week > snap.weeks) return emptyList()
        val pm = snap.periods.associateBy { it.jc }
        // 脏时间(空串/带秒/非数字)防御为 -1: 下面按 <0 过滤, 别让提醒调度崩在后台线程
        fun mins(t: String): Int = TimeParse.minutesOf(t) ?: -1
        fun at(d: LocalDate, min: Int) = d.atTime(min / 60, min % 60)

        val blocks = Merger.blocksOf(snap.courses, week)
            .filter { it.course.day == day.dayOfWeek.value }
            .filter { pm[it.startJc] != null && pm[it.startJc + it.span - 1] != null }
            .filter { mins(pm[it.startJc]!!.start) >= 0 && mins(pm[it.startJc + it.span - 1]!!.end) >= 0 }
        val out = mutableListOf<Ev>()
        if (blocks.isNotEmpty()) {
            if (classOn) {
                for (b in blocks) {
                    val cs = at(day, mins(pm[b.startJc]!!.start))
                    out += Ev(TYPE_CLASS, cs.minusMinutes(lead.toLong()), b, cs)
                }
            }
            if (afterOn) {
                val lastEnd = blocks.maxOf { mins(pm[it.startJc + it.span - 1]!!.end) }
                out += Ev(TYPE_AFTER, at(day, lastEnd))
            }
        }
        if (earlyOn) {
            val tmr = day.plusDays(1)
            val tw = weekOf(start, tmr)
            if (tw in 1..snap.weeks) {
                val first = Merger.blocksOf(snap.courses, tw)
                    .filter { it.course.day == tmr.dayOfWeek.value }
                    .minByOrNull { it.startJc }
                if (first != null && first.startJc == 1 && pm[1] != null && mins(pm[1]!!.start) >= 0) {
                    out += Ev(TYPE_EARLY, day.atTime(EARLY_AT), first, at(tmr, mins(pm[1]!!.start)))
                }
            }
        }
        return out
    }

    /**
     * 重算并重排提醒。多事件前视: 一次排接下来至多 [LOOKAHEAD] 条（各自独立 PendingIntent）,
     * 最早一条触发后由 Receiver 接力补排 —— 单条广播被 ROM 吞掉, 后续仍会把链续上。
     * reschedule 同时是所有自愈入口的汇聚点（开机/包更新/时间变化/回前台/开关变更/后台兜底）。
     */
    suspend fun reschedule(ctx: Context) = withContext(Dispatchers.IO) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withContext
        val classOn = Prefs.reminderOn(ctx)
        val afterOn = Prefs.afterClassOn(ctx)
        val earlyOn = Prefs.earlyOn(ctx)
        if (!classOn && !afterOn && !earlyOn) {
            cancel(ctx); return@withContext
        }
        val snap = ClassCache.load(ctx) ?: return@withContext
        if (snap.courses.isEmpty()) return@withContext
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull()
            ?: return@withContext
        val lead = Prefs.reminderLead(ctx)
        // 真实时钟: 精确闹钟按真实时间触发, 时间旅行 (Dev.offsetDays) 只属于界面语义
        val now = LocalDateTime.now()
        // 容差闭区间: 30 秒内「刚到点」的事件仍算未来并立即补发 —— 原实现严格 isAfter
        // 会把恰好在到点时刻发生的重排永久跳过该事件
        val tolerance = now.minusSeconds(30)

        var day = now.toLocalDate()
        val picked = mutableListOf<Ev>()
        var scanned = 0
        val maxScan = snap.weeks * 7 + 14  // 护栏: 防设备日期严重回拨导致无限扫描
        while (picked.size < LOOKAHEAD && scanned++ < maxScan) {
            picked += dayEvents(snap, start, day, lead, classOn, afterOn, earlyOn)
                .filter { !it.at.isBefore(tolerance) }
            day = day.plusDays(1)
        }
        picked.sortBy { it.at }
        val picks = picked.take(LOOKAHEAD)

        // 容差内已到期: 直接补发（dueWindow 内留痕判定由 fire 自理）
        val dueNow = picks.takeWhile { !it.at.isAfter(now) }
        if (dueNow.isNotEmpty()) {
            DebugLog.log("remind", "重排发现 ${dueNow.size} 条容差内已到期事件, 补发")
            fire(ctx)
        }

        val slots = (0 until LOOKAHEAD).map { firePi(ctx, it) }
        slots.forEach { am.cancel(it) }   // 幂等: 全清再按位重排
        val exact = am.canScheduleExactAlarms()
        val pickMillis = picks.map { it.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        picks.forEachIndexed { i, ev ->
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pickMillis[i], slots[i])
            } else {
                // 未授予「闹钟和提醒」时的降级: 非精确闹钟, 可能晚几分钟
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pickMillis[i], slots[i])
            }
        }
        // 兜底闹钟跟着每条提醒重排 (到点 +5min 判断链, 见 BackgroundSync)
        com.ccsut.kb.BackgroundSync.armHealAlarms(ctx, pickMillis)
        val next = pickMillis.firstOrNull() ?: 0L
        Prefs.setNextReminderAt(ctx, next)
        picks.forEachIndexed { i, ev -> DebugLog.log("remind", "通知排程 #$i ${ev.at} (type=${ev.type})") }
        if (picks.isNotEmpty() && !exact) {
            DebugLog.log("remind", "精确闹钟未授予, 已降级非精确排程（提醒可能推迟数分钟）")
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        (0 until LOOKAHEAD).forEach { am?.cancel(firePi(ctx, it)) }
        com.ccsut.kb.BackgroundSync.armHealAlarms(ctx, emptyList())
        Prefs.setNextReminderAt(ctx, 0L)
    }

    /**
     * 通知链触发: 把投递窗口内到期的提醒发出。
     * 全部丢弃路径留痕（权限缺失/已上课/窗口外/无数据），开发者面板可诊断。
     */
    internal suspend fun fire(ctx: Context) = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.log("remind", "有到期提醒但缺 POST_NOTIFICATIONS 权限, 通知被丢弃")
            Prefs.incNotifDrops(ctx)
            return@withContext
        }
        val snap = ClassCache.load(ctx)
        if (snap == null) {
            DebugLog.log("remind", "提醒触发但无课表缓存, 跳过")
            return@withContext
        }
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull()
        if (start == null) {
            DebugLog.log("remind", "提醒触发但学期开始日期无效, 跳过")
            return@withContext
        }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val lead = Prefs.reminderLead(ctx)
        val classOn = Prefs.reminderOn(ctx)

        // 投递窗口: 精确闹钟也可能被系统推迟几分钟, 降级模式 (Doze) 可达 15 分钟以上 —— 从宽不丢件
        val am2 = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val windowMin = dueWindowMin(am2?.canScheduleExactAlarms() == true)
        val all = dayEvents(snap, start, today, lead, classOn, Prefs.afterClassOn(ctx), Prefs.earlyOn(ctx))
        val inWindow = all.filter { !it.at.isAfter(now) && it.at.isAfter(now.minusMinutes(windowMin)) }
        // 已上课的课前提醒不发: 上课后弹「快上课啦」纯属惊扰（留痕不静默）
        val (due, late) = inWindow.partition { it.type != TYPE_CLASS || it.classStart!!.isAfter(now) }
        if (late.isNotEmpty()) {
            DebugLog.log("remind", "${late.size} 条课前提醒已上课, 丢弃不扰 (${late.joinToString { it.at.toString() }})")
            Prefs.incNotifDrops(ctx, late.size)
        }
        val overdue = all.count { it.at.isBefore(now.minusMinutes(windowMin)) }
        if (overdue > 0) DebugLog.log("remind", "$overdue 条提醒超出投递窗口, 丢弃 (投递被推迟过久)")
        if (due.isEmpty()) {
            DebugLog.log("remind", "触发检查: 无窗口内到期提醒 (窗口=${windowMin}min)")
            return@withContext
        }
        DebugLog.log("remind", "触发 ${due.size} 条提醒: ${due.joinToString { t -> when (t.type) { 0 -> "课前"; 1 -> "放学"; else -> "早八" } }}")

        ensureChannels(ctx)
        val contentPi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return@withContext
        val tails = listOf("记得带书呀", "路上慢点哦", "别走错教室啦")
        val afterPool = listOf(
            "今日课程清零，去玩吧 ヾ(≧▽≦*)o",
            "下课咯～今天也很努力呢，哈哈",
            "辛苦啦 (๑•̀ㅂ•́)و✧ 去放松一下吧",
        )
        val idx = today.dayOfYear

        // 按事件时间排序后取前 3: 延迟投递多事件齐到时优先发最早的, 不让顺序随机截断
        due.sortedBy { it.at }.take(3).forEach { ev ->
            val (title, text) = when (ev.type) {
                TYPE_CLASS -> {
                    val b = ev.block!!
                    val room = b.rooms.ifBlank { b.course.room }.trim()
                    // 投递可能比排定晚几分钟: 按真实剩余分钟生成文案, 不照抄排定时的提前量
                    val remain = ChronoUnit.MINUTES.between(now, ev.classStart).toInt().coerceAtLeast(0)
                    (if (remain > 0) "还有 $remain 分钟上课" else "快上课啦") to buildString {
                        append("第${b.startJc}-${b.startJc + b.span - 1}节 ${b.course.kc}")
                        if (room.isNotBlank()) append(" · $room")
                        append("，${tails[idx % tails.size]}")
                    }
                }
                TYPE_AFTER -> {
                    val pool = if (today.dayOfWeek.value == 5)
                        listOf("周末模式，启动！去玩吧 ヾ(≧▽≦*)o") else afterPool
                    "今天的课上完啦，辛苦啦～" to pool[idx % pool.size]
                }
                else -> {
                    val b = ev.block!!
                    val room = b.rooms.ifBlank { b.course.room }.trim()
                    val at = snap.periods.firstOrNull { it.jc == b.startJc }?.start ?: "8:20"
                    "明天有早八哦" to buildString {
                        append("$at ${b.course.kc}")
                        if (room.isNotBlank()) append(" @ $room")
                        append(" · 早点休息呀，晚安 (´-ω-`)")
                    }
                }
            }
            // 事件过期自动失效: 课前提醒到上课时刻即失效, 不留僵尸托盘
            val expiry = when (ev.type) {
                TYPE_CLASS -> ev.classStart
                else -> ev.at.plusHours(2)
            }
            val timeoutMs = expiry?.let { ChronoUnit.MILLIS.between(now, it) } ?: 0L
            val builder = NotificationCompat.Builder(ctx, channelOf(ev.type))
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
            if (timeoutMs > 0) builder.setTimeoutAfter(timeoutMs)
            nm.notify(("rem${ev.type}${idx}${ev.at}${ev.block?.course?.kc ?: ""}").hashCode(), builder.build())
        }
    }

    /** 开发者模式: 排一条 delayMs 后触发的测试闹钟, 走真实 精确闹钟→接收器→通知 链路 */
    fun scheduleTestNotify(ctx: Context, delayMs: Long = 10_000L) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DEV_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + delayMs
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        DebugLog.log("remind", "测试提醒已排: +${delayMs}ms")
    }

    /** 发一条测试通知; 无通知权限时返回 false */
    fun notifyTest(ctx: Context, title: String, text: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannels(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        nm.notify(
            NOTIF_TEST,
            NotificationCompat.Builder(ctx, CHANNEL_CLASS)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build(),
        )
        DebugLog.log("remind", "测试通知已发: $title")
        return true
    }

    /** 触发时刻: 找出「恰好到期」的事件并发对应通知 (fire 的完整守卫见 [fire]) */
    suspend fun onDevTest(ctx: Context) {
        val ok = notifyTest(ctx, "测试提醒触发", "精确闹钟 → 接收器 → 通知，链路正常 ✅")
        if (!ok) DebugLog.log("remind", "测试触发到点, 但没有通知权限")
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val result = goAsync()
        thread(name = "kb-reminder") {
            try {
                ReminderScheduler.ensureChannels(ctx)
                when (intent.action) {
                    ReminderScheduler.ACTION_FIRE ->
                        runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.fire(ctx) } }
                            .onFailure { DebugLog.log("remind", "ACTION_FIRE 处理失败: ${it.message}") }
                            // 无论窗口内是否真有件都记投递尝试: 后台兜底靠它区分「已触发」与「被吞断链」
                            .also { Prefs.setLastFiredAt(ctx, System.currentTimeMillis()) }
                    ReminderScheduler.ACTION_DEV_TEST ->
                        runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.onDevTest(ctx) } }
                            .onFailure { DebugLog.log("remind", "ACTION_DEV_TEST 处理失败: ${it.message}") }
                    else -> {
                        // 系统自愈入口: BOOT / 快启 / 包更新 / 时间变化 / 精确闹钟权限变化。
                        // 包更新与重启会清掉全部已排闹钟: 先补发容差内已到期的事件, 再重排接力。
                        runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.fire(ctx) } }
                            .onFailure { DebugLog.log("remind", "自愈补发失败: ${it.message}") }
                        runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.reschedule(ctx) } }
                            .onFailure { DebugLog.log("remind", "自愈重排失败: ${it.message}") }
                    }
                }
            } finally {
                result.finish()
            }
            // goAsync 义务已了结: 小组件刷新尽力而为, 不占接收器关键路径 (防 ANR 断链)
            runCatching {
                val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
                val ids = mgr?.getAppWidgetIds(android.content.ComponentName(ctx, com.ccsut.kb.widget.WidgetProvider::class.java))
                if (mgr != null && ids != null && ids.isNotEmpty()) WidgetRenderer.render(ctx, mgr, ids)
            }
        }
    }
}
