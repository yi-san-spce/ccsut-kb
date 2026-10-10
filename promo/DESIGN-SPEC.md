# 长工课表通宣传片 · 设计 Spec（v1，2026-10-10）

> 本文件是制作的单一事实源（Single Source of Truth），随阶段推进持续更新。
> 终检 subagent 以此为审查基准。

## 1. 产品简报（已确认）

| 项目 | 决策 | 依据 |
|---|---|---|
| 项目定位 | 长沙工业学院学生课表工具：打开即看、提醒、个性化、离线可用 | README + slogan |
| 目标用户 | 上章：本校学生；下章：社区开发者与贡献者 | 用户两段式需求 |
| 视频用途 | 产品宣传 + 使用引导 + 开源社区招募 | 用户描述 |
| 核心卖点 | ①打开即看 ②课前提醒/闹钟 ③主题/背景取色个性化 ④数据只在本机+全开源 | README + 截图素材 |
| 必须展示 | 今日状态卡、6 主题切换、背景取色、提醒设置、闹钟响铃、小组件、安全声明、Gitee 下载 | 12 张截图 + 用户口述 |
| 首屏主张 | 清晨意象暖开场「新的一天，从看清课表开始。」 | 用户选定 |
| 时长/画幅/语言 | 一支两章 2.5-3min（用户章 ~95s + 开发者章 ~70s）/ 1920×1080 30fps / 全中文字幕 | 用户确认 |
| 音乐/配音 | 内置曲库 BGM 卡点，无配音，交付带/无 BGM 双版本 | 用户确认 |
| 数据合规 | 截图经用户授权；状态栏裁剪；抓取链路**抽象化**（不出现真实域名/厂商名/真实代码原文） | 用户确认 |
| 硬性禁项 | **不做痛点开场**，不复现登录难过程、不影射教务系统 | 用户明确要求 |
| 个人 IP | 结尾太阳头像签名 +「@yisan · 全平台同名」 | 用户要求 |
| 下载引导 | 「Gitee / GitHub 搜索 ccsut-kb」+ 二维码 | Agent 推荐 |

## 2. 视觉方向（定稿：A×B 融合）

> ⚠️ 用户在 styleframe 对比后未即时回复，按 harness 指引采用 Agent 推荐的 A×B 融合作为默认；
> **可随时推翻**，推翻后本节以下的 tokens/分镜需同步重排。

- **骨架 = B 玻璃晨蓝**：产品截图承载面、开发者章图解、亮色光域背景
- **灵魂 = A 晨光手账**：手绘描边母题（箭头/下划线/短标注）、芥末黄暖点、清晨意象开场、收尾太阳签名
- C 课表网格的遗产：开发者章的数据管线图可用网格对齐排版（仅排版借用，不做全片风格）

### 设计 tokens（全片复用，不另造皮肤）

| Token | 值 | 用途 |
|---|---|---|
| paper | `#FBF8FF` | 全片主底色（浅色为主，不压抑） |
| blue | `#3B64D8` | 主色/结构色/主按钮 |
| blue-deep | `#00174A` | 深色文字（玻璃卡上标题） |
| blue-c | `#DCE3FF` | 渐变光域端色/容器 |
| ink | `#1F1F26` | 正文近黑 |
| muted | `#595D72` | 次级文字 |
| mustard | `#DAB542` | 暖点缀（太阳黄，头像采样） |
| hand-ink | `#2A2A2A` | 手绘描边色 |
| glass | rgba(255,255,255,.55) + blur24 + 1.5px 白描边 + 大软影 | 玻璃卡材质 |
| radius | 14 / 22 / 28 / 36 px | 圆角阶梯（源自 APP 8-28dp 体系放大） |
| dark 仅点缀 | `#131318` / `#B7C4FF` | 主题快切镜头、开发者章代码态 |

字体：PingFang SC（主）、Xingkai SC/Kaiti SC（手写标注）、SF Mono（数字/代码/元信息）。
渲染机为本机 Chrome，系统字体确定性可用；如后续换机器需打包字体。

### 动效性格 tokens（品牌→动效推导）

- 品牌两轴：能量中低（工具、从容）× 调性亲和（学生、温暖）→ **亲和友好预设**
- 主时长 ~26f；入场 easing `bezier(0.25, 0.46, 0.45, 0.94)`；落地过冲 ≤1.04；squash ≤0.08
- 手绘描边生长类动效统一用 20-28f 线性描边 + 微抖动（seed 固定）
- 全片一套动效嗓音；转场 whip/fade 系数只许微调不许换预设

## 3. Styleframe 记录

- 对比页 `promo/styleframes/index.html`，9 帧 1920×1080（A/B/C × 开场/核心/收尾）
- 定稿方向 A×B 融合；a1/a3、b1/b3 帧直接作为对应镜头的构图基准
- 头像已入库 `promo/assets/yisan-avatar.png`（1650×1644，芥末黄 #DAB542 / 米白 #F5F5F5）

## 4. 素材清单（12 张真实截图，均经授权）

| 文件（promo/cipe/） | 内容 | 用途 |
|---|---|---|
| …47_27 | 学院风浅色课表 | 主展示镜头（A 帧基准） |
| …48_27 | 极简浅灰课表 | 主题快切 |
| …49_27 | 瑞士白红课表 | 主题快切 |
| …50_27 | 暗色红课表 | 主题快切 |
| …51_27 | 卡带琥珀课表 | 主题快切 |
| …52_27 | 赛博朋克课表 | 主题快切 |
| …54_27 | 提醒设置页 | 提醒/闹钟镜头 |
| …55_27 | 第6周非本周+回到本周 | 周次切换镜头 |
| …56_27 | 背景取色·星空 | 背景取色四连 |
| …57_27 | 背景取色·人像 | 背景取色四连 |
| …58_27 | 背景取色·新年红 | 背景取色四连 |
| …59_27 | 背景取色·灯塔插画 | 背景取色四连 |

## 5. BGM 与节奏（已定稿）- **选型：`bgm-tech-house`**（288.7s，BPM 124.000）。唯一长度覆盖 173s 成片的候选，且为 skill 推荐的产品宣传片 tech-house 强鼓点片种。其余 4 首（104-124s）时长不足；g-eazy/tonight-hiphop 网格验收不合格（match<0.98 / 残差>15ms）。
- 使用窗口：源 21.0651s 起，窗口拟合 BPM=123.999 / T=0.48387s / 残差 max 10.7ms mean 3.5ms —— 网格可信。
- 网格常量：`SOURCE_BEAT0=21.0651`（源真值）、`BEAT_INT=0.48387`、`beatF(n)=round(n×14.5161)`@30fps；渲后回测输出偏移走 `OUTPUT_AUDIO_OFFSET_SEC`。
- 命中表/能量结构存档：`promo/video/analysis/bgm-tech-house/`（beat_data.json + window.json）。
- 能量叙事：中能(0-49s)功能展示 → 深 breakdown(49-70s)数据安全安静段 → 谷底爬升(70-90s)感谢CTA → 重建(90-160s)开发者章 → 满能量(160-176s)收场签名。
- 大 slam 3 处：b14 / b126(真实瞬态) / ~b356；间隔均 ≥16 拍。

## 6. 功能→镜头映射（已定稿，22 镜）

详见 `promo/STORYBOARD.md` 分镜表。选卡依据 gallery/api/library.json（157 卡清单已导出）：

| 功能 | 首选卡 | 备选 |
|---|---|---|
| 晨光开场揭晓 | stroke-segment-build | draw-svg-trace |
| 品牌登场 | blur-slide + 自绘玻璃卡 | brand-ink-open |
| 课表整页展示 | depth-layer-moves | overhead-camera-moves A |
| 今日状态卡特写 | spotlight-hero-card | crash-zoom-punch |
| 周次切换 | page-turn(轻) | wipe |
| 主题切换 | theme-switch-moves | beat-step-list-theme-cycle |
| 背景取色 | panel-grid-moves A | mosaic-reframe |
| 提醒设置 | list-reveal | scan-bracket-sweep |
| 闹钟响铃页 | slam-entrance-moves | impact-feedback |
| 桌面小组件 | runway-ground-skim | morph-from-primitive |
| 数据安全 | 手绘描边字卡(word-relay 变体) | pill-chip-slot-cycle-handled |
| 感谢 | blur-slide(慢) | paper-title-card |
| 社区 CTA | radial-ripple-phone-chips | floating-glossy-label-pills |
| 章节转场 | line-carry-transition + title-demote-to-label | page-turn-transitions |
| 为什么开源 | typing-code-block | typewriter-moves A |
| 抓取链路图解 | ring-diagram-annotation-reveal | bezier-source-converge-merge |
| 数据管线 | terminal-3d | row-embed |
| 隐私工程 | draw-svg-trace | scan-bracket-sweep |
| 工程质量 | odometer-digit-roll | gauge-readout-moves |
| 邀请共创 | pill-slot-cycle | word-relay-filmstrip |
| 收场签名 | logo-shrink-wordmark-lockup + 自绘太阳描边 | ui-to-brand-morph |

## 7. 分镜（v1 已排定）

`promo/STORYBOARD.md`：22 镜 / 364 拍 / 176.1s，含 beatF 时间轴、素材来源、字幕、SFX、转场、hold 预算。

## 8. 制作与回测记录（2026-10-10）

- **逐镜头实现**：22/22 完成，每镜头 `remotion still` 静帧自检（`promo/video/out/qa/`）。
- **整片渲染**：v1（纯画面）与 v2（画面+SFX）各 176.149s，contact sheet 全片回看通过。
- **卡点回测**（渲出音轨 onset vs 设计拍，±80ms 窗）：22 个切点全部 ≤3f，最大 2.27f（b331，BGM 弱瞬态段），其余 ≤0.4f —— **合格**。
- **输出偏移**：transition-snap 探针实测 -15ms（补偿后残差 -0.45f，在 ±0.5f 容差内）；`OUTPUT_AUDIO_OFFSET_F=1.28` 维持。
- **响度**：渲出音轨 max_volume -1.9dB（无削波）。
- **SFX 表**：`promo/video/src/sfx.ts`（相对拍号 + 偏移补偿 + 长样本显式截断；ui/ 仅真实拟音）
- **修复记录（终检后）**：① BGM 裁剪命令修正（-ss 前置，修复 BGM 提前 20s 归零）② S16 文案去除厂商字样（抽象化禁项）③ S18 打字/输出时间轴前移并补收尾字幕 ④ S07 落格前移消空窗 ⑤ S05 补"回到本周"击回动作 ⑥ S10 小组件改周六真实 3 门课 ⑦ S19/S21 文案微调 ⑧ Phone 组件 img 全部换 Remotion `<Img>`（渲染等待图片解码，消白屏）。
- **深色使用说明**：用户章 S08 设置页/S09 闹钟为深色 —— 设置面板截图本身为深色、闹钟响铃为锁屏语境，与 spec "dark 仅点缀"的例外已由独立终检认可（判可接受，夜晚叙事）。
