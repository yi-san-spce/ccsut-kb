#!/bin/zsh
# 长工课表通 · 数据抓取 + 数据集生成 + Gitee 发布 一条龙
#
# 更新通道 (Gitee 公开发布仓 yisanspce/ccsut-kb-release):
#   - latest.json + 数据集  → master 分支 raw 直链 (每次孤儿提交 force-push, 仓库不积累历史)
#   - APK                  → Release 附件 (tag = v<versionName>, 绝对 URL 写入清单 apk.file)
#   - 源码在私有仓 yisanspce/ccsut-kb, 与本脚本无关
#
# 前置:
#   1. .session/cookie.txt   有效的教务系统 Cookie (重新抓取时)
#   2. .session/gitee_token  Gitee 私人令牌, chmod 600
#
# 用法:
#   scripts/publish.sh                        # 抓取 + 发布数据
#   scripts/publish.sh --skip                 # 不重新抓取, 发布现有数据集
#   scripts/publish.sh --apk <apk路径>        # 发布数据 + APK Release
#   scripts/publish.sh --skip --apk dist/长工课表通_v2.3.0.apk

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

# 空安全取 JSON 字段 (Gitee 对不存在的资源返回 null; release 响应 body 含裸换行, 需 strict=False)
jget() {
  python3 -c '
import json, sys
try: d = json.loads(sys.stdin.read(), strict=False)
except Exception: d = None
print((d or {}).get(sys.argv[1]) or "")' "$1"
}

# ---------- 1. 抓取最新课表并生成数据集 ----------
if [ -z "$SKIP_SCRAPE" ]; then
  python3 scripts/scrape.py
fi

# ---------- 2. 组装发布目录 ----------
mkdir -p "$DIST"
LATEST=$(cat "$ROOT/data/latest.json")
DATASET_FILE=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["file"])')
DATA_VERSION=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["version"])')
XNXQ=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["xnxq"])')
cp "$ROOT/data/$DATASET_FILE" "$DIST/"
echo "$LATEST" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))' > "$DIST/latest.json"

# ---------- 3. APK → Gitee Release ----------
if [ -n "$APK_PATH" ]; then
  [ -f "$APK_PATH" ] || { echo "APK 不存在: $APK_PATH"; exit 1; }
  VCODE=$(grep -o 'versionCode [0-9]*' "$ROOT/android/app/build.gradle" | grep -o '[0-9]*')
  VNAME=$(grep -o 'versionName "[^"]*"' "$ROOT/android/app/build.gradle" | cut -d'"' -f2)
  TAG="v$VNAME"
  APK_NAME="ccsut-kb-$VNAME.apk"
  echo "== 发布 APK $APK_NAME (versionCode $VCODE, tag $TAG) =="

  # 同 tag Release 已存在 → 删除重建, 保证重复发布幂等
  RID=$(curl -s "$API/repos/$OWNER/$REPO/releases/tags/$TAG?access_token=$T" | jget id)
  if [ -n "$RID" ]; then
    echo "Release $TAG 已存在, 删除重建..."
    curl -s -X DELETE "$API/repos/$OWNER/$REPO/releases/$RID?access_token=$T" > /dev/null
    sleep 1
  fi

  # Release 说明: 有 docs/release-notes/<tag>.md 用之, 否则默认文案
  NOTES_FILE="$ROOT/docs/release-notes/$TAG.md"
  if [ -f "$NOTES_FILE" ]; then
    BODY=$(cat "$NOTES_FILE")
  else
    BODY="长工课表通 $TAG (versionCode $VCODE) · 数据版本 v$DATA_VERSION · $XNXQ"
  fi
  CREATE=$(curl -s -X POST "$API/repos/$OWNER/$REPO/releases" \
    -d "access_token=$T" -d "tag_name=$TAG" -d "target_commitish=master" \
    -d "name=长工课表通 $TAG" \
    --data-urlencode "body=$BODY" -d "prerelease=false")
  RID=$(echo "$CREATE" | jget id)
  [ -n "$RID" ] || { echo "创建 Release 失败: $CREATE"; exit 1; }

  UP=$(curl -s -X POST "$API/repos/$OWNER/$REPO/releases/$RID/attach_files?access_token=$T" \
    -F "file=@$APK_PATH;filename=$APK_NAME")
  APK_URL=$(echo "$UP" | jget browser_download_url)
  [ -n "$APK_URL" ] || { echo "上传附件失败: $UP"; exit 1; }
  echo "附件直链: $APK_URL"

  # APK 信息写入 latest.json (绝对 URL, APP 的 resolve() 原生支持)
  APK_SHA=$(shasum -a 256 "$APK_PATH" | cut -d' ' -f1)
  APK_SIZE=$(stat -f%z "$APK_PATH")
  python3 - "$DIST/latest.json" "$VCODE" "$APK_URL" "$APK_SHA" "$APK_SIZE" <<'EOF'
import json, sys
path, vcode, url, sha, size = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4], int(sys.argv[5])
m = json.load(open(path))
m["apk"] = {"versionCode": vcode, "file": url, "sha256": sha, "bytes": size}
json.dump(m, open(path, "w"), ensure_ascii=False, indent=2)
print("清单已写入 apk: versionCode", vcode)
EOF
fi

# ---------- 4. latest.json + 数据集 → master (孤儿提交 force-push) ----------
TMP=$(mktemp -d)
git -C "$TMP" init -q -b master
cp "$DIST/latest.json" "$DIST/$DATASET_FILE" "$TMP/"
cat > "$TMP/README.md" <<EOF
# 长工课表通 · 更新发布仓

APP 内更新通道:

- 清单 (更新地址): https://gitee.com/$OWNER/$REPO/raw/master/latest.json
- APK 下载: 见 [Releases](https://gitee.com/$OWNER/$REPO/releases)
- 源码: 私有仓 $OWNER/ccsut-kb

本仓由 \`scripts/publish.sh\` 自动维护 (孤儿提交 force-push), 请勿手动提交。
EOF
git -C "$TMP" add -A
git -C "$TMP" -c user.name=yisanspce -c user.email=yisanspce@noreply.gitee.com \
  commit -qm "publish: 数据 v$DATA_VERSION · $(date +%F' '%H:%M)"
git -C "$TMP" push -q --force "https://yisanspce:$T@gitee.com/$OWNER/$REPO.git" master 2>&1 \
  | sed 's/yisanspce:[^@]*@/yisanspce:***@/g'
rm -rf "$TMP"

echo "✅ 发布完成: https://gitee.com/$OWNER/$REPO/raw/master/latest.json"
