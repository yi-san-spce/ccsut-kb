#!/bin/zsh
# 长工课表通 · 数据抓取 + 数据集生成 + Cloudflare Pages 发布 一条龙
#
# 前置:
#   1. .session/cookie.txt 里有有效的教务系统 Cookie (过期重新复制)
#   2. npm i -g wrangler && wrangler login  (首次)
#   3. 已创建 Pages 项目: wrangler pages project create ccsut-kb --production-branch main
#
# 用法:
#   scripts/publish.sh            # 抓取+构建+发布(数据)
#   scripts/publish.sh --skip     # 不重新抓取, 只发布现有数据集

set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
DIST="$ROOT/dist"

# 1. 抓取最新课表并生成数据集
if [ "$1" != "--skip" ]; then
  python3 scripts/scrape.py
fi

# 2. 组装发布目录
mkdir -p "$DIST"
LATEST=$(cat "$ROOT/data/latest.json")
DATASET_FILE=$(echo "$LATEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["file"])')
cp "$ROOT/data/$DATASET_FILE" "$DIST/"
echo "$LATEST" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))' > "$DIST/latest.json"

# 3. 如果有构建好的 APK, 一并发布 (发布前手动把它拷到 dist/)
if [ -f "$DIST/update.apk" ]; then
  APK_SHA=$(shasum -a 256 "$DIST/update.apk" | cut -d' ' -f1)
  APK_SIZE=$(stat -f%z "$DIST/update.apk")
  python3 - "$DIST/latest.json" "$APK_SHA" "$APK_SIZE" <<'EOF'
import json, sys
path, sha, size = sys.argv[1], sys.argv[2], int(sys.argv[3])
m = json.load(open(path))
m["apk"] = {"versionCode": 2, "file": "update.apk", "sha256": sha, "bytes": size}
# 发布 APK 新版时手动把 versionCode 改成与 app/build.gradle 一致的值
json.dump(m, open(path, "w"), ensure_ascii=False, indent=2)
print("已把 APK 加入清单 (记得核对 versionCode)")
EOF
fi

# 4. 发布到 Cloudflare Pages
wrangler pages deploy "$DIST" --project-name ccsut-kb --branch main
echo "发布完成: https://ccsut-kb.pages.dev/latest.json"
