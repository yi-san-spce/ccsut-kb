# 文档中心

这里是「长工课表通」的开发者文档通道。产品入口见[主 README](../README.md)。

## 使用者

| 文档 | 内容 |
|---|---|
| [README](../README.md) | 功能介绍 / 下载 / 构建 |
| [隐私政策](../PRIVACY.md) | 数据清单、权限逐条用途、删除方式 |
| [更新日志](../CHANGELOG.md) | 全部版本演进索引 |
| [参与贡献](../CONTRIBUTING.md) | 环境搭建、提交与版本规范、PR 与发版流程 |

## 开发者

| 文档 | 内容 |
|---|---|
| [app.md](app.md) | **应用全流程文档**：项目结构、功能-入口-实现对照、构建与发布 |
| [api.md](api.md) | 班级课表数据链路：教务公开接口、aTrust 网关行为、数据集生成 |
| [api-personal.md](api-personal.md) | 个人课表全链路逆向：CAS 短信登录 + aTrust 零信任网关 + 教务接口语义 |
| [release-notes/](release-notes/) | 逐版本更新说明（现象/根因/修复），应用内更新弹窗文案的源 |

## 代码地图

```
android/app/src/main/java/com/ccsut/kb/
├── MainActivity.kt          # 界面骨架与状态编排
├── data/
│   ├── CasClient.kt         # CAS 短信验证码登录 + aTrust 会话激活
│   ├── PersonalRepo.kt      # 个人课表抓取与解析
│   ├── Repo.kt              # 班级课表内置数据集
│   ├── ClassCache.kt        # 生效课表快照（小组件/提醒进程读取）
│   └── Updater.kt           # 应用内更新（清单 + APK 双通道）
├── ui/                      # 全 Compose：课表/引导/登录/弹层/今日状态
├── widget/                  # 桌面小组件（RemoteViews）
└── reminder/                # 课前提醒
data/                        # 课表数据集与更新清单（发布产物源）
scripts/
├── scrape.py                # 教务数据全量抓取
├── publish.sh               # 一条龙发版（Gitee 更新通道 + GitHub Release）
└── push.sh                  # 源码双站推送
```
