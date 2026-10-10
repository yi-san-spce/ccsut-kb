import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, staticFile , Img } from "remotion";
import { C, F } from "../theme";
import { Phone, CaptionLarge, Noise } from "../components/ui";
import { SHOTS, beatF, localB } from "../beats";

const shot = SHOTS[5]; // b70-b92
const D = shot.to - shot.from;
const L = (n: number) => localB(n);
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const SKINS = [
  { tex: "tex-academia", name: "学院风" },
  { tex: "tex-minimal", name: "极简主义" },
  { tex: "tex-swiss", name: "瑞士国际主义" },
  { tex: "tex-dark", name: "暗色" },
  { tex: "tex-cassette", name: "卡带未来主义" },
  { tex: "tex-cyber", name: "赛博朋克" },
];
const SWEEP_F = 30; // 卡片调校值: 先快后缓 ~38f
const SWEEP_START_BEATS = [0.65, 2.73, 4.8, 6.88, 8.96];
// children 纹理坐标系: 1260×2600; 15° 斜边水平投影 = tan15° × 2600 ≈ 697
const TW = 1260;
const TH = 2600;
const SLANT = 697;

/** S06 主题切换(theme-switch-moves A 斜向扫场): 同一课表在你眼前换肤 ×6 */
export const Shot06Themes: React.FC = () => {
  const f = useCurrentFrame();
  let base = 0;
  let sweeping = -1; // 正在扫向的 skin index
  let sweepP = 0;
  SWEEP_START_BEATS.forEach((b, i) => {
    const start = L(b);
    if (f >= start + SWEEP_F) base = i + 1;
    else if (f >= start) {
      sweeping = i + 1;
      sweepP = (f - start) / SWEEP_F;
    }
  });
  const cur = SKINS[base];
  const moveP = 1 - Math.pow(1 - Math.min(1, sweepP), 3); // 先快后缓

  // 坐实脉冲(每次扫完 1→0.995→1)
  const ends = SWEEP_START_BEATS.map((b) => L(b) + SWEEP_F);
  const sinceEnd = ends.reduce((acc, e) => (f >= e ? f - e : acc), 99);
  const settle = sinceEnd < 12 ? 1 - 0.005 * Math.sin((Math.PI * sinceEnd) / 12) : 1;

  const activeIdx = sweeping >= 0 ? sweeping : base;
  const nameP = interpolate(f, [L(0.32), L(1.62)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  // 扫场几何: 前沿顶点 bx 从画外左到画外右, 斜边 "/" 形
  const bx = -SLANT - 200 + moveP * (TW + SLANT + 400 + SLANT);
  const poly = `polygon(${-2 * TW}px 0%, ${bx}px 0%, ${bx - SLANT}px 100%, ${-2 * TW}px 100%)`;
  const sweepingNow = sweeping >= 0;

  return (
    <AbsoluteFill style={{ background: "linear-gradient(160deg,#FBF8FF 0%,#E9EEFF 55%,#DCE3FF 100%)" }}>
      <Noise />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
        <div style={{ transform: `scale(${settle}) translateY(-26px)` }}>
          <Phone width={440} texture={`textures/${cur.tex}.png`} style={{ borderRadius: 40 }}>
            {sweepingNow && (
              <div style={{ position: "absolute", inset: 0, clipPath: poly }}>
                <Img
                  src={staticFile(`/textures/${SKINS[sweeping].tex}.png`)}
                  style={{ position: "absolute", left: 0, top: 0, width: TW, height: TH }}
                />
                {/* 边界亮线: 从 (bx,0) 到 (bx-SLANT,TH), 15° */}
                <div
                  style={{
                    position: "absolute",
                    left: bx - 3,
                    top: -60,
                    width: 6,
                    height: TH * 1.08,
                    background: "rgba(255,255,255,0.95)",
                    boxShadow: "0 0 18px rgba(255,255,255,0.9), 0 0 40px rgba(120,150,255,0.65)",
                    transformOrigin: "top left",
                    transform: `rotate(15deg)`,
                  }}
                />
              </div>
            )}
          </Phone>
        </div>
      </AbsoluteFill>
      <div style={{ position: "absolute", left: 150, bottom: 150, display: "flex", alignItems: "center", gap: 14, opacity: nameP }}>
        <div style={{ width: 10, height: 10, borderRadius: 3, background: C.blue }} />
        <div style={{ fontFamily: F.sans, fontSize: 40, fontWeight: 700, color: C.ink, letterSpacing: 3 }}>{SKINS[activeIdx].name}</div>
        <div style={{ fontFamily: F.mono, fontSize: 22, color: C.muted, letterSpacing: 2 }}>{`${activeIdx + 1} / 6`}</div>
      </div>
      <CaptionLarge text="六套主题，各有性格" duration={D} y={986} enterFrom={L(0.32)} size={54} />
    </AbsoluteFill>
  );
};
