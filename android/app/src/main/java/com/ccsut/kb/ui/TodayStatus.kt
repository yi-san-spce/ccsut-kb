package com.ccsut.kb.ui

import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.TimeParse
import com.ccsut.kb.util.Weeks
import java.time.LocalDate

/**
 * 今日状态栏状态机: 根据当前时间/今天课块/明天第一节,
 * 算出「今天」横条要说的话。纯 Kotlin 无 Android 依赖;
 * 文案按「今年第几天」取模选取, 同一天内稳定不闪变。
 */
object TodayStatus {

    /** 晚间时段起点(21:00), 之后若明天有早八则优先显示睡前提醒 */
    private const val EVENING_FROM = 21 * 60

    /** 状态色调: 决定状态栏容器颜色 */
    enum class Tone { ACTIVE, CALM, NIGHT, JOY }

    data class Result(
        val title: String,
        val sub: String? = null,
        val tone: Tone = Tone.CALM,
    )

    fun compute(
        blocks: List<Block>,
        periods: List<Period>,
        nowMin: Int,
        dayOfWeek: Int,
        tomorrowEarly: Block?,          // 明天第一节课(仅当 8:20 开头), 无则 null
        today: LocalDate = LocalDate.now(),
    ): Result {
        val pm = periods.associateBy { it.jc }
        // 脏作息时间(空串/带秒/非数字)由 TimeParse 防御为 null, 含脏时间的块按无时间块过滤掉
        fun startMin(b: Block) = pm[b.startJc]?.let { TimeParse.minutesOf(it.start) }
        fun endMin(b: Block) = pm[b.startJc + b.span - 1]?.let { TimeParse.minutesOf(it.end) }
        fun timeOf(b: Block) = pm[b.startJc]?.start ?: ""
        fun where(b: Block): String {
            val r = b.rooms.ifBlank { b.course.room }.trim()
            return if (r.isBlank()) "" else " @ $r"
        }

        val valid = blocks.filter { startMin(it) != null && endMin(it) != null }
        val current = valid.filter { startMin(it)!! <= nowMin && nowMin < endMin(it)!! }
            .minByOrNull { endMin(it)!! }
        val next = valid.filter { startMin(it)!! > nowMin }.minByOrNull { startMin(it)!! }
        val doneAny = valid.any { endMin(it)!! <= nowMin }
        val covered = buildSet {
            valid.forEach { b -> for (j in b.startJc until b.startJc + b.span) add(j) }
        }
        // 满课口径跟作息走: 晚间(21:00 起)之前的节全部有课才算
        val dayJcMax = pm.values
            .filter { (TimeParse.minutesOf(it.end) ?: 1440) <= EVENING_FROM }
            .maxOfOrNull { it.jc } ?: 8
        val fullDay = dayJcMax > 0 && (1..dayJcMax).all { it in covered }
        val evening = nowMin >= EVENING_FROM
        val total = valid.sumOf { it.span }

        return when {
            current != null -> {
                val left = endMin(current)!! - nowMin
                Result(
                    title = "正在上 ${current.course.kc}",
                    sub = "还剩 ${dur(left)}${where(current).replace(" @ ", " · ")}，加油加油",
                    tone = Tone.ACTIVE,
                )
            }
            next != null && startMin(next)!! - nowMin <= 45 -> {
                val gap = startMin(next)!! - nowMin
                Result(
                    title = "$gap 分钟后要上课啦",
                    sub = "${timeOf(next)} ${next.course.kc}${where(next)} · 记得带书呀",
                    tone = Tone.ACTIVE,
                )
            }
            next != null && doneAny -> Result(
                title = pick(between, today).format(dur(startMin(next)!! - nowMin)),
                sub = "下一节 ${timeOf(next)} ${next.course.kc}${where(next)}",
            )
            next != null -> if (fullDay) Result(
                title = pick(fullDayPool, today),
                sub = "共 $total 节课 · 第一节 ${timeOf(next)} ${next.course.kc}${where(next)}",
            ) else Result(
                title = "今天共 $total 节课",
                sub = "第一节 ${timeOf(next)} ${next.course.kc}${where(next)}",
            )
            evening && tomorrowEarly != null -> Result(
                title = pick(earlyNightPool, today),
                sub = "${timeOf(tomorrowEarly)} ${tomorrowEarly.course.kc}${where(tomorrowEarly)} · 晚安晚安",
                tone = Tone.NIGHT,
            )
            valid.isNotEmpty() -> if (dayOfWeek == 5) Result(
                title = pick(fridayPool, today), tone = Tone.JOY,
            ) else Result(
                title = pick(afterPool, today), tone = Tone.JOY,
            )
            dayOfWeek >= 6 -> Result(title = pick(freeWeekend, today), tone = Tone.JOY)
            else -> Result(title = pick(freeWeekday, today), tone = Tone.JOY)
        }
    }

    /**
     * 非本周横幅: 把目标周概括成一眼能扫完的要点。
     * 标题给体量 (总节数 / 几门课), 副行只报**偏离常态**的信息——最早开课、周末有课 (调休/补课)、
     * 工作日空闲、最忙的一天; 周末没课/工作日排满这类默认形态不说。不复述"第几周"(顶栏已有)。
     */
    fun weekSummary(blocks: List<Block>, periods: List<Period>): Result {
        if (blocks.isEmpty()) return Result(
            title = "整周没有课",
            sub = "假期愉快，翻回本周看安排",
            tone = Tone.CALM,
        )
        val pm = periods.associateBy { it.jc }
        val total = blocks.sumOf { it.span }
        val courses = blocks.map { "${it.course.kc}|${it.course.fx}" }.distinct().size
        val byDay = blocks.groupBy { it.course.day }
        val earliest = blocks.minByOrNull { it.startJc }
            ?.let { pm[it.startJc]?.start }?.takeIf { it.isNotBlank() }
        // 最忙的一天: 并列取最早的那天 (确定性, 不随数据顺序抖动)
        val busiest = byDay.entries
            .map { it.key to it.value.sumOf { b -> b.span } }
            .maxWithOrNull(compareBy({ it.second }, { -it.first }))
        val busyWeekdays = (1..5).filter { byDay.containsKey(it) }
        val weekendHas = byDay.containsKey(6) || byDay.containsKey(7)
        val weekendText = when {
            // 周末有课多半是调休/补课, 值得提醒; 周末没课是常态, 不说
            weekendHas && busyWeekdays.isEmpty() -> "只有周末有课"
            weekendHas -> "周末有课"
            else -> null
        }
        val freeWeekdays = (1..5).filter { !byDay.containsKey(it) }
        val freeText = when {
            weekendText == "只有周末有课" -> null   // 已由上一条表达, 不重复
            freeWeekdays.isEmpty() -> null          // 工作日排满是常态
            freeWeekdays.size <= 2 -> freeWeekdays.joinToString("、") { Weeks.cnDay(it) } + "没课"
            else -> "仅" + busyWeekdays.joinToString("、") { Weeks.cnDay(it) } + "有课"
        }
        val busiestText = busiest?.takeIf { byDay.size >= 3 }?.let { (day, n) -> "最忙${Weeks.cnDay(day)} $n 节" }
        val sub = listOfNotNull(
            earliest?.let { "最早 $it" },
            weekendText,
            freeText,
            busiestText,
        ).take(3).joinToString(" · ")
        return Result(
            title = "共 $total 节 · $courses 门课",
            sub = sub.ifBlank { null },
            tone = Tone.CALM,
        )
    }

    // ---- 文案池 (活人感版) ----

    private val between = listOf(
        "课间 %s，喝口水歇歇 (´▽`)",
        "趁课间歇口气～还有 %s",
        "还有 %s 才上课，走动走动呀",
    )
    private val fullDayPool = listOf(
        "服了，这臭课表，怎么安排的！(╯°□°）╯",
        "满课暴击哈哈哈，撑住，晚上就解放啦",
        "呜呜这一天课都排满了…记得按时吃饭呀",
    )
    private val earlyNightPool = listOf(
        "明天有早八哦，早点休息呀 (´-ω-`)",
        "明早的课别忘啦，今晚早点睡哦",
    )
    private val afterPool = listOf(
        "今天的课上完啦，辛苦啦 (๑•̀ㅂ•́)و✧",
        "下课咯～今天也很努力呢，哈哈",
        "今日课程清零！去玩吧去玩吧 ヾ(≧▽≦*)o",
    )
    private val fridayPool = listOf(
        "周末模式，启动！(๑•̀ㅂ•́)و✧",
        "周五收工！周末快乐呀 ヾ(≧▽≦*)o",
    )
    private val freeWeekday = listOf(
        "耶！今天没课，好好休息吧 (´▽`)ﾉ",
        "哈哈今天没课！想干嘛干嘛去啦～",
        "无课日！(๑˃̵ᴗ˂̵)و 把日子过成自己喜欢的样子",
    )
    private val freeWeekend = listOf(
        "周末没课！睡到自然醒的日子来啦～",
        "哈哈周末！好好犒劳一下自己呀 (´▽`)ﾉ",
    )

    private fun <T> pick(pool: List<T>, day: LocalDate): T = pool[(day.dayOfYear - 1) % pool.size]

    /** 分钟数 → 人话时长: 超过 1 小时改小时制 ("45 分钟" / "1 小时 25 分" / "2 小时") */
    private fun dur(mins: Int): String = when {
        mins < 60 -> "$mins 分钟"
        mins % 60 == 0 -> "${mins / 60} 小时"
        else -> "${mins / 60} 小时 ${mins % 60} 分"
    }
}
