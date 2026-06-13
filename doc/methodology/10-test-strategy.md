# 10 — 测试策略方法 (Test Strategy)

## 一、目的与产出

**本阶段解决什么问题**：定义项目的测试金字塔、各级测试的覆盖目标、BDD 场景到测试用例的映射规则，以及性能测试基线。让 TDD 执行有章可循，而非「写测试」三个空字。

**输入**：PRD (BDD 场景) + API Specification + Architecture Document + Security Threat Model

**输出**：

- 测试金字塔定义（各级比例与职责）
- BDD → 测试用例映射规则
- 覆盖率目标（行/分支/方法）
- 性能测试基线（TPS、延迟、并发数）
- 安全测试清单

**产物文档类型**：Test Strategy Document (参考 ISTQB 测试策略模板)

---

## 二、方法论步骤

### 步骤 1：定义测试金字塔

标准三层金字塔 + 电商项目特化：

```
       /\
      /E2E\          5%  — 核心业务全链路（下单→支付→发货→收货）
     /------\
    /  API   \        20% — 接口契约测试（每个端点 1 正常 + N 异常）
   /----------\
  /   Unit     \      70% — 单元测试（领域逻辑 + 工具类 + Converter）
 /--------------\
     Security        5%  — 越权/注入/签名（渗透测试，不按比例算）
```

| 层级 | 占比 | 范围 | 框架 | 运行频率 |
|------|------|------|------|---------|
| Unit | 70% | Service 层、Domain 层、Util、Converter | JUnit 5 + Mockito | 每次 push |
| API/Integration | 20% | Controller → Service → Mapper → DB | SpringBootTest + Testcontainers | 每次 PR |
| E2E | 5% | 全链路，含外部系统 Mock | Postman/Cypress | 每次发布前 |
| Security | 不按占比 | 越权、注入、签名伪造 | 手动 + OWASP ZAP | 每次大版本 |

### 步骤 2：BDD 场景 → 测试用例映射

PRD 中的每个 BDD Scenario 至少映射到一个 API 集成测试：

```gherkin
# PRD 中的 BDD 场景
Scenario: 成功注册
  Given 手机号未被注册
  And 短信验证码有效
  When 用户提交注册请求
  Then 返回 JWT Token 且 HTTP 201
```

映射为：

```java
@Test
@DisplayName("注册 — 新手机号 + 有效验证码 → 注册成功返回Token")
void shouldRegister_whenNewPhoneAndValidSmsCode() {
    // Given: 未注册手机号 + Mock短信验证码
    // When: POST /api/auth/register
    // Then: HTTP 201, body.data.accessToken ≠ null
}
```

**映射原则**：
- 1 个 BDD 正常场景 → 1 个 API 集成测试
- 1 个 BDD 备选场景 → 1 个 API 异常测试
- BDD 场景中的 Given 条件 → Mock 或数据准备
- BDD 场景中的 Then 断言 → HTTP 状态码 + Response body 校验

### 步骤 3：覆盖率目标

| 维度 | 最低目标 | 推荐目标 |
|------|---------|---------|
| 行覆盖率 | 70% | 80% |
| 分支覆盖率 | 60% | 70% |
| 方法覆盖率 | 75% | 85% |
| Service 层 | 90% | 95% |
| Controller 层 | 80% | 90% |
| Mapper 层 | 不要求 | — |

**豁免**：Lombok 生成代码、纯 POJO/DTO、配置类、常量类

### 步骤 4：性能测试基线

| 场景 | 并发 | TPS 目标 | P99 延迟 | 失败率 |
|------|------|---------|---------|--------|
| 商品列表查询 | 500 | ≥ 5000 | < 200ms | < 0.1% |
| 商品详情 | 500 | ≥ 3000 | < 150ms | < 0.1% |
| 登录 | 200 | ≥ 1000 | < 300ms | < 0.1% |
| 创建订单 | 200 | ≥ 500 | < 500ms | < 0.1% |
| 支付回调 | 100 | ≥ 800 | < 200ms | < 0.01% |

### 步骤 5：安全测试检查点

| 测试项 | 方法 | 工具 |
|--------|------|------|
| 越权检查 | 替换 Token 中的 userId 后请求他人资源 | 手写 |
| SQL 注入 | 在搜索/排序字段注入 payload | sqlmap / 手写 |
| 签名伪造 | 篡改支付回调参数重放 | 手写 |
| 限流验证 | 60s 内高频请求 /api/auth/send-sms | JMeter |
| 敏感数据暴露 | 检查响应和日志中是否含密码/手机号 | grep + 手写 |

---

## 三、冰美商城实践示例

### BDD 到测试映射（以「创建订单」为例）

来自 PRD 的 BDD 场景：

```
Scenario: 成功创建订单
  Given 用户已登录且购物车有选中商品
  And 默认收货地址已设置
  When 用户提交创建订单请求
  Then 订单状态为「待付款」
  And 购物车中该商品被清除

Scenario: 库存不足时创建订单失败
  Given 用户已登录且购物车有选中商品
  And 某 SKU 库存仅剩 1 件
  When 用户提交购买该 SKU 2 件的订单
  Then 返回「库存不足」错误
  And 未生成任何订单记录
```

对应测试：

```java
@SpringBootTest
class CreateOrderApiTest {

    @Test
    void shouldCreateOrderAndClearCart_whenStockSufficient() { ... }

    @Test
    void shouldReturnStockInsufficient_whenQuantityExceedsStock() { ... }
}
```

### 本项目的覆盖率统计点（JaCoCo 配置建议）

- Service 层（trade-service、pay-service）：核心业务，目标 90%+
- Domain/Util 层：纯逻辑，天然高覆盖，目标 95%+
- Controller 层：薄层转发，目标 80%
- Manager 层：编排逻辑，目标 85%
- Feign 接口：不统计

---

## 四、常见错误与检查清单

### 容易犯的错误

1. **只追求覆盖率数字**：写 `assertTrue(true)` 刷覆盖率 → 要求有意义断言
2. **测试只覆盖正常路径**：没有异常场景 → 每个 BDD 的备选流程必须对应测试
3. **用真实外部依赖测试**：测试真的调微信支付 → 必须 Mock/Stub
4. **测试数据库不隔离**：测试之间互相污染 → @Transactional rollback 或 Testcontainers
5. **性能测试放到最后一刻**：上线前才压测 → 每个 Sprint 有基础压测

### 完成后的自检清单

- [ ] 测试金字塔比例合理（Unit/API/E2E 明确分工）
- [ ] 每个 BDD 场景映射到至少一个测试用例
- [ ] 覆盖率目标有数字（不是「尽量高」）
- [ ] 性能基线有可衡量指标
- [ ] 安全测试有明确检查点
- [ ] 测试数据隔离策略已确定

### 本阶段完成定义 (DoD)

- [ ] 测试策略文档通过评审
- [ ] BDD 场景 → 测试用例的映射表完成
- [ ] JaCoCo/覆盖率工具配置就绪
- [ ] 性能测试脚本（JMeter/Gatling）编写完成

---

## 五、与后续阶段的衔接

- **输出到 Phase 11 (开发执行)**：每个 Task 的「验收标准」对应具体的测试用例
- **输出到 CI/CD**：覆盖率阈值 + 失败策略（覆盖率不达标 → CI 不通过）
- **输出到 review.md**：测试是否覆盖了异常路径 → 是 Code Review 的检查点
