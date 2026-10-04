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
        val bytes = httpGet(resolve(base, m.file))
        if (m.sha256.isNotBlank() && sha256Hex(bytes) != m.sha256) return null
        return Repo.applyUpdate(ctx, bytes)
    }

    /** 下载 APK 到 cache/apk/, 返回文件 */
    fun downloadApk(ctx: Context, m: Manifest): File? {
        val base = manifestBase(manifestUrl(ctx))
        val dest = File(ctx.cacheDir, "apk/update.apk")
        dest.parentFile?.mkdirs()
        val bytes = runCatching { httpGet(resolve(base, m.apkFile ?: return null), maxBytes = 200L * 1024 * 1024) }
            .getOrNull() ?: return null
        if (m.apkSha256?.isNotBlank() == true && sha256Hex(bytes) != m.apkSha256) return null
        dest.writeBytes(bytes)
        return dest
    }

    // ---------------------------------------------------------------- 工具

    private fun parseManifest(o: JSONObject) = Manifest(
        version = o.optInt("version", 0),
        xnxq = o.optString("xnxq"),
        file = o.optString("file"),
        sha256 = o.optString("sha256"),
        bytes = o.optLong("bytes", 0),
        generatedAt = o.optString("generatedAt"),
        apkVersionCode = o.optJSONObject("apk")?.optInt("versionCode"),
        apkFile = o.optJSONObject("apk")?.optString("file"),
        apkSha256 = o.optJSONObject("apk")?.optString("sha256"),
    )

    private fun manifestBase(manifestUrl: String) =
        manifestUrl.substringBeforeLast('/')

    private fun resolve(base: String, file: String): String =
        if (file.startsWith("http")) file else "$base/$file"

    private fun httpGet(url: String, maxBytes: Long = 64L * 1024 * 1024): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { ins ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) error("文件过大")
                    out.write(buf, 0, n)
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
    const val CODE = 12
    const val NAME = "2.4.0"
}
