package com.ccsut.kb.data

import com.ccsut.kb.util.DebugLog
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
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
    const val ZTS_BASE = "https://zts.ccsut.cn"

    const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 20_000
    private const val MAX_HOPS = 12

    // 日志脱敏: t(JWT)/ticket/execution/username/password 只留前缀与长度, 诊断报告可分享
    private val RE_SECRET_QUERY = Regex("([?&](?:t|ticket|execution|username|password)=)([^&]+)")
    private val RE_SECRET_JSON = Regex("(\"(?:ticket|t|execution|username|password)\"\\s*:\\s*\")([^\"]+)\"")
    private val RE_MAX_AGE_0 = Regex("max-age=0", RegexOption.IGNORE_CASE)
    private val RE_DOMAIN_ATTR = Regex("domain=([^;]+)", RegexOption.IGNORE_CASE)

    private fun maskSecrets(s: String): String =
        RE_SECRET_QUERY.replace(s) { m ->
            val v = m.groupValues[2]
            m.groupValues[1] + v.take(6) + "…(len=" + v.length + ")"
        }.let { out ->
            RE_SECRET_JSON.replace(out) { m ->
                val v = m.groupValues[2]
                m.groupValues[1] + v.take(6) + "…(len=" + v.length + ")\""
            }
        }

    /**
     * 浏览器硬件签名仿制品: aTrust 前端用 RSA 例程生成 "00" 开头的纯十六进制串并长期复用
     * (localStorage deviceId), 服务端按该格式校验; UUID 带连字符会被环境上报拒绝。
     */
    private val hardwareSignature: String by lazy {
        val hex = "0123456789abcdef"
        val rnd = java.security.SecureRandom()
        buildString {
            append("00")
            repeat(62) { append(hex[rnd.nextInt(16)]) }
        }
    }

    // ---------------- cookie jar (内存) ----------------

    private val exact = HashMap<String, HashMap<String, String>>()      // host → cookies
    private val wildcard = HashMap<String, HashMap<String, String>>()   // ".ccsut.cn" → cookies

    fun clearCookies() {
        synchronized(this) {
            exact.clear()
            wildcard.clear()
        }
    }

    private fun cookiesFor(host: String): String {
        // 同名 Cookie 去重: 通配域桶先放, host 精确桶覆盖 —— 重复同名 Cookie 会被网关 400 拒绝
        val merged = LinkedHashMap<String, String>()
        synchronized(this) {
            // 通配桶只发「域后缀确实匹配请求 host」的: 防同会话内 A 域下发的域 cookie 泄漏给 B 域
            for ((domain, m) in wildcard) {
                val d = domain.removePrefix(".")
                if (host == d || host.endsWith(".$d")) m.forEach { (k, v) -> merged[k] = v }
            }
            exact[host]?.forEach { (k, v) -> merged[k] = v }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
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
                    RE_MAX_AGE_0.containsMatchIn(sc)
                val domainAttr = RE_DOMAIN_ATTR.find(sc)?.groupValues?.get(1)?.trim()?.lowercase()
                if (domainAttr != null) {
                    // 显式 Domain= 属性: 统一按域桶存, 发送时按后缀匹配 (RFC 6265);
                    // 与请求 host 无关的域 cookie 直接丢弃, 不给其它域搭车机会
                    val d = domainAttr.removePrefix(".")
                    if (host == d || host.endsWith(".$d")) {
                        val bucket = wildcard.getOrPut(".$d") { HashMap() }
                        if (dead) bucket.remove(name) else bucket[name] = value
                    }
                } else {
                    val bucket = exact.getOrPut(host) { HashMap() }
                    if (dead) bucket.remove(name) else bucket[name] = value
                }
            }
        }
    }

    // ---------------- 底层请求 ----------------

    private fun once(
        method: String,
        urlStr: String,
        body: ByteArray?,
        contentType: String?,
        extraHeaders: Map<String, String>,
    ): Response {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        val host = URL(urlStr).host
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = false
        try {
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/json,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            extraHeaders.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            val cookie = cookiesFor(host)
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
            storeCookies(host, headers)
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

    /**
     * 手动跟随重定向 (POST 302 后转 GET, 与浏览器一致), 返回最终响应; 每跳写 logcat(tag=kb-dev)。
     * aTrust 网关对已建立会话的客户端, 偶尔以「200 + JS/锚点跳转页」代替真 302
     * (浏览器自动跟随而 HttpURLConnection 不会), 这里一并手动跟随。
     */
    fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        followJsRedirects: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        var cur = url
        var curMethod = method
        var curBody: ByteArray? = body
        val originHost = runCatching { URL(url).host }.getOrDefault("")
        repeat(MAX_HOPS) {
            val curHost = runCatching { URL(cur).host }
                .getOrElse { throw CasException(CasException.Kind.NETWORK, "无效的跳转地址") }
            val headersForCurrent = if (curHost.equals(originHost, ignoreCase = true)) {
                extraHeaders
            } else {
                extraHeaders.filterKeys {
                    !it.equals("Origin", true) &&
                        !it.equals("Referer", true) &&
                        !it.equals("x-csrf-token", true) &&
                        !it.equals("X-Requested-With", true)
                }
            }
            val r = runCatching { once(curMethod, cur, curBody, contentType, headersForCurrent) }.getOrElse {
                DebugLog.log("cas", "网络错误 @ ${shortUrl(cur)}: ${it.message}")
                throw CasException(CasException.Kind.NETWORK, "网络错误: ${it.message ?: it.javaClass.simpleName}")
            }
            val loc = if (r.status in 300..399) r.header("Location") else null
            DebugLog.log("cas", "${r.status} $curMethod ${shortUrl(cur)}" +
                (loc?.let { " → ${shortUrl(resolve(cur, it))}" } ?: ""))
            if (loc != null && loc.length > shortUrl(loc).length + 8) {
                // 弹回 Location 的参数结构是关键证据 (t/data/appUrl); 敏感长值打码后记一次
                DebugLog.log("cas", "Location 结构: ${maskSecrets(resolve(cur, loc)).take(400)}")
            }
            if (loc != null) {
                val status = r.status
                cur = resolve(cur, loc)
                // aTrust verify 弹回的 shortcut 页 (…&t=JWT): 浏览器由 shortcut JS 把 t 重写进
                // appUrl 再打一次 verify —— 环境上报已在先, 这一枪才是放行; 这里直接等效跟进
                shortcutVerifyRewrite(cur)?.let { next ->
                    DebugLog.log("cas", "shortcut 弹回(带 t): 改打 verify")
                    cur = next
                }
                if (status == 303 || status == 301 || status == 302) {
                    curMethod = "GET"
                    curBody = null
                }
                return@repeat
            }
            if (followJsRedirects) {
                val js = jsRedirectTarget(r)
                if (js == null || js.replaceFirst("#.*$".toRegex(), "") == cur.replaceFirst("#.*$".toRegex(), "")) {
                    // 无目标 / 目标只是同页 hash 路由(SPA 内部路由, 非真跳转): 停止, 避免死循环
                    return r
                }
                DebugLog.log("cas", "JS 跳转页(${r.body.size}B) → ${shortUrl(js)}")
                cur = js
                curMethod = "GET"
                curBody = null
                return@repeat
            }
            return r
        }
        throw CasException(CasException.Kind.PROTOCOL, "重定向次数过多")
    }

    /**
     * 识别「200 但实际是跳转」的页面, 返回跳转目标 (无则 null)。
     * aTrust 网关已知形态: 先 var locationUrl="…" 再赋值跳转 / window.location= / <meta refresh> / <a href=..>Found</a>。
     */
    private fun jsRedirectTarget(r: Response): String? {
        val t = r.text()
        if (r.body.size > 32 * 1024) return null
        val patterns = listOf(
            // aTrust 网关跳转页: var locationUrl = "https://…verify?t=…" (后文 window.location.href = locationUrl)
            Regex("""var\s+\w*[Ll]ocation\w*\s*=\s*["']([^"']+)["']"""),
            Regex("""location\.replace\(\s*["']([^"']+)["']\s*\)"""),
            Regex("""location(?:\.href)?\s*=\s*["']([^"']+)["']"""),
            Regex("""http-equiv=["']?refresh["']?[^>]*url=([^"'>]+)"""),
            Regex("""<a href=["']([^"']+)["']>Found</a>"""),
        )
        for (p in patterns) {
            val m = p.find(t) ?: continue
            val u = m.groupValues[1].replace("&amp;", "&").trim()
            if (u.startsWith("http") || u.startsWith("/")) return resolve(r.url, u)
        }
        return null
    }

    fun get(url: String): Response = request("GET", url)

    /**
     * 识别 aTrust verify 弹回的 shortcut 页 (dest=#!/login&appUrl=…&t=JWT)。
     * 浏览器 shortcut JS 的 initData: s.t && (s.appUrl = "/controller/v1/public/verify?t=" + s.t),
     * 随后 gotoDest 跳向该 verify —— 这里等效提取 t 直接改打 verify, 不加载 shortcut 页面。
     * 非 shortcut 页 / 无 t 参数返回 null。
     */
    private fun shortcutVerifyRewrite(url: String): String? {
        if (!url.contains("shortcut.html", ignoreCase = true)) return null
        val q = url.substringAfter('?', "")
        val t = q.split('&')
            .firstOrNull { it.startsWith("t=") && it.length > 22 }
            ?.substring(2)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val jwt = runCatching { URLDecoder.decode(t, "UTF-8") }.getOrDefault(t)
        return "$ZTS_BASE/controller/v1/public/verify?t=$jwt"
    }

    private fun resolve(base: String, loc: String): String =
        runCatching { URI(base).resolve(loc.trim()).toString() }.getOrDefault(loc.trim())

    private fun shortUrl(url: String): String =
        url.substringBefore("?").let { u -> if (u.length > 90) u.take(87) + "…" else u }

    /** 识别最终落点页面, 用于错误提示与远程排障 */
    fun diagnose(r: Response): String = diagnose(url = r.url, html = r.text())

    /** 同上, html 已取出的场景 (PersonalRepo 异常文案复用同一套识别, 勿再各写一份) */
    fun diagnose(url: String = "", html: String): String {
        val host = runCatching { URL(url).host }.getOrDefault("?")
        return when {
            html.contains("75500006") || html.contains("当前账号已在线") -> "aTrust 提示账号已在线(等约3分钟)"
            html.contains("sfDomainParam") || html.contains("locationUrl") -> "aTrust 网关跳转页"
            html.contains("flowExecutionKey") -> "CAS 登录页"
            html.contains("账号登录") && html.contains("统一认证") -> "教务登录页(tls 会话未建立)"
            html.contains("app_center") || html.contains("工作台") -> "aTrust 工作台"
            html.contains("xhid") -> "教务课表页"
            html.isBlank() -> "空响应"
            else -> "未知页面($host, ${html.length}B)"
        }
    }

    /**
     * CAS 提交后的 shortcut 页面处理。
     * 只有 shortcut data 明确要求环境校验时才上报 reportEnv；普通 auth_cas 的 data.ticket
     * 只是 shortcut 路由数据，不能当作 reportEnv ticket 直接提交。
     */
    private fun handleShortcut(shortcutUrl: String) {
        val data = runCatching {
            val query = URI(shortcutUrl).rawQuery.orEmpty()
                .split('&')
                .mapNotNull { part ->
                    val p = part.indexOf('=')
                    if (p <= 0) null else URLDecoder.decode(part.substring(0, p), "UTF-8") to
                        URLDecoder.decode(part.substring(p + 1), "UTF-8")
                }
                .toMap()
            JSONObject(query["data"] ?: "")
        }.getOrNull() ?: run {
            DebugLog.log("cas", "shortcut data 不是 JSON，按普通 auth_cas 流程继续")
            return
        }
        DebugLog.log("cas", "shortcut data: ${maskSecrets(data.toString())}")
        val env = data.optJSONObject("env")
        if (env?.optBoolean("need", false) != true) {
            DebugLog.log("cas", "shortcut 无环境校验要求，跳过 reportEnv")
            return
        }
        val ticket = data.optString("ticket").trim()
        if (ticket.isBlank()) {
            throw CasException(CasException.Kind.PROTOCOL, "网关环境票据为空，请重新验证码登录")
        }
        reportBrowserEnv(ticket)
    }

    private fun reportBrowserEnv(ticket: String) {
        // 浏览器 shortcut 流程在 reportEnv 前必先调 authConfig({mod:1}) (checkSkipClientStart):
        // 该调用为「未上线会话」建立服务端上下文, 直接 POST reportEnv 会 403 session not found。
        // csrf 一并取自这次响应的 security.csrfToken。
        val csrf = runCatching {
            val auth = get("$ZTS_BASE/passport/v1/public/authConfig?clientType=SDPBrowserClient&platform=Android&lang=zh-CN&mod=1")
            val data = runCatching { JSONObject(auth.text()).optJSONObject("data") }.getOrNull()
            DebugLog.log("cas", "authConfig(mod=1) ${auth.status} isLogin=${data?.optBoolean("isLogin")}")
            if (auth.status !in 200..299) ""
            else data?.optJSONObject("security")?.optString("csrfToken").orEmpty()
        }.getOrDefault("")
        if (csrf.isBlank()) {
            DebugLog.log("cas", "authConfig(mod=1) 未取得 csrf, 仍按浏览器行为继续上报")
        }
        val deviceId = hardwareSignature
        val payload = JSONObject().apply {
            put("ticket", ticket)
            put("deviceId", deviceId)
            put("env", JSONObject().apply {
                put("endpoint", JSONObject().apply {
                    put("device_id", deviceId)
                    put("device", JSONObject().put("type", "browser"))
                })
            })
        }.toString().toByteArray(Charsets.UTF_8)
        val r = request(
            "POST",
            "$ZTS_BASE/controller/v1/public/reportEnv?clientType=SDPBrowserClient&platform=Android&lang=zh-CN",
            payload,
            "application/json",
            followJsRedirects = false,
            extraHeaders = buildMap {
                put("Origin", ZTS_BASE)
                put("Referer", "$ZTS_BASE/portal/shortcut.html")
                put("X-Requested-With", "XMLHttpRequest")
                put("x-sdp-traceid", UUID.randomUUID().toString())
                if (csrf.isNotBlank()) put("x-csrf-token", csrf)
            },
        )
        val reportBody = r.text()
        val reportCode = runCatching { JSONObject(reportBody).optInt("code", -1) }.getOrDefault(-1)
        DebugLog.log("cas", "reportEnv ${r.status}/$reportCode ${reportBody.take(240)}")
        // 浏览器行为: 上报失败也不阻断, 仍继续 authCheck
        // reportEnv 之后必须 authCheck (mitmproxy 实测): 响应把 sdp_limit_auth_tag 从
        // secondary_auth 翻转为 online 并轮换 sid —— 没有这一步 verify 会无限弹回登录页
        val chk = get("$ZTS_BASE/passport/v1/auth/authCheck?clientType=SDPBrowserClient&platform=Android&lang=zh-CN")
        val chkRoot = runCatching { JSONObject(chk.text()) }.getOrNull()
        val chkCode = chkRoot?.optInt("code", -1) ?: -1
        val chkData = chkRoot?.optJSONObject("data")
        DebugLog.log(
            "cas",
            "authCheck ${chk.status}/$chkCode " +
                "isOnline=${chkData?.optJSONObject("onlineInfo")?.optBoolean("isOnline")} " +
                "tag→online 见 Set-Cookie",
        )
    }


    /**
     * 打开 CAS 登录页。
     * 返回 flowExecutionKey = 需要短信登录;
     * 返回 null = CAS 会话仍有效(直接带票跳去了 service), aTrust 会话已顺带建立, 可免短信直接拉课表。
     */
    fun openLoginPage(): String? {
        // followJsRedirects=false: 只跟 HTTP 302 (CAS→auth/cas→shortcut 全程 302),
        // 停在 shortcut 页本身 —— 否则 JS 跟进会把 r.url 带离 shortcut, 下面的处理永远不触发
        val r = request("GET", "$CAS_LOGIN_URL?service=${URLEncoder.encode(CAS_SERVICE, "UTF-8")}", followJsRedirects = false)
        val host = runCatching { URL(r.url).host }.getOrDefault("")
        val html = r.text()
        if (html.contains("75500006") || html.contains("当前账号已在线")) {
            throw CasException(CasException.Kind.ALREADY_ONLINE, "账号可能刚在别处登录或退出，请等约 3 分钟再试")
        }
        if (host != CAS_HOST) {
            DebugLog.log("cas", "openLoginPage: CAS 会话有效, 已自动放行到 $host (${diagnose(r)})")
            // 免短信路径同样会落在 shortcut 页: 补 gateway 要求的环境上报 (env.need=true 时)
            if (host == "zts.ccsut.cn" && r.url.contains("/shortcut.html", ignoreCase = true)) {
                handleShortcut(r.url)
            }
            return null
        }
        val m = Regex("flowExecutionKey\\s*:\\s*\"([^\"]+)\"").find(html)
            ?: throw CasException(
                CasException.Kind.PROTOCOL,
                "登录页加载异常(${diagnose(r)})，请稍后再试",
            )
        return m.groupValues[1]
    }

    /** 发送短信验证码, 返回服务端提示文案 */
    fun sendSms(account: String): String {
        val url = "$CAS_BASE/auth/verificationCode/sendCode" +
            "?username=${URLEncoder.encode(account, "UTF-8")}&domain=$CAS_HOST"
        val r = request("POST", url, "{}".toByteArray(), "application/json")
        val body = r.text()
        val msg = runCatching {
            val root = JSONObject(body)
            root.optString("message").ifBlank {
                root.optJSONObject("error")?.optString("message").orEmpty()
            }
        }.getOrDefault("")
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
        // followJsRedirects=false: 落点判定只看「是否离开 auth 域」, 跟进门户 SPA 反而模糊结论
        val r = request("POST", CAS_LOGIN_URL, body, followJsRedirects = false)
        val host = runCatching { URL(r.url).host }.getOrDefault("")
        val finalHtml = r.text()
        DebugLog.log("cas", "submitCode 最终落点: $host (${diagnose(r)})")
        if (finalHtml.contains("75500006") || finalHtml.contains("当前账号已在线")) {
            throw CasException(CasException.Kind.ALREADY_ONLINE, "账号可能刚在别处登录或退出，请等约 3 分钟再试")
        }
        if (host == CAS_HOST) {
            // 原地渲染 = 登录失败 (验证码错误/过期等)
            val m = Regex("验证码[^<]{0,12}(错误|失效|不正确)|登录失败|已失效").find(finalHtml)
            throw CasException(CasException.Kind.BAD_CODE, m?.value ?: "验证码不正确或已过期，请重试")
        }
        if (host == "zts.ccsut.cn" &&
            r.url.contains("/shortcut.html", ignoreCase = true)
        ) {
            handleShortcut(r.url)
        }
    }

    /** 用已建立的会话进入教务系统: 整条重定向链 + getXlzc 真实验证, 失败自动再试一轮 */
    fun establishTls() {
        var last: Response? = null
        repeat(2) { attempt ->
            val r = get("$TLS_BASE/admin/caslogin")
            DebugLog.log("cas", "establishTls#$attempt 落点: ${shortUrl(r.url)} (${diagnose(r)}) head=${maskSecrets(r.text().take(400))}")
            last = r
            if (isTlsAlive()) return
        }
        // 显式判空而非 last!!: 防未来 repeat 改成条件循环时 last 未赋值直接 NPE
        val r = last ?: throw CasException(CasException.Kind.NOT_LOGGED_IN, "教务登录未完成，请重新验证码登录")
        throw CasException(
            CasException.Kind.NOT_LOGGED_IN,
            "教务登录未完成（${diagnose(r)}），请重新验证码登录",
        )
    }

    /** 教务会话是否存活 (getXlzc 快速探测) */
    fun isTlsAlive(): Boolean = runCatching {
        val r = get("$TLS_BASE/admin/api/getXlzc")
        r.status in 200..299 && runCatching {
            JSONObject(r.text()).optInt("ret", -1) == 0
        }.getOrDefault(false)
    }.getOrDefault(false)
}
