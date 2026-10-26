# 匿名统计与下载量实时化

这条线做两件事：**① 匿名使用统计**（公告点击计数 + 活跃探针，经 Vercount 公共计数服务，无 SDK、无设备标识）；**② 版本页下载量实时化**（镜像模式的版本列表先显示发版快照，再用 GitHub API 实时值覆盖）。

代码已合入 master 并通过全量单测，**尚未发版**。本文档同时是接手人的交接说明：机制、端点选型依据、待办清单都在这里。

## 1. 匿名统计（Vercount）

### 机制

`data/notice.json` 顶层新增可选字段 `"track"`（计数端点，仅接受 https，旧版本自动忽略）：

```json
{
  "track": "https://www.vercount.one/api/v2/log",
  "notices": [...]
}
```

客户端行为（`data/Notice.kt`）：

| 触发点 | 计数 key | 说明 |
|---|---|---|
| 成功拉到 notice.json（冷启动 / 回前台 / 后台闹钟） | `https://ccsut-kb.app/alive` | **活跃探针**：一次成功拉取 ≈ 一次活跃启动。补上 GitHub 下载计数看不到的「群直装」盲区 |
| 用户点公告「确认」按钮 | `https://ccsut-kb.app/notice/<id>` | 交互量，按公告 id 区分 |

- 请求是 `POST <track>` body `{"url":"<key>"}`（`Updater.httpPostJson`，5s 超时），独立线程 fire-and-forget，**任何失败静默**——Vercount 挂了只丢计数，不影响任何功能。
- 公告 id 进 key 前只保留 `[A-Za-z0-9_-]`（防御性过滤）。
- 拉取失败（404/断网）不 ping；后台闹钟拉到也算活跃。

### 查数（不进任何后台）

```bash
curl "https://www.vercount.one/api/v2/log?url=https://ccsut-kb.app/alive"
# → {"status":"success","data":{"site_uv":0,"site_pv":N,"page_pv":M}}
```

只看 `page_pv`（该 key 的累计 ping 数）。`site_uv` 基于 cookie，App 无 cookie 恒为 0，忽略。读数有几分钟内的同步延迟。`site_pv` 是同 host 全部 key 之和，可当总量参考。

### 端点选型依据（裸网实测，2026-10-25）

⚠️ **连通性测试必须绕开代理**：开发机挂着 Clash（`127.0.0.1:7897`，环境变量 + 系统代理双开），`curl` 默认全走代理。复测请用 `curl --noproxy '*' -w "%{http_code} %{time_total}s"`。

| 端点 | 直连结果 | 结论 |
|---|---|---|
| `www.vercount.one` | **6/6 稳定，~0.9s**（独立服务器，非 CDN） | **选定** |
| `events.vercount.one` | 3/6，Cloudflare 边缘抖动（首次可 TCP 超时） | 官方主推域名反而最不稳；与 www 共享同一计数后端 |
| api.github.com | TLS 握手立即失败（疑似 SNI 阻断） | 见 §2；官方直连模式在裸网用户处会回退静态索引 |
| raw.githubusercontent.com | TLS 半途黑洞超时 | 数据线依赖镜像 fallback 是刚需 |
| busuanzi.ibruce.info | JS 可达但 API 端点超时 | 历史上反复 502，排除 |
| count.getloli.com / workers.dev / hits.seeyoufarm | 403 反爬 / TCP 不通 / DNS 失败 | 排除 |
| abacus.jasoncameron.dev | 可达 | 次备胎 |

**换端点的方法**：改 `data/notice.json` 的 `track` 一行并 push 即全网生效（端点随公告文件下发，App 内写死的是协议不是地址）。

### 注意事项

- Vercount 是个人公益项目（开源 GPL-3.0，Go+Redis，可自托管），无 SLA——所以客户端 ping 必须 best-effort，断档可接受。
- Vercount 明确**禁止脚本刷计数**（违反会被封）。我们的 ping 全部来自真实用户行为，不要在发布脚本里预填充数据。
- 合规：key 只含事件语义（alive / notice-id），**不携带设备号、学号、IP 存储**；属于匿名点击统计。发版时建议在 `docs/PRIVACY.md` 补一句（见待办）。

## 2. 版本页下载量实时化

镜像模式的版本列表来自静态 `versions.json`，其中 `downloads` 是 `publish.sh` 发版时快照的 GitHub 下载计数，**发版后永不更新**；官方直连模式主源即 GitHub Releases API，本来就是实时值。

改动（`Releases.kt` + `MainActivity.loadReleases`）：镜像模式拉到列表后**先显示快照**，随后异步调 `Releases.downloadCountsByTag()`（GitHub API，tag → `.apk` 资产 `download_count` 求和）按 versionName 覆盖 `downloadCount`，失败静默保留快照。UI 展示口径不变，用户会看到数字从快照值跳到实时值。

已知盲区（与既有口径一致）：镜像代理（ghfast.top 等）转发的下载不计入 GitHub `download_count`。Vercount 侧将来若要观测镜像下载，可在 `startDownload` 处加 per-version ping（本次未做，接手人可选）。

## 3. 改动清单（本次已提交）

| 文件 | 改动 |
|---|---|
| `data/Notice.kt` | 顶层 `track` 解析并透传给每条 `RemoteNotice`；`fetch` 成功后活跃探针；`pingConfirm()` 点击计数 |
| `data/Updater.kt` | `httpPostJson()`（5s 超时最小 POST） |
| `MainActivity.kt` | 公告 onConfirm 处 fire-and-forget ping；`loadReleases` 镜像模式下载量异步覆盖 |
| `data/Releases.kt` | `downloadCountsByTag()` + `parseDownloadCounts()`（internal 供单测） |
| `data/notice.json` | 顶层 `"track": "https://www.vercount.one/api/v2/log"` |
| 测试 | `NoticeTest`（track 透传/非法丢弃）、`ReleasesTest`（tag 聚合求和）；全量 `testDebugUnitTest` 通过 |

## 4. 接手待办

1. **真机回归**（发版前必做）：
   - 公告弹窗点「确认/去同步」→ 跳转正常，无卡顿（ping 是独立线程）；
   - 版本页（镜像模式）进入后下载量数字先快照后跳实时；断网/飞行模式进入版本页无 crash、无卡死；
   - 旧版本（1.2.0-stable）装回后拉新 notice.json 正常（旧版忽略 `track` 字段，`check_notice.py` 已验证兼容）。
2. **发版**：随 `1.3.0-beta.1` 走 `scripts/publish.sh` 常规流程（版本基线已在 master）。release-notes 提一笔「版本页下载量实时显示」。
3. **隐私政策**：`docs/PRIVACY.md` 建议补一句：「为统计匿名使用情况，App 在收到远程通知时向第三方计数服务发送一次不含任何设备标识的计数请求」。若不愿引入第三方，把 `track` 字段删掉即全网停用统计，App 端无需改代码。
4. **数据校准（可选）**：`alive` 计数稳定运行一两周后，用它校准用户量估算模型（群直装盲区）。
5. **测试时的自检**：本地开发构建也会 ping（`DevSheet` 场景注意别把测试 key 当真实数据）；查数用 §1 的 curl 命令，key 规则见 §1 表格。
