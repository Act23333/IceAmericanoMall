# 06 — 接口设计方法 (API Design — SDD 核心)

## 一、目的与产出

**本阶段解决什么问题**：将功能需求转化为精确的 API 契约，让前端和后端可以并行开发，让测试可以提前编写。

**输入**：PRD（功能列表 + BDD 场景） + Domain Model（实体/聚合） + 行为模型（时序图）

**输出**：
- API 设计约定文档
- 端点目录（按服务列出所有 API）
- Request/Response Schema（精确到字段类型）
- 错误码目录
- BDD 风格 API 契约

**产物文档类型**：API Specification (参考 OpenAPI 3.x)

---

## 二、方法论步骤

### 步骤 1：确定 API 设计约定

| 约定 | 标准 |
|------|------|
| URL 风格 | RESTful：`/{resource}/{id}/{sub-resource}` |
| HTTP 方法 | GET(查)/POST(创)/PUT(全量改)/PATCH(部分改)/DELETE(删) |
| 认证 | Bearer JWT in Authorization header |
| 响应格式 | 统一 `Result<T>`：`{code, data, message}` |
| 分页 | `?page=1&size=20&sort=field,asc` |
| 版本 | MVP 暂不加版本前缀，后续 `/v1/api/...` |

### 步骤 2：从 PRD 功能列表提取端点

每个用户功能 → 至少一个 API 端点：

| PRD 功能 | 端点 |
|----------|------|
| 用户注册 | POST /api/auth/register |
| 查看商品列表 | GET /api/item/product/page |
| 创建订单 | POST /api/trade/order |
| 确认收货 | POST /api/trade/order/{orderNo}/confirm |

### 步骤 3：定义 Request/Response Schema

精确到字段级别：

```json
// Request: POST /api/auth/register
{
  "phone": "string, required, pattern: ^1[3-9]\\d{9}$",
  "smsCode": "string, required, length: 6",
  "password": "string, required, min: 6, max: 20",
  "requestId": "string, required, UUID — 幂等令牌"
}

// Response 200:
{
  "code": 200,
  "data": {
    "accessToken": "string (JWT)",
    "userId": "string (业务用户ID)"
  }
}
```

### 步骤 4：建立错误码体系

| 错误码 | HTTP状态 | 语义 |
|--------|---------|------|
| 200 | 200 | 成功 |
| 40000 | 400 | 参数校验失败 |
| 40001 | 400 | 业务规则不满足 |
| 40100 | 401 | 未认证 |
| 40300 | 403 | 无权限 |
| 40400 | 404 | 资源不存在 |
| 42900 | 429 | 限流 |
| 50000 | 500 | 系统错误 |

### 步骤 5：编写 BDD API 契约

对于核心 API，用 Gherkin 写验收契约：

```gherkin
Scenario: POST /api/trade/order — 成功创建订单
  Given 请求头 Authorization 包含有效 JWT
  And 请求体包含选中的购物车项 ID 列表和收货地址 ID
  When 发送 POST 请求到 /api/trade/order
  Then 响应 HTTP 200, code=200
  And 响应 data 包含 orderNo（订单号）
  And 响应 data.status 为 1（待付款）
```

---

## 三、冰美商城实践示例

### 端点总量

| 服务 | MVP 端点数 | 说明 |
|------|-----------|------|
| authorization-service | 5 | 登录/注册/刷新/登出/发短信 |
| user-service | 11 | CRUD/地址/签到 |
| item-service | 7 | 类目/商品/规格 |
| cart-service | 6 | CRUD/选中/批量 |
| trade-service | 8 | 订单/取消/确认/商家操作 |
| pay-service | 3 | 发起支付/回调/查询 |
| logistics-service | 1 | 物流查询 |
| search-service | 3 | (V1.1+) |
| **合计** | **44** | |

### 响应格式示例

```json
// 成功
{"code": 200, "data": {...}, "message": "success"}

// 异常（HTTP 状态码由 GlobalExceptionHandler 设置）
{"code": 40100, "data": null, "message": "Token已过期，请重新登录"}
```

详见：[project-docs/05-API-Specification.md](../../project-docs/05-API-Specification.md)

---

## 四、常见错误与检查清单

### 容易犯的错误

1. **URL 用动词**：`/api/getUser` → 应该用 `GET /api/user/{id}`
2. **GET 请求有 Body**：GET 不应有 Request Body
3. **返回数据库 Entity**：密码哈希值、createTime 等内部字段泄露
4. **错误码随意**：同一个「用户不存在」在不同接口返回不同 code
5. **响应格式不统一**：有的接口返回 `{data: ...}` 有的直接返回数组

### 完成后的自检清单

- [ ] 每个 PRD 功能对应至少一个 API 端点
- [ ] URL 不含动词，资源名用复数/不可数名词
- [ ] HTTP 方法语义正确
- [ ] Request/Response 字段有类型、必填/可选、约束标注
- [ ] 错误码不重复，有清晰的语义
- [ ] 核心 API 有 BDD Given-When-Then 契约
- [ ] 认证接口有 Token 生命周期说明
- [ ] 分页接口有 page/size/sort 参数说明

### 本阶段完成定义 (DoD)

- [ ] API 规格文档通过前后端联合评审
- [ ] 所有检查清单项通过
- [ ] 每个端点可追溯到 PRD 中的功能或 Phase 5 的聚合根
- [ ] 产出已同步到 project-docs/05-API-Specification.md

---

## 五、与后续阶段的衔接

- **输出到 Phase 7 (数据建模)**：API Schema → 数据库 VO 字段设计参考
- **输出到 Phase 8 (行为建模)**：API 端点 → 时序图消息（验证 API 设计正确性）
- **输出到 Phase 11 (开发执行)**：API 契约 → TDD 测试用例 → Controller 实现
- **输出到前端**：API Spec → Mock Server → 前端并行开发

**方法论对应**：
- 本阶段是 **SDD (Specification-Driven Development)** 的核心
- 「先写规格，再写代码」——API Spec 就是可执行的规格
