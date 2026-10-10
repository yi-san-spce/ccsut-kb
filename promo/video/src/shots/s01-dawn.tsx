import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { HandSun, HandNote, CaptionLarge, Noise, AppIcon } from "../components/ui";
import { SHOTS, beatF } from "../beats";

const shot = SHOTS[0]; // b0-b14, 203f
const D = shot.to - shot.from;
const L = (n: number) => beatF(n); // from=0, 局部拍 n → 局部帧
const EASE_OUT_ARR = [0.16, 1, 0.3, 1] as [number, number, number, number];

/** S01 晨光开场: 太阳描边生长 → 主文案落定(b14 slam①) */
export const Shot01Dawn: React.FC = () => {
  const f = useCurrentFrame();
  const outlineP = interpolate(f, [L(0.0) + 6, L(1.95)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...EASE_OUT_ARR),
  });
  const fillP = interpolate(f, [L(0.97), L(2.27)], [0, 0.95], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const raysP = interpolate(f, [L(1.95), L(3.57)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const faceP = interpolate(f, [L(3.57), L(4.54)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  // 主文案: b10 入, b13.5 落定(轻微过冲回稳 = slam① 视觉落点)
  const titleP = interpolate(f, [L(4.54), L(6.17)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...EASE_OUT_ARR),
  });
  const settle = f >= L(6.17) ? 1 + 0.03 * Math.exp(-(f - L(6.17)) * 0.25) * Math.cos((f - L(6.17)) * 0.55) : 1;

  const noteP1 = interpolate(f, [L(5.19), L(5.84)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const brandP = interpolate(f, [L(5.52), L(6.17)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const sunRise = interpolate(f, [L(0.0), L(3.57)], [36, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...EASE_OUT_ARR),
  });

  return (
    <AbsoluteFill style={{ background: C.paper }}>
      <Noise />
      {/* 晨光顶部晕染 */}
      <div
        style={{
          position: "absolute",
          left: 560,
          top: -420,
          width: 800,
          height: 800,
          borderRadius: "50%",
          background: "radial-gradient(circle, rgba(218,181,66,0.16), rgba(218,181,66,0) 65%)",
        }}
      />
      <div
        style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "column",
        }}
      >
        <HandSun
          size={400}
          outlineP={outlineP}
          fillP={fillP}
          raysP={raysP}
          faceP={faceP}
          style={{ marginTop: -60, transform: `translateY(${sunRise}px)` }}
        />
        <div
          style={{
            marginTop: 64,
            opacity: titleP,
            transform: `scale(${settle * (0.94 + 0.06 * titleP)})`,
            transformOrigin: "center",
            textAlign: "center",
          }}
        >
          <div style={{ fontFamily: F.sans, fontSize: 78, fontWeight: 800, color: C.ink, letterSpacing: 3 }}>
            新的一天，从看清课表开始。
          </div>
          <div
            style={{
              fontFamily: F.sans,
              fontSize: 34,
              color: C.muted,
              letterSpacing: 10,
              marginTop: 26,
              opacity: interpolate(f, [L(5.52), L(6.17)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
            }}
          >
            打开手机 · 它就在那里
          </div>
        </div>
      </div>
      <HandNote text="a new day ☀" p={noteP1} rotate={-4} style={{ left: 110, top: 96 }} />
      <HandNote text="早八人的小太阳" p={noteP1} rotate={3} style={{ right: 130, bottom: 150 }} />
      <div
        style={{
          position: "absolute",
          bottom: 64,
          left: 0,
          right: 0,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: 18,
          opacity: brandP,
          fontFamily: F.sans,
          fontSize: 26,
          color: C.muted,
          letterSpacing: 3,
        }}
      >
        <AppIcon size={52} />
        长工课表通
      </div>
    </AbsoluteFill>
  );
};
