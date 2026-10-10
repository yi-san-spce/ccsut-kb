import React from "react";
import { staticFile, useCurrentFrame, interpolate, Easing, Img } from "remotion";
import { C, F, NOISE_URL } from "../theme";

const EASE_OUT_ARR = [0.16, 1, 0.3, 1] as [number, number, number, number];

/* ---------- 基础 ---------- */

export const AbsoluteFill: React.FC<{
  style?: React.CSSProperties;
  children?: React.ReactNode;
}> = ({
  style,
  children,
}) => (
  <div
    style={{
      position: "absolute",
      inset: 0,
      display: "flex",
      ...style,
    }}
  >
    {children}
  </div>
);

export const Noise: React.FC<{ opacity?: number }> = ({ opacity = 0.05 }) => (
  <div
    style={{
      position: "absolute",
      inset: 0,
      opacity,
      pointerEvents: "none",
      backgroundImage: `url("${NOISE_URL}")`,
    }}
  />
);

/* ---------- APP 图标（真实截图抠图） ---------- */

export const AppIcon: React.FC<{
  size: number;
  shadow?: boolean;
}> = ({ size, shadow = true }) => (
  <Img
    src={staticFile("textures/cut-appicon.png")}
    style={{
      width: size,
      height: size,
      objectFit: "contain",
      borderRadius: size * 0.24,
      boxShadow: shadow ? `0 ${size * 0.12}px ${size * 0.36}px rgba(59,100,216,0.35)` : undefined,
    }}
  />
);

/* ---------- 玻璃卡（B 骨架材质） ---------- */

export const GlassCard: React.FC<{
  style?: React.CSSProperties;
  children?: React.ReactNode;
}> = ({ style, children }) => (
  <div
    style={{
      background: C.glassBg,
      backdropFilter: "blur(24px)",
      WebkitBackdropFilter: "blur(24px)",
      border: "1.5px solid rgba(255,255,255,0.85)",
      boxShadow: `${C.glassShadow}, inset 0 1px 0 rgba(255,255,255,0.9)`,
      borderRadius: 36,
      ...style,
    }}
  >
    {children}
  </div>
);

/* ---------- 手机（纹理坐标系容器） ----------
 * texture 为 1260×2600 截图; children 以纹理像素坐标定位, 随容器缩放。
 * overflow 裁切模拟屏幕。 */
export const Phone: React.FC<{
  width: number;
  texture?: string; // staticFile 相对路径; 缺省为纯色屏
  screenBg?: string;
  style?: React.CSSProperties;
  children?: React.ReactNode; // 纹理坐标系 overlay
  frame?: boolean;
}> = ({ width, texture, screenBg = "#fff", style, children, frame = true }) => {
  const H = (2600 / 1260) * width;
  const scale = width / 1260;
  return (
    <div
      style={{
        width,
        height: H,
        borderRadius: width * 0.078,
        overflow: "hidden",
        background: screenBg,
        border: frame ? `${Math.max(6, width * 0.018)}px solid #fff` : undefined,
        boxShadow: "0 30px 80px rgba(31,31,38,0.22), 0 4px 16px rgba(31,31,38,0.10)",
        position: "relative",
        ...style,
      }}
    >
      {texture && (
        <Img
          src={staticFile(texture)}
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            width,
            height: H,
            objectFit: "cover",
          }}
        />
      )}
      {children && (
        <div
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            width: 1260,
            height: 2600,
            transform: `scale(${scale})`,
            transformOrigin: "top left",
          }}
        >
          {children}
        </div>
      )}
    </div>
  );
};

/* ---------- 手绘太阳（可分段描边生长） ----------
 * viewBox 400×400; p: 各部位 0-1 进度 */
export const RAYS: [number, number, number, number][] = [
  [200, 14, 199, 52], [297, 41, 277, 74], [364, 106, 333, 127],
  [386, 200, 348, 199], [358, 297, 326, 278], [300, 363, 279, 332],
  [201, 386, 200, 349], [104, 360, 124, 328], [37, 295, 69, 275],
  [14, 199, 52, 200], [41, 103, 73, 123], [102, 38, 122, 69],
];

export const HandSun: React.FC<{
  size: number;
  outlineP?: number; // 轮廓描边进度
  fillP?: number; // 芥末黄填充透明度
  raysP?: number; // 光线整体进度(逐根 stagger)
  faceP?: number; // 表情进度
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  style?: React.CSSProperties;
}> = ({
  size,
  outlineP = 1,
  fillP = 1,
  raysP = 1,
  faceP = 1,
  fill = C.mustard,
  stroke = C.handInk,
  strokeWidth = 8,
  style,
}) => {
  const dash = (p: number) => ({ strokeDasharray: 1, strokeDashoffset: 1 - Math.max(0, Math.min(1, p)) });
  const rayN = RAYS.length;
  return (
    <svg width={size} height={size} viewBox="0 0 400 400" fill="none" style={style}>
      <g stroke={stroke} strokeWidth={strokeWidth * 0.875} strokeLinecap="round">
        {RAYS.map(([x1, y1, x2, y2], i) => {
          const rp = interpolate(raysP, [i / rayN, (i + 2) / rayN], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(...EASE_OUT_ARR),
          });
          return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} pathLength={1} {...dash(rp)} />;
        })}
      </g>
      <circle cx={202} cy={202} r={118} fill={fill} opacity={fillP} />
      <circle cx={196} cy={199} r={116} stroke={stroke} strokeWidth={strokeWidth} pathLength={1} {...dash(outlineP)} transform="rotate(-90 196 199)" />
      <g stroke={stroke} strokeWidth={strokeWidth * 1.125} strokeLinecap="round" fill="none">
        <path d="M156 178 q14 -22 30 -2" pathLength={1} {...dash(Math.min(1, faceP * 2))} />
        <path d="M222 176 q14 -22 30 -2" pathLength={1} {...dash(Math.max(0, Math.min(1, faceP * 2 - 1)))} />
        <path d="M143 232 q56 52 114 -4" pathLength={1} {...dash(Math.max(0, Math.min(1, faceP * 3 - 2)))} />
      </g>
    </svg>
  );
};

/* ---------- 主叙事字幕（Q11: ≥56px 档） ---------- */

export const CaptionLarge: React.FC<{
  text: string;
  sub?: string;
  enterFrom?: number; // 局部帧
  duration: number;
  y?: number;
  color?: string;
  subColor?: string;
  size?: number;
}> = ({ text, sub, enterFrom = 0, duration, y = 880, color = C.ink, subColor = C.muted, size = 64 }) => {
  const frame = useCurrentFrame();
  const inP = interpolate(frame, [enterFrom, enterFrom + 14], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(...EASE_OUT_ARR),
  });
  const outP = interpolate(frame, [duration - 12, duration - 2], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        top: y,
        textAlign: "center",
        opacity: inP * outP,
        transform: `translateY(${(1 - inP) * 22}px)`,
        fontFamily: F.sans,
      }}
    >
      <div style={{ fontSize: size, fontWeight: 700, color, letterSpacing: 2 }}>{text}</div>
      {sub && (
        <div style={{ fontSize: size * 0.46, color: subColor, marginTop: 20, letterSpacing: 6 }}>{sub}</div>
      )}
    </div>
  );
};

/* ---------- 手写标注 ---------- */

export const HandNote: React.FC<{
  text: string;
  style?: React.CSSProperties;
  p?: number; // 出现进度
  rotate?: number;
  size?: number;
}> = ({ text, style, p = 1, rotate = 0, size = 40 }) => (
  <div
    style={{
      position: "absolute",
      fontFamily: F.hand,
      fontSize: size,
      color: "#8A6D1C",
      opacity: p,
      transform: `rotate(${rotate}deg) translateY(${(1 - p) * 10}px)`,
      ...style,
    }}
  >
    {text}
  </div>
);
