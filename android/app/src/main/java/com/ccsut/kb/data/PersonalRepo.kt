package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.util.DebugLog
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

/**
 * 个人课表仓库: 教务登录会话里一气呵成拉取 → 解析成 [Course] → 缓存。
 *
 * 复用班级课表的全部基建 —— 个人课表用固定伪班级 id [PERSONAL_BJID]:
 * [UserEdits] 按 bjid 键控, 本地编辑/拖拽/配色对个人课表零改动生效;
 * 渲染层只需要一个 [Cls] 和一个 [Dataset] (周次/作息来自教务 getZclistByXnxq)。
 *
 * 会话特性 (docs/api-personal.md): 教务会话短时效, 设计为「登录→立即拉全量→本地缓存,
 * 刷新需重新验证码登录」; 本对象不保存任何 Cookie。
 */
object PersonalRepo {

    /** 个人课表的伪班级 id (UserEdits/课程配色的键) */
    const val PERSONAL_BJID = "PERSONAL"

    /** Prefs.activeTimetable 的个人课表取值 */
    const val PERSONAL_KEY = "personal"

    data class PersonalData(
        val account: String,        // 登录账号 (学号或手机号)
        val studentName: String,    // 教务里的姓名
        val xnxq: String,           // 学年学期, 如 2026-2027-1
        val startDate: String,      // 第 1 周周一, yyyy-MM-dd
        val weeks: Int,
        val periods: List<Period>,
        val courses: List<Course>,  // 原始解析结果 (不含 UserEdits 覆盖层)
        val syncedAt: Long,
    )

    /** 当前内存中的个人课表 (App 启动时从 PersonalCache 载入) */
    var current: PersonalData? = null
        private set

    fun load(ctx: Context) {
        UserEdits.ensureLoaded(ctx)
        current = PersonalCache.load(ctx)
        DebugLog.log("personal", "载入个人课表缓存: ${if (current != null) "${current!!.courses.size} 条" else "无"}")
    }

    fun clear(ctx: Context) {
        current = null
        PersonalCache.clear(ctx)
    }

    fun hasData(): Boolean = current != null

    /** 生效个人课表 = 原始解析 + UserEdits 覆盖层 (键控 PERSONAL) */
    fun effectiveCls(): Cls? {
        val d = current ?: return null
        val eff = UserEdits.effective(PERSONAL_BJID, d.courses)
        return Cls(
            bjmc = if (d.studentName.isNotBlank()) "${d.studentName}的个人课表" else "个人课表",
            yxmc = "", zymc = "", sznj = "",
            courses = eff,
        )
    }

    /** 供 ScheduleScreen/翻周/今日状态使用的伪 Dataset */
    fun dataset(): Dataset? {
        val d = current ?: return null
        return PersonalCache.toDataset(d)
    }

    // ---------------------------------------------------------------- 拉取

    /**
     * 登录成功后调用: 教务会话 → 课表页(取 xhid/姓名/学期) → 周次作息 → 全量课表。
     * 任一步会话失效都会抛 [CasClient.CasException](NOT_LOGGED_IN)。
     */
    fun fetchAll(ctx: Context, account: String): PersonalData {
        CasClient.establishTls()

        // 1) 课表页: 先带推算学期参数 (与网页行为一致), 不行再试无参(服务端默认当前学期)
        val guess = guessXnxq(LocalDate.now())
        val page = CasClient.get("${CasClient.TLS_BASE}/admin/pkgl/xskb/queryKbForXsd?xnxq=$guess")
        var html = page.text()
        var parsed = parseKbPage(html)
        if (parsed == null) {
            val fallback = CasClient.get("${CasClient.TLS_BASE}/admin/pkgl/xskb/queryKbForXsd")
            html = fallback.text()
            parsed = parseKbPage(html)
        }
        val (xnxq, xhid, campus, studentName) = parsed
            ?: throw CasClient.CasException(
                CasClient.CasException.Kind.NOT_LOGGED_IN,
                "教务会话已失效，请重新验证码登录（${diagPage(html)}）",
            )

        // 2) 周次与作息 (个人课表自包含, 不依赖班级数据集)
        val zcResponse = CasClient.get(
            "${CasClient.TLS_BASE}/admin/api/getZclistByXnxq?xnxq=$xnxq&xqid=${URLEncoder.encode(campus, "UTF-8")}",
        )
        val zc = zcResponse.text()
        val zcRoot = runCatching { JSONObject(zc) }.getOrNull()
        val zcOk = zcRoot?.has("ret") == true && zcRoot.optInt("ret", -1) == 0
        if (zcResponse.status !in 200..299 || !zcOk) {
            throw CasClient.CasException(CasClient.CasException.Kind.PROTOCOL, responseError("周次", zcResponse))
        }
        val (startDate, weeks, periods) = parseZclist(zc)
        if (startDate.isBlank() || weeks <= 0) {
            throw CasClient.CasException(CasClient.CasException.Kind.PROTOCOL, "学期周次数据异常，请稍后再试")
        }

        // 3) 全量课表 (一次拿整学期)
        val kbResponse = CasClient.get(
            "${CasClient.TLS_BASE}/admin/pkgl/xskb/sdpkkbList" +
                "?xnxq=$xnxq&xhid=${URLEncoder.encode(xhid, "UTF-8")}" +
                "&xqdm=${URLEncoder.encode(campus, "UTF-8")}" +
                "&zxzc=&zdzc=&xskbxslx=0",
        )
        val kb = kbResponse.text()
        val kbRoot = runCatching { JSONObject(kb) }.getOrNull()
        val kbOk = kbRoot?.has("ret") == true && kbRoot.optInt("ret", -1) == 0
        if (kbResponse.status !in 200..299 || !kbOk) {
            throw CasClient.CasException(CasClient.CasException.Kind.PROTOCOL, responseError("课表", kbResponse))
        }
        val courses = parseSdpkkb(kb)
        if (courses.isEmpty()) {
            throw CasClient.CasException(CasClient.CasException.Kind.PROTOCOL, "课表数据为空，请稍后再试")
        }

        val data = PersonalData(
            account = account,
            studentName = studentName,
            xnxq = xnxq,
            startDate = startDate,
            weeks = weeks,
            periods = periods,
            courses = courses,
            syncedAt = System.currentTimeMillis(),
        )
        current = data
        PersonalCache.save(ctx, data)
        DebugLog.log("personal", "拉取个人课表成功: ${courses.size} 条 / $xnxq / $studentName")
        return data
    }

    /** 按今天推算当前学年学期 (如 2026-10 → 2026-2027-1) */
    fun guessXnxq(today: LocalDate): String {
        val y = today.year
        return when {
            today.monthValue >= 8 -> "$y-${y + 1}-1"
            today.monthValue == 1 -> "${y - 1}-$y-1"
            else -> "${y - 1}-$y-2"
        }
    }

    private fun responseError(label: String, response: CasClient.Response): String {
        val text = response.text()
        val root = runCatching { JSONObject(text) }.getOrNull()
        val ret = root?.optString("ret")?.takeIf { it.isNotBlank() }
        val msg = root?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: root?.optString("message")?.takeIf { it.isNotBlank() }
        return "$label 请求失败(HTTP ${response.status}${ret?.let { ", ret=$it" } ?: ""}" +
            "${msg?.let { ": $it" } ?: ""})"
    }

    /** 响应 HTML 落点识别 (诊断信息) */
    private fun diagPage(html: String): String = when {
        html.contains("75500006") || html.contains("当前账号已在线") -> "aTrust 提示账号已在线"
        html.contains("账号登录") && html.contains("统一认证") -> "落在教务登录页"
        html.contains("flowExecutionKey") -> "落在 CAS 登录页"
        html.isBlank() -> "空响应"
        else -> "未知页面 ${html.length}B"
    }

    // ---------------------------------------------------------------- 解析

    /**
     * 课表页 HTML → (xnxq, xhid, xqdm 校区, 学生姓名); 结构不符返回 null。
     * 页面为服务端渲染的隐藏域: <input type="hidden" id="xhid" value="WG...">
     */
    fun parseKbPage(html: String): Quad? {
        fun hidden(id: String): String? =
            Regex("id=\"$id\"[^>]*value=\"([^\"]*)\"").find(html)?.groupValues?.get(1)
        val xhid = hidden("xhid")?.takeIf { it.isNotBlank() } ?: return null
        val xnxq = hidden("xnxq")?.takeIf { it.isNotBlank() } ?: return null
        val campus = hidden("xqdm")?.takeIf { it.isNotBlank() } ?: "01"
        // 标题形如 "2026-2027学年第1学期王奕的课表"
        val name = Regex("学年第\\d学期([^<]{1,12}?)的课表").find(html)?.groupValues?.get(1)?.trim() ?: ""
        return Quad(xnxq, xhid, campus, name)
    }

    data class Quad(val xnxq: String, val xhid: String, val campus: String, val name: String)

    /** getZclistByXnxq → (第1周周一, 总周数, 节次时间) */
    fun parseZclist(json: String): Triple<String, Int, List<Period>> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return Triple("", 0, emptyList())
        val data = root.optJSONObject("data") ?: return Triple("", 0, emptyList())
        val zclist = data.optJSONArray("zclist")
        // minrq 形如 "2026-09-07 00:00:00", 只要日期部分 (Weeks 按 yyyy-MM-dd 解析)
        val startDate = zclist?.optJSONObject(0)?.optString("minrq")?.take(10) ?: ""
        val weeks = zclist?.length() ?: 0
        val periods = data.optJSONArray("jcsjszList")?.let { arr ->
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                Period(p.optInt("jc", i + 1), p.optString("kssj"), p.optString("jssj"))
            }
        } ?: emptyList()
        return Triple(startDate, weeks, periods)
    }

    private val tagRegex = Regex("<[^>]*>")

    /** 剥掉 kcmc/tmc/croommc 里的 <a> 标签与常见实体 */
    private fun stripHtml(s: String): String =
        s.replace(tagRegex, "").replace("&nbsp;", " ").trim()

    /** "11,13,14,15" → ("11,13-15", [11..11, 13..15]) */
    fun parseZcstr(zcstr: String): Pair<String, List<IntRange>> {
        val ws = zcstr.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..30 }.distinct().sorted()
        val ranges = mutableListOf<IntRange>()
        for (w in ws) {
            val last = ranges.lastOrNull()
            if (last != null && last.last == w - 1) ranges[ranges.size - 1] = last.first..w
            else ranges += w..w
        }
        val text = ranges.joinToString(",") {
            if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}"
        }
        return text to ranges
    }

    /**
     * sdpkkbList JSON → Course 列表。
     * 每条记录 = 一个「星期×节次」格 (djc 起始节), 连堂表现为相邻 djc 的多条记录 ——
     * 与班级课表相同的「每节一行」形态, Merger 按连续节次自行合并, 这里一律 djs=1。
     */
    fun parseSdpkkb(json: String): List<Course> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Course>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val zcstr = r.optString("zcstr")
            if (zcstr.isBlank()) continue
            val (zc, ranges) = parseZcstr(zcstr)
            if (ranges.isEmpty()) continue
            out += Course(
                kc = stripHtml(r.optString("kcmc")).ifBlank { "未命名课程" },
                teacher = stripHtml(r.optString("tmc")),
                room = stripHtml(r.optString("croommc")),
                day = r.optInt("xingqi", 1).coerceIn(1, 7),
                jc = r.optInt("djc", 1).coerceIn(1, 15),
                djs = 1,
                zc = zc,
                ranges = ranges,
                fx = "",
                jxb = stripHtml(r.optString("jxbmc")),
                jxbzc = stripHtml(r.optString("jxbzc")),
                type = 1,
            )
        }
        return out
    }
}
