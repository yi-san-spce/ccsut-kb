import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing } from "remotion";
import { C, F } from "../theme";
import { Noise } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[20]; // b321-b331
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const PILLS = ["提个 Bug", "提个建议", "交份课表", "点个赞"];
const PILL_AT = [1.5, 3.2, 4.9, 6.6]; // pill-slot-cycle: 稳定节拍换词

/** S21 邀请共创(pill-slot-cycle): 「在这里，你可以 ___」逐拍换词 */
export const Shot21Invite: React.FC = () => {
  const f = useCurrentFrame();
  const titleP = interpolate(f, [0, 12], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 当前 pill: 换词节奏(滑出→滑入)
  let idx = 0;
  let swapP = 1;
  PILLS.forEach((_, i) => {
    const t = L(PILL_AT[i]);
    if (f >= t) {
      idx = i;
      swapP = interpolate(f, [t, t + 9], [0, 1], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
        easing: Easing.bezier(...OUT),
      });
    }
  });
  const tailP = interpolate(f, [L(5.06), L(5.84)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: "linear-gradient(160deg,#FBF8FF 0%,#E9EEFF 60%,#DCE3FF 100%)", alignItems: "center", justifyContent: "center" }}>
      <Noise opacity={0.04} />
      <div style={{ display: "flex", alignItems: "center", gap: 30, opacity: titleP }}>
        <div style={{ fontFamily: F.sans, fontSize: 92, fontWeight: 800, color: C.ink, letterSpacing: 3 }}>在这里，你可以</div>
        {/* pill 槽位 */}
        <div
          style={{
            minWidth: 430,
            height: 130,
            background: C.blue,
            borderRadius: 999,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            boxShadow: "0 20px 50px rgba(59,100,216,0.35)",
            overflow: "hidden",
          }}
        >
          <div
            key={idx}
            style={{
              fontFamily: F.sans,
              fontSize: 56,
              fontWeight: 800,
              color: "#fff",
              letterSpacing: 3,
              opacity: swapP,
              transform: `translateX(${(1 - swapP) * 60}px)`,
              whiteSpace: "nowrap",
            }}
          >
            {PILLS[idx]}
          </div>
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          bottom: 170,
          left: 0,
          right: 0,
          textAlign: "center",
          fontFamily: F.sans,
          fontSize: 34,
          color: C.muted,
          opacity: tailP,
          letterSpacing: 3,
        }}
      >
        你的每一条 Issue，都会被认真读。
      </div>
    </AbsoluteFill>
  );
};
