#!/usr/bin/env bash
# 只读结构对账（U4）—— 解释器探测包装（与 closure-audit.sh 同形态）。
# Windows Git Bash 常只有 python/python3 其中之一，缺解释器时必须**红得明白**，
# 不能因为「命令不存在」被当成「没有异常」。
# 注意：本脚本**不进 CI、不阻塞提交**（U4 定性：先当排障工具用）。
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for PY in python3 python; do
  if command -v "$PY" >/dev/null 2>&1; then
    exec "$PY" "$DIR/check-schema-keys.py" "$@"
  fi
done
echo "[FAIL] 找不到 python3/python —— 结构对账无法执行（这不是通过）" >&2
exit 3
