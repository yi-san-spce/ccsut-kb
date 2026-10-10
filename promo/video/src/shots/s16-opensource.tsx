import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[15]; // b233-b249
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const CMD = "git clone ccsut-kb  # 全部源码";
const LINES = [
  { text: "scraper/   数据抓取 —— 认证到解析，每一步都摆开", at: 0.18 },
  { text: "dataset/   228 个班级 · 离线课表数据集", at: 0.30 },
  { text: "app/       原生 Kotlin · 无第三方网络库", at: 0.42 },
  { text: "LICENSE    GPL-3.0 —— 欢迎审查每一行", at: 0.54, hl: true },
];

/** S16 为什么开源(typing-code-block): 代码窗打字, 抽象无真实域名 */
export const Shot16Opensource: React.FC = () => {
  const f = useCurrentFrame();
  const winP = interpolate(f, [0, 14], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 命令逐字符: L(1.3)-L(5.19)
  const typeStart = L(0.97);
  const typeEnd = L(4.22);
  const chars = Math.round(
    interpolate(f, [typeStart, typeEnd], [0, CMD.length], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    }),
  );
  const typing = f >= typeStart && f < typeEnd;
  const cursorBlink = typing ? Math.floor(f * 3) % 2 === 0 : Math.floor(f / 20) % 2 === 0;

  const titleP = interpolate(f, [L(4.54), L(6.17)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: C.dark, alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 110 }}>
      <Noise opacity={0.07} />
      {/* 代码窗 */}
      <div
        style={{
          width: 980,
          background: "#1C1C26",
          border: "1.5px solid rgba(255,255,255,0.10)",
          borderRadius: 24,
          overflow: "hidden",
          boxShadow: "0 40px 100px rgba(0,0,0,0.5)",
          opacity: winP,
          transform: `translateY(${(1 - winP) * 40}px)`,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "20px 26px", borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
          {["#FF5F57", "#FEBC2E", "#28C840"].map((c) => (
            <div key={c} style={{ width: 16, height: 16, borderRadius: "50%", background: c }} />
          ))}
          <div style={{ fontFamily: F.mono, fontSize: 20, color: "#7B87B8", marginLeft: 14 }}>ccsut-kb — zsh</div>
        </div>
        <div style={{ padding: "36px 40px", fontFamily: F.mono, minHeight: 380 }}>
          <div style={{ fontSize: 34, color: "#EAF0FF", letterSpacing: 1 }}>
            <span style={{ color: "#59C2E8" }}>$ </span>
            {CMD.slice(0, chars)}
            {cursorBlink && <span style={{ color: C.mustard }}>▌</span>}
          </div>
          <div style={{ marginTop: 34, display: "flex", flexDirection: "column", gap: 22 }}>
            {LINES.map(({ text, at, hl }) => {
              const lp = interpolate(f, [typeEnd + at * 100, typeEnd + at * 100 + 12], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
                easing: Easing.bezier(...OUT),
              });
              return (
                <div
                  key={text}
                  style={{
                    fontSize: 27,
                    color: hl ? C.blueSoft : "#8B95BD",
                    opacity: lp,
                    transform: `translateX(${(1 - lp) * -10}px)`,
                    letterSpacing: 0.5,
                  }}
                >
                  {hl ? "✓ " : "> "}
                  {text}
                </div>
              );
            })}
          </div>
        </div>
      </div>
      <div style={{ width: 560, opacity: titleP, transform: `translateX(${(1 - titleP) * 40}px)` }}>
        <div style={{ fontFamily: F.sans, fontSize: 80, fontWeight: 800, color: "#F2F5FF", lineHeight: 1.45 }}>
          全部开源，
          <br />
          <span style={{ color: C.blueSoft }}>一行不少。</span>
        </div>
        <div style={{ fontFamily: F.sans, fontSize: 30, color: "#8B95BD", marginTop: 30, lineHeight: 1.8 }}>
          从抓取到数据集到 APP，
          <br />
          每一步都摊开在阳光下。
        </div>
      </div>
    </AbsoluteFill>
  );
};
