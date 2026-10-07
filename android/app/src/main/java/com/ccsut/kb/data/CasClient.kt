package com.ccsut.kb.data

import com.ccsut.kb.util.DebugLog
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * 教务登录 HTTP 客户端 (纯 HttpURLConnection, 无第三方依赖)。
 *
 * 要点 (全链路见 docs/api-personal.md):
 * - 自建内存 cookie jar: 按 host 存取; 带 Domain=.ccsut.cn 的 Cookie 进通配桶, *.ccsut.cn 共享;
 * - instanceFollowRedirects=false, 跨域 302 逐跳手跟, 每跳都可能发 Set-Cookie;
 * - Cookie 只存内存 (进程结束即清, 不落盘), 手机号/学号这类非敏感预填值才进 Prefs。
 */
object CasClient {

    /** CAS 登录异常, kind 决定 UI 提示 */
    class CasException(val kind: Kind, message: String) : Exception(message) {
        enum class Kind { ALREADY_ONLINE, BAD_CODE, NOT_LOGGED_IN, PROTOCOL, NETWORK }
    }

    data class Response(
        val url: String,
        val status: Int,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    ) {
        fun text(): String = String(body, Charsets.UTF_8)
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
        fun containsAny(vararg marks: String): Boolean {
            val t = text()
            return marks.any { t.contains(it) }
        }
    }

    const val CAS_BASE = "https://auth.ccsut.cn/backstage"
    const val CAS_LOGIN_URL = "$CAS_BASE/cas/login"
    /** aTrust 的 CAS service 地址 (sfDomain 是学校在网关侧的应用标识) */
    const val CAS_SERVICE = "https://zts.ccsut.cn:443/passport/v1/auth/cas?sfDomain=cas88719"
    const val CAS_HOST = "auth.ccsut.cn"
    const val TLS_BASE = "https://tls.ccsut.cn"

    const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 20_000
    private const val MAX_HOPS = 12

    // ---------------- cookie jar (内存) ----------------

    private val exact = HashMap<String, HashMap<String, String>>()      // host → cookies
    private val wildcard = HashMap<String, HashMap<String, String>>()   // ".ccsut.cn" → cookies

    fun clearCookies() {
        exact.clear()
        wildcard.clear()
    }

    private fun cookiesFor(host: String): String {
        val parts = ArrayList<String>()
        synchronized(this) {
            for ((d, m) in wildcard) {
                if (host == d.removePrefix(".") || host.endsWith(d)) {
                    m.forEach { (k, v) -> parts += "$k=$v" }
                }
            }
            exact[host]?.forEach { (k, v) -> parts += "$k=$v" }
        }
        return parts.joinToString("; ")
    }

    private fun storeCookies(host: String, headers: Map<String, List<String>>) {
        val setCookies = headers.entries
            .filter { it.key.equals("Set-Cookie", true) }
            .flatMap { it.value }
        synchronized(this) {
            for (sc in setCookies) {
                val first = sc.split(";").firstOrNull()?.trim() ?: continue
                val eq = first.indexOf('=')
                if (eq <= 0) continue
                val name = first.substring(0, eq).trim()
                val value = first.substring(eq + 1).trim()
                val lower = sc.lowercase()
                val dead = value.isEmpty() || "expires=thu, 01 jan 1970" in lower ||
                    Regex("max-age=0", RegexOption.IGNORE_CASE).containsMatchIn(sc)
                val domainAttr = Regex("domain=([^;]+)", RegexOption.IGNORE_CASE)
                    .find(sc)?.groupValues?.get(1)?.trim()?.lowercase()
                if (domainAttr != null && domainAttr.startsWith(".")) {
                    val bucket = wildcard.getOrPut(domainAttr) { HashMap() }
                    if (dead) bucket.remove(name) else bucket[name] = value
                } else {
                    val bucket = exact.getOrPut(domainAttr ?: host) { HashMap() }
                    if (dead) bucket.remove(name) else bucket[name] = value
                }
            }
        }
    }

    // ---------------- 底层请求 ----------------

    private fun once(method: String, urlStr: String, body: ByteArray?, contentType: String?): Response {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = false
        try {
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/json,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            val cookie = cookiesFor(URL(urlStr).host)
            if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie)
            if (body != null) {
                conn.doOutput = true
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val headers = conn.headerFields ?: emptyMap()
            storeCookies(URL(urlStr).host, headers)
            val stream: InputStream? = if (status in 200..399) conn.inputStream else conn.errorStream
            val bytes = stream?.use { readAll(it) } ?: ByteArray(0)
            return Response(urlStr, status, headers, bytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(ins: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > 16L * 1024 * 1024) error("响应过大")
        }
        return out.toByteArray()
    }

    /** 手动跟随重定向 (POST 302 后转 GET, 与浏览器一致), 返回最终响应 */
    fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): Response {
        var cur = url
        var curMethod = method
        var curBody: ByteArray? = body
        repeat(MAX_HOPS) {
            val r = runCatching { once(curMethod, cur, curBody, contentType) }.getOrElse {
                DebugLog.log("cas", "网络错误 @ $cur: ${it.message}")
                throw CasException(CasException.Kind.NETWORK, "网络错误: ${it.message ?: it.javaClass.simpleName}")
            }
            if (r.status in 300..399) {
                val loc = r.header("Location") ?: return r
                cur = resolve(cur, loc)
                if (curMethod != "GET") {
                    curMethod = "GET"
                    curBody = null
                }
                return@repeat
            }
            return r
        }
        throw CasException(CasException.Kind.PROTOCOL, "重定向次数过多")
    }

    fun get(url: String): Response = request("GET", url)

    private fun resolve(base: String, loc: String): String =
        runCatching { URI(base).resolve(loc.trim()).toString() }.getOrDefault(loc.trim())

    // ---------------- CAS 短信登录 ----------------

    /** 打开 CAS 登录页, 返回 flowExecutionKey; 账号已在别处在线(75500006)时抛 ALREADY_ONLINE */
    fun openLoginPage(): String {
        val r = get("$CAS_LOGIN_URL?service=${URLEncoder.encode(CAS_SERVICE, "UTF-8")}")
        val html = r.text()
        if (html.contains("75500006") || html.contains("当前账号已在线")) {
            throw CasException(CasException.Kind.ALREADY_ONLINE, "账号可能刚在别处登录或退出，请等约 3 分钟再试")
        }
        val m = Regex("flowExecutionKey\\s*:\\s*\"([^\"]+)\"").find(html)
            ?: throw CasException(CasException.Kind.PROTOCOL, "登录页加载异常，请稍后再试")
        return m.groupValues[1]
    }

    /** 发送短信验证码, 返回服务端提示文案 */
    fun sendSms(account: String): String {
        val url = "$CAS_BASE/auth/verificationCode/sendCode" +
            "?username=${URLEncoder.encode(account, "UTF-8")}&domain=$CAS_HOST"
        val r = request("POST", url, "{}".toByteArray(), "application/json")
        val body = r.text()
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        if (r.status !in 200..299) {
            throw CasException(CasException.Kind.PROTOCOL, msg.ifBlank { "验证码发送失败 (HTTP ${r.status})" })
        }
        return msg.ifBlank { "验证码已发送" }
    }

    /**
     * 提交验证码完成登录 (经典表单 POST; 短信验证码走魔法串 phone_msg###<code>, 不做 AES 加密)。
     * 成功 = 重定向链离开 auth.ccsut.cn (落到 aTrust); 失败 = 原地重渲染登录页。
     */
    fun submitCode(account: String, code: String, execution: String) {
        val form = linkedMapOf(
            "username" to account,
            // phone_msg + ### + 验证码: 服务端识别该前缀走短信校验分支
            "password" to "phone_msg###$code",
            "execution" to execution,
            "_eventId" to "submit",
            "geolocation" to "",
            "captcha" to "",
            "rememberMe" to "false",
            "domain" to CAS_HOST,
            "tenantId" to "",
            "validateCode" to "",
        )
        val body = form.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }.toByteArray()
        val r = request("POST", CAS_LOGIN_URL, body)
        val host = runCatching { URL(r.url).host }.getOrDefault("")
        val finalHtml = r.text()
        if (finalHtml.contains("75500006") || finalHtml.contains("当前账号已在线")) {
            throw CasException(CasException.Kind.ALREADY_ONLINE, "账号可能刚在别处登录或退出，请等约 3 分钟再试")
        }
        if (host == CAS_HOST) {
            // 原地渲染 = 登录失败 (验证码错误/过期等)
            val m = Regex("验证码[^<]{0,12}(错误|失效|不正确)|登录失败|已失效").find(finalHtml)
            throw CasException(CasException.Kind.BAD_CODE, m?.value ?: "验证码不正确或已过期，请重试")
        }
    }

    /** 用已建立的 aTrust 会话进入教务系统 (GET /admin/caslogin 整条重定向链) */
    fun establishTls() {
        val r = get("$TLS_BASE/admin/caslogin")
        val host = runCatching { URL(r.url).host }.getOrDefault("")
        if (host != "tls.ccsut.cn" || r.status !in 200..299) {
            throw CasException(CasException.Kind.NOT_LOGGED_IN, "教务系统登录未完成，请重新登录")
        }
    }

    /** 教务会话是否存活 (getXlzc 快速探测) */
    fun isTlsAlive(): Boolean = runCatching {
        val r = get("$TLS_BASE/admin/api/getXlzc")
        r.status in 200..299 && runCatching {
            JSONObject(r.text()).optInt("ret", -1) == 0
        }.getOrDefault(false)
    }.getOrDefault(false)
}
