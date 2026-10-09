## 改动说明

<!-- 一句话说清做了什么、为什么这么做 -->

## 改动类型

- [ ] 修复 Bug（fix）
- [ ] 新功能（feat）
- [ ] 重构 / 性能（refactor / perf）
- [ ] 文档（docs）
- [ ] 构建 / CI（build / ci）
- [ ] 测试（test）
- [ ] 其他（chore / style）

## 自测清单

- [ ] `./gradlew checkVersionSync testDebugUnitTest` 通过
- [ ] 模拟器或真机验证过主路径（课表浏览 / 切换周次 / 今日状态）
- [ ] 提交信息符合 Conventional Commits（见 [CONTRIBUTING.md](../CONTRIBUTING.md)）
- [ ] 涉及版本号改动时，`build.gradle` 与 `Updater.kt` 的 `BuildVersion` 双写已同步
