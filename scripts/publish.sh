#!/bin/zsh
# 长工课表通 · 数据抓取 + 数据集生成 + Gitee 发布 一条龙
#
# 更新通道 (Gitee 公开发布仓 yisanspce/ccsut-kb-release):
#   master 分支 = latest.json + 数据集 + 最新 APK, 每次孤儿提交 force-push, 仓库不积累历史
#   ⚠️ 故意不用 Release/标签: Gitee Release 页面会自动挂「源码归档」下载按钮, 造成源码公开的误会
#   仓库内容只有 分发文件 (清单/课表数据/APK/README), 无任何源码; 源码在私有仓 yisanspce/ccsut-kb
#
# 前置:
#   1. .session/cookie.txt   有效的教务系统 Cookie (重新抓取时)
#   2. .session/gitee_token  Gitee 私人令牌 (chmod 600)
#
# 用法:
#   scripts/publish.sh                                          # 抓取 + 发布数据
#   scripts/publish.sh --skip                                   # 不重新抓取, 发布现有数据集
#   scripts/publish.sh --skip --apk dist/长工课表通_v2.3.0.apk   # 连最新 APK 一起发布

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
while [ $# -gt 0 ]; do
  case "$1" in
    --skip) SKIP_SCRAPE=1 ;;
    --apk)  APK_PATH="$2"; shift ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
  shift
done

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
cat > "$TMP/README.md" <<EOF
# 长工课表通 · 更新发布仓

本仓只存放「长工课表通」APP 的更新分发文件（更新清单 / 课表数据 / APK 安装包），**不含任何源码**。

- 更新清单（APP 内更新地址）：https://gitee.com/$OWNER/$REPO/raw/master/latest.json
- 源码：私有仓 $OWNER/ccsut-kb（不公开）

本仓由 \`scripts/publish.sh\` 自动维护（孤儿提交 force-push），请勿手动提交。
EOF
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
