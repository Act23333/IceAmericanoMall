# 11 — 测试策略文档

> 来源：`doc/methodology/10-test-strategy.md`（方法论模板）
> 关联：`05-API-Specification.md`（BDD 契约）、`10-review.md`（测试检查点）

---

## 〇、当前实施状态 vs 目标策略

> ⚠️ 以下测试金字塔为 **V1.1 目标策略**。当前 MVP 阶段的实际覆盖与目标差距较大。

| 测试层级 | 目标占比 | 当前实际 | 状态 |
|---------|---------|---------|------|
| Unit (Entity/Enum/DTO) | 70% | **77 tests** (7/12 模块) | ✅ 部分覆盖 |
| Unit (Mockito 业务逻辑) | 70% | **~15 tests** (logistics/trade/cart/pay) | 🟡 起步 |
| API / Integration | 20% | **0 tests** | ❌ 未开始 |
| Contract (Pact) | — | **0 tests** | ❌ 未开始 |
| E2E | 5% | **0 tests** | ❌ 未开始 |
| Performance (JMeter) | — | **0 tests** | ❌ 未开始 |
| JaCoCo 覆盖率 | — | **未配置** | ❌ 未开始 |

**已覆盖模块**: ia-common (8), user-service (9), cart-service (12), pay-service (14), trade-service (16), item-service (5), logistics-service (13)  
**零测试模块**: authorization-service, gate-service, ia-api, search-service, database

**已知限制**: MyBatis-Plus `ServiceImpl` 的 `lambdaQuery()`/`lambdaUpdate()` 链无法被 Mockito mock（`currentModelClass()` 从 Mapper 接口读取泛型类型信息，在 Mockito 代理中丢失）。这些方法需要 `@SpringBootTest` + H2 或 Testcontainers 集成测试。

---

## 一、测试金字塔（V1.1 目标）

```
       /\
      /E2E\          5%   — 核心全链路（下单→支付→发货→收货）
     /------\
    /  API   \        20%  — 接口契约测试（每端点 1 正常 + N 异常）
   /----------\
  /   Unit     \      70%  — 单元测试（Service/Domain/Util/Converter）
 /--------------\
     Security         不按占比 — 越权/注入/签名伪造
```

| 层级 | 占比 | 范围 | 框架 | 运行频率 |
|------|------|------|------|---------|
| Unit | 70% | Service、Domain、Util、Converter | JUnit 5 + Mockito | 每次 push |
| API / Integration | 20% | Controller → Service → Mapper → DB | SpringBootTest + Testcontainers | 每次 PR |
| Contract | — | Feign 接口契约 | Pact | 每次 PR |
| E2E | 5% | 全链路，外部系统 Mock | Postman / Newman | 每次发布前 |
| Security | — | 越权、注入、签名伪造 | 手动 + OWASP ZAP | 每次大版本 |

---

## 二、BDD → 测试用例映射规则

PRD 中每个 BDD Scenario 映射到至少一个集成测试：

| BDD 场景 | 测试类型 | 最少测试数 |
|----------|---------|-----------|
| 正常场景（Happy Path） | API 集成测试 | 1 |
| 备选场景（Alternative） | API 集成测试 | 每个备选 1 个 |
| 异常场景（Error） | API 集成测试 + 单元测试 | 每个异常 1 个 |

**命名约定**：`should_[预期行为]_when_[条件]`

### 示例：注册功能

```java
// BDD 正常场景 → API 测试
@Test
@DisplayName("注册 — 新手机号+有效验证码 → 返回Token")
void shouldReturnToken_whenNewPhoneAndValidCode() { ... }

// BDD 备选场景 → API 测试
@Test
@DisplayName("注册 — 已注册手机号 → 返回错误")
void shouldReturnError_whenPhoneAlreadyRegistered() { ... }

// BDD 备选场景 → API 测试
@Test
@DisplayName("注册 — 验证码错误 → 提示错误，允许重试")
void shouldReturnError_whenWrongSmsCode() { ... }
```

---

## 三、契约测试 (Contract Testing)

微服务间 Feign 接口通过 **Pact** 做 Consumer-Driven Contract Testing：

### 3.1 原则

- **消费者定义契约**：调用方（如 trade-service）定义期望的 Feign 接口行为
- **提供者验证契约**：被调用方（如 user-service）验证自己能满足所有消费者的期望
- **契约即文档**：`.pact` 文件可导出为可读的 API 规范

### 3.2 关键接口契约清单

| 消费者 | 提供者 | Feign 接口 | 关键场景 |
|--------|--------|-----------|---------|
| trade-service | user-service | `GET /internal/user/{id}` | 返回用户基本信息 |
| trade-service | user-service | `GET /internal/user/address/{id}` | 返回收货地址 |
| trade-service | item-service | `GET /internal/sku/{id}` | 返回 SKU 库存/价格 |
| trade-service | item-service | `PUT /internal/sku/{id}/stock` | 锁库存 |
| pay-service | trade-service | `PUT /internal/order/{orderNo}/status` | 更新订单支付状态 |
| gate-service | authorization-service | `POST /api/auth/*` | JWT 签发/验证 |

### 3.3 CI 集成

```
PR 创建 → 消费者 Pact 生成 → 提供者 Pact 验证 → 契约不匹配 → CI 失败
```

契约变更必须双向 Review：消费者提 PR 时附带 `.pact` 变更，提供者确认兼容性。

---

## 四、覆盖率目标

| 维度 | 最低 | 目标 | 豁免 |
|------|------|------|------|
| 行覆盖率（全项目） | 70% | 80% | Lombok 生成、POJO、Config、Constants |
| 分支覆盖率 | 60% | 70% | — |
| Service 层 | 90% | 95% | — |
| Manager 层 | 85% | 90% | — |
| Controller 层 | 80% | 90% | — |
| Domain/Util 层 | 95% | 95%+ | — |
| Mapper 层 | 不要求 | — | MyBatis-Plus 自动生成 |

**执行**：JaCoCo 插件，CI 中覆盖率不达标则构建失败。

---

## 五、性能测试基线

| 场景 | 并发 | TPS 目标 | P99 延迟 | 失败率 |
|------|------|---------|---------|--------|
| 商品列表查询 | 500 | ≥ 5000 | < 200ms | < 0.1% |
| 商品详情 | 500 | ≥ 3000 | < 150ms | < 0.1% |
| 登录 | 200 | ≥ 1000 | < 300ms | < 0.1% |
| 创建订单 | 200 | ≥ 500 | < 500ms | < 0.1% |
| 支付回调 | 100 | ≥ 800 | < 200ms | < 0.01% |

**压测工具**：JMeter（计划中），每个 Sprint 至少跑一次基准测试。

---

## 六、安全测试清单

| 测试项 | 方法 | 优先级 |
|--------|------|--------|
| 横向越权 — 订单 | 替换 JWT 中的 userId 后请求他人订单 | 🔴 必测 |
| 横向越权 — 地址 | 修改 addressId 参数访问他人地址 | 🔴 必测 |
| SQL 注入 | 搜索/排序字段注入 payload | 🔴 必测 |
| 支付回调签名伪造 | 篡改金额/订单号重放回调 | 🔴 必测 |
| 短信接口爆破 | 60s 内高频请求 `/api/auth/send-sms` | 🟡 应测 |
| 日志敏感信息泄露 | grep 日志中的手机号/密码/Token | 🟡 应测 |
| 管理员接口越权 | 普通用户 Token 请求 `/admin/**` | 🔴 必测 |

---

## 七、测试数据管理

- **单元测试**：纯 Mock，不需数据库
- **API 集成测试**：Testcontainers 启动临时 MySQL/Redis，测试结束自动销毁
- **E2E 测试**：独立测试环境，预置标准测试数据集
- **隔离原则**：测试之间不共享可变状态

---

## 八、测试在 CI/CD 中的位置

```
Push → 单元测试 (3min) → 编译 → 契约测试 (2min) → API集成测试 (8min) → PR 创建
                                                                    │
PR Merge ← Code Review ← 覆盖率检查 ← E2E测试 (15min) ←──────────┘
```

覆盖率不达标 → CI 直接失败，禁止合并。
