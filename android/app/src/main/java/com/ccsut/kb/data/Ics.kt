package com.ccsut.kb.data

import com.ccsut.kb.util.Block
import com.ccsut.kb.util.Merger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 课表 → RFC 5545 iCalendar (.ics) 文本, 给 iPhone (Apple 日历) / Google 日历等导入用。
 * 纯函数无 Android 依赖, 单测直接覆盖。
 *
 * 合并语义与渲染层一致: 逐周跑 [Merger.blocksOf] 得到「某周某天某大节」的课块,
 * 再把同Identity(星期+起始节+跨度+课程要素)的块按连续周折叠成一条 VEVENT + RRULE。
 * 因此 App 里怎么显示, 日历里就怎么排 (含 UserEdits 覆盖层——调用方传生效视图即可)。
 *
 * 已知限制: 单周调休(如周日补上周一的课)是周循环表达不了的, 事件落在课表标注的星期上;
 * 与市面同类课表产品的 ICS 导出一致, 以学校校历为准。
 */
object Ics {

    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val timeFmt = DateTimeFormatter.ofPattern("HHmmss")

    /** 生成 ICS 的输入: 课表内容 + 周次作息, 与具体数据源(班级/个人)解耦 */
    data class Source(
        val title: String,          // 日历名 (X-WR-CALNAME), 如 "张三的课表"
        val courses: List<Course>,  // 生效视图 (已含本地修改)
        val periods: List<Period>,  // 节次 → "H:mm"
        val startDate: String,      // 第 1 周周一, yyyy-MM-dd
        val weeks: Int,             // 总周数
        val uidSeed: String,        // UID 前缀 (学期等, 不放账号——文件会被转发)
    )

    /** 生成完整 ICS 文本; 无任何可导出事件时返回 null */
    fun build(s: Source): String? {
        val start = runCatching { LocalDate.parse(s.startDate) }.getOrNull() ?: return null
        val events = mutableListOf<String>()

        // 逐周展开课块 → 按 Identity 聚合 → 连续周折叠
        data class Occur(val week: Int, val start: LocalDateTime, val end: LocalDateTime, val block: Block)
        val byKey = LinkedHashMap<String, MutableList<Occur>>()
        for (w in 1..s.weeks.coerceAtLeast(0)) {
            for (b in Merger.blocksOf(s.courses, w)) {
                val st = timeOf(s.periods, b.startJc, later = false) ?: continue
                val en = timeOf(s.periods, b.startJc + b.span - 1, later = true) ?: continue
                val date = start.plusDays((w - 1) * 7L + (b.course.day - 1L))
                val key = listOf(b.course.day, b.startJc, b.span, b.course.kc, b.course.teacher, b.course.room, b.course.fx, b.course.jxb)
                    .joinToString("\u0001")
                byKey.getOrPut(key) { mutableListOf() } += Occur(w, LocalDateTime.of(date, st), LocalDateTime.of(date, en), b)
            }
        }
        for ((_, ocs) in byKey) {
            // 按周排序后折叠连续段 (缺周 = 放假/调休停课, 拆成两条 VEVENT)
            val sorted = ocs.sortedBy { it.week }
            var run = mutableListOf(sorted.first())
            val runs = mutableListOf<List<Occur>>()
            for (o in sorted.drop(1)) {
                if (o.week == run.last().week + 1) run += o else { runs += run; run = mutableListOf(o) }
            }
            runs += run
            for (r in runs) {
                val first = r.first()
                val b = first.block
                val desc = buildString {
                    if (b.course.teacher.isNotBlank()) append("教师: ${b.course.teacher}\n")
                    if (b.course.jxb.isNotBlank()) append("教学班: ${b.course.jxb}\n")
                    if (b.course.fx.isNotBlank()) append("分项: ${b.course.fx}\n")
                    append("周次: ${b.course.zc}")
                }
                val uid = "${s.uidSeed}-d${b.course.day}j${b.startJc}w${first.week}@ccsut-kb"
                events += listOf(
                    "BEGIN:VEVENT",
                    "UID:$uid",
                    "DTSTAMP:${utcNow()}",
                    "DTSTART;TZID=Asia/Shanghai:${first.start.format(dateFmt)}T${first.start.format(timeFmt)}",
                    "DTEND;TZID=Asia/Shanghai:${first.end.format(dateFmt)}T${first.end.format(timeFmt)}",
                    "RRULE:FREQ=WEEKLY;COUNT=${r.size}",
                    "SUMMARY:${escape(summaryOf(b))}",
                    if (b.rooms.isNotBlank()) "LOCATION:${escape(b.rooms)}" else null,
                    "DESCRIPTION:${escape(desc)}",
                    "END:VEVENT",
                ).filterNotNull()
            }
        }
        if (events.isEmpty()) return null

        val head = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//ccsut-kb//长工课表通//CN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${escape(s.title)}",
            "X-WR-TIMEZONE:Asia/Shanghai",
        ) + vtimezoneAsiaShanghai()
        return (head + events + "END:VCALENDAR").joinToString("\r\n") + "\r\n"
    }

    /** 摘要: 课程名 + 体育分项 */
    private fun summaryOf(b: Block): String =
        if (b.course.fx.isNotBlank()) "${b.course.kc}(${b.course.fx})" else b.course.kc

    /** 第 jc 节的时刻; later=false 取 start, true 取 end; 脏时间返回 null */
    private fun timeOf(periods: List<Period>, jc: Int, later: Boolean): java.time.LocalTime? {
        val p = periods.firstOrNull { it.jc == jc } ?: return null
        val m = com.ccsut.kb.util.TimeParse.minutesOf(if (later) p.end else p.start) ?: return null
        return runCatching { java.time.LocalTime.of(m / 60, m % 60) }.getOrNull()
    }

    /** RFC 5545 文本转义: 反斜杠/分号/逗号/换行 */
    internal fun escape(t: String): String = t
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    private fun utcNow(): String =
        ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    /** 中国标准时间 (无夏令时), 精简 VTIMEZONE */
    private fun vtimezoneAsiaShanghai() = listOf(
        "BEGIN:VTIMEZONE",
        "TZID:Asia/Shanghai",
        "BEGIN:STANDARD",
        "DTSTART:19700101T000000",
        "TZOFFSETFROM:+0800",
        "TZOFFSETTO:+0800",
        "TZNAME:CST",
        "END:STANDARD",
        "END:VTIMEZONE",
    )
}
