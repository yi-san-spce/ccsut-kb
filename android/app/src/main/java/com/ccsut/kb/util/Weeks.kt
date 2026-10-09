package com.ccsut.kb.util

import com.ccsut.kb.data.Course
import com.ccsut.kb.data.Dataset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Weeks {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 当前是第几周(1-based), 溢出夹到 [1, weeks] */
    fun currentWeek(d: Dataset, today: LocalDate = LocalDate.now()): Int =
        currentWeek(d.startDate, d.weeks, today)

    /** 同上, 直接给学期参数的重载 (供小组件/提醒使用的轻量快照) */
    fun currentWeek(startDate: String, weeks: Int, today: LocalDate): Int {
        val start = runCatching { LocalDate.parse(startDate, fmt) }.getOrNull()
            ?: return 1
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, today)
        // weeks.coerceAtLeast(1): 快照 JSON 显式写 0 时 coerceIn(1,0) 会抛 IllegalArgumentException
        return when {
            days < 0 -> 1
            else -> (days / 7 + 1).toInt().coerceIn(1, weeks.coerceAtLeast(1))
        }
    }

    /** 第 week 周 星期 day(1-7) 的日期 */
    fun dateOf(d: Dataset, week: Int, day: Int): LocalDate? =
        runCatching { LocalDate.parse(d.startDate, fmt) }
            .getOrNull()?.plusDays(((week - 1) * 7L + (day - 1)))

    fun md(date: LocalDate?): String = date?.let { "${it.monthValue}.${it.dayOfMonth}" } ?: ""

    fun cnDay(day: Int): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day - 1) { "" }
}

/** 合并连堂后的课块 */
data class Block(
    val course: Course,
    val startJc: Int,
    val span: Int,
    val rooms: String = "",     // 跨节合并后汇总的教室(最多显示2个)
    val teachers: String = "",
    /**
     * 拥有 [course.editId] 的那个 session 的节数(行数)。
     * 契约:
     * - 未融合的单行块: editSpan == span;
     * - 全由原始行(editId=null)组成的融合块: editSpan == span;
     * - 块头取到被修改行(editId!=null)的融合块: editSpan = 同 editId 的行数,
     *   editSpan < span 表示块里混入了其它 session 的行(可能未修改, 也可能属于另一条修改),
     *   还原/再拖时只作用于 editSpan 对应的那一段;
     * - 0 = 未指定(旧调用方/默认构造), 调用方按 span 处理。
     */
    val editSpan: Int = 0,
)

object Merger {

    private val key = { c: Course ->
        listOf(c.day, c.kc, c.teacher, c.room, c.fx, c.jxb, c.zc)
    }

    /**
     * 教务数据里同一 session 是"每节一行"(jc1..jcN 各一条, djs 不可靠),
     * 所以忽略 djs: 把同 key 的条目按连续节次串成块, 块高 = 行数。
     * 同一槽位若有两门不同课(真实排课冲突)则各自成块。
     */
    fun blocksOf(clsCourses: List<Course>, week: Int): List<Block> {
        val out = mutableListOf<Block>()
        for ((_, list) in clsCourses.filter { it.inWeek(week) }.groupBy(key)) {
            val jcs = list.map { it.jc }.distinct().sorted()
            var start = jcs.first()
            var prev = start
            fun flush(s: Int, e: Int) {
                val seg = list.filter { it.jc in s..e }
                val span = e - s + 1
                val rooms = seg.map { it.room }.filter { it.isNotBlank() }
                    .distinct().take(2).joinToString("/")
                val teachers = seg.map { it.teacher }.filter { it.isNotBlank() }
                    .distinct().take(2).joinToString("/")
                // 块头优先取被本地修改过的行: effective 视图里 edited 行排在原始行之后,
                // 融合块混有两者时 seg.first() 会取到原始行(editId=null), 详情页判不出
                // editKind 导致还原按钮消失; 取 edited 行后还原即拆开归位
                val course = seg.firstOrNull { it.editId != null } ?: seg.first()
                val editSpan = if (course.editId != null)
                    seg.count { it.editId == course.editId }.coerceIn(1, span)
                else span
                out += Block(course, s, span, rooms, teachers, editSpan)
            }
            for (i in 1 until jcs.size) {
                val jc = jcs[i]
                if (jc == prev + 1) {
                    prev = jc
                } else {
                    flush(start, prev)
                    start = jc
                    prev = jc
                }
            }
            flush(start, prev)
        }
        return out.sortedWith(compareBy({ it.course.day }, { it.startJc }))
    }
}
