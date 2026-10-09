#!/bin/zsh
# 双站推送: GitHub 主站 + Gitee 中国区副站
#
# 用法: scripts/push.sh [分支]     (默认 master)
#   - GitHub: SSH key 认证 (remote: github)
#   - Gitee:  私人令牌一次性 URL (令牌存 .session/gitee_token, 不写入 git config)
set -e
cd "$(dirname "$0")/.."
T=$(tr -d '[:space:]' < .session/gitee_token)
BRANCH=${1:-master}

echo "── 推送 GitHub 主站 ──"
git push github "$BRANCH"
echo "── 推送 Gitee 副站 ──"
git push "https://yisanspce:${T}@gitee.com/yisanspce/ccsut-kb.git" "$BRANCH" | sed "s/${T}/<TOKEN>/g"
echo "✅ 双站推送完成: github.com/yi-san-spce/ccsut-kb + gitee.com/yisanspce/ccsut-kb"
