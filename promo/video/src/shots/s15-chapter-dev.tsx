import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[14]; // b223-b233
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

/** S15 章节转场(line-carry + title-demote): 芥末黄线拉出→开发者章分隔轴, 底色翻暗 */
export const Shot15ChapterDev: React.FC = () => {
  const f = useCurrentFrame();
  // 底色翻暗: 线扫过处翻色
  const darkP = interpolate(f, [L(1.62), L(2.92)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  // 横线从左侧拉出(继承上镜"下划线"的母题)
  const lineP = interpolate(f, [L(0.19), L(1.95)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const chapP = interpolate(f, [L(2.27), L(3.25)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const titleP = interpolate(f, [L(2.92), L(4.22)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  const bg = darkP < 1 ? `rgb(${Math.round(251 - 120 * darkP)},${Math.round(248 - 117 * darkP)},${Math.round(255 - 4 * darkP)})` : "#8B8B8B";
  // 直接用暗色叠加层(保证渐变终点正确)
  return (
    <AbsoluteFill style={{ background: C.paper }}>
      <AbsoluteFill style={{ background: C.dark, opacity: darkP }} />
      <Noise opacity={0.05} />
      {/* 贯穿分隔轴 */}
      <div
        style={{
          position: "absolute",
          left: 0,
          top: 470,
          height: 8,
          width: `${lineP * 100}%`,
          background: C.mustard,
          boxShadow: "0 0 24px rgba(218,181,66,0.5)",
        }}
      />
      <div
        style={{
          position: "absolute",
          left: 240,
          top: 350,
          fontFamily: F.mono,
          fontSize: 30,
          letterSpacing: 8,
          color: darkP > 0.5 ? "#7B87B8" : C.muted,
          opacity: chapP,
        }}
      >
        CHAPTER 02 · FOR DEVELOPERS
      </div>
      <div
        style={{
          position: "absolute",
          left: 240,
          top: 530,
          fontFamily: F.sans,
          fontSize: 110,
          fontWeight: 800,
          color: darkP > 0.5 ? "#F2F5FF" : C.ink,
          opacity: titleP,
          transform: `translateY(${(1 - titleP) * 30}px)`,
          letterSpacing: 6,
        }}
      >
        写给开发者
      </div>
      <div
        style={{
          position: "absolute",
          right: 240,
          top: 560,
          fontFamily: F.sans,
          fontSize: 34,
          color: darkP > 0.5 ? "#8B95BD" : C.muted,
          opacity: titleP,
          textAlign: "right",
          lineHeight: 1.8,
        }}
      >
        逆向 · 开源 · 工程化
        <br />
        这一章讲给愿意看代码的你
      </div>
    </AbsoluteFill>
  );
};
