#!/usr/bin/env bash
# 交接单「不许复述可测量事实」门禁
#
# 为什么要有它：open-items.md 曾被当成 SSOT —— 它写着「测试全绿 / 已改大半」，而真实代码库
# 在同一上午已经变了。**文档复述代码状态 = 定时炸弹**（本项目已因此出过 `notification.type`
# 列注释、`ProductVO.name`、`backend/02` "路径刻意不变" 三起事故）。
#
# 判据：交接单只允许写「决策 / 意图 / 约束 / 为什么 / 去哪个命令取事实」，
# **不允许**写「当前测试数 / 当前表数 / 当前端点对账结果 / 某批完成了几分之几」。
# 确属历史证据（某次真库实跑的原样输出）必须显式标注 [历史]，否则视为状态断言。
#
# 覆盖的表述变体（见 --self-test 的样本表）：
#   数量 + 事实名词的任意组合（允许中间的 个/张/条/份/项/余/多/左右 等量词与空格、Markdown 强调）；
#   **模糊措辞也算违规**（约 / 大约 / 近 / 约计 / ~）——「约 N 个测试」与「N 个测试」一样会漂；
#   分数进度（16 / 30 已改）、matched N、codeOnly N、N failures。
#
# 用法:
#   bash check-ledger-facts.sh              # 检查真实交接单
#   bash check-ledger-facts.sh --self-test  # 门禁自身的变异测试：合成违规样本必须红、[历史] 必须豁免
#   LEDGER_PATH=/path/to/x.md bash check-ledger-facts.sh   # 检查别的文件
#
# 退出码: 0 = 干净; 1 = 存在状态断言/自证失败
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# LEDGER_PATH 可注入：给「门禁自身的变异测试」喂合成样本，或检查别的交接单。
LEDGER="${LEDGER_PATH:-$ROOT/project-docs/collaboration/open-items.md}"

# 「数量 + 事实名词」的统一判据。设计要点：
#   · 量词放在数字之后（「2284 个测试」「63 张表」）或之前（没有）；用可选组吃掉；
#   · 模糊前缀（约/大约/近/约计/~）**必须**被认成违规，否则「约 2254 测试」就绕过去了；
#   · 名词表只收**会随代码漂的事实**：测试/表/tables/tests/模块/端点/failures/错误码。
#     刻意不收「方法/类/次/行」——那些属于设计口径，误伤会让交接单不可用。
FACT_PATTERN='(?<![A-Za-z0-9_-])(约|大约|近|约计|~)?[[:space:]]*[0-9]+[[:space:]]*(个|张|条|份|项|余|多|左右)?[[:space:]]*(tests|测试|tables|表|模块|端点|failures|错误码)'

PATTERNS=(
  "$FACT_PATTERN"                                     # 数量 + 事实名词（含模糊措辞/量词/Markdown 强调）
  'matched[[:space:]]*[0-9]+'                         # 端点对账 matched 289
  'codeOnly[[:space:]]*[0-9]+'                        # 端点漂移 codeOnly 0
  '[0-9]+[[:space:]]*/[[:space:]]*[0-9]+[[:space:]]*(已改|完成)'   # 16 / 30 已改
)

# 需要 PCRE（负向后行断言排除标识符里的数字，否则「H2 测试」「JDK 21 测试」会假阳性）。
# git-bash(GNU grep) 与 ubuntu 的 grep 都支持 -P；不支持时退回 -E 并自报降级，不静默。
if echo x | grep -Pq x 2>/dev/null; then
  GREP_MODE="-P"
else
  GREP_MODE="-E"
  echo "⚠ 当前 grep 不支持 -P：退回 -E，形如「H2 测试」的标识符可能被误判为状态断言" >&2
fi

# ---------------------------------------------------------------------------
# 单个文件 → 违规行
# ---------------------------------------------------------------------------
scan_file() {
  local file="$1" hits=0 detail="" p line lineno text
  for p in "${PATTERNS[@]}"; do
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      lineno="${line%%:*}"
      text="${line#*:}"
      case "$text" in
        *"[历史]"*) continue ;;   # 历史证据显式豁免
      esac
      hits=$((hits + 1))
      detail="${detail}
  $file:$lineno  匹配 /$p/
    ${text}"
    done < <(grep -n "$GREP_MODE" "$p" "$file" 2>/dev/null || true)
  done
  SCAN_HITS="$hits"
  SCAN_DETAIL="$detail"
}

# ---------------------------------------------------------------------------
# --self-test：门禁自身的变异测试
#   · 每条「应违规」样本都必须被拦（漏一条 = 门禁有洞）
#   · 每条「应放行」样本都必须放行（误伤 = 门禁会被绕过/被删）
# ---------------------------------------------------------------------------
self_test() {
  local tmp rc=0
  tmp="$(mktemp)"

  # 应违规（含模糊措辞与量词变体）
  local should_flag=(
    '| 测试 | 2254 / 0 failures |'
    '| 表 | 62 张表 |'
    '| 对账 | matched 289 / codeOnly 0 |'
    '- 已改 16 / 30 已改'
    '约 2254 测试通过'
    '大约 63 张表'
    '近 2300 tests'
    '约计 19 个模块'
    '2284 个测试'
    '63 张 表（Markdown 强调：**63** 张表）'
    '端点共 289 个端点'
    '错误码 99 个错误码'
  )
  # 应放行（设计口径 / 标识符 / 历史证据）
  local should_pass=(
    '最多 5 次重试、2^n 分钟退避'
    '前 3 轮验证、13 个方法不透传'
    'ADR-056 / 1103 / §6 的补偿表'
    'H2 测试的键集 / JDK 21 下的分支（标识符里的数字不算状态断言）'
    '[历史] 2026-01-01 曾 2254 测试 / 0 failures'
  )

  echo "── 门禁自证：应违规样本（共 ${#should_flag[@]} 条）──"
  local s
  for s in "${should_flag[@]}"; do
    printf '%s\n' "$s" >"$tmp"
    if LEDGER_PATH="$tmp" "$0" >/dev/null 2>&1; then
      echo "  ✗ 漏拦：$s"
      rc=1
    else
      echo "  ✓ 拦住：$s"
    fi
  done

  echo "── 门禁自证：应放行样本（共 ${#should_pass[@]} 条，误伤即失败）──"
  for s in "${should_pass[@]}"; do
    printf '%s\n' "$s" >"$tmp"
    if LEDGER_PATH="$tmp" "$0" >/dev/null 2>&1; then
      echo "  ✓ 放行：$s"
    else
      echo "  ✗ 误伤：$s"
      rc=1
    fi
  done

  rm -f "$tmp"
  return "$rc"
}

# ---------------------------------------------------------------------------
main() {
  local mode="${1:-}"
  if [ "$mode" = "--self-test" ]; then
    self_test && { echo "✓ 门禁自证通过（无漏拦、无误伤）"; exit 0; }
    echo "✗ 门禁自证失败：见上方漏拦 / 误伤项" >&2
    exit 1
  fi

  if [ ! -f "$LEDGER" ]; then
    echo "✗ 找不到交接单: $LEDGER" >&2
    exit 1
  fi

  scan_file "$LEDGER"
  if [ "$SCAN_HITS" -gt 0 ]; then
    echo "✗ 交接单里出现 $SCAN_HITS 处「状态断言」（交接单不是 SSOT，代码才是）："
    echo "$SCAN_DETAIL"
    echo
    echo "  改法：把数字换成「跑哪条命令去量」。确属历史证据的，在该行标注 [历史]。"
    exit 1
  fi
  echo "✓ 交接单未复述可测量事实（无状态断言）"
  exit 0
}

main "$@"
