# 更新日志

本文件记录对外发布历史。每个版本的完整说明（现象 / 根因 / 修复的写法自 v2.10.x 起）在 [`docs/release-notes/`](docs/release-notes/) 下逐版本成文；应用内更新弹窗展示的文案由 `scripts/publish.sh` 从对应版本的 release-notes 自动提取。

APK 课表数据集可独立于应用版本热更（无需重装），数据集版本见各版本说明。

## 正式发布

| 版本 | 要点 |
|---|---|
| [v1.1.0](docs/release-notes/v1.1.0.md) | 闹钟模式（课前全屏响铃，setAlarmClock 语义不依赖通知权限）；修复提醒全灭（通知权限获取链/三开关解耦/迟到容差放宽/VIBRATE） |
| [v1.0.0](docs/release-notes/v1.0.0.md) | **首个公开正式版**（2026-10）。全量功能 + 全盘代码审查加固：12 处稳定性修复、隐私加固、49 个单元测试、GitHub Actions CI、开源协议 GPL-3.0。此前的 v1.x–v2.12 为内部版本号，自本版起重置为 1.x 对外计数 |

## 内部迭代存档（v1.0.0 之前）

以下为项目公开发布前的内部版本号演进，留存作开发历史：

| 版本 | 要点 |
|---|---|
| [v2.11.3](docs/release-notes/v2.11.3.md) | 修复冷启动课表类型不记忆（个人课表被静默回退班级课表） |
| [v2.11.2](docs/release-notes/v2.11.2.md) | 修复背景图模式玻璃顶栏与状态栏的割裂 |
| [v2.11.1](docs/release-notes/v2.11.1.md) | 外观卡平铺主题色卡，主题风格点选直达 |
| [v2.11.0](docs/release-notes/v2.11.0.md) | 更多页成熟化改版；检查更新收进关于页脚 |
| [v2.10.4](docs/release-notes/v2.10.4.md) | v2.10.3 启动闪退热修（发布流程竞态事故，publish.sh 增加干净工作区门禁） |
| [v2.10.3](docs/release-notes/v2.10.3.md) | 浅色主题课程块显色度重做（加深加饱 + 隔位跨步取色） |
| [v2.10.2](docs/release-notes/v2.10.2.md) | 时间线常驻（课间/午休钳位显示）+ 拖拽单周化 |
| [v2.10.1](docs/release-notes/v2.10.1.md) | 主题质感机制：每主题精选课程块色板 + 防撞色分配 |
| [v2.10.0](docs/release-notes/v2.10.0.md) | 5 套主题风格包（卡带/极简/瑞士/学院/赛博）× 光暗 |
| [v2.9.7](docs/release-notes/v2.9.7.md) | 修复提醒不弹通知（targetSdk 36 精确闹钟默认拒绝） |
| [v2.9.0–v2.9.6](docs/release-notes/) | 交互统一、玻璃质感、圆角体系、DevSheet 等打磨 |
| [v2.8.0](docs/release-notes/v2.8.0.md) | 全项目开源（本仓库），引导页双通道入口 |
| [v2.7.0](docs/release-notes/v2.7.0.md) | 个人课表上线（教务短信验证码登录，直连学校系统） |
| [v2.6.0–v2.6.1](docs/release-notes/) | 本地课表编辑（加课/拖拽换课/隐藏/还原）；拖拽体验四轮修复 |
| [v2.5.0–v2.5.1](docs/release-notes/) | 应用内自动检查更新 + APK 更新弹窗 |
| [v2.4.0](docs/release-notes/v2.4.0.md) | 更新通道迁移 Gitee，今日状态栏文案体系 |
| [v2.3.0](docs/release-notes/v2.3.0.md) | 桌面小组件、课前提醒、课程颜色、背景图 |

更早版本（内部 v1.x / v2.0–v2.2）为内测迭代，见 git 提交历史。
