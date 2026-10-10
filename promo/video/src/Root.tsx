import React from "react";
import { AbsoluteFill, Audio, Composition, Sequence, interpolate, staticFile } from "remotion";
import { C, F } from "./theme";
import { SHOTS, DURATION, FPS } from "./beats";
import { SFX } from "./sfx";
import { Shot01Dawn } from "./shots/s01-dawn";
import { Shot02Brand } from "./shots/s02-brand";
import { Shot03OpenWeek } from "./shots/s03-openweek";
import { Shot04StatusCard } from "./shots/s04-statuscard";
import { Shot05WeekSwitch } from "./shots/s05-weekswitch";
import { Shot06Themes } from "./shots/s06-themes";
import { Shot07Wallpaper } from "./shots/s07-wallpaper";
import { Shot08Reminders } from "./shots/s08-reminders";
import { Shot09Alarm } from "./shots/s09-alarm";
import { Shot10Widget } from "./shots/s10-widget";
import { Shot11SafeLocal } from "./shots/s11-safe-local";
import { Shot12SafePledge } from "./shots/s12-safe-pledge";
import { Shot13Thanks } from "./shots/s13-thanks";
import { Shot14Community } from "./shots/s14-community";
import { Shot15ChapterDev } from "./shots/s15-chapter-dev";
import { Shot16Opensource } from "./shots/s16-opensource";
import { Shot17Pipeline } from "./shots/s17-pipeline";
import { Shot18Dataset } from "./shots/s18-dataset";
import { Shot19Privacy } from "./shots/s19-privacy";
import { Shot20Tests } from "./shots/s20-tests";
import { Shot21Invite } from "./shots/s21-invite";
import { Shot22Outro } from "./shots/s22-outro";

export const REGISTRY: Record<string, React.FC> = {
  "s01-dawn": Shot01Dawn,
  "s02-brand": Shot02Brand,
  "s03-openweek": Shot03OpenWeek,
  "s04-statuscard": Shot04StatusCard,
  "s05-weekswitch": Shot05WeekSwitch,
  "s06-themes": Shot06Themes,
  "s07-wallpaper": Shot07Wallpaper,
  "s08-reminders": Shot08Reminders,
  "s09-alarm": Shot09Alarm,
  "s10-widget": Shot10Widget,
  "s11-safe-local": Shot11SafeLocal,
  "s12-safe-pledge": Shot12SafePledge,
  "s13-thanks": Shot13Thanks,
  "s14-community": Shot14Community,
  "s15-chapter-dev": Shot15ChapterDev,
  "s16-opensource": Shot16Opensource,
  "s17-pipeline": Shot17Pipeline,
  "s18-dataset": Shot18Dataset,
  "s19-privacy": Shot19Privacy,
  "s20-tests": Shot20Tests,
  "s21-invite": Shot21Invite,
  "s22-outro": Shot22Outro,
};

const Placeholder: React.FC<{ id: string }> = ({ id }) => (
  <AbsoluteFill style={{ background: C.paperDeep, alignItems: "center", justifyContent: "center" }}>
    <div style={{ fontFamily: F.mono, fontSize: 28, color: C.muted }}>{id} · 制作中</div>
  </AbsoluteFill>
);

export const Promo: React.FC<{ bgm: boolean }> = ({ bgm }) => {
  return (
    <AbsoluteFill style={{ background: C.paper, fontFamily: F.sans }}>
      {SHOTS.map((s) => {
        const Comp = REGISTRY[s.id] ?? (() => <Placeholder id={s.id} />);
        return (
          <Sequence key={s.id} from={s.from} durationInFrames={s.to - s.from} name={s.id}>
            <Comp />
          </Sequence>
        );
      })}
      {bgm && (
        <Audio
          src={staticFile("audio/bgm-full.mp3")}
          volume={(f) =>
            interpolate(
              f,
              [0, 12, DURATION - 30, DURATION - 2],
              [0, 0.34, 0.34, 0],
              { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
            )
          }
        />
      )}
      {/* SFX 钉帧表(相对拍号+偏移补偿, 见 sfx.ts) */}
      {SFX.map((s, i) => (
        <Sequence key={`sfx-${i}`} from={s.from} durationInFrames={s.durationInFrames ?? 90} name={`sfx:${s.src.split("/").pop()}`}>
          <Audio src={staticFile(`audio/${s.src}`)} volume={s.volume} />
        </Sequence>
      ))}
    </AbsoluteFill>
  );
};

export const RemotionRoot: React.FC = () => (
  <Composition
    id="CcsutKbPromo"
    component={Promo}
    durationInFrames={DURATION}
    fps={FPS}
    width={1920}
    height={1080}
    defaultProps={{ bgm: true }}
  />
);
