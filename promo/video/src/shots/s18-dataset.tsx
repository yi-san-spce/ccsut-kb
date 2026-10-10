import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[17]; // b277-b297
const D = shot.to - shot.from;
const L = localB;
const DUR = D;

// terminal-3d 参数: 飞行窗(归一化) + 拉远鼓包 + 打字起点(前移, 留足输出落定)
const FLIGHTS: [number, number][] = [
  [0.22, 0.32],
  [0.5, 0.6],
];
const TYPE_AT = [0.03, 0.34, 0.58];
const PULL = 210;

const WINS = [
  {
    x: -640, y: -30, z: -80, ry: 18,
    cmd: "scrape --all",
    out: ["228 个班级 · 全量抓取", "19 周 × 每周课表", "仅公开课表数据"],
  },
  {
    x: 0, y: 50, z: 40, ry: -13,
    cmd: "build dataset",
    out: ["dataset.json · 4.6 MB", "sha256 校验 ✓", "打包进 APK · 离线可用"],
  },
  {
    x: 640, y: -40, z: -60, ry: 15,
    cmd: "publish release",
    out: ["manifest → 发布仓", "APP 内热更新", "无需重装 ✓"],
  },
];

/** S18 数据管线(terminal-3d): 三站空间旅程 —— 抓取→构建→发布 */
export const Shot18Dataset: React.FC = () => {
  const f = useCurrentFrame();
  const t = f / DUR;

  // 相机 x: 停在当前窗, 飞行窗间过渡 + 正弦拉远
  let camX = WINS[0].x;
  let pull = 0;
  FLIGHTS.forEach(([a, b], i) => {
    if (t >= b) camX = WINS[i + 1].x;
    else if (t >= a) {
      const p = (t - a) / (b - a);
      const ep = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2; // inOutCubic
      camX = WINS[i].x + (WINS[i + 1].x - WINS[i].x) * ep;
      pull = Math.sin(Math.PI * p) * PULL;
    }
  });
  if (t < FLIGHTS[0][0]) pull = Math.sin(Math.PI * (t / FLIGHTS[0][0])) * PULL * 0.4;

  return (
    <AbsoluteFill style={{ background: "radial-gradient(140% 120% at 50% 0%, #182036 0%, #0F1420 65%)", perspective: 1400, overflow: "hidden" }}>
      <Noise opacity={0.07} />
      <div style={{ position: "absolute", left: 0, right: 0, top: 92, textAlign: "center", fontFamily: F.mono, fontSize: 26, letterSpacing: 8, color: "#7B87B8" }}>
        THREE STEPS, ONE PIPELINE
      </div>
      {/* 相机逆变换 */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          transformStyle: "preserve-3d",
          transform: `translateZ(${300 - pull}px) translateX(${-camX}px)`,
        }}
      >
        {WINS.map(({ x, y, z, ry, cmd, out }, i) => {
          const focus = 1 - Math.min(1, Math.abs(x - camX) / 700);
          const opacity = 0.3 + 0.7 * focus;
          const blur = (1 - focus) * 2.4;
          // 打字机(45f 打完, 输出行紧随)
          const typeStart = TYPE_AT[i] * DUR;
          const chars = Math.round(
            interpolate(f, [typeStart, typeStart + 45], [0, cmd.length], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
            }),
          );
          const typing = f >= typeStart && f < typeStart + 45;
          const cursor = typing ? Math.floor(f * 3) % 2 === 0 : Math.floor(f / 18) % 2 === 0;
          return (
            <div
              key={cmd}
              style={{
                position: "absolute",
                left: 960 + x - 330,
                top: 540 + y - 190,
                width: 660,
                transformStyle: "preserve-3d",
                transform: `translateZ(${z}px) rotateY(${ry}deg)`,
                opacity,
                filter: blur > 0.1 ? `blur(${blur}px)` : undefined,
              }}
            >
              <div
                style={{
                  background: "#15151E",
                  border: "1.5px solid rgba(255,255,255,0.12)",
                  borderRadius: 20,
                  overflow: "hidden",
                  boxShadow: "0 30px 80px rgba(0,0,0,0.55)",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "14px 20px", borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
                  {["#FF5F57", "#FEBC2E", "#28C840"].map((c) => (
                    <div key={c} style={{ width: 12, height: 12, borderRadius: "50%", background: c }} />
                  ))}
                  <div style={{ fontFamily: F.mono, fontSize: 16, color: "#7B87B8", marginLeft: 10 }}>{`step ${i + 1} / 3`}</div>
                </div>
                <div style={{ padding: "24px 26px", fontFamily: F.mono, minHeight: 220 }}>
                  <div style={{ fontSize: 26, color: "#EAF0FF" }}>
                    <span style={{ color: "#59C2E8" }}>$ </span>
                    {cmd.slice(0, chars)}
                    {cursor && <span style={{ color: C.mustard }}>▌</span>}
                  </div>
                  <div style={{ marginTop: 20, display: "flex", flexDirection: "column", gap: 14 }}>
                    {out.map((o, j) => {
                      const op = interpolate(f, [typeStart + 48 + j * 8, typeStart + 60 + j * 8], [0, 1], {
                        extrapolateLeft: "clamp",
                        extrapolateRight: "clamp",
                        easing: Easing.bezier(0.16, 1, 0.3, 1),
                      });
                      return (
                        <div key={o} style={{ fontSize: 23, color: "#8B95BD", opacity: op, transform: `translateX(${(1 - op) * -8}px)` }}>
                          {o}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
      <div
        style={{
          position: "absolute",
          left: 0,
          right: 0,
          bottom: 96,
          textAlign: "center",
          fontFamily: F.sans,
          fontSize: 52,
          fontWeight: 700,
          color: "#F2F5FF",
          opacity: interpolate(f, [DUR * 0.74, DUR * 0.82], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          letterSpacing: 3,
        }}
      >
        数据热更新，无需重装。
      </div>
    </AbsoluteFill>
  );
};
