package com.ccsut.kb.data

import android.content.Context
import com.ccsut.kb.util.DebugLog
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * GitHub 镜像加速: 前缀式代理, {base} + 完整 github.com / raw.githubusercontent.com 地址。
 *
 * - 内置精选清单 (发布前实测可用); versions.json 的 "mirrors" 字段每次拉取后存本地 ——
 *   镜像站随时可能失效, 远程清单可不发版增删镜像, 不依赖第三方清单项目
 * - 取镜像时并发探测全部候选, 取最快可用者 (谁能先拿回合法 versions.json 谁最快), 缓存 24h
 * - 下载失败时调用方 invalidate, 下次重新测速; 镜像全挂时 mirrorize(null, url) 原样直连兜底
 */
object Mirrors {

    // 内置清单 (2026-10 实测: 均支持 releases/download 资产与 raw, 按当时延迟排序)
    private val BUILTIN = listOf(
        "https://ghproxy.link",
        "https://gh.llkk.cc",
        "https://ghfast.top",
        "https://ghproxy.net",
        "https://gh-proxy.com",
    )

    private const val CACHE_TTL = 24L * 60 * 60 * 1000
    private const val KEY_REMOTE = "mirror_remote"
    private const val KEY_BASE = "mirror_base"
    private const val KEY_AT = "mirror_at"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)

    /** versions.json 里的 "mirrors" 清单, 每次成功拉取后存本地 (远程在前, 优先于内置) */
    fun saveRemote(ctx: Context, bases: List<String>) {
        if (bases.isEmpty()) return
        val arr = JSONArray()
        bases.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_REMOTE, arr.toString()).apply()
    }

    private fun remote(ctx: Context): List<String> = runCatching {
        val raw = prefs(ctx).getString(KEY_REMOTE, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).trim().takeIf { it.startsWith("https://") }
        }
    }.getOrDefault(emptyList())

    /** 全部候选镜像: 远程清单在前 (优先探测), 内置在后, 去重后最多 10 个 */
    fun candidates(ctx: Context): List<String> =
        (remote(ctx) + BUILTIN).distinct().take(10)

    /** 当前缓存的可用镜像; 过期/未测速返回 null (不触发探测, 探测由 [current] 主动做) */
    fun cached(ctx: Context): String? {
        val p = prefs(ctx)
        val base = p.getString(KEY_BASE, null) ?: return null
        val at = p.getLong(KEY_AT, 0)
        return base.takeIf { System.currentTimeMillis() - at < CACHE_TTL }
    }

    /** 已选镜像的 host, 给更新页副标题展示 */
    fun statusText(ctx: Context): String? = cached(ctx)?.let {
        runCatching { URI(it).host }.getOrNull()
    }

    /**
     * 取一个可用镜像: 缓存未过期直接用, 否则并发探测全部候选。
     * 全挂返回 null —— 调用方按直连兜底。阻塞网络调用, 只在 IO 线程用。
     */
    fun current(ctx: Context, pingUrl: String): String? {
        cached(ctx)?.let { return it }
        return probe(ctx, pingUrl)?.also { base ->
            prefs(ctx).edit()
                .putString(KEY_BASE, base)
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply()
        }
    }

    /** 缓存的镜像不可用时清除, 下次 [current] 重新测速 */
    fun invalidate(ctx: Context) {
        prefs(ctx).edit().remove(KEY_BASE).remove(KEY_AT).apply()
    }

    /** 并发探测: 每个候选拉一次 pingUrl, 校验返回确实是 versions.json, 取延迟最低者 */
    private fun probe(ctx: Context, pingUrl: String): String? {
        val targets = candidates(ctx)
        if (targets.isEmpty()) return null
        val best = arrayOfNulls<Pair<String, Long>>(1)   // (base, 延迟ms)
        val lock = Any()
        val latch = CountDownLatch(targets.size)
        targets.forEach { base ->
            thread(isDaemon = true, name = "mirror-probe") {
                val t0 = System.nanoTime()
                try {
                    val body = probeGet("$base/$pingUrl") ?: return@thread
                    if (!body.contains("\"versions\"")) return@thread
                    val cost = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
                    synchronized(lock) {
                        if (best[0] == null || cost < best[0]!!.second) best[0] = base to cost
                    }
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(8, TimeUnit.SECONDS)
        val hit = best[0]
        DebugLog.log("update", "镜像测速: ${hit?.first ?: "全部不可用"}${hit?.let { " · ${it.second}ms" } ?: ""}")
        return hit?.first
    }

    /** 探测专用短超时 GET (连接 4s / 读 6s / 上限 1MB); 失败返回 null */
    private fun probeGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 6_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "ccsut-kb-app")
            try {
                if (conn.responseCode !in 200..299) return null
                val out = java.io.ByteArrayOutputStream()
                conn.inputStream.use { ins ->
                    val buf = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 1024 * 1024) break
                        out.write(buf, 0, n)
                    }
                }
                out.toString("UTF-8")
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 前缀加速: {mirror}/{url}; mirror 为空或 url 已带该前缀时原样返回 (直连兜底) */
    internal fun mirrorize(mirror: String?, url: String): String =
        if (mirror.isNullOrBlank() || url.startsWith(mirror)) url else "$mirror/$url"
}
