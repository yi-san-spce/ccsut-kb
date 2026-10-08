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
import com.ccsut.kb.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

/**
 * 贴心提醒: 只维护「下一次事件」这一条闹钟, 触发后发对应通知再排下一条;
 * 开机/改时间/数据更新后调用 [ReminderScheduler.reschedule] 重算。
 * 事件三类: 课前提醒 / 放学小结 / 早八前夜。
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.ccsut.kb.ACTION_CLASS_REMINDER"
    const val ACTION_DEV_TEST = "com.ccsut.kb.ACTION_DEV_TEST"
    const val CHANNEL_ID = "class_reminder"

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

    private fun pi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.cancel(pi(ctx))
        Prefs.setNextReminderAt(ctx, 0L)
    }

    /** 某一天会发生的提醒事件(放学/早八/课前) */
    private fun dayEvents(
        snap: ClassCache.Snapshot,
        start: LocalDate,
        day: LocalDate,
        lead: Int,
        afterOn: Boolean,
        earlyOn: Boolean,
    ): List<Ev> {
        val week = (ChronoUnit.DAYS.between(start, day) / 7 + 1).toInt()
        if (week < 1 || week > snap.weeks) return emptyList()
        val pm = snap.periods.associateBy { it.jc }
        fun mins(t: String): Int {
            val (h, m) = t.split(":").map { it.trim().toInt() }
            return h * 60 + m
        }
        fun at(d: LocalDate, min: Int) = d.atTime(min / 60, min % 60)

        val blocks = Merger.blocksOf(snap.courses, week)
            .filter { it.course.day == day.dayOfWeek.value }
            .filter { pm[it.startJc] != null && pm[it.startJc + it.span - 1] != null }
        val out = mutableListOf<Ev>()
        if (blocks.isNotEmpty()) {
            for (b in blocks) {
                val cs = at(day, mins(pm[b.startJc]!!.start))
                out += Ev(TYPE_CLASS, cs.minusMinutes(lead.toLong()), b, cs)
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
                if (first != null && first.startJc == 1 && pm[1] != null) {
                    out += Ev(TYPE_EARLY, day.atTime(EARLY_AT), first, at(tmr, mins(pm[1]!!.start)))
                }
            }
        }
        return out
    }

    /** 重算下一次事件并设定闹钟; 数据/班级/设置就绪才有值 */
    suspend fun reschedule(ctx: Context) = withContext(Dispatchers.IO) {
        if (!Prefs.reminderOn(ctx)) {
            cancel(ctx); return@withContext
        }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return@withContext
        val snap = ClassCache.load(ctx) ?: return@withContext
        if (snap.courses.isEmpty()) return@withContext
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull()
            ?: return@withContext
        val lead = Prefs.reminderLead(ctx)
        val afterOn = Prefs.afterClassOn(ctx)
        val earlyOn = Prefs.earlyOn(ctx)
        val now = KbClock.now()

        var day = now.toLocalDate()
        var best: Ev? = null
        var scanned = 0
        val maxScan = snap.weeks * 7 + 14  // 护栏: 设备日期严重回拨时不再无限扫
        while (best == null && scanned++ < maxScan) {
            val week = (ChronoUnit.DAYS.between(start, day) / 7 + 1).toInt()
            if (week > snap.weeks) break
            best = dayEvents(snap, start, day, lead, afterOn, earlyOn)
                .filter { it.at.isAfter(now) }
                .minByOrNull { it.at }
            day = day.plusDays(1)
        }

        val ev = best ?: run { Prefs.setNextReminderAt(ctx, 0L); am.cancel(pi(ctx)); return@withContext }
        val millis = ev.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(ctx))
        } else {
            // 未授予「闹钟和提醒」时的降级: 非精确闹钟, 可能晚几分钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(ctx))
        }
        Prefs.setNextReminderAt(ctx, millis)
        DebugLog.log("remind", "下次事件 ${ev.at} (type=${ev.type}, 课程=${ev.block?.course?.kc ?: "-"})")
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

    /** 触发时刻: 找出「恰好到期」的事件并发对应通知 */
    internal suspend fun fire(ctx: Context) = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@withContext
        val snap = ClassCache.load(ctx) ?: return@withContext
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull() ?: return@withContext
        val now = KbClock.now()
        val today = now.toLocalDate()
        val lead = Prefs.reminderLead(ctx)

        // 投递窗口: 精确闹钟 10 分钟容差; 降级模式 (Doze 可延迟 15 分钟以上) 放宽到 35 分钟
        val am2 = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val windowMin = if (am2?.canScheduleExactAlarms() == true) 10L else 35L
        val due = dayEvents(snap, start, today, lead, Prefs.afterClassOn(ctx), Prefs.earlyOn(ctx))
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
                if (Prefs.reminderOn(ctx)) runCatching { kotlinx.coroutines.runBlocking { ReminderScheduler.reschedule(ctx) } }
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
