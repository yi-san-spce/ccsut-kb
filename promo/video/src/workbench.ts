// 动效工作台清单 —— 全部表格从 beats/sfx 导入(同源原则, 不抄第二份)
// 时间与图层编辑对所有镜头可用; 语境级参数在各自 shots/*.tsx 的常量里。
import React from "react";
import { SHOTS, DURATION, FPS } from "./beats";
import { SFX } from "./sfx";
import { Promo, REGISTRY } from "./Root";

const LABELS: Record<string, string> = {
  "s01-dawn": "S1 晨光开场",
  "s02-brand": "S2 品牌玻璃卡",
  "s03-openweek": "S3 打开即看",
  "s04-statuscard": "S4 今日状态卡",
  "s05-weekswitch": "S5 周次切换",
  "s06-themes": "S6 主题扫场",
  "s07-wallpaper": "S7 背景取色",
  "s08-reminders": "S8 提醒设置",
  "s09-alarm": "S9 闹钟响铃",
  "s10-widget": "S10 桌面小组件",
  "s11-safe-local": "S11 数据在手机上",
  "s12-safe-pledge": "S12 三条承诺",
  "s13-thanks": "S13 感谢",
  "s14-community": "S14 社区 CTA",
  "s15-chapter-dev": "S15 章节转场",
  "s16-opensource": "S16 全开源",
  "s17-pipeline": "S17 抓取链路",
  "s18-dataset": "S18 数据管线",
  "s19-privacy": "S19 隐私工程",
  "s20-tests": "S20 工程质量",
  "s21-invite": "S21 邀请共创",
  "s22-outro": "S22 收场签名",
};

export const WORKBENCH = {
  name: "长工课表通 · 宣传片",
  fps: FPS,
  width: 1920,
  height: 1080,
  total: DURATION,
  background: "#FBF8FF",
  shots: SHOTS.map((s) => ({
    id: s.id,
    label: LABELS[s.id] ?? s.id,
    from: s.from,
    duration: s.to - s.from,
    component: REGISTRY[s.id] as React.FC,
  })),
  sfx: SFX.map((s) => ({
    from: s.from,
    duration: s.durationInFrames ?? 90,
    src: `audio/${s.src}`,
    volume: s.volume,
  })),
  bgm: [{ from: 0, duration: DURATION, src: "audio/bgm-full.mp3", volume: 0.34 }],
  original: Promo,
};
