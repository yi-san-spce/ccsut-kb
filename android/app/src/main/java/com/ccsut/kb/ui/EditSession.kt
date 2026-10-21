package com.ccsut.kb.ui

import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.util.Block
import java.util.UUID

/**
 * 编辑会话: 课表卡片被 添加/删除/修改 后的即时反馈层 (内存态, 不持久化)。
 * - 回退栈: 每笔操作记下覆盖层记录的操作前形态, 撤销时精确还原, 互不殃及后续操作;
 * - 虚影: 删除/移位在原位置留一张变淡的残影, 生存期内点按即恢复, 到点销毁。
 * 换班级/重置修改即整场作废。
 */
object EditSession {

    const val GHOST_DELETED = 0   // 删除虚影: 课程已隐藏/自建记录已删
    const val GHOST_MOVED = 1     // 移位虚影: 时间/周次被改, 新位置是带点的正常卡片

    /** 虚影生存期(毫秒): 到点销毁 */
    const val GHOST_TTL_MS = 2 * 60_000L

    /** 回退按钮生存期(毫秒): 最后一次编辑/回退后保留这么久, 过一会儿消失 */
    const val UNDO_VISIBLE_MS = 8_000L

    fun newOpId(): String = "op" + UUID.randomUUID().toString().replace("-", "").take(12)
    fun newGhostId(): String = "gh" + UUID.randomUUID().toString().replace("-", "").take(12)

    /** 剔除已过生存期的虚影 (生命周期到点即销毁; 无到期时返回原列表避免多余重组) */
    fun prune(ghosts: List<Ghost>, nowMs: Long): List<Ghost> =
        if (ghosts.none { nowMs >= it.expiresAt }) ghosts
        else ghosts.filter { nowMs < it.expiresAt }

    /** 距下一个虚影销毁的毫秒数; 无虚影返回 null */
    fun nextExpiryIn(ghosts: List<Ghost>, nowMs: Long): Long? =
        ghosts.minOfOrNull { it.expiresAt - nowMs }?.coerceAtLeast(1L)

    /** 修改是否动了 时间(day/节次/节数) 或 周次(区间): 是则原位置生成移位虚影 */
    fun touchedTimeOrWeek(form: UserEdits.Form, oldBlock: Block): Boolean =
        form.day != oldBlock.course.day ||
            form.startJc != oldBlock.startJc ||
            form.span != oldBlock.span ||
            form.ranges != oldBlock.course.ranges
}

/** 一次可撤销操作的逆操作描述 (LIFO 出栈执行) */
sealed class UndoEntry {
    abstract val opId: String

    /** 新增自建课: 撤销 = 删掉该条自建记录 */
    data class Added(override val opId: String, val editId: String) : UndoEntry()

    /** 修改课块: 撤销 = 把记录还原为操作前形态 (before=null 表示原本不存在 → 删掉) */
    data class Modified(override val opId: String, val editId: String, val before: UserEdits.Edit?) : UndoEntry()

    /** 删除课块: 撤销 = 撤掉隐藏记录 + 放回被移除的原记录 (学校课/自建课各取其一) */
    data class Removed(override val opId: String, val hideEditId: String?, val prior: UserEdits.Edit?) : UndoEntry()

    /** 还原单块(回到学校原始): 撤销 = 把该记录放回 */
    data class Reverted(override val opId: String, val before: UserEdits.Edit) : UndoEntry()
}

/** 课程卡片虚影: 编辑前形态的残影, 信息保留、整体变淡, 生存期内点按恢复 */
data class Ghost(
    val id: String,
    val opId: String,               // 与产生它的回退条目关联 (恢复/撤销互相同步摘除)
    val block: Block,               // 编辑前的课块 (原位置/原内容)
    val kind: Int,                  // GHOST_DELETED / GHOST_MOVED
    val priorEdit: UserEdits.Edit?, // 操作前的覆盖层记录 (恢复依据)
    val hideEditId: String? = null, // 删除虚影: 学校课对应的隐藏记录 id
    val moveEditId: String? = null, // 移位虚影: 现行修改记录 id (首次修改时 priorEdit 为空, 靠它撤销)
    val bornAt: Long = System.currentTimeMillis(),
    val ttlMs: Long = EditSession.GHOST_TTL_MS,
) {
    val expiresAt: Long get() = bornAt + ttlMs

    /** 原课块的周次覆盖到 viewingWeek 时, 该周页面显示此虚影 */
    fun coversWeek(week: Int): Boolean = block.course.ranges.any { week in it }
}

/** 一次编辑操作在会话里留下的产物 (操作 lambda 在 IO 线程产出) */
data class EditOpResult(
    val entry: UndoEntry?,
    val ghost: Ghost?,
    /** 本笔操作前整层覆盖层快照; 会话第一笔操作的快照即「还原全部」的目标态 */
    val beforeSnapshot: List<UserEdits.Edit>,
)
