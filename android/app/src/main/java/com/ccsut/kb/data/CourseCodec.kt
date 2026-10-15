package com.ccsut.kb.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Course ↔ JSONObject 编解码。
 * 内置数据集(Models.mapCourse)、班级快照(ClassCache)、个人课表缓存(PersonalCache)
 * 共用同一字段格式 (zcRanges 为 [first,last] 数对), 字段增改只动这里。
 */
internal object CourseCodec {

    fun fromJson(c: JSONObject): Course = Course(
        kc = c.optString("kc"), teacher = c.optString("teacher"), room = c.optString("room"),
        day = c.optInt("day", 1), jc = c.optInt("jc", 1), djs = c.optInt("djs", 1).coerceAtLeast(1),
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

    fun toJson(c: Course): JSONObject = JSONObject()
        .put("kc", c.kc).put("teacher", c.teacher).put("room", c.room)
        .put("day", c.day).put("jc", c.jc).put("djs", c.djs)
        .put("zc", c.zc)
        .put("zcRanges", JSONArray().apply { c.ranges.forEach { put(JSONArray(listOf(it.first, it.last))) } })
        .put("fx", c.fx).put("jxb", c.jxb).put("jxbzc", c.jxbzc).put("type", c.type)

    fun list(a: JSONArray?): List<Course> =
        a?.let { arr -> (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) } } ?: emptyList()
}
