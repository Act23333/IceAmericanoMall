#!/bin/bash
# ============================================================
# 大厂标准: OpenAPI → Postman Collection 一键导出
# 用法: ./test/export-postman.sh [服务名]
#       ./test/export-postman.sh              # 导出全部服务
#       ./test/export-postman.sh user-service # 导出单个服务
# ============================================================
set -e

OUTPUT_DIR="test/postman"
mkdir -p "$OUTPUT_DIR"

# 网关地址 (启动后可用)
GATEWAY="http://localhost:8080"

# 服务列表 (名称:端口)
declare -A SERVICES=(
  ["authorization-service"]="9000"
  ["user-service"]="8081"
  ["trade-service"]="8082"
  ["item-service"]="8083"
  ["cart-service"]="8084"
  ["pay-service"]="8085"
  ["logistics-service"]="8086"
  ["search-service"]="8087"
  ["ai-service"]="8089"
  ["marketing-service"]="8090"
)

export_single() {
  local name="$1" port="$2"
  local url="http://localhost:${port}/v3/api-docs"
  local out="$OUTPUT_DIR/${name}.json"

  echo -n "导出 $name (port $port)... "
  if curl -s -o "$out" -w "%{http_code}" "$url" | grep -q "200"; then
    echo "✅ $(wc -c < "$out" | tr -d ' ') bytes"
  else
    echo "❌ 服务未启动"
    rm -f "$out"
  fi
}

export_gateway_aggregated() {
  # 网关聚合版: 一个文件包含所有服务
  local url="${GATEWAY}/v3/api-docs"
  local out="$OUTPUT_DIR/all-services-gateway.json"

  echo -n "导出网关聚合版... "
  if curl -s -o "$out" -w "%{http_code}" "$url" | grep -q "200"; then
    echo "✅ $(wc -c < "$out" | tr -d ' ') bytes"

    # 转换为 Postman Collection
    echo -n "转换 Postman Collection... "
    npx openapi-to-postmanv2 -s "$out" -o "$OUTPUT_DIR/IceAmericanoMall.postman_collection.json" \
      -p -O folderStrategy=Tags 2>/dev/null && echo "✅" || echo "⚠️ 需安装 openapi-to-postman"
  else
    echo "❌ 网关未启动"
  fi
}

echo "=== IceAmericanoMall API → Postman 导出 ==="
echo ""

# 单服务模式
if [ -n "$1" ]; then
  port="${SERVICES[$1]}"
  if [ -z "$port" ]; then
    echo "未知服务: $1"
    echo "可用: ${!SERVICES[@]}"
    exit 1
  fi
  export_single "$1" "$port"
  exit 0
fi

# 全量导出模式
for name in "${!SERVICES[@]}"; do
  export_single "$name" "${SERVICES[$name]}"
done

echo ""
echo "---"
echo ""

# 网关聚合 + Postman 转换
export_gateway_aggregated

echo ""
echo "=== 导出完成 ==="
echo "文件位置: $OUTPUT_DIR/"
ls -lh "$OUTPUT_DIR/"*.json 2>/dev/null
echo ""
echo "导入 Postman: File → Import → 选择 .json 文件"
echo "自动化测试: npx newman run $OUTPUT_DIR/IceAmericanoMall.postman_collection.json"
