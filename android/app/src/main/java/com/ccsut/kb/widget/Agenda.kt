package com.ccsut.kb.widget

import com.ccsut.kb.data.Course
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.Weeks
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 小组件议程纯数据层: 按天分组 / 下一次课扫描, 不依赖任何 Android API —— 渲染与单测共用。
 * 时刻解析独立于 ClassCache (避免单测触碰 Android 类)。
 */
object Agenda {

    /** 一条课程行: 课块 + 起止时刻 (periods 缺数据时文本为空串, 分钟取边界值) */
    data class Entry(
        val block: Block,
        val startMin: Int,
        val endMin: Int,
        val tStart: String,
        val tEnd: String,
    )

    /** 议程按天分组段: 小节头文案 + 该天全部课程行 */
    data class DayGroup(
        val date: LocalDate,
        val label: String,
        val entries: List<Entry>,
    )

    /** 某周某天的课程行 (合并连堂 + 起止时刻, 按开始时间排序) */
    fun entriesOfDay(courses: List<Course>, periods: List<Period>, week: Int, day: Int): List<Entry> {
        val byJc = periods.associateBy { it.jc }
        return Merger.blocksOf(courses, week)
            .filter { it.course.day == day }
            .map { b ->
                val s = byJc[b.startJc]
                val e = byJc[b.startJc + b.span - 1]
                Entry(
                    b,
                    s?.let { minutesOf(it.start) } ?: 0,
                    e?.let { minutesOf(it.end) } ?: 1440,
                    s?.start ?: "",
                    e?.end ?: "",
                )
            }
            .sortedWith(compareBy({ it.startMin }, { it.endMin }))
    }

    /**
     * 从 today 起最多扫描 [maxDays] 天 (含今天), 只保留有课的日子。
     * 小节头: 今天 / 明天 / 周X(M/d) + · N 节; 学期外截断。
     */
    fun groups(
        courses: List<Course>,
        periods: List<Period>,
        startDate: String,
        weeks: Int,
        today: LocalDate,
        maxDays: Int = 14,
    ): List<DayGroup> {
        val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return emptyList()
        val termDays = weeks.coerceAtLeast(1) * 7L
        val todayIdx = ChronoUnit.DAYS.between(start, today)
        val out = mutableListOf<DayGroup>()
        for (offset in 0 until maxDays) {
            val dayIdx = todayIdx + offset
            if (dayIdx < 0 || dayIdx >= termDays) break
            val date = today.plusDays(offset.toLong())
            val es = entriesOfDay(courses, periods, (dayIdx / 7 + 1).toInt(), date.dayOfWeek.value)
            if (es.isEmpty()) continue
            val head = when (offset) {
                0 -> "今天"
                1 -> "明天"
                else -> "${Weeks.cnDay(date.dayOfWeek.value)}(${date.monthValue}/${date.dayOfMonth})"
            }
            out += DayGroup(date, "$head · ${es.size} 节", es)
        }
        return out
    }

    /**
     * 下一次课: 从明天起找第一个有课日 (空态底行 / S 档单行)。
     * 今天无课不等于明天有课 —— 周末/假期由这里统一兜住; 学期内找不到 (假期/期末后) 返回 null。
     */
    fun nextClass(
        courses: List<Course>,
        periods: List<Period>,
        startDate: String,
        weeks: Int,
        today: LocalDate,
    ): DayGroup? {
        val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
        val termDays = weeks.coerceAtLeast(1) * 7L
        var date = today.plusDays(1)
        while (ChronoUnit.DAYS.between(start, date) < termDays) {
            val dayIdx = ChronoUnit.DAYS.between(start, date)
            val es = entriesOfDay(courses, periods, (dayIdx / 7 + 1).toInt(), date.dayOfWeek.value)
            if (es.isNotEmpty()) {
                val head = "${Weeks.cnDay(date.dayOfWeek.value)}(${date.monthValue}/${date.dayOfMonth})"
                return DayGroup(date, head, es)
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun minutesOf(s: String): Int? {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(s.trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }
}
