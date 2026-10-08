package com.ccsut.kb.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 个人课表缓存 (filesDir/personal_cache.json)。
 * 结构与 ClassCache 快照一致但多存账号/姓名/学期, 供启动时载入与小组件/提醒进程直读
 * (提醒与小组件仍然只读 class_cache.json —— [save] 后由 ClassCache.save 分支刷写)。
 * 不存任何 Cookie/验证码。
 */
object PersonalCache {

    private const val FILE = "personal_cache.json"

    fun save(ctx: Context, d: PersonalRepo.PersonalData) {
        val root = JSONObject()
            .put("account", d.account)
            .put("studentName", d.studentName)
            .put("xnxq", d.xnxq)
            .put("startDate", d.startDate)
            .put("weeks", d.weeks)
            .put("syncedAt", d.syncedAt)
        root.put("periods", JSONArray().apply {
            d.periods.forEach { p -> put(JSONObject().put("jc", p.jc).put("start", p.start).put("end", p.end)) }
        })
        root.put("courses", JSONArray().apply {
            d.courses.forEach { c ->
                put(
CourseCodec.toJson(c))
            }
        })
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use { it.write(root.toString().toByteArray()) }
    }

    fun load(ctx: Context): PersonalRepo.PersonalData? {
        val f = ctx.getFileStreamPath(FILE) ?: return null
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()
    }

    fun clear(ctx: Context) {
        ctx.deleteFile(FILE)
    }

    /** 伪 Dataset: 周次/作息来自教务, colleges/classes 为空, 仅渲染层使用 */
    fun toDataset(d: PersonalRepo.PersonalData): Dataset = Dataset(
        version = 0,
        generatedAt = "",
        xnxq = d.xnxq,
        startDate = d.startDate,
        weeks = d.weeks,
        weekRanges = (1..d.weeks).map { "" },
        periods = d.periods,
        colleges = emptyList(),
        classes = emptyMap(),
    )

    private fun parse(text: String): PersonalRepo.PersonalData {
        val o = JSONObject(text)
        return PersonalRepo.PersonalData(
            account = o.optString("account"),
            studentName = o.optString("studentName"),
            xnxq = o.optString("xnxq"),
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
            syncedAt = o.optLong("syncedAt", 0L),
        )
    }
}
