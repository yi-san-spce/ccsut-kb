package com.ccsut.kb.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一节课(单节)的课程条目 */
data class Course(
    val kc: String,        // 课程名
    val teacher: String,   // 教师
    val room: String,      // 教室
    val day: Int,          // 星期 1-7
    val jc: Int,           // 起始节次 1-10
    val djs: Int,          // 连堂节数
    val zc: String,        // 周次原文, 如 "1-4,6-12"
    val ranges: List<IntRange>,
    val fx: String,        // 体育分项名(可能为空)
    val jxb: String,       // 教学班
    val jxbzc: String,     // 合班班级
    val type: Int,         // 1=普通 9=体育分项占位
) {
    fun inWeek(w: Int) = ranges.any { w in it }
}

data class Cls(
    val bjmc: String,   // 班级名
    val yxmc: String,   // 学院
    val zymc: String,   // 专业
    val sznj: String,   // 年级
    val courses: List<Course>,
)

data class Major(val name: String, val classIds: List<String>)
data class College(val name: String, val majors: List<Major>)

data class Period(val jc: Int, val start: String, val end: String)

data class Dataset(
    val version: Int,
    val generatedAt: String,
    val xnxq: String,
    val startDate: String,      // 第1周周一, yyyy-MM-dd
    val weeks: Int,
    val weekRanges: List<String>,
    val periods: List<Period>,
    val colleges: List<College>,
    val classes: Map<String, Cls>,
)

object DatasetParser {

    fun parse(text: String): Dataset {
        val root = JSONObject(text)
        val term = root.getJSONObject("term")
        val classes = LinkedHashMap<String, Cls>()
        root.getJSONObject("classes").let { co ->
            for (id in co.keys()) {
                val c = co.getJSONObject(id)
                classes[id] = Cls(
                    bjmc = c.optString("bjmc"), yxmc = c.optString("yxmc"),
                    zymc = c.optString("zymc"), sznj = c.optString("sznj"),
                    courses = c.optJSONArray("courses")?.mapCourse() ?: emptyList(),
                )
            }
        }
        val colleges = root.optJSONArray("colleges")?.let { arr ->
            (0 until arr.length()).map { i ->
                val yx = arr.getJSONObject(i)
                College(
                    name = yx.optString("name"),
                    majors = yx.optJSONArray("majors")?.let { ms ->
                        (0 until ms.length()).map { j ->
                            val zy = ms.getJSONObject(j)
                            Major(
                                name = zy.optString("name"),
                                classIds = zy.optJSONArray("classes")?.let { bs ->
                                    (0 until bs.length()).map { k -> bs.getString(k) }
                                } ?: emptyList(),
                            )
                        }
                    } ?: emptyList(),
                )
            }
        } ?: emptyList()

        return Dataset(
            version = root.optInt("version", 0),
            generatedAt = root.optString("generatedAt"),
            xnxq = root.optString("xnxq"),
            startDate = term.optString("startDate"),
            weeks = term.optInt("weeks", 20),
            weekRanges = term.optJSONArray("weekRanges")?.let { ws ->
                (0 until ws.length()).map { i -> ws.getJSONObject(i).optString("range") }
            } ?: emptyList(),
            periods = root.optJSONArray("periods")?.let { ps ->
                (0 until ps.length()).map { i ->
                    val p = ps.getJSONObject(i)
                    Period(p.optInt("jc"), p.optString("start"), p.optString("end"))
                }
            } ?: emptyList(),
            colleges = colleges,
            classes = classes,
        )
    }

    private fun JSONArray.mapCourse(): List<Course> = (0 until length()).map { i ->
        val e = getJSONObject(i)
        Course(
            kc = e.optString("kc"), teacher = e.optString("teacher"), room = e.optString("room"),
            day = e.optInt("day", 1), jc = e.optInt("jc", 1), djs = e.optInt("djs", 1).coerceAtLeast(1),
            zc = e.optString("zc"),
            ranges = e.optJSONArray("zcRanges")?.let { rs ->
                (0 until rs.length()).map { k ->
                    val r = rs.getJSONArray(k)
                    r.getInt(0)..r.getInt(1)
                }
            } ?: emptyList(),
            fx = e.optString("fx"), jxb = e.optString("jxb"),
            jxbzc = e.optString("jxbzc"), type = e.optInt("type", 1),
        )
    }
}

/** 数据仓库: 内置 assets 优先级低于已下载的更新数据 */
object Repo {
    var dataset: Dataset? = null
        private set
    var fromUpdate = false
        private set

    fun load(ctx: Context) {
        val f = ctx.getFileStreamPath("dataset.json")
        if (f.exists()) {
            runCatching {
                dataset = DatasetParser.parse(f.readText())
                fromUpdate = true
                com.ccsut.kb.util.DebugLog.log("data", "载入更新数据 v${dataset?.version} (${f.length()}B)")
                return
            }
        }
        ctx.assets.open("data/dataset.json").bufferedReader().use { text ->
            dataset = DatasetParser.parse(text.readText())
        }
        fromUpdate = false
        com.ccsut.kb.util.DebugLog.log("data", "载入内置数据 v${dataset?.version}")
    }

    fun applyUpdate(ctx: Context, bytes: ByteArray): Dataset? {
        val text = bytes.toString(Charsets.UTF_8)
        val parsed = runCatching { DatasetParser.parse(text) }.getOrNull() ?: return null
        ctx.openFileOutput("dataset.json", Context.MODE_PRIVATE).use { it.write(bytes) }
        dataset = parsed
        fromUpdate = true
        com.ccsut.kb.util.DebugLog.log("data", "应用更新数据 v${parsed.version} (${bytes.size}B)")
        return parsed
    }

    fun clearUpdate(ctx: Context) {
        ctx.deleteFile("dataset.json")
        fromUpdate = false
        load(ctx)
        com.ccsut.kb.util.DebugLog.log("data", "已清除更新数据, 回到内置 v${dataset?.version}")
    }
}
