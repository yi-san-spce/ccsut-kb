import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, spring, staticFile , Img } from "remotion";
import { C, F } from "../theme";
import { Phone, CaptionLarge, Noise } from "../components/ui";
import { SHOTS, beatF, localB } from "../beats";

const shot = SHOTS[4]; // b60-b70
const D = shot.to - shot.from;
const L = (n: number) => localB(n);
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

/** S05 周次切换: 翻到第6周(非本周) → 回到本周按钮击回 */
export const Shot05WeekSwitch: React.FC = () => {
  const f = useCurrentFrame();
  // 翻页: academia 页 rotateY 翻出 → week 页翻入; L(4.87) 击回 → L(6.17) 翻回第5周
  const flipIn = interpolate(f, [L(0), L(1.6)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.45, 0, 0.25, 1),
  });
  const flipBack = interpolate(f, [L(3.9), L(5.4)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.5, 0, 0.3, 1),
  });
  const flip = flipIn * (1 - flipBack);
  // 回到本周按钮: 弹落 + 点击涟漪(两处: 弹落 L(3.25), 击回 L(4.87))
  const btn = spring({ frame: f - L(2.6), fps: 30, config: { damping: 10, stiffness: 140 }, durationInFrames: 36 });
  const btnPress = f >= L(3.9) && f < L(3.9) + 8 ? 0.88 : 1;
  const ripple = interpolate(f, [L(3.4), L(4.7)], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const rippleR = 40 + ripple * 220;
  const capP = interpolate(f, [L(3), L(4.7)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  // 击回白闪(2f)
  const flash = f >= L(3.9) && f < L(3.9) + 2;

  return (
    <AbsoluteFill style={{ background: "linear-gradient(135deg,#FBF8FF 0%,#E9EEFF 60%,#D8E1FF 100%)" }}>
      <Noise />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "flex-start", flexDirection: "row", perspective: 1600, paddingLeft: 210 }}>
        <div style={{ position: "relative", transformStyle: "preserve-3d" }}>
          {/* 第6周页(翻入面) */}
          <div style={{ transform: `rotateY(${(1 - flip) * 70}deg)`, opacity: Math.min(1, flip * 1.8), transformOrigin: "left center" }}>
            <Phone width={470} texture="textures/tex-week.png">
              <Img
                src={staticFile("/textures/cut-back-now.png")}
                style={{
                  position: "absolute",
                  left: 940,
                  top: 240,
                  width: 250,
                  transform: `scale(${(0.5 + 0.5 * btn) * btnPress})`,
                  opacity: Math.min(1, btn * 2),
                  filter: "drop-shadow(0 14px 30px rgba(0,23,74,0.35))",
                }}
              />
              {f > L(3.4) && (
                <div
                  style={{
                    position: "absolute",
                    left: 940 + 125 - rippleR,
                    top: 240 + 137 - rippleR,
                    width: rippleR * 2,
                    height: rippleR * 2,
                    borderRadius: "50%",
                    border: "6px solid rgba(59,100,216,0.5)",
                    opacity: (1 - ripple) * 0.9,
                  }}
                />
              )}
            </Phone>
          </div>
          {/* 第5周页(翻出面, 背面) */}
          <div
            style={{
              position: "absolute",
              inset: 0,
              transform: `rotateY(${-90 + flip * 70}deg)`,
              opacity: 1 - flip,
              transformOrigin: "right center",
              backfaceVisibility: "hidden",
            }}
          >
            <Phone width={470} texture="textures/tex-academia.png" />
          </div>
        </div>
        <div style={{ width: 640, marginLeft: 110, alignSelf: "center" }}>
          <div style={{ fontFamily: F.sans, fontSize: 84, fontWeight: 800, color: C.ink, lineHeight: 1.3, opacity: capP, transform: `translateX(${(1 - capP) * 40}px)` }}>
            哪一周的课，
            <br />
            都能翻到。
          </div>
          <svg width="420" height="22" viewBox="0 0 420 22" style={{ marginTop: 4 }}>
            <path
              d="M6 14 Q 110 4 210 11 T 414 8"
              stroke={C.mustard}
              strokeWidth="7"
              fill="none"
              strokeLinecap="round"
              pathLength={1}
              strokeDasharray={1}
              strokeDashoffset={1 - capP}
            />
          </svg>
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ background: "#fff", opacity: flash ? 0.85 : 0 }} />
    </AbsoluteFill>
  );
};
