import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[12]; // b189-b205
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

/** S13 感谢(全片最静的一拍): 心里话慢速浮现 */
export const Shot13Thanks: React.FC = () => {
  const f = useCurrentFrame();
  const line1P = interpolate(f, [L(0.97), L(3.25)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const line2P = interpolate(f, [L(3.25), L(5.52)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const sunP = interpolate(f, [L(5.52), L(7.14)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const drift = Math.sin(f * 0.04) * 6;

  return (
    <AbsoluteFill style={{ background: C.paper, alignItems: "center", justifyContent: "center" }}>
      <Noise />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column" }}>
        <div
          style={{
            fontFamily: F.sans,
            fontSize: 76,
            fontWeight: 700,
            color: C.ink,
            textAlign: "center",
            lineHeight: 1.6,
            opacity: line1P,
            transform: `translateY(${(1 - line1P) * 24 + drift}px)`,
            letterSpacing: 3,
          }}
        >
          如果它能真正帮到你，
        </div>
        <div
          style={{
            fontFamily: F.sans,
            fontSize: 76,
            fontWeight: 700,
            color: C.blue,
            textAlign: "center",
            opacity: line2P,
            transform: `translateY(${(1 - line2P) * 24}px)`,
            letterSpacing: 3,
            marginTop: 8,
          }}
        >
          这是我的荣幸。
        </div>
        {/* 小太阳一点(收束到结尾 IP 的伏笔) */}
        <svg width="72" height="72" viewBox="0 0 400 400" fill="none" style={{ marginTop: 60, opacity: sunP }}>
          <circle cx="202" cy="202" r="118" fill={C.mustard} opacity={sunP} />
          <circle cx="196" cy="199" r="116" stroke={C.handInk} strokeWidth="14" />
          <path d="M143 232 q56 52 114 -4" stroke={C.handInk} strokeWidth="14" strokeLinecap="round" />
          <path d="M156 178 q14 -22 30 -2" stroke={C.handInk} strokeWidth="14" strokeLinecap="round" />
          <path d="M222 176 q14 -22 30 -2" stroke={C.handInk} strokeWidth="14" strokeLinecap="round" />
        </svg>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
