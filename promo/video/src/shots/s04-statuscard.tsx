import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, staticFile , Img } from "remotion";
import { C, F } from "../theme";
import { CaptionLarge, Noise } from "../components/ui";
import { SHOTS, beatF, localB } from "../beats";

const shot = SHOTS[3]; // b46-b60
const D = shot.to - shot.from;
const L = (n: number) => localB(n);
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

/** S04 今日状态卡聚光(spotlight-hero-card): 全场让位, 卡片缓推近, 手绘圈重点 */
export const Shot04StatusCard: React.FC = () => {
  const f = useCurrentFrame();
  const inP = interpolate(f, [L(0.0), L(1.95)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 缓推: 慢而稳
  const push = interpolate(f, [L(1.3), D], [1, 1.14], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.25, 0.46, 0.45, 0.94),
  });
  // 手绘椭圆圈住「喝口水歇歇」(卡内偏右区)
  const ovalP = interpolate(f, [L(3.9), L(5.3)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const capP = interpolate(f, [L(4.2), L(5.5)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: C.paper }}>
      <Noise />
      {/* 聚光: 单点柔光, 卡片后方 */}
      <div
        style={{
          position: "absolute",
          left: 460,
          top: 40,
          width: 1000,
          height: 700,
          borderRadius: "50%",
          background: "radial-gradient(ellipse, rgba(218,181,66,0.20), rgba(218,181,66,0) 62%)",
          opacity: inP,
        }}
      />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 70 }}>
        <div style={{ position: "relative", transform: `scale(${0.94 + 0.06 * push})`, opacity: inP }}>
          <Img
            src={staticFile("/textures/cut-status-card.png")}
            style={{ width: 1240, borderRadius: 44, boxShadow: "0 40px 100px rgba(31,31,38,0.18), 0 8px 28px rgba(31,31,38,0.10)", display: "block" }}
          />
          {/* 手绘椭圆 */}
          <svg
            width="760"
            height="150"
            viewBox="0 0 760 150"
            fill="none"
            style={{ position: "absolute", right: 60, top: -18, overflow: "visible" }}
          >
            <path
              d="M60 96 C 120 18, 640 6, 706 62 C 748 100, 560 142, 300 140 C 140 139, 40 122, 60 96 Z"
              stroke={C.mustard}
              strokeWidth="9"
              strokeLinecap="round"
              pathLength={1}
              strokeDasharray={1}
              strokeDashoffset={1 - ovalP}
              transform="rotate(-2 380 75)"
            />
          </svg>
        </div>
        <div
          style={{
            fontFamily: F.sans,
            fontSize: 56,
            fontWeight: 600,
            color: C.muted,
            opacity: capP,
            transform: `translateY(${(1 - capP) * 20}px)`,
            letterSpacing: 2,
          }}
        >
          连休息，都替你安排好了。
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
