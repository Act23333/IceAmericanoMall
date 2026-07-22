#!/bin/bash
# ============================================================
# 大厂标准: Newman (Postman CLI) 自动化 API 回归测试
# 用于 CI/CD 流水线，每次 PR 自动运行
# 用法: ./test/newman-ci.sh
# ============================================================
set -e

COLLECTION="test/postman/IceAmericanoMall.postman_collection.json"
ENV="test/postman/localhost-env.json"
REPORT_DIR="test/newman-reports/$(date +%Y%m%d_%H%M%S)"

# 检查依赖
if ! command -v newman &> /dev/null; then
  echo "❌ newman 未安装，执行: npm install -g newman"
  exit 1
fi

mkdir -p "$REPORT_DIR"

echo "=== Newman API 回归测试 ==="

newman run "$COLLECTION" \
  -e "$ENV" \
  --reporters cli,htmlextra \
  --reporter-htmlextra-export "$REPORT_DIR/report.html" \
  --delay-request 200 \
  --timeout-request 30000 \
  --bail

echo ""
echo "=== 测试完成 ==="
echo "报告: $REPORT_DIR/report.html"
