#!/usr/bin/env bash
# 交接单「不许复述可测量事实」门禁
#
# 为什么要有它：open-items.md 曾被当成 SSOT —— 它写着「测试 2235 全绿 / 已改 16/30」，
# 而真实代码库在同一上午已经变了。**文档复述代码状态 = 定时炸弹**（本项目已因此出过
# `notification.type` 列注释、`ProductVO.name`、`backend/02` "路径刻意不变" 三起事故）。
#
# 判据：交接单只允许写「决策 / 意图 / 约束 / 为什么 / 去哪个命令取事实」，
# **不允许**写「当前测试数 / 当前表数 / 当前端点对账结果 / 某批完成了几分之几」。
# 确属历史证据（某次真库实跑的原样输出）必须显式标注 [历史]，否则视为状态断言。
#
# 用法: bash check-ledger-facts.sh [--verbose]
# 退出码: 0 = 干净; 1 = 存在状态断言
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# LEDGER_PATH 可注入：一是给「门禁自身的变异测试」喂合成样本（见 verify-gates.sh --self-test），
# 二是允许检查别的交接单（如某个 ADR 索引）。默认就是唯一那份交接单。
LEDGER="${LEDGER_PATH:-$ROOT/project-docs/collaboration/open-items.md}"
VERBOSE="${1:-}"

if [ ! -f "$LEDGER" ]; then
  echo "✗ 找不到交接单: $LEDGER" >&2
  exit 1
fi

# 被禁止的「状态断言」形态（POSIX ERE；与中文/英文两种写法都要覆盖）
PATTERNS=(
  '[0-9]{3,5}[[:space:]]*(tests|测试)'      # 2235 测试 / 2254 tests
  '[0-9]+[[:space:]]*failures'              # 0 failures（自证时发现的漏网形态：没有「测试」二字也要拦）
  '[0-9]{1,3}[[:space:]]*张表'              # 62 张表
  '[0-9]{1,3}[[:space:]]*tables'            # 63 tables
  'matched[[:space:]]+[0-9]+'               # 端点对账 matched 289
  '[0-9]{1,3}[[:space:]]*个模块'            # 19 个模块
  '[0-9]{1,3}[[:space:]]*个端点'            # 289 个端点
  '[0-9]{1,2}[[:space:]]*/[[:space:]]*[0-9]{1,2}[[:space:]]*(已改|完成)'  # 16 / 30 已改
  '全绿[[:space:]]*[（(][[:space:]]*[0-9]'  # 全绿（2217
)

hits=0
detail=""
for p in "${PATTERNS[@]}"; do
  # 允许 [历史] 标记的行（历史证据），其余一律视为状态断言
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    lineno="${line%%:*}"
    text="${line#*:}"
    case "$text" in
      *"[历史]"*) continue ;;
    esac
    hits=$((hits + 1))
    detail="${detail}
  $LEDGER:$lineno  匹配 /$p/
    ${text}"
  done < <(grep -nE "$p" "$LEDGER" 2>/dev/null || true)
done

if [ "$hits" -gt 0 ]; then
  echo "✗ 交接单里出现 $hits 处「状态断言」（交接单不是 SSOT，代码才是）："
  echo "$detail"
  echo
  echo "  改法：把数字换成「跑哪条命令去量」。确属历史证据的，在该行标注 [历史]。"
  exit 1
fi

echo "✓ 交接单未复述可测量事实（无状态断言）"
[ "$VERBOSE" = "--verbose" ] && echo "  已检查: $LEDGER"
exit 0
