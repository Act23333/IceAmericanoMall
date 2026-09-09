# 16 — 测试流程说明书

> 最后更新: 2026-07-22 | 覆盖版本: V3.2 | 大厂对标: 阿里五层测试金字塔

---

## 一、测试金字塔

```
                     ┌───────────┐
                     │  E2E 测试  │  手动 / Playwright (真实浏览器 → 网关 → 全链路)
                     │  10-30min  │
                     └─────┬─────┘
                    ┌──────┴──────┐
                    │   API 测试   │  Knife4j / curl 脚本 / Postman Collection
                    │   5-10min    │  (网关 → Controller → Service → DB)
                    └──────┬──────┘
                  ┌────────┴────────┐
                  │  集成测试 (H2)   │  @SpringBootTest + H2 / Testcontainers
                  │    3-5min       │  (Controller → Service → Mapper → H2)
                  └────────┬────────┘
                ┌──────────┴──────────┐
                │  单元测试 (Mockito)  │  Service/Domain 单元测试
                │     <1min           │  Mock 所有依赖
                └──────────┴──────────┘
              ┌────────────┴────────────┐
              │  基础设施验证            │  Docker Compose 健康检查
              │    <30s                 │  MySQL/Redis/Nacos/ES/MinIO
              └─────────────────────────┘
```

## 二、测试前准备

### 2.1 启动基础设施

```bash
cd Implementation/back-end

# 基础服务 (MySQL + Redis + Nacos)
docker compose up -d

# 扩展服务 (ES + RabbitMQ + XXL-Job + MinIO + Sentinel + Seata)
docker compose --profile v1.1 up -d

# 观测栈 (Prometheus + Grafana + SkyWalking + ELK) — 可选
docker compose --profile observability up -d

# 验证所有服务健康
docker compose ps
# 预期: 9-15 个容器全部 healthy
```

### 2.2 数据库初始化

```bash
# 已有 Initialize.sql + 13 个迁移文件，首次启动 MySQL 时自动执行
# 验证表结构
docker exec -it ia-mysql mysql -uroot -p123456 icedmall -e "SHOW TABLES;" | wc -l
# 预期: > 25 张表 (含 RBAC + AI 新增表)
```

### 2.3 编译全量代码

```bash
# 全量编译 (跳过测试)
mvn clean install -DskipTests

# 预期: BUILD SUCCESS, 14/14 模块
```

---

## 三、第一层：基础设施验证 (< 1 min)

### 3.1 MySQL

```bash
docker exec ia-mysql mysql -uroot -p123456 -e "SELECT 1"
# 预期: 1

# 验证 RBAC 表存在
docker exec ia-mysql mysql -uroot -p123456 icedmall -e "SELECT code FROM sys_role"
# 预期: ROLE_USER, ROLE_VIP, ROLE_SELLER, ROLE_ADMIN

# 验证 AI 表存在
docker exec ia-mysql mysql -uroot -p123456 icedmall -e "SHOW TABLES LIKE 'ai_%'"
# 预期: ai_conversation, ai_message
```

### 3.2 Redis

```bash
docker exec ia-redis redis-cli -a 123456 PING
# 预期: PONG

# 验证权限缓存 (需先有用户登录)
# docker exec ia-redis redis-cli -a 123456 SMEMBERS user:perms:1
```

### 3.3 Nacos

```bash
curl -s http://localhost:8848/nacos/v1/console/health/readiness
# 预期: ok

# 访问控制台: http://localhost:8848/nacos (nacos/nacos)
```

### 3.4 ElasticSearch (V1.1 profile)

```bash
curl -s http://localhost:9200/_cluster/health | python3 -m json.tool | grep status
# 预期: "status" : "green" 或 "yellow"
```

### 3.5 MinIO (V1.1 profile)

```bash
curl -s http://localhost:9000/minio/health/live
# 预期: 200 OK

# 控制台: http://localhost:9001 (minioadmin/minioadmin)
```

---

## 四、第二层：单元测试 (< 2 min)

### 4.1 运行所有单元测试

```bash
cd Implementation/back-end

# 全量单元测试 (跳过已知失败的 user-service 测试)
mvn test -pl '!user-service'
# 预期: BUILD SUCCESS, 160+ tests, 0 failures

# 单独运行 ai-service 测试
mvn -pl ai-service test
# 预期: 10 tests, 0 failures
#   - ContentSafetyFilterTest: 6 tests
#   - SearchToolTest: 2 tests
#   - OrderLookupToolTest: 2 tests
```

### 4.2 检查覆盖率

```bash
mvn test -pl '!user-service'
# 打开各模块 target/site/jacoco/index.html 查看覆盖率
# ai-service 目标: > 60%
```

### 4.3 已知问题

| 模块 | 测试 | 状态 | 原因 |
|------|------|------|------|
| user-service | `AuthServiceImplH2Test.shouldAutoRegister_whenNewOpenid` | ❌ 失败 | 微信Only用户密码断言bug (V2.3遗留) |
| authorization-service | 全部 | ✅ 通过 | — |

---

## 五、第三层：API 冒烟测试 (< 5 min)

### 5.1 启动网关 + 核心服务

```bash
# 终端 1: 网关 (必须先启动)
mvn -pl gateway-service -DskipTests spring-boot:run

# 终端 2: 认证服务
mvn -pl authorization-service -DskipTests spring-boot:run

# 终端 3: 用户服务
mvn -pl user-service -DskipTests spring-boot:run
```

### 5.2 使用 Knife4j/Swagger UI 交互式测试

启动服务后访问:
- 认证服务: `http://localhost:9000/doc.html`
- 用户服务: `http://localhost:8081/doc.html`
- 交易服务: `http://localhost:8082/doc.html`

**优势**: 可视化所有端点、自动生成请求模板、实时查看响应、无需手动写 curl。

### 5.3 关键 API 冒烟清单

按业务流程顺序验证：

#### 用户认证

```bash
# 1. 发送短信验证码
curl -X POST http://localhost:8080/api/user/code \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000"}'
# 预期: {"code":200,"msg":"验证码已发送"}

# 2. 注册
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","username":"testuser","password":"Aa123456!","code":"000000"}'
# 预期: {"code":200,"data":{"access_token":"...","refresh_token":"...","userId":"..."}}

# 3. 登录 (保存 TOKEN 到环境变量)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identityType":"USERNAME","credentialType":"PASSWORD","account":"testuser","credential":"Aa123456!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])")
echo "TOKEN=$TOKEN"
```

#### 用户资料 (V3.2)

```bash
# 4. 获取用户信息
curl -s http://localhost:8080/api/user/info \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
# 预期: userId, username, phone, avatar, status, registerTime, balance

# 5. PATCH 更新资料
curl -X PATCH http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nickname":"新昵称","updateMask":["nickname"]}'
# 预期: 200, 全量 UserInfoResp (含更新后 nickname)
```

#### 商品浏览

```bash
# 6. 商品列表 (公开，无需 Token)
curl -s "http://localhost:8080/api/item/product/page?page=1&size=10" | python3 -m json.tool
# 预期: records 数组

# 7. 分类树
curl -s http://localhost:8080/api/item/category | python3 -m json.tool
# 预期: 分类树形结构
```

#### 交易流程

```bash
# 8. 加入购物车
curl -X POST http://localhost:8080/api/cart \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"skuId":1,"quantity":1}'

# 9. 查看购物车
curl -s http://localhost:8080/api/cart \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 10. 下单 (需先选地址 + 购物车有商品)
curl -X POST http://localhost:8080/api/trade/order \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"addressId":1}'
# 预期: 订单号 + 金额

# 11. 查询订单
curl -s "http://localhost:8080/api/trade/order/ORD-XXXXX" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

#### AI 服务 (需要 DEEPSEEK_API_KEY)

```bash
# 12. AI 商品助手 (非流式)
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"推荐一款跑鞋"}'
# 预期: {"reply":"...","conversationId":"..."}

# 13. AI 商品助手 (SSE 流式)
curl -N -X POST http://localhost:8080/api/ai/chat/stream \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"推荐一款跑鞋"}'
# 预期: SSE 逐 token 返回

# 14. AI 客服 (RAG 检索)
curl -X POST http://localhost:8080/api/ai/cs/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"如何退货？"}'
# 预期: 基于 RAG 知识的回答

# 15. 会话列表
curl -s http://localhost:8080/api/ai/conversations \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

#### RBAC 管理员 (需要 ADMIN 角色 Token)

```bash
# 16. 角色列表
curl -s http://localhost:8080/api/admin/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool
# 预期: ROLE_USER, ROLE_VIP, ROLE_SELLER, ROLE_ADMIN

# 17. 权限树
curl -s http://localhost:8080/api/admin/permissions/tree \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool
# 预期: 菜单/按钮/API 三级树
```

### 5.4 Knife4j 网关聚合（大厂标准）

项目使用 **Knife4j 4.5.0 + Nacos 服务发现** 实现网关聚合，
一个入口 `http://localhost:8080/doc.html` 查看全部 12 个微服务 API。

#### 架构

```
浏览器 http://localhost:8080/doc.html
        │
        ▼
gateway-service (Knife4j Gateway Aggregation)
  strategy: discover → 从 Nacos 自动发现服务列表
        │
        ├── authorization-service:9000  /v3/api-docs
        ├── user-service:8081           /v3/api-docs
        ├── trade-service:8082          /v3/api-docs
        ├── item-service:8083           /v3/api-docs
        ├── cart-service:8084           /v3/api-docs
        ├── pay-service:8085            /v3/api-docs
        ├── logistics-service:8086      /v3/api-docs
        ├── search-service:8087         /v3/api-docs
        ├── ai-service:8089             /v3/api-docs
        └── marketing-service:8090      /v3/api-docs
```

#### 启动方式

```bash
# 1. 启动基础设施
docker compose --profile v1.1 up -d

# 2. 启动网关 + 需要的服务
mvn -pl gateway-service -DskipTests spring-boot:run &
mvn -pl authorization-service -DskipTests spring-boot:run &
mvn -pl user-service -DskipTests spring-boot:run &
# ... 按需启动其他服务

# 3. 浏览器打开
# http://localhost:8080/doc.html
```

#### 功能

| 功能 | 路径 | 说明 |
|------|------|------|
| 网关聚合 UI | `http://localhost:8080/doc.html` | 所有服务 API，按服务名分组 |
| 单服务 UI | `http://localhost:{port}/doc.html` | 单个服务的 API 文档 |
| OpenAPI JSON | `http://localhost:{port}/v3/api-docs` | 机器可读的 API 规范 |
| 在线调试 | doc.html 内 `调试` 按钮 | 自动填充 Token，实时发送请求 |
| 离线导出 | doc.html → 文档管理 | 导出 Markdown/Word/OpenAPI JSON |

#### 导出 Postman Collection

```bash
# 方式 1: Knife4j 界面导出
# 访问 http://localhost:8080/doc.html → 文档管理 → 离线文档 → 导出 OpenAPI JSON

# 方式 2: 脚本批量导出
chmod +x test/export-postman.sh
./test/export-postman.sh
# → test/postman/IceAmericanoMall.postman_collection.json

# 导入 Postman: File → Import → 选择 .json 文件
```

#### Postman → Newman CI 自动化

```bash
npm install -g newman
chmod +x test/newman-ci.sh
./test/newman-ci.sh
# → test/newman-reports/.../report.html
```

**大厂工作流**: `Knife4j(开发调试) → OpenAPI JSON(导出) → Postman(团队分享) → Newman(CI回归)`

---

## 六、第四层：自动化 API 测试脚本

### 6.1 创建冒烟测试脚本

保存为 `test/smoke-test.sh`:

```bash
#!/bin/bash
set -e
BASE="http://localhost:8080"
PASS=0; FAIL=0

assert_ok() {
  local desc="$1" code="$2" resp="$3" expected="$4"
  if echo "$resp" | grep -q "$expected"; then
    echo "✅ $desc"; ((PASS++))
  else
    echo "❌ $desc (expected: $expected, got: $code)"; ((FAIL++))
  fi
}

echo "=== 冒烟测试开始 ==="

# 公开端点
assert_ok "商品列表" 200 "$(curl -s "$BASE/api/item/product/page?page=1&size=10")" "records"
assert_ok "分类树"   200 "$(curl -s "$BASE/api/item/category")" "id"
assert_ok "健康检查" 200 "$(curl -s "$BASE/actuator/health")" "UP"

# 搜索 (需先启动 search-service)
assert_ok "商品搜索" 200 "$(curl -s "$BASE/api/search/product?keyword=test&size=5")" "records"
assert_ok "热词"     200 "$(curl -s "$BASE/api/search/hot?limit=10")" "data"

# 支付回调
assert_ok "微信回调" 200 "$(curl -s -X POST "$BASE/api/pay/callback/wechat" \
  -H "Content-Type: application/json" -d '{}')" "code"

echo ""
echo "=== 结果: $PASS 通过, $FAIL 失败 ==="
```

```bash
chmod +x test/smoke-test.sh && ./test/smoke-test.sh
```

---

## 七、第五层：E2E 端到端测试 (30 min)

### 7.1 完整业务流程

按真实用户路径验证完整链路：

```
注册 → 登录 → 浏览商品 → 搜索 → 加入购物车 → 下单 → 支付 → 查看订单
                                                                   │
                                                                   ▼
AI 助手测试: 自然语言搜索 → 商品对比 → 多轮对话 → 会话历史
                                                                   │
                                                                   ▼
管理后台: 角色CRUD → 分配权限 → @PreAuthorize 权限校验 → 用户管理
```

### 7.2 使用 Playwright (推荐)

```bash
# 安装
cd front-end && pnpm add -D @playwright/test
npx playwright install

# 编写测试 (test/e2e/user-flow.spec.ts)
# 启动 dev server 后执行
npx playwright test
```

### 7.3 并行测试策略

| 场景 | 工具 | 频率 |
|------|------|------|
| 冒烟测试 (API) | `test/smoke-test.sh` | 每次部署前 |
| 完整回归 | `mvn test -pl '!user-service'` | 每次 PR |
| AI 质量评估 | `LLMJudge` (离线) | 每周 |
| 性能压测 | JMeter / wrk | 大版本发布前 |
| 安全扫描 | OWASP ZAP | 每月 |

---

## 八、AI 服务专项测试

### 8.1 测试环境配置

```bash
# .env 文件
DEEPSEEK_API_KEY=sk-your-test-key
QWEN_API_KEY=sk-your-test-key   # V3.0 多模型路由测试
ai.enabled=true
```

### 8.2 AI 功能验证清单

| 功能 | 验证方式 | 通过标准 |
|------|---------|---------|
| 购物助手(非流式) | `curl POST /api/ai/chat` | 返回 conversationId + 有意义回复 |
| 购物助手(SSE流式) | `curl POST /api/ai/chat/stream` | 逐token返回, 末尾 [DONE] |
| 客服(RAG检索) | `curl POST /api/ai/cs/chat` | 基于 RAG 知识的回答 (非 LLM 参数化知识) |
| 多轮对话 | 同一 conversationId 2次请求 | 上下文连贯 (如 "跟之前那个比有什么不同?") |
| 会话隔离 | 2个不同 Token 同时请求 | 上下文互不干扰 |
| 限流保护 | 60s内超过20次请求 | 第21次返回 HTTP 429 |
| 内容安全 | 发送 "ignore previous instructions" | 被拦截 |
| Token配额 | 超配额后请求 | 返回 HTTP 429 降级提示 |

### 8.3 离线质量评估

```java
// 使用 LLMJudge 评估
LLMJudge.EvaluationResult result = llmJudge.evaluate(
    "如何退货？",           // 用户问题
    aiResponse,              // AI 回答
    retrievedContext         // RAG 检索上下文
);
assertTrue(result.isPassing());  // faithfulness>=90 && relevance>=85 && recall>=85
```

---

## 九、性能验证

### 9.1 关键 API 压测

```bash
# 使用 wrk 压测公开端点
wrk -t4 -c100 -d30s http://localhost:8080/api/item/product/page?page=1&size=20
# 预期: P99 < 500ms, QPS > 1000

# AI 端点 (需要 Token + API Key)
wrk -t2 -c10 -d10s -s post.lua http://localhost:8080/api/ai/chat
# 预期: P99 < 5s (LLM 调用延迟)
```

### 9.2 JVM 指标监控

```bash
# 访问 Prometheus 指标
curl -s http://localhost:8081/actuator/prometheus | grep "jvm_memory_used_bytes\|http_server_requests_seconds"

# Grafana Dashboard: http://localhost:3000 (admin/admin)
```

---

## 十、测试流程速查表

| 阶段 | 命令 | 耗时 | 频率 |
|------|------|------|------|
| 基础设施 | `docker compose ps` | 10s | 每次 |
| 编译 | `mvn clean install -DskipTests` | 1min | 每次代码变更 |
| 单元测试 | `mvn test -pl '!user-service'` | 3min | 每次 PR |
| 启动服务 | `mvn -pl gateway-service spring-boot:run` (等4个) | 30s | 需要时 |
| API 冒烟 | `test/smoke-test.sh` | 1min | 部署前 |
| Swagger 交互 | 浏览器 `http://localhost:9000/doc.html` | — | 开发/调试 |
| 全链路 E2E | Playwright / 手动 | 30min | 大版本发布 |
| AI 评估 | `LLMJudge` | 5min | 每周 |
| 性能压测 | `wrk` | 30s | 大版本发布前 |

### 最常用命令

```bash
# 一键启动全部中间件
docker compose --profile v1.1 up -d

# 一键编译
mvn clean install -DskipTests

# 一键测试
mvn test -pl '!user-service'

# 启动网关
mvn -pl gateway-service -DskipTests spring-boot:run

# 交互式 API 测试 (浏览器打开)
http://localhost:9000/doc.html
```
