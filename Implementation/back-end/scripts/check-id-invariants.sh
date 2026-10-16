#!/usr/bin/env bash
# =============================================================================
# ADR-050/051/061/062 不变量门禁（CI 与本地均可运行）
#
# 背景：业务 ID 的存储边界规则（BINARY(16) + IdGenerator + toBytes 参数）与
# 「对外标识/寻址口径」（业务键单形态、路径变量显式、编解码唯一出口）属
# 「文档约定 + 人工评审」型约束，mock 单测无法覆盖。本脚本把最关键的几条
# 固化为机械检查，防止后续会话/多人协作再次漂移（2026-10-10 CR 结论；
# 检查 4/5/6 为 ADR-061 §守卫与 ADR-062 §守卫 的落点）。
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


# 检查 4：线格式标识字段的【类型】必须为 String（ADR-061 §守卫 + ADR-062 §守卫 2）
# 命中面：adapter/response、adapter/request 与 ia-api dto/ 下的字段名
#        spuId/spuIds/skuId/skuIds（含 hotSpuIds / hotSkuIds 等「SPU/SKU 业务标识 + 前缀」形态）、
#        productId/productIds（**旧词的改名绕过面** —— 改名成 productId 的 Long 字段不得漏网，
#        ADR-062 §守卫 2「防止改名绕过」）、以及业务单号族 ...No（order_no/pay_order_no/team_no…）。
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

# 字段名口径：SPU/SKU 业务标识族（含 hotSpuIds 等前缀形态）+ 旧词 productId（改名绕过面）+ 业务单号族 No
FIELD_SUFFIX='([A-Za-z0-9_]*)([sS]puId|[sS]puIds|[sS]kuId|[sS]kuIds|[pP]roductId|[pP]roductIds|No)'
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

# 白名单：**有意口径并存**字段（非「SPU/SKU 业务标识」），一行一项，逐条给理由。
# 条目口径 =「文件名:行:字段名声明」逐字匹配（与下方 HIT_ITEMS 同形，保证「陈旧项」可判定；
# 字段改名/移位/改型后必须同步更新本项，否则门禁会点名该条陈旧 —— 不允许静默豁免）。
#
# ⚠ 2026-10-17（B5，ADR-062 落码批）：原三项白名单（RecommendSpuVO.spuId、
#   ShopDecorationVO/Req.hotSpuIds）已随本批**收口为业务键**，故**必须删除**——
#   保留它们会立刻触发下方的「白名单陈旧」失败（本脚本刻意的防复发设计）。
WHITELIST=(
  # packageNo（3 处）：包裹<b>序号</b>（order_item.package_no / 分包裹发货的序号），
  #   不是实体标识、无跨环境唯一性语义，按 ADR-037 保持 Integer；字段名以 No 结尾
  #   仅为「编号」语义，不属 ADR-062 §守卫 2 的「数字型 id」。
  'CreateLogisticsCO\.java:24:private Integer packageNo;'
  'LogisticsVO\.java:27:private Integer packageNo;'
  'OrderItemVO\.java:67:private Integer packageNo;'
)
WHITELIST_RE="$(printf '%s\n' "${WHITELIST[@]}" | sed '/^$/d' | paste -sd'|' -)"

# 检测结果 → 「文件名:行:字段名」条目列表（逐条精确标识违规项，供白名单比对与陈旧检测）
# 注意：本仓 grep 3.0 的 ERE 在「字符类 + 可选空白 + 字符类」组合下存在漏配（实测），
#       故此处用最简单的 `[^/]+\.java:[0-9]+:.*<name>;` 形态逐名提取，避免依赖复杂回溯。
hits4_items_of() {
  local re="$1"
  printf '%s\n' "$hits4" | sed '/^[[:space:]]*$/d' \
    | grep -ioE "[^/]+\.java:[0-9]+:.*${re}[[:space:]]*;" \
    | sed -E 's/:[[:space:]]+/:/; s/[[:space:]]+;/;/' | sort -u || true
}
HIT_ITEMS=$(
  for name in spuId spuIds skuId skuIds productId productIds No; do
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
  echo "[FAIL] ADR-061/062：以下线格式字段名对应 spu_id/sku_id/product_id/业务单号，但类型不是 String（应 32-hex 业务键 String / List<String>）："
  printf '%s\n' "$hits4_effective"
  fail=1
fi


# 检查 5：外部 @PathVariable 不得为技术键 / 数字，变量名须显式（ADR-062 §守卫 1）
#
# 规则（命中面 = 路径模板指向 SPU/SKU 实体的方法）：
#   A. 该方法的每个 @PathVariable 声明类型必须是 String —— Long/Integer/int/long 一律失败
#      （技术键不得出现在路径变量上；ADR-062 §决策 3）；
#   B. 变量名必须显式表达实体：spuId / spuIds / skuId / skuIds（`{id}` 在 SPU/SKU 资源段内退役，
#      ADR-062 §决策 2 表 C）；
#   C. 路径模板本身不得残留 `{id}` / `{ids}` 占位符（同一条「退役」要求）。
#
# 命中面判定：类级 @RequestMapping 路径 + 方法级映射路径拼接后
#   ① 含 `/spu`、`/spus`、`/sku`、`/skus` 整段，或
#   ② 含 {spuId|spuIds|skuId|skuIds|productId|productIds} 形态的占位符。
CONTROLLER_FILES=$(find . -type f -path '*/src/main/java/*/adapter/*' -name '*Controller.java' 2>/dev/null | sort)
SCANNED_CTRL=$(printf '%s\n' "$CONTROLLER_FILES" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ')

# 自证：一个 Controller 都没扫到 → 门禁自身失效（路径基准错误），不得静默通过
if [ "$SCANNED_CTRL" -eq 0 ]; then
  echo "[FAIL] ADR-062：路径变量检查的命中面为 0 个 Controller —— 门禁自身失效（find 基准错误），不得据 [OK] 判定合规。"
  fail=1
fi

SPU_SKU_SEGMENT_RE='/(spu|spus|sku|skus)(/|$)'
SPU_SKU_PLACEHOLDER_RE='\{(spuId|spuIds|skuId|skuIds|productId|productIds)\}'
EXPLICIT_NAME_RE='^(spuId|skuId|spuIds|skuIds)$'
LEGACY_PLACEHOLDER_RE='\{(id|ids)\}'

hits5=""
checked5=0
for f in $CONTROLLER_FILES; do
  classPath=$(grep -m1 -oE '@RequestMapping\([[:space:]]*"[^"]*"' "$f" 2>/dev/null | sed -E 's/.*"([^"]*)"$/\1/' || true)

  while IFS= read -r decl; do
    [ -z "$decl" ] && continue
    ln=${decl%%:*}
    body=${decl#*:}

    # 最近的前置方法级映射注解（行号 < 当前声明行）
    mapRec=$(grep -nE '@(Get|Post|Put|Delete|Patch)Mapping' "$f" 2>/dev/null \
      | awk -F: -v L="$ln" '$1+0 < L+0 {rec=$0} END{if (rec != "") print rec}')
    methodPath=""
    if [ -n "$mapRec" ]; then
      methodPath=$(printf '%s' "$mapRec" | grep -oE '"[^"]*"' | head -1 | tr -d '"')
    fi
    combined="${classPath}${methodPath}"

    isFace=0
    if printf '%s' "$combined" | grep -qE "$SPU_SKU_SEGMENT_RE"; then isFace=1; fi
    if printf '%s' "$combined" | grep -qE "$SPU_SKU_PLACEHOLDER_RE"; then isFace=1; fi
    [ "$isFace" -eq 0 ] && continue

    checked5=$((checked5 + 1))

    # 解析 @PathVariable 的声明：剥掉注解自身参数与其余注解，取出「类型 变量名」
    rest=$(printf '%s' "$body" | sed -E 's/.*@PathVariable//')
    rest=$(printf '%s' "$rest" | sed -E 's/^[[:space:]]*\([^)]*\)//')
    rest=$(printf '%s' "$rest" | sed -E 's/^[[:space:]]*(@[A-Za-z0-9_]+(\([^()]*\))?[[:space:]]*)+//')
    varType=$(printf '%s' "$rest" | awk '{print $1}')
    varName=$(printf '%s' "$rest" | awk '{print $2}' | sed -E 's/[),;].*$//')

    if [ "$varType" != "String" ]; then
      hits5="${hits5}${f}:${ln} — 路径 \`${combined}\` 指向 SPU/SKU 实体，但 @PathVariable 类型为 \`${varType} ${varName}\`（应为 String 业务键 32-hex）
"
    fi
    if ! printf '%s' "$varName" | grep -qE "$EXPLICIT_NAME_RE"; then
      hits5="${hits5}${f}:${ln} — 路径 \`${combined}\` 的路径变量名 \`${varName}\` 未显式表达实体（应为 spuId/skuId/spuIds/skuIds）
"
    fi
    if printf '%s' "$combined" | grep -qE "$LEGACY_PLACEHOLDER_RE"; then
      hits5="${hits5}${f}:${ln} — 路径 \`${combined}\` 仍含退役占位符 {id}/{ids}（SPU/SKU 资源段内一律显式命名）
"
    fi
  done < <(grep -nE '@PathVariable' "$f" 2>/dev/null || true)
done

if [ -n "$hits5" ]; then
  echo "[FAIL] ADR-062：外部 @PathVariable 口径违规（技术键禁止出现在路径变量；变量名须显式）："
  printf '%s' "$hits5"
  fail=1
fi


# 检查 6：业务键 ↔ 技术键的解析只许在服务内（ADR-062 §守卫 4）
#
# 断言：适配层（`adapter/` 包）不得调用 IdGenerator 的**解析/编码字节**方法
#   （toBytes / toBytesOrNull / toHexString / parseWire）—— 解析只能出现在
#   infrastructure/persistence（同库、走唯一索引）或领域/应用层；
#   适配层入参唯一允许的调用是形态校验 `IdGenerator.requireWire(...)`（返回 String，不是 byte[]）。
# 依据：ADR-062 §决策 3「技术键只许活在服务内部的持久化与领域层」+ §守卫 4。
ADAPTER_DIRS=()
while IFS= read -r d; do
  [ -n "$d" ] && ADAPTER_DIRS+=("$d")
done < <(find . -type d -path '*/src/main/java/*/adapter' 2>/dev/null) || true
ADAPTER_DIRS=($(printf '%s\n' "${ADAPTER_DIRS[@]:-}" | sed '/^$/d' | sort -u))
SCANNED_ADAPTERS=${#ADAPTER_DIRS[@]}

# 自证：命中面为零即门禁自身失效
if [ "$SCANNED_ADAPTERS" -eq 0 ]; then
  echo "[FAIL] ADR-062：适配层编码出口检查的命中面为 0 个 adapter 目录 —— 门禁自身失效，不得据 [OK] 判定合规。"
  fail=1
fi

hits6=""
for d in "${ADAPTER_DIRS[@]}"; do
  matches=$(grep -rnE 'IdGenerator\.(toBytes|toBytesOrNull|toHexString|parseWire)' --include='*.java' "$d" 2>/dev/null \
    | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)' || true)
  if [ -n "$matches" ]; then
    hits6="${hits6}${matches}
"
  fi
done
if [ -n "$hits6" ]; then
  echo "[FAIL] ADR-062：适配层出现业务键解析调用（byte[] ↔ String 转换只许在服务内的持久化/领域层；适配层仅可用 IdGenerator.requireWire 做形态校验）："
  printf '%s' "$hits6"
  fail=1
fi


if [ "$fail" -eq 0 ]; then
  echo "[OK] ID 不变量门禁通过（ADR-050/051/061/062：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令 / 线格式标识类型 / 外部路径变量口径 / 适配层无解析调用）"
  echo "     命中面自证：线格式类型检查 ${SCANNED_DIRS} 个目录 + 路径变量检查 ${SCANNED_CTRL} 个 Controller（其中 SPU/SKU 路径方法内 ${checked5} 个路径变量）+ 适配层编码出口检查 ${SCANNED_ADAPTERS} 个 adapter 目录（任一为 0 即失败）"
fi
exit "$fail"
