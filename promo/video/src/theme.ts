// 设计 tokens —— 来源 promo/DESIGN-SPEC.md（APP 真实设计体系 + 头像采样）
export const C = {
  paper: "#FBF8FF",
  paperDeep: "#F3F0FA",
  ink: "#1F1F26",
  muted: "#595D72",
  blue: "#3B64D8",
  blueDeep: "#00174A",
  blueC: "#DCE3FF",
  blueSoft: "#B7C4FF",
  mustard: "#DAB542",
  handInk: "#2A2A2A",
  dark: "#131318",
  darkSurface: "#1C1C22",
  glassBg: "rgba(255,255,255,0.55)",
  glassBorder: "rgba(255,255,255,0.85)",
  glassShadow: "0 24px 60px rgba(0,23,74,0.14)",
};

export const F = {
  sans: '"PingFang SC","HarmonyOS Sans SC","Source Han Sans SC",sans-serif',
  hand: '"Xingkai SC","Kaiti SC","Marker Felt",cursive',
  mono: '"SF Mono",ui-monospace,Menlo,monospace',
};

// 动效性格：亲和友好预设（spec §2）
export const EASE_INOUT = [0.25, 0.46, 0.45, 0.94] as const;
export const EASE_OUT = [0.16, 1, 0.3, 1] as const;

export const RADIUS = { sm: 14, md: 22, lg: 28, xl: 36 };

/** 纸感噪点层（styleframes 同源） */
export const NOISE_URL =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='0.55'/%3E%3C/svg%3E";
