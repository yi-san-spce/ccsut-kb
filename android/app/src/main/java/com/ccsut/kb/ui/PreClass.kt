package com.ccsut.kb.ui

import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.TimeParse
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
        fun startMin(b: Block) = pm[b.startJc]?.let { TimeParse.minutesOf(it.start) }
        fun endMin(b: Block) = pm[b.startJc + b.span - 1]?.let { TimeParse.minutesOf(it.end) }
        val valid = blocks.filter { startMin(it) != null && endMin(it) != null }

        val current = valid.filter { startMin(it)!! <= nowMin && nowMin < endMin(it)!! }
            .minByOrNull { endMin(it)!! }
        if (current != null) {
            return State(
                kind = Kind.CURRENT, block = current,
                startMin = startMin(current)!!, endMin = endMin(current)!!,
                mins = endMin(current)!! - nowMin, key = keyOf(current, today),
            )
        }

        val next = valid.filter { startMin(it)!! > nowMin }.minByOrNull { startMin(it)!! } ?: return null
        val gap = startMin(next)!! - nowMin
        if (gap > windowMin) return null
        return State(
            kind = Kind.BEFORE, block = next,
            startMin = startMin(next)!!, endMin = endMin(next)!!,
            mins = gap, key = keyOf(next, today),
        )
    }

    /** 去重键: 日期|星期|起始节|课程名|分项 */
    fun keyOf(b: Block, today: LocalDate): String =
        listOf(today, b.course.day, b.startJc, b.course.kc, b.course.fx).joinToString("|")
}
