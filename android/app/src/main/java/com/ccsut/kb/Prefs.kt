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

    // ---------------- 课程自定义颜色 ----------------

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

    // ---------------- 课表背景图 ----------------

    fun bgOn(ctx: Context): Boolean = sp(ctx).getBoolean("bg_on", false)

    fun setBgOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("bg_on", v).apply()
    }

    /** 背景不透明度, 10-100 (%) */
    fun bgAlpha(ctx: Context): Int = sp(ctx).getInt("bg_alpha", 45)

    fun setBgAlpha(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("bg_alpha", v.coerceIn(10, 100)).apply()
    }

    // ---------------- 主题配色来源 ----------------

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

    // ---------------- 课前提醒 ----------------

    fun reminderOn(ctx: Context): Boolean = sp(ctx).getBoolean("reminder_on", false)

    fun setReminderOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("reminder_on", v).apply()
    }

    /** 提前量(分钟) */
    fun reminderLead(ctx: Context): Int = sp(ctx).getInt("reminder_lead", 15)

    fun setReminderLead(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("reminder_lead", v).apply()
    }

    /** 下课后小结提醒(最后一节下课时) */
    fun afterClassOn(ctx: Context): Boolean = sp(ctx).getBoolean("after_class_on", true)

    fun setAfterClassOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("after_class_on", v).apply()
    }

    /** 早八前夜提醒(前一晚 21:30) */
    fun earlyOn(ctx: Context): Boolean = sp(ctx).getBoolean("early_on", true)

    fun setEarlyOn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("early_on", v).apply()
    }

    /** 下次提醒的时间戳(毫秒), 0=未安排 */
    fun nextReminderAt(ctx: Context): Long = sp(ctx).getLong("next_reminder_at", 0L)

    fun setNextReminderAt(ctx: Context, at: Long) {
        sp(ctx).edit().putLong("next_reminder_at", at).apply()
    }

    // ---------------- 欢迎引导 ----------------

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

    // ---------------- 开发者模式 ----------------

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
            .remove("bg_seed")
            .apply()
    }

    /** 完全重置: 清空全部键 (调用方自行恢复想保留的键, 如 dev_mode) */
    fun clearAll(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }
}
