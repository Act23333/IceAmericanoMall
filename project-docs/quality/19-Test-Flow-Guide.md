# 19 — 测试流程说明书

> 最后更新: 2026-10-14（新增 §十一 各平台测试执行流程：Web / 小程序 / 原生 / 桌面端；§一–§十 后端五层金字塔实操未变）| 覆盖版本: V3.2 | 大厂对标: 阿里五层测试金字塔
> 归属域: quality | Owner: doc-engineer | 更新触发条件: 测试实操流程变更时（含新增平台/分发渠道）

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

# 预期: BUILD SUCCESS, 18/18 模块
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

# 支付回调（ADR-029：应答为渠道协议裸 JSON，非平台 Result 信封）
# 空/非法报文 → HTTP 500 + {"code":"FAIL"}（非 200 是渠道重推信号；合法报文才应 200 + SUCCESS）
# 冒烟阶段无法构造微信 v3 合法签名，故按「非法报文」分支断言
WX_STATUS=$(curl -s -o /tmp/wx_callback.json -w "%{http_code}" \
  -X POST "$BASE/api/pay/callback/wechat" -H "Content-Type: application/json" -d '{}')
if [ "$WX_STATUS" = "500" ] && grep -q '"code":"FAIL"' /tmp/wx_callback.json; then
  echo "✅ 微信回调（空报文 → 500/FAIL，触发渠道重推）"; ((PASS++))
else
  echo "❌ 微信回调（预期 500/FAIL，实际 HTTP $WX_STATUS: $(cat /tmp/wx_callback.json)）"; ((FAIL++))
fi

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

## 十一、各平台测试执行流程

> **适用范围**：§一–§十 是**后端**五层金字塔的实操（后端全部平台共享，与前端平台无关）。
> 本节是**前端多平台化后新增的执行流程** —— 每个平台从「本机跑通」到「发布出去」的完整步骤。
>
> **测什么、在哪些设备上测、测到什么程度** 见 [11-Test-Strategy.md](./11-Test-Strategy.md) §九（设备矩阵 / 原生测试要求 / 包体回归）。
> **本节只写「怎么执行」**，不重复策略。
>
> | 平台 | 本节目 | 执行状态 |
> | :-- | :-- | :-- |
> | Web | §11.2 | 🟡 步骤可执行，CI 门禁未接入 |
> | 小程序 | §11.3 | 🔴 未开始 |
> | Android / iOS | §11.4 | 🔴 未开始（无独立工程） |
> | 桌面端 | §11.5 | ⚪ 未排期 |

### 11.1 通用前置

```bash
# 所有前端平台共享：后端必须先可用
cd Implementation/back-end
docker compose up -d                        # MySQL + Redis + Nacos
docker compose --profile v1.1 up -d         # ES + RocketMQ 等
mvn -pl gateway-service -DskipTests spring-boot:run    # 网关 :8080

# 前端工作区依赖（Monorepo，一次装好五平台共享）
cd Implementation/front-end
pnpm install
```

> 环境变量按平台分别配置；**API 基址一律指向网关**，不得绕过网关直连服务
> （契约 §六 网关路由表本身存在缺行，以 `gateway-service/src/main/resources/application.yml` 为准，见 [20 §A5](./20-Doc-Code-Consistency-Audit.md)）。

### 11.2 Web 平台流程

#### 步骤 0 — 静态门禁（< 1 min）

```bash
cd Implementation/front-end

pnpm lint                 # ESLint（turbo lint）
pnpm type-check           # tsc --noEmit（turbo type-check）
pnpm format:check         # Prettier

# 设计 token 与 17 §2.4 的强制比对（R1 死配置 / R2 文档不撒谎 / R3 裸 hex / R4 平台覆盖）
node scripts/check-design-tokens.mjs
```

#### 步骤 1 — 单元/组件测试（< 2 min）

```bash
pnpm test                                    # turbo test
pnpm --filter @icedmall/web test             # 单应用
```

#### 步骤 2 — 本地开发服务器联调

```bash
pnpm dev:web       # 用户端 :3000
pnpm dev:admin     # 平台管理后台 :3001
pnpm dev:seller    # 商家后台 :3002
```

#### 步骤 3 — E2E（Playwright）

```bash
cd Implementation/front-end
pnpm add -D @playwright/test -w
npx playwright install                       # 首次

npx playwright test                          # 默认 desktop 视口
npx playwright test --project="Mobile Chrome" # 移动视口（375px 起）
npx playwright show-report
```

> E2E 在**移动视口**上跑只验证响应式与 Touch，**不等于**移动端 App 验收（见 [11 §9.1.3](./11-Test-Strategy.md)）。

#### 步骤 4 — 性能门禁（Lighthouse CI）

```bash
pnpm build
npx lhci autorun --collect.url=http://localhost:3000/ \
                 --collect.url=http://localhost:3000/marketplace \
                 --collect.url=http://localhost:3000/product/1
```

**目标**：Desktop ≥ 95 / Mobile ≥ 90（品牌主页 ≥ 85）；LCP < 2.5s、INP < 200ms、CLS < 0.1。

#### 步骤 5 — PWA 与包体

```bash
pnpm --filter @icedmall/web build
# 检查产物：首屏 JS / CSS 体积，与基线对比（见 11 §9.3）
# 检查 Service Worker：可安装、离线可用、缓存不含鉴权数据
```

#### 步骤 6 — 发布与灰度

Web **无需审核**，可在任何时候发布与回滚：

| 动作 | 手段 | 生效时间 |
| :-- | :-- | :-- |
| 发布 | 滚动更新 / 蓝绿（镜像） | 分钟级 |
| 灰度 | 按流量比例或用户分桶 | 随时可调 |
| 回滚 | 回退镜像版本 | **分钟级** |
| 强制刷新 | Service Worker 版本失效策略 | 取决于 `skipWaiting` 策略 |

> ⚠️ 上线新版本时必须**同时兼容旧前端**（用户的 PWA 缓存会滞留旧版本）。

### 11.3 小程序平台流程

> 承载：`apps/miniprogram`（Taro 4，微信 + 支付宝）。**宿主维度必须分开验**。

#### 步骤 1 — 构建

```bash
cd Implementation/front-end

pnpm --filter @icedmall/miniprogram build:weapp    # 微信
pnpm --filter @icedmall/miniprogram build:alipay   # 支付宝

# 开发模式（watch）
pnpm --filter @icedmall/miniprogram dev:weapp
```

产物输出到 `apps/miniprogram/dist`。

#### 步骤 2 — 包体检查（**硬门禁**）

| 检查 | 要求 |
| :-- | :-- |
| 主包体积 | **不得超过宿主上限**（超限无法提交，不是告警）—— 上限值查 `15` 平台矩阵 |
| 分包拆分 | 非首屏页面必须在分包中；主包只放启动必需 |
| 总包体积 | 与基线对比，趋势不得持续上升 |

> 主包超限是**阻断提交**项，必须在上限前留出余量。

#### 步骤 3 — 开发者工具预览

1. 用**对应宿主**的开发者工具打开 `apps/miniprogram/dist`：
   - 微信 → 微信开发者工具
   - 支付宝 → 支付宝小程序开发者工具
2. 检查：控制台无报错、页面路由可达、分包加载正常
3. **配置域名白名单**（真机预览的前置条件；本地联调常用「不校验合法域名」临时开关，**不得带进生产配置**）

#### 步骤 4 — 真机预览

- 开发者工具生成预览二维码 → 用**真实账号**扫码（测试账号与生产账号分离）
- **强制真机项**（[11 §9.2.1](./11-Test-Strategy.md)）：登录（`wx.login` / 宿主授权）、**支付（PAY7 JSAPI 拉起）**、分享码、分包加载、弱网
- 宿主分支必须**逐宿主**验：微信与支付宝的登录、支付、分享 API **不是同一套**

#### 步骤 5 — 内测分发

| 渠道 | 用途 | 说明 |
| :-- | :-- | :-- |
| **体验版**（微信）/ 体验版（支付宝） | 内部与指定用户验收 | 绑定体验者白名单，**无需审核**，可随时更新 |
| 灰度发布 | 小比例放量 | 按版本灰度，观察后再全量 |
| 审核 | 提交宿主平台审核 | 有审核周期与合规要求，**发出去收不回** |

#### 步骤 6 — 发布与回滚

| 动作 | 手段 | 生效时间 |
| :-- | :-- | :-- |
| 发布 | 提交审核 → 审核通过 → 发布 | 有审核等待 |
| 灰度 | 按版本灰度放量 | 可逐步放开 |
| **回滚** | **无「回退到上一版本」** —— 只能发布新版本 | **分钟级~天级（受审核影响）** |

> 🔴 **回滚能力弱，因此策略必须是「服务端开关优先」**：功能开关、紧急下线的能力放在后端，
> 客户端只做展示。**不要把只靠客户端才能关闭的功能直接发出去。**

### 11.4 Android / iOS 平台流程

> ⚠️ **现状**：本仓库**无独立原生工程**（Android / iOS）。当前唯一候选承载路径是占位的
> `apps/mobile`（React Native，1 文件 / 0 行）。**是否另立原生工程、是否走 RN，均未决策**
> （见 [18-platforms/android.md](../frontend/18-platforms/android.md) §九、[ios.md](../frontend/18-platforms/ios.md) §九）。
>
> 下列流程是**该平台开工后必须走的分发路径**，在此先行登记，避免开工时才发现签名与审核周期没排期。
> **具体命令与配置待平台技术栈定稿后补全 —— 本文不预写未定技术栈的命令。**

#### 11.4.1 iOS

```
本地构建 (Xcode) → 单元/UI 测试 → 归档 (Archive) → 签名 (证书 + Provisioning Profile)
      → TestFlight 内测 → 外部测试（需 Beta App Review）→ App Store 提交审核 → 分阶段发布
```

| 阶段 | 要点 |
| :-- | :-- |
| **签名前置** | 需 Apple Developer 账号、证书、描述文件；**账号申请与审核有周期，必须提前排期** |
| **TestFlight 内测** | 内部测试者（团队）无需 Beta 审核；外部测试者需 Beta App Review |
| **真机必测** | 冷启动、包体、推送（APNs 需真实设备令牌）、IAP/支付、授权弹窗、后台执行、边缘返回手势 |
| **App Store 审核** | 有审核周期；被拒需修改后重提 |
| **分阶段发布** | 可按比例逐步放量（Phased Release） |
| **回滚** | 🔴 **无法回退已发布版本** —— 只能提交新版本（走审核）。**必须依赖服务端开关做紧急下线** |

#### 11.4.2 Android

```
本地构建 (Gradle) → 单元测试 → 签名 (keystore) → AAB/APK
      → 内部测试轨道 → 封闭测试 → 开放测试 → 生产轨道分阶段放量 → Play Console 审核
```

| 阶段 | 要点 |
| :-- | :-- |
| **keystore 管理** | 签名密钥**丢失即无法更新应用** —— 必须纳入密钥管理（参考 [../ops/开发密钥生成说明.md](../ops/开发密钥生成说明.md) 的口径），且与仓库分离 |
| **Play Console** | 账号、应用创建、内容分级、数据安全表单、目标 API 级别要求 |
| **内测轨道** | 内部测试 / 封闭测试（Alpha）/ 开放测试（Beta），对应不同受众规模 |
| **真机必测** | 冷启动、按 ABI 的包体、FCM 推送、权限（分组授权与拒绝路径）、系统返回手势/预测式返回、折叠屏与多分辨率 |
| **分阶段放量** | 按比例放量，可随时暂停 |
| **回滚** | 🟠 可暂停放量、可发新版本；**已安装用户的旧版本收不回** |

> 多商店分发（华为/小米/OPPO/vivo 等应用市场）各有独立审核与上架要求，若需覆盖须单独排期。

### 11.5 桌面端流程

> ⚪ **现状：未排期**，且是否设立该平台未决策（见 [18-platforms/desktop.md](../frontend/18-platforms/desktop.md) §九）。
>
> 开工后必然涉及的能力（**判定该平台复杂度的依据**）：
> - **安装包签名**：Windows 代码签名证书 / macOS 公证（Notarization，需 Apple Developer 账号）
> - **自动更新通道**：增量更新、强制更新、版本回退策略 —— **这是桌面端与 Web 最大的差异**
> - **多平台安装包**：Windows（exe/msi）、macOS（dmg/pkg），可能还需 Linux
> - **真机必测**：高 DPI/缩放比、多显示器、安装/卸载/覆盖升级、离线可用性
>
> **技术栈未定，不预写构建与打包命令。**

### 11.6 全平台执行流程速查表

| 平台 | 构建 | 本机验收 | 内测分发 | 商店审核 | **回滚能力** | 回滚手段 |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| **Web** | `pnpm build` | E2E + Lighthouse + PWA | 无需（可直接发布） | 无 | 🟢 **分钟级** | 回退镜像 |
| **小程序** | `taro build` | 开发者工具预览 + **真机预览** | **体验版**（白名单，免审核） | 有 | 🟠 差（只能发新版） | 服务端开关 |
| **iOS** | Xcode Archive | 模拟器 + **真机矩阵** | **TestFlight** | 有 | 🔴 **无** | 服务端开关 |
| **Android** | Gradle 构建 + 签名 | 模拟器 + **真机矩阵** | 内测轨道（内部/封闭/开放） | 有 | 🟠 差 | 暂停放量 + 服务端开关 |
| **桌面端** | 待定 | 真机（多 OS） | 待定 | 视分发方式 | 🟡 中 | **自动更新通道可回退** |

> **这张表的用法**：任何平台设计功能时，先看它的「回滚能力」列。
> 🔴/🟠 的平台，**每个新功能都必须自带一个服务端可关闭的开关**，否则事故时无法收敛。

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
