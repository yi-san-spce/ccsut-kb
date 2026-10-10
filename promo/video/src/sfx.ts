// SFX 钉帧表 —— 单一事实源(相对拍号 + 输出偏移补偿, 绝不写裸帧号)
// 规则: sound-design.md — 电影系词汇(whoosh/impact/riser/sparkle/transition),
// ui/ 仅放行真实拟音(switch-*/pop), 长样本显式 durationInFrames, 连发音量阶梯递减。
import { SHOTS, beatF, localB } from "./beats";

const S = (id: string) => SHOTS.find((s) => s.id === id)!;

// 本管线(Remotion 4.x + AAC 48kHz mp4)实测输出音轨偏移, 渲后交叉相关校准
const OUTPUT_AUDIO_OFFSET_F = 1.28;
const PEAK_F: Record<string, number> = {
  "impact/impact-deep-whoosh.mp3": 2, // 技能档案实测
};
const sfxFrom = (targetPeakF: number, src: string) =>
  Math.max(0, Math.round(targetPeakF - (PEAK_F[src] ?? 0) - OUTPUT_AUDIO_OFFSET_F));

const A = "sfx";
// 便利: 镜头内拍 n 的绝对目标帧
const at = (id: string, beat: number) => {
  const s = S(id);
  return s.from + localB(beat);
};

export type Sfx = { from: number; src: string; volume: number; durationInFrames?: number };

export const SFX: Sfx[] = [
  // ---- S01 晨光开场 ----
  { from: sfxFrom(S("s01-dawn").from + 4, `${A}/transition/sweep-short.mp3`), src: `${A}/transition/sweep-short.mp3`, volume: 0.3 }, // 太阳描边起笔
  { from: sfxFrom(at("s01-dawn", 12), `${A}/light/sparkle-touch.mp3`), src: `${A}/light/sparkle-touch.mp3`, volume: 0.28 }, // 表情完成+手写注
  // slam① 主文案落定(b14, 全片大 slam #1)
  { from: sfxFrom(S("s02-brand").from, `${A}/impact/impact-deep-whoosh.mp3`), src: `${A}/impact/impact-deep-whoosh.mp3`, volume: 0.55 },
  // ---- S02 品牌卡 ----
  { from: sfxFrom(S("s02-brand").from + 2, `${A}/transition/swoosh-quick.mp3`), src: `${A}/transition/swoosh-quick.mp3`, volume: 0.32 }, // 图标弹落
  { from: sfxFrom(at("s02-brand", 5), `${A}/ui/pop.mp3`), src: `${A}/ui/pop.mp3`, volume: 0.38 }, // chip×3 阶梯
  { from: sfxFrom(at("s02-brand", 7), `${A}/ui/pop.mp3`), src: `${A}/ui/pop.mp3`, volume: 0.33 },
  { from: sfxFrom(at("s02-brand", 9), `${A}/ui/pop.mp3`), src: `${A}/ui/pop.mp3`, volume: 0.28 },
  // ---- S03 打开即看 ----
  { from: sfxFrom(S("s03-openweek").from + 4, `${A}/transition/whoosh-big.mp3`), src: `${A}/transition/whoosh-big.mp3`, volume: 0.38 }, // 手机入场推近
  { from: sfxFrom(at("s03-openweek", 8), `${A}/transition/transition-soft.mp3`), src: `${A}/transition/transition-soft.mp3`, volume: 0.28 }, // 页面巡览
  // ---- S04 状态卡 ----
  { from: sfxFrom(S("s04-statuscard").from + 6, `${A}/text/chalk-line.mp3`), src: `${A}/text/chalk-line.mp3`, volume: 0.3 }, // 手绘椭圆
  // ---- S05 周次切换 ----
  { from: sfxFrom(S("s05-weekswitch").from + 2, `${A}/paper/paper-page-turn.mp3`), src: `${A}/paper/paper-page-turn.mp3`, volume: 0.38, durationInFrames: 40 }, // 翻页
  { from: sfxFrom(at("s05-weekswitch", 5), `${A}/ui/switch-tap.mp3`), src: `${A}/ui/switch-tap.mp3`, volume: 0.35 }, // 回到本周按钮
  // ---- S06 主题扫场 ×5(扫场 whoosh + 就地换肤开关拟音, 音量阶梯) ----
  ...[1, 4.2, 7.4, 10.6, 13.8].flatMap((b, i) => [
    { from: sfxFrom(at("s06-themes", b), `${A}/transition/whoosh-fast.mp3`), src: `${A}/transition/whoosh-fast.mp3`, volume: 0.36 - i * 0.03 },
    { from: sfxFrom(at("s06-themes", b + 2.6), `${A}/ui/switch-light.mp3`), src: `${A}/ui/switch-light.mp3`, volume: 0.3 - i * 0.03 },
  ]),
  // ---- S07 壁纸网格 ×4 踩拍 pop ----
  ...[0.5, 2.5, 4.5, 6.5].map((b, i) => ({
    from: sfxFrom(at("s07-wallpaper", b), `${A}/ui/pop.mp3`),
    src: `${A}/ui/pop.mp3`,
    volume: 0.4 - i * 0.04,
  })),
  // ---- S08 提醒设置 ×5 行 ----
  ...[0.5, 2.5, 4.5, 6.5, 8.5].map((b, i) => ({
    from: sfxFrom(at("s08-reminders", b), `${A}/ui/pop.mp3`),
    src: `${A}/ui/pop.mp3`,
    volume: 0.38 - i * 0.035,
  })),
  // ---- S09 闹钟 slam②(b126 真实瞬态) ----
  { from: sfxFrom(at("s09-alarm", 2.5) - 6, `${A}/transition/whoosh-big.mp3`), src: `${A}/transition/whoosh-big.mp3`, volume: 0.38 }, // 逼近
  { from: sfxFrom(at("s09-alarm", 2.5), `${A}/impact/impact-deep-whoosh.mp3`), src: `${A}/impact/impact-deep-whoosh.mp3`, volume: 0.6 }, // 砸入(全片 SFX 峰值之一)
  // ---- S10 小组件 ----
  { from: sfxFrom(at("s10-widget", 2), `${A}/transition/whoosh-fast.mp3`), src: `${A}/transition/whoosh-fast.mp3`, volume: 0.32 }, // 下落
  { from: sfxFrom(at("s10-widget", 2) + 9, `${A}/impact/bass-hit-short.mp3`), src: `${A}/impact/bass-hit-short.mp3`, volume: 0.38 }, // 零回弹落地
  // ---- S11 数据安全(安静段, 极简) ----
  { from: sfxFrom(at("s11-safe-local", 5), `${A}/light/light-aura.mp3`), src: `${A}/light/light-aura.mp3`, volume: 0.22, durationInFrames: 70 }, // 盾牌生长余韵
  // ---- S12 三条承诺盖章 ----
  ...[0.5, 3, 5.5].map((b, i) => ({
    from: sfxFrom(at("s12-safe-pledge", b), `${A}/impact/bass-hit-short.mp3`),
    src: `${A}/impact/bass-hit-short.mp3`,
    volume: 0.34 - i * 0.04,
  })),
  // ---- S13 感谢: 留白(无 SFX) ----
  // ---- S14 社区 CTA ----
  { from: sfxFrom(at("s14-community", 0.5), `${A}/light/sparkle-touch.mp3`), src: `${A}/light/sparkle-touch.mp3`, volume: 0.26 }, // 涟漪
  ...[2, 4, 6].map((b, i) => ({
    from: sfxFrom(at("s14-community", b), `${A}/ui/pop.mp3`),
    src: `${A}/ui/pop.mp3`,
    volume: 0.32 - i * 0.04,
  })),
  { from: sfxFrom(at("s14-community", 4), `${A}/transition/swoosh-quick.mp3`), src: `${A}/transition/swoosh-quick.mp3`, volume: 0.28 }, // 二维码卡
  // ---- S15 章节转场 ----
  { from: sfxFrom(at("s15-chapter-dev", 0.5), `${A}/transition/sweep-fast.mp3`), src: `${A}/transition/sweep-fast.mp3`, volume: 0.35 }, // 分隔轴拉出
  { from: sfxFrom(at("s15-chapter-dev", 3), `${A}/transition/air-woosh-deep.mp3`), src: `${A}/transition/air-woosh-deep.mp3`, volume: 0.35 }, // 翻暗
  // ---- S16 开源代码 ----
  { from: sfxFrom(at("s16-opensource", 1.5), `${A}/text/keyboard.mp3`), src: `${A}/text/keyboard.mp3`, volume: 0.3, durationInFrames: 100 }, // 打字窗
  { from: sfxFrom(at("s16-opensource", 8.5), `${A}/light/sparkle-touch.mp3`), src: `${A}/light/sparkle-touch.mp3`, volume: 0.25 }, // GPL 高亮
  // ---- S17 链路图解 ----
  ...[0.5, 2.5, 4.5].map((b, i) => ({
    from: sfxFrom(at("s17-pipeline", b), `${A}/data/sweep-digital.mp3`),
    src: `${A}/data/sweep-digital.mp3`,
    volume: 0.3 - i * 0.04,
  })),
  { from: sfxFrom(at("s17-pipeline", 10), `${A}/transition/transition-soft.mp3`), src: `${A}/transition/transition-soft.mp3`, volume: 0.26 }, // 标题
  // ---- S18 三站旅程 ----
  { from: sfxFrom(at("s18-dataset", 3.96), `${A}/transition/whoosh-fast.mp3`), src: `${A}/transition/whoosh-fast.mp3`, volume: 0.32 }, // 飞行1
  { from: sfxFrom(at("s18-dataset", 9), `${A}/transition/whoosh-fast.mp3`), src: `${A}/transition/whoosh-fast.mp3`, volume: 0.32 }, // 飞行2
  { from: sfxFrom(at("s18-dataset", 0.6), `${A}/text/keyboard.mp3`), src: `${A}/text/keyboard.mp3`, volume: 0.28, durationInFrames: 90 }, // 打字
  { from: sfxFrom(at("s18-dataset", 6.2), `${A}/text/keyboard.mp3`), src: `${A}/text/keyboard.mp3`, volume: 0.28, durationInFrames: 90 },
  { from: sfxFrom(at("s18-dataset", 10.5), `${A}/text/keyboard.mp3`), src: `${A}/text/keyboard.mp3`, volume: 0.28, durationInFrames: 90 },
  // ---- S19 隐私工程 ×3 描边 ----
  ...[0.5, 2.75, 5].map((b, i) => ({
    from: sfxFrom(at("s19-privacy", b), `${A}/text/chalk-line.mp3`),
    src: `${A}/text/chalk-line.mp3`,
    volume: 0.28 - i * 0.04,
  })),
  // ---- S20 里程表 ----
  ...[0.5, 2.5].map((b, i) => ({
    from: sfxFrom(at("s20-tests", b) + 26, `${A}/ui/pop.mp3`), // 各位锁定帧
    src: `${A}/ui/pop.mp3`,
    volume: 0.3 + i * 0.04,
  })),
  { from: sfxFrom(at("s20-tests", 2.5) + 26 + 6, `${A}/impact/bass-hit-short.mp3`), src: `${A}/impact/bass-hit-short.mp3`, volume: 0.34 }, // 终值脉冲
  // ---- S21 邀请共创 ----
  ...[1.5, 3.2, 4.9, 6.6].map((b, i) => ({
    from: sfxFrom(at("s21-invite", b), `${A}/ui/switch-click-quick.mp3`),
    src: `${A}/ui/switch-click-quick.mp3`,
    volume: 0.3 - i * 0.03,
  })),
  // ---- S22 收场 ----
  { from: sfxFrom(at("s22-outro", 3.5), `${A}/transition/whoosh-big.mp3`), src: `${A}/transition/whoosh-big.mp3`, volume: 0.4 }, // 课程块收束
  { from: sfxFrom(at("s22-outro", 5.5), `${A}/transition/transition-snap.mp3`), src: `${A}/transition/transition-snap.mp3`, volume: 0.4 }, // 图标落定
  ...[0, 1, 2, 3, 4].map((i) => ({
    from: sfxFrom(at("s22-outro", 6.5) + i * 5, `${A}/ui/pop.mp3`), // 字标逐字
    src: `${A}/ui/pop.mp3`,
    volume: 0.36 - i * 0.03,
  })),
  { from: sfxFrom(at("s22-outro", 14), `${A}/light/light-spell.mp3`), src: `${A}/light/light-spell.mp3`, volume: 0.28, durationInFrames: 90 }, // 光线绽放
  { from: sfxFrom(at("s22-outro", 18.5), `${A}/riser/riser-cine.mp3`), src: `${A}/riser/riser-cine.mp3`, volume: 0.42, durationInFrames: 57 }, // riser(20拍起, 25拍收)
  // impact③ @yisan 落定(b356, 全片响度峰值)
  { from: sfxFrom(at("s22-outro", 22.5), `${A}/impact/impact-deep-whoosh.mp3`), src: `${A}/impact/impact-deep-whoosh.mp3`, volume: 0.6 },
  { from: sfxFrom(at("s22-outro", 22.5) + 25, `${A}/light/sparkle.mp3`), src: `${A}/light/sparkle.mp3`, volume: 0.34, durationInFrames: 90 }, // sparkle 余韵
];
