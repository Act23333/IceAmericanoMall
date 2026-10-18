#!/usr/bin/env bash
# 预检：现在能不能停用「已弃用的权限位」？（执行顺序的最后一步：后删旧位）
#
# 为什么是脚本而不是门禁：这是**某个不可逆步骤的准入检查**，不是稳态不变量。
#   - 稳态不变量（旧位停用后引用必须为 0）放在门禁里，CI 常驻；
#   - 本脚本在"准备停用"时手动跑，**引用未归零就退出非零** —— 它红是它的正常工作状态。
# 把准入检查做成门禁，会让 CI 在"第 5 步还没做"的整个期间常红，人就会开始忽略红色。
#
# 清单来源：scripts/rbac-deprecated-bits.txt（与门禁读同一个文件，避免两处理解不同）。
#
# 用法：scripts/preflight-rbac-deprecated-bits.sh
#   退出码 0 = 可以停用；1 = 仍有引用，先替换注解；2 = 环境/清单异常。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIST="$ROOT/scripts/rbac-deprecated-bits.txt"

if [[ ! -f "$LIST" ]]; then
  echo "[error] 找不到旧位清单: $LIST" >&2
  exit 2
fi

mapfile -t BITS < <(grep -vE '^\s*(#|$)' "$LIST" | tr -d '[:space:]')
if [[ ${#BITS[@]} -eq 0 ]]; then
  echo "[error] 旧位清单为空 —— 要么清单写坏了，要么确实没有旧位（后者请删掉本脚本的调用）" >&2
  exit 2
fi

echo "旧位清单（共 ${#BITS[@]} 个）: ${BITS[*]}"
echo

fail=0
for bit in "${BITS[@]}"; do
  # 只扫**代码里**的注解引用：匹配后把「行首为 * 的 javadoc 行」和「行注释行」滤掉
  # （注释不是数据 —— open-items §5.9）。这一步不能省：首次实现直接 grep，把
  # PermissionService javadoc 里的「用法示例」也算成了引用（22 vs 实际 21），
  # 是同一族事故的第 5 次。
  # 已知局限：只滤「整行注释」，不处理行内 /* ... */ —— 注解本身不会出现在行内块注释里，
  # 代价可接受；若将来出现，改用与门禁同一个剥注释实现（同源原则，open-items §5.11）。
  mapfile -t hits < <(
    grep -rn --include=*.java \
      -E "@PreAuthorize\(\s*\"@ss\.hasPermi\('${bit}'\)\"\s*\)" \
      "$ROOT"/*/src/main/java 2>/dev/null \
      | grep -vE '^[^:]*:[0-9]+:[[:space:]]*\*' \
      | grep -vE '^[^:]*:[0-9]+:[[:space:]]*//' \
      || true)

  count=${#hits[@]}
  # ② **代码内调用**也要扫：`hasPermi("x")` / `hasPermission("x")` / `hasAnyPermi("x", ...)`。
  #    只扫注解会漏掉「不挂注解、在方法体里判权」的写法（AdminPay/AdminMerchant 在被 Q5 改造前
  #    正是这个形态）。当前这类调用应为 0 处，但**必须扫**——不能因为"现在是 0"就不设这道检查，
  #    否则以后有人写了代码内判定，停用旧位时脚本会误报"可以停用"。
  mapfile -t code_hits < <(
    grep -rn --include=*.java \
      -E "(hasPermi|hasPermission|hasAnyPermi|hasAnyPermission)\(\s*\"${bit}\"" \
      "$ROOT"/*/src/main/java 2>/dev/null \
      | grep -vE '^[^:]*:[0-9]+:[[:space:]]*\*' \
      | grep -vE '^[^:]*:[0-9]+:[[:space:]]*//' \
      || true)

  code_count=${#code_hits[@]}
  if [[ $code_count -gt 0 ]]; then
    fail=1
    echo "[BLOCK] $bit：代码内判权调用仍有 $code_count 处，**不允许停用**"
    printf '        %s\n' "${code_hits[@]}"
  fi

  total=$((count + code_count))
  if [[ $total -eq 0 ]]; then
    echo "[OK]   $bit：注解引用 0 处 + 代码内判权 0 处，可以停用"
  else
    fail=1
    echo "[BLOCK] $bit：合计 $total 处引用（注解 $count + 代码内 $code_count），**不允许停用**"
    [[ $count -gt 0 ]] && printf '        %s\n' "${hits[@]}"
  fi
done

echo
if [[ $fail -eq 0 ]]; then
  echo "预检通过：旧位引用已归零，可以执行「后删旧位」（UPDATE sys_permission SET status=0）。"
  echo "提醒：停用后必须 DEL user:perms:* 并打印清除条数（见 rbac-permission-matrix-draft.md §7.3）。"
  exit 0
fi

echo "预检未通过：先把上面的注解替换成新位（先种子 → 再注解 → 后删旧位），再重跑本脚本。" >&2
exit 1
