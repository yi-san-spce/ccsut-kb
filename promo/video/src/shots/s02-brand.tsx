import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, spring } from "remotion";
import { C, F } from "../theme";
import { GlassCard, AppIcon } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[1]; // b14-b26, 175f
const D = shot.to - shot.from;

const CHIPS = ["Android 8+", "GPL-3.0 开源", "离线可用"];

/** S02 品牌登场: 晨蓝光域 + 玻璃卡, 图标弹落、字标成对滑入、chips 逐个亮相, 收尾 hold */
export const Shot02Brand: React.FC = () => {
  const f = useCurrentFrame();
  const L = (n: number) => localB(n);

  const iconSpring = spring({
    frame: f - L(0.0),
    fps: 30,
    config: { damping: 11, stiffness: 130, mass: 0.9 },
    durationInFrames: 40,
  });
  const cardP = interpolate(f, [0, 16], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const titleP = interpolate(f, [L(1.3), L(3.25)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const sloganP = interpolate(f, [L(2.27), L(4.22)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const metaP = interpolate(f, [L(0.65), L(2.6)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <AbsoluteFill
      style={{
        background: "linear-gradient(135deg,#FBF8FF 0%,#E3EAFF 45%,#C9D6FF 100%)",
        fontFamily: F.sans,
      }}
    >
      {/* 柔光斑(单点, 克制) */}
      <div style={{ position: "absolute", right: -220, top: -320, width: 900, height: 900, borderRadius: "50%", background: "rgba(255,255,255,0.85)", filter: "blur(90px)" }} />
      <div style={{ position: "absolute", left: -260, bottom: -320, width: 700, height: 700, borderRadius: "50%", background: "rgba(255,255,255,0.6)", filter: "blur(90px)" }} />

      <div style={{ position: "absolute", left: 110, top: 88, fontFamily: F.mono, fontSize: 22, letterSpacing: 4, color: "#6B76A8", opacity: metaP }}>
        CCSUT KB · SCHEDULE
      </div>
      <div style={{ position: "absolute", right: 110, top: 84, fontSize: 26, letterSpacing: 2, color: "#41528F", opacity: metaP }}>
        给每一位长工学子
      </div>

      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
        <GlassCard
          style={{
            width: 860,
            height: 480,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            textAlign: "center",
            opacity: cardP,
            transform: `translateY(${(1 - cardP) * 40}px)`,
          }}
        >
          <div style={{ transform: `translateY(${(1 - iconSpring) * -90}px) scale(${0.6 + 0.4 * iconSpring})`, opacity: Math.min(1, iconSpring * 2) }}>
            <AppIcon size={120} />
          </div>
          <div
            style={{
              fontSize: 66,
              fontWeight: 800,
              color: C.blueDeep,
              letterSpacing: 4,
              marginTop: 34,
              opacity: titleP,
              transform: `translateY(${(1 - titleP) * 24}px)`,
            }}
          >
            长工课表通
          </div>
          <div
            style={{
              fontSize: 30,
              color: "#41528F",
              marginTop: 20,
              letterSpacing: 2,
              opacity: sloganP,
              transform: `translateY(${(1 - sloganP) * 16}px)`,
            }}
          >
            让查看课表这件事，不再那么狼狈。
          </div>
          <div style={{ display: "flex", gap: 16, marginTop: 38 }}>
            {CHIPS.map((c, i) => {
              const p = interpolate(f, [L(3.25) + i * 6, L(3.25) + i * 6 + 10], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
                easing: Easing.bezier(0.16, 1, 0.3, 1),
              });
              return (
                <div
                  key={c}
                  style={{
                    background: "rgba(255,255,255,0.7)",
                    color: C.blue,
                    fontSize: 22,
                    borderRadius: 999,
                    padding: "10px 24px",
                    opacity: p,
                    transform: `translateY(${(1 - p) * 12}px)`,
                  }}
                >
                  {c}
                </div>
              );
            })}
          </div>
        </GlassCard>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
