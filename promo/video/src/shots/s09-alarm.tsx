import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { CaptionLarge } from "../components/ui";
import { SHOTS, localB } from "../beats";
import { mulberry32 } from "../lib/helpers/rand";

const shot = SHOTS[8]; // b123-b139
const D = shot.to - shot.from;
const L = localB;
// slam② 落点 = 本地 L(1.62), 具体 beatF 由选曲后网格给出(见 beats.ts)
const LAND = localB(3);

const DUST = Array.from({ length: 22 }, (_, i) => {
  const rnd = mulberry32(1000 + i);
  const ang = rnd() * Math.PI * 2;
  const dist = 160 + rnd() * 160;
  const size = 18 + rnd() * 12;
  return { ang, dist, size, seed: i };
});

/** S09 闹钟全屏响铃(slam-entrance-moves B score-slam): 重拳砸入 + 三件套 */
export const Shot09Alarm: React.FC = () => {
  const f = useCurrentFrame();
  // 砸落: 6f Easing.in(quad) 加速砸(不是减速落)
  const slamP = interpolate(f, [LAND - 6, LAND], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.in(Easing.quad),
  });
  const scale = 2.5 - 1.5 * slamP;
  const rot = 5 * (1 - slamP);

  // 震屏: 18px 指数衰减 4f(落点帧起)
  const tSince = f - LAND;
  const shake = tSince >= 0 && tSince < 5 ? 18 * Math.exp(-tSince * 0.9) : 0;
  const shakeX = shake * Math.sin(f * 7.3);
  const shakeY = shake * Math.cos(f * 5.1);

  // 圆环: 80→860px, 扩散 out-cubic / 消散 线性(解耦命门)
  const ringSpread = interpolate(tSince, [0, 14], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.out(Easing.cubic),
  });
  const ringFade = 1 - interpolate(tSince, [0, 14], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const ringR = 80 + ringSpread * 780;

  // 响铃余韵波纹(落定后持续, 呼吸)
  const waveP = f > LAND + 10 ? (Math.sin((f - LAND - 10) * 0.09) + 1) / 2 : 0;

  const capP = interpolate(f, [LAND + 18, LAND + 32], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });

  return (
    <AbsoluteFill style={{ background: "#0F1420", overflow: "hidden" }}>
      <AbsoluteFill
        style={{
          transform: `translate(${shakeX}px, ${shakeY}px)`,
        }}
      >
        {/* 响铃页本体 */}
        <AbsoluteFill
          style={{
            transform: `scale(${scale}) rotate(${rot}deg)`,
            opacity: Math.min(1, 0.45 + slamP * 0.55),
            background: "radial-gradient(120% 100% at 50% 0%, #16203A 0%, #0F1420 60%)",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          {/* 响铃波纹底纹 */}
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              style={{
                position: "absolute",
                width: 700 + i * 260 + waveP * 60,
                height: 700 + i * 260 + waveP * 60,
                borderRadius: "50%",
                border: "2px solid rgba(119,148,255,0.16)",
              }}
            />
          ))}
          <div style={{ fontFamily: F.mono, fontSize: 30, letterSpacing: 8, color: "#7B87B8", marginBottom: 30 }}>
            课前提醒 · 提前 15 分钟
          </div>
          <div style={{ fontFamily: F.mono, fontSize: 220, fontWeight: 700, color: "#F2F5FF", letterSpacing: 6, fontVariantNumeric: "tabular-nums" }}>
            08:20
          </div>
          <div style={{ fontFamily: F.sans, fontSize: 62, fontWeight: 700, color: "#B7C4FF", marginTop: 34, letterSpacing: 3 }}>
            概率论与数理统计A @ 7-南204
          </div>
          <div
            style={{
              marginTop: 46,
              fontFamily: F.sans,
              fontSize: 30,
              color: "#7B87B8",
              border: "1.5px solid rgba(119,148,255,0.4)",
              borderRadius: 999,
              padding: "14px 36px",
              letterSpacing: 4,
            }}
          >
            起床，别狼狈
          </div>
        </AbsoluteFill>

        {/* 冲击三件套: 圆环 + 尘点 */}
        {tSince >= 0 && tSince < 15 && (
          <>
            <div
              style={{
                position: "absolute",
                left: 960 - ringR,
                top: 540 - ringR,
                width: ringR * 2,
                height: ringR * 2,
                borderRadius: "50%",
                border: "5px solid rgba(183,196,255,0.85)",
                opacity: ringFade,
              }}
            />
            {DUST.map(({ ang, dist, size, seed }) => {
              const dp = interpolate(tSince, [0, 13], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
                easing: Easing.out(Easing.cubic),
              });
              const fade = 1 - dp;
              return (
                <div
                  key={seed}
                  style={{
                    position: "absolute",
                    left: 960 + Math.cos(ang) * dist * dp - size / 2,
                    top: 540 + Math.sin(ang) * dist * dp - size / 2,
                    width: size,
                    height: size,
                    borderRadius: "50%",
                    background: "rgba(183,196,255,0.9)",
                    opacity: fade,
                  }}
                />
              );
            })}
          </>
        )}
      </AbsoluteFill>
      {/* 落点白闪(2f) */}
      {tSince >= 0 && tSince < 2 && <AbsoluteFill style={{ background: "rgba(255,255,255,0.75)" }} />}
      <div
        style={{
          position: "absolute",
          left: 0,
          right: 0,
          bottom: 96,
          textAlign: "center",
          fontFamily: F.sans,
          fontSize: 54,
          fontWeight: 700,
          color: "#EAF0FF",
          opacity: capP,
          transform: `translateY(${(1 - capP) * 20}px)`,
          letterSpacing: 3,
        }}
      >
        闹钟模式 · 锁屏亮起，想睡过头都难
      </div>
    </AbsoluteFill>
  );
};
