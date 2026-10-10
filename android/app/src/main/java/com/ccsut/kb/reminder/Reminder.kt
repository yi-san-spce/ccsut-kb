package com.ccsut.kb.reminder

import android.Manifest
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
import com.ccsut.kb.util.KbClock
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
 * 贴心提醒: 通知链(放学小结/早八前夜/课前提醒)与闹钟链(闹钟模式的课前响铃)各维护
 * 「下一次事件」一条闹钟, 触发后发对应通知/响铃再排下一条;
 * 开机/改时间/数据更新后调用 [ReminderScheduler.reschedule] 重算。
 * 事件三类: 课前提醒 / 放学小结 / 早八前夜。
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.ccsut.kb.ACTION_CLASS_REMINDER"
    const val ACTION_DEV_TEST = "com.ccsut.kb.ACTION_DEV_TEST"
    const val ACTION_ALARM_RING = "com.ccsut.kb.ACTION_ALARM_RING"
    const val CHANNEL_ID = "class_reminder"
    const val CHANNEL_ALARM = "alarm_ring"
    const val NOTIF_ALARM_RING = 2002

    /** 响铃页的 extras: 直接带上课程信息, 页面不必重新推导 */
    const val EXTRA_KC = "kc"
    const val EXTRA_ROOM = "room"
    const val EXTRA_START = "start"
    const val EXTRA_LEAD = "lead"
    const val EXTRA_TEST = "test"

    private const val TYPE_CLASS = 0   // 课前提醒
    private const val TYPE_AFTER = 1   // 放学小结
    private const val TYPE_EARLY = 2   // 早八前夜

    /** 早八前夜提醒时刻 */
    private val EARLY_AT: LocalTime = LocalTime.of(21, 30)

    private data class Ev(
        val type: Int,
        val at: LocalDateTime,
        val block: Block? = null,          // CLASS/EARLY 关联课块
        val classStart: LocalDateTime? = null,
    )

    /** 链路路由: 开闹钟模式时 TYPE_CLASS 走全屏响铃, 否则走通知 (放学/早八恒走通知) */
    internal fun goesToAlarmChain(type: Int, alarmMode: Boolean): Boolean =
        type == TYPE_CLASS && alarmMode

    /** 迟到投递容差(分钟): 精确闹钟也可能被推迟几分钟, 降级闹钟 (Doze) 可达 15 分钟以上 */
    internal fun dueWindowMin(exact: Boolean): Long = if (exact) 30L else 60L

    private fun pi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * API 34+ 起, 经 PendingIntent 的后台 Activity 启动必须由创建方显式授权
     * (targetSdk 35+/新平台强制 opt-in, 否则 BAL 拦截 —— 响铃页弹不出, 模拟器实测)。
     * 只能设置 creator 侧模式 (sender 侧模式由创建方设置会被系统重置并抛异常, 实测);
     * 用 ALLOW_ALWAYS: 响铃发生时 App 几乎必然不在前台(锁屏/被杀), 闹钟语义本就始终放行。
     */
    private fun balOpts(): android.os.Bundle? {
        if (android.os.Build.VERSION.SDK_INT < 34) return null
        val ao = android.app.ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            )
        return ao.toBundle()
    }

    /** 响铃页 PI: 全屏意图通知与尽力直启共用 (extras 随重排更新) */
    private fun alarmActivityPi(ctx: Context, ev: Ev? = null): PendingIntent {
        val it = Intent(ctx, AlarmActivity::class.java).setAction(ACTION_ALARM_RING)
        if (ev != null) {
            it.putExtra(EXTRA_KC, ev.block!!.course.kc)
                .putExtra(EXTRA_ROOM, ev.block!!.rooms.ifBlank { ev.block!!.course.room }.trim())
                .putExtra(EXTRA_START, ev.classStart?.format(DateTimeFormatter.ofPattern("H:mm")) ?: "")
                .putExtra(EXTRA_LEAD, Prefs.reminderLead(ctx))
        }
        return PendingIntent.getActivity(
            ctx, 2, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            balOpts(),
        )
    }

    /** 闹钟链操作 = 广播 (接收器无 BAL 限制, 由它再拉起全屏意图通知/直启) */
    private fun alarmBroadcastPi(ctx: Context, ev: Ev?): PendingIntent {
        val it = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_ALARM_RING)
        if (ev != null) {
            it.putExtra(EXTRA_KC, ev.block!!.course.kc)
                .putExtra(EXTRA_ROOM, ev.block!!.rooms.ifBlank { ev.block!!.course.room }.trim())
                .putExtra(EXTRA_START, ev.classStart?.format(DateTimeFormatter.ofPattern("H:mm")) ?: "")
                .putExtra(EXTRA_LEAD, Prefs.reminderLead(ctx))
        }
        return PendingIntent.getBroadcast(
            ctx, 4, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAlarmChain(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(alarmBroadcastPi(ctx, null))
    }

    /** 系统闹钟图标点击后打开的界面 (AlarmClockInfo.showIntent 必须是 Activity) */
    private fun showPi(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 5, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 闹钟到点 (由 ReminderReceiver 调起): 双通道拉起响铃页
     * ① 全屏意图通知 —— 平台保障路径, 锁屏/后台直接全屏 (USE_FULL_SCREEN_INTENT, 闹钟类应用默认授予)
     * ② 尽力直启 —— BAL opt-in 生效的平台立即全屏, 不依赖通知权限
     */
    fun fireAlarmRing(ctx: Context, kc: String?, room: String?, start: String?, lead: Int?) {
        runCatching {
            ensureAlarmChannel(ctx)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val title = if (lead != null && lead > 0) "还有 $lead 分钟上课" else "快上课啦"
            val fullText = buildString {
                if (!start.isNullOrBlank()) append("$start · ")
                append(kc?.takeIf { it.isNotBlank() } ?: "该去上课啦")
                if (!room.isNullOrBlank()) append(" · $room")
            }
            val notif = NotificationCompat.Builder(ctx, CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(fullText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setFullScreenIntent(alarmActivityPi(ctx, null), true)
                .build()
            nm.notify(NOTIF_ALARM_RING, notif)
            // 尽力直启: BAL opt-in 生效的平台立即弹全屏 (FSI 也可能被系统转成 heads-up, 双保险)
            val intent = Intent(ctx, AlarmActivity::class.java).setAction(ACTION_ALARM_RING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (kc != null) intent.putExtra(EXTRA_KC, kc)
            if (room != null) intent.putExtra(EXTRA_ROOM, room)
            if (start != null) intent.putExtra(EXTRA_START, start)
            if (lead != null) intent.putExtra(EXTRA_LEAD, lead)
            ctx.startActivity(intent, balOpts())
            DebugLog.log("remind", "响铃通知已发 + 响铃页已尝试直启")
        }.onFailure {
            DebugLog.log("remind", "闹钟响铃链路失败: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    fun ensureAlarmChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ALARM) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "闹钟响铃", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "闹钟模式的课前响铃"
                enableVibration(true)
                setBypassDnd(true)
            },
        )
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.cancel(pi(ctx))
        cancelAlarmChain(ctx)
        Prefs.setNextReminderAt(ctx, 0L)
    }

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
        val week = (ChronoUnit.DAYS.between(start, day) / 7 + 1).toInt()
        if (week < 1 || week > snap.weeks) return emptyList()
        val pm = snap.periods.associateBy { it.jc }
        // 脏时间(空串/带秒/非数字)防御为 -1: 下面按 <0 过滤, 别让闹钟调度崩在后台线程
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
            val tw = (ChronoUnit.DAYS.between(start, tmr) / 7 + 1).toInt()
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
     * 重算下一次事件并设定闹钟。三开关各自独立生效:
     * 通知链 = 放学小结/早八前夜 + (开提醒且未开闹钟模式时的课前),
     * 闹钟链 = 开提醒且开闹钟模式时的课前 (setAlarmClock, 全屏响铃)。
     */
    suspend fun reschedule(ctx: Context) = withContext(Dispatchers.IO) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withContext
        val classOn = Prefs.reminderOn(ctx)
        val afterOn = Prefs.afterClassOn(ctx)
        val earlyOn = Prefs.earlyOn(ctx)
        val alarmMode = classOn && Prefs.alarmMode(ctx)
        if (!classOn && !afterOn && !earlyOn) {
            cancel(ctx); return@withContext
        }
        val snap = ClassCache.load(ctx) ?: return@withContext
        if (snap.courses.isEmpty()) return@withContext
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull()
            ?: return@withContext
        val lead = Prefs.reminderLead(ctx)
        val now = KbClock.now()

        var day = now.toLocalDate()
        var bestNotify: Ev? = null
        var bestAlarm: Ev? = null
        var scanned = 0
        val maxScan = snap.weeks * 7 + 14  // 护栏: 设备日期严重回拨时不再无限扫
        while ((bestNotify == null || bestAlarm == null) && scanned++ < maxScan) {
            val week = (ChronoUnit.DAYS.between(start, day) / 7 + 1).toInt()
            if (week > snap.weeks) break
            val future = { e: Ev -> e.at.isAfter(now) }
            if (bestNotify == null) {
                bestNotify = dayEvents(snap, start, day, lead, classOn && !alarmMode, afterOn, earlyOn)
                    .filter(future)
                    .minByOrNull { it.at }
            }
            if (bestAlarm == null && alarmMode) {
                bestAlarm = dayEvents(snap, start, day, lead, classOn, afterOn = false, earlyOn = false)
                    .filter(future)
                    .minByOrNull { it.at }
            }
            day = day.plusDays(1)
        }

        val millisNotify = bestNotify?.let { it.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val millisAlarm = bestAlarm?.let { it.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        if (millisNotify == null) {
            am.cancel(pi(ctx))
        } else if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millisNotify, pi(ctx))
        } else {
            // 未授予「闹钟和提醒」时的降级: 非精确闹钟, 可能晚几分钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millisNotify, pi(ctx))
        }
        if (!alarmMode || millisAlarm == null) {
            cancelAlarmChain(ctx)
        } else {
            // setAlarmClock: 系统闹钟语义 (状态栏闹钟图标, 厂商省电策略最尊重)
            // 操作=广播 (无 BAL 限制), 由 ReminderReceiver 再拉起全屏意图通知/直启响铃页
            am.setAlarmClock(AlarmManager.AlarmClockInfo(millisAlarm, showPi(ctx)), alarmBroadcastPi(ctx, bestAlarm))
        }
        val next = listOfNotNull(millisNotify, millisAlarm).minOrNull() ?: 0L
        Prefs.setNextReminderAt(ctx, next)
        bestNotify?.let { DebugLog.log("remind", "通知链下次 ${it.at} (type=${it.type})") }
        bestAlarm?.let { DebugLog.log("remind", "闹钟链下次 ${it.at} 课程=${it.block?.course?.kc}") }
    }

    /** 开发者模式: 排一条 delayMs 后触发的测试闹钟, 走真实 闹钟→接收器→通知 链路 */
    fun scheduleTestAlarm(ctx: Context, delayMs: Long = 10_000L) {
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
        DebugLog.log("remind", "测试闹钟已排: +${delayMs}ms")
    }

    /** 开发者模式: 10 秒后全链路试响 (setAlarmClock→广播→全屏意图通知/直启, 与生产路径一致) */
    fun scheduleTestRing(ctx: Context, delayMs: Long = 10_000L) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val it = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_ALARM_RING)
            .putExtra(EXTRA_KC, "高等数学（测试）")
            .putExtra(EXTRA_ROOM, "7-南206")
            .putExtra(EXTRA_START, "8:20")
            .putExtra(EXTRA_LEAD, 15)
            .putExtra(EXTRA_TEST, true)
        val pi = PendingIntent.getBroadcast(
            ctx, 3, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + delayMs
        if (am.canScheduleExactAlarms()) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPi(ctx)), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        DebugLog.log("remind", "测试响铃已排: +${delayMs}ms (setAlarmClock)")
    }

    /** 发一条测试通知; 无通知权限时返回 false */
    fun notifyTest(ctx: Context, title: String, text: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        nm.notify(
            1001,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
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

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "贴心提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课前提醒、放学小结和早八前夜的温馨问候"
                enableVibration(true)
            },
        )
    }

    /** 触发时刻: 找出「恰好到期」的事件并发对应通知 (闹钟模式下的课前事件不在这里, 走响铃页) */
    internal suspend fun fire(ctx: Context) = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // 不再纯静默: 闹钟照排但通知发不出去, 留痕便于开发者面板诊断
            DebugLog.log("remind", "有到期提醒但缺 POST_NOTIFICATIONS 权限, 通知被丢弃")
            return@withContext
        }
        val snap = ClassCache.load(ctx) ?: return@withContext
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull() ?: return@withContext
        val now = KbClock.now()
        val today = now.toLocalDate()
        val lead = Prefs.reminderLead(ctx)
        val classOn = Prefs.reminderOn(ctx)
        val alarmMode = classOn && Prefs.alarmMode(ctx)

        // 投递窗口: 精确闹钟也可能被系统推迟几分钟, 降级模式 (Doze) 可达 15 分钟以上 —— 从宽不丢件
        val am2 = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val windowMin = dueWindowMin(am2?.canScheduleExactAlarms() == true)
        val due = dayEvents(snap, start, today, lead, classOn && !alarmMode, Prefs.afterClassOn(ctx), Prefs.earlyOn(ctx))
            .filter { !it.at.isAfter(now) && it.at.isAfter(now.minusMinutes(windowMin)) }
            .filter { it.type != TYPE_CLASS || it.classStart!!.isAfter(now) }
        if (due.isEmpty()) return@withContext
        DebugLog.log("remind", "触发 ${due.size} 条提醒: ${due.joinToString { t -> when (t.type) { 0 -> "课前"; 1 -> "放学"; else -> "早八" } }}")

        ensureChannel(ctx)
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

        due.take(3).forEach { ev ->
            val (title, text) = when (ev.type) {
                TYPE_CLASS -> {
                    val b = ev.block!!
                    val room = b.rooms.ifBlank { b.course.room }.trim()
                    "还有 $lead 分钟上课" to buildString {
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
            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build()
            nm.notify(("rem${ev.type}${idx}${ev.at}${ev.block?.course?.kc ?: ""}").hashCode(), notification)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val fire = intent.action == ReminderScheduler.ACTION_FIRE
        val devTest = intent.action == ReminderScheduler.ACTION_DEV_TEST
        val alarmRing = intent.action == ReminderScheduler.ACTION_ALARM_RING
        val result = goAsync()
        thread(name = "kb-reminder") {
            try {
                ReminderScheduler.ensureChannel(ctx)
                if (fire) runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.fire(ctx) } }
                if (devTest) {
                    runCatching {
                        val ok = ReminderScheduler.notifyTest(ctx, "测试闹钟触发", "闹钟 → 接收器 → 通知，链路正常 ✅")
                        if (!ok) DebugLog.log("remind", "测试闹钟到点, 但没有通知权限")
                    }
                }
                if (alarmRing) runCatching {
                    ReminderScheduler.fireAlarmRing(
                        ctx,
                        intent.getStringExtra(ReminderScheduler.EXTRA_KC),
                        intent.getStringExtra(ReminderScheduler.EXTRA_ROOM),
                        intent.getStringExtra(ReminderScheduler.EXTRA_START),
                        intent.getIntExtra(ReminderScheduler.EXTRA_LEAD, -1).takeIf { it > 0 },
                    )
                }.onFailure { DebugLog.log("remind", "ACTION_ALARM_RING 处理失败: ${it.message}") }
                if (Prefs.reminderOn(ctx) || Prefs.afterClassOn(ctx) || Prefs.earlyOn(ctx))
                    runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.reschedule(ctx) } }
                // 上下课边界顺带刷新小组件的「进行中 / 下节课」状态
                runCatching {
                    val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
                    val ids = mgr?.getAppWidgetIds(android.content.ComponentName(ctx, com.ccsut.kb.widget.WidgetProvider::class.java))
                    if (mgr != null && ids != null && ids.isNotEmpty()) WidgetRenderer.render(ctx, mgr, ids)
                }
            } finally {
                result.finish()
            }
        }
    }
}
