# 长工课表通

一款为长沙工业学院（CCSUT）学生打造的 Android 课表应用。原生 Kotlin + Jetpack Compose，无第三方网络库，APK 体积小、启动快、离线可用。

> 让查看课表这件事，不再那么狼狈。

![License](https://img.shields.io/badge/license-GPL--3.0-blue) ![Platform](https://img.shields.io/badge/platform-Android%208%2B-green) ![CI](https://github.com/yi-san-spce/ccsut-kb/actions/workflows/ci.yml/badge.svg)

## 功能

- **班级课表**：内置全院班级课表数据集，选择班级即用，无网也能看；支持周次切换、今日状态、课程详情
- **个人课表**（可选）：用学校统一身份认证的手机号/学号 + 短信验证码登录教务系统，显示属于你一个人的完整课表——选课、重修课都会一并出现
- **本地编辑**：点空格加课、长按拖拽换课/改时间、改色、隐藏、一键还原；编辑只存在本机，学校换课后自动重套（自建课保留、修改安全丢弃并提示）
- **桌面小组件**：跟随当前生效课表（班级或个人），Material You 动态取色
- **课前提醒**：每节课开始前定时通知，可自定义提前量
- **主题**：Material You 动态取色 / 液态玻璃 / 背景图取色 / 5 套主题风格（卡带未来主义、极简主义、瑞士国际主义、学院风、赛博朋克）/ 浅色与暗色
- **更新**：应用内自动检查新版本与课表数据更新（Gitee 通道），数据集可热更、无需重装

## 下载

- **应用内更新**（推荐）：已安装用户在「更多 → 检查更新」或启动时自动获取新版本
- **全新安装**：前往[发布仓 `ccsut-kb-release`](https://gitee.com/yisanspce/ccsut-kb-release)（master 分支托管最新版 APK 与更新清单，国内直连快），或直接获取[最新版本清单 `latest.json`](https://gitee.com/yisanspce/ccsut-kb-release/raw/master/latest.json)（含 APK 直链与 sha256）

## 隐私与信息安全（重要）

这个应用是离线优先的本地应用，我们对你数据的态度非常明确：

- **课表数据只存在你手机上**，不上传到任何第三方服务器
- **个人课表的工作方式**：发送你收到的短信验证码登录学校教务网站，用你本人的会话只拉取你自己的课表。除学校官方系统外，**不经过、也不上报任何中间服务器**
- **验证码用完即弃**；登录会话凭据（Cookie）只保存在内存里，退出登录或杀进程即清空，**不写入手机存储**
- 本地存储仅含：课表缓存、你选的班级、（可选）用于预填的手机号/学号、昵称
- 整段登录与抓取代码全部开源（`data/CasClient.kt`、`data/PersonalRepo.kt`），技术细节见 [`docs/api-personal.md`](docs/api-personal.md)，欢迎审查

完整数据清单、权限逐条用途与删除方式见独立 [隐私政策](PRIVACY.md)。

## 构建

要求：JDK 17、Android SDK（compileSdk 37）。

```bash
git clone https://github.com/yi-san-spce/ccsut-kb.git   # 主站
# 中国区镜像: git clone https://gitee.com/yisanspce/ccsut-kb.git
cd ccsut-kb/android
./gradlew assembleDebug        # 或在 Android Studio 中直接打开 android/ 目录
./gradlew testDebugUnitTest    # 纯函数单元测试 (49 个)
./gradlew checkVersionSync     # 版本双写一致性校验 (build.gradle ↔ Updater.kt)
```

产物在 `app/build/outputs/apk/`。

发布签名：自建 `keystore/release.keystore`，口令写入 `android/local.properties` 的 `keystore.password=<你的口令>`（该文件已被 gitignore，**不要**把口令提交进仓库）。

## 项目结构

```
android/app/src/main/java/com/ccsut/kb/
├── MainActivity.kt        # 界面骨架与状态编排
├── Prefs.kt               # SharedPreferences 封装
├── data/
│   ├── CasClient.kt       # 登录链路: CAS 短信验证码 + aTrust 网关会话激活
│   ├── PersonalRepo.kt    # 个人课表抓取与解析 (教务接口)
│   ├── PersonalCache.kt   # 个人课表本地缓存
│   ├── Models.kt          # 数据模型 + DatasetParser + Repo (班级课表数据仓库)
│   ├── ClassCache.kt      # 生效课表快照 (小组件/提醒进程读取)
│   └── Updater.kt         # 应用内更新 (清单+APK 双通道)
├── ui/                    # 全 Compose: 课表/引导/登录/弹层/今日状态
├── widget/                # 桌面小组件 (RemoteViews)
└── ...
data/                      # 课表数据集与更新清单 (发布产物源)
docs/                      # 技术文档 (接口逆向、应用架构、发版说明)
docs/release-notes/        # 逐版本更新说明 (CHANGELOG.md 为汇总索引)
scripts/                   # 数据抓取与发布脚本
.github/workflows/         # CI: 版本双写校验 + 单元测试 + 构建冒烟
```

## 质量保障

- **单元测试**：解析、周次计算、合并、编辑覆盖层等纯函数 49 个用例（`android/app/src/test/`）
- **CI**：每次推送自动跑版本双写校验 + 测试 + 构建冒烟（v2.10.3 发布事故后的制度化防线）
- **发布门禁**：`scripts/publish.sh` 拒绝在脏工作区发版，APK 版本号从产物本身读取而非配置文件

## 仓库与镜像

- **代码主站（GitHub）**：本仓库，Issue / PR 以此为准
- **更新发布主站 + 中国区镜像（Gitee）**：[yisanspce/ccsut-kb](https://gitee.com/yisanspce/ccsut-kb)——**应用内更新固定走 Gitee**（国内直连快，GitHub 打开困难也不影响升级），源码每次提交双站同步（`scripts/push.sh`），APP 内关于页/引导页均提供双通道入口

## 数据来源与更新体系

- **班级课表数据集**：从学校教务系统公开接口抓取，打包进应用并可通过更新通道热更新（无需发新版 APK）
- **发布仓** [ccsut-kb-release](https://gitee.com/yisanspce/ccsut-kb-release)：只承载分发文件（更新清单 / 数据包 / APK），`scripts/publish.sh` 一条龙发布
- 教务登录链路、接口语义与网关行为的技术文档见 [`docs/api.md`](docs/api.md) 与 [`docs/api-personal.md`](docs/api-personal.md)

## 参与贡献

发现 Bug、想要新功能、或者课表数据不对？欢迎提 [Issue](https://github.com/yi-san-spce/ccsut-kb/issues) 或 PR。提交 PR 前请确认 `./gradlew checkVersionSync testDebugUnitTest` 通过。

## License

[GPL-3.0](LICENSE)。以源码形式二次分发请保持同协议开源；分发 APK 时请按协议提供对应源码获取途径（本仓库 GitHub / Gitee 地址即可）。
