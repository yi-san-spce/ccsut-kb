import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { CaptionLarge, Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[9]; // b139-b151
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const W = 640;
const H = 252;
// runway-ground-skim 命门: 9f 重力下落、着地即停零回弹、空中提亮
const FALL_START = L(0.97);
const FALL_F = 9;

// 周六真实课表(与 tex-academia 一致, 防 mock 穿帮)
const TODAY = [
  { name: "概率论与数理统计A", room: "7-南204", color: "#59C2E8", t: "8:20" },
  { name: "创业基础(创新思维)", room: "7-南206", color: "#C9526B", t: "10:20" },
  { name: "人工智能导论", room: "7-南306", color: "#DAB542", t: "14:00" },
];

/** S10 桌面小组件(runway-ground-skim 改): 从天而降、零回弹落上桌面 */
export const Shot10Widget: React.FC = () => {
  const f = useCurrentFrame();
  // 重力下落: 距离∝t²
  const g = interpolate(f, [FALL_START, FALL_START + FALL_F], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.quad, // in-quad = 加速砸
  });
  const airborne = f < FALL_START + FALL_F;
  const y = (1 - g) * -860; // 悬浮高度 860px
  const tilt = airborne ? 4 * (1 - g) : 0;
  const bright = airborne ? 1.35 - 0.35 * g : 1;
  // 落定: 微 squatch 零回弹(着地即停, 仅 1 帧压扁)
  const landT = f - (FALL_START + FALL_F);
  const squash = landT === 0 ? 0.955 : landT === 1 ? 0.985 : 1;
  // 桌面
  const deskP = interpolate(f, [L(1.95), L(3.9)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const capP = interpolate(f, [L(3.57), L(4.87)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: "linear-gradient(180deg,#E9EDFB 0%,#D8E1F8 100%)" }}>
      <Noise opacity={0.04} />
      {/* 桌面暗示: 时钟 + 天气占位(轻) */}
      <div style={{ position: "absolute", left: 180, top: 140, fontFamily: F.mono, fontSize: 96, fontWeight: 300, color: "rgba(31,31,38,0.30)", opacity: deskP }}>
        11:08
      </div>
      <div style={{ position: "absolute", right: 200, top: 170, fontFamily: F.sans, fontSize: 34, color: "rgba(31,31,38,0.28)", opacity: deskP }}>
        ☀ 23°
      </div>
      {/* 小组件 */}
      <div
        style={{
          position: "absolute",
          left: 960 - W / 2,
          top: 462 + y,
          width: W,
          transform: `rotate(${tilt}deg) scaleY(${squash})`,
          filter: `brightness(${bright})`,
        }}
      >
        <div
          style={{
            width: W,
            height: H,
            borderRadius: 34,
            background: "rgba(250,248,255,0.92)",
            boxShadow: airborne
              ? "0 40px 90px rgba(31,31,38,0.35)"
              : "0 22px 50px rgba(31,31,38,0.22), 0 4px 14px rgba(31,31,38,0.10)",
            border: "1.5px solid rgba(255,255,255,0.9)",
            padding: "26px 30px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ fontFamily: F.sans, fontSize: 26, fontWeight: 700, color: C.ink }}>
              今天 · 10.10 周六
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <div style={{ width: 20, height: 20, borderRadius: 6, background: C.blue }} />
              <div style={{ fontFamily: F.sans, fontSize: 20, color: C.muted }}>课表通</div>
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, marginTop: 18 }}>
            {TODAY.map((c) => (
              <div key={c.name} style={{ borderRadius: 16, background: c.color, opacity: 0.92, padding: "10px 12px", height: 128, overflow: "hidden" }}>
                <div style={{ fontFamily: F.mono, fontSize: 17, color: "rgba(255,255,255,0.85)" }}>{c.t}</div>
                <div style={{ fontFamily: F.sans, fontSize: 21, fontWeight: 600, color: "#fff", lineHeight: 1.3, marginTop: 6, overflow: "hidden" }}>
                  {c.name}
                </div>
                <div style={{ fontFamily: F.sans, fontSize: 16, fontWeight: 400, opacity: 0.85, marginTop: 2, overflow: "hidden" }}>
                  {c.room}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
      <CaptionLarge text="桌面小组件，一眼看今天" duration={D} y={952} enterFrom={L(3.57)} size={54} />
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 130, textAlign: "center", fontFamily: F.sans, fontSize: 28, color: C.muted, opacity: capP, letterSpacing: 3 }}>
        跟随当前课表 · Material You 动态取色
      </div>
    </AbsoluteFill>
  );
};
