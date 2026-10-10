import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, Easing, staticFile , Img } from "remotion";
import { C, F } from "../theme";
import { Noise, AppIcon } from "../components/ui";
import { SHOTS, localB } from "../beats";

const shot = SHOTS[13]; // b205-b223
const D = shot.to - shot.from;
const L = localB;
const OUT = [0.16, 1, 0.3, 1] as [number, number, number, number];

const CHIPS = ["提 Bug", "提建议", "交课表数据"];

/** S14 社区邀请 + 下载 CTA(radial-ripple-phone-chips): 涟漪 + 二维码卡 */
export const Shot14Community: React.FC = () => {
  const f = useCurrentFrame();
  const iconP = interpolate(f, [0, 12], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const titleP = interpolate(f, [L(0.32), L(1.95)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });
  const qrP = interpolate(f, [L(2.6), L(4.22)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...OUT),
  });

  return (
    <AbsoluteFill style={{ background: "linear-gradient(160deg,#FBF8FF 0%,#E9EEFF 60%,#DCE3FF 100%)" }}>
      <Noise />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 110 }}>
        {/* 涟漪图标 + 文案 */}
        <div style={{ position: "relative", width: 760, paddingLeft: 130 }}>
          {[0, 1].map((i) => {
            const t = (f - L(0.32) - i * 24) / 70;
            const r = t > 0 && t < 1 ? 90 + t * 300 : 0;
            return (
              t > 0 && t < 1 && (
                <div
                  key={i}
                  style={{
                    position: "absolute",
                    left: 130 + 60 - r,
                    top: 60 - r,
                    width: r * 2,
                    height: r * 2,
                    borderRadius: "50%",
                    border: "2px solid rgba(59,100,216,0.35)",
                    opacity: (1 - t) * 0.9,
                  }}
                />
              )
            );
          })}
          <div style={{ transform: `scale(${0.6 + 0.4 * iconP})`, opacity: Math.min(1, iconP * 2), width: 120 }}>
            <AppIcon size={120} />
          </div>
          <div style={{ fontFamily: F.sans, fontSize: 72, fontWeight: 800, color: C.ink, marginTop: 40, opacity: titleP, transform: `translateY(${(1 - titleP) * 22}px)`, lineHeight: 1.4 }}>
            有想法？我们都在听。
          </div>
          <div style={{ display: "flex", gap: 16, marginTop: 34 }}>
            {CHIPS.map((c, i) => {
              const p = interpolate(f, [L(1.3) + i * 4, L(1.3) + i * 4 + 10], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
                easing: Easing.bezier(...OUT),
              });
              return (
                <div key={c} style={{ background: "#fff", border: "2px solid rgba(31,31,38,0.14)", borderRadius: 999, padding: "12px 30px", fontFamily: F.sans, fontSize: 28, color: C.ink, opacity: p, transform: `translateY(${(1 - p) * 12}px)` }}>
                  {c}
                </div>
              );
            })}
          </div>
        </div>
        {/* 二维码卡 */}
        <div
          style={{
            background: "#fff",
            borderRadius: 34,
            padding: "40px 44px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            boxShadow: "0 24px 60px rgba(0,23,74,0.16)",
            opacity: qrP,
            transform: `scale(${0.9 + 0.1 * qrP})`,
          }}
        >
          <Img src={staticFile("/textures/qr-gitee.png")} style={{ width: 300, height: 300, borderRadius: 12 }} />
          <div style={{ fontFamily: F.sans, fontSize: 28, fontWeight: 700, color: C.ink, marginTop: 24 }}>扫码直达发布仓</div>
          <div style={{ fontFamily: F.mono, fontSize: 20, color: C.muted, marginTop: 10, letterSpacing: 1 }}>GITEE / GITHUB — CCSUT-KB</div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
