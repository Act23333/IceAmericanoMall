#!/usr/bin/env bash
# 只读检查：每个源文件是否比它**自己的** .class 更新（main 与 test 两类）。
#
# 为什么不用「目录 mtime」：覆盖已存在的 .class **不会**改目录 mtime，重编后目录仍显旧 ⇒ 假阳性；
# 而只 touch 不改内容时 .class 不重写 ⇒ 目录对比既漏报又误报。按「源码 ↔ 自己的 .class」比才准。
#
# 用法：bash scripts/check-stale-classes.sh [模块目录...]
# 退出码：0 = 全部产物新于源码且扫描面正常；1 = 有陈旧/缺失产物 或 扫描面异常
set -uo pipefail
BE="$(cd "$(dirname "$0")/.." && pwd)"
stale=0

discover() {
  find "$BE" -maxdepth 3 -type d -name classes -path "*/target/*" 2>/dev/null \
    | sed -e 's#/target/classes$##' | sort -u
}

MODULES=("$@")
if [ ${#MODULES[@]} -eq 0 ]; then
  mapfile -t MODULES < <(discover)
fi
if [ ${#MODULES[@]} -lt 5 ]; then
  echo "✗ 扫描面异常（仅 ${#MODULES[@]} 个模块）—— 检查器在空转，不能据此判绿"
  exit 1
fi

# 逐个源码比对自己的 .class；命中第一处即报（同类只报一次，避免刷屏）
check_tree() { # $1=src根 $2=class根 $3=标签
  local src_root="$1" cls_root="$2" label="$3" src rel cls
  [ -d "$src_root" ] && [ -d "$cls_root" ] || return 0
  while IFS= read -r src; do
    # 整文件被注释掉 / 无类型声明 ⇒ 不产生 .class，不算陈旧（ia-common 的 IMapper.java 就是这种）
    if ! grep -qE '^[[:space:]]*(public |abstract |final |sealed )*(class|interface|enum|record)[[:space:]]' "$src"; then
      continue
    fi
    rel="${src#"$src_root"/}"
    cls="$cls_root/${rel%.java}.class"
    if [ ! -f "$cls" ]; then
      # 包名与目录不一致 / 生成类（如 MapStruct *Impl）时，按简单名回落找一次
      cls=$(find "$cls_root" -name "$(basename "$cls")" -print -quit 2>/dev/null)
      if [ -z "$cls" ]; then
        echo "✗ 缺少${label}产物: ${src#"$BE"/}"
        return 1
      fi
    fi
    if [ "$src" -nt "$cls" ]; then
      echo "✗ 陈旧${label}产物: ${src#"$BE"/} 比 ${cls#"$BE"/} 新"
      return 1
    fi
  done < <(find "$src_root" -name '*.java' 2>/dev/null)
  return 0
}

count=0
for m in "${MODULES[@]}"; do
  count=$((count + 1))
  check_tree "$m/src/main/java" "$m/target/classes" "主代码" || stale=1
  check_tree "$m/src/test/java" "$m/target/test-classes" "测试" || stale=1
done

if [ "$stale" -eq 0 ]; then
  echo "✓ 无陈旧产物（${count} 个模块；每个源码都不新于自己的 .class）"
fi
exit "$stale"
