# 贡献指南

> **给谁看**：所有想参与的人——报告问题、补充数据、写代码、改文档都算。
> **怎么读**：第一次参与从头读；只想提 PR 的直接看「开发流程」和「提交规范」。

感谢关注长工课表通！无论是报告问题、补充数据、写代码还是改文档，都欢迎。

## 环境搭建

| 依赖 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | compileSdk 37（Gradle 会自动装到 `local.properties` 指定的 sdk.dir） |
| IDE | Android Studio 或任意支持 Kotlin 的编辑器 |

```bash
git clone https://github.com/yi-san-spce/ccsut-kb.git
cd ccsut-kb/android
./gradlew assembleDebug          # 仓库自带 Gradle Wrapper，无需额外安装 Gradle
./gradlew installDebug           # 装到连接的设备/模拟器
```

验证环境完整可用：

```bash
./gradlew checkVersionSync testDebugUnitTest
```

## 提交规范（Conventional Commits）

> 2026-10 起，新提交一律采用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)；历史提交保留原样不改写。

```
<type>(<scope>): <中文主题>

<可选正文: 动机、根因、验证方式>
```

| type | 用途 | 示例 |
|---|---|---|
| `feat` | 新功能 | `feat(widget): 小组件支持单周课表` |
| `fix` | 修复 Bug | `fix(reminder): 精确闹钟被拒时降级为非精确提醒` |
| `perf` | 性能优化 | `perf(schedule): 首帧课程块懒加载` |
| `refactor` | 重构（不改行为） | `refactor(data): 抽出 TimeParse 统一防御` |
| `docs` | 文档 | `docs: 补充桌面小组件说明` |
| `test` | 测试 | `test(weeks): 补跨周合并边界用例` |
| `build` | 构建脚本 / 依赖 | `build: 升级 Compose BOM 至 2026.09.00` |
| `ci` | CI 配置 | `ci: 测试报告作为失败产物上传` |
| `chore` | 杂务 | `chore: 清理未用资源` |
| `style` | 格式（不影响语义） | `style: 统一 import 顺序` |

- **发版提交**固定为 `chore(release): vX.Y.Z <主题>`
- 主题行 ≤ 200 字符，技术细节写进正文；正文请沿用本仓风格：**现象 / 根因 / 修复 / 验证**
- 仓库提供提交校验钩子（纯 shell，无额外依赖），建议启用：

```bash
git config core.hooksPath scripts/githooks
```

## 版本规范

- 版本号三段式 `X.Y.Z`（SemVer）：新功能 Y+1，修复 Z+1，不兼容改动 X+1；发版时版本号带**版本类后缀**（全英文）：稳定 `X.Y.Z-stable` / 抢先 `X.Y.Z-pre.YYYYMMDD` / 内测 `X.Y.Z-beta.N`
- 本地开发构建（`assembleDebug` / `installDebug`）自动挂 `-dev` 后缀（开发者版）：不进任何更新通道、不出现在版本列表，也不会被同主版本的发布版催更
- `versionCode` 分段方案见 [release.md](release.md)（三通道防倒挂；40 是旧线性方案最后一个）
- 版本号**双写**在 `android/app/build.gradle` 与 `data/Updater.kt` 的 `BuildVersion`，改一处必须同步另一处；`./gradlew checkVersionSync` 会校验
- 每个发版打 tag `v<versionName>`（annotated，如 `v1.0.1-stable`），由 `scripts/publish.sh` 自动完成

## 开发流程

1. 从 `master` 拉分支（或直接改小改动）
2. 改动 + 补充/调整单测（纯函数测试在 `android/app/src/test/`）
3. 本地跑 `./gradlew checkVersionSync testDebugUnitTest`，模拟器过一遍主路径（课表浏览 / 切周 / 今日状态 / 小组件）
4. 按 Conventional Commits 提交，推送并开 PR（PR 模板有自测清单）
5. CI 全绿后等待 review

UI 改动建议附截图；涉及登录链路（`CasClient`）的改动请格外谨慎并在 PR 中说明验证方式。

## 发版流程（维护者）

完整手册见 **[release.md](release.md)**（版本规则 / 发布通道 / 历史教训），四种推送场景各有一节：**稳定版 / 抢先版（--pre）/ 内测版（--beta，团队私发清单）/ 只更课表数据**。速览（稳定版）：

1. 写 `docs/release-notes/v<versionName>.md`（应用内「软件更新」页文案自动从这里提取）
2. `build.gradle` + `BuildVersion` 双写升版本
3. `scripts/publish.sh --skip --apk <apk路径>` 一条龙：校验工作区干净 → aapt 读产物真实版本 → 生成清单（sha256/notes/sections 注入）→ 自动打 tag 并创建 GitHub Release（APK + versions.json 版本索引 + 说明）
4. `scripts/push.sh` 推送源码
5. 数据有变化时去掉 `--skip`，由维护者按内部流程重新构建数据集（细节因敏感因素暂不公开）

## 课表数据维护

数据集由维护者从学校教务系统公开接口全量获取（接口说明因敏感因素，暂不提供相关的文档说明）。发现数据问题请到[问题反馈](https://github.com/yi-san-spce/ccsut-kb/issues/new?template=bug_report.yml)提 Issue，问题类型选「课表数据不对」，附教务系统截图最快。

## 协议

提交即表示你同意代码以 [GPL-3.0](../../LICENSE) 开源。
