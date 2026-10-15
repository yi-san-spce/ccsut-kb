# 文档中心

「长工课表通」全部文档的目录索引。产品入口见[主 README](../README.md)——QQ 交流群（`1129626080`）/ [QQ 频道](https://pd.qq.com/s/9pnez0un5?b=9) / 下载都在那里。

## 目录总览

```
docs/
├── 上手教程/          # 从零跑起来 + 功能对照
│   ├── getting-started.md
│   └── features.md
├── 原理与接口/        # 应用架构与数据链路说明
│   ├── architecture.md
│   ├── api.md
│   └── api-personal.md
├── glossary.md        # 术语表：CAS、覆盖层、双链这些词第一次见，来这查
├── 开发者/            # 贡献 / 发布 / 社区治理
│   ├── CONTRIBUTING.md
│   ├── CODE_OF_CONDUCT.md
│   ├── SECURITY.md
│   └── release.md
├── release-notes/     # 历史版本说明（publish.sh 从这里提取应用内更新文案）
├── CHANGELOG.md       # 更新日志
└── PRIVACY.md         # 隐私政策
```

## 按你想做的事找文档

| 我想… | 去读 |
|---|---|
| **装 APP**（我是用户） | [README 下载区](../README.md#-下载给用户) |
| **把项目跑起来**：环境、构建、装机、开发者模式 | [上手教程/getting-started.md](上手教程/getting-started.md) |
| **查某个功能怎么做进去的** | [上手教程/features.md](上手教程/features.md)（功能-入口-实现对照） |
| **理解架构再动手改**：数据流、模块职责、设计约定 | [原理与接口/architecture.md](原理与接口/architecture.md) |
| **搞懂教务接口**：班级课表数据从哪来 | 因敏感因素，暂不提供相关的文档说明 |
| **搞懂登录链路**：CAS 短信登录与零信任网关 | 因敏感因素，暂不提供相关的文档说明 |
| **查术语**：覆盖层、双链、CAS、BAL 这些词什么意思 | [glossary.md](glossary.md) |
| **发布新版本 / 更新课表数据** | [开发者/release.md](开发者/release.md) |
| **参与贡献**：提交规范、PR 流程 | [开发者/CONTRIBUTING.md](开发者/CONTRIBUTING.md) |
| **报告安全问题** | [开发者/SECURITY.md](开发者/SECURITY.md)（私下报告，勿公开） |
| **看每个版本改了什么** | [CHANGELOG](CHANGELOG.md) · [release-notes/](release-notes/) |
| **了解隐私保护** | [PRIVACY.md](PRIVACY.md) |
| **社区行为约定** | [开发者/CODE_OF_CONDUCT.md](开发者/CODE_OF_CONDUCT.md) |

## 一页速览

- **技术栈**：Kotlin + Jetpack Compose，零第三方网络库（登录链路纯 `HttpURLConnection`），minSdk 31
- **数据**：班级课表数据集（`scripts/scrape.py` 从教务公开接口抓取）打包进 APK + GitHub 通道（镜像加速）热更；个人课表登录后实时拉取
- **分发**：GitHub Releases（应用内更新主通道，镜像加速）
- **质量**：54 个纯函数单测 + GitHub Actions CI（版本双写校验/测试/构建冒烟）+ 发布门禁（拒绝脏工作区）

## 代码地图

```
android/app/src/main/java/com/ccsut/kb/
├── MainActivity.kt          # 界面骨架与状态编排
├── Prefs.kt                 # 本地偏好
├── data/                    # Models+Repo / Updater / ClassCache / CasClient / PersonalRepo / UserEdits / BgStore
├── ui/                      # 全 Compose: 课表/引导/选班/登录/弹层/主题/Links
├── widget/                  # 桌面小组件 (RemoteViews)
└── reminder/                # 提醒双链 (通知+闹钟) + 全屏响铃页
data/                        # 课表数据集与更新清单 (发布产物源)
scripts/                     # scrape.py 抓取 · publish.sh 发版 · push.sh 推送
```
