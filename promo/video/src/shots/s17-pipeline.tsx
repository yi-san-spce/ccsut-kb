import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[16]; // b249-b277
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const NODES = [
  { label: "统一身份认证", icon: "key", note: "短信验证码登录\n凭据只在内存", at: 0.5 },
  { label: "零信任网关", icon: "shield", note: "会话逐级激活\n每一步都校验", at: 2.5 },
  { label: "教务系统", icon: "server", note: "课表拉取\n拉完即断开", at: 4.5 },
];
const NODE_X = [360, 960, 1560];

const NodeIcon: React.FC<{ kind: string }> = ({ kind }) => {
  if (kind === "key")
    return (
      <svg width="72" height="72" viewBox="0 0 72 72" fill="none">
        <circle cx="26" cy="36" r="13" stroke="#B7C4FF" strokeWidth="5" />
        <path d="M39 36 H62 M52 36 v10 M60 36 v8" stroke="#B7C4FF" strokeWidth="5" strokeLinecap="round" />
      </svg>
    );
  if (kind === "shield")
    return (
      <svg width="72" height="72" viewBox="0 0 72 72" fill="none">
        <path d="M36 8 L60 17 V34 C60 50 49 60 36 64 C23 60 12 50 12 34 V17 Z" stroke="#B7C4FF" strokeWidth="5" strokeLinejoin="round" />
        <path d="M26 35 L34 44 L48 28" stroke="#DAB542" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  return (
    <svg width="72" height="72" viewBox="0 0 72 72" fill="none">
      <rect x="12" y="12" width="48" height="20" rx="5" stroke="#B7C4FF" strokeWidth="5" />
      <rect x="12" y="40" width="48" height="20" rx="5" stroke="#B7C4FF" strokeWidth="5" />
      <circle cx="22" cy="22" r="3" fill="#DAB542" />
      <circle cx="22" cy="50" r="3" fill="#DAB542" />
    </svg>
  );
};

/** S17 抓取链路图解(ring-diagram-annotation-reveal 改): 抽象三节点 + 数据包流动 */
export const Shot17Pipeline: React.FC = () => {
  const f = useCurrentFrame();
  const drift = interpolate(f, [L(2.6), D], [1, 1.045], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.25, 0.46, 0.45, 0.94),
  });
  const titleP = interpolate(f, [L(6.49), L(8.11)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 连接线绘制进度(节点间两段)
  const seg = (i: number) =>
    interpolate(f, [L(3.5 + i * 2), L(4.7 + i * 2)], [0, 1], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
      easing: Easing.bezier(...OUT),
    });
  // 数据包沿线流动(循环)
  const flowT = ((f - L(6.49)) % 45) / 45;

  return (
    <AbsoluteFill style={{ background: C.dark }}>
      <Noise opacity={0.07} />
      <div style={{ position: "absolute", left: 0, right: 0, top: 96, textAlign: "center", fontFamily: F.mono, fontSize: 26, letterSpacing: 8, color: "#7B87B8", opacity: titleP }}>
        HOW IT WORKS — ABSTRACT
      </div>
      <div style={{ position: "absolute", inset: 0, transform: `scale(${drift})` }}>
        {/* 连接线 */}
        {[0, 1].map((i) => {
          const x1 = NODE_X[i] + 150;
          const x2 = NODE_X[i + 1] - 150;
          const p = seg(i);
          return (
            <React.Fragment key={i}>
              <div style={{ position: "absolute", left: x1, top: 470, width: (x2 - x1) * p, height: 3, background: "rgba(183,196,255,0.45)" }} />
              {/* 数据包 */}
              {f > L(3.57) && p >= 1 && (
                <div
                  style={{
                    position: "absolute",
                    left: x1 + (x2 - x1) * flowT - 9,
                    top: 461,
                    width: 18,
                    height: 18,
                    borderRadius: "50%",
                    background: C.mustard,
                    boxShadow: "0 0 16px rgba(218,181,66,0.8)",
                    opacity: 0.9,
                  }}
                />
              )}
            </React.Fragment>
          );
        })}
        {/* 三节点 */}
        {NODES.map(({ label, icon, note, at }, i) => {
          const start = L(at);
          const p = interpolate(f, [start, start + 14], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(...OUT),
          });
          const noteP = interpolate(f, [L(7 + i * 1.7), L(8.7 + i * 1.7)], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(...OUT),
          });
          return (
            <div key={label} style={{ position: "absolute", left: NODE_X[i] - 150, top: 320, width: 300, opacity: p, transform: `scale(${0.85 + 0.15 * p})` }}>
              <div
                style={{
                  width: 300,
                  height: 300,
                  borderRadius: 34,
                  background: "#1C1C26",
                  border: "1.5px solid rgba(183,196,255,0.25)",
                  boxShadow: "0 24px 70px rgba(0,0,0,0.45)",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: 20,
                }}
              >
                <NodeIcon kind={icon} />
                <div style={{ fontFamily: F.sans, fontSize: 34, fontWeight: 700, color: "#F2F5FF", letterSpacing: 2 }}>{label}</div>
              </div>
              <div style={{ fontFamily: F.sans, fontSize: 26, color: "#8B95BD", textAlign: "center", marginTop: 28, lineHeight: 1.7, whiteSpace: "pre-line", opacity: noteP }}>
                {note}
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
          bottom: 110,
          textAlign: "center",
          fontFamily: F.sans,
          fontSize: 54,
          fontWeight: 700,
          color: "#F2F5FF",
          opacity: titleP,
          letterSpacing: 3,
        }}
      >
        一条链路，稳稳走通。
      </div>
    </AbsoluteFill>
  );
};
