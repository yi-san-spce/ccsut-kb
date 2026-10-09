package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.Prefs
import com.ccsut.kb.util.TimeParse
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 当前班级的轻量快照 (filesDir/class_cache.json, 几十 KB)。
 * 桌面小组件和课前提醒的进程里解析全量 dataset(4MB+)太贵,
 * APP 每次加载数据/换班/更新后调用 [save] 刷新快照, 二者只读快照。
 */
object ClassCache {

    data class Snapshot(
        val bjid: String,
        val bjmc: String,
        val version: Int,
        val startDate: String,   // 第1周周一, yyyy-MM-dd
        val weeks: Int,
        val periods: List<Period>,
        val courses: List<Course>,
    )

    private const val FILE = "class_cache.json"

    // App 内多处在途并发写快照(启动更新检查/resync/登出): 互斥防两份 tmp 交错
    private val writeLock = Any()

    /** APP 侧调用: 把当前生效课表写成快照; 未选班且无个人课表则删除快照 */
    fun save(ctx: Context) {
        // 个人课表生效时: 快照直接来自 PersonalRepo (含 UserEdits 覆盖层), 小组件/提醒零改动
        if (Prefs.activeTimetable(ctx) == PersonalRepo.PERSONAL_KEY) {
            val d = PersonalRepo.current
            val cls = d?.let { PersonalRepo.effectiveCls() }
            if (d != null && cls != null) {
                saveSnapshot(
                    ctx, bjid = PersonalRepo.PERSONAL_BJID, bjmc = cls.bjmc, version = 0,
                    startDate = d.startDate, weeks = d.weeks, periods = d.periods, courses = cls.courses,
                )
                return
            }
            // 个人数据不可用(缓存丢失/损坏): 不再删快照留空 —— 落到下面的班级分支回写班级快照,
            // 与 App 内「回退显示班级课表」保持同一标准, 小组件/提醒不至于停摆
        }
        val ds = Repo.dataset
        val id = Prefs.bjid(ctx)
        if (ds == null || id == null) {
            ctx.deleteFile(FILE)
            return
        }
        // 用覆盖层叠加后的生效课表, 用户编辑自动进小组件/提醒
        val cls = Repo.effectiveCls(id) ?: run {
            ctx.deleteFile(FILE)
            return
        }
        saveSnapshot(
            ctx, bjid = id, bjmc = cls.bjmc, version = ds.version,
            startDate = ds.startDate, weeks = ds.weeks, periods = ds.periods, courses = cls.courses,
        )
    }

    private fun saveSnapshot(
        ctx: Context,
        bjid: String,
        bjmc: String,
        version: Int,
        startDate: String,
        weeks: Int,
        periods: List<Period>,
        courses: List<Course>,
    ) {
        val root = JSONObject()
            .put("bjid", bjid)
            .put("bjmc", bjmc)
            .put("version", version)
            .put("startDate", startDate)
            .put("weeks", weeks)
        root.put("periods", JSONArray().apply {
            periods.forEach { p -> put(JSONObject().put("jc", p.jc).put("start", p.start).put("end", p.end)) }
        })
        root.put("courses", JSONArray().apply {
            courses.forEach { c ->
                put(CourseCodec.toJson(c))
            }
        })
        // tmp+rename 原子写 (与 UserEdits 同款): 写一半崩溃不损坏快照,
        // 小组件/提醒进程也读不到撕裂文件; 对照旧实现 openFileOutput 直写
        synchronized(writeLock) {
            val dst = File(ctx.filesDir, FILE)
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            runCatching {
                tmp.writeText(root.toString())
                if (!tmp.renameTo(dst)) {
                    dst.writeText(root.toString())  // 同分区 rename 失败极少见, 兜底直写
                    tmp.delete()
                }
            }.onFailure {
                tmp.delete()
                throw it
            }
        }
    }

    fun load(ctx: Context): Snapshot? {
        val f = ctx.getFileStreamPath(FILE)
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()
    }

    /** "8:20" 这种无前导零格式也能解析; 脏数据返回 -1 (调用方按无时间处理), 不让小组件进程崩 */
    fun minutesOf(t: String): Int = TimeParse.minutesOf(t) ?: -1

    private fun parse(text: String): Snapshot {
        val o = JSONObject(text)
        val bjid = o.getString("bjid")
        return Snapshot(
            bjid = bjid,
            bjmc = o.optString("bjmc"),
            version = o.optInt("version", 0),
            startDate = o.optString("startDate"),
            weeks = o.optInt("weeks", 20),
            periods = o.optJSONArray("periods")?.let { ps ->
                (0 until ps.length()).map { i ->
                    val p = ps.getJSONObject(i)
                    Period(p.optInt("jc"), p.optString("start"), p.optString("end"))
                }
            } ?: emptyList(),
            courses = o.optJSONArray("courses")?.let { cs ->
                (0 until cs.length()).map { i ->
                    val c = cs.getJSONObject(i)
            CourseCodec.fromJson(c)
                }
            } ?: emptyList(),
        )
    }
}
