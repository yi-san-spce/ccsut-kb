package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.DebugLog
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 用户本地课表编辑 (filesDir/user_edits.json)。
 *
 * 设计: 用户的修改绝不写进 dataset, 而是存成独立的「覆盖层」,
 * 显示/小组件/提醒用的都是 [Repo.effectiveCls] 叠加后的「生效课表」。
 * 这样数据热更新(整体覆盖 dataset.json)后重新套用即可:
 * - 自建课程永远保留;
 * - 修改/隐藏按锚点(课程名+分项+星期+节次+周次)重新吸附到新课表,
 *   学校恰好动了这门课则安全丢弃并计数(见 [lastApplied]/[lastDropped]);
 * - 增量只存用户改过的字段([Delta]), 学校更新里用户没改的字段继续生效。
 */
object UserEdits {

    /** 自建课程哨兵 type, 区别于学校数据 (1=普通 9=体育分项占位) */
    const val TYPE_LOCAL = 100

    /** 锚点: 定位学校原始课块 (修改/隐藏用, 创建后不变, 始终指向原始行) */
    data class Match(
        val kc: String,
        val fx: String,
        val day: Int,
        val startJc: Int,
        val endJc: Int,
        val zc: String,
    )

    /** 增量: 只存用户改过的字段, null = 沿用学校值 */
    data class Delta(
        val kc: String? = null,
        val teacher: String? = null,
        val room: String? = null,
        val day: Int? = null,
        val startJc: Int? = null,
        val span: Int? = null,
        val zc: String? = null,
        val ranges: List<IntRange>? = null,
    )

    data class Edit(
        val id: String,
        val bjid: String,
        val kind: Int,              // 0=自建 1=修改 2=隐藏
        val week: Int? = null,      // null=对该课所有周生效; 非 null=仅该周(拖拽单周移动产生)
        val match: Match? = null,
        val delta: Delta? = null,
        val course: Course? = null, // 自建课(单条, djs=连堂数, 生效时展开成每节一行)
    )

    /** 课程编辑表单的值 (加课/改课共用) */
    data class Form(
        val kc: String,
        val teacher: String,
        val room: String,
        val day: Int,
        val startJc: Int,
        val span: Int,
        val zc: String,
        val ranges: List<IntRange>,
    )

    private const val FILE = "user_edits.json"
    private const val VERSION = 2
    private val lock = Any()
    // 单线程串行落盘: 同步改内存, 异步原子写文件 (tmp+rename, 崩溃不损坏)
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var edits: MutableList<Edit> = mutableListOf()
    private var loaded = false

    /** 最近一次 [effective] 的统计, 供数据更新完成的提示展示 */
    var lastApplied = 0
        private set
    var lastDropped = 0
        private set

    fun ensureLoaded(ctx: Context) {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val f = ctx.getFileStreamPath(FILE) ?: return
            if (!f.exists()) return
            val text = runCatching { f.readText() }.getOrNull() ?: return
            runCatching { parse(text) }
                .onSuccess { edits = it.toMutableList() }
                .onFailure {
                    // 文件损坏: 备份坏文件保留现场, 不静默清空用户数据
                    val bak = ctx.getFileStreamPath("$FILE.broken")
                    if (bak != null) {
                        bak.delete()
                        runCatching { f.renameTo(bak) }
                    }
                    DebugLog.log("edit", "本地修改文件损坏, 已备份为 $FILE.broken: ${it.message}")
                }
            DebugLog.log("edit", "载入本地修改 ${edits.size} 条")
        }
    }

    /** 原始行 + 覆盖层 → 生效课程列表 (重组时重算) */
    fun effective(bjid: String, pristine: List<Course>): List<Course> {
        val mine = synchronized(lock) { edits.filter { it.bjid == bjid } }
        if (mine.isEmpty()) {
            lastApplied = 0; lastDropped = 0
            return pristine
        }
        val rowEdits = mine.filter { it.kind != 0 }
        val used = LinkedHashMap<String, MutableList<Course>>()
        val out = ArrayList<Course>(pristine.size + 16)
        for (c in pristine) {
            val e = rowEdits.firstOrNull { x ->
                val m = x.match
                m != null && c.kc == m.kc && c.fx == m.fx && c.zc == m.zc &&
                    c.day == m.day && c.jc in m.startJc..m.endJc
            }
            if (e == null) out += c else used.getOrPut(e.id) { mutableListOf() } += c
        }
        var applied = 0
        var dropped = 0
        for (e in rowEdits) {
            val m = e.match ?: continue
            val d = e.delta
            val rows = used[e.id]
            if (rows.isNullOrEmpty()) {
                dropped++
                continue
            }
            applied++
            if (e.kind == 2) continue
            val span = (d?.span ?: (m.endJc - m.startJc + 1)).coerceAtLeast(1)
            val base = d?.startJc ?: m.startJc
            if (e.week != null) {
                // 单周修改(拖拽产生): 原始行扣除该周原样保留(其余周不动), 该周输出移动后的副本。
                // 两部分都沿用原始 zc 文本保持锚点/身份稳定, 详情页的周次以 ranges 渲染为准。
                val w = e.week
                for (src in rows) {
                    val rest = minusWeek(src.ranges, w)
                    if (rest.isNotEmpty()) out += src.copy(ranges = rest, editId = null)
                }
                for (i in 0 until span) {
                    val src = rows.getOrNull(i) ?: rows.last()
                    out += src.copy(
                        kc = d?.kc ?: src.kc,
                        teacher = d?.teacher ?: src.teacher,
                        room = d?.room ?: src.room,
                        day = d?.day ?: src.day,
                        jc = base + i,
                        ranges = listOf(w..w),
                        editId = e.id,
                    )
                }
                continue
            }
            for (i in 0 until span) {
                val src = rows.getOrNull(i) ?: rows.last()
                out += src.copy(
                    kc = d?.kc ?: src.kc,
                    teacher = d?.teacher ?: src.teacher,
                    room = d?.room ?: src.room,
                    day = d?.day ?: src.day,
                    jc = base + i,
                    zc = d?.zc ?: src.zc,
                    ranges = d?.ranges ?: src.ranges,
                    editId = e.id,
                )
            }
        }
        for (e in mine.filter { it.kind == 0 }) {
            val c = e.course ?: continue
            applied++
            val n = c.djs.coerceAtLeast(1)
            for (i in 0 until n) out += c.copy(jc = c.jc + i, djs = 1, editId = e.id)
        }
        lastApplied = applied
        lastDropped = dropped
        return out
    }

    fun editOf(id: String?): Edit? = synchronized(lock) {
        id?.let { i -> edits.firstOrNull { it.id == i } }
    }

    /** 添加自建课程 */
    fun add(ctx: Context, bjid: String, form: Form) {
        ensureLoaded(ctx)
        val c = Course(
            kc = form.kc, teacher = form.teacher, room = form.room,
            day = form.day, jc = form.startJc, djs = form.span,
            zc = form.zc, ranges = form.ranges,
            fx = "", jxb = "", jxbzc = "", type = TYPE_LOCAL,
        )
        synchronized(lock) { edits += Edit(id = newId(), bjid = bjid, kind = 0, course = c) }
        persist(ctx)
    }

    /**
     * 拖拽移动课块 (newDay/newStartJc 为落点, week=拖拽所在周)。
     * 语义: 只移动该周的这一节课, 其它周的原课不动 ——
     * 多周课会被拆成「其余周(原位) + 该周(新位置)」两条记录; 拖回学校原始位置 = 单周修改自动还原。
     */
    fun moveBlock(ctx: Context, bjid: String, block: Block, newDay: Int, newStartJc: Int, week: Int) {
        ensureLoaded(ctx)
        synchronized(lock) {
            val e = editOfLocked(block.course.editId)
            when (e?.kind) {
                0 -> {
                    // 自建课: 多周行拆出单周副本移走, 单周行直接移动
                    val c = e.course!!
                    val rest = minusWeek(c.ranges, week)
                    if (rest.size == c.ranges.size) {
                        replace(e, e.copy(course = c.copy(day = newDay, jc = newStartJc)))
                    } else if (rest.isEmpty()) {
                        replace(e, e.copy(course = c.copy(day = newDay, jc = newStartJc, ranges = listOf(week..week))))
                    } else {
                        replace(e, e.copy(course = c.copy(ranges = rest)))
                        edits += Edit(newId(), bjid, 0, course = c.copy(
                            day = newDay, jc = newStartJc, ranges = listOf(week..week)))
                    }
                }
                1 -> {
                    val m = e.match!!
                    if (e.week != null) {
                        // 已是单周修改: 更新该周位置; 拖回学校原始位置 = 还原(其余周本来就没动)
                        val d = (e.delta ?: Delta()).copy(
                            day = newDay.takeIf { it != m.day },
                            startJc = newStartJc.takeIf { it != m.startJc },
                        )
                        if (d == Delta()) edits.remove(e)
                        else replace(e, e.copy(delta = d))
                    } else {
                        // 全周修改再拖拽: 生效范围扣除该周留给原记录, 该周另开单周修改
                        val ref = Repo.dataset?.classes?.get(bjid)?.courses?.firstOrNull {
                            it.kc == m.kc && it.fx == m.fx && it.zc == m.zc && it.day == m.day && it.jc == m.startJc
                        }
                        val cur = e.delta?.ranges ?: ref?.ranges ?: block.course.ranges
                        val rest = minusWeek(cur, week)
                        if (rest.size == cur.size) {
                            // 该周不在生效范围(异常), 退回整行移动的旧语义
                            val d = (e.delta ?: Delta()).copy(
                                day = newDay.takeIf { it != m.day },
                                startJc = newStartJc.takeIf { it != m.startJc },
                            )
                            if (d == Delta()) edits.remove(e) else replace(e, e.copy(delta = d))
                        } else if (rest.isEmpty()) {
                            // 生效范围只有这一周: 整条转单周修改(丢弃周次增量, 位置/内容增量保留)
                            val d = (e.delta ?: Delta()).copy(
                                day = newDay.takeIf { it != m.day },
                                startJc = newStartJc.takeIf { it != m.startJc },
                                ranges = null, zc = null,
                            )
                            if (d == Delta()) edits.remove(e)
                            else replace(e, e.copy(week = week, delta = d))
                        } else {
                            replace(e, e.copy(delta = (e.delta ?: Delta()).copy(
                                ranges = rest, zc = e.delta?.zc ?: null)))
                            edits += Edit(newId(), bjid, 1, week = week, match = m, delta = Delta(
                                day = newDay.takeIf { it != m.day },
                                startJc = newStartJc.takeIf { it != m.startJc },
                            ))
                        }
                    }
                }
                else -> {
                    val m = anchorOf(block)
                    val d = Delta(
                        day = newDay.takeIf { it != m.day },
                        startJc = newStartJc.takeIf { it != m.startJc },
                    )
                    if (d.day != null || d.startJc != null)
                        edits += Edit(newId(), bjid, 1, week = week, match = m, delta = d)
                }
            }
        }
        persist(ctx)
    }

    /** 表单保存 (加课信息/位置/周次一并生效) */
    fun saveBlock(ctx: Context, bjid: String, block: Block, form: Form) {
        ensureLoaded(ctx)
        synchronized(lock) {
            val e = editOfLocked(block.course.editId)
            if (e?.kind == 0) {
                replace(e, e.copy(course = e.course!!.copy(
                    kc = form.kc, teacher = form.teacher, room = form.room,
                    day = form.day, jc = form.startJc, djs = form.span,
                    zc = form.zc, ranges = form.ranges)))
                persist(ctx)
                return
            }
            val m = e?.match ?: anchorOf(block)
            val d = diff(bjid, m, form)
            if (e != null) replace(e, e.copy(match = m, delta = d))
            else edits += Edit(newId(), bjid, 1, match = m, delta = d)
            persist(ctx)
        }
    }

    /** 删除课块: 自建课删记录; 学校课按原始锚点隐藏 (不影响他人) */
    fun removeBlock(ctx: Context, bjid: String, block: Block) {
        ensureLoaded(ctx)
        synchronized(lock) {
            val e = editOfLocked(block.course.editId)
            if (e?.kind == 2) return  // 已是隐藏记录, 无需重复
            if (e != null) edits.remove(e)
            if (e?.kind != 0) {
                edits += Edit(newId(), bjid, 2, match = e?.match ?: anchorOf(block))
            }
            persist(ctx)
        }
    }

    /** 还原一条修改记录 (回到学校原始数据); 自建课请走 [removeBlock] */
    fun revertBlock(ctx: Context, bjid: String, block: Block) {
        ensureLoaded(ctx)
        synchronized(lock) {
            val e = editOfLocked(block.course.editId) ?: return
            edits.remove(e)
            persist(ctx)
        }
    }

    /** 完全重置用: 清空全部本地修改 */
    fun clear(ctx: Context) {
        synchronized(lock) {
            loaded = true
            edits = mutableListOf()
        }
        ctx.deleteFile(FILE)
        io.execute { ctx.getFileStreamPath("$FILE.tmp")?.delete() }
        DebugLog.log("edit", "本地修改已清空")
    }

    /** 两组周次区间是否有交集 (整数区间首尾比较, O(1); 供拖拽/加课的落点占用判定共用) */
    fun rangesOverlap(a: List<IntRange>, b: List<IntRange>): Boolean =
        a.any { ra -> b.any { rb -> ra.first <= rb.last && rb.first <= ra.last } }

    /** 由勾选周次生成 ("1-4,6-12" 文本, 区间列表) */
    fun zcOf(sel: Set<Int>): Pair<String, List<IntRange>> {
        val ranges = mutableListOf<IntRange>()
        for (w in sel.sorted()) {
            val last = ranges.lastOrNull()
            if (last != null && last.last == w - 1) ranges[ranges.size - 1] = last.first..w
            else ranges += w..w
        }
        val text = ranges.joinToString(",") {
            if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}"
        }
        return text to ranges
    }

    // ---------- 内部 ----------

    /** 锁内查找 (调用方必须已持 [lock]) */
    private fun editOfLocked(id: String?): Edit? = id?.let { i -> edits.firstOrNull { it.id == i } }

    private fun anchorOf(b: Block) = Match(
        kc = b.course.kc, fx = b.course.fx, day = b.course.day,
        startJc = b.startJc, endJc = b.startJc + b.span - 1, zc = b.course.zc,
    )

    /** 表单值 vs 原始锚点行 → 增量 (用户没改的字段回 null, 让学校未来的新值继续生效) */
    private fun diff(bjid: String, m: Match, form: Form): Delta {
        val ref = Repo.dataset?.classes?.get(bjid)?.courses?.firstOrNull {
            it.kc == m.kc && it.fx == m.fx && it.zc == m.zc && it.day == m.day && it.jc == m.startJc
        }
        val span = m.endJc - m.startJc + 1
        return Delta(
            kc = form.kc.takeIf { it != m.kc },
            teacher = form.teacher.takeIf { ref == null || it != ref.teacher },
            room = form.room.takeIf { ref == null || it != ref.room },
            day = form.day.takeIf { it != m.day },
            startJc = form.startJc.takeIf { it != m.startJc },
            span = form.span.takeIf { it != span },
            zc = form.zc.takeIf { it != m.zc },
            ranges = form.ranges.takeIf { form.zc != m.zc },
        )
    }

    private fun replace(old: Edit, new: Edit) {
        val i = edits.indexOfFirst { it.id == old.id }
        if (i >= 0) edits[i] = new
    }

    private fun newId() = "e" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)

    /** 同步改内存 → 异步落盘: tmp+rename 原子写, 写一半崩溃不会损坏正式文件 */
    private fun persist(ctx: Context) {
        val snapshot = synchronized(lock) { edits.toList() }
        io.execute {
            runCatching {
                val arr = JSONArray()
                for (e in snapshot) arr.put(toJson(e))
                val text = JSONObject().put("v", VERSION).put("edits", arr).toString()
                val tmp = ctx.getFileStreamPath("$FILE.tmp") ?: return@execute
                tmp.writeText(text)
                val dst = ctx.getFileStreamPath(FILE) ?: return@execute
                if (!tmp.renameTo(dst)) {
                    dst.writeText(text)  // 同分区 rename 失败极少见, 兜底直写
                    tmp.delete()
                }
                DebugLog.log("edit", "本地修改已保存, 共 ${snapshot.size} 条")
            }.onFailure { DebugLog.log("edit", "本地修改保存失败: ${it.message}") }
        }
    }

    private fun toJson(e: Edit): JSONObject = JSONObject()
        .put("id", e.id).put("bjid", e.bjid).put("kind", e.kind)
        .apply {
            e.match?.let { m ->
                put("match", JSONObject()
                    .put("kc", m.kc).put("fx", m.fx).put("day", m.day)
                    .put("startJc", m.startJc).put("endJc", m.endJc).put("zc", m.zc))
            }
            e.delta?.let { d ->
                put("delta", JSONObject().apply {
                    d.kc?.let { put("kc", it) }
                    d.teacher?.let { put("teacher", it) }
                    d.room?.let { put("room", it) }
                    d.day?.let { put("day", it) }
                    d.startJc?.let { put("startJc", it) }
                    d.span?.let { put("span", it) }
                    d.zc?.let { put("zc", it) }
                    d.ranges?.let { rs ->
                        put("ranges", JSONArray().apply { rs.forEach { put(JSONArray().put(it.first).put(it.last)) } })
                    }
                })
            }
            e.course?.let { c ->
                put("course", JSONObject()
                    .put("kc", c.kc).put("teacher", c.teacher).put("room", c.room)
                    .put("day", c.day).put("jc", c.jc).put("djs", c.djs)
                    .put("zc", c.zc)
                    .put("ranges", JSONArray().apply { c.ranges.forEach { put(JSONArray().put(it.first).put(it.last)) } })
                    .put("fx", c.fx).put("jxb", c.jxb).put("jxbzc", c.jxbzc).put("type", c.type))
            }
        }

    private fun parse(text: String): List<Edit> {
        val arr = JSONObject(text).optJSONArray("edits") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            Edit(
                id = o.getString("id"), bjid = o.getString("bjid"), kind = o.optInt("kind", 0),
                match = o.optJSONObject("match")?.let { m ->
                    Match(
                        kc = m.getString("kc"), fx = m.optString("fx"), day = m.optInt("day", 1),
                        startJc = m.optInt("startJc", 1), endJc = m.optInt("endJc", 1), zc = m.getString("zc"),
                    )
                },
                delta = o.optJSONObject("delta")?.let { d ->
                    Delta(
                        kc = if (d.has("kc")) d.getString("kc") else null,
                        teacher = if (d.has("teacher")) d.getString("teacher") else null,
                        room = if (d.has("room")) d.getString("room") else null,
                        day = if (d.has("day")) d.optInt("day") else null,
                        startJc = if (d.has("startJc")) d.optInt("startJc") else null,
                        span = if (d.has("span")) d.optInt("span") else null,
                        zc = if (d.has("zc")) d.getString("zc") else null,
                        ranges = d.optJSONArray("ranges")?.let { rs ->
                            (0 until rs.length()).map { k ->
                                val r = rs.getJSONArray(k)
                                r.getInt(0)..r.getInt(1)
                            }
                        },
                    )
                },
                course = o.optJSONObject("course")?.let { c ->
                    Course(
                        kc = c.getString("kc"), teacher = c.optString("teacher"), room = c.optString("room"),
                        day = c.optInt("day", 1), jc = c.optInt("jc", 1), djs = c.optInt("djs", 1).coerceAtLeast(1),
                        zc = c.optString("zc"),
                        ranges = c.optJSONArray("ranges")?.let { rs ->
                            (0 until rs.length()).map { k ->
                                val r = rs.getJSONArray(k)
                                r.getInt(0)..r.getInt(1)
                            }
                        } ?: emptyList(),
                        fx = c.optString("fx"), jxb = c.optString("jxb"),
                        jxbzc = c.optString("jxbzc"), type = c.optInt("type", TYPE_LOCAL),
                    )
                },
            )
        }
    }
}
