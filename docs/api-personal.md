# 长沙工业学院 · 教务登录链路与个人课表接口

> 班级课表（离线抓取）见 [api.md](api.md)；本文档是 **App 内登录教务账号并拉取个人课表** 的全链路逆向笔记。
> 2026-10 抓包 + 静态分析产出（抓包样本存 `/tmp/atrust/`：`sdpkkbList.json` 94 条、`getZclistByXnxq.json`、`queryKbForXsd.html`、`cas_login.html`、`ng_main.js`）。

## 0. 拓扑与会话模型

```
App ──▶ auth.ccsut.cn  CAS 统一身份认证（Angular SPA "ng-login"，产 ticket）
    ──▶ zts.ccsut.cn   深信服 aTrust 零信任网关（验 ticket → 建 aTrust 会话）
    ──▶ tls.ccsut.cn   超星综合教务（经 aTrust 网关放行 → 自身再用 CAS 登录）
```

- **单会话策略**：同一账号只允许一个 aTrust 在线会话。已在线时访问 CAS 登录页会被
  直接重定向到错误页，标记 **`75500006`「当前账号已在线，无需重复上线」**。
- **3 分钟残留**：浏览器/会话关闭后，服务器侧在线状态还保留约 3 分钟，期间重登报 75500006。
  → App 设计为「登录成功立即拉全量 → 存本地缓存；刷新必须重新验证码登录」。
- **Cookie 三个域各自独立**：`auth.ccsut.cn`（CAS TGT）、`zts.ccsut.cn`（aTrust：`language`、`online`）、
  `tls.ccsut.cn`（教务：`route`、`puid`、`username`、`jw_uf`、`jw_uf_u`、`twiceAuthSign`、`defaultPass`…）。
  Cookie 只存内存，不落盘。
- App 端用裸 `HttpURLConnection` + **手动跟随跨域重定向**（逐跳收 Set-Cookie），UA 与浏览器一致即可。

## 1. 短信登录链路（CAS）

### 1.1 发送验证码

```
POST https://auth.ccsut.cn/backstage/auth/verificationCode/sendCode?username=<学号或手机号>&domain=auth.ccsut.cn
Content-Type: application/json
Body: {}
```

- `domain` 固定传当前 CAS 域名 `auth.ccsut.cn`（网页版取 `location.hostname`）。
- 成功响应：`{"code":0,"message":"...","data":...,"traceId":"uuid"}`（前端只展示 `message`）。
  失败时 HTTP 层非 2xx、body 为 `{"error":{"message":...}}` 风格，同样取 `message` 展示。
- 前端倒计时 60s（`time=0x3c`）。**注意**：这一步不需要 execution key、不需要图形验证码。

### 1.2 提交登录（经典表单，不是 JSON！）

SPA 里 `submitForm()` 把密码拼成魔法串 `phone_msg###<验证码>`（以 `phone_msg` 开头且含 `###` 的密码
**不做 AES 加密**、原样提交），然后**创建一个 HTML `<form>` 提交**（即 `application/x-www-form-urlencoded` 的整页 POST）：

```
POST https://auth.ccsut.cn/backstage/cas/login        （登录页相对路径 ./login）
Content-Type: application/x-www-form-urlencoded

username=<学号或手机号>
password=phone_msg###<6位验证码>
execution=<flowExecutionKey>
_eventId=submit
geolocation=
captcha=
rememberMe=false
domain=auth.ccsut.cn
tenantId=
validateCode=
```

- `execution` 来自**登录页 HTML 内嵌**的 `bridgeData`：
  `var bridgeData = { flowExecutionKey: "<uuid>_<base64JWT>" }`，正则
  `flowExecutionKey:\s*"([^"]+)"` 提取。每次打开登录页都会变。
- `tenantId` 本校为空；`validateCode`/`captcha` 留空；`rememberMe` 传 `false`。
- 登录成功：**302 → `service` 地址带 ticket** → zts 验票后进入 `/portal/shortcut.html`。普通 `auth_cas` 跳转的 `data` 可能只有 `ticket`，此时 shortcut 会按普通服务入口继续路由；只有 `data.env.need=true` 且同时存在 `data.ticket` 时，才执行浏览器环境上报。
- 浏览器环境上报（仅 `data.env.need=true` 时适用）：

```text
POST https://zts.ccsut.cn/controller/v1/public/reportEnv
Content-Type: application/json
x-csrf-token: <GET /passport/v1/public/authConfig → data.security.csrfToken>
x-sdp-traceid: <uuid>
Referer: https://zts.ccsut.cn/portal/shortcut.html
Body: {
  "ticket": "<shortcut data.ticket>",
  "deviceId": "<随机设备标识>",
  "env": {
    "endpoint": {
      "device_id": "<同一 deviceId>",
      "device": {"type": "browser"}
    }
  }
}
```

  环境上报成功后还有**决定性的一步**（mitmproxy 抓包实测）：

```text
GET https://zts.ccsut.cn/passport/v1/auth/authCheck?clientType=SDPBrowserClient&platform=<平台>&lang=zh-CN
```

  响应 `{"code":0,"data":{"sidTicket":"…","onlineInfo":{"isOnline":true,…}}}`，
  并 `Set-Cookie: sdp_limit_auth_tag=online` + 轮换 `sid`——会话由 `secondary_auth` 翻转为 `online`。
  **没有 authCheck，`verify?t=` 会无限 302 弹回 `shortcut.html?dest=#!%2Flogin`**（这是本链路最难定位的一环：
  CAS shortcut 落点带 `nextService=auth/authCheck` 参数，浏览器 shortcut JS 据此调用它，纯静态分析极易漏掉）。
  之后浏览器跳向 stored appUrl = `/controller/v1/public/verify?t=<JWT>`，一次放行 302 回 returnUrl。
- 普通登录数据不应强行调用 `reportEnv`；若跳转页持续回到 `shortcut.html?dest=#!/login`，依次检查：
  reportEnv 前是否调过 `authConfig?…&mod=1`（否则 403 session not found）、deviceId 是否为
  `00` 开头纯十六进制硬件签名（浏览器用 RSA 例程生成后长期复用）、是否调用了 authCheck。
- App 手动跟随整条重定向链并逐跳收 Cookie，终点 URL 形如
  `https://zts.ccsut.cn/portal/?redirectid=...#/app_center`。
- 登录失败（验证码错/过期）：**HTTP 200 重新返回登录页 HTML**（无 302）→ 据此判定失败。
- `service` 参数（固定）：
  `https%3A%2F%2Fzts.ccsut.cn%3A443%2Fpassport%2Fv1%2Fauth%2Fcas%3FsfDomain%3Dcas88719`
  即 `https://zts.ccsut.cn:443/passport/v1/auth/cas?sfDomain=cas88719`。

### 1.3 登录页获取

```
GET https://auth.ccsut.cn/backstage/cas/login?service=<上面的 service>
```

- 正常：200 返回 Angular 登录页 HTML（含 `flowExecutionKey`、脚本 `ng-login/main.*.js`）。
- **账号已在别处在线**：被重定向到含 `75500006` /「当前账号已在线」的页面 →
  App 检出后提示「账号可能刚在别处下线，请等约 3 分钟再试」。
- 页面登录方式三种：扫码（企业微信/微信）、密码、验证码；App 只走验证码。
- 密码登录（备用知识）：`password` 字段走 AES（SPA 内 `LOGIN_PASS_AES_KEY/IV`）后提交同一表单；
  短信验证码登录则完全绕开 AES。

### 1.4 aTrust 会话注销（调试用）

```
GET  https://zts.ccsut.cn/passport/v1/public/authConfig   → data.security.csrfToken
POST https://zts.ccsut.cn/passport/v1/user/logout
Headers: x-csrf-token: <csrfToken>, x-sdp-traceid: <uuid>, Content-Type: application/json
Body: {}
→ {"code":0,"message":"成功",...}
```
UI 路径：portal 工作台 → 右上菜单 →「注销登录」→ 确认弹窗（`views/confirm_logout`）。
App 不需要此接口（会话本来就该留在服务器 3 分钟自然消亡）。

### 1.5 建立 tls 教务会话

aTrust 会话就绪后（zts 域有 `online=1`）：

```
GET https://tls.ccsut.cn/admin/caslogin
```

网关自动放行 → tls 侧发现无本地会话 → 重定向到 CAS（SSO 已登录）→ 带 ticket 回
`/admin/caslogin` → tls 建会话 → 落到 `https://tls.ccsut.cn/admin/?loginType=1` 教务主页。
全程手动跟随重定向、逐跳收 tls 域 Cookie 即可，无需用户再输入。

> tls 登录页 `https://tls.ccsut.cn/admin/login` 有「账号登录 / 统一认证」两个入口；
> 「统一认证登录」就是 `<a href="/admin/caslogin">`。Cookie 里 `puid`、`username`（明文学号）
> 是持久 Cookie，会话失效后仍在，不能作为存活判据。

## 2. 个人课表接口（tls 域，全部 GET，带教务 Cookie）

### 2.1 会话/周次探测

```
GET /admin/api/getXlzc
→ {"ret":0,"msg":"操作成功","data":{"xlzc":"5"}}     // 当前教学周
```
无会话时返回 302 到登录页（或登录 HTML）。**用作会话存活探测**。

### 2.2 学生课表页（取 xhid 令牌 + 姓名）

```
GET /admin/pkgl/xskb/queryKbForXsd?xnxq=2026-2027-1
```
返回服务端渲染 HTML（超星教务，jQuery），内嵌关键数据：
- `<input id="xhid" value="WG...">` —— **加密学号令牌**（`WGEyQ0` 开头，AES 风格，对同一学生不变）
- 学期下拉（共 5 个 `2026-2027-1` 式选项）；`#xqdm` 校区代码（`01`=本校区）
- 课表标题含学生姓名（如「王奕」）

### 2.3 周次与作息（个人课表自包含，不依赖班级数据集）

```
GET /admin/api/getZclistByXnxq?xnxq=2026-2027-1&xqid=01
→ {"ret":0,"msg":"操作成功","data":{
     "dqzc":"5",
     "zclist":[{"zc":1,"minrq":"2026-09-07","maxrq":"2026-09-13","rqfw":"..."}, ... 20 周],
     "jcsjszList":[{"jc":"1","kssj":"8:20","jssj":"9:05","djs":"1","sjd":"sw"}, ... 10 节]}}
```
- 第 1 周 `minrq` = 开学日（第 1 周周一）；`zclist` 长度 = 学期总周数。
- 与班级课表接口（api.md §2）同源同形，但个人课表流程**独立请求**，字段足够自行对齐。

### 2.4 个人课表数据（一次拉整学期）

```
GET /admin/pkgl/xskb/sdpkkbList?xnxq=<学期>&xhid=<令牌>&xqdm=<校区>&zxzc=&zdzc=&xskbxslx=0
→ {"ret":0,"msg":"操作成功","data":[ ...94 条... ]}    // data 直接是数组
```

`zxzc`/`zdzc`（起止周）留空 = 全学期。每条记录 48 字段：

| 字段 | 含义 | 示例 / 处理 |
|---|---|---|
| `xnxq` | 学年学期 | `2026-2027-1` |
| `kcmc` | 课程名 | **含 HTML**：`<a href=...>创业基础（创新实践）</a>` → 剥标签 |
| `kcbh` | 课程编号 | `IB07007` |
| `tmc` | 教师 | 含 HTML `<a ...>冯文韬</a>` → 剥标签 |
| `croommc` | 教室 | 含 HTML `<a ...>7-南410</a>` → 剥标签 |
| `xingqi` | 星期 | `1`-`7` |
| `djc` | 第几节（起始节） | `1`；连堂课=相邻多条记录（1、2 各一条） |
| `djs` | 连堂节数 | 多数记录为 `1`；连堂以**相邻 djc 记录合并**表达 |
| `rqxl` | 日期序列编码 | `= xingqi*100 + djc`（如周一第1节=101） |
| `zc` | 周次区间串 | `"11-11,13-15"` |
| `zcstr` | 周次逗号串 | `"11,13,14,15"` —— **用这个**（已是展开形式） |
| `xqid`/`xqmc` | 校区 | `01`/`本校区` |
| `jxbmc` | 教学班 | `创业基础（创新实践）(就业指导与创新创业课程)-实践035` |
| `jxbzc` | 合班班级 | `25计科3班` |
| `kcxz` | 课程性质 | `就业指导与创新创业课程` |
| `ksxs` | 考核形式 | `考查` |
| `xf` | 学分 | `"1"` |
| `bjrs`/`xkrs` | 班级/选课人数 | |
| `source` | 数据来源 | 主修/重修区分 |
| `sfwc` | 是否完成 | |
| `id`/`pkid`/`jxbid`/`croomid` | 各类 id | 仅 `jxbid` 稳定，可用作去重键成分 |

- **连堂判定**：同课程同星期 `djc` 相邻（n、n+1）且周次一致 → 合并为一条 n 起、2 节的课
  （App 的 `Merger` 对 `djs` 语义天然兼容：解析时把相邻记录折叠成 `djc=n, djs=2`）。
- **单双周**：`zcstr` 已展开（如 `"1,3,5,7,9,11,13,15"`），无需特判。
- 实测样例（2026-2027-1，王奕/25计科3班）：94 条记录 12 门课，含
  大学英语A3（周五 1-2 连堂）、数据库原理与应用课程设计（集中实践，整周）、认知实习等。
- **评教拦截**：网页端会先发 `POST /admin/xsd/kcapcx/checkSfkc` 与
  `POST /admin/xsd/pkgl/xskb/checkWfwSfpj`（是否评教检查）。**App 直接调 sdpkkbList，不调这两个**，
  未评教账号照样能拉到数据；若某账号被服务端拦，会得到非 `ret:0` 响应，按「需先在网页完成评教」提示。

### 2.5 其他备用

```
GET /admin/api/getbzxx      → 报障/通知信息（实测 540B）
GET /admin/api/getRqListByWeek
POST /admin/system/jxzy/jsxx/getXqrqxx
```

扩展方向（成绩/考试）：教务主页「快捷通道」的「我的成绩 / 我的考试」入口页面即对应
`/admin/...` 下的查询页面，抓包方法与本文档相同（登录会话内 F12 或 App 内复用本链路）。

## 3. App 实现要点（与代码对应）

1. `CasClient`（Kotlin，裸 `HttpURLConnection`）：
   - 自建 cookie jar（按 host 存 `Set-Cookie`，手动 `Cookie` 头回传；不落盘）；
   - `followRedirects=false`，跨域 302 逐跳手跟并收集 Cookie（302 的 Location 可能是相对路径）；
   - 常量 UA 与浏览器一致。
2. 登录序列：`GET cas/login?service=...`（提 execution，同时检出 75500006）
   → `POST sendCode`（JSON）→ 用户填码 → `POST ./login`（form，`phone_msg###<code>`）
   → 手跟 302 链到 zts portal → `GET tls/admin/caslogin` 手跟链到教务主页。
3. 拉取序列：`getXlzc`（存活探测）→ `queryKbForXsd`（xhid/姓名/学期）→ `getZclistByXnxq`
   （周次/作息）→ `sdpkkbList`（全量课表）→ 解析为 App `Course` 列表（剥 HTML、zcstr→区间、
   相邻 djc 折叠连堂）→ 存 `PersonalCache`（`personal_cache.json`，含学生姓名/学期/开学日/节次时间）。
4. 失效判定：响应 30x 到登录页 / HTML 含登录标记 / `ret != 0` 且 msg 含「登录」——
   参考 `scripts/scrape.py` 的 `SessionExpired` 检测（301/302/401/403/登录 HTML）。
