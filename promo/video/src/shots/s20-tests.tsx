import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[19]; // b309-b321
const D = shot.to - shot.from;
const L = localB;

// odometer-digit-roll: 两位数字 "49"
const DIGITS = [4, 9];
const START_AT = [0.5, 2.5]; // 每位起滚拍
const ROLL_F = 20; // 减速时长
const SETTLE_F = 6; // 过冲回弹
const LINE_H = 300;

const Digit: React.FC<{ target: number; start: number; f: number }> = ({ target, start, f }) => {
  const decel = interpolate(f, [start, start + ROLL_F], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.out(Easing.cubic),
  });
  // 过冲半格再弹回
  const overshootPhase = f - (start + ROLL_F);
  const extra = overshootPhase > 0 && overshootPhase < SETTLE_F ? 0.5 * (1 - overshootPhase / SETTLE_F) : 0;
  const pos = (target - 10 + (decel + extra) * 10) % 10; // 从 0 滚到 target(经 10 格)
  const posAbs = target - 10 + (decel + extra) * 10;
  const strip = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
  const yOffset = -(((posAbs % 10) + 10) % 10) * LINE_H;
  const settled = f >= start + ROLL_F + SETTLE_F;
  const speed = settled ? 0 : 1;
  return (
    <div style={{ position: "relative", width: 230, height: LINE_H, overflow: "hidden" }}>
      {/* 残影(速度门控, 停稳摘除) */}
      {speed > 0 && (
        <>
          <div style={{ position: "absolute", left: 0, top: 0, width: 230, height: LINE_H, overflow: "hidden", opacity: 0.25, transform: `translateY(${LINE_H * 0.5}px)` }}>
            <DigitStrip yOffset={yOffset} />
          </div>
          <div style={{ position: "absolute", left: 0, top: 0, width: 230, height: LINE_H, overflow: "hidden", opacity: 0.12, transform: `translateY(${-LINE_H * 0.5}px)` }}>
            <DigitStrip yOffset={yOffset} />
          </div>
        </>
      )}
      <div style={{ width: 230, height: LINE_H, overflow: "hidden" }}>
        <DigitStrip yOffset={yOffset} />
      </div>
    </div>
  );
};

const DigitStrip: React.FC<{ yOffset: number }> = ({ yOffset }) => (
  <div style={{ transform: `translateY(${yOffset}px)` }}>
    {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((n, i) => (
      <div key={i} style={{ height: LINE_H, fontFamily: F.mono, fontSize: 300, fontWeight: 800, color: "#F2F5FF", textAlign: "center", lineHeight: `${LINE_H}px`, fontVariantNumeric: "tabular-nums" }}>
        {n}
      </div>
    ))}
  </div>
);

/** S20 工程质量(odometer-digit-roll): 49 个单测, 逐位锁定 */
export const Shot20Tests: React.FC = () => {
  const f = useCurrentFrame();
  const allLocked = f >= L(START_AT[1]) + ROLL_F + SETTLE_F;
  const pulseT = f - L(START_AT[1]) - ROLL_F - SETTLE_F;
  const pulse = allLocked && pulseT < 8 ? 1 - 0.12 * Math.sin((Math.PI * pulseT) / 8) : 1;
  const scale = allLocked && pulseT < 8 ? 1.035 - 0.035 * (pulseT / 8) : 1;
  const labelP = interpolate(f, [L(START_AT[1]) + ROLL_F + SETTLE_F, L(START_AT[1]) + ROLL_F + SETTLE_F + 12], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const tint = allLocked && pulseT < 8 ? 0.5 + 0.5 * Math.sin((Math.PI * pulseT) / 8) : 0;

  return (
    <AbsoluteFill style={{ background: C.dark, alignItems: "center", justifyContent: "center", flexDirection: "column" }}>
      <Noise opacity={0.07} />
      <div style={{ display: "flex", gap: 40, filter: `brightness(${1 + tint * 0.35})`, transform: `scale(${scale})` }}>
        {DIGITS.map((target, i) => (
          <Digit key={i} target={target} start={L(START_AT[i])} f={f} />
        ))}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 26, marginTop: 60, opacity: labelP, transform: `translateY(${(1 - labelP) * 18}px)` }}>
        <div style={{ fontFamily: F.sans, fontSize: 52, fontWeight: 700, color: "#F2F5FF", letterSpacing: 3 }}>个单元测试，CI 持续守护</div>
        <div style={{ fontFamily: F.mono, fontSize: 26, color: "#0F1420", background: "#28C840", borderRadius: 8, padding: "8px 18px", letterSpacing: 1 }}>
          ✓ build passing
        </div>
      </div>
      <div style={{ fontFamily: F.sans, fontSize: 28, color: "#7B87B8", marginTop: 40, opacity: labelP, letterSpacing: 4 }}>
        版本号双写校验 · 数据包 sha256 校验 · 原子落盘
      </div>
    </AbsoluteFill>
  );
};
