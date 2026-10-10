#!/usr/bin/env python3
"""BGM 候选节奏分析 —— 按 video-shotcraft references/music-beat-sync.md §1-3
对每首候选曲输出: 网格拟合(bpm/t0/T/残差) + 0.5x/1x/2x 网格验收 +
kick/snare/hihat 三分类命中表 + RMS 能量结构。产物: analysis/<track>/beat_data.json
"""
import json, os, sys
import numpy as np
import librosa
from scipy.signal import butter, sosfilt

SKILL_BGM = "/Users/yisan/.zcode/skills/video-shotcraft/assets/audio/bgm"
OUT_ROOT = "/Users/yisan/projects/长沙工业学院课表一键查/promo/video/analysis"

def band_env(y, sr, lo, hi):
    sos = butter(4, [lo, hi], btype="band", fs=sr, output="sos")
    env = librosa.onset.onset_strength(y=sosfilt(sos, y), sr=sr)
    return env, librosa.times_like(env, sr=sr)

def peak_hits(env, times, t0, T, n_beats, top_frac=0.5, dedup_s=0.12):
    """在每个网格拍附近取 env 峰值, 输出超过中位数的命中(候选池)"""
    hits = []
    if len(env) == 0:
        return hits
    med = float(np.median(env[env > 0])) if (env > 0).any() else 1.0
    last_t = -1e9
    for n in range(n_beats):
        t = t0 + n * T
        i = np.argmin(np.abs(times - t))
        win = env[max(0, i - 2): i + 3]
        e = float(win.max())
        if e >= med * 1.4 and times[i] - last_t > dedup_s:
            hits.append({"t": round(float(times[i]), 4), "n": n, "s": round(e / (med * 3), 3)})
            last_t = float(times[i])
    hits.sort(key=lambda h: -h["s"])
    return hits

def grid_metrics(grid_t0, T, onset_times, max_ms=70):
    """网格拍与真实瞬态的对齐质量"""
    n = min(int((onset_times[-1] - grid_t0) / T), 4000) if len(onset_times) else 0
    matched, errs = 0, []
    for i in range(n):
        t = grid_t0 + i * T
        d = np.min(np.abs(onset_times - t))
        if d * 1000 <= max_ms:
            matched += 1
            errs.append(d * 1000)
    if n == 0:
        return {"match": 0.0, "mean_abs_ms": 999.0}
    resid = np.array(errs) if errs else np.array([999.0])
    return {
        "match": round(matched / n, 4),
        "mean_abs_ms": round(float(resid.mean()), 2),
    }

def analyze(path):
    name = os.path.splitext(os.path.basename(path))[0]
    outdir = os.path.join(OUT_ROOT, name)
    os.makedirs(outdir, exist_ok=True)
    y, sr = librosa.load(path, sr=None, mono=True)
    dur = len(y) / sr
    y_perc = librosa.effects.hpss(y)[1]

    tempo, beats = librosa.beat.beat_track(y=y_perc, sr=sr, tightness=400, units="time")
    i = np.arange(len(beats))
    A = np.vstack([i, np.ones_like(i)]).T
    (T, t0), *_ = np.linalg.lstsq(A, beats, rcond=None)
    resid = beats - (t0 + i * T)
    bpm = 60.0 / T

    onset_env = librosa.onset.onset_strength(y=y_perc, sr=sr)
    onset_times = librosa.times_like(onset_env, sr=sr)[librosa.onset.onset_detect(onset_envelope=onset_env, sr=sr)]

    # 0.5x / 1x / 2x 网格验收
    cands = {}
    for label, (g_t0, g_T) in {
        "0.5x": (t0, T * 2), "1x": (t0, T), "2x": (t0, T / 2),
    }.items():
        cands[label] = {"grid_metrics": grid_metrics(g_t0, g_T, onset_times)}
    winner = max(cands, key=lambda k: (cands[k]["grid_metrics"]["match"], -cands[k]["grid_metrics"]["mean_abs_ms"]))
    if winner == "0.5x":
        T, t0 = T * 2, t0
        bpm = 60.0 / T
    elif winner == "2x":
        T, t0 = T / 2, t0
        bpm = 60.0 / T

    n_beats = int((dur - t0) / T)
    kick_env, ktimes = band_env(y_perc, sr, 40, 160)
    sn1_env, s1times = band_env(y_perc, sr, 150, 500)
    sn2_env, s2times = band_env(y_perc, sr, 1000, 3000)
    snare_env = sn1_env + sn2_env
    hi_env, htimes = band_env(y_perc, sr, 6000, 14000)

    # RMS 能量结构(每 0.5s)
    rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0]
    rt = librosa.times_like(rms, sr=sr, hop_length=512)
    step = int(0.5 / (rt[1] - rt[0])) if len(rt) > 1 else 1
    rms_ds = [{"t": round(float(rt[i]), 2), "v": round(float(rms[i]), 4)} for i in range(0, len(rms), step)]
    rvals = np.array([r["v"] for r in rms_ds])
    full_start = next((r["t"] for r, v in zip(rms_ds, rvals) if v > rvals.max() * 0.55), 0)

    data = {
        "track": name, "duration": round(dur, 2), "sr": sr,
        "bpm": round(float(bpm), 2), "t0": round(float(t0), 4), "T": round(float(T), 5),
        "grid_residual_ms": round(float(np.abs(resid).max()) * 1000, 1),
        "beat_count": n_beats,
        "grid_candidates": cands, "grid_winner": winner,
        "full_energy_from": round(float(full_start), 2),
        "rms": rms_ds,
        "hits_kick": peak_hits(kick_env, ktimes, t0, T, n_beats)[:40],
        "hits_snare": peak_hits(snare_env, s1times, t0, T, n_beats)[:40],
        "hits_hihat": peak_hits(hi_env, htimes, t0, T, n_beats)[:60],
    }
    with open(os.path.join(outdir, "beat_data.json"), "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)

    top_kick = data["hits_kick"][0] if data["hits_kick"] else None
    print(f"{name}: dur={dur:.1f}s BPM={bpm:.2f} t0={t0:.3f} 残差={data['grid_residual_ms']}ms "
          f"网格={winner} match={cands[winner]['grid_metrics']['match']} "
          f"满能量@{full_start:.1f}s 最强kick@b{top_kick['n'] if top_kick else '-'}")

if __name__ == "__main__":
    files = sys.argv[1:] or [os.path.join(SKILL_BGM, f) for f in sorted(os.listdir(SKILL_BGM)) if f.endswith(".mp3")]
    for p in files:
        try:
            analyze(p)
        except Exception as e:
            print(f"FAIL {p}: {e}")
