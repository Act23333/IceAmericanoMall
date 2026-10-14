#!/usr/bin/env bash
# =============================================================================
# ADR-050/051 不变量门禁（CI 与本地均可运行）
#
# 背景：业务 ID 的存储边界规则（BINARY(16) + IdGenerator + toBytes 参数）
# 属于「文档约定 + 人工评审」型约束，mock 单测无法覆盖。本脚本把最关键的三条
# 固化为机械检查，防止后续会话/多人协作再次漂移（2026-10-10 CR 结论）。
#
# 用法：仓库根执行  bash Implementation/back-end/scripts/check-id-invariants.sh
# CI：.github/workflows/ci.yml 在编译前调用；退出码非 0 即构建失败。
# =============================================================================
set -uo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$BACKEND_DIR"
fail=0

# 白名单：非业务标识用途的合法 UUID 生成点（trace / event / mock，已在 ADR-050 登记）
ALLOWED_RE='IdGenerator\.java|common/filter/TraceIdFilter\.java|common/filter/UserContextFilter\.java|common/event/DomainEvent\.java|gateway/filter/TraceIdFilter\.java|MockStorageClient\.java|MockInvoiceIssueAdapter\.java'

# 检查 1：业务代码禁止散落 UUID 生成（必须走 IdGenerator.nextId / secureToken）
hits1=$(grep -rlE 'UUID\.randomUUID|IdUtil\.fastSimpleUUID' --include='*.java' */src/main/java 2>/dev/null | grep -vE "$ALLOWED_RE" || true)
if [ -n "$hits1" ]; then
  echo "[FAIL] ADR-050：以下主代码存在散落的 UUID 生成（应走 org.icedamericanomall.common.utils.IdGenerator）："
  echo "$hits1"
  fail=1
fi

# 检查 2：BINARY(16) 业务 ID 字段的包装器条件禁止传字符串字面量（应 IdGenerator.toBytes/toBytesOrNull）
# 命中面：.eq/.in/.ne(XxxPO::getXxxId|No, "字面量")——String 对 BINARY(16) 列会【静默查空】；
# 过滤注释行（javadoc 反例示例如 IdGenerator 自身说明不属违例）
hits2=$(grep -rnE '\.(eq|in|ne)\([A-Za-z]+PO::get[A-Za-z]*(Id|No),\s*"' --include='*.java' */src/main/java 2>/dev/null \
  | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)' || true)
if [ -n "$hits2" ]; then
  echo "[FAIL] ADR-050：以下包装器条件对 ID 字段传字符串字面量（BINARY(16) 列静默查空；技术键字段传字符串字面量同样是缺陷）："
  echo "$hits2"
  fail=1
fi

# 检查 3：显式 SQL 中 BINARY(16) 业务 ID 列禁止裸字符串字面量等值比较
# （应 #{param} + byte[] 参数化，或与种子/运维场景显式 UNHEX('...')）
ID_COLS='order_no|pay_order_no|refund_no|invoice_no|settlement_no|withdrawal_no|team_no|message_id|product_id|sku_id|user_id|used_order_no|biz_order_no|template_no|transaction_no|coupon_id|conversation_id'
hits3=$(grep -rnE "(${ID_COLS})[[:space:]]*=[[:space:]]*'" --include='*.java' */src/main/java 2>/dev/null \
  | grep -vE 'UNHEX|BIN_TO_UUID|HEX\(' \
  | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)' || true)
if [ -n "$hits3" ]; then
  echo "[FAIL] ADR-050：以下显式 SQL 对业务 ID 列做裸字符串比较（应参数化 byte[] 或 UNHEX 包裹）："
  echo "$hits3"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "[OK] ID 不变量门禁通过（ADR-050/051：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令）"
fi
exit "$fail"
