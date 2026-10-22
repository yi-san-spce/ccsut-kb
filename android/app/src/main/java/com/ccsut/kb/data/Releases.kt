package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.util.DebugLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * 更新软件源: GitHub 镜像加速 (默认) / GitHub 官方直连。
 *
 * 两种源共用同一套 [ReleaseInfo] 展示模型与下载安装链路, 切源只影响数据从哪来:
 * - 镜像模式: 并发探测镜像清单 (内置 + versions.json 远程下发) 选最快可用,
 *   版本列表与 APK 走最新 Release 资产里的静态 versions.json (镜像代理 github.com 下载地址),
 *   课表数据热更走主仓 raw (同样经镜像); 镜像全挂自动退直连
 * - 官方模式: Releases API 为主 (含下载量统计), 限流/失败回退 versions.json 直连
 *
 * 存储: SharedPreferences("kb") 的 "update_source" (int)。0=官方 1=镜像(默认);
 * 键缺失或异常遗留值一律按镜像处理。
 */
object UpdateChannels {
    const val SOURCE_OFFICIAL = 0
    const val SOURCE_MIRROR = 1

    const val GH_SLUG = "yi-san-spce/ccsut-kb"

    /** 主仓 master 上的课表数据清单 (数据热更通道, 随源码仓库一起提交) */
    const val GITHUB_DATA_URL = "https://raw.githubusercontent.com/$GH_SLUG/master/data/latest.json"

    fun source(ctx: Context): Int {
        val p = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)
        if (!p.contains("update_source")) {
            // 键缺失按镜像处理; 明确存过 0 的才是官方直连用户
            p.edit().putInt("update_source", SOURCE_MIRROR).apply()
            return SOURCE_MIRROR
        }
        val v = p.getInt("update_source", SOURCE_MIRROR)
        return if (v == SOURCE_OFFICIAL) SOURCE_OFFICIAL else SOURCE_MIRROR
    }

    fun setSource(ctx: Context, source: Int) {
        ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)
            .edit().putInt("update_source", source).apply()
    }

    fun label(source: Int): String =
        if (source == SOURCE_OFFICIAL) "官方直连" else "镜像加速"

    /** 课表数据清单地址: 镜像模式下经当前镜像代理 (探测失败原样直连) */
    fun dataManifestUrl(ctx: Context): String {
        val raw = GITHUB_DATA_URL
        return if (source(ctx) == SOURCE_MIRROR) {
            Mirrors.mirrorize(Mirrors.current(ctx, Releases.GH_VERSIONS_URL), raw)
        } else raw
    }
}

/**
 * 版本通道: 稳定版 / 内测版 / 抢先版 (内部标识 stable / beta / preview, 与 tag 后缀对应)。
 * 开发者版 dev 是**版本类**而非可选通道: 用户不可切换, 也不出现在任何版本列表 (visible 恒 false),
 * 只体现在版本号后缀里 (debug 构建自动挂 -dev)。
 *
 * - 稳定版与抢先版走公开渠道 (GitHub 镜像/官方); 内测版**不走公开渠道**:
 *   团队单独派发一个内测清单地址 (https, versions 索引或 latest.json 格式均可), 只有拿到链接的人能更新
 * - 可见性为累积式: 稳定只看稳定; 内测看自己的清单; 抢先看稳定+抢先; dev 不看也不被看
 * - 版本号后缀 (全英文): X.Y.Z-stable / vX.Y.Z-beta.N / vX.Y.Z-pre.YYYYMMDD / X.Y.Z-dev
 */
object Channels {
    const val STABLE = 0
    const val BETA = 1
    const val PREVIEW = 2
    const val DEV = 3

    const val TAG_STABLE = "stable"
    const val TAG_BETA = "beta"
    const val TAG_PREVIEW = "preview"
    const val TAG_DEV = "dev"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)

    fun current(ctx: Context): Int = prefs(ctx).getInt("update_channel", STABLE)

    fun set(ctx: Context, channel: Int) {
        prefs(ctx).edit().putInt("update_channel", channel).apply()
    }

    fun label(channel: Int): String = when (channel) {
        BETA -> "内测版"
        PREVIEW -> "抢先版"
        DEV -> "开发者版"
        else -> "稳定版"
    }

    /** 版本名后缀 → 通道标识 (无后缀 = 稳定, 兼容历史裸版本号) */
    fun ofVersion(versionName: String): String {
        val s = versionName.trim().removePrefix("v").removePrefix("V")
        return when {
            Regex("-(beta\\.?\\d*)$").containsMatchIn(s) -> TAG_BETA
            Regex("-(pre\\.?\\d*)$").containsMatchIn(s) -> TAG_PREVIEW
            Regex("-(dev\\.?\\d*)$").containsMatchIn(s) -> TAG_DEV
            Regex("-stable$").containsMatchIn(s) -> TAG_STABLE
            else -> TAG_STABLE
        }
    }

    /** 公开通道可见性: 稳定只看稳定, 抢先看稳定+抢先; 内测只看自己的清单 (beta+stable 条目,
     *  公开渠道拉取不到内测条目); dev 条目在任何通道都不显示 —— 开发者版本只存在于版本号里 */
    fun visible(releaseChannel: String, channel: Int): Boolean = when {
        releaseChannel == TAG_DEV -> false
        channel == PREVIEW -> releaseChannel == TAG_STABLE || releaseChannel == TAG_PREVIEW
        channel == BETA -> releaseChannel == TAG_BETA || releaseChannel == TAG_STABLE
        else -> releaseChannel == TAG_STABLE
    }

    /** 内测清单地址 (团队私发, https), 内测通道专属 */
    fun betaUrl(ctx: Context): String =
        prefs(ctx).getString("beta_url", "") ?: ""

    fun setBetaUrl(ctx: Context, url: String) {
        prefs(ctx).edit().putString("beta_url", url.trim()).apply()
    }
}

/** 一段更新说明 (小节标题 + 条目; title 为 null 表示无小节的散条) */
data class NoteSection(val title: String?, val items: List<String>)

/** 一个可安装的 APK 版本, 各通道统一成这个展示模型 */
data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int? = null,
    val date: String = "",           // ISO yyyy-MM-dd, 可能为空
    val fileUrl: String? = null,     // APK 绝对 https 直链 (可能已带镜像前缀); null = 该版本没有安装包
    val bytes: Long? = null,
    val sha256: String? = null,      // versions.json 有 sha256; 纯 API 通道没有, 为 null 时跳过校验
    val downloadCount: Long? = null, // GitHub Releases 资产下载量 (发布时快照 / API 实时)
    val channel: String = Channels.TAG_STABLE,
    val sections: List<NoteSection> = emptyList(),
    val force: Boolean = false,      // 强制更新: 弹窗不可取消 (publish.sh --force 写入 versions.json / Release 正文标记)
)

object Releases {

    // ---- 拉取 ----

    /** 拉取指定源+通道的版本列表 (新版本在前)。网络/解析失败抛异常, 由调用方兜。 */
    fun fetchAll(ctx: Context, source: Int, channel: Int): List<ReleaseInfo> {
        val list = when (channel) {
            Channels.BETA -> fetchBetaIndex(ctx)
            else -> when (source) {
                UpdateChannels.SOURCE_OFFICIAL -> fetchOfficial(ctx, channel)
                else -> fetchViaMirror(ctx, channel)
            }
        }
        return list.sortedWith(compareByDescending { it.versionCode ?: Int.MIN_VALUE })
    }

    /** 启动自动检查用: 只拿「最新一个版本」。 */
    fun fetchLatest(ctx: Context, source: Int, channel: Int): ReleaseInfo? =
        runCatching { fetchAll(ctx, source, channel).firstOrNull() }.getOrNull()

    /** 官方直连: Releases API 为主; 限流/失败回退 Release 资产里的静态索引 (无限流) */
    private fun fetchOfficial(ctx: Context, channel: Int): List<ReleaseInfo> {
        try {
            val arr = JSONArray(String(Updater.httpGet(GH_API_RELEASES, headers = GH_HEADERS), Charsets.UTF_8))
            return (0 until arr.length()).mapNotNull { i -> parseGitHubRelease(arr.getJSONObject(i)) }
                .filter { Channels.visible(it.channel, channel) }
        } catch (apiError: Exception) {
            DebugLog.log("update", "GitHub API 失败(${apiError.message}), 回退静态索引")
            try {
                val indexUrl = if (channel == Channels.PREVIEW) GH_PREVIEW_VERSIONS_URL else GH_VERSIONS_URL
                return parseVersionsEntries(JSONObject(String(Updater.httpGet(indexUrl), Charsets.UTF_8))) { name, file ->
                    ghAssetUrl(name, file)
                }.filter { Channels.visible(it.channel, channel) }
            } catch (staticError: Exception) {
                DebugLog.log("update", "静态索引也失败: ${staticError.message}")
                throw apiError   // 兜底也没成: 抛回原始 API 错误 (限流等更有解释性)
            }
        }
    }

    /**
     * 镜像模式: 并发探测选最快镜像 → 最新 Release 资产里的静态索引。
     * 镜像失败 (缓存过期/坏了) invalidate 后退直连再试一次, 再失败把错误抛给页面。
     */
    private fun fetchViaMirror(ctx: Context, channel: Int): List<ReleaseInfo> {
        val indexUrl = if (channel == Channels.PREVIEW) GH_PREVIEW_VERSIONS_URL else GH_VERSIONS_URL
        var lastError: Exception? = null
        for (mirror in listOf(Mirrors.current(ctx, GH_VERSIONS_URL), null).distinct()) {
            try {
                val o = JSONObject(
                    String(Updater.httpGet(Mirrors.mirrorize(mirror, indexUrl), headers = GH_HEADERS), Charsets.UTF_8),
                )
                Mirrors.saveRemote(ctx, mirrorsOf(o))
                return parseVersionsEntries(o) { name, file ->
                    Mirrors.mirrorize(mirror, ghAssetUrl(name, file))
                }.filter { Channels.visible(it.channel, channel) }
            } catch (e: Exception) {
                lastError = e
                if (mirror != null) Mirrors.invalidate(ctx)   // 缓存的镜像坏了, 下次重新测速
            }
        }
        throw lastError ?: IllegalStateException("镜像源不可用，请切换官方直连或稍后再试")
    }

    /**
     * 内测通道: 团队私发的清单地址, 不经镜像直连 (链接本身就是访问凭证)。
     * 兼容两种格式: versions 索引 ({"versions":[...]}) 与 latest.json 清单 (apk 段, 仅最新一条)。
     */
    private fun fetchBetaIndex(ctx: Context): List<ReleaseInfo> {
        val url = Channels.betaUrl(ctx)
        if (url.isBlank()) throw IllegalStateException("未配置内测清单地址，点「内测清单地址」填入团队派发的链接")
        if (!url.startsWith("https://")) throw IllegalStateException("内测清单地址要以 https:// 开头")
        val o = JSONObject(String(Updater.httpGet(url), Charsets.UTF_8))
        val list = if (o.optJSONArray("versions") != null) {
            parseVersionsEntries(o) { _, file ->
                file?.let { runCatching { Updater.resolve(url.substringBeforeLast('/'), it) }.getOrNull() }
            }
        } else {
            listOfNotNull(manifestToRelease(url, Updater.parseManifest(o)))
        }
        return list.filter { Channels.visible(it.channel, Channels.BETA) }
    }

    /** 清单 apk 段 → ReleaseInfo (内测 latest.json 兼容) */
    private fun manifestToRelease(manifestUrl: String, m: Manifest?): ReleaseInfo? {
        m ?: return null
        val file = m.apkFile ?: return null
        val name = m.apkVersionName ?: m.apkVersionCode?.toString() ?: return null
        return ReleaseInfo(
            versionName = name,
            versionCode = m.apkVersionCode,
            date = m.generatedAt.take(10),
            fileUrl = runCatching { Updater.resolve(manifestUrl.substringBeforeLast('/'), file) }.getOrNull(),
            bytes = m.apkBytes,
            sha256 = m.apkSha256,
            channel = Channels.ofVersion(name),
            sections = if (m.apkNotes.isEmpty()) emptyList() else listOf(NoteSection(null, m.apkNotes)),
        )
    }

    private const val GH_API_RELEASES =
        "https://api.github.com/repos/${UpdateChannels.GH_SLUG}/releases?per_page=30"
    private const val GH_API_LATEST =
        "https://api.github.com/repos/${UpdateChannels.GH_SLUG}/releases/latest"

    /** 静态稳定索引: 最新 Release 资产 (publish.sh 每次发版上传并写入 mirrors 清单), 静态直链无限流 */
    internal const val GH_VERSIONS_URL =
        "https://github.com/${UpdateChannels.GH_SLUG}/releases/latest/download/versions.json"

    /** 静态全通道索引 (稳定+抢先): 随每次发版回填到最新稳定 Release, 抢先通道数据源 */
    internal const val GH_PREVIEW_VERSIONS_URL =
        "https://github.com/${UpdateChannels.GH_SLUG}/releases/latest/download/versions-preview.json"

    private val GH_HEADERS = mapOf(
        "Accept" to "application/vnd.github+json",
        // GitHub API 必带 User-Agent, 否则 403
        "User-Agent" to "ccsut-kb-app",
    )

    // ---- 转换与解析 ----

    /** versions.json 条目 → ReleaseInfo; fileUrl 由调用方按所在通道构造 (镜像前缀 / GitHub 资产直链) */
    private fun parseVersionsEntries(o: JSONObject, urlOf: (name: String, file: String?) -> String?): List<ReleaseInfo> {
        val arr = o.optJSONArray("versions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.getJSONObject(i)
            val name = v.optString("versionName").trim()
            val file = v.optString("file").takeIf { it.isNotBlank() }
            if (name.isBlank()) null else entryToRelease(v, urlOf(name, file))
        }
    }

    /** versions.json → ReleaseInfo 列表 (相对 file 名按 base 拼接; 保留给单测与通用解析) */
    internal fun parseVersionsJson(o: JSONObject, base: String): List<ReleaseInfo> =
        parseVersionsEntries(o) { _, file ->
            file?.let { runCatching { Updater.resolve(base, it) }.getOrNull() }
        }

    /** versions.json 的 "mirrors" 远程镜像清单 (publish.sh 维护, 可不发版增删镜像) */
    internal fun mirrorsOf(o: JSONObject): List<String> {
        val arr = o.optJSONArray("mirrors") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).trim().takeIf { it.startsWith("https://") }
        }
    }

    private fun entryToRelease(v: JSONObject, fileUrl: String?): ReleaseInfo? {
        val name = v.optString("versionName").trim().takeIf { it.isNotBlank() } ?: return null
        val channel = v.optString("channel").takeIf {
            it == Channels.TAG_STABLE || it == Channels.TAG_BETA ||
                it == Channels.TAG_PREVIEW || it == Channels.TAG_DEV
        } ?: Channels.ofVersion(name)
        val sections = v.optJSONArray("sections")?.let { parseSectionsJson(it) }
            ?: v.optJSONArray("notes")?.let { notes ->
                val items = (0 until notes.length()).mapNotNull { k ->
                    notes.optString(k).trim().takeIf { it.isNotBlank() }
                }
                if (items.isEmpty()) null else listOf(NoteSection(null, items))
            }
        return ReleaseInfo(
            versionName = name,
            versionCode = v.optInt("versionCode").takeIf { it > 0 },
            date = v.optString("date"),
            fileUrl = fileUrl,
            bytes = v.optLong("bytes").takeIf { it > 0 },
            sha256 = v.optString("sha256").takeIf { it.isNotBlank() },
            downloadCount = v.optLong("downloads").takeIf { it > 0 },
            channel = channel,
            sections = sections ?: emptyList(),
            force = v.optBoolean("force", false),
        )
    }
    private fun parseSectionsJson(arr: JSONArray): List<NoteSection>? {
        val out = (0 until arr.length()).mapNotNull { i ->
            val s = arr.getJSONObject(i)
        val items = s.optJSONArray("items")?.let { ia ->
            (0 until ia.length()).mapNotNull { k -> ia.optString(k).trim().takeIf { it.isNotBlank() } }
        }.orEmpty()
        // Android 平台 org.json 对显式 null 的 optString 会返回字符串 "null" (JVM 版返回 ""),
        // 必须用 isNull 判空, 否则更新页会把无标题散条画成字面 "null"
        val title = if (s.isNull("title")) null else s.optString("title")
        if (items.isEmpty()) null else NoteSection(title?.trim()?.ifBlank { null }, items)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** GitHub Release JSON → ReleaseInfo; draft 跳过, 通道由 tag 后缀识别 */
    internal fun parseGitHubRelease(o: JSONObject): ReleaseInfo? {
        if (o.optBoolean("draft")) return null
        val name = o.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            .ifBlank { o.optString("name").trim() }
        if (name.isBlank()) return null
        val asset = o.optJSONArray("assets")?.let { arr ->
            (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        }
        return ReleaseInfo(
            versionName = name,
            date = o.optString("published_at").take(10),
            fileUrl = asset?.optString("browser_download_url")?.takeIf { it.startsWith("https://") },
            bytes = asset?.optLong("size")?.takeIf { it > 0 },
            downloadCount = asset?.optLong("download_count")?.takeIf { it > 0 },
            channel = Channels.ofVersion(name),
            sections = parseMarkdownSections(o.optString("body")),
            // 强制更新标记: publish.sh --force 在 Release 正文尾部埋 HTML 注释 (页面不显示, 这里识别)
            force = o.optString("body").contains("<!-- kb-force"),
        )
    }

    /** versions.json 条目的相对文件名 → GitHub Release 资产直链 (资产名 = publish.sh 的 ccsut-kb-{版本}.apk 规则) */
    internal fun ghAssetUrl(versionName: String, file: String?): String {
        val name = file?.takeIf { it.isNotBlank() } ?: "ccsut-kb-$versionName.apk"
        return "https://github.com/${UpdateChannels.GH_SLUG}/releases/download/v$versionName/$name"
    }

    // ---- 版本比较 ----

    /**
     * 版本比较 (含通道后缀): 先比数字主版本, 同主版本再比通道优先级
     * 抢先(0) < 内测(1) < 稳定(2, 含历史裸版本号) < 开发者(3), 同通道比序号。
     * 即 1.3.0-dev.2 > 1.3.0-stable > 1.3.0-beta.2 > 1.3.0-pre.20261013。
     * dev 排最高: 开发者构建不被同主版本的发布版催更, 跨主版本仍按数字比较。
     */
    fun compareVersion(a: String, b: String): Int {
        fun parse(v: String): Triple<List<Int>, Int, Long> {
            val s = v.trim().removePrefix("v").removePrefix("V")
            val m = Regex("^(\\d+(?:\\.\\d+)*)(?:-(beta|pre|stable|dev)\\.?(\\d*))?$").find(s)
            return if (m == null) {
                Triple(s.split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }, 2, 0L)
            } else {
                Triple(
                    m.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 },
                    when (m.groupValues[2]) { "pre" -> 0; "beta" -> 1; "dev" -> 3; else -> 2 },
                    m.groupValues[3].toLongOrNull() ?: 0L,
                )
            }
        }
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until maxOf(pa.first.size, pb.first.size)) {
            val c = pa.first.getOrElse(i) { 0 }.compareTo(pb.first.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        if (pa.second != pb.second) return pa.second.compareTo(pb.second)
        return pa.third.compareTo(pb.third)
    }

    /** 版本名的数字主版本段 ("1.2.3-pre.X" → "1.2.3") */
    private fun mainVersion(v: String): String =
        Regex("^\\d+(?:\\.\\d+)*").find(v.trim().removePrefix("v").removePrefix("V"))?.value ?: "0"

    /** 与当前安装版本比较 (见 [relationTo]); 版本名/版本号来自 BuildVersion (开发者「临时版本」可覆盖) */
    fun relation(r: ReleaseInfo): Int = relationTo(r, BuildVersion.DISPLAY, BuildVersion.EFFECTIVE_CODE)

    /**
     * 与当前安装版本比较: 1=更高(可更新) 0=同一版本 -1=更低(可回退)。
     * 有 versionCode 走 code, 否则走版本名比较; 平局时按「版本号 + 版本类型」细分。
     * **版本号与版本类型必须同时匹配才算当前版本**——同号不同类 (1.0.1-stable vs 1.0.1-beta.1、
     * versionCode 撞号的脏数据) 一律判更低, 安装走回退确认, 不会误标当前版本。
     * 当前为开发者版 (dev) 时: 同主版本的发布条目一律不算更新也不算当前 (本地代码更新), 跨主版本按数字主版本比。
     * internal 仅为单测可注入当前版本 (单测跑在 debug 构建, DISPLAY 恒为 dev)。
     */
    internal fun relationTo(r: ReleaseInfo, currentName: String, currentCode: Int): Int {
        val myChannel = Channels.ofVersion(currentName)
        if (myChannel == Channels.TAG_DEV) {
            val byMain = compareVersion(mainVersion(r.versionName), mainVersion(currentName))
            return when {
                byMain != 0 -> byMain
                r.channel == myChannel -> 0
                else -> -1
            }
        }
        val byCode = if (r.versionCode != null && r.versionCode > 0) r.versionCode.compareTo(currentCode) else null
        return when {
            byCode == null -> {
                val byName = compareVersion(r.versionName, currentName)
                when {
                    byName != 0 -> byName
                    r.channel == myChannel -> 0
                    else -> -1
                }
            }
            byCode != 0 -> byCode
            r.channel == myChannel -> 0
            else -> -1
        }
    }

    // ---- Release 正文 (Markdown) 解析 ----

    /**
     * GitHub Release body (来自 docs/release-notes 的 md 文件) → 小节列表。
     * 规则与 publish.sh 的 notes 提取一致: 跳过文档大标题 / 分隔线, `## 标题` 分段,
     * 引用块 / 列表行去标记后作为条目, 行内 Markdown 标记剥掉。
     */
    internal fun parseMarkdownSections(body: String): List<NoteSection> {
        if (body.isBlank()) return emptyList()
        val sections = mutableListOf<NoteSection>()
        var title: String? = null
        var items = mutableListOf<String>()
        fun flush() {
            if (items.isNotEmpty()) sections.add(NoteSection(title, items))
            items = mutableListOf()
        }
        body.lines().forEach { line0 ->
            val line = line0.trim()
            if (line.isEmpty() || line.all { it == '-' || it == '=' || it == '—' || it == ' ' }) return@forEach
            val heading = Regex("^#{1,6}\\s+(.+)$").find(line)?.groupValues?.get(1)?.trim()
            if (heading != null) {
                if (heading.startsWith("长工课表通")) return@forEach   // 文档大标题, 版本号页面上已显示
                flush()
                title = heading
                return@forEach
            }
            if (line.startsWith("#")) return@forEach                  // 异常井号行, 保守跳过
            if (line.startsWith("<!--")) return@forEach              // HTML 注释 (如强制更新标记), 不入说明条目
            if (line.startsWith("![") && line.contains("](")) return@forEach   // 图片
            var s = line.removePrefix(">").trim()                     // 引用块标记 (release-notes 摘要行)
            if (s.isEmpty()) return@forEach
            s = s.replace(Regex("^([-*+]|\\d+\\.)\\s+"), "")          // 列表标记
            s = cleanInlineMd(s)
            if (s.isBlank()) return@forEach
            items.add(s)
        }
        flush()
        return sections
    }

    private fun cleanInlineMd(s: String): String = s
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")   // [文字](链接) → 文字
        .replace("**", "").replace("__", "").replace("`", "")
        .trim()
}
