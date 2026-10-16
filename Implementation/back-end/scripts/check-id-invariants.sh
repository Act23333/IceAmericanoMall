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
ID_COLS='order_no|pay_order_no|refund_no|invoice_no|settlement_no|withdrawal_no|team_no|message_id|spu_id|sku_id|user_id|used_order_no|biz_order_no|template_no|transaction_no|coupon_id|conversation_id'
hits3=$(grep -rnE "(${ID_COLS})[[:space:]]*=[[:space:]]*'" --include='*.java' */src/main/java 2>/dev/null \
  | grep -vE 'UNHEX|BIN_TO_UUID|HEX\(' \
  | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)' || true)
if [ -n "$hits3" ]; then
  echo "[FAIL] ADR-050：以下显式 SQL 对业务 ID 列做裸字符串比较（应参数化 byte[] 或 UNHEX 包裹）："
  echo "$hits3"
  fail=1
fi


# 检查 4：线格式标识字段的【类型】必须为 String（ADR-061 §守卫：闭合 ADR-059 只查命名未查类型的漏洞）
# 命中面：adapter/response、adapter/request 与 ia-api dto/ 下的字段名 spuId/spuIds/skuId/skuIds
#        （含 hotSpuIds / hotSkuIds 等「SPU/SKU 业务标识 + 前缀」形态）
# 断言：其 Java 类型必须是 String 或 List<String>；出现 Long/Integer/int/long 一律失败。
# 依据：契约 §1.8「类型也是口径的一部分 —— 字段名对而类型为 Long 同样是违规」。
#
# ⚠ 目录枚举必须用 find（2026-10-17 修复，ADR-061 §后果 R7）：
#   旧写法 `FORMAT_DIRS="*/src/main/java/*/adapter/response ..."` 依赖 bash 展开，
#   而 bash 的 `*` 不跨 `/` 且脚本未开 globstar —— 实际目录深度为
#   `<service>/src/main/java/org/icedamericanomall/<svc>/adapter/{response,request}`
#   （多一层包路径）→ 该 glob **零匹配**，检查 4 对 adapter 面从未真正扫描（假绿）。
#   find 对路径深度不敏感，且下面的自证（命中目录数为 0 即 fail）防止再次「零匹配却报 OK」。
FORMAT_DIRS=()
while IFS= read -r d; do
  [ -n "$d" ] && FORMAT_DIRS+=("$d")
done < <(
  find . -type d -path '*/src/main/java/*/adapter/response' 2>/dev/null
  find . -type d -path '*/src/main/java/*/adapter/request' 2>/dev/null
  find ia-api/src/main/java/org/icedamericanomall/dto -type d 2>/dev/null
) || true
FORMAT_DIRS=($(printf '%s\n' "${FORMAT_DIRS[@]:-}" | sed '/^$/d' | sort -u))
SCANNED_DIRS=${#FORMAT_DIRS[@]}

# 自证：命中面为零即门禁自身失效 → 直接失败（不得静默通过）
if [ "$SCANNED_DIRS" -eq 0 ]; then
  echo "[FAIL] ADR-061：线格式类型检查的命中面为 0 个目录 —— 门禁自身失效（glob/路径基准错误），不得据 [OK] 判定合规。"
  fail=1
fi

# 字段名口径：以 [sS]puId/[sS]puIds/[sS]kuId/[sS]kuIds 结尾（含 hotSpuIds 等前缀形态）
FIELD_SUFFIX='([A-Za-z0-9_]*)([sS]puId|[sS]puIds|[sS]kuId|[sS]kuIds)'
# 合法类型：String / List<String>（允许空格变体）
TYPE_OK_RE='private[[:space:]]+(String|List[[:space:]]*<[[:space:]]*String[[:space:]]*>)[[:space:]]+'

hits4=""
for d in "${FORMAT_DIRS[@]}"; do
  matches=$(grep -rnE "private[[:space:]]+(static[[:space:]]+)?(final[[:space:]]+)?[A-Za-z0-9_.]+([[:space:]]*<[[:space:]]*[A-Za-z0-9_.]+([[:space:]]*,[[:space:]]*[A-Za-z0-9_.]+)*[[:space:]]*>)?([[:space:]]*\[[[:space:]]*\])?[[:space:]]+${FIELD_SUFFIX}[[:space:]]*;[[:space:]]*\$" --include='*.java' "$d" 2>/dev/null \
    | grep -vE "$TYPE_OK_RE" || true)
  if [ -n "$matches" ]; then
    hits4="${hits4}${matches}
"
  fi
done

# 白名单：ADR-061 §决策 4 + §后果 R1 登记的**有意口径并存**字段（技术键），一行一项，逐条给理由。
# 条目口径 =「文件名:行:字段名声明」逐字匹配（与下方 HIT_ITEMS 同形，保证「陈旧项」可判定；
# 字段改名/移位/改型后必须同步更新本项，否则门禁会点名该条陈旧 —— 不允许静默豁免）。
WHITELIST=(
  # RecommendSpuVO.spuId（item，Long）—— 与商品详情路径变量 GET /api/item/product/{id} 同口径，
  #   P27 相似商品卡/P35 搭配卡回链走同一路径；ADR-061 §后果 R1 已登记为「有意的口径并存」。
  'RecommendSpuVO\.java:19:private Long spuId;'
  # ShopDecorationVO.hotSpuIds（merchant，List<Long>）—— 与 ShopDecorationReq 成对；
  #   该字段是「spu.id」寻址且**展示端过滤失效/非在售**（ADR-048 决策 4 明示内部外键保持 BIGINT），
  #   merchant 域不持有也不解析 SPU 业务键（跨域校验留 Phase 2），收口需改 DB 列形态 + 新端点 → 不本批。
  'ShopDecorationVO\.java:29:private List<Long> hotSpuIds;'
  # ShopDecorationReq.hotSpuIds（merchant，List<Long>）—— 同上（写侧与读侧同型同义，不得半侧收口）。
  'ShopDecorationReq\.java:33:private List<Long> hotSpuIds;'
)
WHITELIST_RE="$(printf '%s\n' "${WHITELIST[@]}" | sed '/^$/d' | paste -sd'|' -)"

# 检测结果 → 「文件名:行:字段名」条目列表（逐条精确标识违规项，供白名单比对与陈旧检测）
# 注意：本仓 grep 3.0 的 ERE 在「字符类 + 可选空白 + 字符类」组合下存在漏配（实测），
#       故此处用最简单的 `[^/]+\.java:[0-9]+:.*spuId;` 形态逐名提取，避免依赖复杂回溯。
hits4_items_of() {
  local re="$1"
  printf '%s\n' "$hits4" | sed '/^[[:space:]]*$/d' \
    | grep -ioE "[^/]+\.java:[0-9]+:.*${re}[[:space:]]*;" \
    | sed -E 's/:[[:space:]]+/:/; s/[[:space:]]+;/;/' | sort -u || true
}
HIT_ITEMS=$(
  for name in spuId spuIds skuId skuIds; do
    hits4_items_of "$name"
  done | sort -u
)

# 自证：白名单陈旧即失败（防止「字段已收口/改名后白名单继续放行」的静默豁免）
for entry in "${WHITELIST[@]}"; do
  [ -z "$entry" ] && continue
  if ! printf '%s\n' "$HIT_ITEMS" | grep -qE "^${entry}$"; then
    echo "[FAIL] ADR-061：白名单项已陈旧（命中面中已无此字段，请删除该白名单项）：${entry}"
    echo "       本批实测命中项：$(printf '%s' "$HIT_ITEMS" | tr '\n' ' ')"
    fail=1
  fi
done

if [ -n "$WHITELIST_RE" ]; then
  hits4_effective=$(printf '%s\n' "$HIT_ITEMS" | grep -vE -- "$WHITELIST_RE" || true)
else
  hits4_effective="$HIT_ITEMS"
fi
hits4_effective=$(printf '%s\n' "$hits4_effective" | sed '/^[[:space:]]*$/d')

if [ -n "$hits4_effective" ]; then
  echo "[FAIL] ADR-061：以下线格式字段名对应 spu_id/sku_id，但类型不是 String（应 32-hex 业务键 String / List<String>）："
  printf '%s\n' "$hits4_effective"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "[OK] ID 不变量门禁通过（ADR-050/051/061：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令 / 线格式标识类型）"
  echo "     线格式类型检查命中面：${SCANNED_DIRS} 个目录（adapter/response + adapter/request + ia-api/dto；零匹配即失败）"
fi
exit "$fail"
