import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[18]; // b297-b309
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const ITEMS = [
  {
    label: "会话只在内存",
    sub: "退出登录或杀进程即清空",
    at: 0.5,
    d: "M14 30 h96 v24 h-96 Z M24 34 h14 v12 h-14 Z M44 34 h14 v12 h-14 Z M64 34 h14 v12 h-14 Z M88 30 v24 M96 30 v24",
  },
  {
    label: "不写入手机存储",
    sub: "验证码即弃 · 缓存即删",
    at: 2.75,
    d: "M22 14 h80 v52 h-80 Z M36 40 h52 M46 26 L66 54 M66 26 L46 54",
  },
  {
    label: "不随云备份导出",
    sub: "allowBackup=false · 敏感权限一律不申请",
    at: 5,
    d: "M28 50 a14 14 0 0 1 4 -28 a20 20 0 0 1 38 -4 a15 15 0 0 1 6 30 M24 24 L98 58",
  },
];

/** S19 隐私工程(draw-svg-trace): 三项工程事实逐条描边点亮 */
export const Shot19Privacy: React.FC = () => {
  const f = useCurrentFrame();
  const titleP = interpolate(f, [0, 12], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: C.dark, alignItems: "center", justifyContent: "center" }}>
      <Noise opacity={0.07} />
      <div style={{ width: 1300 }}>
        <div style={{ fontFamily: F.sans, fontSize: 62, fontWeight: 800, color: "#F2F5FF", textAlign: "center", opacity: titleP, letterSpacing: 2 }}>
          隐私不是口号，是工程决策
        </div>
        <div style={{ display: "flex", gap: 40, marginTop: 76 }}>
          {ITEMS.map(({ label, sub, at, d }, i) => {
            const start = L(at);
            const p = interpolate(f, [start, start + 16], [0, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(...OUT),
            });
            return (
              <div
                key={label}
                style={{
                  flex: 1,
                  background: "#1C1C26",
                  border: "1.5px solid rgba(183,196,255,0.22)",
                  borderRadius: 28,
                  padding: "38px 36px",
                  opacity: p,
                  transform: `translateY(${(1 - p) * 24}px)`,
                }}
              >
                <svg width="120" height="80" viewBox="0 0 120 80" fill="none">
                  <path
                    d={d}
                    stroke="#B7C4FF"
                    strokeWidth="5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    fill="none"
                    pathLength={1}
                    strokeDasharray={1}
                    strokeDashoffset={1 - p}
                  />
                </svg>
                <div style={{ fontFamily: F.sans, fontSize: 40, fontWeight: 700, color: "#F2F5FF", marginTop: 26, letterSpacing: 1 }}>{label}</div>
                <div style={{ fontFamily: F.sans, fontSize: 24, color: "#8B95BD", marginTop: 14, lineHeight: 1.7 }}>{sub}</div>
              </div>
            );
          })}
        </div>
      </div>
    </AbsoluteFill>
  );
};
