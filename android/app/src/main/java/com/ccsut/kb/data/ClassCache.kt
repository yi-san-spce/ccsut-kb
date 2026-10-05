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

    /** APP 侧调用: 把 Repo 生效班级(原始数据+用户本地修改)写成快照; 未选班则删除快照 */
    fun save(ctx: Context) {
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
        val root = JSONObject()
            .put("bjid", id)
            .put("bjmc", cls.bjmc)
            .put("version", ds.version)
            .put("startDate", ds.startDate)
            .put("weeks", ds.weeks)
        root.put("periods", JSONArray().apply {
            ds.periods.forEach { p -> put(JSONObject().put("jc", p.jc).put("start", p.start).put("end", p.end)) }
        })
        root.put("courses", JSONArray().apply {
            cls.courses.forEach { c ->
                put(
                    JSONObject()
                        .put("kc", c.kc).put("teacher", c.teacher).put("room", c.room)
                        .put("day", c.day).put("jc", c.jc).put("djs", c.djs)
                        .put("zc", c.zc)
                        .put("zcRanges", JSONArray().apply {
                            c.ranges.forEach { r -> put(JSONArray(listOf(r.first, r.last))) }
                        })
                        .put("fx", c.fx).put("jxb", c.jxb).put("jxbzc", c.jxbzc).put("type", c.type)
                )
            }
        })
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(root.toString().toByteArray()) }
    }

    fun load(ctx: Context): Snapshot? {
        val f = ctx.getFileStreamPath(FILE)
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()
    }

    /** "8:20" 这种无前导零格式也能解析 */
    fun minutesOf(t: String): Int {
        val (h, m) = t.split(":").map { it.trim().toInt() }
        return h * 60 + m
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
                    Course(
                        kc = c.optString("kc"), teacher = c.optString("teacher"), room = c.optString("room"),
                        day = c.optInt("day", 1), jc = c.optInt("jc", 1), djs = c.optInt("djs", 1),
                        zc = c.optString("zc"),
                        ranges = c.optJSONArray("zcRanges")?.let { rs ->
                            (0 until rs.length()).map { k ->
                                val r = rs.getJSONArray(k)
                                r.getInt(0)..r.getInt(1)
                            }
                        } ?: emptyList(),
                        fx = c.optString("fx"), jxb = c.optString("jxb"),
                        jxbzc = c.optString("jxbzc"), type = c.optInt("type", 1),
                    )
                }
            } ?: emptyList(),
        )
    }
}
