#!/usr/bin/env bash
# 只读检查：是否存在「比源码更旧」的已编译 class —— 即增量构建留下的陈旧产物。
#
# 为什么要它：本轮实证 mvn install 后 item-service 才暴露已提交的失败，
# 早期 full 绿是**陈旧 target** 给的假绿。full 入口不强制 clean ⇒ 必须有东西盯住这件事。
#
# 用法：bash scripts/check-stale-classes.sh [模块目录...]（默认扫所有含 target/classes 的模块）
# 退出码：0 = 无陈旧产物；1 = 存在（说明本次 full 的绿只在「增量」意义下成立）
set -uo pipefail
BE="$(cd "$(dirname "$0")/.." && pwd)"
MODULES=("$@")
if [ ${#MODULES[@]} -eq 0 ]; then
  mapfile -t MODULES < <(find "$BE" -maxdepth 2 -type d -name classes -path "*/target/*" -printf "%h\n" | sed 's#/target$##' | sort -u)
fi
stale=0
for m in "${MODULES[@]}"; do
  [ -d "$m/target/classes" ] || continue
  newest_src=$(find "$m/src/main/java" -name '*.java' -newer "$m/target/classes" -print -quit 2>/dev/null || true)
  if [ -n "$newest_src" ]; then
    echo "✗ 陈旧产物: $(basename "$m") —— $(basename "$newest_src") 比 target/classes 新"
    stale=1
  fi
done
[ "$stale" -eq 0 ] && echo "✓ 无陈旧 class（本次构建的绿对应当前源码）"
exit "$stale"
