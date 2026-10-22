# 应用架构

> **给谁看**：准备动手改代码的贡献者。
> **读完你能**：讲清课表数据从哪来、经过谁、到哪去；知道每个模块为什么存在；知道改代码的红线在哪。
> **读法**：先看总览建立心智模型，再按你要改的模块跳到对应小节。**动代码前务必读完「设计约定」**。

## 一句话架构

界面全用 Jetpack Compose 写。数据只有一份学校原始课表（只读），用户的编辑以覆盖层形式叠加在上面；小组件、提醒、更新全部从叠加后的结果派生——**同一份数据，服务所有出口**。

```
┌─────────────────────── UI (全 Compose) ───────────────────────┐
│  ScheduleScreen   Welcome/ChooseClass   LoginScreen   MoreSheet│
│         │                │                 │            │      │
└─────────┼────────────────┼─────────────────┼────────────┼──────┘
          │  remember(dataTick, clsId) 派生             │      │
┌─────────▼──────────────────────────────────────────────▼──────┐
│  Repo (dataset)  ──叠加──  UserEdits(本机编辑)  ⇒  生效课表      │
│  Prefs.kt            ClassCache.kt (生效课表快照)              │
└─────────┬────────────────────┬────────────────────────────────┘
          │                    │ 免解析共享
┌─────────▼──────┐   ┌─────────▼─────────┐   ┌──────────────────┐
│ widget/小组件   │   │ reminder/课前提醒  │   │ data/Updater 更新 │
│ RemoteViews    │   │ 通知链 + 闹钟链    │   │ 数据热更 + APK    │
└────────────────┘   └───────────────────┘   └──────────────────┘
```

用一个场景读这张图：你打开 App，`MainActivity` 根据 `remember(dataTick, clsId)` 从 `Repo` 取出内置课表，叠上 `UserEdits` 里的本机编辑，得到「生效课表」渲染到屏幕。每次数据变化，生效课表会写进 `ClassCache` 快照——桌面小组件和课前提醒读的是这个快照，不用解析 4MB 的全量数据。

**单一数据源原则**是整个架构的地基：学校原始数据永不修改。学校换课时，用户的编辑自动重新套用到新数据上；任何时候删掉编辑记录，都能回到学校的原版课表。渲染层永远只消费叠加后的结果。

## 代码地图

```
android/app/src/main/java/com/ccsut/kb/
├── MainActivity.kt          # 入口 + 界面导航 + 更新流程 + 提醒回调编排
├── Prefs.kt                 # 全部本地偏好 (SharedPreferences "kb")
├── data/
│   ├── Models.kt            # 数据模型 + JSON 解析 + Repo (班级课表仓库)
│   ├── Updater.kt           # 更新清单解析/下载/sha256 校验/APK 安装 (BuildVersion 双写)
│   ├── ClassCache.kt        # 生效课表轻量快照 (小组件/提醒进程读取, 免解析 4MB 全量)
│   ├── CasClient.kt         # 教务登录 HTTP 客户端 (CAS 短信 + cookie jar + 手动跨域重定向)
│   ├── PersonalRepo.kt      # 个人课表拉取/解析 + 内存仓库
│   ├── PersonalCache.kt     # 个人课表缓存 (personal_cache.json, 不含凭据)
│   ├── UserEdits.kt         # 本机编辑覆盖层 (add/modify/hide 三类记录)
│   └── BgStore.kt           # 课表背景图 (Photo Picker 压缩转存/种子色提取)
├── reminder/Reminder.kt     # 提醒调度器 (通知链+闹钟链双链) + ReminderReceiver
├── reminder/AlarmActivity.kt# 闹钟模式全屏响铃页
├── widget/WidgetProvider.kt # 今日课程小组件 (RemoteViews, Material You 动态取色)
└── ui/                      # Compose 界面 (课表/引导/选班/登录/弹层/主题/Links)
```

数据与脚本：

```
data/               # 数据集产物 (数据集 json + latest.json 清单) —— 构建时打包进 APK
scripts/scrape.py   # 数据集全量构建 (流程说明因敏感因素暂不公开)
scripts/publish.sh  # 一条龙发版 (见 release.md)
```

## 核心数据流

### 班级课表 = 仓库 + 覆盖层

App 里你看到的课表，永远由两层叠加而成：

- **仓库层 `Repo.dataset`**：学校给的原始课表。构建时打包进 APK，之后可以被「数据热更」整体替换。**这一层只读**。
- **编辑层 `UserEdits`**：你在手机上做的所有改动，存在本机 `filesDir/user_edits.json`。一个文件、三类记录：

| 记录类型 | 用户动作 | 记了什么 |
|---|---|---|
| `add` | 点空格加课 | 新课程本体。`type=100` 是个哨兵值，意思是「这节是自建课」；`jxb="LOCAL#uuid"` 给它一个本地唯一的教学班号，保证合并逻辑不会把它和学校数据搅在一起。同名多节的自建课各自独立，不互相合并 |
| `modify` | 编辑卡片改时间/节数/周次 | 只记「改了哪几项」：用锚点 `{kc,fx,day,jc,zc}` 定位到原来的课，再记增量 delta。学校更新后，没被你改到的字段继续跟学校走 |
| `hide` | 隐藏学校课程 | 隐藏哪条课 |

**为什么要分两层**：学校换课时，用户的编辑可以原样重新套到新数据上；想撤销任何改动，删掉那条记录就回到学校原版。

叠加发生在 `Repo.effectiveCls()`，两个贴心行为都从这里来：

- 学校换课后编辑自动重套；找不到锚点的失效修改会被安全丢弃并提示，不会崩
- 把课拖回学校原来的位置 = 修改自动还原（增量变成空记录，直接清除）

### 快照：ClassCache

**为什么需要快照**：全量数据集有 4MB，而桌面小组件和提醒运行在独立进程里，为了知道「今天有什么课」去解析 4MB 不划算。所以 App 在加载数据、换班、更新数据之后，把生效课表浓缩成一个几十 KB 的快照存进 `ClassCache`，小组件和提醒只读它。

App 和小组件共用同一套渲染逻辑（连堂合并 `Merger`），所以两边看到的课表永远一致。一个格式细节要注意：节次时间是 `8:20` 这种无前导零格式，统一走 `ClassCache.minutesOf()` 换算成分钟，**不要**用 `LocalTime.parse` 直接解析。

### 班级 ⇄ 个人课表切换

- 当前显示哪张表由 `Prefs.active_timetable` 决定（`class` / `personal`）。切换入口有两个——更多面板的 chips 和顶栏徽标——但都收口到 `setTimetable` 一个函数，避免两个入口行为漂移。
- 个人课表在代码里被包装成一个「伪班级」：id 固定为 `PERSONAL`，类型还是 `Cls`。好处是渲染链路上的所有环节（`ScheduleScreen`、`Merger`、配色、今日状态、小组件、提醒）**零改动**就能同时服务两种课表。
- 冷启动时用 `hasPersonal` 校验缓存里的个人课表（节次数 ≥4 且节次从 1 连续）才敢切换；不达标就回退班级课表——防止坏缓存把渲染层搞崩。
- 登录链路细节因敏感因素，暂不提供相关的文档说明。隐私约束：Cookie 只存内存，缓存文件不含凭据。

## 提醒双链

「双链」指两条各自独立维护的闹钟队列，由 `ReminderScheduler.reschedule()` 统一重排。三个开关（课前提醒 / 放学小结 / 早八前夜）各自独立生效，互不连坐。

**为什么是两条链**：两种提醒的「送达强度」需求不同。

- **通知链**（requestCode 0，轻量）：负责放学小结、早八前夜，以及没开闹钟模式时的课前提醒。用 `setExactAndAllowWhileIdle` 定时（系统拒绝精确闹钟时自动降级为非精确）。触发 → 发通知 → 重排下一条，链式接力。
- **闹钟链**（requestCode 4，强力）：开闹钟模式时的课前提醒走这条。用系统闹钟语义 `setAlarmClock`（状态栏出现闹钟图标），触发后广播 → `ReminderReceiver` 发**全屏意图通知**，并尽力直接启动 [AlarmActivity](../../android/app/src/main/java/com/ccsut/kb/reminder/AlarmActivity.kt)——锁屏点亮、循环闹钟铃声、2 分钟自动停。

> ⚠️ **为什么要「广播 + 通知」绕一道**（Android 后台启动限制 BAL 的教训）：`setAlarmClock` 的 PendingIntent 如果直接指向 Activity，会被系统的后台启动限制拦截；而且直启模式的 opt-in 只能由 PendingIntent 的创建方（creator 侧）设置——放在发送方（sender 侧）会被系统重置并抛异常。所以闹钟触发点必须是广播，再由广播发全屏通知、尽力直启页面。

投递容差：精确闹钟允许 30 分钟、降级后允许 60 分钟的投递延迟——Doze 或厂商省电策略推迟投递时，提醒不会丢。缺通知权限时 `fire()` 会往 DebugLog 留痕，方便诊断「为什么没提醒」。

## 更新双通道

更新有两种，互不依赖：

- **数据热更**（无需重装）：学校换课了，数据包走 GitHub 通道（镜像加速）独立下发。流程：更新清单 `version` 大于本地 → 下载数据包 → sha256 校验 → 落盘热切换。App 一秒换新数据，不用重新安装。
- **APK 更新**（需要安装）：更新清单 `apk.versionCode` 大于本地 `BuildVersion.CODE` → 弹更新页（版本 / 体积 / 更新说明 + 下载进度）。`BuildVersion.CODE` 与 build.gradle 双写（两处都要写版本号），`checkVersionSync` 负责校验一致。

安全约束：更新源默认是 GitHub 镜像加速（内置镜像清单并发测速取最快，全部失效自动退官方直连），可在设置切换官方直连；清单里的明文 http 下载地址会被直接拒绝，强制 HTTPS。

「更新提醒」在冷启动与每次回到前台时检查：发布说明带「修复」小节的版本以**阻断弹窗**强提醒（与远程通知共用 `ui/NoticeDialog` 外壳），普通更新发**系统通知栏**消息（点按进更新页，划掉即跳过该版本，同版本不再提醒，出现更高版本时恢复）。所有命中条件的应用内弹窗（修复类更新、远程通知）进入按优先级排序的**队列**——修复类更新 > critical 通知 > 普通通知，一次只弹一个，处理完自动弹下一个，直到队列清空。行为细节与运维注意：[update-reminder.md](../update-reminder.md)。

此外还有一条**远程通知广播**通道：主仓 `data/notice.json` 下发运营公告，客户端按排期窗口 / 版本定向 / 去重 / 优先级选出至多一条弹窗（不发版全网广播），文件格式与运维方式见 [docs/notice.md](../notice.md)。

## 主题与配色

配色来源三档选其一：

1. **壁纸取色**（默认）：Material You 动态取色，跟系统壁纸走
2. **背景图取色**：`BgStore.extractSeed()` 用 materialkolor 的 HCT 算法从用户选的背景图提取种子色
3. **品牌色**：固定品牌色

`KbTheme(themeMode, colorSource, seed, styleTheme)` 统一管理主题状态。5 套主题风格包（卡带 / 极简 / 瑞士 / 学院 / 赛博）选中后接管整套配色。几个实现细节：

- 背景图启用时，顶栏 / 今日状态条走 haze 毛玻璃
- 小组件没有 Compose，无法实时取色——种子色按色相分桶（12 桶 × 30°）预烘焙成 drawable，保证小组件与 App 颜色同源
- 课程块防撞色：按全学期课程的种子做稳定排序后分配色板，超出色板就在同色相里用明度续接。App 与小组件同一套逻辑，同一节课两边颜色一致
- 圆角 token：28 弹层 / 22 大卡 / 18 卡片 / 16 胶囊 / 14 行卡 / 12 行内件 / 8 小件 / CircleShape 把手

## 设计约定（改代码前必读）

三条红线，每条都踩过坑：

- **「今天」一律走 `KbClock.today()/now()`，不要直接 `LocalDate.now()`**——开发者模式的时间旅行靠这个钩子才能生效，写死 `LocalDate.now()` 的地方时间旅行会失灵
- **并发写盘统一 tmp+rename 原子写；共享可变状态加 `@Volatile`**——这是开源前全盘加固的成果，别退回
- **个人课表登录链路改动格外谨慎**：单会话策略、手动跨域重定向、CAS 会话参数，任何一处变动都要完整回归验证（细节文档因敏感因素暂不公开）

## Recap

- 数据只有一份（学校原始课表，只读），用户编辑是覆盖层，渲染层只消费叠加结果
- 小组件和提醒不读全量数据，读 `ClassCache` 快照
- 个人课表 = 伪班级 `PERSONAL`，渲染链路零改动复用
- 提醒两条链：通知链（轻）+ 闹钟链（强），三个开关互不连坐
- 更新两条通道：数据热更（免重装）+ APK 更新
- 红线：时间走 `KbClock`、写盘原子化、登录链路谨慎


## 鸿蒙线（harmony/）

安卓版的 HarmonyOS NEXT 移植，与安卓线并行维护、共用仓库数据（`data/`）与发版口径。要点：

- **纯逻辑层（`entry/src/main/ets/common/`）**是 Kotlin 核心的 ArkTS 直译：数据模型/编解码/数据集解析/Merger/UserEdits/周次/今日状态/大节槽位，**不依赖 ohos API**，用 Node 直跑单测（`harmony/tests/`，`npm test`），语义对齐安卓 junit
- **ohos 依赖层（`data/`）**：文件原子写（tmp+rename 同款规范）、Preferences 偏好（键名与安卓一致）、RCP 网络层（`autoRedirect:false` 手动逐跳 302 + 内存 CookieJar，登录链路逐行移植 `CasClient.kt`）、数据热更（同一份 GitHub `data/latest.json` 清单 + sha256）
- **UI（ArkUI 声明式）**：单 UIAbility + 页面状态机（对应安卓无导航库的布尔状态机），Swiper 周翻页对应 HorizontalPager，bindSheet 对应 ModalBottomSheet
- **版本独立线**：`AppScope/app.json5` versionCode 从 1 起，不与安卓 versionCode 混用
- 首期未做：小组件（→ArkTS 服务卡片）、提醒（→reminderAgentManager）、应用内更新页（HAP 无法自装）、背景图取色/主题包
- 工具链与构建：见 `harmony/README.md`（CLT/hvigorw/rsync 中文路径桥接、签名待华为账号）

## 延伸阅读

- 接口与登录链路细节：因敏感因素，暂不提供相关的文档说明
- 功能入口与代码关键词速查：[上手教程/features.md](../上手教程/features.md)
