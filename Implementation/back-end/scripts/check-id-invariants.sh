#!/usr/bin/env bash
# =============================================================================
# ADR-050/051/061/062 + 契约 §1.8④ 不变量门禁（CI 与本地均可运行）
#
# 背景：业务 ID 的存储边界规则（BINARY(16) + IdGenerator + toBytes 参数）与
# 「对外标识/寻址口径」（业务键单形态、路径变量显式、编解码唯一出口）属
# 「文档约定 + 人工评审」型约束，mock 单测无法覆盖。本脚本把最关键的几条
# 固化为机械检查，防止后续会话/多人协作再次漂移（2026-10-10 CR 结论；
# 检查 4/5/6 为 ADR-061 §守卫与 ADR-062 §守卫 的落点）。
#
# 检查 7（2026-10-18「甲b」新增）= 契约 §1.8④「SPU 名称字段线上键一律 spu_name」：
#   补的是既有 6 道闸的真空 —— 它们只查路径 / 错误码 / 查询参数 / 端点漂移与
#   **标识**字段类型，**没有一道看名称字段**，于是「后端改名、前端没跟」
#   （Jackson SNAKE_CASE 忽略未知键 → 静默 null，页面空白不报错）在 CI 上不可见。
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

# ── 承载位别名例外（2026-10-18，批次 B7b；ADR-063 §决策 7 表 #2 / §决策 8）─────────────
# P38 `GET /api/item/spu/by-code/{code}` 的路径变量是**短码**：它是 SPU 的「对外寻址别名」，
# 不是实体标识（契约 §1.9⑧：不得用于业务判断、不得替代 spu_id、不得进任何 VO）。
# 因此变量名**不能**叫 spuId（那会谎称它表达实体身份），正确名字恰是 code。
# 例外被压到最窄，三条同时满足才放行：
#   ① 路径含字面段 /by-code/；② 变量名恰为 code；③ 类型仍必须 String（技术键是数字类型，仍在上面失败）。
BY_CODE_SEGMENT='/by-code/'
carrierAlias5=0

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
      if printf '%s' "$combined" | grep -q "$BY_CODE_SEGMENT" && [ "$varName" = "code" ]; then
        # 承载位别名（短码）：名字必须表达「这不是实体」—— 见上方例外说明
        carrierAlias5=$((carrierAlias5 + 1))
      else
        hits5="${hits5}${f}:${ln} — 路径 \`${combined}\` 的路径变量名 \`${varName}\` 未显式表达实体（应为 spuId/skuId/spuIds/skuIds；承载位别名仅 /by-code/ 段允许 code）
"
      fi
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


# =============================================================================
# 检查 7：SPU 名称字段的线上键必须是 spu_name（契约 §1.8④，2026-10-18 用户裁决「甲」）
#
# ── 为什么需要这一道（本批发现的真空）─────────────────────────────────
# 上一批（甲a）把「凡报文中表示某个 SPU 名字的字段，线上键一律 spu_name」写进了契约，
# 而当时仓库里**没有一道闸看字段级一致性**：检查 1-3 管 UUID/SQL 字面量、检查 4 管
# **标识**字段的类型、检查 5 管路径变量、检查 6 管编码出口；`check-frontend-api-paths.mjs`
# / `check-error-codes.mjs` / `check-query-param-naming.mjs` / `crosscheck-api-paths.mjs`
# 只管**路径 / 错误码 / 查询参数 / 端点漂移**。于是「后端改名、前端没跟」或反之
# （Jackson SNAKE_CASE 忽略未知键 → 字段静默落 null/undefined，页面空白而不报错）
# 在 CI 上是**完全不可见**的 —— 本检查就是补这个真空。
#
# ── 断言 ────────────────────────────────────────────────────────────
# A. 「SPU 载体」判定：命中面内**文件名**或**其中的嵌套类型名**含 `Spu` 的类
#    （SpuVO / RecommendSpuVO / NoteDetailVO 的 SpuSummaryVO / SpuSearchVO /
#     SpuDetailCO / SpuSearchItemCO / SpuVectorUpsert{CO,Req} / CreateSpuReq / UpdateSpuReq …）。
#    非 SPU 实体的 `name`（CategoryVO / CouponVO / PromotionTagVO / RoleVO / CurrencyVO /
#    SearchFacetsVO.brands[] …）不在命中面 —— 契约 §1.8④「不改清单」要求它们**一字不动**，
#    本检查因此不做「凡 name 皆禁」的粗暴规则（那才会制造歧义）。
# B. 载体类内**不得**再出现名字字段 `name` / `productName`（POJO 形态 `private String name;`、
#    record 形态 `String name,` / `String name)`）—— 除非该行以 `spu_name` 显式钉名。
#    只断言「有 spuName」不够：真正的坑是**两个键并存**。
# C. 上一条的载体清单必须**齐全**（10 个报文载体逐个点名）—— 只查「已出现的字段」无法发现
#    「有人把某个载体整段删了/改了名」，故清单本身也要被断言（文件缺失 = 失败）。
# D. 白名单（唯一例外）：`HistoryVO.productName` + `@JsonProperty("product_name")`
#    （契约 §1.9④ 登记的唯一保留旧键）。**白名单项陈旧即失败** —— 若该字段被改名/删注解，
#    本检查直接红，逼人要么恢复例外、要么删掉白名单项，不允许「豁免悄悄失效」。
# E. 白名单之外的任何 `@JsonProperty("product_name")` 一律失败（防「再登记一个旧键」）。
# =============================================================================

# C：必须存在的「SPU 名载体」报文类（逐条点名；缺失即失败，见断言 C）
REQUIRED_SPU_NAME_CARRIERS='SpuVO.java RecommendSpuVO.java NoteDetailVO.java SpuSearchVO.java
SpuVectorUpsertReq.java SpuDetailCO.java SpuSearchItemCO.java SpuVectorUpsertCO.java
CreateSpuReq.java UpdateSpuReq.java'

# D：白名单（唯一例外）—— 文件名 + 字段名 + 钉名注解，三者同时成立才放行
NAME_EXCEPTION_FILE='HistoryVO.java'
NAME_EXCEPTION_FIELD='productName'
NAME_EXCEPTION_ANNOTATION='@JsonProperty("product_name")'

SPU_CARRIER_FILES=()
carrier7=0
name_hits7=""

for d in "${FORMAT_DIRS[@]}"; do
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    base=$(basename "$f")

    is_carrier=0
    case "$base" in
      *Spu*) is_carrier=1 ;;
    esac
    # 嵌套类型名含 Spu（如 NoteDetailVO 内嵌 SpuSummaryVO）—— 该文件整体按载体检查
    if [ "$is_carrier" -eq 0 ] \
      && grep -qE '(^|[[:space:]])(public[[:space:]]+)?(static[[:space:]]+)?(final[[:space:]]+)?(class|record|interface)[[:space:]]+[A-Za-z0-9_]*Spu' "$f" 2>/dev/null; then
      is_carrier=1
    fi
    [ "$is_carrier" -eq 0 ] && continue

    carrier7=$((carrier7 + 1))
    SPU_CARRIER_FILES+=("$f")

    # B：载体内的名字字段（POJO 声明 / record 组件 / 以旧键显式钉名）
    matches=$(grep -nE 'private[[:space:]]+(static[[:space:]]+)?(final[[:space:]]+)?String[[:space:]]+(name|productName)[[:space:]]*;|(^|[^A-Za-z0-9_])String[[:space:]]+(name|productName)[[:space:]]*[,)]|@JsonProperty\("(name|product_name)"\)' "$f" 2>/dev/null \
      | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)' || true)
    # 允许「同一行显式钉名为 spu_name」的写法（等价合规形态）
    if [ -n "$matches" ]; then
      matches=$(printf '%s\n' "$matches" | grep -v 'spu_name' || true)
    fi
    if [ -n "$matches" ]; then
      name_hits7="${name_hits7}${f}: ${matches}"$'\n'
    fi
  done < <(find "$d" -type f -name '*.java' 2>/dev/null)
done

# 自证：一个 SPU 载体都没扫到 → 门禁自身失效（路径/命中面错误），不得据 [OK] 判定合规
if [ "$carrier7" -eq 0 ]; then
  echo "[FAIL] §1.8④：SPU 名称字段检查的命中面为 0 个载体类 —— 门禁自身失效（find 基准/命名基准错误），不得据 [OK] 判定合规。"
  fail=1
fi

# C：逐条核对「必须存在的载体」并断言其声明 spuName
missing7=""
checked7=0
for want in $REQUIRED_SPU_NAME_CARRIERS; do
  found=""
  for f in "${SPU_CARRIER_FILES[@]:-}"; do
    [ -z "$f" ] && continue
    if [ "$(basename "$f")" = "$want" ]; then
      found="$f"
      break
    fi
  done
  if [ -z "$found" ]; then
    missing7="${missing7}  - ${want}：命中面内未找到该载体类（文件被改名/删除？请同步本清单）"$'\n'
    continue
  fi
  checked7=$((checked7 + 1))
  if ! grep -qE '(private[[:space:]]+(static[[:space:]]+)?(final[[:space:]]+)?String[[:space:]]+spuName[[:space:]]*;)|((^|[^A-Za-z0-9_])String[[:space:]]+spuName[[:space:]]*[,)])' "$found" 2>/dev/null; then
    missing7="${missing7}  - ${found}：未声明 spuName 字段（线上键必须渲染为 spu_name）"$'\n'
  fi
done

if [ -n "$missing7" ]; then
  echo "[FAIL] §1.8④：以下「SPU 名载体」缺失或未声明 spuName（契约：凡报文中表示某个 SPU 名字的字段，键名一律 spu_name）："
  printf '%s' "$missing7"
  fail=1
fi

if [ -n "$name_hits7" ]; then
  echo "[FAIL] §1.8④：以下 SPU 载体仍用旧名称键（name / product_name）—— 线上键必须是 spu_name（同一实体只许一个名字；旧键残留会让前端按任一键取值时在另一侧静默 undefined）："
  printf '%s' "$name_hits7"
  fail=1
fi

# D：白名单项必须仍然成立（陈旧即失败）
exception_ok=0
while IFS= read -r f; do
  [ -z "$f" ] && continue
  [ "$(basename "$f")" = "$NAME_EXCEPTION_FILE" ] || continue
  if grep -qE "private[[:space:]]+String[[:space:]]+${NAME_EXCEPTION_FIELD}[[:space:]]*;" "$f" 2>/dev/null \
    && grep -qF "${NAME_EXCEPTION_ANNOTATION}" "$f" 2>/dev/null; then
    exception_ok=1
  fi
done < <(for d in "${FORMAT_DIRS[@]}"; do find "$d" -type f -name '*.java' 2>/dev/null; done)

if [ "$exception_ok" -ne 1 ]; then
  echo "[FAIL] §1.8④：白名单项已陈旧或失实 —— 命中面内已不存在「${NAME_EXCEPTION_FILE} 的 ${NAME_EXCEPTION_FIELD} 以 ${NAME_EXCEPTION_ANNOTATION} 钉名」这一登记例外。"
  echo "       处置：若该例外确实已取消（改为 spu_name），请从本脚本删除该白名单项；若只是改名，请恢复字段与注解。"
  fail=1
fi

# E：白名单之外不得再出现 product_name 钉名（防「再登记一个旧键」）
hits7_pin=""
for d in "${FORMAT_DIRS[@]}"; do
  m=$(grep -rnF '@JsonProperty("product_name")' --include='*.java' "$d" 2>/dev/null \
    | grep -vE ':[0-9]+:[[:space:]]*(\*|//|/\*)' \
    | grep -v "/${NAME_EXCEPTION_FILE}:" || true)
  if [ -n "$m" ]; then
    hits7_pin="${hits7_pin}${m}"$'\n'
  fi
done
if [ -n "$hits7_pin" ]; then
  echo "[FAIL] §1.8④：以下位置把名字字段钉成了旧键 product_name —— 该旧键是**唯一**登记例外（${NAME_EXCEPTION_FILE}），不得扩散："
  printf '%s' "$hits7_pin"
  fail=1
fi


if [ "$fail" -eq 0 ]; then
  echo "[OK] ID 不变量门禁通过（ADR-050/051/061/062 + 契约 §1.8④：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令 / 线格式标识类型 / 外部路径变量口径 / 适配层无解析调用 / SPU 名称键 spu_name）"
  echo "     命中面自证：线格式类型检查 ${SCANNED_DIRS} 个目录 + 路径变量检查 ${SCANNED_CTRL} 个 Controller（其中 SPU/SKU 路径方法内 ${checked5} 个路径变量，承载位别名 ${carrierAlias5} 个）+ 适配层编码出口检查 ${SCANNED_ADAPTERS} 个 adapter 目录 + SPU 名称键检查 ${carrier7} 个载体类（点名核对 ${checked7} 个必检载体，白名单例外 1 条）—— 任一为 0 即失败"
fi
exit "$fail"
