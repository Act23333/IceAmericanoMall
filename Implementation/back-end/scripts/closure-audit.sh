#!/usr/bin/env bash
# 跨服务契约闭环（Feign ↔ 提供方端点）—— 解释器探测包装。
# Windows Git Bash 常只有 python/python3 其中之一，缺解释器时必须**红得明白**，
# 不能因为"命令不存在"被当成"没有断点"。
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for PY in python3 python; do
  if command -v "$PY" >/dev/null 2>&1; then
    exec "$PY" "$DIR/closure-audit.py" "$@"
  fi
done
echo "[FAIL] 找不到 python3/python —— 契约闭环门禁无法执行（这不是通过）" >&2
exit 3
