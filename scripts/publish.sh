#!/bin/zsh
# 长工课表通 · 数据抓取 + 数据集生成 + 发布 一条龙
#
# 更新通道:
#   - 稳定版 (默认): GitHub Releases + versions.json / versions-preview.json
#   - 抢先版 (--pre): GitHub prerelease (tag vX.Y.Z-pre.YYYYMMDD), 并把全通道索引 versions-preview.json
#     回填到最新稳定 Release —— App 抢先通道读 releases/latest/download/versions-preview.json
#   - 内测版 (--beta): 仅创建 GitHub prerelease, **不进任何公开索引**; 脚本末尾打印可直接粘贴进
#     团队内测清单 (versions-beta.json) 的条目 JSON, 内测清单链接由团队私发给内测用户填入 App
#
# versionCode 分段 (三通道防倒挂, 发预发布前由脚本强校验):
#   versionCode = 主版本×10⁸ + 次版本×10⁶ + 修订×10⁴ + 段位
#   段位: 抢先 0..4999 (epoch 天数%5000) | 内测 5000..8999 (5000+序号) | 稳定 9000
#   即同主版本内 抢先 < 内测 < 稳定, 且下一个稳定版必然高于之前所有预发布
#
# 前置:
#   1. .session/cookie.txt   有效的教务系统 Cookie (重新抓取时)
#   2. gh CLI 已登录 (GitHub Release 自动化用)
#
# 用法:
#   scripts/publish.sh                                          # 抓取 + 发布稳定版数据
#   scripts/publish.sh --skip                                   # 不重新抓取, 发布现有数据集
#   scripts/publish.sh --skip --apk dist/ccsut-kb-1.0.1-stable.apk  # 稳定版连 APK 一起发布 (versionName 须为 X.Y.Z-stable)
#   scripts/publish.sh --skip --pre  --apk <apk>                # 抢先版发布 (APK versionName 须为 X.Y.Z-pre.YYYYMMDD)
#   scripts/publish.sh --skip --beta --apk <apk>                # 内测版发布 (APK versionName 须为 X.Y.Z-beta.N)
# 版本类后缀 (App 内 Channels 约定, 全英文): 稳定 X.Y.Z-stable / 抢先 X.Y.Z-pre.YYYYMMDD / 内测 X.Y.Z-beta.N;
# 开发者版 X.Y.Z-dev 仅 debug 构建自动挂载, 不进任何版本通道、不发布。

set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
DIST="$ROOT/dist"
# GitHub 主仓 (App 更新的数据源: Releases API / Release 资产 / 主仓 raw 数据清单)
GH_SLUG=yi-san-spce/ccsut-kb
# App 内置镜像清单的远程副本: 随 versions.json 下发, 镜像站失效时改这里即可让所有用户换血, 无需发版
GH_MIRRORS="https://ghproxy.link https://gh.llkk.cc https://ghfast.top https://ghproxy.net https://gh-proxy.com"

SKIP_SCRAPE=""
APK_PATH=""
ALLOW_DIRTY=""
PRERELEASE=""
KIND=stable
while [ $# -gt 0 ]; do
  case "$1" in
    --skip) SKIP_SCRAPE=1 ;;
    --apk)  APK_PATH="$2"; shift ;;
    --allow-dirty) ALLOW_DIRTY=1 ;;
    --beta) PRERELEASE=1; KIND=beta ;;
    --pre)  PRERELEASE=1; KIND=preview ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
  shift
done

# ---------- 0. 发布门禁: 工作区必须干净 ----------
# 教训 (v2.10.3): 发版构建与并行会话的提交同分钟竞态, APK 裹进了半成品代码导致启动闪退。
# 未提交改动可能是别人会话的中间态 —— 要么先提交, 要么确知无误时用 --allow-dirty 自担风险。
if [ -n "$(git status --porcelain)" ] && [ -z "$ALLOW_DIRTY" ]; then
  echo "❌ 发布中止: 工作区有未提交改动 (可能混入并行会话的半成品):"
  git status --short | head -10
  echo "   先 commit, 或确知无误时用 --allow-dirty 跳过本检查。"
  exit 1
fi

# ---------- 1. 抓取最新课表并生成数据集 (预发布与数据无关, 自动跳过) ----------
if [ -z "$PRERELEASE" ] && [ -z "$SKIP_SCRAPE" ]; then
  python3 scripts/scrape.py
fi

# ---------- 2. APK 版本读取 (aapt 以产物为准, 稳定/预发布共用) ----------
# build.gradle 常被提前改到下一个版本, 不可信
VCODE=""
VNAME=""
if [ -n "$APK_PATH" ]; then
  [ -f "$APK_PATH" ] || { echo "APK 不存在: $APK_PATH"; exit 1; }
  AAPT=$(ls "$HOME/Library/Android/sdk/build-tools/"*/aapt(N) /opt/homebrew/share/android-commandlinetools/build-tools/*/aapt(N) 2>/dev/null | sort | tail -1)
  if [ -n "$AAPT" ]; then
    BADGING=$("$AAPT" dump badging "$APK_PATH")
    VCODE=$(echo "$BADGING" | grep -o "versionCode='[0-9]*'" | head -1 | grep -o '[0-9]*')
    VNAME=$(echo "$BADGING" | grep -o "versionName='[^']*'" | head -1 | sed "s/^versionName='//;s/'$//")
  else
    echo "⚠️ 未找到 aapt, 退回读 build.gradle 版本 (可能超前于该 APK)"
    VCODE=$(grep -o 'versionCode [0-9]*' "$ROOT/android/app/build.gradle" | grep -o '[0-9]*')
    VNAME=$(grep -o 'versionName "[^"]*"' "$ROOT/android/app/build.gradle" | cut -d'"' -f2)
  fi
  [ -n "$VNAME" ] || { echo "无法确定 APK 版本名"; exit 1; }
fi

# ---------- 预发布流程 (内测 --beta / 抢先 --pre): 不碰 latest.json / 稳定索引 ----------
if [ -n "$PRERELEASE" ]; then
  if [ -z "$VNAME" ]; then
    echo "❌ 预发布需要 --apk 指定 APK"; exit 1
  fi
  # 格式与 versionCode 分段强校验 (抢先按今天/昨天两天宽限, 允许隔夜发)
  python3 - "$VNAME" "$VCODE" "$KIND" <<'PYEOF'
import sys, re, datetime
vname, vcode, kind = sys.argv[1], int(sys.argv[2]), sys.argv[3]
m = re.match(r"^(\d+)\.(\d+)\.(\d+)-(beta|pre)\.?(\d*)$", vname)
if not m or m.group(4) != kind:
    want = "X.Y.Z-beta.N" if kind == "beta" else "X.Y.Z-pre.YYYYMMDD"
    print(f"❌ versionName '{vname}' 不符合{kind}格式, 应为 {want} (改 build.gradle 后重新构建)")
    sys.exit(1)
major, minor, patch, seq = int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(5) or 0)
base = major * 10**8 + minor * 10**6 + patch * 10**4
if kind == "beta":
    expected = base + 5000 + seq
else:
    today = (datetime.date.today() - datetime.date(1970, 1, 1)).days
    expected = base + today % 5000
    yesterday = base + (today - 1) % 5000
    if vcode == yesterday:
        expected = yesterday
if vcode != expected:
    print(f"❌ versionCode {vcode} 与分段方案不符, 期望 {expected}")
    print("   公式: 主版本×10⁸ + 次版本×10⁶ + 修订×10⁴ + 段位 (内测 5000+序号 / 抢先 epoch天数%5000, 稳定 9000)")
    print("   改 build.gradle 后重新构建 APK 再发布")
    sys.exit(1)
print(f"✅ 版本分段校验通过: v{vname} → versionCode {vcode}")
PYEOF
  APK_NAME="ccsut-kb-$VNAME.apk"
  cp "$APK_PATH" "$DIST/$APK_NAME"
  APK_SHA=$(shasum -a 256 "$DIST/$APK_NAME" | cut -d' ' -f1)
  APK_SIZE=$(stat -f%z "$DIST/$APK_NAME")

  # 更新内容提取 (与稳定路径同一规则) → notes.json
  python3 - "$VNAME" "$DIST/notes.json" <<'PYEOF'
import json, sys, os, re
vname, out_path = sys.argv[1], sys.argv[2]
notes_path = f"docs/release-notes/v{vname}.md"
notes, sections = [], []
if os.path.exists(notes_path):
    cur = None
    for raw in open(notes_path, encoding="utf-8"):
        s = raw.strip()
        if not s or set(s) <= set("-—= "):
            continue
        h = re.match(r"^#{1,6}\s+(.+)$", s)
        if h:
            t = h.group(1).strip()
            if t.startswith("长工课表通"):
                continue
            cur = {"title": t, "items": []}
            sections.append(cur)
            continue
        if s.startswith("#") or (s.startswith("![") and "](" in s):
            continue
        s = re.sub(r"^>\s*", "", s)                 # 引用块
        if not s:
            continue
        s = re.sub(r"^[-*+]\s+", "", s)             # 列表符
        s = re.sub(r"^\d+\.\s+", "", s)             # 有序列表
        s = re.sub(r"\[([^]]+)\]\([^)]*\)", r"\1", s)
        s = s.replace("**", "").replace("`", "")
        if not s:
            continue
        if cur is None:
            cur = {"title": None, "items": []}
            sections.append(cur)
        cur["items"].append(s)
    notes = [i for sec in sections for i in sec["items"]]
json.dump({"notes": notes, "sections": sections}, open(out_path, "w", encoding="utf-8"), ensure_ascii=False)
print(f"更新内容: {len(notes)} 条 / {len(sections)} 个小节")
PYEOF

  # 预发布 tag 由 GitHub 从远端默认分支创建, 先把本地提交推上去 (失败仅提示, 不阻断)
  git push -q github HEAD 2>/dev/null || echo "⚠️ master 推送 GitHub 失败, tag 可能指向旧提交, 建议手动 push.sh 后重发"

  TAG="v$VNAME"
  BODY=$(mktemp)
  NOTES_MD="docs/release-notes/v$VNAME.md"
  if [ -f "$NOTES_MD" ]; then
    sed -E 's|\]\((\.\./)+|](https://github.com/'"$GH_SLUG"'/blob/master/|g' "$NOTES_MD" > "$BODY"
  else
    echo "长工课表通 $TAG" > "$BODY"
  fi

  PRE_INDEX_ARGS=""
  if [ "$KIND" = preview ]; then
    # 抢先版: 全通道索引 = 稳定索引 (channel=stable) + 历史抢先条目 + 本版;
    # 回填到最新稳定 Release, 让 releases/latest/download/versions-preview.json 始终最新
    curl -m 15 -sL "https://github.com/$GH_SLUG/releases/latest/download/versions.json" -o "$DIST/stable-snapshot.json" || true
    curl -m 15 -sL "https://github.com/$GH_SLUG/releases/latest/download/versions-preview.json" -o "$DIST/prev-preview.json" || true
    python3 - "$DIST/stable-snapshot.json" "$DIST/prev-preview.json" "$DIST/notes.json" "$DIST/versions-preview.json" "$VNAME" "$VCODE" "$APK_NAME" "$APK_SHA" "$APK_SIZE" <<'PYEOF'
import json, sys, os, datetime
stable_path, prev_path, notes_path, out_path, vname, vcode, name, sha, size = sys.argv[1:10]
vcode, size = int(vcode), int(size)
def load(p, key):
    try:
        return json.load(open(p, encoding="utf-8")).get(key) or []
    except Exception:
        return []
stable = []
for e in load(stable_path, "versions"):
    e["channel"] = "stable"
    stable.append(e)
if not stable:
    # 稳定索引快照拉取失败时, 保留旧索引里的稳定条目, 避免抢先通道丢稳定版
    stable = [e for e in load(prev_path, "versions") if e.get("channel") == "stable"]
pre = [e for e in load(prev_path, "versions")
       if e.get("channel") == "preview" and e.get("versionName") != vname]
new_entry = {
    "versionCode": vcode, "versionName": vname,
    "date": datetime.date.today().isoformat(),
    "file": name, "sha256": sha, "bytes": size, "channel": "preview",
    "sections": load(notes_path, "sections"), "notes": load(notes_path, "notes"),
}
entries = sorted([new_entry] + pre + stable, key=lambda e: e.get("versionCode") or 0, reverse=True)
json.dump({"versions": entries}, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(f"versions-preview.json 已更新: 稳定 {len(stable)} + 抢先 {len(pre) + 1}")
PYEOF
    PRE_INDEX_ARGS="$DIST/versions-preview.json"  fi

  if gh release create "$TAG" "$DIST/$APK_NAME" $PRE_INDEX_ARGS -R "$GH_SLUG" --prerelease \
      --title "长工课表通 $TAG" --notes-file "$BODY"; then
    echo "✅ 预发布 Release: https://github.com/$GH_SLUG/releases/tag/$TAG"
  else
    echo "❌ GitHub Release 创建失败 (tag 可能已存在)"
    exit 1
  fi
  rm -f "$BODY"

  if [ "$KIND" = preview ]; then
    STABLE_TAG=$(gh api "repos/$GH_SLUG/releases/latest" --jq .tag_name)
    if gh release upload "$STABLE_TAG" "$DIST/versions-preview.json" --clobber -R "$GH_SLUG"; then
      echo "✅ 抢先索引已回填到最新稳定 Release ($STABLE_TAG)"
    else
      echo "⚠️ 抢先索引回填失败, 可手动: gh release upload $STABLE_TAG dist/versions-preview.json --clobber"
    fi
  else
    # 内测: 打印可直接粘贴进团队内测清单 (versions-beta.json) 的条目
    python3 - "$VNAME" "$VCODE" "$APK_NAME" "$APK_SHA" "$APK_SIZE" "$DIST/notes.json" <<'PYEOF'
import json, sys, datetime
vname, vcode, name = sys.argv[1], int(sys.argv[2]), sys.argv[3]
sha, size = sys.argv[4], int(sys.argv[5])
meta = json.load(open(sys.argv[6], encoding="utf-8"))
entry = {
    "versionCode": vcode, "versionName": vname,
    "date": datetime.date.today().isoformat(),
    "file": name, "sha256": sha, "bytes": size, "channel": "beta",
    "sections": meta.get("sections") or [], "notes": meta.get("notes") or [],
    "fileUrl": f"https://github.com/yi-san-spce/ccsut-kb/releases/download/v{vname}/{name}",
}
print("把下面这条 JSON 追加到内测清单 versions-beta.json 的 versions 数组最前 (发到团队内测群里):")
print(json.dumps(entry, ensure_ascii=False))
print(f"内测版直链: https://github.com/yi-san-spce/ccsut-kb/releases/download/v{vname}/{name}")
PYEOF
    echo "ℹ️ 内测版不进任何公开索引, 仅通过上面链接/条目由团队私发"
  fi
  exit 0
fi

# ---------- 3. 组装发布目录 (稳定版) ----------
mkdir -p "$DIST"
LATEST=$(cat "$ROOT/data/latest.json")
DATASET_FILE=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["file"])')
cp "$ROOT/data/$DATASET_FILE" "$DIST/"
echo "$LATEST" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))' > "$DIST/latest.json"

# ---------- 4. APK 信息写进清单 (相对路径 = 与清单同目录的 raw 直链) ----------
APK_NAME=""
if [ -n "$APK_PATH" ]; then
  APK_NAME="ccsut-kb-$VNAME.apk"
  cp "$APK_PATH" "$DIST/$APK_NAME"
  APK_SHA=$(shasum -a 256 "$DIST/$APK_NAME" | cut -d' ' -f1)
  APK_SIZE=$(stat -f%z "$DIST/$APK_NAME")
  python3 - "$DIST/latest.json" "$VCODE" "$VNAME" "$APK_NAME" "$APK_SHA" "$APK_SIZE" <<'EOF'
import json, sys
path, vcode, vname, name, sha, size = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5], int(sys.argv[6])
m = json.load(open(path))
m["apk"] = {"versionCode": vcode, "versionName": vname, "file": name, "sha256": sha, "bytes": size}
json.dump(m, open(path, "w"), ensure_ascii=False, indent=2)
print("清单已写入 apk:", "v"+vname, name)

# ---------- 更新内容注入: 从 docs/release-notes/v{版本}.md 提取 ----------
# notes = 扁平条目 (兼容旧版 App 更新弹窗); sections = 小节分组 (新版「软件更新」页按标题分段展示)
import os, re
notes_path = f"docs/release-notes/v{vname}.md"
notes, sections = [], []
if os.path.exists(notes_path):
    cur = None                                   # 当前小节 {"title": str|None, "items": []}
    for raw in open(notes_path, encoding="utf-8"):
        s = raw.strip()
        if not s or set(s) <= set("-—= "):
            continue                              # 空行 / 分隔线
        h = re.match(r"^#{1,6}\s+(.+)$", s)
        if h:                                     # 标题行: 文档大标题跳过, 其余作为小节标题
            t = h.group(1).strip()
            if t.startswith("长工课表通"):
                continue
            cur = {"title": t, "items": []}
            sections.append(cur)
            continue
        if s.startswith("#"):
            continue                              # 异常井号行, 保守跳过
        if s.startswith("![") and "](" in s:
            continue                              # 图片
        s = re.sub(r"^>\s*", "", s)               # 引用块标记 (摘要行)
        if not s:
            continue
        s = re.sub(r"^[-*+]\s+", "", s)           # 列表符
        s = re.sub(r"^\d+\.\s+", "", s)           # 有序列表
        s = re.sub(r"\[([^]]+)\]\([^)]*\)", r"\1", s)   # [文字](链接) → 文字
        s = s.replace("**", "").replace("`", "")  # 行内强调标记
        if not s:
            continue
        if cur is None:
            cur = {"title": None, "items": []}    # 首个小节之前的散条
            sections.append(cur)
        cur["items"].append(s)
    notes = [i for sec in sections for i in sec["items"]]
    print(f"清单已写入更新内容: {len(notes)} 条 / {len(sections)} 个小节 (来自 {os.path.basename(notes_path)})")
else:
    print(f"⚠️ 未找到 {notes_path}, 更新页将显示默认文案")
m["apk"]["notes"] = notes
m["apk"]["sections"] = sections
json.dump(m, open(path, "w"), ensure_ascii=False, indent=2)
EOF
fi

# ---------- 5. 组装 versions 索引 ----------
TMP=$(mktemp -d)
# versions.json 的 prev 索引取自 GitHub 最新 Release 快照 (全新首发时为空, 不影响)
curl -m 15 -sL "https://github.com/$GH_SLUG/releases/latest/download/versions.json" -o "$TMP/versions.json" || true
if [ -n "$APK_NAME" ]; then
  cp "$DIST/$APK_NAME" "$TMP/"
  # versions.json: 稳定版全量索引 (App 稳定/抢先通道与官方兜底的数据源), 新条目插最前, 保留最近 30 个
  # 同时写入: mirrors = App 远程镜像清单; downloads = 各版本 GitHub 资产下载量快照 (gh api 逐版查询, 尽力而为)
  python3 - "$TMP/versions.json" "$DIST/latest.json" "$VCODE" "$VNAME" "$APK_NAME" "$APK_SHA" "$APK_SIZE" "$(date +%F)" "$GH_MIRRORS" "$GH_SLUG" <<'PYEOF'
import json, sys, os
versions_path, latest_path, vcode, vname, name, sha, size, date = sys.argv[1:9]
mirrors, gh_slug = sys.argv[9].split(), sys.argv[10]
vcode, size = int(vcode), int(size)

def downloads_of(vn):
    # GitHub Releases 资产下载量快照 (镜像模式没有实时统计, 随发版回填); gh 不可用时跳过
    try:
        import subprocess
        r = subprocess.run(
            ["gh", "api", f"repos/{gh_slug}/releases/tags/v{vn}",
             "--jq", "[.assets[] | select(.name | endswith(\".apk\")) | .download_count] | add // 0"],
            capture_output=True, text=True, timeout=20)
        v = int(r.stdout.strip() or -1)
        return v if v >= 0 else None
    except Exception:
        return None

prev = {"versions": []}
if os.path.exists(versions_path):
    try:
        prev = json.load(open(versions_path, encoding="utf-8"))
    except Exception:
        prev = {"versions": []}
apk = json.load(open(latest_path, encoding="utf-8")).get("apk") or {}
entries = [e for e in (prev.get("versions") or []) if e.get("versionName") != vname]
entries.insert(0, {
    "versionCode": vcode, "versionName": vname, "date": date,
    "file": name, "sha256": sha, "bytes": size,
    "sections": apk.get("sections") or [],
    "notes": apk.get("notes") or [],
})
entries.sort(key=lambda e: e.get("versionCode") or 0, reverse=True)
entries = entries[:30]
for e in entries:
    d = downloads_of(e["versionName"])
    if d is not None:
        e["downloads"] = d
json.dump({"generatedAt": apk.get("generatedAt") or "", "mirrors": mirrors, "versions": entries},
          open(versions_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(f"versions.json 已更新: {len(entries)} 个版本, 最新 v{vname}, 镜像 {len(mirrors)} 个")
PYEOF
  cp "$TMP/versions.json" "$DIST/versions.json"
  # versions-preview.json: 全通道索引 (稳定 + 抢先), 抢先通道数据源; 稳定发布时顺手重建, 让抢先用户也跟进
  curl -m 15 -sL "https://github.com/$GH_SLUG/releases/latest/download/versions-preview.json" -o "$DIST/prev-preview.json" || true
  python3 - "$TMP/versions.json" "$DIST/prev-preview.json" "$TMP/versions-preview.json" <<'PYEOF'
import json, sys
stable_path, prev_path, out_path = sys.argv[1:4]
def load(p, key):
    try:
        return json.load(open(p, encoding="utf-8")).get(key) or []
    except Exception:
        return []
stable = []
for e in load(stable_path, "versions"):
    e["channel"] = "stable"
    stable.append(e)
pre = [e for e in load(prev_path, "versions") if e.get("channel") == "preview"][:10]
entries = sorted(pre + stable, key=lambda e: e.get("versionCode") or 0, reverse=True)
json.dump({"versions": entries}, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(f"versions-preview.json 已更新: 稳定 {len(stable)} + 抢先 {len(pre)}")
PYEOF
  cp "$TMP/versions-preview.json" "$DIST/versions-preview.json"
fi
rm -rf "$TMP"

# ---------- 6. 打 tag 推 GitHub + 创建 GitHub Release (仅 APK 发版时) ----------
if [ -n "$APK_NAME" ] && [ -n "$VNAME" ]; then
  TAG="v$VNAME"
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    echo "tag $TAG 已存在, 跳过打 tag"
  else
    git tag -a "$TAG" -m "长工课表通 $TAG"
    echo "✅ 已打 tag $TAG"
  fi
  git push -q github "refs/tags/$TAG" 2>/dev/null \
    && echo "✅ tag 已推送 GitHub" \
    || echo "⚠️ tag 推送 GitHub 失败, 可稍后 git push github $TAG"

  if gh release view "$TAG" -R "$GH_SLUG" >/dev/null 2>&1; then
    echo "GitHub Release $TAG 已存在, 跳过"
  else
    BODY=$(mktemp)
    NOTES="docs/release-notes/v$VNAME.md"
    if [ -f "$NOTES" ]; then
      # 相对链接在 Release 页面会失效, 转为仓库绝对链接
      sed -E 's|\]\((\.\./)+|](https://github.com/'"$GH_SLUG"'/blob/master/|g' "$NOTES" > "$BODY"
    else
      echo "长工课表通 $TAG" > "$BODY"
    fi
    PRE_INDEX_ARGS=""
    [ -f "$DIST/versions-preview.json" ] && PRE_INDEX_ARGS="$DIST/versions-preview.json"
    if gh release create "$TAG" "$DIST/$APK_NAME" "$DIST/versions.json" $PRE_INDEX_ARGS -R "$GH_SLUG" \
        --title "长工课表通 $TAG" --notes-file "$BODY"; then
      echo "✅ GitHub Release: https://github.com/$GH_SLUG/releases/tag/$TAG"
    else
      echo "⚠️ GitHub Release 创建失败 (tag 已推送, 可在 GitHub 手动补建并上传 $APK_NAME / versions.json)"
    fi
    rm -f "$BODY"
  fi
fi
