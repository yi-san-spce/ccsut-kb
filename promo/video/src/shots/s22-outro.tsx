import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { AppIcon, HandSun, Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";
import { mulberry32 } from "../lib/helpers/rand";

const shot = SHOTS[21]; // b331-b364
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];
const INOUT = [0.45, 0, 0.55, 1] as [number, number, number, number];

const WORD = ["长", "工", "课", "表", "通"];
// 收敛的课程块(满屏能量 → 收束)
const BLOCKS = Array.from({ length: 14 }, (_, i) => {
  const rnd = mulberry32(700 + i);
  const colors = ["#C9526B", "#DAB542", "#7A9E9F", "#8B6BB8", "#E58B6A", "#59C2E8", "#5F9E6E"];
  return {
    x: rnd() * 1920,
    y: rnd() * 1080,
    w: 90 + rnd() * 130,
    h: 60 + rnd() * 110,
    color: colors[i % colors.length],
    delay: rnd() * 10,
  };
});

/** S22 收场签名: 课程块收束 → 图标落定 → 字标 → 太阳描边绽放 → @yisan */
export const Shot22Outro: React.FC = () => {
  const f = useCurrentFrame();

  // P1 课程块收束(向中心飞并淡出)
  const conv = interpolate(f, [L(0.0), L(2.27)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...INOUT),
  });

  // P2 图标收束: scale 5.4→1 + sin 刹车(logo-shrink 参数)
  const shrinkT = interpolate(f, [L(0.65), L(3.57)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...INOUT),
  });
  const brake =
    shrinkT > 0.26 && shrinkT < 0.4 ? Math.sin(((shrinkT - 0.26) / 0.14) * Math.PI) * 0.06 : 0;
  const iconScale = (5.4 - 4.4 * shrinkT) * (1 + brake);
  const glowP = 1 - interpolate(shrinkT, [0.1, 0.28], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  // P3 字标逐字
  const charP = (i: number) =>
    interpolate(f, [L(4.22) + i * 5, L(4.22) + i * 5 + 12], [0, 1], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
      easing: Easing.bezier(...OUT),
    });

  // P4 太阳描边生长 + 光线绽放
  const outlineP = interpolate(f, [L(7.14), L(9.09)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(...OUT) });
  const fillP = interpolate(f, [L(8.44), L(10.06)], [0, 0.95], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const raysP = interpolate(f, [L(9.09), L(10.71)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const faceP = interpolate(f, [L(10.71), L(11.69)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  // P5 @yisan(impact③ 落点 = L(16.23))
  const handleP = interpolate(f, [L(12.33), L(14.28)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const sunPop = f >= L(14.28) ? 1 + 0.05 * Math.exp(-(f - L(14.28)) * 0.3) : 1;
  const subP = interpolate(f, [L(14.61), L(15.91)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const glowBreath = 0.5 + 0.5 * Math.sin(f * 0.06);

  return (
    <AbsoluteFill style={{ background: C.paper, alignItems: "center", justifyContent: "center" }}>
      <Noise />
      {/* P1 满屏课程块(收束前) */}
      {conv < 1 &&
        BLOCKS.map((b, i) => (
          <div
            key={i}
            style={{
              position: "absolute",
              left: b.x + (960 - b.x - b.w / 2) * conv,
              top: b.y + (540 - b.y - b.h / 2) * conv,
              width: b.w * (1 - conv * 0.7),
              height: b.h * (1 - conv * 0.7),
              borderRadius: 18,
              background: b.color,
              opacity: (1 - conv) * 0.95,
            }}
          />
        ))}
      {/* 中央光晕(太阳背后) */}
      <div
        style={{
          position: "absolute",
          left: 960 - 380,
          top: 620 - 380,
          width: 760,
          height: 760,
          borderRadius: "50%",
          background: `radial-gradient(circle, rgba(218,181,66,${0.10 + glowBreath * 0.08}), rgba(218,181,66,0) 62%)`,
          opacity: fillP,
        }}
      />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column" }}>
        {/* lockup: 图标 + 字标 */}
        <div style={{ display: "flex", alignItems: "center", gap: 26, transform: `translateY(-30px)` }}>
          <div style={{ position: "relative", transform: `scale(${Math.min(iconScale, 5.4)})`, opacity: shrinkT > 0.01 ? 1 : 0 }}>
            {glowP > 0 && (
              <div style={{ position: "absolute", inset: -30, borderRadius: "50%", background: "rgba(59,100,216,0.35)", filter: `blur(${40 * glowP}px)` }} />
            )}
            <AppIcon size={100} />
          </div>
          <div style={{ display: "flex" }}>
            {WORD.map((ch, i) => (
              <div
                key={ch}
                style={{
                  fontFamily: F.sans,
                  fontSize: 96,
                  fontWeight: 800,
                  color: C.ink,
                  opacity: charP(i),
                  transform: `translateX(${(1 - charP(i)) * 26}px)`,
                  letterSpacing: 4,
                }}
              >
                {ch}
              </div>
            ))}
          </div>
        </div>
        {/* 太阳签名 */}
        <div style={{ marginTop: 40, transform: `scale(${sunPop})` }}>
          <HandSun size={400} outlineP={outlineP} fillP={fillP} raysP={raysP} faceP={faceP} />
        </div>
        {/* @yisan */}
        <div
          style={{
            fontFamily: F.mono,
            fontSize: 84,
            fontWeight: 700,
            color: C.ink,
            marginTop: 44,
            opacity: handleP,
            transform: `translateY(${(1 - handleP) * 26}px)`,
            letterSpacing: 2,
          }}
        >
          @yisan
        </div>
        <div
          style={{
            fontFamily: F.sans,
            fontSize: 32,
            color: C.muted,
            marginTop: 18,
            opacity: subP,
            letterSpacing: 10,
          }}
        >
          全平台同名 · 谢谢你看到这里
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
