package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 更新清单, 对应 latest.json (自定义源 / GitHub 主仓 data/)。
 * 数据集段必填; apk 段由 scripts/publish.sh 发版时注入。
 */
data class Manifest(
    val version: Int,
    val xnxq: String,
    val file: String,
    val sha256: String,
    val bytes: Long,
    val generatedAt: String,
    val apkVersionCode: Int?,
    val apkFile: String?,
    val apkSha256: String?,
    val apkVersionName: String? = null,
    val apkBytes: Long? = null,
    val apkNotes: List<String> = emptyList(),
)

object Updater {

    /** 纯网络+解析, 不碰 UI: 拉当前源的课表数据清单 (GitHub 主仓 raw, 镜像模式下经镜像代理); 网络失败返回 null */
    fun fetchManifest(ctx: Context): Manifest? {
        return runCatching { fetchManifestAt(UpdateChannels.dataManifestUrl(ctx)) }.getOrElse {
            com.ccsut.kb.util.DebugLog.log("update", "清单拉取失败: ${it.message}")
            null
        }
    }

    internal fun fetchManifestAt(url: String): Manifest =
        parseManifest(JSONObject(String(httpGet(url), Charsets.UTF_8)))

    /** 开发者模式: 拉取原始清单文本 */
    fun fetchRawManifest(ctx: Context): String? =
        String(httpGet(UpdateChannels.dataManifestUrl(ctx)), Charsets.UTF_8)

    /** 下载数据包并校验 sha256, 成功则落盘并热切换 */
    fun applyDataUpdate(ctx: Context, m: Manifest): Dataset? {
        val base = UpdateChannels.dataManifestUrl(ctx).substringBeforeLast('/')
        val bytes = runCatching { httpGet(resolve(base, m.file)) }.getOrElse {
            com.ccsut.kb.util.DebugLog.log("update", "数据包下载失败: ${it.message}")
            return null
        }
        if (m.sha256.isNotBlank() && sha256Hex(bytes) != m.sha256) return null
        return Repo.applyUpdate(ctx, bytes)
    }

    /** 下载目录里的版本化文件名: 通道+版本 (稳定/内测/抢先各存各的, 同版本号跨通道互不覆盖; 只保留文件名安全字符) */
    private fun apkFileName(channel: String, versionName: String): String {
        val safe = { s: String -> s.replace(Regex("[^A-Za-z0-9._-]"), "_") }
        return "ccsut-kb-${safe(channel)}-${safe(versionName)}.apk"
    }

    /**
     * 查下载目录里是否已有该通道该版本的 APK: 存在且 (清单给了校验值时) sha256 匹配才算数。
     * 命中即免下载直接安装; 不匹配 (半截包/换源包) 删掉让下次重新下载。
     * 旧版固定名 update.apk 无法区分版本, 顺手清掉。
     */
    fun cachedApk(ctx: Context, channel: String, versionName: String, sha256: String? = null): File? {
        val dir = File(ctx.cacheDir, "apk")
        File(dir, "update.apk").delete()
        val f = File(dir, apkFileName(channel, versionName))
        if (!f.isFile) return null
        if (sha256.isNullOrBlank()) return f
        val hex = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: return null
        if (hex == sha256) return f
        f.delete()
        return null
    }

    /**
     * 下载 APK 到 cache/apk/ccsut-kb-{通道}-{versionName}.apk (url 为清单/发布页解析出的绝对 https 直链),
     * 流式写盘+边读边算 sha256 (不整包进内存), 原子落盘; onProgress(doneBytes, totalBytes) 在 IO 线程回调。
     * 按通道+版本命名落盘, 配合 [cachedApk] 实现「下过一次, 重开页面直接安装」。
     */
    /** allowHttp: 仅自定义 http 调试源放行明文下载 (调用方按源类型传入), 其余场景仍强制 https */
    fun downloadApk(ctx: Context, channel: String, versionName: String, url: String, sha256: String? = null, allowHttp: Boolean = false, onProgress: ((Long, Long) -> Unit)? = null): File? {
        if (!url.startsWith("https://") && !(allowHttp && url.startsWith("http://"))) {
            com.ccsut.kb.util.DebugLog.log("update", "拒绝非 http(s) 下载地址: $url")
            return null
        }
        val dir = File(ctx.cacheDir, "apk")
        dir.mkdirs()
        val dest = File(dir, apkFileName(channel, versionName))
        val part = File(dir, dest.name + ".part")
        val ok = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val md = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { ins ->
                    part.outputStream().use { outs ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > 200L * 1024 * 1024) error("文件过大")
                            outs.write(buf, 0, n)
                            md.update(buf, 0, n)
                            if (onProgress != null && total > 0) onProgress(done, total)
                        }
                    }
                }
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (!sha256.isNullOrBlank() && hex != sha256) error("sha256 校验不符")
            } finally {
                conn.disconnect()
            }
            true
        }.getOrElse {
            com.ccsut.kb.util.DebugLog.log("update", "APK 下载失败: ${it.message}")
            part.delete()
            false
        }
        if (!ok) return null
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) return null
        return dest
    }

    /**
     * 启动清理: 删除不比当前安装版本新的缓存安装包。
     * 装完的包与过期的旧包留在 cache/apk 里, 会让版本页把旧版本误亮成「立即安装」;
     * 这里按文件名里的版本与当前安装版本比较, <= 当前的一律删 (当前版本的重装可重新下载)。
     */
    fun pruneApks(ctx: Context, currentVersionName: String) {
        val dir = File(ctx.cacheDir, "apk")
        for (f in dir.listFiles() ?: return) {
            // 文件名规则见 [apkFileName]: ccsut-kb-{通道}-{版本名}.apk; 版本段以数字开头,
            // 贪婪回溯后通道/版本边界唯一 (版本名自身可含 -beta.N 等后缀)
            val m = Regex("^ccsut-kb-[A-Za-z0-9._-]+-([0-9][A-Za-z0-9._-]*)\\.apk$").find(f.name)
                ?: continue
            val version = m.groupValues[1]
            if (Releases.compareVersion(version, currentVersionName) <= 0) {
                f.delete()
                com.ccsut.kb.util.DebugLog.log("update", "清理过期安装包: ${f.name}")
            }
        }
    }

    // ---- 工具 ----

    /** internal 仅为单测可及; 清单字段缺失时给默认值, 不抛格式异常 (格式异常由调用方兜) */
    internal fun parseManifest(o: JSONObject): Manifest {
        val apk = o.optJSONObject("apk")
        val notes = apk?.optJSONArray("notes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).trim().takeIf { it.isNotBlank() }
            }
        }
        return Manifest(
            version = o.optInt("version", 0),
            xnxq = o.optString("xnxq"),
            file = o.optString("file"),
            sha256 = o.optString("sha256"),
            bytes = o.optLong("bytes", 0),
            generatedAt = o.optString("generatedAt"),
            apkVersionCode = apk?.optInt("versionCode")?.takeIf { it > 0 },
            apkFile = apk?.optString("file")?.takeIf { it.isNotBlank() },
            apkSha256 = apk?.optString("sha256")?.takeIf { it.isNotBlank() },
            apkVersionName = apk?.optString("versionName")?.takeIf { it.isNotBlank() },
            apkBytes = apk?.optLong("bytes")?.takeIf { it > 0 },
            apkNotes = notes ?: emptyList(),
        )
    }

    /** 清单相对地址 → 绝对地址; https 源拒绝绝对 http 文件地址 (防清单被改后降级下载),
     *  自定义 http 调试源 (base 本身是 http) 则整体放行明文 */
    internal fun resolve(base: String, file: String): String =
        when {
            file.startsWith("https://") -> file
            file.startsWith("http://") ->
                if (base.startsWith("http://")) file else error("拒绝明文 http 下载地址: $file")
            else -> "$base/$file"
        }

    /** internal 供 Releases 复用 (GitHub API 需要自定义请求头) */
    internal fun httpGet(
        url: String,
        maxBytes: Long = 64L * 1024 * 1024,
        headers: Map<String, String> = emptyMap(),
        onProgress: ((Long, Long) -> Unit)? = null,
    ): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { ins ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    done += n
                    if (done > maxBytes) error("文件过大")
                    out.write(buf, 0, n)
                    if (onProgress != null && total > 0) onProgress(done, total)
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/**
 * 编译期版本号 (与 build.gradle 双写, checkVersionSync 校验), 独立对象便于测试。
 * 版本类体现在版本号后缀 (全英文): 稳定 X.Y.Z-stable / 抢先 X.Y.Z-pre.YYYYMMDD /
 * 内测 X.Y.Z-beta.N 由发版时的 NAME 携带; 开发者版 X.Y.Z-dev 由 debug 构建自动挂载。
 */
object BuildVersion {
    const val CODE = 102005002
    const val NAME = "1.2.0-beta.2"

    /** DevSheet「临时版本」覆盖: 只影响更新比较与展示, 仅本进程有效, 重启或「还原」即失效 */
    var displayOverride: String? = null
    var codeOverride: Int? = null

    /** 展示与版本比较用: release 构建原样用 NAME, debug 构建 (开发者版) 自动挂 -dev; 可被临时版本覆盖 */
    val DISPLAY: String get() = displayOverride ?: if (BuildConfig.DEBUG) "$NAME-dev" else NAME

    /** 更新比较用的版本号 (受临时版本覆盖; 编译期常量之外的唯一可变口子) */
    val EFFECTIVE_CODE: Int get() = codeOverride ?: CODE
}
