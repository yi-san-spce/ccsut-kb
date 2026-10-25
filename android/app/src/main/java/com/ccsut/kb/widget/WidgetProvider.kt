package com.ccsut.kb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.ccsut.kb.ui.DarkColors
import com.ccsut.kb.ui.LightColors
import com.ccsut.kb.ui.ThemePacks
import com.ccsut.kb.MainActivity
import com.ccsut.kb.Prefs
import com.ccsut.kb.R
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.ui.CourseColors
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Weeks
import java.time.LocalTime
import kotlin.concurrent.thread

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

    /** 深色判断: 系统设置优先, 键缺失(旧 ROM)时退回进程 config */
    private fun isDark(ctx: Context): Boolean {
        val v = runCatching {
            android.provider.Settings.Secure.getInt(ctx.contentResolver, "ui_night_mode", -1)
        }.getOrDefault(-1)
        return when (v) {
            1 -> false
            2 -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** 与 KbTheme 同一套配色决策: 小组件渲染时从 Prefs 重建 ColorScheme, 课程块与 App 完全同色 */
    private fun schemeFor(ctx: Context, dark: Boolean): ColorScheme {
        val pack = Prefs.styleTheme(ctx).takeIf { it != 0 }?.let { ThemePacks.byId(it) }
        return when {
            pack != null -> if (dark) pack.dark else pack.light
            Prefs.colorSource(ctx) == 1 && Prefs.bgSeed(ctx) != 0 ->
                com.materialkolor.dynamicColorScheme(
                    seedColor = androidx.compose.ui.graphics.Color(Prefs.bgSeed(ctx)),
                    isDark = dark,
                    isAmoled = false,
                    style = com.materialkolor.PaletteStyle.TonalSpot,
                )
            Prefs.colorSource(ctx) == 2 -> if (dark) DarkColors else LightColors
            else -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
    }

    // 容器/标题/次级/强调色全部运行时烤进 RemoteViews —— launcher 重布局 XML 主题色有时机问题
    private const val C_TITLE_L = 0xFF1C1B20.toInt()
    private const val C_TITLE_D = 0xFFE5E1E9.toInt()
    private const val C_SEC_L = 0xFF4A454E.toInt()
    private const val C_SEC_D = 0xFF948F9C.toInt()
    private const val C_ACC_L = 0xFF4756B8.toInt()
    private const val C_ACC_D = 0xFFB7C4FF.toInt()

    private fun hsl(h: Float, s: Float, l: Float): Int =
        ColorUtils.HSLToColor(floatArrayOf(h % 360f, s, l))

    /** 种子色色相分桶到 12×30°, 与课程 pill 的色相体系对齐; 近灰归 0 桶 */
    private fun hueBucket(seed: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed, hsv)
        if (hsv[1] < 0.08f) return 0
        return (((hsv[0] + 15f) / 30f).toInt()) % 12
    }

    // ---- 渲染场景: 每个 widget id 算一次, S/M/L 三档构建共用 ----

    private class Scene(
        val noSnap: Boolean,
        val dark: Boolean,
        val followBg: Boolean,
        val hue: Int,
        val glass: Bitmap?,          // null = 玻璃不可用/关闭, 回落实色卡片
        val solidBg: Int,            // 实色回退的卡片底色 (followBg 桶色或默认底)
        val title: String,
        val date: String,
        val nextText: String?,       // 有课时的「下节课/进行中/结束」行; 空态 null
        val emptyTitle: String?,     // 空态大字 (null = 有课)
        val emptySub: String,
        val bottomText: String?,     // 空态/全天结束时的「下一次课」行
        val today: List<Agenda.Entry>,
        val groups: List<Agenda.DayGroup>,   // L 档议程 (今天+后续有课日)
        val scheme: ColorScheme,
        val colors: Map<String, Int>,
    )

    private fun sceneOf(ctx: Context, mgr: AppWidgetManager, id: Int, snapIn: ClassCache.Snapshot?): Scene {
        val dark = isDark(ctx)
        val followBg = Prefs.colorSource(ctx) == 1 && Prefs.bgSeed(ctx) != 0
        val hue = if (followBg) hueBucket(Prefs.bgSeed(ctx)) else 0

        // 玻璃: 快照染色基色沿用实色卡片的底色 (followBg 时随背景图色相), 高光强度同 App glassEdge
        val tintRes = when {
            followBg -> if (dark) BG_D[hue] else BG[hue]
            dark -> R.drawable.widget_bg_d
            else -> R.drawable.widget_bg
        }
        val solidBg = solidColorOf(ctx, tintRes)
        val strength = Prefs.widgetGlass(ctx)
        val opts = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) ?: 180
        val hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
        val glass = WidgetGlass.snapshot(ctx, strength, solidBg, wDp, hDp, if (dark) 0.14f else 0.42f)

        val scheme = schemeFor(ctx, dark)
        val colors = Prefs.courseColors(ctx)

        if (snapIn == null) {
            return Scene(
                noSnap = true, dark = dark, followBg = followBg, hue = hue,
                glass = glass, solidBg = solidBg,
                title = "长工课表通", date = "", nextText = null,
                emptyTitle = "长工课表通", emptySub = "打开APP选择班级后展示今日课程",
                bottomText = null, today = emptyList(), groups = emptyList(),
                scheme = scheme, colors = colors,
            )
        }

        CourseColors.bindOrder(ctx, snapIn.courses.map { c -> CourseColors.seedOf(c.kc, c.fx) })
        val today = KbClock.today()
        val day = today.dayOfWeek.value
        val week = Weeks.currentWeek(snapIn.startDate, snapIn.weeks, today)
        val todayEntries = Agenda.entriesOfDay(snapIn.courses, snapIn.periods, week, day)

        val now = LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        val ongoing = todayEntries.filter { nowMin >= it.startMin && nowMin < it.endMin }
        val next = todayEntries.firstOrNull { it.startMin > nowMin }
        val nextText = when {
            todayEntries.isEmpty() -> null
            next != null -> "下节课 ${next.tStart} · ${next.block.course.kc}"
            ongoing.isNotEmpty() -> "最后一节课进行中"
            else -> "今天的课都结束啦"
        }

        // 空态/收尾态: 大字标题 + 「下一次课」底行 (今天没剩课 ≠ 明天有课, 周末/假期在这里兜住)
        val termOver = week > snapIn.weeks
        val empty = todayEntries.isEmpty()
        val emptyTitle = when {
            empty && termOver -> "假期愉快"
            empty -> "今天没有课"
            else -> null
        }
        val emptySub = if (termOver) "本学期课程已结束" else "好好休息"
        val nextClass = if (next == null) {
            Agenda.nextClass(snapIn.courses, snapIn.periods, snapIn.startDate, snapIn.weeks, today)
        } else null
        val bottomText = nextClass?.let { g ->
            val first = g.entries.first()
            "下一次 ${g.label} ${first.tStart} · ${first.block.course.kc}"
        }

        return Scene(
            noSnap = false, dark = dark, followBg = followBg, hue = hue,
            glass = glass, solidBg = solidBg,
            title = "第${week}周 · ${Weeks.cnDay(day)}",
            date = "${today.monthValue}/${today.dayOfMonth}",
            nextText = nextText,
            emptyTitle = emptyTitle, emptySub = emptySub, bottomText = bottomText,
            today = todayEntries,
            groups = Agenda.groups(snapIn.courses, snapIn.periods, snapIn.startDate, snapIn.weeks, today),
            scheme = scheme, colors = colors,
        )
    }

    private fun build(ctx: Context, mgr: AppWidgetManager, id: Int, snapIn: ClassCache.Snapshot?): RemoteViews {
        val s = sceneOf(ctx, mgr, id, snapIn)
        // 响应式分档 (API 31+ 原生): 启动器按实际尺寸选最近档, 根治"估行数与桌面 cell 偏差"的遮挡
        // 锚点覆盖常见格位: 2×1(150×100) / 2×2(150×190) / 3×2(230×120)→M / 3×3+(230×190)→L
        val mapping = mapOf(
            SizeF(150f, 100f) to bucketS(ctx, s),
            SizeF(180f, 130f) to bucketM(ctx, s),
            SizeF(230f, 190f) to bucketL(ctx, s),
        )
        return RemoteViews(mapping)
    }

    // ---- 公共底座: 玻璃底/实色回退 + 点击 + 文字色 ----

    private fun applyBase(rv: RemoteViews, ctx: Context, s: Scene, vararg textIds: Int) {
        rv.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        if (s.glass != null) {
            rv.setViewVisibility(R.id.w_glass, View.VISIBLE)
            rv.setImageViewBitmap(R.id.w_glass, s.glass)
            rv.setInt(R.id.w_root, "setCardBackgroundColor", Color.TRANSPARENT)
        } else {
            rv.setViewVisibility(R.id.w_glass, View.GONE)
            rv.setInt(R.id.w_root, "setCardBackgroundColor", s.solidBg)
        }
        // 文字色: followBg 从同一色相派生, 否则常量; 只给本档存在的 id 上色 (RemoteViews 对缺失 id 静默跳过)
        val byId = mapOf(
            R.id.w_title to (if (s.followBg) hsl(s.hue * 30f, 0.28f, if (s.dark) 0.90f else 0.16f)
            else if (s.dark) C_TITLE_D else C_TITLE_L),
            R.id.w_date to (if (s.followBg) hsl(s.hue * 30f, 0.14f, if (s.dark) 0.70f else 0.36f)
            else if (s.dark) C_SEC_D else C_SEC_L),
            R.id.w_next to (if (s.followBg) hsl(s.hue * 30f, 0.50f, if (s.dark) 0.80f else 0.40f)
            else if (s.dark) C_ACC_D else C_ACC_L),
            R.id.w_bottom_next to (if (s.followBg) hsl(s.hue * 30f, 0.50f, if (s.dark) 0.80f else 0.40f)
            else if (s.dark) C_ACC_D else C_ACC_L),
            R.id.w_more to (if (s.followBg) hsl(s.hue * 30f, 0.14f, if (s.dark) 0.70f else 0.36f)
            else if (s.dark) C_SEC_D else C_SEC_L),
            R.id.w_agenda_head to (if (s.followBg) hsl(s.hue * 30f, 0.14f, if (s.dark) 0.70f else 0.36f)
            else if (s.dark) C_SEC_D else C_SEC_L),
            R.id.w_empty_title to (if (s.followBg) hsl(s.hue * 30f, 0.28f, if (s.dark) 0.90f else 0.16f)
            else if (s.dark) C_TITLE_D else C_TITLE_L),
            R.id.w_empty_sub to (if (s.followBg) hsl(s.hue * 30f, 0.14f, if (s.dark) 0.70f else 0.36f)
            else if (s.dark) C_SEC_D else C_SEC_L),
        )
        textIds.forEach { id -> byId[id]?.let { c -> rv.setTextColor(id, c) } }
    }

    /** 实色底色提取: shape drawable 的 solid 色直接读出来当 CardView 背景色/玻璃染色基色 */
    private fun solidColorOf(ctx: Context, resId: Int): Int = runCatching {
        (ContextCompat.getDrawable(ctx, resId) as? GradientDrawable)?.color?.defaultColor ?: 0
    }.getOrDefault(0xFFECEBF1.toInt())

    // ---- S 档 (≈2×1): 标题 + 单行 (下一节课 / 下一次课 / 空态) ----

    private fun bucketS(ctx: Context, s: Scene): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_b_s)
        applyBase(rv, ctx, s, R.id.w_title, R.id.w_date, R.id.w_next)
        rv.setTextViewText(R.id.w_title, s.title)
        rv.setTextViewText(R.id.w_date, s.date)
        val line = when {
            s.nextText != null -> s.nextText
            s.noSnap -> "打开APP选择班级后展示今日课程"
            else -> s.bottomText ?: s.emptyTitle.orEmpty()
        }
        rv.setTextViewText(R.id.w_next, line)
        rv.setViewVisibility(R.id.w_next, View.VISIBLE)
        return rv
    }

    // ---- M 档 (≈2×2 / 3×2): 标题 + 下节课 + 固定药丸 / 空态居中 + 底部行 ----

    private fun bucketM(ctx: Context, s: Scene): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_b_m)
        applyBase(
            rv, ctx, s,
            R.id.w_title, R.id.w_date, R.id.w_next, R.id.w_more,
            R.id.w_empty_title, R.id.w_empty_sub, R.id.w_bottom_next,
        )
        rv.setTextViewText(R.id.w_title, s.title)
        rv.setTextViewText(R.id.w_date, s.date)
        val glassOn = s.glass != null
        if (s.emptyTitle != null) {
            rv.setViewVisibility(R.id.w_next, View.GONE)
            rv.setViewVisibility(R.id.w_rows, View.GONE)
            rv.setViewVisibility(R.id.w_more, View.GONE)
            rv.setViewVisibility(R.id.w_empty_box, View.VISIBLE)
            rv.setTextViewText(R.id.w_empty_title, s.emptyTitle)
            rv.setTextViewText(R.id.w_empty_sub, s.emptySub)
        } else {
            rv.setViewVisibility(R.id.w_empty_box, View.GONE)
            s.nextText?.let {
                rv.setViewVisibility(R.id.w_next, View.VISIBLE)
                rv.setTextViewText(R.id.w_next, it)
            } ?: rv.setViewVisibility(R.id.w_next, View.GONE)
            rv.removeAllViews(R.id.w_rows)
            val ongoingSet = ongoingIds(s)
            s.today.take(3).forEach { e ->
                rv.addView(R.id.w_rows, pill(ctx, s, e, e in ongoingSet, glassOn))
            }
            if (s.today.size > 3) {
                rv.setViewVisibility(R.id.w_more, View.VISIBLE)
                rv.setTextViewText(R.id.w_more, "还有 ${s.today.size - 3} 门课, 拉大查看全部")
            } else {
                rv.setViewVisibility(R.id.w_more, View.GONE)
            }
        }
        // 底行: 今天没剩课但后续有课才出现 (空态/全天结束)
        if (s.nextText == null && s.bottomText != null) {
            rv.setViewVisibility(R.id.w_bottom_next, View.VISIBLE)
            rv.setTextViewText(R.id.w_bottom_next, s.bottomText)
        } else {
            rv.setViewVisibility(R.id.w_bottom_next, View.GONE)
        }
        return rv
    }

    // ---- L/XL 档 (≈3×3+): 标题 + 下节课 + 可滚动议程(今天+后续按天分组) / 空态居中 + 底部行 ----

    private fun bucketL(ctx: Context, s: Scene): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_b_l)
        applyBase(
            rv, ctx, s,
            R.id.w_title, R.id.w_date, R.id.w_next,
            R.id.w_empty_title, R.id.w_empty_sub, R.id.w_bottom_next, R.id.w_agenda_head,
        )
        rv.setTextViewText(R.id.w_title, s.title)
        rv.setTextViewText(R.id.w_date, s.date)
        val glassOn = s.glass != null
        if (s.emptyTitle != null) {
            rv.setViewVisibility(R.id.w_next, View.GONE)
            rv.setViewVisibility(R.id.w_agenda, View.GONE)
            rv.setViewVisibility(R.id.w_empty_box, View.VISIBLE)
            rv.setTextViewText(R.id.w_empty_title, s.emptyTitle)
            rv.setTextViewText(R.id.w_empty_sub, s.emptySub)
        } else {
            rv.setViewVisibility(R.id.w_empty_box, View.GONE)
            s.nextText?.let {
                rv.setViewVisibility(R.id.w_next, View.VISIBLE)
                rv.setTextViewText(R.id.w_next, it)
            } ?: rv.setViewVisibility(R.id.w_next, View.GONE)
            // 议程: 今天 + 未来 7 天内有课日, 按天分组; RemoteCollectionItems 直传免 Service
            val items = RemoteViews.RemoteCollectionItems.Builder()
            var key = 0L
            val ongoingSet = ongoingIds(s)
            s.groups.forEach { g ->
                val head = RemoteViews(ctx.packageName, R.layout.widget_agenda_header)
                head.setTextViewText(R.id.w_agenda_head, g.label)
                items.addItem(key++, head)
                val isToday = g.date == KbClock.today()
                g.entries.forEach { e ->
                    items.addItem(key++, pill(ctx, s, e, isToday && e in ongoingSet, glassOn))
                }
            }
            rv.setViewVisibility(R.id.w_agenda, View.VISIBLE)
            rv.setRemoteAdapter(
                R.id.w_agenda,
                items.setViewTypeCount(2).build(),
            )
        }
        if (s.nextText == null && s.bottomText != null) {
            rv.setViewVisibility(R.id.w_bottom_next, View.VISIBLE)
            rv.setTextViewText(R.id.w_bottom_next, s.bottomText)
        } else {
            rv.setViewVisibility(R.id.w_bottom_next, View.GONE)
        }
        return rv
    }

    private fun ongoingIds(s: Scene): Set<Agenda.Entry> {
        val now = LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        return s.today.filter { nowMin >= it.startMin && nowMin < it.endMin }.toSet()
    }

    /** 单条课程药丸: 玻璃档用 alpha 渐变 drawable + 课程色半透明 tint (顶部微亮), 否则实色药丸 */
    private fun pill(
        ctx: Context,
        s: Scene,
        e: Agenda.Entry,
        ongoing: Boolean,
        glassOn: Boolean,
    ): RemoteViews {
        val item = RemoteViews(ctx.packageName, R.layout.widget_item)
        val seed = CourseColors.seedOf(e.block.course.kc, e.block.course.fx)
        val (container, on) = CourseColors.pair(s.scheme, seed, s.colors[seed])
        item.setInt(
            R.id.w_item, "setBackgroundResource",
            if (glassOn) R.drawable.widget_pill_glass else if (s.dark) PILL_D[0] else PILL[0],
        )
        val tint = if (glassOn) {
            // 玻璃药丸: 课程色乘 ~0.8 透明度, 底下是大玻璃卡 —— 顶部微亮渐变来自 drawable 的 alpha 梯度
            ColorUtils.setAlphaComponent(container.toArgb(), (if (s.dark) 0.75f else 0.85f).let { (it * 255).toInt() })
        } else container.toArgb()
        item.setColorStateList(
            R.id.w_item, "setBackgroundTintList",
            android.content.res.ColorStateList.valueOf(tint),
        )
        item.setTextColor(R.id.w_name, on.toArgb())
        val onSec = ColorUtils.setAlphaComponent(on.toArgb(), 214)
        item.setTextColor(R.id.w_meta, onSec)
        item.setTextColor(R.id.w_time, onSec)
        item.setTextViewText(R.id.w_name, e.block.course.kc)
        val jcs = "第${e.block.startJc}-${e.block.startJc + e.block.span - 1}节"
        val room = e.block.course.room.ifBlank { null } ?: e.block.rooms.ifBlank { null }
        item.setTextViewText(
            R.id.w_meta,
            when {
                ongoing && room != null -> "进行中 · $room"
                ongoing -> "进行中 · $jcs"
                room != null -> "$jcs · $room"
                else -> jcs
            },
        )
        if (e.tStart.isNotBlank() && e.tEnd.isNotBlank()) {
            item.setTextViewText(R.id.w_time, "${e.tStart}\n${e.tEnd}")
        } else {
            item.setViewVisibility(R.id.w_time, View.GONE)
        }
        return item
    }

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

    /** APP 侧数据/班级/外观变化后调用; 未添加小组件时无操作 */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
        if (ids.isEmpty()) return
        renderExec.execute { render(ctx, mgr, ids) }
    }
}
