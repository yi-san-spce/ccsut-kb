import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[11]; // b171-b189
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const PLEDGES = [
  { title: "验证码，用完即弃", sub: "短信验证码只用于这一次登录", at: 0.5, hl: false },
  { title: "登录凭据只在内存", sub: "不写入手机存储 · 约 3 分钟自然消亡 · 不随云备份导出", at: 3, hl: false },
  { title: "代码全开源 GPL-3.0", sub: "包括数据抓取代码 —— 欢迎审查每一行", at: 5.5, hl: true },
];

/** S12 数据安全·下: 三条承诺逐条盖章 */
export const Shot12SafePledge: React.FC = () => {
  const f = useCurrentFrame();
  const titleP = interpolate(f, [0, 10], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: C.paper, alignItems: "center", justifyContent: "center" }}>
      <Noise />
      <div style={{ width: 1240 }}>
        <div
          style={{
            fontFamily: F.sans,
            fontSize: 60,
            fontWeight: 800,
            color: C.ink,
            textAlign: "center",
            opacity: titleP,
            letterSpacing: 2,
          }}
        >
          三条承诺，写进代码里
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 30, marginTop: 64 }}>
          {PLEDGES.map(({ title, sub, at, hl }) => {
            const start = L(at);
            const stamp = interpolate(f, [start, start + 10], [0, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.out(Easing.cubic),
            });
            const settle = f >= start + 10 ? 1 + 0.04 * Math.exp(-(f - start - 10) * 0.4) : 1.12 - 0.12 * stamp;
            return (
              <div
                key={title}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 30,
                  background: hl ? "rgba(59,100,216,0.08)" : "#FFFFFF",
                  border: hl ? `2.5px solid ${C.blue}` : "2px solid rgba(31,31,38,0.14)",
                  borderRadius: 26,
                  padding: "30px 44px",
                  opacity: stamp,
                  transform: `scale(${Math.min(settle, 1.12)})`,
                  boxShadow: hl ? "0 16px 44px rgba(59,100,216,0.18)" : "0 10px 30px rgba(31,31,38,0.08)",
                }}
              >
                <div
                  style={{
                    width: 54,
                    height: 54,
                    borderRadius: 16,
                    background: hl ? C.blue : C.mustard,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  <svg width="30" height="30" viewBox="0 0 30 30" fill="none">
                    <path d="M6 16 L13 23 L25 8" stroke="#fff" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </div>
                <div>
                  <div style={{ fontFamily: F.sans, fontSize: 46, fontWeight: 800, color: hl ? C.blue : C.ink, letterSpacing: 1 }}>{title}</div>
                  <div style={{ fontFamily: F.sans, fontSize: 26, color: C.muted, marginTop: 8, letterSpacing: 1 }}>{sub}</div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </AbsoluteFill>
  );
};
