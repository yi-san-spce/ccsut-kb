// 拍网格 —— 单一事实源（analysis/3strikers.json）
// BGM=3 Strikers(用户自备, Mixkit 之外的网源曲, 版权自查), 使用窗口源 8.0s 起(借安静铺垫做晨光开场)
// 全曲拟合: BPM=80.50, T=0.74534s, t0=12.1541s(曲内), 残差 mean 7.0ms —— 网格可信
// 片内拍 n 的曲内时刻 = 8.0 + (SOURCE_BEAT0 + n*T), 其中 SOURCE_BEAT0=4.154 为曲内 b0 的片内时刻
export const FPS = 30;
export const BEAT_INT = 0.74534; // 秒/拍(80.5 BPM)
export const SOURCE_BEAT0 = 4.154; // 曲内 b0 的片内时刻(秒)
export const OUTPUT_AUDIO_OFFSET_SEC = 0; // 渲后回测若测得输出偏移则填此

export const beatT = (n: number) => SOURCE_BEAT0 + n * BEAT_INT + OUTPUT_AUDIO_OFFSET_SEC;
export const beatF = (n: number) => Math.round(beatT(n) * FPS);

export const BEATS_TOTAL = 166;
export const DURATION = beatF(BEATS_TOTAL);

export type Shot = { id: string; from: number; to: number };
const s = (id: string, b0: number, b1: number): Shot => ({
  id,
  from: id === "s01-dawn" ? 0 : beatF(b0), // S01 从片头 0 起(借曲子前 8s 铺垫)
  to: beatF(b1),
});

/** 分镜注册表 v2(提速版) —— STORYBOARD.md 的帧级时间轴（镜头边界全落拍上）
 * 270 拍 ≈ 2:07-2:11 @124-127BPM；hold 标准化：品牌/收场 ≥1.5拍，普通镜头 ≤2拍 */
export const SHOTS: Shot[] = [
  s("s01-dawn", 0, 2),
  s("s02-brand", 2, 8),
  s("s03-openweek", 8, 15),
  s("s04-statuscard", 15, 21),
  s("s05-weekswitch", 21, 26),
  s("s06-themes", 26, 40),
  s("s07-wallpaper", 40, 48),
  s("s08-reminders", 48, 55),
  s("s09-alarm", 55, 63),
  s("s10-widget", 63, 69),
  s("s11-safe-local", 69, 76),
  s("s12-safe-pledge", 76, 82),
  s("s13-thanks", 82, 90),
  s("s14-community", 90, 96),
  s("s15-chapter-dev", 96, 102),
  s("s16-opensource", 102, 110),
  s("s17-pipeline", 110, 119),
  s("s18-dataset", 119, 130),
  s("s19-privacy", 130, 136),
  s("s20-tests", 136, 143),
  s("s21-invite", 143, 149),
  s("s22-outro", 149, 166),
];

/** 镜头内局部拍 n → 镜头内局部帧(与镜头起点无关; 绝对帧 = shot.from + localB(n)) */
export const localB = (n: number) => Math.round(n * BEAT_INT * FPS);
export const localBeat = localB;
