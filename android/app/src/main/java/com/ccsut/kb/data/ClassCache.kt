package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.Prefs
import org.json.JSONArray
import org.json.JSONObject

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

    /** APP 侧调用: 把当前生效课表写成快照; 未选班且无个人课表则删除快照 */
    fun save(ctx: Context) {
        // 个人课表生效时: 快照直接来自 PersonalRepo (含 UserEdits 覆盖层), 小组件/提醒零改动
        if (Prefs.activeTimetable(ctx) == PersonalRepo.PERSONAL_KEY) {
            val d = PersonalRepo.current ?: run { ctx.deleteFile(FILE); return }
            val cls = PersonalRepo.effectiveCls() ?: run { ctx.deleteFile(FILE); return }
            saveSnapshot(
                ctx, bjid = PersonalRepo.PERSONAL_BJID, bjmc = cls.bjmc, version = 0,
                startDate = d.startDate, weeks = d.weeks, periods = d.periods, courses = cls.courses,
            )
            return
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
                put(
CourseCodec.toJson(c))
            }
        })
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(root.toString().toByteArray()) }
    }

    fun load(ctx: Context): Snapshot? {
        val f = ctx.getFileStreamPath(FILE)
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()
    }

    /** "8:20" 这种无前导零格式也能解析; 脏数据返回 -1 (调用方按无时间处理), 不让小组件进程崩 */
    fun minutesOf(t: String): Int {
        val parts = t.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 2) return -1
        return parts[0] * 60 + parts[1]
    }

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
