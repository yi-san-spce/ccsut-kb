package com.ccsut.kb

import android.content.Context
import org.json.JSONObject

/** 统一本地偏好 ("kb" SharedPreferences) */
object Prefs {
    private const val SP = "kb"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun bjid(ctx: Context): String? = sp(ctx).getString("bjid", null)

    fun setBjid(ctx: Context, id: String) {
        sp(ctx).edit().putString("bjid", id).apply()
    }

    /** 0=跟随系统 1=浅色 2=深色 */
    fun themeMode(ctx: Context): Int = sp(ctx).getInt("theme_mode", 0)

    fun setThemeMode(ctx: Context, mode: Int) {
        sp(ctx).edit().putInt("theme_mode", mode).apply()
    }

    // ---- 课程自定义颜色 ----

    /** key = 课程种子(kc|fx), value = 色板下标 */
    fun courseColors(ctx: Context): Map<String, Int> {
        val raw = sp(ctx).getString("course_colors", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap { for (k in o.keys()) put(k, o.getInt(k)) }
        }.getOrDefault(emptyMap())
    }

    /** idx 传 null 表示恢复自动配色 */
    fun setCourseColor(ctx: Context, key: String, idx: Int?) {
        val map = courseColors(ctx).toMutableMap()
        if (idx == null) map.remove(key) else map[key] = idx
        val raw = JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()
        sp(ctx).edit().putString("course_colors", raw).apply()
    }

    // ---- 课表背景图 ----

    fun bgOn(ctx: Context): Boolean = sp(ctx).getBoolean("bg_on", false)

    fun setBgOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("bg_on", v).apply()
    }

    /** 背景不透明度, 10-100 (%) */
    fun bgAlpha(ctx: Context): Int = sp(ctx).getInt("bg_alpha", 45)

    fun setBgAlpha(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("bg_alpha", v.coerceIn(10, 100)).apply()
    }

    // ---- 主题配色来源 ----

    /** 0=跟随系统壁纸 1=跟随背景图取色 2=固定品牌色 */
    fun colorSource(ctx: Context): Int = sp(ctx).getInt("color_source", 0)

    fun setColorSource(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("color_source", v.coerceIn(0, 2)).apply()
    }

    /** 从背景图提取的种子色 (ARGB), 0=还没提取过 */
    fun bgSeed(ctx: Context): Int = sp(ctx).getInt("bg_seed", 0)

    fun setBgSeed(ctx: Context, argb: Int) {
        sp(ctx).edit().putInt("bg_seed", argb).apply()
    }

    // ---- 主题风格包 ----

    /** 0=默认(动态取色/品牌) 1..N=ThemePacks 固定主题, 选中后接管 colorSource */
    fun styleTheme(ctx: Context): Int = sp(ctx).getInt("style_theme", 0)

    fun setStyleTheme(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("style_theme", v).apply()
    }

    // ---- 课前提醒 ----

    fun reminderOn(ctx: Context): Boolean = sp(ctx).getBoolean("reminder_on", false)

    fun setReminderOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("reminder_on", v).apply()
    }

    /** 提前量(分钟) */
    fun reminderLead(ctx: Context): Int = sp(ctx).getInt("reminder_lead", 15)

    fun setReminderLead(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("reminder_lead", v).apply()
    }

    /** 下课后小结提醒(最后一节下课时)。默认关: 没碰过设置的用户不默认收通知/挂保活 */
    fun afterClassOn(ctx: Context): Boolean = sp(ctx).getBoolean("after_class_on", false)

    fun setAfterClassOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("after_class_on", v).apply()
    }

    /** 早八前夜提醒(前一晚 21:30)。默认关: 同上 */
    fun earlyOn(ctx: Context): Boolean = sp(ctx).getBoolean("early_on", false)

    fun setEarlyOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("early_on", v).apply()
    }

    /** 下次提醒的时间戳(毫秒), 0=未安排 */
    fun nextReminderAt(ctx: Context): Long = sp(ctx).getLong("next_reminder_at", 0L)

    fun setNextReminderAt(ctx: Context, at: Long) {
        sp(ctx).edit().putLong("next_reminder_at", at).apply()
    }

    /** 最近一次提醒触发器完成投递尝试的时间戳(毫秒): 后台兜底用它区分「链路完好但账面未前移」
     *  与「真断链被吞」—— nextReminderAt 在正常触发后要到下次 reschedule 才更新, 不能直接比 */
    fun lastFiredAt(ctx: Context): Long = sp(ctx).getLong("last_fired_at", 0L)

    fun setLastFiredAt(ctx: Context, at: Long) {
        sp(ctx).edit().putLong("last_fired_at", at).apply()
    }

    /** 通知丢弃计数(诊断): 权限缺失/已上课丢弃等, 开发者面板可见 */
    fun notifDrops(ctx: Context): Int = sp(ctx).getInt("notif_drops", 0)

    fun incNotifDrops(ctx: Context, n: Int = 1) {
        sp(ctx).edit().putInt("notif_drops", notifDrops(ctx) + n).apply()
    }

    // ---- 自定义外观 ----

    // 旧键存的是"不透明度"(10-100, 100=不透明), 与滑杆"透明度"文案语义相反, 已被新键替代
    private const val KEY_TODAY_BAR_ALPHA_LEGACY = "today_bar_alpha"
    private const val KEY_TODAY_BAR_TRANSPARENCY = "today_bar_transparency"

    /** 「今天」状态条透明度百分比 (0-100): 0=完全不透明, 100=纯文字(无底/无磨砂/无阴影); 「回到本周」药丸同族跟随 */
    fun todayBarTransparency(ctx: Context): Int {
        val sp = sp(ctx)
        if (sp.contains(KEY_TODAY_BAR_TRANSPARENCY)) return sp.getInt(KEY_TODAY_BAR_TRANSPARENCY, 0)
        // 首次读到新键: 从旧不透明度翻转迁移, 老用户视觉不变; 旧键随即删除
        val migrated = migrateLegacyTodayBarAlpha(
            if (sp.contains(KEY_TODAY_BAR_ALPHA_LEGACY)) sp.getInt(KEY_TODAY_BAR_ALPHA_LEGACY, 100) else null,
        )
        sp.edit().putInt(KEY_TODAY_BAR_TRANSPARENCY, migrated).remove(KEY_TODAY_BAR_ALPHA_LEGACY).apply()
        return migrated
    }

    fun setTodayBarTransparency(ctx: Context, v: Int) {
        sp(ctx).edit().putInt(KEY_TODAY_BAR_TRANSPARENCY, v.coerceIn(0, 100)).apply()
    }

    /** 顶栏透明度百分比 (0-100): 0=毛玻璃(现状), 100=纯文字+玻璃字; 仅设置背景图后生效 */
    fun topbarTransparency(ctx: Context): Int = sp(ctx).getInt("topbar_transparency", 0)

    fun setTopbarTransparency(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("topbar_transparency", v.coerceIn(0, 100)).apply()
    }

    // ---- 小组件外观 ----

    /** 小组件玻璃强度 (0-100): 磨砂快照上的染色不透明度; 0 = 关玻璃回实色卡片 */
    fun widgetGlass(ctx: Context): Int = sp(ctx).getInt("widget_glass", 55)

    fun setWidgetGlass(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("widget_glass", v.coerceIn(0, 100)).apply()
    }

    /** 旧不透明度 → 新透明度的翻转: 100(不透明)→0, 10→90; 未存过(null, 旧默认=不透明)→0 */
    fun migrateLegacyTodayBarAlpha(legacy: Int?): Int =
        legacy?.let { (100 - it).coerceIn(0, 100) } ?: 0

    // ---- 课程颜色注册表 (删除/恢复课程不重排其余课程颜色) ----

    private const val SEED_SEP = "\u0001"   // 课程名不会出现的控制符

    /** 课程种子 → 颜色槽位的分配顺序 (持久化, 只增不减) */
    fun courseSeedOrder(ctx: Context): List<String> =
        sp(ctx).getString("course_seed_order", null)
            ?.split(SEED_SEP)?.filter { it.isNotBlank() }
            ?: emptyList()

    fun setCourseSeedOrder(ctx: Context, order: List<String>) {
        sp(ctx).edit().putString("course_seed_order", order.joinToString(SEED_SEP)).apply()
    }

    // ---- 功能演示 (首次触发去重) ----

    private const val DEMO_SEP = ","

    /** 已看过的演示 id 集合 */
    fun demoSeen(ctx: Context): Set<String> =
        sp(ctx).getString("demo_seen", null)?.split(DEMO_SEP)?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    fun markDemoShown(ctx: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = demoSeen(ctx) + ids
        sp(ctx).edit().putString("demo_seen", merged.joinToString(DEMO_SEP)).apply()
    }

    /** 进表后的完整演示组是否已放过 (只放一次) */
    fun demoIntroDone(ctx: Context): Boolean = sp(ctx).getBoolean("demo_intro_done", false)

    fun setDemoIntroDone(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("demo_intro_done", v).apply()
    }

    // ---- 欢迎引导 ----

    /** 用户昵称(选填, 姓名/昵称/空) */
    fun nickname(ctx: Context): String = sp(ctx).getString("nickname", "") ?: ""

    fun setNickname(ctx: Context, v: String) {
        sp(ctx).edit().putString("nickname", v.trim()).apply()
    }

    /** 欢迎引导是否已完成/跳过 (true 后不再弹) */
    fun onboardingDone(ctx: Context): Boolean = sp(ctx).getBoolean("onboarding_done", false)

    fun setOnboardingDone(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("onboarding_done", v).apply()
    }

    // ---- 个人课表 ----

    /** 当前生效课表: "class"=班级课表 "personal"=个人课表(教务登录) */
    fun activeTimetable(ctx: Context): String = sp(ctx).getString("active_timetable", "class") ?: "class"

    fun setActiveTimetable(ctx: Context, v: String) {
        sp(ctx).edit().putString("active_timetable", v).apply()
    }

    /** 教务登录账号 (学号/手机号), 仅用于下次预填 */
    fun casAccount(ctx: Context): String = sp(ctx).getString("cas_account", "") ?: ""

    fun setCasAccount(ctx: Context, v: String) {
        sp(ctx).edit().putString("cas_account", v.trim()).apply()
    }

    /** 教务里的学生姓名 */
    fun studentName(ctx: Context): String = sp(ctx).getString("student_name", "") ?: ""

    fun setStudentName(ctx: Context, v: String) {
        sp(ctx).edit().putString("student_name", v.trim()).apply()
    }

    /** 上次同步个人课表的时间戳(毫秒), 0=从未同步 */
    fun lastSyncAt(ctx: Context): Long = sp(ctx).getLong("last_sync_at", 0L)

    fun setLastSyncAt(ctx: Context, v: Long) {
        sp(ctx).edit().putLong("last_sync_at", v).apply()
    }

    // ---- 开发者模式 ----

    fun devMode(ctx: Context): Boolean = sp(ctx).getBoolean("dev_mode", false)

    fun setDevMode(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("dev_mode", v).apply()
    }

    /** 时间旅行偏移天数(0=真实今天), 日期整体平移、时刻不变 */
    fun dbgOffsetDays(ctx: Context): Int = sp(ctx).getInt("dbg_offset_days", 0)

    fun setDbgOffsetDays(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("dbg_offset_days", v.coerceIn(-30, 60)).apply()
    }

    /** 重置个性化: 课程颜色/背景/主题, 保留班级、提醒与数据 */
    fun clearPersonal(ctx: Context) {
        sp(ctx).edit()
            .remove("course_colors")
            .remove("nickname")
            .putBoolean("bg_on", false)
            .putInt("theme_mode", 0)
            .putInt("color_source", 0)
            .putInt("style_theme", 0)
            .remove("bg_seed")
            .apply()
    }

    /** 完全重置: 清空全部键 (调用方自行恢复想保留的键, 如 dev_mode) */
    fun clearAll(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }

    /** 更新提醒 (设置页开关, 默认开; 明确关过的用户保持关): 启动时自动检查新版本并弹窗提醒 */
    fun autoUpdateOn(ctx: Context): Boolean = sp(ctx).getBoolean("auto_update_on", true)

    fun setAutoUpdateOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("auto_update_on", v).apply()
    }

    /** 更新提醒里用户点「取消」跳过的版本名: 该版本不再弹, 出现更高版本时重新提醒 */
    fun updateDismissedVersion(ctx: Context): String = sp(ctx).getString("update_dismissed_version", "") ?: ""

    fun setUpdateDismissedVersion(ctx: Context, v: String) {
        sp(ctx).edit().putString("update_dismissed_version", v).apply()
    }

    // ---- 远程通知 ----

    /** 已读的远程通知 id → 已读时间戳 (once 类通知只弹一次); 读写失败按空表处理 */
    fun noticeSeen(ctx: Context): Map<String, Long> {
        val raw = sp(ctx).getString("notice_seen", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap { for (k in o.keys()) put(k, o.getLong(k)) }
        }.getOrDefault(emptyMap())
    }

    fun setNoticeSeen(ctx: Context, map: Map<String, Long>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        sp(ctx).edit().putString("notice_seen", o.toString()).apply()
    }

    // ---- 后台消息通道 (BackgroundSync) ----

    private const val BG_SEP = ","

    /** 后台消息通道已发过通知的远程通知 id 集合: 与弹窗已读(noticeSeen)独立,
     *  保证每条 id 在后台只提醒一次, 弹窗「每次都弹」(once=false) 的也不会被后台反复骚扰 */
    fun bgNotifiedIds(ctx: Context): Set<String> =
        sp(ctx).getString("bg_notified_ids", null)?.split(BG_SEP)?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()

    fun markBgNotified(ctx: Context, id: String) {
        val merged = (bgNotifiedIds(ctx) + id).toList().takeLast(64)
        sp(ctx).edit().putString("bg_notified_ids", merged.joinToString(BG_SEP)).apply()
    }

    /** 后台消息通道已提醒过的课表数据版本 (清单版本, 0=未提醒过) */
    fun bgDataNotifiedVersion(ctx: Context): Int = sp(ctx).getInt("bg_data_notified_version", 0)

    fun setBgDataNotifiedVersion(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("bg_data_notified_version", v).apply()
    }

    /** 开发者: 无视版本关系, 更新页任意版本均可安装 (DevSheet 开关, 默认关) */
    fun devIgnoreVersion(ctx: Context): Boolean = sp(ctx).getBoolean("dev_ignore_version", false)

    fun setDevIgnoreVersion(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("dev_ignore_version", v).apply()
    }
}
