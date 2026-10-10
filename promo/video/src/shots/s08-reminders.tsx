import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, staticFile , Img } from "remotion";
import { C, F } from "../theme";
import { CaptionLarge, Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[7]; // b109-b123
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];
const PANEL = "#26252A";
const SCREEN = "#141318";

const ROWS = [
  { tex: "cut-row-reminder", h: 195, at: 0.5 },
  { tex: "cut-row-lead", h: 170, at: 2.5 },
  { tex: "cut-row-summary", h: 155, at: 4.5 },
  { tex: "cut-row-early8", h: 170, at: 6.5 },
  { tex: "cut-row-widget", h: 145, at: 8.5 },
];

/** S08 提醒设置(list-reveal): 深色设置面板逐条点亮 */
export const Shot08Reminders: React.FC = () => {
  const f = useCurrentFrame();
  const sheetP = interpolate(f, [0, 14], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const capP = interpolate(f, [L(1.3), L(3.25)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: SCREEN }}>
      <Noise opacity={0.07} />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 100 }}>
        {/* 深色设置面板(重绘底板 + 真实行切片) */}
        <div
          style={{
            width: 660,
            background: PANEL,
            borderRadius: 40,
            padding: "34px 30px",
            display: "flex",
            flexDirection: "column",
            gap: 18,
            opacity: sheetP,
            transform: `translateY(${(1 - sheetP) * 50}px)`,
            boxShadow: "0 40px 100px rgba(0,0,0,0.5)",
          }}
        >
          {ROWS.map(({ tex, h, at }) => {
            const start = L(at);
            const born = f >= start;
            const p = interpolate(f, [start, start + 12], [0, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(...OUT),
            });
            return (
              <div key={tex} style={{ height: (h / 1080) * 600, borderRadius: 20, overflow: "hidden", opacity: born ? p : 0, transform: `translateY(${(1 - p) * 14}px)` }}>
                {born && <Img src={staticFile(`/textures/${tex}.png`)} style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "left center" }} />}
              </div>
            );
          })}
        </div>
        <div style={{ width: 620, opacity: capP, transform: `translateX(${(1 - capP) * 40}px)` }}>
          <div style={{ fontFamily: F.mono, fontSize: 24, color: "#6B76A8", letterSpacing: 6 }}>REMINDERS</div>
          <div style={{ fontFamily: F.sans, fontSize: 84, fontWeight: 800, color: "#EAF0FF", lineHeight: 1.35, marginTop: 22 }}>
            每节课，
            <br />
            都有人替你记着。
          </div>
          <div style={{ fontFamily: F.sans, fontSize: 30, color: "#8B95BD", marginTop: 26, lineHeight: 1.7 }}>
            课前提醒 · 提前量自定义 · 下课后小结
            <br />
            早八前夜，前晚 21:30 才提醒
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
