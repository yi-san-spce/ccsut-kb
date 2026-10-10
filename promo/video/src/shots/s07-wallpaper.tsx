import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, staticFile } from "remotion";
import { C, F } from "../theme";
import { Phone, CaptionLarge, Noise } from "../components/ui";
import { SHOTS, beatF, localB } from "../beats";

const shot = SHOTS[6]; // b92-b109
const D = shot.to - shot.from;
const L = (n: number) => localB(n);
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const WALLS = ["tex-bg-space", "tex-bg-portrait", "tex-bg-newyear", "tex-bg-lighthouse"];
const LAND_BEATS = [0.5, 2.5, 4.5, 6.5]; // 逐格踩拍硬入(前移消除空窗)

/** S07 背景图取色(panel-grid-moves A 改): 2×2 壁纸网格逐格踩拍亮相 */
export const Shot07Wallpaper: React.FC = () => {
  const f = useCurrentFrame();
  const cells = WALLS.map((tex, i) => {
    const start = L(LAND_BEATS[i]);
    const born = f >= start;
    const p = interpolate(f, [start, start + 3], [0, 1], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
      easing: Easing.bezier(...OUT),
    });
    // 加深脉冲: 落格后 2f(条件挂载, 窗口外不挂 filter)
    const since = f - start;
    const pulse = born && since < 4 ? 0.82 : 1;
    return { tex, born, p, pulse };
  });
  const allIn = f >= L(LAND_BEATS[3]) + 4;
  const groupPulse = allIn && f < L(5.52) ? 0.86 : 1;

  return (
    <AbsoluteFill style={{ background: C.paper }}>
      <Noise />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
        <div style={{ display: "flex", gap: 36, filter: `brightness(${groupPulse})` }}>
          {cells.map(({ tex, born, p, pulse }) =>
            born ? (
              <div key={tex} style={{ transform: `scale(${1.18 - 0.18 * p})`, filter: `brightness(${pulse})` }}>
                <Phone width={256} texture={`textures/${tex}.png`} style={{ border: "3px solid rgba(31,31,38,0.9)", borderRadius: 24 }} />
              </div>
            ) : (
              <div key={tex} style={{ width: 256, height: (2600 / 1260) * 256 }} />
            ),
          )}
        </div>
      </AbsoluteFill>
      <CaptionLarge text="换个壁纸，课表跟着换色" duration={D} y={952} enterFrom={L(4.87)} size={54} />
      <div style={{ position: "absolute", left: 0, right: 0, top: 96, textAlign: "center", fontFamily: F.mono, fontSize: 22, letterSpacing: 6, color: C.muted, opacity: 0.9 }}>
        BACKGROUND → COLOR
      </div>
    </AbsoluteFill>
  );
};
