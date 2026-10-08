package com.ccsut.kb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.ccsut.kb.MainActivity
import com.ccsut.kb.Prefs
import com.ccsut.kb.R
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.ui.CourseColors
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.Weeks
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread
import kotlin.math.abs

/** 今日课程小组件: 只读 ClassCache 快照, 30 分钟周期 / APP 数据变化 / 尺寸变化 / 闹钟触发时刷新 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        thread(name = "kb-widget") {
            try { WidgetRenderer.render(ctx, mgr, ids) } finally { result.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        val result = goAsync()
        thread(name = "kb-widget") {
            try { WidgetRenderer.render(ctx, mgr, intArrayOf(id)) } finally { result.finish() }
        }
    }
}

object WidgetRenderer {

    // 12 色药丸背景(与 CourseColors 自定义色板同源), 深浅两套由渲染时显式选择:
    // 不能靠 values-night —— 文字色是渲染时烤进 RemoteViews 的, 进程 config 同步有延迟会深浅错配
    private val PILL = intArrayOf(
        R.drawable.widget_pill_0, R.drawable.widget_pill_1, R.drawable.widget_pill_2,
        R.drawable.widget_pill_3, R.drawable.widget_pill_4, R.drawable.widget_pill_5,
        R.drawable.widget_pill_6, R.drawable.widget_pill_7, R.drawable.widget_pill_8,
        R.drawable.widget_pill_9, R.drawable.widget_pill_10, R.drawable.widget_pill_11,
    )
    private val PILL_D = intArrayOf(
        R.drawable.widget_pill_d0, R.drawable.widget_pill_d1, R.drawable.widget_pill_d2,
        R.drawable.widget_pill_d3, R.drawable.widget_pill_d4, R.drawable.widget_pill_d5,
        R.drawable.widget_pill_d6, R.drawable.widget_pill_d7, R.drawable.widget_pill_d8,
        R.drawable.widget_pill_d9, R.drawable.widget_pill_d10, R.drawable.widget_pill_d11,
    )

    // 容器底跟随背景图取色: 种子色按色相分桶 12×30°, 与 pill 同一套色相体系
    private val BG = intArrayOf(
        R.drawable.widget_bg_h0, R.drawable.widget_bg_h1, R.drawable.widget_bg_h2,
        R.drawable.widget_bg_h3, R.drawable.widget_bg_h4, R.drawable.widget_bg_h5,
        R.drawable.widget_bg_h6, R.drawable.widget_bg_h7, R.drawable.widget_bg_h8,
        R.drawable.widget_bg_h9, R.drawable.widget_bg_h10, R.drawable.widget_bg_h11,
    )
    private val BG_D = intArrayOf(
        R.drawable.widget_bg_dh0, R.drawable.widget_bg_dh1, R.drawable.widget_bg_dh2,
        R.drawable.widget_bg_dh3, R.drawable.widget_bg_dh4, R.drawable.widget_bg_dh5,
        R.drawable.widget_bg_dh6, R.drawable.widget_bg_dh7, R.drawable.widget_bg_dh8,
        R.drawable.widget_bg_dh9, R.drawable.widget_bg_dh10, R.drawable.widget_bg_dh11,
    )

    /** 深色判断直接读系统设置, 避开进程 config 尚未同步的窗口期 */
    private fun isDark(ctx: Context): Boolean {
        val v = runCatching {
            android.provider.Settings.Secure.getInt(ctx.contentResolver, "ui_night_mode", 1)
        }.getOrDefault(1)
        return when (v) {
            1 -> false
            2 -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }

    // 容器/标题/次级/强调色全部运行时烤进 RemoteViews —— launcher 重布局 XML 主题色有时机问题
    private const val C_TITLE_L = 0xFF1C1B20.toInt()
    private const val C_TITLE_D = 0xFFE5E1E9.toInt()
    private const val C_SEC_L = 0xFF4A454E.toInt()
    private const val C_SEC_D = 0xFF948F9C.toInt()
    private const val C_ACC_L = 0xFF4756B8.toInt()
    private const val C_ACC_D = 0xFFB7C4FF.toInt()

    private fun applyChrome(rv: RemoteViews, dark: Boolean, followBg: Boolean, hue: Int) {
        rv.setInt(
            R.id.w_root, "setBackgroundResource",
            when {
                followBg -> if (dark) BG_D[hue] else BG[hue]
                dark -> R.drawable.widget_bg_d
                else -> R.drawable.widget_bg
            },
        )
        if (followBg) {
            // 文字色从同一色相派生 (setTextColor 接受运行时色值, remotable 安全)
            val h = hue * 30f
            val sec = hsl(h, 0.14f, if (dark) 0.70f else 0.36f)
            rv.setTextColor(R.id.w_title, hsl(h, 0.28f, if (dark) 0.90f else 0.16f))
            rv.setTextColor(R.id.w_date, sec)
            rv.setTextColor(R.id.w_next, hsl(h, 0.50f, if (dark) 0.80f else 0.40f))
            rv.setTextColor(R.id.w_more, sec)
            rv.setTextColor(R.id.w_empty, sec)
        } else {
            rv.setTextColor(R.id.w_title, if (dark) C_TITLE_D else C_TITLE_L)
            rv.setTextColor(R.id.w_date, if (dark) C_SEC_D else C_SEC_L)
            rv.setTextColor(R.id.w_next, if (dark) C_ACC_D else C_ACC_L)
            rv.setTextColor(R.id.w_more, if (dark) C_SEC_D else C_SEC_L)
            rv.setTextColor(R.id.w_empty, if (dark) C_SEC_D else C_SEC_L)
        }
    }

    /** 种子色色相分桶到 12×30°, 与课程 pill 的色相体系对齐; 近灰归 0 桶 */
    private fun hueBucket(seed: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed, hsv)
        if (hsv[1] < 0.08f) return 0
        return (((hsv[0] + 15f) / 30f).toInt()) % 12
    }

    private fun hsl(h: Float, s: Float, l: Float): Int =
        ColorUtils.HSLToColor(floatArrayOf(h % 360f, s, l))

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val snap = ClassCache.load(ctx)
        for (id in ids) {
            runCatching { mgr.updateAppWidget(id, build(ctx, mgr, id, snap)) }
                .onFailure { com.ccsut.kb.util.DebugLog.log("widget", "渲染失败 id=$id: ${it.message}") }
        }
        if (ids.isNotEmpty()) {
            com.ccsut.kb.util.DebugLog.log("widget", "render ${ids.size} 个, today=${KbClock.today()}")
        }
    }

    // 单线程串行渲染: 快速连调时合并请求, 避免并发多份 ClassCache 文件 IO
    private val renderExec = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "kb-widget")
    }

    /** APP 侧数据/班级变化后调用; 未添加小组件时无操作 */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
        if (ids.isEmpty()) return
        renderExec.execute { render(ctx, mgr, ids) }
    }

    /** 小组件越高给的行数越多 (OPTION_APPWIDGET_MAX_HEIGHT 单位是 dp) */
    private fun maxRows(opts: Bundle?): Int {
        val h = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return when {
            h >= 260 -> 6
            h >= 180 -> 5
            else -> 4
        }
    }

    private fun build(ctx: Context, mgr: AppWidgetManager, id: Int, snapIn: ClassCache.Snapshot?): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_today)
        val dark = isDark(ctx)
        val followBg = Prefs.colorSource(ctx) == 1 && Prefs.bgSeed(ctx) != 0
        val hue = if (followBg) hueBucket(Prefs.bgSeed(ctx)) else 0
        applyChrome(rv, dark, followBg, hue)
        rv.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        if (snapIn == null) {
            rv.setTextViewText(R.id.w_title, "长工课表通")
            rv.setTextViewText(R.id.w_date, "")
            rv.setViewVisibility(R.id.w_next, View.GONE)
            rv.setViewVisibility(R.id.w_rows, View.GONE)
            rv.setViewVisibility(R.id.w_more, View.GONE)
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
            rv.setTextViewText(R.id.w_empty, "打开APP选择班级后展示今日课程")
            return rv
        }

        val today = KbClock.today()
        val day = today.dayOfWeek.value
        val week = Weeks.currentWeek(snapIn.startDate, snapIn.weeks, today)
        rv.setTextViewText(R.id.w_title, "第${week}周 · ${Weeks.cnDay(day)}")
        rv.setTextViewText(R.id.w_date, "${today.monthValue}/${today.dayOfMonth}")

        val blocks: List<Block> = Merger.blocksOf(snapIn.courses, week).filter { it.course.day == day }
        rv.removeAllViews(R.id.w_rows)

        if (blocks.isEmpty()) {
            rv.setViewVisibility(R.id.w_rows, View.GONE)
            rv.setViewVisibility(R.id.w_more, View.GONE)
            rv.setViewVisibility(R.id.w_next, View.GONE)
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
            rv.setTextViewText(R.id.w_empty, "今天没有课，好好休息 🎉" + tomorrowHint(snapIn, today))
            return rv
        }
        rv.setViewVisibility(R.id.w_rows, View.VISIBLE)
        rv.setViewVisibility(R.id.w_empty, View.GONE)

        val now = LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        // (课块, 起止分钟, 起始时间文本)
        val timeline = blocks.map { b ->
            val s = snapIn.periods.firstOrNull { it.jc == b.startJc }
            val e = snapIn.periods.firstOrNull { it.jc == b.startJc + b.span - 1 }
            Triple(b, (s?.let { ClassCache.minutesOf(it.start) } ?: 0) to (e?.let { ClassCache.minutesOf(it.end) } ?: 1440), s?.start ?: "")
        }
        val ongoing = timeline.filter { nowMin >= it.second.first && nowMin < it.second.second }
        val next = timeline.firstOrNull { it.second.first > nowMin }
        when {
            next != null -> {
                rv.setViewVisibility(R.id.w_next, View.VISIBLE)
                rv.setTextViewText(R.id.w_next, "下节课 ${next.third} · ${next.first.course.kc}")
            }
            ongoing.isNotEmpty() -> {
                rv.setViewVisibility(R.id.w_next, View.VISIBLE)
                rv.setTextViewText(R.id.w_next, "最后一节课进行中")
            }
            else -> {
                rv.setViewVisibility(R.id.w_next, View.VISIBLE)
                rv.setTextViewText(R.id.w_next, "今天的课都结束啦 🎉")
            }
        }

        val colors = Prefs.courseColors(ctx)
        val rows = maxRows(mgr.getAppWidgetOptions(id))
        timeline.take(rows).forEach { (b, span, _) ->
            val item = RemoteViews(ctx.packageName, R.layout.widget_item)
            val seed = CourseColors.seedOf(b.course.kc, b.course.fx)
            // 自定义颜色优先; 未自定义按种子稳定取 12 色板
            val i = colors[seed] ?: abs(seed.hashCode()) % 12
            item.setInt(R.id.w_item, "setBackgroundResource", if (dark) PILL_D[i] else PILL[i])
            val on = CourseColors.customOnContainer(i, dark).toArgb()
            item.setTextColor(R.id.w_name, on)
            item.setTextColor(R.id.w_meta, ColorUtils.setAlphaComponent(on, 214))
            item.setTextViewText(R.id.w_name, b.course.kc)
            val jcs = "第${b.startJc}-${b.startJc + b.span - 1}节"
            val room = b.course.room.ifBlank { null } ?: b.rooms.ifBlank { null }
            val isOngoing = ongoing.any { it.first == b }
            item.setTextViewText(
                R.id.w_meta,
                when {
                    isOngoing && room != null -> "进行中 · $room"
                    isOngoing -> "进行中 · $jcs"
                    room != null -> "$jcs · $room"
                    else -> jcs
                },
            )
            rv.addView(R.id.w_rows, item)
        }
        if (timeline.size > rows) {
            rv.setViewVisibility(R.id.w_more, View.VISIBLE)
            rv.setTextViewText(R.id.w_more, "还有 ${timeline.size - rows} 门课…")
        } else {
            rv.setViewVisibility(R.id.w_more, View.GONE)
        }
        return rv
    }

    /** 无课日预告明天课程数, 学期外不显示 */
    private fun tomorrowHint(snap: ClassCache.Snapshot, today: LocalDate): String {
        val start = runCatching { LocalDate.parse(snap.startDate) }.getOrNull() ?: return ""
        val tmr = today.plusDays(1)
        val offset = ChronoUnit.DAYS.between(start, tmr)
        if (offset < 0 || offset >= snap.weeks * 7L) return ""
        val n = Merger.blocksOf(snap.courses, (offset / 7 + 1).toInt()).count { it.course.day == tmr.dayOfWeek.value }
        return if (n > 0) "\n明天有 $n 节课" else ""
    }
}
