#!/usr/bin/env bash
# 先验证、再提交：mvn test 通过才执行 git commit（不靠"记得先跑测试"这种纪律）。
#
# 用法：
#   MODULES=trade-service TESTS='PayoutStaleAlerterTest,WithdrawalPayoutServiceTest' \
#     bash scripts/commit-verified.sh -m "msg" -- <paths...>
#   MODULES='' 表示跑全 reactor（慢，但最稳）
set -euo pipefail
BE="$(cd "$(dirname "$0")/.." && pwd)"
MODULES="${MODULES-trade-service}"
TESTS="${TESTS:-}"

ARGS=(-o -f "$BE/pom.xml" test)
[ -n "$MODULES" ] && ARGS+=(-pl "$MODULES")
[ -n "$TESTS" ] && ARGS+=(-Dtest="$TESTS")

echo "▶ 先验证：mvn ${ARGS[*]}"
mvn "${ARGS[@]}"
echo "✓ 验证通过，开始提交"
git commit "$@"
