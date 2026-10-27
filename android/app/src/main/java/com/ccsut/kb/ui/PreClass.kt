package com.ccsut.kb.ui

import com.ccsut.kb.data.Course
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.TimeParse
import com.ccsut.kb.util.Weeks
import java.time.LocalDate

/**
 * 课前全屏聚焦状态机: 判定「此刻打开聚焦页该展示什么」。
 * 纯 Kotlin 无 Android 依赖; 脏作息防御口径与 TodayStatus 一致(缺时间/脏时间的块按无时间跳过)。
 * 自动触发只认 BEFORE(距下节课 ≤ windowMin); 上课中(CURRENT)仅经 TodayBar 手动进入, 不自动打扰。
 */
object PreClass {

    enum class Kind { BEFORE, CURRENT }

    data class State(
        val kind: Kind,
        val block: Block,
        val startMin: Int,
        val endMin: Int,
        /** BEFORE = 距开课分钟数; CURRENT = 距下课分钟数 */
        val mins: Int,
        /** 去重键(手动关闭后本次不再自动弹) */
        val key: String,
    )

    /**
     * [blocks] 须是「今天」的课块([today] 只参与去重键, 保证每周同名课是独立实例)。
     * 上课中优先返回 CURRENT; 否则找下一节, 距开课 ≤ [windowMin] 才返回 BEFORE, 其余返回 null。
     */
    fun compute(
        blocks: List<Block>,
        periods: List<Period>,
        nowMin: Int,
        windowMin: Int,
        today: LocalDate = LocalDate.now(),
    ): State? {
        val pm = periods.associateBy { it.jc }
        val valid = blocks.filter { startMin(it, pm) != null && endMin(it, pm) != null }

        val current = valid.filter { startMin(it, pm)!! <= nowMin && nowMin < endMin(it, pm)!! }
            .minByOrNull { endMin(it, pm)!! }
        if (current != null) {
            return State(
                kind = Kind.CURRENT, block = current,
                startMin = startMin(current, pm)!!, endMin = endMin(current, pm)!!,
                mins = endMin(current, pm)!! - nowMin, key = keyOf(current, today),
            )
        }

        val next = valid.filter { startMin(it, pm)!! > nowMin }.minByOrNull { startMin(it, pm)!! } ?: return null
        val gap = startMin(next, pm)!! - nowMin
        if (gap > windowMin) return null
        return State(
            kind = Kind.BEFORE, block = next,
            startMin = startMin(next, pm)!!, endMin = endMin(next, pm)!!,
            mins = gap, key = keyOf(next, today),
        )
    }

    /**
     * 开发者预览: 无视触发窗口的聚焦目标 —— 上课中→CURRENT; 今天还有课→BEFORE(最近一节);
     * 今天上完了→未来 7 天内最近一节(分钟数含跨天)。全无课才返回 null。
     * 与 [compute] 同一套时刻换算, 仅去掉窗口闸门, 供「页面预览」随时看样式。
     */
    fun preview(
        courses: List<Course>,
        periods: List<Period>,
        nowMin: Int,
        today: LocalDate,
        startDate: String,
        weeks: Int,
    ): State? {
        val pm = periods.associateBy { it.jc }
        compute(dayBlocks(courses, startDate, weeks, today), periods, nowMin, 24 * 60, today)?.let { return it }
        for (k in 1..7) {
            val date = today.plusDays(k.toLong())
            val block = dayBlocks(courses, startDate, weeks, date).minByOrNull { it.startJc } ?: continue
            val s = startMin(block, pm) ?: continue
            val e = endMin(block, pm) ?: continue
            return State(
                kind = Kind.BEFORE, block = block,
                startMin = s, endMin = e,
                mins = k * 24 * 60 + s - nowMin,
                key = keyOf(block, date),
            )
        }
        return null
    }

    /** 去重键: 日期|星期|起始节|课程名|分项 */
    fun keyOf(b: Block, today: LocalDate): String =
        listOf(today, b.course.day, b.startJc, b.course.kc, b.course.fx).joinToString("|")

    /** 某一天(按学期周次)的课块 */
    private fun dayBlocks(courses: List<Course>, startDate: String, weeks: Int, date: LocalDate): List<Block> =
        Merger.blocksOf(courses, Weeks.currentWeek(startDate, weeks, date))
            .filter { it.course.day == date.dayOfWeek.value }

    private fun startMin(b: Block, pm: Map<Int, Period>): Int? =
        pm[b.startJc]?.let { TimeParse.minutesOf(it.start) }

    private fun endMin(b: Block, pm: Map<Int, Period>): Int? =
        pm[b.startJc + b.span - 1]?.let { TimeParse.minutesOf(it.end) }
}
