#!/usr/bin/env bash
# 装库后校验 RBAC 种子（§8 第 4 步的产物）—— 只读，不改数据
#
# 为什么需要：Initialize.sql 的种子用 `INSERT ... SELECT ... WHERE code='x'` 按编码取 ID，
# 这种写法的**静默失败形态**是「SELECT 命中 0 行 ⇒ INSERT 插入 0 行且不报错」。
# 也就是说：种子看起来执行成功，而 rbac:admin 谁都没有 —— 管理员改不了 RBAC，且没人知道。
# 所以必须显式校验**绑定存在且唯一**（等价于 affected rows = 1）。
#
# 用法：scripts/verify-rbac-seed.sh
#   环境变量：MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PWD（或 ~/.my.cnf）
#   退出码：0 = 通过；1 = 校验失败；2 = 环境异常

set -uo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-icedamericano_mall}"
MYSQL_USER="${MYSQL_USER:-root}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "[error] 找不到 mysql 客户端" >&2
  exit 2
fi

q() { mysql -N -B -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DB" -e "$1" 2>/dev/null; }
qq() { mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DB" --table -e "$1"; }

if ! q "SELECT 1" >/dev/null; then
  echo "[error] 连不上 MySQL（$MYSQL_USER@$MYSQL_HOST:$MYSQL_PORT/$MYSQL_DB）" >&2
  exit 2
fi

fail=0
check() { # check <描述> <期望> <实际>
  if [[ "$2" == "$3" ]]; then
    echo "[OK]   $1 = $3"
  else
    echo "[FAIL] $1：期望 $2，实际 $3"; fail=1
  fi
}

echo "=== 1. rbac:admin 必须恰好绑定到 ROLE_ADMIN（INSERT ... SELECT 未命中会静默插 0 行）==="
rbac_id=$(q "SELECT id FROM sys_permission WHERE code='rbac:admin'")
if [[ -z "$rbac_id" ]]; then
  echo "[FAIL] sys_permission 里没有 rbac:admin —— 种子第 20 行没落库"; fail=1
else
  check "rbac:admin 绑定行数（affected rows 语义）" "1" \
        "$(q "SELECT COUNT(*) FROM sys_role_permission WHERE permission_id=$rbac_id")"
  check "rbac:admin 的持有角色" "ROLE_ADMIN" \
        "$(q "SELECT r.code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE rp.permission_id=$rbac_id")"
fi

echo
echo "=== 2. 非 ROLE_ADMIN 角色不得持有 user:admin（替换旧位的前提）==="
check "非管理员持有 user:admin 的角色数" "0" \
      "$(q "SELECT COUNT(*) FROM sys_role_permission rp
             JOIN sys_role r ON r.id=rp.role_id
             JOIN sys_permission p ON p.id=rp.permission_id
            WHERE p.code='user:admin' AND r.code<>'ROLE_ADMIN'")"

echo
echo "=== 3. 拆位新增的位必须全部 status=1（数量下限防"整段没执行"）==="
new_active=$(q "SELECT COUNT(*) FROM sys_permission WHERE id BETWEEN 7 AND 26 AND status=1")
if [[ "${new_active:-0}" -ge 20 ]]; then
  echo "[OK]   id 7–26 中 status=1 的位 = $new_active"
else
  echo "[FAIL] id 7–26 中 status=1 的位 = ${new_active:-0}（应 ≥ 20）—— 种子迁移可能未执行完整"; fail=1
fi

echo
echo "=== 4. 商家侧不得持有任何 admin 档位（Q3 存量不失权 ≠ 新增跨商家能力）==="
check "ROLE_SELLER 持有的 admin 档位数量" "0" \
      "$(q "SELECT COUNT(*) FROM sys_role_permission rp
             JOIN sys_role r ON r.id=rp.role_id
             JOIN sys_permission p ON p.id=rp.permission_id
            WHERE r.code='ROLE_SELLER'
              AND p.code IN ('user:write','order:write','aftersale:write','pay:admin','finance:admin',
                             'invoice:issue','invoice:void','currency:admin','marketing:admin',
                             'merchant:admin','home:admin','kg:admin','rbac:admin')")"

echo
echo "=== 现状快照（人工复核用）==="
qq "SELECT r.code AS role_code, GROUP_CONCAT(p.code ORDER BY p.code) AS perms
      FROM sys_role r
      LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
      LEFT JOIN sys_permission p ON p.id = rp.permission_id
     GROUP BY r.code ORDER BY r.code"

echo
if [[ $fail -eq 0 ]]; then
  echo "RBAC 种子校验通过。"
  exit 0
fi
echo "RBAC 种子校验未通过 —— 不要在此基础上做注解替换（先修种子）。" >&2
exit 1
