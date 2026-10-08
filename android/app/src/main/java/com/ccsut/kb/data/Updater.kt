package com.ccsut.kb.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 更新清单, 对应托管在 Gitee 发布仓 (yisanspce/ccsut-kb-release) 的 latest.json */
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

sealed class CheckResult {
    data object Disabled : CheckResult()
    data class Error(val msg: String) : CheckResult()
    data object UpToDate : CheckResult()
    data class DataUpdate(val m: Manifest) : CheckResult()
    data class ApkUpdate(val m: Manifest) : CheckResult()
}

object Updater {

    // Gitee 发布仓 raw 直链: 清单+数据包在 master 分支, APK 走 Release 附件绝对 URL
    const val DEFAULT_URL = "https://gitee.com/yisanspce/ccsut-kb-release/raw/master/latest.json"

    fun manifestUrl(ctx: Context): String =
        ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)
            .getString("update_url", DEFAULT_URL) ?: DEFAULT_URL

    fun setManifestUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)
            .edit().putString("update_url", url.trim()).apply()
    }

    /** 纯网络+校验, 不碰 UI */
    fun check(ctx: Context): CheckResult {
        val url = manifestUrl(ctx)
        if (url.isBlank()) return CheckResult.Disabled
        val body = runCatching { httpGet(url) }.getOrElse {
            com.ccsut.kb.util.DebugLog.log("update", "check 网络错误: ${it.message}")
            return CheckResult.Error("网络错误: ${it.message ?: it.javaClass.simpleName}")
        }
        val m = runCatching { parseManifest(JSONObject(String(body, Charsets.UTF_8))) }.getOrElse {
            com.ccsut.kb.util.DebugLog.log("update", "清单格式错误")
            return CheckResult.Error("清单格式错误")
        }
        val cur = Repo.dataset?.version ?: 0
        val r = when {
            m.apkVersionCode != null && m.apkVersionCode > BuildVersion.CODE -> CheckResult.ApkUpdate(m)
            m.version > cur -> CheckResult.DataUpdate(m)
            else -> CheckResult.UpToDate
        }
        com.ccsut.kb.util.DebugLog.log(
            "update",
            "check: 远端 v${m.version} apk=${m.apkVersionCode ?: "-"} 本地 v$cur → " + when (r) {
                is CheckResult.ApkUpdate -> "APK 更新"
                is CheckResult.DataUpdate -> "数据更新"
                is CheckResult.UpToDate -> "已是最新"
                else -> r.javaClass.simpleName
            },
        )
        return r
    }

    /** 开发者模式: 拉取原始清单文本 */
    fun fetchRawManifest(ctx: Context): String? {
        val url = manifestUrl(ctx)
        if (url.isBlank()) return null
        return String(httpGet(url), Charsets.UTF_8)
    }

    /** 下载数据包并校验 sha256, 成功则落盘并热切换 */
    fun applyDataUpdate(ctx: Context, m: Manifest): Dataset? {
        val base = manifestBase(manifestUrl(ctx))
        val bytes = runCatching { httpGet(resolve(base, m.file)) }.getOrElse {
            com.ccsut.kb.util.DebugLog.log("update", "数据包下载失败: ${it.message}")
            return null
        }
        if (m.sha256.isNotBlank() && sha256Hex(bytes) != m.sha256) return null
        return Repo.applyUpdate(ctx, bytes)
    }

    /** 下载 APK 到 cache/apk/, 流式写盘+边读边算 sha256 (不整包进内存), 原子落盘; onProgress(doneBytes, totalBytes) 在 IO 线程回调 */
    fun downloadApk(ctx: Context, m: Manifest, onProgress: ((Long, Long) -> Unit)? = null): File? {
        val base = manifestBase(manifestUrl(ctx))
        val dir = File(ctx.cacheDir, "apk")
        dir.mkdirs()
        val part = File(dir, "update.apk.part")
        val dest = File(dir, "update.apk")
        val ok = runCatching {
            val conn = URL(resolve(base, m.apkFile ?: return null)).openConnection() as HttpURLConnection
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
                if (m.apkSha256?.isNotBlank() == true && hex != m.apkSha256) error("sha256 校验不符")
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

    // ---------------------------------------------------------------- 工具

    private fun parseManifest(o: JSONObject): Manifest {
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

    private fun manifestBase(manifestUrl: String) =
        manifestUrl.substringBeforeLast('/')

    private fun resolve(base: String, file: String): String =
        if (file.startsWith("http")) file else "$base/$file"

    private fun httpGet(url: String, maxBytes: Long = 64L * 1024 * 1024, onProgress: ((Long, Long) -> Unit)? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
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

/** 编译期版本号, 独立对象便于测试与避免 BuildConfig 依赖 */
object BuildVersion {
    const val CODE = 24
    const val NAME = "2.9.5"
}
