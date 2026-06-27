# 05 — 接口规格文档 (API Specification — SDD)

> 来源：`doc/项目的分析流程/06.接口设计.md`、`doc/项目流程/三层架构命名规范.md`、`doc/项目流程/登录流程详解.md`

---

## 一、API 设计约定

### 1.1 URL 规范

```
/{版本前缀}/{领域资源}/{动作或子资源}
```

- 版本前缀：MVP 阶段暂不加入，通过 URL path 前缀区分服务（`/api/user`, `/api/trade` 等）
- 资源名用复数或不可数名词，小写
- 不使用动词作为资源名（如 `/api/user/register` 中的 `register` 是子资源/动作）

### 1.2 HTTP 方法

| 方法 | 用途 | 幂等 |
|------|------|------|
| GET | 查询资源（单条/列表/分页） | ✅ |
| POST | 创建资源、登录注册等业务动作 | ❌ |
| PUT | 全量更新资源 | ✅ |
| PATCH | 部分更新（如只改状态） | ✅ |
| DELETE | 删除资源 | ✅ |

### 1.3 统一响应格式 `Result<T>`

```json
// 成功
{ "code": 200, "data": { ... }, "message": "success" }

// 业务异常
{ "code": 40001, "data": null, "message": "手机号已注册" }

// 系统异常
{ "code": 50000, "data": null, "message": "系统繁忙，请稍后重试" }
```

- 成功时 HTTP 状态码 200，`code` 字段为 `ErrorCode` 枚举值
- 异常时 HTTP 状态码由 `GlobalExceptionHandler` 根据 `ErrorCode.getHttpStatus()` 设置
- 仅 `/internal/**` 路径不包装 `Result`，异常直接向上抛给调用方

### 1.4 分页请求/响应

```json
// 请求
GET /api/user/page?page=1&size=20&sort=create_time,desc

// 响应
{
  "code": 200,
  "data": {
    "records": [ ... ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

---

## 二、错误码

| 错误码 | HTTP 状态 | 含义 |
|--------|----------|------|
| 200 | 200 | 成功 |
| 40000 | 400 | 参数校验失败 |
| 40001 | 400 | 业务规则不满足（手机号已注册等） |
| 40100 | 401 | 未认证（Token 无效或过期） |
| 40300 | 403 | 无权限（角色不足或越权） |
| 40400 | 404 | 资源不存在 |
| 40900 | 409 | 冲突（重复操作等） |
| 42900 | 429 | 请求过于频繁（被限流） |
| 50000 | 500 | 系统内部错误 |

---

## 三、认证与鉴权

### 3.1 请求头

```
Authorization: Bearer <JWT_TOKEN>
```

### 3.2 Token 生命周期

| Token 类型 | 存储位置 | 有效期 | 用途 |
|------------|---------|--------|------|
| Access Token (JWT) | 前端内存/本地 | 30 分钟 | API 请求认证 |
| Refresh Token | Redis + HttpOnly Cookie | 7 天 | 刷新 Access Token |

### 3.3 认证接口

认证采用 Strategy 模式，通过 `identityType` + `credentialType` 枚举组合分发到对应的 LoginStrategy 实现。

#### POST /api/auth/login — 统一登录入口

```
Request:
{
  "identityType": "PHONE",       // USERNAME | PHONE | EMAIL
  "credentialType": "PASSWORD",  // PASSWORD | SMS_CODE
  "account": "13800138000",      // 账号（手机号/用户名/邮箱）
  "credential": "123456"         // 凭证（密码/验证码）
}

Response (200):
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "xxx",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

**支持的登录方式：**
| identityType | credentialType | 对应 Strategy |
|-------------|---------------|---------------|
| PHONE | PASSWORD | PasswordLoginStrategy |
| PHONE | SMS_CODE | SmsLoginStrategy |
| USERNAME | PASSWORD | PasswordLoginStrategy |
| EMAIL | PASSWORD | EmailPasswordLoginStrategy |

**安全机制：**
- Redisson 分布式锁防并发登录
- Redis Lua 脚本限流（5次失败/60秒锁定）
- 登录失败计数器 + 自动重置

#### POST /api/auth/register — 注册

```
Request:
{
  "phone": "13800138000",
  "smsCode": "123456",
  "password": "123456",
  "requestId": "uuid-幂等令牌"
}

Response (200):
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "userId": "业务用户ID"
  }
}
```

#### POST /api/auth/refresh — 刷新 Token

```
Request (Cookie自动携带 refresh token)

Response (200): 新的 access token
```

#### POST /api/auth/logout — 退出登录

```
Request:
Authorization: Bearer <token>

Response (200): Token 加入黑名单（Redis），Refresh Token 删除
```

#### POST /user/code — 发送短信验证码

```
功能位于 user-service: POST /user/code
限流: @RateLimit(IP, 10次/分钟) + 手机号限流
人机验证: Geetest 极验
```

---

## 四、端点目录（按服务）

> 标记：✅ = MVP 已定义 | 🔵 = V1.1+ | ⚪ = V2.0+

### 4.1 用户服务 (user-service)

#### 用户端（`/user`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/user/info` | 获取当前用户信息 | ✅ |
| PUT | `/user/profile` | 修改个人信息（昵称/头像） | ✅ |
| POST | `/user/code` | 发送短信验证码 | ✅ |
| POST | `/user/sign` | 每日签到 | ✅ |
| GET | `/user/sign/status` | 签到状态（今天是否已签到） | ✅ |

#### 地址管理（`/user/address`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/user/address/list` | 我的地址列表 | ✅ |
| POST | `/user/address/add` | 新增地址 | ✅ |
| GET | `/user/address/{id}` | 获取单个地址 | ✅ |
| PUT | `/user/address/update/{id}` | 修改地址 | ✅ |
| DELETE | `/user/address/delete/{id}` | 删除地址 | ✅ |
| PUT | `/user/address/default/{id}` | 设为默认地址 | ✅ |

#### 商家端（`/api/seller`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| POST | `/api/seller/register` | 用户申请成为商家 | ✅ |
| GET | `/api/seller/shop` | 获取当前商家店铺信息 | ✅ |
| PUT | `/api/seller/shop` | 更新店铺信息（部分更新） | ✅ |

#### 管理员（`/api/admin`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/admin/user/page` | 分页查询用户 | ✅ |
| PUT | `/api/admin/user/{id}/status` | 启用/禁用用户 | ✅ |
| PUT | `/api/admin/user/{id}/role` | 修改用户角色 | ✅ |
| GET | `/api/admin/seller/pending` | 待审核商家列表 | ✅ |
| PUT | `/api/admin/seller/{id}/approve` | 审核通过商家 | ✅ |

#### 内部接口（`/internal/user`，Feign 专用）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| POST | `/internal/user/register` | 注册用户 | ✅ |
| POST | `/internal/user/login/password` | 密码登录校验 | ✅ |
| POST | `/internal/user/login/sms` | 验证码登录校验 | ✅ |
| GET | `/internal/user/{id}` | 获取用户信息 | ✅ |
| GET | `/internal/user/address/{id}` | 获取地址信息 | ✅ |
| GET | `/internal/user/count` | 用户总数统计 | ✅ |

### 4.2 商品服务 (item-service)

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/item/category` | 获取类目树 | ✅ |
| GET | `/api/item/product/page` | 商品分页列表（支持排序/筛选） | ✅ |
| GET | `/api/item/product/{id}` | 商品详情（含 SKU 列表） | ✅ |
| POST | `/api/item/product` | 发布商品（商家） | ✅ |
| PUT | `/api/item/product/{id}` | 编辑商品（商家） | ✅ |
| PUT | `/api/item/product/{id}/status` | 上架/下架（商家/管理员） | ✅ |
| GET | `/api/item/sku/{id}` | SKU 详情 | ✅ |

### 4.3 购物车服务 (cart-service)

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/cart` | 我的购物车列表 | ✅ |
| POST | `/api/cart/item` | 添加商品到购物车 | ✅ |
| PUT | `/api/cart/item` | 修改购物车项数量 | ✅ |
| PATCH | `/api/cart/item/{skuId}/selected` | 切换选中状态 | ✅ |
| DELETE | `/api/cart/item/{skuId}` | 删除购物车项 | ✅ |
| DELETE | `/api/cart/clear` | 清空购物车 | ✅ |

> 购物车项以 `skuId` 为唯一标识，同一 SKU 重复添加时合并数量。

### 4.4 订单服务 (trade-service)

#### 用户端（`/api/trade/order`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| POST | `/api/trade/order` | 创建订单 | ✅ |
| GET | `/api/trade/order/{orderNo}` | 订单详情 | ✅ |
| GET | `/api/trade/order/page` | 我的订单分页（按状态） | ✅ |
| POST | `/api/trade/order/{orderNo}/cancel` | 取消订单 | ✅ |
| POST | `/api/trade/order/{orderNo}/confirm` | 确认收货 | ✅ |

#### 商家端（`/api/trade/seller`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/trade/seller/order/page` | 商家查看订单（按状态） | ✅ |
| GET | `/api/trade/seller/order/{orderNo}` | 商家查看订单详情 | ✅ |
| POST | `/api/trade/seller/order/{orderNo}/ship` | 商家发货 | ✅ |
| GET | `/api/trade/seller/dashboard` | 商家仪表盘（今日订单/待发货/月收入） | ✅ |

#### 管理员（`/api/trade/admin`）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/trade/admin/dashboard` | 管理仪表盘（用户数/订单数/收入） | ✅ |
| GET | `/api/trade/admin/orders/page` | 所有订单分页（按状态筛选） | ✅ |

#### 内部接口（`/internal/trade/order`，Feign 专用）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| PUT | `/internal/trade/order/{orderNo}/status` | 更新订单状态 | ✅ |
| GET | `/internal/trade/order/{orderNo}` | 获取订单摘要 | ✅ |

### 4.5 支付服务 (pay-service)

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| POST | `/api/pay/order/{orderNo}` | 发起支付（返回二维码/支付链接） | ✅ |
| POST | `/api/pay/callback/wechat` | 微信支付回调（第三方调用） | ✅ |
| GET | `/api/pay/order/{orderNo}/status` | 查询支付状态 | ✅ |

### 4.6 搜索服务 (search-service)

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/search/product` | 关键词搜索 | 🔵 |
| GET | `/api/search/hot` | 热门搜索词 | 🔵 |
| GET | `/api/search/history` | 我的搜索历史 | 🔵 |

### 4.7 物流服务 (logistics-service)

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| GET | `/api/logistics/{orderId}` | 查询物流轨迹（含物流状态） | ✅ |

#### 内部接口（`/internal/logistics`，Feign 专用）

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| POST | `/internal/logistics/create` | 创建物流记录（trade-service 调用） | ✅ |
| PUT | `/internal/logistics/{orderId}/status` | 更新物流状态 | ✅ |

---

## 五、接口命名规范对照

| HTTP 动作 | Controller 方法 | Service 方法 | 示例 |
|-----------|----------------|-------------|------|
| GET (单条) | `get` + 资源名 | `get` + 资源名 + `ById` | `getUser`, `getUserById` |
| GET (列表) | `list` / `page` | `page` + 资源名 | `pageUsers`, `pageUsers` |
| POST (创建) | `create` / `register` | `create` / `register` | `register`, `register` |
| PUT (全量更新) | `update` | `update` | `updateUser`, `updateUser` |
| PATCH (部分更新) | `modify` / `patch` | 业务动词 + 名词 | `modifyUser`, `changePassword` |
| DELETE | `delete` / `remove` | `delete` / `remove` | `deleteAddress`, `deleteAddress` |
| 业务操作 | 动词 + 资源 | 动词 + 领域名词 | `confirmReceipt`, `confirmOrder` |
| 幂等操作 | `handle` 前缀 | `handle` + 回调类型 | `handlePayCallback` |

---

## 六、BDD 风格接口契约示例

### 创建订单

```gherkin
Feature: POST /api/trade/order

  Scenario: 从购物车成功创建订单
    Given 请求头 Authorization 包含有效 JWT
    And 请求体包含选中的购物车项 ID 列表和收货地址 ID
    When 发送 POST 请求到 /api/trade/order
    Then 响应 HTTP 200, code=200
    And 响应 data 包含 orderNo（订单号）
    And 响应 data.status 为 1（待付款）
    And 购物车中已下单的商品被清空

  Scenario: 购物车为空时下单
    Given 购物车中没有选中的商品
    When 发送 POST 请求到 /api/trade/order
    Then 响应 HTTP 400, code=40001
    And 响应 message 为 "没有可下单的商品"

  Scenario: 库存不足时下单
    Given 某 SKU 库存为 3 但用户购买数量为 5
    When 发送 POST 请求到 /api/trade/order
    Then 响应 HTTP 400, code=40001
    And 响应 message 包含 "库存不足"

  Scenario: 未登录用户下单
    Given 请求头不包含 Authorization
    When 发送 POST 请求到 /api/trade/order
    Then 响应 HTTP 401, code=40100
```

---

## 六、相关图表

| 图表 | 文件 | 说明 |
|------|------|------|
| 认证流程时序图 | `drawio/IA_Sequence_Diagram.drawio` | 注册 + 登录完整调用链（Client→Controller→Service→DAO→Redis） |
| ER 图 | `drawio/IA_E-R.drawio` | 数据库物理设计（已引用自 04-Data-Model.md） |
