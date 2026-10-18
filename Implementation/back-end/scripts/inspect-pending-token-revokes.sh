#!/usr/bin/env bash
# 只读排查：哪些用户「已禁用但令牌吊销没有终结记录」（U17 对账补偿的人工入口）
#
# 为什么需要这个脚本（而不是只给一句 SQL）：
#   `TokenRevokeReconcileManager` 会自动重放，但只在**禁用时刻 + 24 小时**窗口内。
#   超出窗口的失败不会自动收敛 —— 运维需要一个**可执行、只读、不会误改**的入口来捞这批数据，
#   而不是临时手写 SQL（手写 SQL 容易漏 action 名、漏 NOT EXISTS、甚至误加 UPDATE）。
#
# 判定口径与代码保持一致（禁止另立一套）：
#   候选 = operation_log 有 action='USER:DISABLE_USER'
#   终结 = 同 target 有 action='USER:REVOKE_USER_TOKENS' 且 create_time >= 该禁用记录时刻
#   待处理 = 候选有、终结无
#
# 用法：
#   scripts/inspect-pending-token-revokes.sh                # 只看窗口内（最近 24h 的禁用）
#   scripts/inspect-pending-token-revokes.sh --all          # 看全部历史（窗口外人工收敛用）
#   MYSQL_HOST=... MYSQL_PORT=... MYSQL_DB=... MYSQL_USER=... MYSQL_PWD=... scripts/...
#
# 安全：全部是 SELECT，不含任何写操作；密码不落仓库（用 MYSQL_PWD 或 ~/.my.cnf）。
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-icedamericano_mall}"
MYSQL_USER="${MYSQL_USER:-root}"

WINDOW_CLAUSE="AND d.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)"
if [[ "${1:-}" == "--all" ]]; then
  WINDOW_CLAUSE=""   # 窗口外：人工收敛用（自动化不会处理这批）
  echo "[warn] --all：包含窗口外（>24h）的历史失败，这些**不会**被对账 Job 自动重放" >&2
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "[error] 找不到 mysql 客户端；请安装或在有客户端的机器上执行本脚本的 SQL 部分" >&2
  exit 1
fi

mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DB" --table -e "
SELECT d.target                                   AS user_id,
       MIN(d.create_time)                         AS disabled_at,
       COUNT(*)                                   AS disable_rows,
       TIMESTAMPDIFF(HOUR, MIN(d.create_time), NOW()) AS hours_since
FROM operation_log d
WHERE d.action = 'USER:DISABLE_USER'
  AND d.target IS NOT NULL
  ${WINDOW_CLAUSE}
  AND NOT EXISTS (
      SELECT 1 FROM operation_log r
      WHERE r.action = 'USER:REVOKE_USER_TOKENS'
        AND r.target = d.target
        AND r.create_time >= d.create_time
  )
GROUP BY d.target
ORDER BY disabled_at;
"

echo
echo "说明："
echo "  1) 上表每行 = 一个「账号已禁用、但旧令牌可能仍有效」的用户；请人工核实后决定是否重新禁用/吊销。"
echo "  2) 不要手工 UPDATE operation_log 去「补一条终结记录」—— 那会让对账永久跳过该用户，"
echo "     而真实状态没有被修正（登记在 open-items U17）。"
echo "  3) 需要重放时，调 authorization-service 的用户级吊销入口（/internal/auth/token-version/revoke）。"
