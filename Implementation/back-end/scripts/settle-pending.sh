#!/usr/bin/env bash
# 提现链路的上游触发：把「到期待结算」结算单结算掉（PENDING → SETTLED = 可提现）。
#
# 背景：`SettlementManager#settle` 是 PENDING→SETTLED 的唯一入口，但在此之前**没有调用者** ——
# 结算单永远停在 PENDING、商家 settled_total 恒为 0 ⇒ 提现在生产上完全不可用。
# 本脚本是那条断链的**运维侧**接法（应用侧接法是 XXL-Job handler `settlePendingSettlements`）。
#
# 用法:
#   bash scripts/settle-pending.sh                 # 只读：报告当前有多少条可结算（默认，安全）
#   bash scripts/settle-pending.sh --trigger       # 先报告，再触发 XXL-Job handler
#   bash scripts/settle-pending.sh --trigger --limit 50
#
# 环境变量（都有默认值，按需覆盖）:
#   DB_HOST=192.168.10.128 DB_PORT=3306 DB_NAME=icedamericano_mall DB_USER=root DB_PASS=root
#   XXL_JOB_TRIGGER_URL   —— XXL-Job 管理端的触发地址（**含 handler 参数**，由运维填完整）:
#       http://<admin>/xxl-job-admin/jobinfo/trigger?id=<jobId>&executorHandler=settlePendingSettlements
#       若为空则只报告、不触发（避免猜地址猜错还以为触发过）。
#
# 判定口径与代码保持一致（不是新口径）: status=PENDING AND period_end<=今天 AND settlement_amount>0 AND order_count>0
#   今天由**本机时钟**给出（`date +%F`），与 `SettlementSettleJob` 用 `LocalDate.now()` 一致；
#   **不要**改成 CURDATE()：应用认为的今天与数据库时区不一定是同一天。
set -euo pipefail

DB_HOST="${DB_HOST:-192.168.10.128}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-icedamericano_mall}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-root}"
XXL_JOB_TRIGGER_URL="${XXL_JOB_TRIGGER_URL:-}"

TRIGGER=0
LIMIT=200
while [ $# -gt 0 ]; do
  case "$1" in
    --trigger) TRIGGER=1; shift ;;
    --limit)   LIMIT="${2:-200}"; shift 2 ;;
    -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
    *) echo "未知参数: $1（用 --help 看用法）" >&2; exit 2 ;;
  esac
done

TODAY="$(date +%F)"
SQL="SELECT COUNT(*) FROM settlement WHERE status = 1 AND period_end <= '${TODAY}' AND settlement_amount > 0 AND order_count > 0;"

echo "── 结算候选（只读） ─────────────────────────────"
echo "库: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}   今天(本机): ${TODAY}   单批上限: ${LIMIT}"

if command -v mysql >/dev/null 2>&1; then
  CNT="$(MYSQL_PWD="${DB_PASS}" mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -N -B "${DB_NAME}" -e "${SQL}")"
  echo "可结算条数: ${CNT}"
  if [ "${CNT}" = "0" ]; then
    echo "⇒ 无候选：要么确实都结完了，要么账期还没走完。不用触发。"
    exit 0
  fi
else
  echo "⚠ 本机没有 mysql 客户端：跳过只读统计（不改用其它方式猜数）"
fi

if [ "${TRIGGER}" != "1" ]; then
  echo
  echo "下一步（二选一）:"
  echo "  1) 再跑一次加 --trigger（需先设 XXL_JOB_TRIGGER_URL）"
  echo "  2) 在 XXL-Job 管理端对 handler「settlePendingSettlements」点『执行一次』"
  echo "幂等：重复触发不会重复记账（条件更新 0 行 = 已结算，跳过）。"
  exit 0
fi

if [ -z "${XXL_JOB_TRIGGER_URL}" ]; then
  echo "✗ 未设置 XXL_JOB_TRIGGER_URL —— 不猜地址。请按 --help 里的格式填完整再跑。" >&2
  exit 3
fi

echo
echo "── 触发调度 ────────────────────────────────────"
echo "触发: ${XXL_JOB_TRIGGER_URL}"
if command -v curl >/dev/null 2>&1; then
  curl -fsS -X POST "${XXL_JOB_TRIGGER_URL}"
  echo
  echo "✓ 已提交触发请求（返回体见上）。触发成功 ≠ 结算成功：请回查"
  echo "  trade-service 日志「settlePendingSettlements 完成，实际结算 N 条」与库内 status 分布。"
else
  echo "✗ 本机没有 curl —— 请改用 XXL-Job 管理端点『执行一次』。" >&2
  exit 3
fi
