#!/bin/zsh
# 推送源码到 GitHub 主站
#
# 用法: scripts/push.sh [分支]     (默认 master)
#   - GitHub: SSH key 认证 (remote: github)
set -e
cd "$(dirname "$0")/.."
BRANCH=${1:-master}

echo "── 推送 GitHub 主站 ──"
git push github "$BRANCH"
echo "✅ 推送完成: github.com/yi-san-spce/ccsut-kb ($BRANCH)"
