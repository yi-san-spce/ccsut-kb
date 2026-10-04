# 长工课表通 · APP 全流程文档

## 项目结构

```
android/                     # 安卓工程 (Gradle 根)
  app/                       # 唯一模块, 包名 com.ccsut.kb
    src/main/assets/data/dataset.json   # 内置课表数据 (构建时打包)
    src/main/java/com/ccsut/kb/
      MainActivity.kt        # 入口 + 界面导航 + 更新流程
      Prefs.kt               # 全部本地偏好 (班级/主题/颜色/背景/提醒)
      data/Models.kt         # 数据模型 + JSON 解析 + 本地仓库
      data/Updater.kt        # 清单/下载/sha256 校验/APK 安装
      data/ClassCache.kt     # 当前班级轻量快照 (小组件/提醒用, 免解析 4MB 全量)
      data/BgStore.kt        # 课表背景图 (相册压缩转存/解码/删除)
      reminder/Reminder.kt   # 课前提醒: AlarmManager 链式调度 + 通知 + Receiver
      widget/WidgetProvider.kt  # 今日课程桌面小组件
      ui/                    # Compose 界面 (课表/选班/弹层/主题)
      util/Weeks.kt          # 周次计算 + 连堂合并
data/                        # 抓取产物 (数据集 + latest.json 清单)
scripts/scrape.py            # 全量抓取脚本 (见 docs/api.md)
scripts/publish.sh           # 抓取 → 数据集 → Gitee 发布 (Release + 更新清单)
docs/release-notes/          # 各版本更新说明手稿 (发版文案/群公告用)
keystore/release.keystore    # 签名 (密码 ccsut-kb-2026, 别名 kb, 30年)
```

## 二版功能 (v2.0.1)

| 功能 | 入口 | 实现 |
|---|---|---|
| 课程颜色自定义 | 点课表任意课程块 → 详情弹层"颜色"色板(12色+自动) | 按课程种子 `kc|fx` 存 SharedPreferences JSON; 自动配色保留为默认 |
| 课表背景图 | 更多 → 个性化 → 课表背景 (Photo Picker 免权限) | 压缩至 ≤1600px 转存 filesDir/bg.jpg; 透明度可调, 叠 25% 底色保证可读 |
| 今日课表小组件 | 长按桌面 → 小组件 → 长工课表通 (默认 3×2) | RemoteViews 渲染当天前 6 节; 只读 ClassCache 快照; 30min 周期 + APP 数据变化即刷; **Material You 动态取色** |
| 课前提醒 | 更多 → 课前提醒 (开关 + 提前 5/10/15/20/30 分钟) | 只排一条「下一次提醒」闹钟, 触发后发通知再排下一条; 依赖 POST_NOTIFICATIONS 与 SCHEDULE_EXACT_ALARM(缺失自动降级非精确并提示) |

v2.0.1 修复/升级:
- 背景图「换一张不生效」: produceState 只以文件路径为 key, 路径恒为 bg.jpg 导致换图后不重新解码 → 增加 `bgVersion`(bgTick) 作为第二个 key; 选图失败改为 Toast 明显提示。
- 小组件「Can't load widget」: RemoteViews 白名单不允许裸 `<View>`, 色条改用空 `TextView` 承载背景。
- 小组件 Material You 化: 根布局 `android:theme=Theme.DeviceDefault.DayNight`, 背景/文字/色条改用 `@android:color/system_neutral1_900`、`system_neutral2_50/700`、`system_accent1_400` 等框架动态色资源 (S+ 跟随壁纸取色, 自带深浅色变体); 自定义课程颜色仍然覆盖色条。
- Photo Picker 个别条目 `openInputStream` 返回 null(如 PNG), BgStore 增加 `openFileDescriptor` 兜底路径。

v2.0.2 修复:
- 换班级后课表不刷新, 要点一次「更多」才更新: `if (screenChoose)` 在 `Surface{}` 内容 lambda 内, 该状态变化只触发 lambda 局部重组, 而 `cls` 是外层作用域从 SharedPreferences 读出的普通局部变量, 被闭包捕获后不重算 → 引入 `clsId` Compose 状态, `dataset`/`cls` 改为 `remember(dataTick, clsId)` 在 App 作用域派生(数据更新路径同样受益)。

## 开发者模式 (v2.2.0)

入口: 更多 → 关于 → **连点「长工课表通 v…」7 次**开启(开启后长按同一行关闭); 开启后「数据」区下方出现「开发者模式」入口, 点开独立面板。

| 区块 | 内容 |
|---|---|
| 状态速览 | 数据版本/学期/来源、ClassCache 快照(班级/课程数/文件大小)、语义今天(偏移≠0 红字提示)、下次闹钟 + 精确闹钟/通知权限、小组件数量 |
| 时间旅行 | 日期偏移 -30..+60 天(快捷: 真实/明天/下周一/+7)。`KbClock` 只平移日期不动时刻(周日晚可测周一早八), 课表/小组件/提醒全部跟随, 偏移持久化在 Prefs |
| 即时操作 | 重建快照 / 强刷小组件 / 重排闹钟 / 发测试通知 / **10 秒测试闹钟**(ACTION_DEV_TEST 走真实 AlarmManager→Receiver→通知链路) / 重载数据 / 模拟 APK 更新弹窗 |
| 更新通道 | 拉取并显示 latest.json 原始 JSON |
| 日志 | `DebugLog` 内存环形缓冲 300 条, 关键路径打点(data/update/remind/widget/dev), 同时镜像 logcat `kb-dev` |
| 诊断 | 生成现场报告(设备/版本/数据/设置/日志)用系统分享发出, 远程排障用 |
| 危险区 | 重置个性化(颜色/背景/主题, 保留班级) / 完全重置(清全部+数据+快照+背景, 回选班级, devMode 保留) |

注意:
- 时间旅行通过 `KbClock.today()/now()` 生效, 新代码里"今天"一律走它, 不要直接 `LocalDate.now()`; LocalTime 时刻类(时间线/进行中)保留真实时钟是有意设计。
- 日志是进程内存, 杀进程即清空; 完整现场以「导出诊断报告」为准。

注意:
- 小组件与提醒进程读 `ClassCache` 快照 (几十 KB), APP 在加载数据/换班/更新数据后调用 `ClassCache.save()` 刷新; 直接改课表渲染逻辑时两者共用 `Merger`。
- 节次时间是 `8:20` 无前导零格式, 一律走 `ClassCache.minutesOf()` 或手动 split, 不能 `LocalTime.parse`。

## 背景图取色 + Material You 视觉 (v2.3.0)

核心规则: **设置了自定义背景图后, App 全部界面 + 桌面小组件的配色一律跟随背景图提取的种子色, 不再跟随系统壁纸**; 移除背景图自动回到跟随壁纸。

| 功能 | 实现 |
|---|---|
| 配色来源三档 | 更多 → 外观 → 「主题配色」: 壁纸(默认) / 背景图 / 品牌色。选图保存后自动切到「背景图」档, 无需手动操作 |
| 种子色提取 | `BgStore.extractSeed()`: bg.jpg 降采样 128px → materialkolor `themeColors()`(Google HCT 量化打分算法, 与系统壁纸取色同源) → 存 `Prefs.bg_seed` |
| App 主题 | `KbTheme(themeMode, colorSource, seed)`: 档 1 用 `dynamicColorScheme(seed, isDark, TonalSpot)` 生成完整 48 角色 M3 配色; 档 2 复活品牌蓝 LightColors/DarkColors; 课程自动配色/今日状态条读 colorScheme 自动跟随 |
| 小组件跟随 | 种子色按色相分桶 12×30°(与课程 pill 同体系) → `widget_bg_h0..h11` / `widget_bg_dh0..dh11` 预烘焙 drawable `setBackgroundResource`; 标题/次级/强调文字色运行时从同色相 HSL 派生(`setTextColor` remotable 安全)。壁纸/品牌档维持中性灰底 |
| 毛玻璃 | haze 1.5.3: 背景图为 HazeSource, 顶栏 + 今日状态条 `hazeChild`(blur 24dp, surface 色 68%/55% tint)。仅设背景图时启用; 圆角玻璃用 `Modifier.clip` 实现(1.5.3 的 hazeChild 无 shape 参数) |

版本注意 (2026-10):
- material3 stable 仍是 1.4.0, **LoadingIndicator 等 Expressive 组件只在 1.5.0-alpha**——不要在 stable 里 import, 会 unresolved。
- materialkolor 用 2.1.1 stable; 2.x 的 `dynamicColorScheme` 要求显式传 `isAmoled`。
- `android.graphics.Color` 没有 `HSLToColor`, 用 `androidx.core.graphics.ColorUtils.HSLToColor`。
- 深色冷启动白闪已修: `res/values-night/themes.xml` 深色 windowBackground; Manifest 已开 `enableOnBackInvokedCallback`(预测性返回)。

## 工具链

| 组件 | 版本 | 备注 |
|---|---|---|
| AGP | 9.3.3 | 内置 Kotlin, 不需要 kotlin-android 插件 |
| Gradle | 9.5.0 | `~/tools/gradle-9.5.0` (华为镜像下载) |
| JDK | 17 | `brew install openjdk@17` |
| SDK | compileSdk 36 / minSdk 31 / target 36 | minSdk 31 = 安卓 12, 保证动态取色无条件可用 |
| Compose BOM | 2026.09.00 | material3 / ui / icons-core |
| 网络仓库 | 阿里云镜像优先 | `android/settings.gradle` |

## 构建

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
cd android
~/tools/gradle-9.5.0/bin/gradle -p . :app:assembleRelease --no-daemon
# 产物: android/app/build/outputs/apk/release/app-release.apk
```

## 更新机制 (数据 + APK 双通道, Gitee 承载)

发布体系 (v2.3.0 起从 Cloudflare Pages 迁到 Gitee):

| 仓库 | 可见性 | 内容 |
|---|---|---|
| `yisanspce/ccsut-kb` | 私有 | 全部源码 (本工程) |
| `yisanspce/ccsut-kb-release` | 公开 | `latest.json` + 数据集 + 最新 APK, 全部走 master 分支孤儿提交 + raw 直链 |

⚠️ 发布仓**故意不用 Gitee Release/标签**: Release 页面会自动挂「源码归档 zip/tar.gz」下载按钮 (内容其实只是发布仓自身文件), 但会造成"源码公开"的误会。发布仓 master 上只有 4 个分发文件: `README.md`、`latest.json`、数据集 json、`ccsut-kb-<版本>.apk`, 无任何源码。

APP 从「更新地址」(默认 `https://gitee.com/yisanspce/ccsut-kb-release/raw/master/latest.json`, 设置页可改) 拉清单:

```json
{
  "version": 4, "xnxq": "2026-2027-1",
  "file": "dataset_2026-2027-1_v4.json", "sha256": "…",
  "apk": { "versionCode": 12, "file": "ccsut-kb-2.4.0.apk", "sha256": "…" }
}
```

- `version` > 本地数据版本 → 相对清单地址下载数据包 → sha256 校验 → 落盘热切换 (无需重装 APP)
- `apk.versionCode` > 本地 APP → 相对清单地址下载 `apk.file` (raw 直链) → 拉起系统安装
- Gitee raw 有 60 秒 CDN 缓存 (max-age=60), 发布后最多 1 分钟全量可见
- APP 启动时静默检查一次 (失败不打扰), 设置页可手动检查

## 日常发布流程

前置: `.session/gitee_token` 存放 Gitee 私人令牌 (chmod 600, 已 gitignore)。

1. 更新数据: 浏览器登录教务系统 → F12 复制 Cookie 整行 → 覆盖 `.session/cookie.txt`
2. `scripts/publish.sh` — 抓取 + 发布数据 (同学们 APP 下次启动自动拿到)
3. 发新版本: 构建后 `scripts/publish.sh --skip --apk dist/长工课表通_vX.Y.Z.apk`
   - APK 改名 `ccsut-kb-<版本>.apk` 与清单/数据集一起孤儿提交进 master, 直链 + sha256 自动写进 `latest.json`
   - **版本号以 APK 产物自身为准** (aapt 读取), build.gradle 被提前改到下一版本也不会写错清单
4. 每次孤儿提交 force-push, 发布仓不积累历史; 历史版本 APK 需要时在本地 dist/ 自行归档

注意:
- 新建 Gitee 仓默认**私有**, 且空仓库不能改为公开 → 须先 push 内容再 `PATCH repos/... -d private=false`
- 旧版本 (≤2.2.3, 默认走 Cloudflare Pages) 的同学需手动安装一次 v2.3.0+ 才接入 Gitee 通道
