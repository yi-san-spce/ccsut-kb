# 发版手册

> **给谁看**：维护者。从代码到用户手机上的一条龙发版，版本规则、发布通道、历史教训都在这里。
> 常规贡献者不需要本文，提 PR 见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 版本规则

- **versionName** 三段式 SemVer，带**版本类后缀**（全英文）：稳定 `X.Y.Z-stable` / 内测 `X.Y.Z-beta.N` / 抢先 `X.Y.Z-pre.YYYYMMDD`；新功能 MINOR+1，修复 PATCH+1，不兼容改动 MAJOR+1。历史裸版本号（如 v1.0.0）App 侧按稳定类解析，保持兼容
- **开发者版 `X.Y.Z-dev`**：debug 构建（`assembleDebug` / `installDebug`）自动挂 `-dev`。它是版本类而**非可选通道**——不发布、不进任何版本列表（`Channels.visible` 恒 false）、不被同主版本的发布版催更；只体现在版本号里（设置里/关于页/诊断报告均显示 `X.Y.Z-dev`）
- **versionCode 分段**（三通道防倒挂，自 v1.0.0 之后的下一版起生效；40 是旧线性方案最后一个）：
  ```
  versionCode = 主版本×10⁸ + 次版本×10⁶ + 修订×10⁴ + 段位
  段位：抢先 0..4999（epoch 天数%5000）| 内测 5000..8999（5000+序号）| 稳定 9000
  ```
  同主版本内 抢先 < 内测 < 稳定 < 开发者(dev)，且下一个稳定版必然高于之前所有预发布——预发布用户升正式版走正常更新，不会被系统判为降级
- 版本号**双写**在 `android/app/build.gradle` 与 `data/Updater.kt` 的 `BuildVersion`；`./gradlew checkVersionSync` 校验一致性（历史上失同步过一次差点发错版）
- 发版提交固定 `chore(release): vX.Y.Z <主题>`；稳定版 tag 恒为 `v<versionName>`（annotated，publish.sh 自动打），预发布 tag 由 GitHub 从远端 master 创建

## 发版流程总则：内测先行（2026-10-22 起，团队约定）

所有对用户可见的更新**一律先发内测版**，内测验证通过后再按情况上更高通道：

1. **常规迭代**：内测版 → 验证通过 → 稳定版；想提前放量收集反馈，先发抢先版再转稳定
2. **恶性 bug（崩溃 / 数据错误 / 必须立刻止血）**：内测版 → 验证通过 → **立即上稳定版**，不等抢先节奏
3. **内测清单托管在本仓** `data/versions-beta.json`，raw 直链永久不变（内测同学在 App 里填一次即可长期收内测推送）：
   `https://raw.githubusercontent.com/yi-san-spce/ccsut-kb/master/data/versions-beta.json`
   每发一个内测版：把 publish.sh 结尾打印的条目 JSON 插到 `versions` 数组**最前** → commit → `git push github master`
4. ⚠️ **一旦发过某基线的内测版，同基线稳定版的 versionCode 必须走分段方案**（如 `1.2.0-beta.1`=102005001 → `1.2.0` 稳定=102009000），绝不能回退旧的顺序计数，否则内测用户会被判降级、永远升不上正式版

## 发布通道

| 通道 | 用途 | 机制 |
|---|---|---|
| **GitHub Releases** `yi-san-spce/ccsut-kb` | 新版 App 更新主通道（App 内镜像加速/官方直连都从这里取数） | publish.sh 打 tag + `gh release create`；稳定版 Release 附带 `versions.json`（稳定索引）与 `versions-preview.json`（稳定+抢先全通道索引，每次发版回填到最新稳定 Release） |
| **内测清单** `versions-beta.json` | 内测版专用（不公开） | 团队自管的 https 静态文件（OSS / 自有服务器均可），链接私发给内测用户填入 App；内容格式见下文内测版一节 |
| **源码备份** | GitHub 主站 | `scripts/push.sh` 推送 master |

## 前置条件

- `.session/cookie.txt`：数据抓取会话文件（仅重新构建数据集时需要；获取方式由维护者内部掌握，暂不公开）
- `gh` CLI 已登录（GitHub Release 自动化用）
- 签名：`keystore/release.keystore` + `android/local.properties` 的 `keystore.password=<口令>`

## 推送更新 · 场景一：稳定版（正式发版，全体用户）

1. **写更新说明** `docs/release-notes/vX.Y.Z-stable.md`——应用内「软件更新」页文案自动从这里提取（`# 长工课表通 vX.Y.Z` 大标题跳过；`## 小节标题` 分组展示为分段说明，同时保留一份扁平 notes 兼容旧版）
2. **升版本双写**：`build.gradle` 的 `versionCode/versionName` + `Updater.kt` 的 `BuildVersion`（稳定段位 = base + 9000；自 1.0.1 起 versionName 带 `-stable` 后缀，如 `1.0.1-stable`）
3. **构建签名 APK**：
   ```bash
   cd android && ./gradlew assembleRelease
   # 产物 app/build/outputs/apk/release/app-release.apk
   ```
4. **一条龙发布**：
   ```bash
   scripts/publish.sh --skip --apk android/app/build/outputs/apk/release/app-release.apk
   ```
   自动完成：干净工作区门禁 → aapt 读 APK 真实版本（**以产物为准**，build.gradle 被提前改到下一版本也不会写错清单）→ sha256/notes/sections 注入清单 → 打 tag `vX.Y.Z` 推 GitHub → `gh release create`（APK + versions.json 稳定索引 + versions-preview.json 全通道索引 + notes；versions.json 内含远程镜像清单 mirrors 与各版本下载量快照 downloads，改镜像清单只需改脚本顶部 GH_MIRRORS 后重发，无需发新版 App）
5. **推源码**：`scripts/push.sh`
6. **升 master 版本号到下一版本**（如 1.3.0 → 1.4.0），否则之后的抢先版会与该稳定版同基线倒挂
7. **核验**：`gh release view vX.Y.Z` 看资产（APK / versions.json）；应用内「软件更新」页切源核对版本列表

## 推送更新 · 场景二：抢先版（公开预发布，App「抢先版」通道可见）

频率自定，按需发布即可；抢先 = 最快拿到 master 构建，不是每天必须出包。

1. 确认 master 版本号是**下一个未发布版本**（发完 1.3.0 后应为 1.4.0）
2. **升版本双写**：`build.gradle` 设 `versionName "1.4.0-pre.20261013"`、`versionCode = base + epoch天数%5000`（算错会被脚本拦下并打印期望值；隔夜发按昨天宽限）+ `Updater.kt` 的 `BuildVersion`
3. 构建签名 APK（同上）
4. **发布**：
   ```bash
   scripts/publish.sh --skip --pre --apk android/app/build/outputs/apk/release/app-release.apk
   ```
   自动：格式与 versionCode 分段强校验 → 创建 prerelease（tag `v1.4.0-pre.20261013`，APK + versions-preview.json）→ 把全通道索引回填到最新稳定 Release（App 抢先通道读 `releases/latest/download/versions-preview.json`，镜像模式同样可读）
5. 核验：App 切「抢先版」通道刷新，应看到带「抢先」徽标的新版本

## 推送更新 · 场景三：内测版（团队私发，App「内测版」通道需填链接）

内测版**不进任何公开索引**——GitHub 上只有一个知道链接才能访问的 prerelease，App 里必须填入团队派发的清单链接才能更新。

1. **升版本双写**：`build.gradle` 设 `versionName "1.4.0-beta.1"`、`versionCode = base + 5000 + 1` + `Updater.kt` 的 `BuildVersion`
2. 构建签名 APK（同上）
3. **发布**：
   ```bash
   scripts/publish.sh --skip --beta --apk android/app/build/outputs/apk/release/app-release.apk
   ```
   自动：创建 prerelease（不碰 versions.json / versions-preview.json / latest.json，稳定用户零感知），结尾**打印内测清单条目 JSON + APK 直链**
4. **维护内测清单**：把打印的条目 JSON 追加到本仓 `data/versions-beta.json`（`versions` 数组最前）→ commit → 推送。清单 raw 直链（发给内测用户，填一次长期有效）：
   `https://raw.githubusercontent.com/yi-san-spce/ccsut-kb/master/data/versions-beta.json`
   文件格式：
   ```json
   {
     "versions": [
       { "versionCode": 103005001, "versionName": "1.3.0-beta.1", "date": "2026-10-13",
         "file": "ccsut-kb-1.3.0-beta.1.apk",
         "fileUrl": "https://github.com/yi-san-spce/ccsut-kb/releases/download/v1.3.0-beta.1/ccsut-kb-1.3.0-beta.1.apk",
         "sha256": "…", "bytes": 13527019, "channel": "beta", "notes": ["…"] }
     ]
   }
   ```
   `file` 写相对文件名时按清单所在目录解析，`fileUrl` 直接给绝对 https 直链也可以（App 端 `fileUrl` 优先）。**推荐直接放镜像前缀直链**（如 `https://ghfast.top/https://github.com/...`），大陆网络免代理可下；App 端有 sha256 校验，镜像转存不影响完整性
5. 内测用户：App「软件更新 → 版本通道 → 内测版」→ 确认切换 → 填入清单链接 → 更新；升级到下一个稳定版走正常更新（versionCode 分段保证），不用重新填链接

## 推送更新 · 场景四：只更课表数据（不发版）

```bash
# 先按维护者内部流程准备 .session/cookie.txt（细节暂不公开）
scripts/publish.sh            # 抓取 + 发布数据，用户下次启动自动拿到
```
