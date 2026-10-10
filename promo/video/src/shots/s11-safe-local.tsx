import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise, HandNote } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[10]; // b151-b171
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const Shield: React.FC<{ p: number; checkP: number }> = ({ p, checkP }) => (
  <svg width="440" height="500" viewBox="0 0 440 500" fill="none">
    <path
      d="M220 30 L392 96 V252 C392 372 316 448 220 474 C124 448 48 372 48 252 V96 Z"
      stroke={C.handInk}
      strokeWidth="11"
      strokeLinejoin="round"
      pathLength={1}
      strokeDasharray={1}
      strokeDashoffset={1 - p}
    />
    <path
      d="M148 246 L204 306 L306 186"
      stroke={C.mustard}
      strokeWidth="16"
      strokeLinecap="round"
      strokeLinejoin="round"
      pathLength={1}
      strokeDasharray={1}
      strokeDashoffset={1 - checkP}
    />
  </svg>
);

/** S11 数据安全·上(breakdown 安静段): 手绘盾牌描边生长 —— 数据只在你手机上 */
export const Shot11SafeLocal: React.FC = () => {
  const f = useCurrentFrame();
  const shieldP = interpolate(f, [L(0.32), L(3.25)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const checkP = interpolate(f, [L(3.25), L(4.54)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const titleP = interpolate(f, [L(2.6), L(4.54)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const subP = interpolate(f, [L(4.54), L(6.17)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const float = Math.sin(f * 0.05) * 8;

  return (
    <AbsoluteFill style={{ background: C.paper }}>
      <Noise />
      <AbsoluteFill style={{ alignItems: "center", flexDirection: "row", justifyContent: "center", gap: 130 }}>
        <div style={{ transform: `translateY(${float}px)` }}>
          <Shield p={shieldP} checkP={checkP} />
        </div>
        <div style={{ width: 760 }}>
          <div style={{ fontFamily: F.mono, fontSize: 24, color: C.muted, letterSpacing: 6, opacity: titleP }}>PRIVACY BY DESIGN</div>
          <div
            style={{
              fontFamily: F.sans,
              fontSize: 88,
              fontWeight: 800,
              color: C.ink,
              lineHeight: 1.4,
              marginTop: 24,
              opacity: titleP,
              transform: `translateY(${(1 - titleP) * 26}px)`,
            }}
          >
            你的数据，
            <br />
            只存在<span style={{ color: C.blue }}>你手机</span>上。
          </div>
          <div
            style={{
              fontFamily: F.sans,
              fontSize: 32,
              color: C.muted,
              marginTop: 34,
              lineHeight: 2,
              opacity: subP,
              letterSpacing: 2,
            }}
          >
            无服务器 · 无统计 SDK · 无广告 SDK
            <br />
            不申请定位 / 通讯录 / 相机权限
          </div>
        </div>
      </AbsoluteFill>
      <HandNote text="treat it gently ✎" p={subP} rotate={-3} style={{ right: 170, bottom: 110 }} size={36} />
    </AbsoluteFill>
  );
};
