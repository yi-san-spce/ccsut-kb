# 安全政策

## 支持的版本

| 版本 | 支持 |
|---|---|
| 1.0.x | ✅ |
| 更早的内部版本（v1.x–v2.x 内部号） | ❌ |

## 如何报告漏洞

**请勿以公开 Issue 形式报告安全漏洞。**

使用 GitHub 的[私下安全报告](https://github.com/yi-san-spce/ccsut-kb/security/advisories/new)提交，或通过仓库主页维护者资料页提供的联系方式私下报告。

请在报告中包含：

- 漏洞类型与影响的组件（如 `data/CasClient.kt` 登录链路、更新校验、本地存储）
- 复现步骤或概念验证（PoC）
- 影响评估（会碰到什么数据、什么条件可触发）

## 我们承诺

- **72 小时内**确认收到
- 确认后与你沟通评估与修复时间线
- 修复随下一个版本发布，发布说明中致谢（除非你希望匿名）
- 在修复版本发布前不公开披露细节

## 安全设计要点（供审查参考）

- **登录链路**：短信验证码直连学校 CAS 与教务系统，验证码用完即弃，会话 Cookie 仅存内存不落盘，无任何中间服务器——相关代码全部在 [`data/CasClient.kt`](android/app/src/main/java/com/ccsut/kb/data/CasClient.kt) 与 [`data/PersonalRepo.kt`](android/app/src/main/java/com/ccsut/kb/data/PersonalRepo.kt)，欢迎审查
- **更新通道**：Gitee 发布仓清单 + SHA-256 校验 + 强制 HTTPS 下载地址（明文 http 被拒绝）
- **本地存储**：仅课表缓存、所选班级、可选的预填手机号与昵称

## 不在范围内

- 学校教务系统 / CAS / aTrust 网关本身的漏洞——请向学校信息化部门报告
- 需要已解锁设备物理接触的攻击
