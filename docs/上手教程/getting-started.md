# 开发者上手：15 分钟跑起来

> **给谁看**：想在本机构建、运行、调试「长工课表通」的开发者。不需要 Android 经验，会装软件、会用终端就行。
> **读完你能**：配好环境、构建出 APK、装到设备上跑起来，并且知道提交代码前要过哪些检查。
> 只想装 APP 用？不用看本文，去 [README 下载区](../../README.md#-下载给用户) 下载安装包即可。

## 1. 准备环境

你需要三样东西：

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | `brew install openjdk@17`；如果装了 Android Studio，它自带的 JBR 也能直接用 |
| Android SDK | compileSdk 37 | 不用手动装：Gradle 首次构建会按需自动下载 |
| Gradle | 不用装 | 仓库自带 Wrapper，首次构建时自动下载 Gradle 9.5.0 |

推荐用 **Android Studio**（最新版）做 IDE，但任何支持 Kotlin 的编辑器都可以。

> **网络说明**：`settings.gradle` 在本机默认走阿里云镜像（国内网络友好），检测到 CI 环境会自动切回官方源。本地不需要做任何配置。

## 2. 拉代码并构建

```bash
git clone https://github.com/yi-san-spce/ccsut-kb.git
cd ccsut-kb/android
./gradlew assembleDebug        # 构建 debug APK
```

**你会看到**：首次构建会自动下载 Gradle 和依赖，可能需要几分钟到十几分钟（取决于网络），最后以 `BUILD SUCCESSFUL` 结尾。构建产物在 `android/app/build/outputs/apk/debug/` 下。

**装到设备上**：手机插 USB 并打开开发者选项，或启动一个模拟器，然后：

```bash
./gradlew installDebug         # 装到已连接的设备/模拟器
```

用 Android Studio 的话更简单：直接打开 `android/` 目录，等索引完成，选好设备点 ▶ 运行。

## 3. 验证环境（提交前必跑）

这两条命令跑通了，说明本地环境是好的：

```bash
./gradlew checkVersionSync     # 校验 build.gradle ↔ Updater.kt 版本号双写一致
./gradlew testDebugUnitTest    # 跑 54 个纯函数单元测试
```

**你会看到**：两条都以 `BUILD SUCCESSFUL` 结尾，测试任务全绿、0 failed。

CI（GitHub Actions）在每次 push/PR 时跑同样两项加构建冒烟。本地绿了再推，别让 CI 替你跑第一步。

> **什么是「版本双写」**：应用版本号同时写在 `build.gradle` 和 `Updater.kt` 两处——前者管打包，后者管应用内检查更新。`checkVersionSync` 就是在校验这两处没有写岔。背景见[架构文档 · 更新双通道](../原理与接口/architecture.md#更新双通道)。

## 4. 首次启动与开发者模式

App 第一次启动会进入欢迎引导：选班级，或者登录教务拉个人课表。

**开发者模式**是个内置调试面板。开法：更多 → 关于 → **连点版本号 7 次**（长按版本号关闭）。面板里有三组工具：

- **时间旅行**：把 App 认为的「今天」偏移 -30 到 +60 天。周日晚上想测「周一早八提醒」？偏移一天就行。代码里所有「今天」都从 `util/Dev.kt` 的 `KbClock` 取、不直接调 `LocalDate.now()`，就是为了配合这个功能。
- **即时操作**：重排提醒闹钟、发测试通知、10 秒测试闹钟/响铃、重载数据、模拟 APK 更新弹窗。
- **诊断报告**：一键生成现场（设备/版本/数据/设置/日志）并系统分享——远程帮人排障时用。

看日志：

```bash
adb logcat -s kb-dev
```

## 5. 提交规范速览

提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)，正文用中文写技术细节：

```
feat(widget): 小组件支持单周课表
fix(reminder): 精确闹钟被拒时降级为非精确提醒
chore(release): v1.0.1 修复xx
```

类型只有十种：`feat` `fix` `perf` `refactor` `docs` `test` `build` `ci` `chore` `style`。正文（空一行之后）沿用本仓「现象 / 根因 / 修复 / 验证」的四段式写法。

仓库自带提交校验钩子，建议启用：

```bash
git config core.hooksPath scripts/githooks
```

完整规范见 [CONTRIBUTING.md](../开发者/CONTRIBUTING.md)。

## 6. 常见构建问题

| 症状 | 原因与处理 |
|---|---|
| `Unable to locate a Java Runtime` | 终端找不到 JDK 17。设置 `export JAVA_HOME=<JDK17 路径>`（macOS Homebrew 安装的话，路径是 `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`） |
| 依赖解析失败 / `Repository maven2 is disabled` | 阿里云镜像偶发不稳定。临时用 `CI=true ./gradlew …` 强制走官方源 |
| `local.properties` 缺失 | 在 `android/` 下新建这个文件，写一行 `sdk.dir=<SDK 路径>`；要发版的话再加一行 `keystore.password=<签名口令>` |
| material3 `LoadingIndicator` unresolved | 这个组件在 1.5.0-alpha 里，stable 1.4.0 没有——别在 stable 里 import 它 |
| 冷启动深色模式白闪 | 已修复（`values-night/themes.xml`），那行别删 |

## 下一步

- 动代码前先读[架构总览](../原理与接口/architecture.md)——数据怎么流动、每个模块为什么存在、改代码的红线在哪
- 找某个功能落在哪个文件，查[功能-入口-实现对照](features.md)
- 提 PR 的完整流程与版本规则，见[贡献指南](../开发者/CONTRIBUTING.md)
