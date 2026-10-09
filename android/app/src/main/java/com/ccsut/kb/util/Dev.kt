package com.ccsut.kb.util

import android.content.Context
import android.os.Build
import com.ccsut.kb.data.BuildVersion
import com.ccsut.kb.Prefs
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.PersonalRepo
import com.ccsut.kb.data.Repo
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 开发者模式工具集: 时间旅行 / 应用内日志 / 诊断报告。
 * 只在开发者模式 UI 中暴露, offset=0 时行为与真实时钟完全一致。
 */
object KbClock {

    /** 日期偏移天数, App 启动时从 Prefs 恢复 */
    var offsetDays: Int = 0

    fun init(ctx: Context) {
        offsetDays = Prefs.dbgOffsetDays(ctx)
    }

    /** 语义上的"今天"(受时间旅行影响) */
    fun today(): LocalDate = LocalDate.now().plusDays(offsetDays.toLong())

    /** 语义上的"现在"(只平移日期, 时刻保持真实 —— 晚上可测白天场景) */
    fun now(): LocalDateTime = LocalDateTime.now().plusDays(offsetDays.toLong())

    fun fmt(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))
}

/** 应用内环形日志: 开发者模式查看 + 诊断报告导出 */
object DebugLog {

    private const val CAP = 300
    private val buf = ArrayDeque<String>()
    private val ts = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    fun log(tag: String, msg: String) {
        val line = "${ts.format(java.time.LocalDateTime.now())} [$tag] $msg"
        synchronized(buf) {
            buf.addLast(line)
            while (buf.size > CAP) buf.removeFirst()
        }
        android.util.Log.i("kb-dev", "[$tag] $msg")
    }

    fun dump(): String = synchronized(buf) { buf.joinToString("\n") }

    fun clear() = synchronized(buf) { buf.clear() }
}

/** 诊断报告: 远程排障时让同学一键分享的全部现场信息 */
object Diag {

    fun build(ctx: Context): String = buildString {
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        appendLine("== 长工课表通 诊断报告 ==")
        appendLine("生成时间: ${df.format(java.time.LocalDateTime.now())}")
        appendLine()
        appendLine("-- 设备/版本 --")
        appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("APP: v${BuildVersion.NAME} (${BuildVersion.CODE})")
        appendLine()
        appendLine("-- 时间旅行 --")
        appendLine("偏移: ${KbClock.offsetDays} 天 (语义今天 = ${KbClock.today()})")
        appendLine()
        appendLine("-- 数据 --")
        Repo.dataset?.let { ds ->
            appendLine("数据版本: v${ds.version} · ${ds.xnxq}")
            appendLine("起始日: ${ds.startDate} · ${ds.weeks} 周 · ${ds.classes.size} 个班")
            appendLine("生成于: ${ds.generatedAt}")
            appendLine("来源: ${if (Repo.fromUpdate) "在线更新" else "内置"}")
        } ?: appendLine("数据未加载")
        appendLine()
        appendLine("-- 班级快照 --")
        ClassCache.load(ctx)?.let { s ->
            appendLine("班级: ${snapBjmcForDiag(s)} (${s.bjid})")
            appendLine("课程条目: ${s.courses.size} · 节次: ${s.periods.size}")
        } ?: appendLine("无快照(未选班级)")
        val f = ctx.getFileStreamPath("class_cache.json")
        if (f.exists()) appendLine("快照文件: ${f.length()} B, 写于 ${df.format(java.time.Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()))}")
        appendLine()
        appendLine("-- 提醒 --")
        appendLine("开关: ${Prefs.reminderOn(ctx)} · 提前 ${Prefs.reminderLead(ctx)} 分钟")
        appendLine("放学小结: ${Prefs.afterClassOn(ctx)} · 早八前夜: ${Prefs.earlyOn(ctx)}")
        val next = Prefs.nextReminderAt(ctx)
        appendLine(
            "下次闹钟: " + (if (next > 0) df.format(java.time.Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())) else "未安排"),
        )
        appendLine()
        appendLine("-- 其他设置 --")
        appendLine("主题模式: ${Prefs.themeMode(ctx)} · 背景: ${Prefs.bgOn(ctx)} (alpha ${Prefs.bgAlpha(ctx)})")
        appendLine("更新地址: ${com.ccsut.kb.data.Updater.manifestUrl(ctx)}")
        appendLine("自定义课程色: ${Prefs.courseColors(ctx).size} 门")
        appendLine()
        appendLine("-- 最近日志 --")
        appendLine(DebugLog.dump().ifBlank { "(空)" })
    }

    /**
     * 诊断报告可能被整段分享出去, 个人课表快照的班级名含学生实名 —— 打码为 "王××的个人课表"。
     * 班级课表名(公开教学安排)不打码。
     */
    private fun snapBjmcForDiag(s: ClassCache.Snapshot): String =
        if (s.bjid == PersonalRepo.PERSONAL_BJID) {
            val n = s.bjmc.removeSuffix("的个人课表")
            (if (n.isEmpty()) "" else n.first().toString()) + "××的个人课表"
        } else s.bjmc
}
