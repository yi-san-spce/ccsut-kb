import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, staticFile , Img } from "remotion";
import { C, F } from "../theme";
import { Phone, CaptionLarge, Noise } from "../components/ui";
import { SHOTS, beatF, localB } from "../beats";

const shot = SHOTS[2]; // b26-b46
const D = shot.to - shot.from;
const L = (n: number) => localB(n);
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

/** S03 打开即看: 手机入场 → 2.5D 视差巡览周课表(depth-layer-moves) */
export const Shot03OpenWeek: React.FC = () => {
  const f = useCurrentFrame();
  // 手机从右侧滑入并转正
  const inP = interpolate(f, [L(0.0), L(2.6)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 相机推进: 整机缓推近
  const zoom = interpolate(f, [L(2.6), L(6.8), D], [1, 1.05, 1.05], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 课表是整页, 不做滚动: 只推近 + 呼吸式浮层视差(中段分离、两端归零)
  const panProg = interpolate(f, [L(2.6), L(7.79)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.25, 0.46, 0.45, 0.94),
  });
  const parallax = Math.sin(Math.PI * panProg) * 26;
  const capP = interpolate(f, [L(3.9), L(5.84)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  const layer = (box: { x: number; y: number }, depth: number) => ({
    position: "absolute" as const,
    left: box.x,
    top: box.y,
    transform: `translateY(${-parallax * depth}px)`,
  });

  return (
    <AbsoluteFill style={{ background: C.paper }}>
      <Noise />
      {/* 晨蓝光域 */}
      <div style={{ position: "absolute", inset: 0, background: "linear-gradient(135deg,#FBF8FF 0%,#E9EEFF 55%,#D8E1FF 100%)" }} />
      <div style={{ position: "absolute", right: -160, top: -220, width: 700, height: 700, borderRadius: "50%", background: "rgba(255,255,255,0.8)", filter: "blur(80px)" }} />
      <AbsoluteFill style={{ alignItems: "center", flexDirection: "row", justifyContent: "center", gap: 90 }}>
        <div style={{ transform: `translateX(${(1 - inP) * 220}px) translateY(${(1 - inP) * 60}px) rotate(${(1 - inP) * 6 - 1.5}deg) scale(${zoom})`, opacity: Math.min(1, inP * 1.6) }}>
          <Phone width={470}>
            {/* 纹理坐标系 1260×2600: 底图滚动 + 悬浮层视差 */}
            <div style={{ position: "absolute", inset: 0, overflow: "hidden" }}>
              <Img src={staticFile("/textures/tex-academia.png")} style={{ position: "absolute", left: 0, top: 0, width: 1260 }} />
              {/* 状态卡悬浮层 */}
              <Img src={staticFile("/textures/cut-status-card.png")} style={{ ...layer({ x: 30, y: 265 }, 1), width: 1200, filter: "drop-shadow(0 18px 40px rgba(31,31,38,0.16))" }} />
              {/* 周六 chip */}
              <Img src={staticFile("/textures/cut-sat-chip.png")} style={{ ...layer({ x: 935, y: 515 }, 0.6), width: 165 }} />
              {/* 课程块 */}
              <Img src={staticFile("/textures/cut-course-teal.png")} style={{ ...layer({ x: 775, y: 820 }, 0.8), width: 195 }} />
              <Img src={staticFile("/textures/cut-course-gold.png")} style={{ ...layer({ x: 965, y: 820 }, 0.8), width: 180 }} />
              <Img src={staticFile("/textures/cut-course-red.png")} style={{ ...layer({ x: 125, y: 1240 }, 0.8), width: 155 }} />
            </div>
          </Phone>
        </div>
        <div style={{ width: 700, opacity: capP, transform: `translateX(${(1 - capP) * 40}px)` }}>
          <div style={{ fontFamily: F.mono, fontSize: 24, color: C.muted, letterSpacing: 6 }}>WEEKLY · OFFLINE · YOURS</div>
          <div style={{ fontFamily: F.sans, fontSize: 96, fontWeight: 800, color: C.ink, lineHeight: 1.25, marginTop: 20 }}>
            打开，<br />就是这一周。
          </div>
          <svg width="560" height="26" viewBox="0 0 560 26" style={{ marginTop: 6 }}>
            <path
              d="M6 16 Q 140 4 280 13 T 552 10"
              stroke={C.mustard}
              strokeWidth="8"
              fill="none"
              strokeLinecap="round"
              pathLength={1}
              strokeDasharray={1}
              strokeDashoffset={1 - capP}
            />
          </svg>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
