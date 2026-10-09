#!/bin/zsh
# 长工课表通 · 数据抓取 + 数据集生成 + Gitee 发布 一条龙
#
# 更新通道 (Gitee 公开发布仓 yisanspce/ccsut-kb-release):
#   master 分支 = latest.json + 数据集 + 最新 APK, 每次孤儿提交 force-push, 仓库不积累历史
#   仓库内容只有 分发文件 (清单/课表数据/APK/README), 无任何源码
#   源码已全面开源 (GPL-3.0, v1.0.0 前为 MIT): GitHub 主站 https://github.com/yi-san-spce/ccsut-kb
#                         Gitee 中国区镜像 https://gitee.com/yisanspce/ccsut-kb (scripts/push.sh 双站同步)
#
# 前置:
#   1. .session/cookie.txt   有效的教务系统 Cookie (重新抓取时)
#   2. .session/gitee_token  Gitee 私人令牌 (chmod 600)
#
# 用法:
#   scripts/publish.sh                                          # 抓取 + 发布数据
#   scripts/publish.sh --skip                                   # 不重新抓取, 发布现有数据集
#   scripts/publish.sh --skip --apk dist/ccsut-kb-1.0.0.apk     # 连最新 APK 一起发布
#
# 带 APK 发版时自动完成: Gitee 更新通道孤儿提交 → 打 annotated tag v* 推双站 →
# 创建 GitHub Release (APK 附件 + release-notes 正文, 相对链接转绝对)。

set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
DIST="$ROOT/dist"
OWNER=yisanspce
REPO=ccsut-kb-release
API="https://gitee.com/api/v5"
T=$(tr -d '[:space:]' < "$ROOT/.session/gitee_token")

SKIP_SCRAPE=""
APK_PATH=""
ALLOW_DIRTY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --skip) SKIP_SCRAPE=1 ;;
    --apk)  APK_PATH="$2"; shift ;;
    --allow-dirty) ALLOW_DIRTY=1 ;;
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

# ---------- 1. 抓取最新课表并生成数据集 ----------
if [ -z "$SKIP_SCRAPE" ]; then
  python3 scripts/scrape.py
fi

# ---------- 2. 组装发布目录 ----------
mkdir -p "$DIST"
LATEST=$(cat "$ROOT/data/latest.json")
DATASET_FILE=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["file"])')
DATA_VERSION=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["version"])')
cp "$ROOT/data/$DATASET_FILE" "$DIST/"
echo "$LATEST" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))' > "$DIST/latest.json"

# ---------- 3. APK 信息写进清单 (相对路径 = 与清单同目录的 raw 直链) ----------
APK_NAME=""
if [ -n "$APK_PATH" ]; then
  [ -f "$APK_PATH" ] || { echo "APK 不存在: $APK_PATH"; exit 1; }
  # 以 APK 产物自身的版本为准 (build.gradle 常被提前改到下一个版本, 不可信)
  AAPT=$(ls "$HOME/Library/Android/sdk/build-tools/"*/aapt 2>/dev/null | sort | tail -1)
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

# ---------- 更新内容注入: 从 docs/release-notes/v{版本}.md 提取, APP 更新弹窗展示 ----------
import os, re
notes_path = f"docs/release-notes/v{vname}.md"
notes = []
if os.path.exists(notes_path):
    for raw in open(notes_path, encoding="utf-8"):
        s = raw.strip()
        if not s or set(s) <= set("-—= "):
            continue                              # 空行 / 分隔线
        s = re.sub(r"^#{1,6}\s*", "", s)          # 标题井号
        if s.startswith("长工课表通 v"):
            continue                              # 文档大标题, 弹窗里已显示版本号
        if len(s) <= 4:
            continue                              # 「新增/优化/修复」等小节标题, 不是给用户看的条目
        s = re.sub(r"^[-*+]\s+", "", s)           # 列表符
        s = re.sub(r"^\d+\.\s+", "", s)           # 有序列表
        s = s.replace("**", "").replace("`", "")  # 行内强调标记
        if s:
            notes.append(s)
    print(f"清单已写入更新内容: {len(notes)} 条 (来自 {os.path.basename(notes_path)})")
else:
    print(f"⚠️ 未找到 {notes_path}, 更新弹窗将显示默认文案")
m["apk"]["notes"] = notes
json.dump(m, open(path, "w"), ensure_ascii=False, indent=2)
EOF
fi

# ---------- 4. 孤儿提交 force-push → master (清单 + 数据集 + APK + README) ----------
TMP=$(mktemp -d)
git -C "$TMP" init -q -b master
cp "$DIST/latest.json" "$DIST/$DATASET_FILE" "$TMP/"
if [ -n "$APK_NAME" ]; then cp "$DIST/$APK_NAME" "$TMP/"; fi
# 发布仓 README 由 latest.json 单一数据源生成 (当前版本 / 更新内容 / 文件直链表格)
python3 - "$DIST/latest.json" "$TMP" "$OWNER/$REPO" <<'PYEOF'
import json, os, sys
latest, tmp, slug = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(latest))
apk = m.get("apk") or {}
lines = []
w = lines.append
w("# 长工课表通 · 更新发布仓")
w("")
w("本仓只存放「长工课表通」APP 的更新分发文件（更新清单 / 课表数据 / APK 安装包），**不含任何源码**。")
w("由 `scripts/publish.sh` 自动维护（孤儿提交 force-push），请勿手动提交。")
w("")
w("## 当前版本")
w("")
if apk.get("versionName"):
    w(f"**APK v{apk['versionName']}**（versionCode {apk['versionCode']}） · 课表数据集 v{m.get('version')}（{m.get('xnxq', '')}）")
else:
    w(f"课表数据集 v{m.get('version')}（{m.get('xnxq', '')}），本次未随新 APK")
w(f"清单生成时间：{m.get('generatedAt', '')}")
w("")
notes = apk.get("notes") or []
if notes:
    w("## 更新内容")
    w("")
    for n in notes:
        w(f"- {n}")
    w("")
w("## 文件直链（点击即下载）")
w("")
w("| 文件 | 大小 | 说明 |")
w("|---|---|---|")
def size_fmt(p):
    try:
        b = os.path.getsize(p)
    except OSError:
        return "-"
    return f"{b / 1048576:.2f} MB" if b > 1048576 else f"{b / 1024:.0f} KB"
base = f"https://gitee.com/{slug}/raw/master/"
if apk.get("file"):
    w(f"| [APK 安装包]({base}{apk['file']}) | {size_fmt(os.path.join(tmp, apk['file']))} | sha256 `{(apk.get('sha256') or '')[:16]}…`（完整值见 latest.json） |")
w(f"| [课表数据集]({base}{m.get('file', '')}) | {size_fmt(os.path.join(tmp, m.get('file', '')))} | 数据集 v{m.get('version')}，应用内热更通道 |")
w(f"| [latest.json]({base}latest.json) | {size_fmt(os.path.join(tmp, 'latest.json'))} | 更新清单（应用内更新地址） |")
w("")
w("## 历史版本")
w("")
w("全部历史版本与更新说明见 GitHub Releases：<https://github.com/yi-san-spce/ccsut-kb/releases>")
w("")
w("## 相关仓库")
w("")
w("- 源码主站（GitHub，GPL-3.0）：<https://github.com/yi-san-spce/ccsut-kb>")
w("- 源码中国区镜像（Gitee）：<https://gitee.com/yisanspce/ccsut-kb>")
open(os.path.join(tmp, "README.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("发布仓 README 已生成")
PYEOF
git -C "$TMP" add -A
git -C "$TMP" -c user.name=yisanspce -c user.email=yisanspce@noreply.gitee.com \
  commit -qm "publish: 数据 v$DATA_VERSION · ${APK_NAME:-无新APK} · $(date +%F' '%H:%M)"
git -C "$TMP" push -q --force "https://yisanspce:$T@gitee.com/$OWNER/$REPO.git" master 2>&1 \
  | sed 's/yisanspce:[^@]*@/yisanspce:***@/g'
rm -rf "$TMP"

echo "✅ 发布完成: https://gitee.com/$OWNER/$REPO/raw/master/latest.json"
if [ -n "$APK_NAME" ]; then
  echo "✅ APK 直链: https://gitee.com/$OWNER/$REPO/raw/master/$APK_NAME"
fi

# ---------- 5. 打 tag 推双站 + 创建 GitHub Release (仅 APK 发版时) ----------
GH_SLUG=yi-san-spce/ccsut-kb
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
  git push -q origin "refs/tags/$TAG" 2>/dev/null \
    && echo "✅ tag 已推送 Gitee 镜像" \
    || echo "⚠️ tag 推送 Gitee 失败, 可稍后 git push origin $TAG"

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
    if gh release create "$TAG" "$DIST/$APK_NAME" -R "$GH_SLUG" \
        --title "长工课表通 $TAG" --notes-file "$BODY"; then
      echo "✅ GitHub Release: https://github.com/$GH_SLUG/releases/tag/$TAG"
    else
      echo "⚠️ GitHub Release 创建失败 (tag 已推送, 可在 GitHub 手动补建并上传 $APK_NAME)"
    fi
    rm -f "$BODY"
  fi
fi
