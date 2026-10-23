package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.Prefs
import com.ccsut.kb.util.DebugLog
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 远程通知: 主仓 master 的 `data/notice.json` (与课表数据清单 latest.json 同目录, 同走镜像加速)。
 * 维护者把文件推上仓库, 全量 App 下次打开时拉取并按规则选取弹窗; 删掉文件即全网停用。
 *
 * 文件支持两种形态 (单对象为简写, 等价于只有一条的数组):
 * ```json
 * {
 *   "notices": [
 *     {
 *       "id": "2026-10-maint",       // 去重键, 缺省由内容派生 (改文案即换 id)
 *       "title": "通知标题",
 *       "message": "通知正文",
 *       "url": "https://...",        // 可选: 点「确认」用浏览器打开
 *       "link": "personal-login",    // 可选: 点「确认」在 App 内跳转 (1.2.0-beta.4+ 支持; 旧版本
 *                                    //  忽略此字段回退 url)。现支持: personal-login = 打开教务登录/同步页
 *       "confirmText": "去频道看看",  // 可选: 主按钮文案, 缺省「确认」
 *       "dismissText": "知道了",      // 可选: 次按钮文案, 缺省「取消」 (critical 无次按钮)
 *       "once": true,                // 默认 true: 用户点过按钮后不再弹; false: 每次打开都弹
 *       "priority": "normal",        // normal(默认) | critical: 阻断弹窗, 只有「确认」, 点外部/返回不关闭
 *       "startAt": "2026-10-15 00:00:00",  // 排期窗口 (闭区间), 缺省=不限制
 *       "endAt":   "2026-10-20 23:59:59",  // 纯日期 "2026-10-20" 表示含当天全天
 *       "minVersion": "1.0.0",       // 版本定向 (版本名比较, 含通道后缀语义): 只弹给 >= min 且 <= max
 *       "maxVersion": "1.0.0"
 *     }
 *   ]
 * }
 * ```
 *
 * 选取规则 ([select]): 排期窗口过滤 → 版本定向过滤 → 去重 (once + 本地已读) → 优先级排序 → 取第一条。
 * 一次启动最多弹一条; 排期/定向判断用 KbClock.now() (开发者时间旅行可测窗口) 与 BuildVersion.DISPLAY。
 */
data class RemoteNotice(
    val id: String,
    val title: String,
    val message: String,
    val url: String? = null,
    val link: String? = null,
    val once: Boolean = true,
    val critical: Boolean = false,
    val confirmText: String = "确认",
    val dismissText: String = "取消",
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null,
    val minVersion: String? = null,
    val maxVersion: String? = null,
)

object RemoteNotices {

    /** 已读记录上限: 防止长期维护把 prefs 撑大 (超出淘汰最旧) */
    private const val SEEN_CAP = 32

    /**
     * 拉取并解析通知列表; 文件不存在 (404) / 网络失败 / 内容无效一律返回空 (不打扰用户)。
     * 拉取成功后顺手清理已读记录里文件已不存在的 id。
     */
    fun fetch(ctx: Context): List<RemoteNotice> {
        val url = UpdateChannels.dataManifestUrl(ctx).substringBeforeLast('/') + "/notice.json"
        val list = runCatching {
            parse(JSONObject(String(Updater.httpGet(url), Charsets.UTF_8)))
        }.getOrElse {
            DebugLog.log("update", "远程通知拉取失败: ${it.message}")
            return emptyList()
        }
        val seen = Prefs.noticeSeen(ctx)
        if (list.isNotEmpty() && seen.isNotEmpty()) {
            val live = list.map { it.id }.toSet()
            val kept = seen.filterKeys { it in live }
            if (kept.size != seen.size) Prefs.setNoticeSeen(ctx, kept)
        }
        return list
    }

    /**
     * 选出本次命中的全部通知 (调用方与更新弹窗合并排序进队列): 排期窗口 → 版本定向 → 去重过滤后,
     * critical 在前、同级保持文件里给的顺序。
     * now / currentVersion / seen 由调用方注入 (KbClock / BuildVersion / Prefs), 单测可全量构造。
     */
    fun selectAll(
        notices: List<RemoteNotice>,
        now: LocalDateTime,
        currentVersion: String,
        seen: Set<String>,
    ): List<RemoteNotice> = notices.asSequence()
        .filter { n ->
            (n.startAt == null || !now.isBefore(n.startAt)) &&
                (n.endAt == null || !now.isAfter(n.endAt))
        }
        .filter { n ->
            (n.minVersion == null || Releases.compareVersion(currentVersion, n.minVersion) >= 0) &&
                (n.maxVersion == null || Releases.compareVersion(currentVersion, n.maxVersion) <= 0)
        }
        .filter { n -> !(n.once && n.id in seen) }
        .sortedWith(compareByDescending { it.critical })   // 稳定排序: 同优先级保持文件里给的顺序
        .toList()

    /** 选出本次要弹的那一条 (selectAll 首条); 保留给单测与简单调用方 */
    fun select(
        notices: List<RemoteNotice>,
        now: LocalDateTime,
        currentVersion: String,
        seen: Set<String>,
    ): RemoteNotice? = selectAll(notices, now, currentVersion, seen).firstOrNull()

    /** 记录已读 (once 类通知在用户点过弹窗按钮后调用) */
    fun markSeen(ctx: Context, id: String) {
        val map = Prefs.noticeSeen(ctx).toMutableMap()
        map[id] = System.currentTimeMillis()
        while (map.size > SEEN_CAP) map.remove(map.minByOrNull { it.value }?.key)
        Prefs.setNoticeSeen(ctx, map)
    }

    fun seenIds(ctx: Context): Set<String> = Prefs.noticeSeen(ctx).keys

    // ---- 解析 ----

    /** internal 仅为单测可及; 兼容 {"notices":[...]} 数组与旧版单对象 {title,message} 两种形态 */
    internal fun parse(root: JSONObject): List<RemoteNotice> {
        val arr = root.optJSONArray("notices") ?: return listOfNotNull(parseOne(root))
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseOne(it) } }
    }

    private fun parseOne(o: JSONObject): RemoteNotice? {
        val msg = o.optString("message").trim()
        if (msg.isBlank()) return null
        return RemoteNotice(
            id = o.optString("id").trim().ifBlank { contentId(o) },
            title = o.optString("title").trim().ifBlank { "通知" },
            message = msg,
            url = o.optString("url").trim()
                .takeIf { it.startsWith("https://") || it.startsWith("http://") },
            link = o.optString("link").trim().takeIf { it.isNotBlank() },
            once = o.optBoolean("once", true),
            critical = o.optString("priority").trim().equals("critical", ignoreCase = true),
            confirmText = o.optString("confirmText").trim().ifBlank { "确认" },
            dismissText = o.optString("dismissText").trim().ifBlank { "取消" },
            startAt = o.optString("startAt").takeIf { it.isNotBlank() }?.let { parseTime(it, endOfDay = false) },
            endAt = o.optString("endAt").takeIf { it.isNotBlank() }?.let { parseTime(it, endOfDay = true) },
            minVersion = o.optString("minVersion").trim().takeIf { it.isNotBlank() },
            maxVersion = o.optString("maxVersion").trim().takeIf { it.isNotBlank() },
        )
    }

    /** 无 id 字段时由内容派生稳定 id (16 位 hex); 改文案即换 id, once 类通知会重新弹一次 */
    private fun contentId(o: JSONObject): String =
        Updater.sha256Hex("${o.optString("title")}|${o.optString("message")}".toByteArray()).take(16)

    /**
     * 时间字段宽松解析, 三种写法都收:
     * - "2026-10-15 09:00:00" / "2026-10-15T09:00:00" / "2026-10-15T09:00" (按设备本地时间)
     * - ISO 带时区 "2026-10-15T09:00:00+08:00" (换算到设备本地时区再比较)
     * - 纯日期 "2026-10-15": startAt=当天 0 点, endAt=当天 23:59:59 (含当天)
     * 解析失败返回 null (该字段视为缺省, 不让一个笔误杀死整条通知)。
     */
    internal fun parseTime(raw: String, endOfDay: Boolean): LocalDateTime? {
        val s = raw.trim().let { if (it.length > 10 && it[10] == ' ') it.take(10) + 'T' + it.substring(11) else it }
        return runCatching { LocalDateTime.parse(s) }
            .recoverCatching { OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }
            .recoverCatching {
                val d = LocalDate.parse(s)
                if (endOfDay) d.atTime(23, 59, 59) else d.atStartOfDay()
            }
            .getOrNull()
    }
}
