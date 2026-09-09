# 05 — API 规格文档 (API Specification — SDD)

> **最后更新**: 2026-08-02 | **版本**: V5.0 | **端点总数**: 210+ | **公共**: 180+ | **内部**: 30+ | **已实现**: 190+
> 
> **角色**: Frontend (Next.js 15 + React 19) ↔ Backend (Spring Cloud Microservices) 唯一 API 契约。
> 本文件是 SDD (Specification-Driven Development) 的核心产物——前后端并行开发的唯一真相来源。

---

## 一、API 设计约定

### 1.1 URL 规范

```
/{领域资源}/{动作或子资源}
```

- 公共端点通过 Gateway 统一入口 (`/api/*`)
- 内部 Feign 端点走 `/internal/*`，不经 Gateway，不包装 Result
- 资源名使用单数名词（`/api/item/product`）
- 动作使用动词或子资源名（`/api/trade/order/{orderNo}/cancel`）

### 1.2 HTTP 方法语义

| 方法     | 用途          | 幂等  | 示例                                  |
| ------ | ----------- | --- | ----------------------------------- |
| GET    | 查询资源        | ✅   | `GET /api/item/product/{id}`        |
| POST   | 创建资源 / 业务动作 | ❌   | `POST /api/trade/order`             |
| PUT    | 全量更新        | ✅   | `PUT /api/user/address/update/{id}` |
| PATCH  | 部分更新        | ✅   | `PATCH /api/user/profile`           |
| DELETE | 删除资源        | ✅   | `DELETE /api/cart/item/{skuId}`     |

### 1.3 统一响应 `Result<T>`

所有公共端点返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

- 成功: `code=200`, `data` 为业务数据
- 失败: `code≠200`, `msg` 为错误描述，`data` 为 null
- `/internal/**` 路径**不包装** Result，异常直抛给 Feign 调用方

### 1.4 分页

```json
// GET /api/item/product/page?page=1&size=20&sort=create_time,desc

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

- `page`: 页码（从 1 开始）
- `size`: 每页条数（默认 20，最大 100）
- `sort`: 排序字段与方向（`field,asc|desc`）

### 1.5 金额约定

所有金额字段（price/totalAmount/payAmount/balance/discountAmount 等）均为 **Integer（分）**。前端展示需除以 100。

```json
{ "price": 12800 }  // = ¥128.00
```

### 1.6 API 版本策略

| 策略            | 说明                                        |
| ------------- | ----------------------------------------- |
| **URL 路径版本**  | 用于破坏性变更：`/api/v2/item/product`            |
| **Header 版本** | 用于小版本协商：`API-Version: 2026-08`            |
| **字段兼容**      | 新增字段不破坏旧客户端；废弃字段标记 `@Deprecated` 保留 2 个版本 |

### 1.7 日期时间格式

所有日期时间字段使用 ISO 8601 格式：`2026-08-02T10:30:00`。时区：Asia/Shanghai (UTC+8)。

---

## 二、错误码体系

| 错误码   | HTTP | 含义               | 前端处理建议           |
| ----- | ---- | ---------------- | ---------------- |
| 200   | 200  | 成功               | —                |
| 40000 | 400  | 参数校验失败           | 表单字段高亮 + 错误提示    |
| 40001 | 400  | 业务规则不满足          | Toast 提示具体原因     |
| 40100 | 401  | 未认证（Token 过期/缺失） | 跳转登录页 + 刷新 Token |
| 40300 | 403  | 无权限              | 提示"无操作权限"        |
| 40400 | 404  | 资源不存在            | 展示 404 页面或空状态    |
| 40900 | 409  | 冲突（重复操作/库存不足）    | 提示冲突原因 + 刷新页面    |
| 42900 | 429  | 请求过于频繁           | 倒计时后允许重试         |
| 50000 | 500  | 系统内部错误           | 提示"系统繁忙，请稍后再试"   |

### 2.1 业务错误码速查

| 错误码   | 场景       | 提示文案             |
| ----- | -------- | ---------------- |
| 40001 | 手机号已注册   | "该手机号已注册，请直接登录"  |
| 40001 | 验证码错误    | "验证码错误或已过期"      |
| 40001 | 库存不足     | "该商品库存不足，请调整数量"  |
| 40001 | 订单不可取消   | "当前订单状态不允许取消"    |
| 40001 | 优惠券不可用   | "该优惠券不适用于当前订单"   |
| 40001 | 余额不足     | "账户余额不足，请更换支付方式" |
| 40100 | Token 过期 | "登录已过期，请重新登录"    |
| 40300 | 非商家操作    | "仅商家可执行此操作"      |
| 40900 | 重复领取优惠券  | "您已领取过该优惠券"      |
| 40900 | 秒杀已结束    | "秒杀活动已结束"        |

### 2.2 AI 专项错误码

| 错误码                         | HTTP | 说明                    |
| --------------------------- | ---- | --------------------- |
| `AI_SERVICE_UNAVAILABLE`    | 503  | AI 服务未启用或 API Key 未配置 |
| `AI_TIMEOUT`                | 504  | LLM 调用超时（60s）         |
| `AI_CONTENT_FILTERED`       | 400  | 输入或输出被内容安全策略拦截        |
| `AI_RATE_LIMITED`           | 429  | AI 调用频率超限             |
| `AI_CONVERSATION_NOT_FOUND` | 404  | 会话不存在或不属于当前用户         |

---

## 三、认证与安全

### 3.1 Token 体系

| 类型            | 算法        | 存储             | 有效期    | 用途              |
| ------------- | --------- | -------------- | ------ | --------------- |
| Access Token  | JWT RS256 | 前端内存           | 30 min | API 请求鉴权        |
| Refresh Token | UUID      | Redis + Cookie | 7 days | 刷新 Access Token |

### 3.2 请求头

```
Authorization: Bearer <access_token>
```

### 3.3 登录策略

| 策略    | 说明                  | 实现状态                            |
| ----- | ------------------- | ------------------------------- |
| 单地登录  | 新登录踢掉旧 Token        | ✅                               |
| 七天免登录 | Refresh Token 7 天有效 | ✅                               |
| 空闲超时  | 30 分钟无操作需重新验证       | ✅ `GET /api/auth/check-session` |

### 3.4 角色与权限

| 角色          | 标识            | 权限范围                |
| ----------- | ------------- | ------------------- |
| 游客 (GUEST)  | 无 Token       | 商品浏览、搜索、分类查看        |
| 普通用户 (USER) | `ROLE_USER`   | 下单、支付、地址管理、签到、收藏    |
| 商家 (SELLER) | `ROLE_SELLER` | 商品管理、订单处理、发货、店铺设置   |
| 管理员 (ADMIN) | `ROLE_ADMIN`  | 用户管理、商家审核、全量订单、系统配置 |

商家端通过 `@PreAuthorize` 校验资源归属（商家只能操作自己的商品/订单）。管理员端通过方法级权限校验 `@PreAuthorize("@ss.hasPermi('user:admin')")`。

---

## 四、端点目录

> **标记说明**: ✅ 已实现 | 🔵 已实现待完善 | ⚪ 计划中 | 🆕 V5.0 新增
> 
> **返回类型列**: 公共端点写 `Result<T>` 的 inner T，内部端点写原始返回类型。
> 
> **认证列**: 🌐 公开 | 🔒 需 JWT | 🔑 需管理员 | 🏪 需商家 | 🔌 内部 Feign

---

### 4.1 认证服务 (authorization-service)

#### 4.1.1 AuthController — `/api/auth`

| #   | 方法   | 路径                         | 认证  | 请求                                                                                                                                              | 响应                                                                           | 阶段     |
| --- | ---- | -------------------------- | --- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ------ |
| A1  | POST | `/api/auth/login`          | 🌐  | `LoginReq { identityType:"PHONE"\|"USERNAME"\|"EMAIL", credentialType:"PASSWORD"\|"SMS_CODE", account, credential, deviceId?, captchaTicket? }` | `OAuth2TokenResp`                                                            | ✅      |
| A2  | POST | `/api/auth/register`       | 🌐  | `RegisterReq { phone, password, code, username?, deviceId?, requestId? }`                                                                       | `OAuth2TokenResp`                                                            | ✅      |
| A3  | POST | `/api/auth/refresh`        | 🌐  | Query: `refresh_token`                                                                                                                          | `OAuth2TokenResp`                                                            | ✅      |
| A4  | POST | `/api/auth/logout`         | 🌐  | Query: `refresh_token`                                                                                                                          | `Void`                                                                       | ✅      |
| A5  | POST | `/api/auth/reset-password` | 🌐  | `ResetPasswordReq { phone, code, newPassword }`                                                                                                 | `Void`                                                                       | ✅      |
| A6  | POST | `/api/auth/login/wechat`   | 🌐  | `WechatLoginReq { code }`                                                                                                                       | `OAuth2TokenResp`                                                            | ✅      |
| A7  | GET  | `/api/auth/check-session`  | 🔒  | Header: `Authorization: Bearer <token>`                                                                                                         | `SessionStatus { valid(Boolean), ttlSeconds(Long), idleTimeoutMinutes(30) }` | ✅ V4.0 |

> **`OAuth2TokenResp`** (snake_case JSON):
> 
> ```json
> {
>   "access_token": "eyJ...",
>   "token_type": "Bearer",
>   "expires_in": 1800,
>   "refresh_token": "xxx-uuid",
>   "user_id": 1,
>   "username": "buy123"
> }
> ```

#### 4.1.2 JwkSetController — `/oauth2/jwks`

| #   | 方法  | 路径             | 认证  | 请求  | 响应                                         | 阶段  |
| --- | --- | -------------- | --- | --- | ------------------------------------------ | --- |
| A8  | GET | `/oauth2/jwks` | 🌐  | —   | `Map<String,Object>` (JWK Set, 不包装 Result) | ✅   |

---

### 4.2 用户服务 (user-service)

#### 4.2.1 UserController — `/api/user`

| #   | 方法    | 路径                         | 认证  | 请求                                                                                                                  | 响应                               | 阶段     |
| --- | ----- | -------------------------- | --- | ------------------------------------------------------------------------------------------------------------------- | -------------------------------- | ------ |
| U1  | GET   | `/api/user/info`           | 🔒  | — (JWT)                                                                                                             | `UserInfoResp`                   | ✅      |
| U2  | PATCH | `/api/user/profile`        | 🔒  | `UpdateProfileReq { nickname?, avatar?, updateMask?: ["nickname"\|"avatar"] }`                                      | `UserInfoResp` (全量)              | ✅ V3.2 |
| U3  | POST  | `/api/user/profile/avatar` | 🔒  | `multipart/form-data: file` (≤5MB, jpg/png/webp)                                                                    | `String` (头像 URL)                | ✅ V3.2 |
| U4  | GET   | `/api/user/profile/avatar` | 🌐  | Query: `width?`, `height?` (默认200x200)                                                                              | `image/jpeg` (Cache-Control: 1h) | ✅ V3.2 |
| U5  | POST  | `/api/user/code`           | 🌐  | `SmsCodeSendReq { phone, requestId, captchaTicket, captchaRandStr?, lotNumber, captchaOutput, passToken, genTime }` | `Void`                           | ✅      |

**`UserInfoResp`**:

```json
{
  "userId": "usr_abc123",
  "username": "buyer001",
  "phone": "138****8000",
  "avatar": "https://cdn.icedmall.com/avatar/xxx.jpg",
  "status": 1,
  "registerTime": "2026-06-28T10:00:00",
  "balance": 50000,
  "roleType": 0
}
```

#### 4.2.2 AddressController — `/api/user/address`

| #   | 方法     | 路径                               | 认证  | 请求                                                                                                                    | 响应                  | 阶段  |
| --- | ------ | -------------------------------- | --- | --------------------------------------------------------------------------------------------------------------------- | ------------------- | --- |
| U6  | GET    | `/api/user/address/list`         | 🔒  | —                                                                                                                     | `List<AddressResp>` | ✅   |
| U7  | POST   | `/api/user/address/add`          | 🔒  | `AddressReq { receiver, phone, province, city, district, street, detail, defaulted?, label?, longitude?, latitude? }` | `Void`              | ✅   |
| U8  | GET    | `/api/user/address/{id}`         | 🔒  | Path: `id` (Long)                                                                                                     | `AddressResp`       | ✅   |
| U9  | PUT    | `/api/user/address/update/{id}`  | 🔒  | Path: `id` (Long), Body: `AddressReq`                                                                                 | `Void`              | ✅   |
| U10 | PUT    | `/api/user/address/default/{id}` | 🔒  | Path: `id` (Long)                                                                                                     | `Void`              | ✅   |
| U11 | DELETE | `/api/user/address/delete/{id}`  | 🔒  | Path: `id` (Long)                                                                                                     | `Void`              | ✅   |

**`AddressResp`**:

```json
{
  "id": 1,
  "userId": 1001,
  "receiver": "张三",
  "phone": "13800138000",
  "province": "北京市",
  "city": "北京市",
  "district": "朝阳区",
  "street": "望京街道",
  "detail": "SOHO T1 1205",
  "defaulted": true,
  "label": "公司",
  "longitude": 116.48,
  "latitude": 39.99,
  "createTime": "2026-06-01T10:00:00",
  "updateTime": "2026-07-15T14:30:00"
}
```

#### 4.2.3 SignController — `/api/user/sign`

| #   | 方法   | 路径                      | 认证  | 请求  | 响应             | 阶段  |
| --- | ---- | ----------------------- | --- | --- | -------------- | --- |
| U12 | POST | `/api/user/sign`        | 🔒  | —   | `SignResultVO` | ✅   |
| U13 | GET  | `/api/user/sign/status` | 🔒  | —   | `SignResultVO` | ✅   |

**`SignResultVO`**:

```json
{
  "signed": true,
  "earnedPoints": 10,
  "monthCount": 15,
  "continuousDays": 5
}
```

#### 4.2.4 BalanceController — `/api/user/balance`

| #   | 方法   | 路径                           | 认证  | 请求                       | 响应              | 阶段     |
| --- | ---- | ---------------------------- | --- | ------------------------ | --------------- | ------ |
| U14 | GET  | `/api/user/balance`          | 🔒  | —                        | `Integer`（余额，分） | ✅ V2.3 |
| U15 | POST | `/api/user/balance/recharge` | 🔒  | Query: `amount` (int, 分) | `Integer`（最新余额） | ✅ V2.3 |

#### 4.2.5 PointsController — `/api/user/points`

| #   | 方法  | 路径                         | 认证  | 请求                           | 响应                                       | 阶段  |
| --- | --- | -------------------------- | --- | ---------------------------- | ---------------------------------------- | --- |
| U16 | GET | `/api/user/points/balance` | 🔒  | —                            | `Map<String,Object>` `{userId, balance}` | ✅   |
| U17 | GET | `/api/user/points/history` | 🔒  | Query: `page`(1), `size`(20) | `IPage<PointsLogVO>`                     | ✅   |

**`PointsLogVO`**:

```json
{
  "id": 1,
  "userId": 1001,
  "points": 10,
  "type": 1,
  "source": "每日签到",
  "balance": 150,
  "createTime": "2026-08-02T08:30:00"
}
```

#### 4.2.6 FavoriteController — `/api/user/favorite`

| #   | 方法     | 路径                   | 认证  | 请求                           | 响应                  | 阶段  |
| --- | ------ | -------------------- | --- | ---------------------------- | ------------------- | --- |
| U18 | POST   | `/api/user/favorite` | 🔒  | Query: `productId` (Long)    | `Void`              | ✅   |
| U19 | DELETE | `/api/user/favorite` | 🔒  | Query: `productId` (Long)    | `Void`              | ✅   |
| U20 | GET    | `/api/user/favorite` | 🔒  | Query: `page`(1), `size`(20) | `IPage<FavoriteVO>` | ✅   |

**`FavoriteVO`**:

```json
{
  "id": 1,
  "productId": 1001,
  "productName": "手工陶瓷茶杯",
  "mainImage": "https://...",
  "price": 12800,
  "status": 1,
  "createTime": "2026-07-01T10:00:00"
}
```

#### 4.2.7 HistoryController — `/api/user/history`

| #   | 方法     | 路径                  | 认证  | 请求                        | 响应                         | 阶段  |
| --- | ------ | ------------------- | --- | ------------------------- | -------------------------- | --- |
| U21 | POST   | `/api/user/history` | 🔒  | Query: `productId` (Long) | `Void`                     | ✅   |
| U22 | GET    | `/api/user/history` | 🔒  | Query: `size`(20)         | `List<Long>` (product IDs) | ✅   |
| U23 | DELETE | `/api/user/history` | 🔒  | —                         | `Void`（清空全部）               | ✅   |

#### 4.2.8 Account Security (🆕 V5.0 计划)

| #   | 方法   | 路径                                  | 认证  | 请求                             | 响应                 | 阶段     |
| --- | ---- | ----------------------------------- | --- | ------------------------------ | ------------------ | ------ |
| U24 | PUT  | `/api/user/security/password`       | 🔒  | `{ oldPassword, newPassword }` | `Void`             | ⚪ V5.0 |
| U25 | PUT  | `/api/user/security/phone`          | 🔒  | `{ newPhone, code, password }` | `Void`             | ⚪ V5.0 |
| U26 | POST | `/api/user/security/delete-account` | 🔒  | `{ password, code, reason? }`  | `Void`（软删除，30天冷静期） | ⚪ V5.0 |
| U27 | POST | `/api/user/security/reactivate`     | 🌐  | `{ phone, code }`              | `Void`（冷静期内恢复账号）   | ⚪ V5.0 |

#### 4.2.9 Notification Center (🆕 V5.0 计划)

| #   | 方法     | 路径                                     | 认证  | 请求                                                             | 响应                      | 阶段     |
| --- | ------ | -------------------------------------- | --- | -------------------------------------------------------------- | ----------------------- | ------ |
| U28 | GET    | `/api/user/notifications`              | 🔒  | Query: `type?`(SYSTEM\|ORDER\|ACTIVITY), `page`(1), `size`(20) | `IPage<NotificationVO>` | ⚪ V5.0 |
| U29 | GET    | `/api/user/notifications/unread-count` | 🔒  | —                                                              | `Integer`               | ⚪ V5.0 |
| U30 | PUT    | `/api/user/notifications/{id}/read`    | 🔒  | Path: `id` (Long)                                              | `Void`                  | ⚪ V5.0 |
| U31 | PUT    | `/api/user/notifications/read-all`     | 🔒  | —                                                              | `Void`                  | ⚪ V5.0 |
| U32 | DELETE | `/api/user/notifications/{id}`         | 🔒  | Path: `id` (Long)                                              | `Void`                  | ⚪ V5.0 |

**`NotificationVO`**:

```json
{
  "id": 1,
  "type": "ORDER",
  "title": "订单已发货",
  "content": "您的订单 ord-xxx 已由顺丰速运发出",
  "isRead": false,
  "linkUrl": "/orders/ord-xxx",
  "createTime": "2026-08-02T15:30:00"
}
```

---

### 4.3 商家/店铺服务 (user-service + trade-service)

#### 4.3.1 SellerController — `/api/seller`

| #   | 方法   | 路径                     | 认证  | 请求                                                                                                   | 响应         | 阶段  |
| --- | ---- | ---------------------- | --- | ---------------------------------------------------------------------------------------------------- | ---------- | --- |
| S1  | POST | `/api/seller/register` | 🔒  | `SellerRegisterReq { shopName, contactPhone, province?, city?, district?, detailAddress? }`          | `Void`     | ✅   |
| S2  | GET  | `/api/seller/shop`     | 🔒  | —                                                                                                    | `SellerVO` | ✅   |
| S3  | PUT  | `/api/seller/shop`     | 🔒  | `UpdateShopReq { shopName?, shopLogo?, contactPhone?, province?, city?, district?, detailAddress? }` | `Void`     | ✅   |

**`SellerVO`**:

```json
{
  "sellerId": 5,
  "userId": 1001,
  "shopName": "冰美精选陶瓷",
  "shopLogo": "https://cdn.icedmall.com/shop/logo_5.jpg",
  "contactPhone": "13900139000",
  "province": "江西省",
  "city": "景德镇市",
  "district": "珠山区",
  "detailAddress": "陶瓷大道88号",
  "status": 1,
  "createTime": "2026-01-15T10:00:00"
}
```

#### 4.3.2 ShopController — `/api/shop` (公开店铺页)

| #   | 方法  | 路径                              | 认证  | 请求                                                                                   | 响应                                 | 阶段     |
| --- | --- | ------------------------------- | --- | ------------------------------------------------------------------------------------ | ---------------------------------- | ------ |
| S4  | GET | `/api/shop/{sellerId}`          | 🌐  | Path: `sellerId` (Long)                                                              | `Map<String,Object>` (店铺信息+评分+商品数) | ✅ V3.5 |
| S5  | GET | `/api/shop/{sellerId}/products` | 🌐  | Path: `sellerId` (Long), Query: `page`(1), `size`(20), `sort?`(sales\|price\|newest) | `IPage<ProductVO>`                 | ✅ V3.5 |

**`/api/shop/{sellerId}` 响应**:

```json
{
  "sellerId": 5,
  "shopName": "冰美精选陶瓷",
  "shopLogo": "https://...",
  "description": "专注手工陶瓷20年",
  "productCount": 128,
  "followerCount": 3650,
  "rating": 4.8,
  "tags": ["品质保障", "7天退换"],
  "createTime": "2026-01-15T10:00:00"
}
```

#### 4.3.3 StoreFollowController — `/api/shop/follow`

| #   | 方法     | 路径                                   | 认证  | 请求                      | 响应        | 阶段     |
| --- | ------ | ------------------------------------ | --- | ----------------------- | --------- | ------ |
| S6  | POST   | `/api/shop/follow/{sellerId}`        | 🔒  | Path: `sellerId` (Long) | `Boolean` | ✅ V3.5 |
| S7  | DELETE | `/api/shop/follow/{sellerId}`        | 🔒  | Path: `sellerId` (Long) | `Boolean` | ✅ V3.5 |
| S8  | GET    | `/api/shop/follow/{sellerId}/status` | 🌐  | Path: `sellerId` (Long) | `Boolean` | ✅ V3.5 |
| S9  | GET    | `/api/shop/follow/{sellerId}/count`  | 🌐  | Path: `sellerId` (Long) | `Long`    | ✅ V3.5 |

#### 4.3.4 SellerDashboardController — `/api/trade/seller/dashboard`

| #   | 方法  | 路径                                          | 认证  | 请求                | 响应                                                                         | 阶段     |
| --- | --- | ------------------------------------------- | --- | ----------------- | -------------------------------------------------------------------------- | ------ |
| S10 | GET | `/api/trade/seller/dashboard`               | 🏪  | —                 | `Map<String,Object>` `{todayOrderCount, pendingShipCount, monthlyRevenue}` | ✅      |
| S11 | GET | `/api/trade/seller/dashboard/product-stats` | 🏪  | Query: `days`(30) | `List<Map<String,Object>>` (productName/quantity/revenue)                  | ✅ V4.0 |

---

### 4.4 商品服务 (item-service)

#### 4.4.1 ProductController — `/api/item/product`

| #   | 方法   | 路径                              | 认证  | 请求                                                                                                                                              | 响应                 | 阶段  |
| --- | ---- | ------------------------------- | --- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ | --- |
| P1  | GET  | `/api/item/product/page`        | 🌐  | Query: `categoryId?`, `keyword?`, `brand?`, `minPrice?`, `maxPrice?`, `sort?`(sales\|price\|newest), `order?`(asc\|desc), `page`(1), `size`(20) | `IPage<ProductVO>` | ✅   |
| P2  | GET  | `/api/item/product/{id}`        | 🌐  | Path: `id` (Long)                                                                                                                               | `ProductVO`        | ✅   |
| P3  | POST | `/api/item/product`             | 🏪  | `CreateProductReq { categoryId, name, mainImage?, description?, brand?, skus: [{ spec, price, stock, image?, originalPrice? }] }`               | `Void`             | ✅   |
| P4  | PUT  | `/api/item/product/{id}`        | 🏪  | Path: `id` (Long), Body: `UpdateProductReq { name?, mainImage?, description?, brand?, categoryId? }`                                            | `Void`             | ✅   |
| P5  | PUT  | `/api/item/product/{id}/status` | 🏪  | Path: `id` (Long), Query: `status` (Integer: 1上架/0下架)                                                                                           | `Void`             | ✅   |

**`ProductVO`**:

```json
{
  "id": 1,
  "productId": "prod-001",
  "sellerId": 5,
  "shopName": "冰美精选陶瓷",
  "shopLogo": "https://...",
  "categoryId": 1,
  "categoryName": "陶瓷器具",
  "name": "手工陶瓷茶杯",
  "mainImage": "https://cdn.icedmall.com/product/main_001.jpg",
  "images": ["https://...", "https://..."],
  "description": "纯手工制作，高温烧制...",
  "brand": "冰美精选",
  "soldCount": 1280,
  "commentCount": 86,
  "rating": 4.7,
  "status": 1,
  "salesTags": ["热销", "新品"],
  "publishTime": "2026-06-28T10:00:00",
  "skus": [ SkuVO, ... ]
}
```

**`SkuVO`**:

```json
{
  "id": 1,
  "skuId": "sku-001",
  "productId": 1,
  "spec": "天青/标准",
  "specDimensions": { "颜色": "天青", "尺寸": "标准" },
  "price": 12800,
  "originalPrice": 15800,
  "stock": 50,
  "stockType": 1,
  "isHot": true,
  "hotReason": "月销10万+",
  "salesTags": ["热销"],
  "image": "https://cdn.icedmall.com/sku/sku_001.jpg",
  "soldCount": 320,
  "status": 1
}
```

#### 4.4.2 ProductDetailController — `/api/item/product` (V3.5 详情页增强)

| #   | 方法  | 路径                              | 认证  | 请求                | 响应                                    | 阶段     |
| --- | --- | ------------------------------- | --- | ----------------- | ------------------------------------- | ------ |
| P6  | GET | `/api/item/product/{id}/detail` | 🌐  | Path: `id` (Long) | `ProductDetailVO`                     | ✅ V3.5 |
| P7  | GET | `/api/item/product/{id}/skus`   | 🌐  | Path: `id` (Long) | `List<SkuVO>` (含 specDimensions 维度信息) | ✅ V3.5 |

**`ProductDetailVO`** (聚合商品+店铺+SKU+评论摘要+可用的优惠券):

```json
{
  "product": { ProductVO },
  "shop": {
    "sellerId": 5,
    "shopName": "冰美精选陶瓷",
    "shopLogo": "https://...",
    "rating": 4.8,
    "followerCount": 3650,
    "productCount": 128
  },
  "skus": [ SkuVO, ... ],
  "specDimensions": [
    { "name": "颜色", "options": ["天青", "月白", "墨黑"] },
    { "name": "尺寸", "options": ["标准", "加大"] }
  ],
  "reviewSummary": {
    "averageRating": 4.7,
    "totalCount": 86,
    "distribution": { "5": 60, "4": 15, "3": 8, "2": 2, "1": 1 }
  },
  "serviceTags": ["7天退换", "正品保证", "极速退款"],
  "availableCoupons": [
    { "couponId": 10, "name": "满100减20", "discountType": 1, "priceInCents": 2000, "minAmountInCents": 10000 }
  ]
}
```

#### 4.4.3 CategoryController — `/api/item/category`

| #   | 方法     | 路径                        | 认证  | 请求                                                      | 响应                     | 阶段  |
| --- | ------ | ------------------------- | --- | ------------------------------------------------------- | ---------------------- | --- |
| P8  | GET    | `/api/item/category/tree` | 🌐  | —                                                       | `List<CategoryTreeVO>` | ✅   |
| P9  | POST   | `/api/item/category`      | 🔑  | `CategoryEntity { name, parentId?, level, sortOrder? }` | `Void`                 | ✅   |
| P10 | PUT    | `/api/item/category/{id}` | 🔑  | Path: `id` (Long), Body: `CategoryEntity`               | `Void`                 | ✅   |
| P11 | DELETE | `/api/item/category/{id}` | 🔑  | Path: `id` (Long)                                       | `Void`                 | ✅   |

**`CategoryTreeVO`**:

```json
{
  "id": 1,
  "name": "陶瓷器具",
  "level": 1,
  "sortOrder": 1,
  "icon": "https://...",
  "children": [
    { "id": 2, "name": "茶杯", "level": 2, "sortOrder": 1, "children": [] },
    { "id": 3, "name": "餐具", "level": 2, "sortOrder": 2, "children": [] }
  ]
}
```

#### 4.4.4 ReviewController — `/api/item/review`

| #   | 方法   | 路径                                             | 认证  | 请求                                                                                              | 响应                | 阶段        |
| --- | ---- | ---------------------------------------------- | --- | ----------------------------------------------------------------------------------------------- | ----------------- | --------- |
| P12 | POST | `/api/item/review`                             | 🔒  | `ReviewCreateReq { orderId, productId, score(1-5), content?, mediaUrls?: [String] }`            | `ReviewVO`        | ✅         |
| P13 | GET  | `/api/item/review/product/{productId}`         | 🌐  | Path: `productId` (Long), Query: `page`(1), `size`(10), `sort?`(newest\|highest\|lowest)        | `IPage<ReviewVO>` | ✅         |
| P14 | GET  | `/api/item/review/product/{productId}/filter`  | 🌐  | Path: `productId` (Long), Query: `rating?`(1-5), `hasMedia?`(Boolean), `sort?`(newest\|highest) | `IPage<ReviewVO>` | ✅ V3.5    |
| P15 | GET  | `/api/item/review/product/{productId}/summary` | 🌐  | Path: `productId` (Long)                                                                        | `ReviewSummaryVO` | ✅ V3.5    |
| P16 | POST | `/api/item/review/{id}/reply`                  | 🏪  | Path: `id` (Long), Body: `{ content }` (商家回复)                                                   | `Void`            | ✅ V5.0 |
| P17 | POST | `/api/item/review/{id}/append`                 | 🔒  | Path: `id` (Long), Body: `{ content, mediaUrls? }` (追评)                                         | `Void`            | ✅ V5.0 |
| P18 | POST | `/api/item/review/{id}/like`                   | 🔒  | Path: `id` (Long)                                                                               | `Void` (点赞)       | ✅ V5.0 |

**`ReviewVO`**:

```json
{
  "id": 1,
  "userId": 1001,
  "username": "匿名用户",
  "avatar": "https://...",
  "productId": 1,
  "orderNo": "ord-xxx",
  "score": 5,
  "content": "质量很好，做工精细，推荐购买！",
  "mediaUrls": ["https://cdn.icedmall.com/review/img_001.jpg"],
  "tags": ["质量好", "物流快"],
  "likeCount": 12,
  "reply": {
    "content": "感谢您的支持！",
    "replyTime": "2026-07-10T14:00:00"
  },
  "append": {
    "content": "用了一个月，依然很好",
    "appendTime": "2026-08-01T10:00:00"
  },
  "createTime": "2026-07-05T10:30:00"
}
```

**`ReviewSummaryVO`**:

```json
{
  "averageRating": 4.7,
  "totalCount": 86,
  "distribution": { "5": 60, "4": 15, "3": 8, "2": 2, "1": 1 },
  "tags": [
    { "tag": "质量好", "count": 45 },
    { "tag": "物流快", "count": 32 },
    { "tag": "性价比高", "count": 28 }
  ]
}
```

#### 4.4.5 Brand Management (🆕 V5.0 计划)

| #   | 方法     | 路径                     | 认证  | 请求                                                        | 响应               | 阶段     |
| --- | ------ | ---------------------- | --- | --------------------------------------------------------- | ---------------- | ------ |
| P19 | GET    | `/api/item/brand/list` | 🌐  | Query: `keyword?`, `page`(1), `size`(20)                  | `IPage<BrandVO>` | ⚪ V5.0 |
| P20 | POST   | `/api/item/brand`      | 🔑  | `{ name, logo?, description? }`                           | `BrandVO`        | ⚪ V5.0 |
| P21 | PUT    | `/api/item/brand/{id}` | 🔑  | Path: `id` (Long), Body: `{ name?, logo?, description? }` | `Void`           | ⚪ V5.0 |
| P22 | DELETE | `/api/item/brand/{id}` | 🔑  | Path: `id` (Long)                                         | `Void`           | ⚪ V5.0 |

#### 4.4.6 HomeController — `/api/home`

| #   | 方法  | 路径                 | 认证  | 请求  | 响应                   | 阶段  |
| --- | --- | ------------------ | --- | --- | -------------------- | --- |
| P23 | GET | `/api/home/config` | 🌐  | —   | `List<HomeConfigVO>` | ✅   |

**`HomeConfigVO`** (首页装修配置):

```json
{
  "id": 1,
  "type": "BANNER",
  "title": "618大促",
  "imageUrl": "https://...",
  "linkUrl": "/promotion/618",
  "sortOrder": 1,
  "status": 1
}
```

> 管理端首页配置见 §4.12.3 AdminHomeController。

#### 4.4.7 Product Comparison (🆕 V5.0 计划 — 京东标准)

| #   | 方法  | 路径                          | 认证  | 请求                        | 响应                       | 阶段     |
| --- | --- | --------------------------- | --- | ------------------------- | ------------------------ | ------ |
| P24 | GET | `/api/item/product/compare` | 🌐  | Query: `ids` (逗号分隔, 最多4个) | `List<ProductCompareVO>` | ⚪ V5.0 |

#### 4.4.8 Stock Notification (🆕 V5.0 计划)

| #   | 方法     | 路径                                   | 认证  | 请求                   | 响应               | 阶段     |
| --- | ------ | ------------------------------------ | --- | -------------------- | ---------------- | ------ |
| P25 | POST   | `/api/item/sku/{skuId}/stock-notify` | 🔒  | Path: `skuId` (Long) | `Void` (到货后推送通知) | ⚪ V5.0 |
| P26 | DELETE | `/api/item/sku/{skuId}/stock-notify` | 🔒  | Path: `skuId` (Long) | `Void`           | ⚪ V5.0 |

---

### 4.5 购物车服务 (cart-service)

#### 4.5.1 CartController — `/api/cart`

| #   | 方法     | 路径                                        | 认证  | 请求                                                | 响应              | 阶段        |
| --- | ------ | ----------------------------------------- | --- | ------------------------------------------------- | --------------- | --------- |
| C1  | GET    | `/api/cart`                               | 🔒  | —                                                 | `CartVO`        | ✅         |
| C2  | POST   | `/api/cart/item`                          | 🔒  | `CartAddReq { skuId(Long), quantity(1) }`         | `Void`          | ✅         |
| C3  | PUT    | `/api/cart/item`                          | 🔒  | `CartUpdateReq { skuId(Long), quantity }`         | `Void`          | ✅         |
| C4  | DELETE | `/api/cart/item/{skuId}`                  | 🔒  | Path: `skuId` (Long)                              | `Void`          | ✅         |
| C5  | PATCH  | `/api/cart/item/{skuId}/selected`         | 🔒  | Path: `skuId` (Long), Query: `selected` (Boolean) | `Void`          | ✅         |
| C6  | DELETE | `/api/cart/clear`                         | 🔒  | —                                                 | `Void`          | ✅         |
| C7  | GET    | `/api/cart/grouped`                       | 🔒  | —                                                 | `CartGroupedVO` | ✅ V3.5    |
| C8  | PATCH  | `/api/cart/select-all`                    | 🔒  | Query: `selected` (Boolean)                       | `Void`          | 🆕 ⚪ V5.0 |
| C9  | DELETE | `/api/cart/batch`                         | 🔒  | Body: `{ skuIds: [Long] }`                        | `Void` (批量删除)   | 🆕 ⚪ V5.0 |
| C10 | POST   | `/api/cart/item/{skuId}/move-to-favorite` | 🔒  | Path: `skuId` (Long)                              | `Void` (移入收藏夹)  | 🆕 ⚪ V5.0 |

**`CartVO`**:

```json
{
  "items": [ CartItemResp, ... ],
  "totalPrice": 38400,
  "selectedPrice": 25600,
  "allSelected": false
}
```

**`CartItemResp`**:

```json
{
  "cartId": 1,
  "skuId": 10,
  "productId": 1,
  "productName": "手工陶瓷茶杯",
  "spec": "天青/标准",
  "specDimensions": { "颜色": "天青", "尺寸": "标准" },
  "image": "https://...",
  "price": 12800,
  "originalPrice": 15800,
  "quantity": 2,
  "selected": true,
  "subTotal": 25600,
  "stock": 50,
  "stockType": 1,
  "status": 1
}
```

**`CartGroupedVO`** (按店铺分组):

```json
{
  "groups": [
    {
      "sellerId": 5,
      "shopName": "冰美精选陶瓷",
      "shopLogo": "https://...",
      "items": [ CartItemResp, ... ],
      "shopTotalPrice": 25600,
      "shopSelectedPrice": 12800
    }
  ],
  "totalPrice": 38400,
  "selectedPrice": 25600
}
```

---

### 4.6 交易服务 (trade-service)

#### 4.6.1 OrderController — `/api/trade/order`

| #   | 方法   | 路径                                   | 认证  | 请求                                                                                   | 响应               | 阶段     |
| --- | ---- | ------------------------------------ | --- | ------------------------------------------------------------------------------------ | ---------------- | ------ |
| T1  | POST | `/api/trade/order`                   | 🔒  | `CreateOrderReq { addressId, cartItemIds: [Long], userCouponIds?: [Long], remark? }` | `OrderVO`        | ✅ V4.4 |
| T2  | GET  | `/api/trade/order/{orderNo}`         | 🔒  | Path: `orderNo` (String)                                                             | `OrderVO`        | ✅      |
| T3  | GET  | `/api/trade/order/page`              | 🔒  | Query: `status?`, `keyword?`, `startDate?`, `endDate?`, `page`(1), `size`(20)        | `IPage<OrderVO>` | ✅      |
| T4  | POST | `/api/trade/order/{orderNo}/cancel`  | 🔒  | Path: `orderNo` (String), Body: `{ reason? }`                                        | `Void`           | ✅      |
| T5  | POST | `/api/trade/order/{orderNo}/confirm` | 🔒  | Path: `orderNo` (String)                                                             | `Void`           | ✅      |
| T6  | POST | `/api/trade/order/direct`            | 🔒  | `DirectOrderReq { skuId(Long), quantity, addressId, userCouponIds?: [Long] }`        | `OrderVO`        | ✅ V4.0 |

**`CreateOrderReq`** (V4.4 支持多券叠加):

```json
{
  "addressId": 1,
  "cartItemIds": [10, 11],
  "userCouponIds": [5, 8],
  "remark": "请发顺丰"
}
```

**`OrderVO`**:

```json
{
  "id": 1,
  "orderNo": "ord-20260802-001",
  "orderType": 1,
  "userId": 1001,
  "sellerId": 5,
  "shopName": "冰美精选陶瓷",
  "totalAmount": 25600,
  "discountAmount": 2000,
  "payAmount": 23600,
  "couponDetails": [
    { "userCouponId": 5, "name": "平台满100减10", "amount": 1000 },
    { "userCouponId": 8, "name": "店铺满50减10", "amount": 1000 }
  ],
  "status": 1,
  "statusText": "待付款",
  "paymentType": 1,
  "receiverName": "张三",
  "receiverPhone": "13800138000",
  "receiverAddress": "北京市朝阳区望京SOHO T1 1205",
  "remark": "请发顺丰",
  "createTime": "2026-08-02T10:30:00",
  "payTime": null,
  "consignTime": null,
  "endTime": null,
  "payTimeoutAt": "2026-08-02T11:00:00",
  "items": [ OrderItemVO, ... ]
}
```

**`OrderItemVO`**:

```json
{
  "id": 1,
  "skuId": 10,
  "productId": 1,
  "productName": "手工陶瓷茶杯",
  "skuSpec": "天青/标准",
  "price": 12800,
  "quantity": 2,
  "subTotal": 25600,
  "image": "https://..."
}
```

#### 4.6.2 Order Lifecycle (🆕 V5.0 计划)

| #   | 方法     | 路径                                          | 认证  | 请求                       | 响应                  | 阶段     |
| --- | ------ | ------------------------------------------- | --- | ------------------------ | ------------------- | ------ |
| T7  | POST   | `/api/trade/order/{orderNo}/extend-receipt` | 🔒  | Path: `orderNo` (String) | `Void` (延长收货7天)     | ⚪ V5.0 |
| T8  | DELETE | `/api/trade/order/{orderNo}/hide`           | 🔒  | Path: `orderNo` (String) | `Void` (软删除，仅隐藏)    | ⚪ V5.0 |
| T9  | POST   | `/api/trade/order/{orderNo}/repurchase`     | 🔒  | Path: `orderNo` (String) | `Void` (再次购买，加入购物车) | ⚪ V5.0 |

#### 4.6.3 SellerOrderController — `/api/trade/seller/order`

| #   | 方法   | 路径                                         | 认证  | 请求                                                                      | 响应               | 阶段        |
| --- | ---- | ------------------------------------------ | --- | ----------------------------------------------------------------------- | ---------------- | --------- |
| T10 | GET  | `/api/trade/seller/order/page`             | 🏪  | Query: `status?`, `keyword?`, `page`(1), `size`(20)                     | `IPage<OrderVO>` | ✅         |
| T11 | GET  | `/api/trade/seller/order/{orderNo}`        | 🏪  | Path: `orderNo` (String)                                                | `OrderVO`        | ✅         |
| T12 | POST | `/api/trade/seller/order/{orderNo}/ship`   | 🏪  | Path: `orderNo` (String), Body: `{ logisticsNumber, logisticsCompany }` | `Void`           | ✅         |
| T13 | PUT  | `/api/trade/seller/order/{orderNo}/remark` | 🏪  | Path: `orderNo` (String), Body: `{ sellerRemark }`                      | `Void`           | 🆕 ⚪ V5.0 |
| T14 | PUT  | `/api/trade/seller/order/{orderNo}/price`  | 🏪  | Path: `orderNo` (String), Body: `{ discountAmount }` (商家改价，仅限待付款)       | `Void`           | 🆕 ⚪ V5.0 |

#### 4.6.4 AfterSaleController — `/api/after-sale`

| #   | 方法   | 路径                               | 认证  | 请求                                                                                                       | 响应                   | 阶段        |
| --- | ---- | -------------------------------- | --- | -------------------------------------------------------------------------------------------------------- | -------------------- | --------- |
| T15 | POST | `/api/after-sale`                | 🔒  | `AfterSaleApplyReq { orderNo, type: REFUND\|RETURN, reason?, evidenceImages?: [String], refundAmount? }` | `AfterSaleVO`        | ✅         |
| T16 | GET  | `/api/after-sale`                | 🔒  | Query: `status?`, `page`(1), `size`(20)                                                                  | `IPage<AfterSaleVO>` | ✅         |
| T17 | GET  | `/api/after-sale/{id}`           | 🔒  | Path: `id` (Long)                                                                                        | `AfterSaleVO`        | 🆕 ⚪ V5.0 |
| T18 | POST | `/api/after-sale/{id}/cancel`    | 🔒  | Path: `id` (Long)                                                                                        | `Void`               | 🆕 ⚪ V5.0 |
| T19 | POST | `/api/after-sale/{id}/ship-back` | 🔒  | Path: `id` (Long), Body: `{ logisticsNumber, logisticsCompany }`                                         | `Void` (退货填写物流)      | 🆕 ⚪ V5.0 |

**`AfterSaleVO`**:

```json
{
  "id": 1,
  "orderNo": "ord-xxx",
  "type": "RETURN",
  "reason": "商品与描述不符",
  "evidenceImages": ["https://..."],
  "refundAmount": 12800,
  "status": 1,
  "statusText": "待商家审核",
  "sellerRemark": null,
  "adminRemark": null,
  "createTime": "2026-08-02T15:00:00",
  "updateTime": "2026-08-02T15:00:00"
}
```

#### 4.6.5 SellerFinanceController — `/api/seller/finance`

| #   | 方法   | 路径                                    | 认证  | 请求                                                                  | 响应                                         | 阶段  |
| --- | ---- | ------------------------------------- | --- | ------------------------------------------------------------------- | ------------------------------------------ | --- |
| T20 | GET  | `/api/seller/finance/settlement/page` | 🏪  | Query: `page`(1), `size`(20)                                        | `IPage<SettlementVO>`                      | ✅   |
| T21 | GET  | `/api/seller/finance/settlement/{id}` | 🏪  | Path: `id` (Long)                                                   | `SettlementVO`                             | ✅   |
| T22 | GET  | `/api/seller/finance/balance`         | 🏪  | —                                                                   | `Map<String,Object>` `{sellerId, balance}` | ✅   |
| T23 | POST | `/api/seller/finance/withdrawal`      | 🏪  | `WithdrawalApplyReq { amount, bankCard?, bankName?, accountName? }` | `WithdrawalVO`                             | ✅   |
| T24 | GET  | `/api/seller/finance/withdrawal/page` | 🏪  | Query: `page`(1), `size`(20)                                        | `IPage<WithdrawalVO>`                      | ✅   |

#### 4.6.6 SellerApplicationController — `/api/seller/apply`

| #   | 方法   | 路径                  | 认证  | 请求                                  | 响应                           | 阶段  |
| --- | ---- | ------------------- | --- | ----------------------------------- | ---------------------------- | --- |
| T25 | POST | `/api/seller/apply` | 🔒  | `SellerApplicationApplyReq { ... }` | `SellerApplicationVO`        | ✅   |
| T26 | GET  | `/api/seller/apply` | 🔒  | —                                   | `SellerApplicationVO` (最新申请) | ✅   |

---

### 4.7 支付服务 (pay-service)

#### 4.7.1 PayController — `/api/pay`

| #    | 方法   | 路径                                | 认证  | 请求                                                                         | 响应                   | 阶段     |
| ---- | ---- | --------------------------------- | --- | -------------------------------------------------------------------------- | -------------------- | ------ |
| PAY1 | POST | `/api/pay/order/{orderNo}`        | 🔒  | Path: `orderNo` (String); Query: `channel`=`WECHAT`(默认)`\|ALIPAY\|BALANCE` | `PayOrderVO`         | ✅ V2.3 |
| PAY2 | POST | `/api/pay/callback/wechat`        | 🌐  | Headers: `Wechatpay-Signature/Nonce/Timestamp/Serial`, Body: 原始回调报文        | `String` ("SUCCESS") | ✅      |
| PAY3 | POST | `/api/pay/callback/alipay`        | 🌐  | Form params (含 `out_trade_no`)                                             | `String` ("success") | ✅ V2.3 |
| PAY4 | GET  | `/api/pay/order/{orderNo}/status` | 🔒  | Path: `orderNo` (String)                                                   | `PayOrderVO`         | ✅      |

**`PayOrderVO`**:

```json
{
  "payOrderNo": "pay-20260802-001",
  "bizOrderNo": "ord-20260802-001",
  "amount": 23600,
  "payChannelCode": "WECHAT",
  "status": 1,
  "statusText": "待支付",
  "qrCodeUrl": "weixin://wxpay/bizpayurl?pr=xxx",
  "payUrl": "https://...",
  "createTime": "2026-08-02T10:30:00",
  "payTime": null,
  "expireTime": "2026-08-02T11:00:00"
}
```

> - 微信支付: `qrCodeUrl` / `payUrl` 包含支付链接/二维码，前端生成二维码
> - 支付宝 Mock: 返回虚拟支付链接
> - 余额支付: 即时扣款，`status` 直接为 "SUCCESS"，无二维码

#### 4.7.2 Payment History (🆕 V5.0 计划)

| #    | 方法  | 路径                 | 认证  | 请求                                                                 | 响应                  | 阶段     |
| ---- | --- | ------------------ | --- | ------------------------------------------------------------------ | ------------------- | ------ |
| PAY5 | GET | `/api/pay/history` | 🔒  | Query: `page`(1), `size`(20), `channel?`, `startDate?`, `endDate?` | `IPage<PayOrderVO>` | ⚪ V5.0 |

---

### 4.8 物流服务 (logistics-service)

#### 4.8.1 LogisticsController — `/api/logistics`

| #   | 方法  | 路径                         | 认证  | 请求                     | 响应            | 阶段  |
| --- | --- | -------------------------- | --- | ---------------------- | ------------- | --- |
| L1  | GET | `/api/logistics/{orderId}` | 🌐  | Path: `orderId` (Long) | `LogisticsVO` | ✅   |

**`LogisticsVO`**:

```json
{
  "orderId": 1,
  "orderNo": "ord-xxx",
  "logisticsNumber": "SF1234567890",
  "logisticsCompany": "顺丰速运",
  "contact": "张三",
  "mobile": "13800138000",
  "address": "北京市朝阳区望京SOHO T1 1205",
  "status": 2,
  "statusText": "运输中",
  "traces": [
    { "time": "2026-08-03T08:00:00", "status": "已签收", "detail": "本人签收" },
    { "time": "2026-08-03T06:00:00", "status": "派送中", "detail": "快递员正在派送" },
    { "time": "2026-08-02T22:00:00", "status": "已到达", "detail": "已到达【北京朝阳集散中心】" },
    { "time": "2026-08-02T18:00:00", "status": "运输中", "detail": "已离开【上海分拨中心】" },
    { "time": "2026-08-02T15:00:00", "status": "已揽收", "detail": "快递员已揽收" }
  ],
  "createTime": "2026-08-02T15:00:00",
  "updateTime": "2026-08-03T08:00:00"
}
```

---

### 4.9 搜索服务 (search-service)

#### 4.9.1 SearchController — `/api/search`

| #   | 方法     | 路径                    | 认证  | 请求                                                                                                                                    | 响应                      | 阶段        |
| --- | ------ | --------------------- | --- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | --------- |
| SE1 | GET    | `/api/search/product` | 🌐  | Query: `keyword?`, `categoryId?`, `brand?`, `minPrice?`, `maxPrice?`, `sort?`(sales\|price\|newest\|relevance), `page`(1), `size`(20) | `Page<ProductSearchVO>` | ✅         |
| SE2 | GET    | `/api/search/hot`     | 🌐  | Query: `limit`(10)                                                                                                                    | `List<String>`          | ✅         |
| SE3 | GET    | `/api/search/history` | 🔒  | Query: `limit`(10)                                                                                                                    | `List<String>`          | ✅         |
| SE4 | DELETE | `/api/search/history` | 🔒  | —                                                                                                                                     | `Void` (清空搜索历史)         | 🆕 ⚪ V5.0 |
| SE5 | GET    | `/api/search/suggest` | 🌐  | Query: `keyword` (前缀)                                                                                                                 | `List<String>` (搜索建议)   | 🆕 ⚪ V5.0 |

**`ProductSearchVO`**:

```json
{
  "id": 1,
  "productId": "prod-001",
  "categoryId": 1,
  "name": "手工陶瓷茶杯",
  "description": "纯手工制作...",
  "brand": "冰美精选",
  "mainImage": "https://...",
  "price": 12800,
  "originalPrice": 15800,
  "soldCount": 1280,
  "rating": 4.7,
  "shopName": "冰美精选陶瓷",
  "salesTags": ["热销"]
}
```

---

### 4.10 营销服务 (marketing-service)

#### 4.10.1 CouponController — `/api/coupon`

| #   | 方法   | 路径                             | 认证  | 请求                                                         | 响应                                                 | 阶段        |
| --- | ---- | ------------------------------ | --- | ---------------------------------------------------------- | -------------------------------------------------- | --------- |
| M1  | POST | `/api/coupon/claim`            | 🔒  | Query: `couponId` (String)                                 | `UserCouponVO`                                     | ✅ V4.4    |
| M2  | POST | `/api/coupon/grab`             | 🔒  | Query: `couponId` (String)                                 | `UserCouponVO` (Redis Lua 秒杀抢券)                    | ✅ V4.5    |
| M3  | GET  | `/api/coupon/available`        | 🔒  | —                                                          | `List<UserCouponVO>` (未使用未过期)                      | ✅         |
| M4  | GET  | `/api/coupon/used`             | 🔒  | —                                                          | `List<UserCouponVO>` (已使用/已过期)                     | ✅         |
| M5  | GET  | `/api/coupon/template`         | 🌐  | —                                                          | `List<CouponVO>` (可领取的券模板)                         | ✅         |
| M6  | POST | `/api/coupon/available/filter` | 🔒  | `CouponFilterReq { skuIds: [Long], totalAmount: Integer }` | `List<UserCouponVO>` (结算页预过滤)                      | ✅ V4.3    |
| M7  | GET  | `/api/coupon/count`            | 🔒  | —                                                          | `Map<String,Integer>` `{available, used, expired}` | 🆕 ⚪ V5.0 |

**`CouponVO`** (券模板):

```json
{
  "couponId": "cpn-001",
  "name": "618满200减30",
  "discountType": 1,
  "couponCategory": 1,
  "scopeType": 1,
  "scopeValues": null,
  "priceInCents": 3000,
  "minAmountInCents": 20000,
  "startTime": "2026-06-01T00:00:00",
  "endTime": "2026-06-18T23:59:59",
  "totalStock": 10000,
  "claimedCount": 6523,
  "status": 1,
  "sellerId": null,
  "shopName": null
}
```

**`UserCouponVO`** (用户持有的券):

```json
{
  "userCouponId": 5,
  "couponId": "cpn-001",
  "name": "618满200减30",
  "discountType": 1,
  "couponCategory": 1,
  "scopeType": 1,
  "priceInCents": 3000,
  "minAmountInCents": 20000,
  "status": 1,
  "statusText": "可用",
  "usedOrderNo": null,
  "claimTime": "2026-06-10T10:00:00",
  "expireTime": "2026-06-18T23:59:59"
}
```

#### 4.10.2 FlashSaleController — `/api/flash`

| #   | 方法   | 路径                    | 认证  | 请求                                          | 响应                             | 阶段        |
| --- | ---- | --------------------- | --- | ------------------------------------------- | ------------------------------ | --------- |
| M8  | GET  | `/api/flash`          | 🌐  | —                                           | `List<FlashSaleVO>` (进行中的秒杀场次) | ✅         |
| M9  | GET  | `/api/flash/{id}`     | 🌐  | Path: `id` (Long)                           | `FlashSaleVO` (秒杀详情)           | 🆕 ⚪ V5.0 |
| M10 | POST | `/api/flash/buy`      | 🔒  | Query: `flashId` (Long), `addressId` (Long) | `FlashBuyVO`                   | ✅ V4.1    |
| M11 | GET  | `/api/flash/timeline` | 🌐  | —                                           | `List<FlashSessionVO>` (秒杀时间段) | 🆕 ⚪ V5.0 |

**`FlashSaleVO`**:

```json
{
  "id": 1,
  "skuId": 10,
  "productName": "手工陶瓷茶杯",
  "productImage": "https://...",
  "flashPrice": 9900,
  "originalPrice": 12800,
  "stock": 100,
  "soldCount": 35,
  "startTime": "2026-08-02T10:00:00",
  "endTime": "2026-08-02T12:00:00",
  "status": 1,
  "progressPercent": 35
}
```

**`FlashBuyVO`**:

```json
{
  "orderNo": "ord-xxx",
  "flashId": 1,
  "flashPrice": 9900,
  "payAmount": 9900,
  "status": 1
}
```

#### 4.10.3 Marketing Activities (🆕 V5.0 计划 — 京东标准)

| #   | 方法  | 路径                   | 认证  | 请求                                                    | 响应                 | 阶段     |
| --- | --- | -------------------- | --- | ----------------------------------------------------- | ------------------ | ------ |
| M12 | GET | `/api/activity`      | 🌐  | Query: `type?`(FULL_REDUCE\|LIMIT_DISCOUNT\|NEW_USER) | `List<ActivityVO>` | ⚪ V5.0 |
| M13 | GET | `/api/activity/{id}` | 🌐  | Path: `id` (Long)                                     | `ActivityVO`       | ⚪ V5.0 |

---

### 4.11 AI 服务 (ai-service)

> 当前实现: V2.5 生产就绪 ✅ | 完整选型见 [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)
> AI 服务通过 `ai.enabled` 配置开关，关闭时返回静态提示。

#### 4.11.1 AiAssistantController — `/api/ai`

| #   | 方法   | 路径                    | 认证  | 请求                                           | 响应                      | 阶段     |
| --- | ---- | --------------------- | --- | -------------------------------------------- | ----------------------- | ------ |
| AI1 | POST | `/api/ai/chat`        | 🔒  | `AiChatRequest { message, conversationId? }` | `AiChatResponse`        | ✅ V2.0 |
| AI2 | POST | `/api/ai/chat/stream` | 🔒  | `AiChatRequest { message, conversationId? }` | SSE `text/event-stream` | ✅ V2.5 |

**`AiChatRequest`**:

```json
{
  "message": "500以内适合夏天穿的透气跑鞋",
  "conversationId": "conv-uuid-xxx"
}
```

**`AiChatResponse`**:

```json
{
  "reply": "为您找到3款适合夏天穿的透气跑鞋：1. Nike ZoomX ¥499...",
  "conversationId": "conv-uuid-xxx",
  "products": [
    { "productId": 1, "name": "Nike ZoomX", "price": 49900, "image": "https://...", "reason": "透气网面，适合夏季跑步" }
  ]
}
```

#### 4.11.2 CustomerServiceController — `/api/ai/cs`

| #   | 方法   | 路径                       | 认证  | 请求                                           | 响应                      | 阶段     |
| --- | ---- | ------------------------ | --- | -------------------------------------------- | ----------------------- | ------ |
| AI3 | POST | `/api/ai/cs/chat`        | 🔒  | `AiChatRequest { message, conversationId? }` | `AiChatResponse`        | ✅ V2.0 |
| AI4 | POST | `/api/ai/cs/chat/stream` | 🔒  | `AiChatRequest { message, conversationId? }` | SSE `text/event-stream` | ✅ V2.5 |

#### 4.11.3 ConversationController — `/api/ai`

| #   | 方法     | 路径                           | 认证  | 请求                  | 响应                          | 阶段     |
| --- | ------ | ---------------------------- | --- | ------------------- | --------------------------- | ------ |
| AI5 | GET    | `/api/ai/conversations`      | 🔒  | —                   | `List<ConversationSummary>` | ✅ V2.5 |
| AI6 | GET    | `/api/ai/conversations/{id}` | 🔒  | Path: `id` (String) | `ConversationDetail`        | ✅ V2.5 |
| AI7 | DELETE | `/api/ai/conversations/{id}` | 🔒  | Path: `id` (String) | `Void`                      | ✅ V2.5 |

**`ConversationSummary`**:

```json
{
  "conversationId": "conv-uuid-xxx",
  "agentType": "SHOPPING",
  "title": "推荐跑鞋",
  "messageCount": 8,
  "lastMessage": "还有什么需要帮您的吗？",
  "createTime": "2026-07-19T10:30:00",
  "updateTime": "2026-07-19T10:35:00"
}
```

**`ConversationDetail`**:

```json
{
  "conversationId": "conv-uuid-xxx",
  "agentType": "SHOPPING",
  "messages": [
    {
      "role": "USER",
      "content": "推荐跑鞋",
      "timestamp": "2026-07-19T10:30:00"
    },
    {
      "role": "ASSISTANT",
      "content": "为您找到以下商品...",
      "toolCalls": [
        {
          "toolName": "searchProducts",
          "input": { "keyword": "透气跑鞋 500以内" },
          "output": "[P001] Nike ZoomX — ¥499.00 (销量:1523)..."
        }
      ],
      "timestamp": "2026-07-19T10:30:05"
    }
  ]
}
```

---

### 4.12 买家-商家消息服务 (user-service)

> **大厂对标**: 淘宝旺旺/京东咚咚。WebSocket + STOMP 协议。

#### 4.12.1 Chat WebSocket

| #   | 方法  | 路径         | 说明                                                          | 阶段     |
| --- | --- | ---------- | ----------------------------------------------------------- | ------ |
| CH1 | WS  | `/ws/chat` | STOMP 端点。订阅 `/user/queue/messages` 接收消息；发送 `/app/chat.send` | ✅ V3.3 |

**STOMP 消息体 (ChatMessage)**:

```json
{
  "messageId": "uuid",
  "conversationId": "buyer_1001_seller_5_product_P001",
  "senderId": 1001,
  "senderRole": "BUYER",
  "senderName": "买家张三",
  "senderAvatar": "https://...",
  "receiverId": 5,
  "content": "这个商品还有货吗？",
  "contentType": "TEXT",
  "timestamp": "2026-08-02T10:30:00"
}
```

#### 4.12.2 ChatMessageController — `/api/chat`

| #   | 方法  | 路径                                      | 认证  | 请求                                                | 响应                                     | 阶段        |
| --- | --- | --------------------------------------- | --- | ------------------------------------------------- | -------------------------------------- | --------- |
| CH2 | GET | `/api/chat/conversations`               | 🔒  | —                                                 | `List<ChatConversationVO>` (按最后消息时间排序) | ✅ V3.3    |
| CH3 | GET | `/api/chat/conversations/{id}/messages` | 🔒  | Path: `id` (String), Query: `page`(1), `size`(50) | `IPage<ChatMessage>`                   | ✅ V3.3    |
| CH4 | GET | `/api/chat/unread-count`                | 🔒  | —                                                 | `Integer`                              | ✅ V3.3    |
| CH5 | PUT | `/api/chat/conversations/{id}/read`     | 🔒  | Path: `id` (String)                               | `Void` (标记已读)                          | 🆕 ⚪ V5.0 |

**`ChatConversationVO`**:

```json
{
  "conversationId": "buyer_1001_seller_5_product_P001",
  "targetUserId": 5,
  "targetUserName": "冰美精选陶瓷",
  "targetUserAvatar": "https://...",
  "targetRole": "SELLER",
  "productId": 1,
  "productName": "手工陶瓷茶杯",
  "productImage": "https://...",
  "lastMessage": "好的，今天发货",
  "lastMessageTime": "2026-08-02T10:35:00",
  "unreadCount": 2
}
```

#### 4.12.3 商家回复模板 — `/api/seller/templates`

| #   | 方法     | 路径                           | 认证  | 请求                                                         | 响应                      | 阶段     |
| --- | ------ | ---------------------------- | --- | ---------------------------------------------------------- | ----------------------- | ------ |
| CH6 | GET    | `/api/seller/templates`      | 🏪  | —                                                          | `List<ReplyTemplateVO>` | ✅ V3.3 |
| CH7 | POST   | `/api/seller/templates`      | 🏪  | `{ title, content, category? }`                            | `ReplyTemplateVO`       | ✅ V3.3 |
| CH8 | PUT    | `/api/seller/templates/{id}` | 🏪  | Path: `id` (Long), Body: `{ title?, content?, category? }` | `Void`                  | ✅ V3.3 |
| CH9 | DELETE | `/api/seller/templates/{id}` | 🏪  | Path: `id` (Long)                                          | `Void`                  | ✅ V3.3 |

#### 4.12.4 商家知识库 — `/api/seller/knowledge`

| #    | 方法     | 路径                           | 认证  | 请求                                                         | 响应                          | 阶段     |
| ---- | ------ | ---------------------------- | --- | ---------------------------------------------------------- | --------------------------- | ------ |
| CH10 | GET    | `/api/seller/knowledge`      | 🏪  | Query: `category?`                                         | `List<MerchantKnowledgeVO>` | ✅ V3.3 |
| CH11 | POST   | `/api/seller/knowledge`      | 🏪  | `{ title, content, category? }`                            | `MerchantKnowledgeVO`       | ✅ V3.3 |
| CH12 | PUT    | `/api/seller/knowledge/{id}` | 🏪  | Path: `id` (Long), Body: `{ title?, content?, category? }` | `Void`                      | ✅ V3.3 |
| CH13 | DELETE | `/api/seller/knowledge/{id}` | 🏪  | Path: `id` (Long)                                          | `Void`                      | ✅ V3.3 |

---

### 4.13 管理员端 (多服务)

#### 4.13.1 AdminUserController — `/api/admin` (user-service)

| #   | 方法  | 路径                               | 认证  | 请求                                                  | 响应                    | 阶段  |
| --- | --- | -------------------------------- | --- | --------------------------------------------------- | --------------------- | --- |
| AD1 | GET | `/api/admin/user/page`           | 🔑  | Query: `page`(1), `size`(20), `keyword?`, `status?` | `IPage<UserInfoResp>` | ✅   |
| AD2 | PUT | `/api/admin/user/{id}/status`    | 🔑  | Path: `id` (Long), Query: `status` (Integer)        | `Void`                | ✅   |
| AD3 | PUT | `/api/admin/user/{id}/role`      | 🔑  | Path: `id` (Long), Query: `roleType` (Integer)      | `Void`                | ✅   |
| AD4 | GET | `/api/admin/seller/pending`      | 🔑  | Query: `page`(1), `size`(20)                        | `IPage<SellerVO>`     | ✅   |
| AD5 | PUT | `/api/admin/seller/{id}/approve` | 🔑  | Path: `id` (Long)                                   | `Void`                | ✅   |

#### 4.13.2 AdminLogController — `/api/admin/log` (user-service)

| #   | 方法  | 路径               | 认证  | 请求                                                                           | 响应                      | 阶段  |
| --- | --- | ---------------- | --- | ---------------------------------------------------------------------------- | ----------------------- | --- |
| AD6 | GET | `/api/admin/log` | 🔑  | Query: `page`(1), `size`(20), `userId?`, `action?`, `startDate?`, `endDate?` | `IPage<OperationLogVO>` | ✅   |

#### 4.13.3 AdminHomeController — `/api/admin/home` (item-service)

| #    | 方法     | 路径                     | 认证  | 请求                                          | 响应                       | 阶段  |
| ---- | ------ | ---------------------- | --- | ------------------------------------------- | ------------------------ | --- |
| AD7  | POST   | `/api/admin/home`      | 🔑  | `HomeConfigEntity`                          | `HomeConfigEntity`       | ✅   |
| AD8  | PUT    | `/api/admin/home/{id}` | 🔑  | Path: `id` (Long), Body: `HomeConfigEntity` | `HomeConfigEntity`       | ✅   |
| AD9  | DELETE | `/api/admin/home/{id}` | 🔑  | Path: `id` (Long)                           | `Void`                   | ✅   |
| AD10 | GET    | `/api/admin/home`      | 🔑  | —                                           | `List<HomeConfigEntity>` | ✅   |

#### 4.13.4 AdminController — `/api/trade/admin` (trade-service)

| #    | 方法  | 路径                                    | 认证  | 请求                                                                            | 响应                              | 阶段     |
| ---- | --- | ------------------------------------- | --- | ----------------------------------------------------------------------------- | ------------------------------- | ------ |
| AD11 | GET | `/api/trade/admin/dashboard`          | 🔑  | —                                                                             | `AdminDashboardVO`              | ✅      |
| AD12 | GET | `/api/trade/admin/orders/page`        | 🔑  | Query: `page`(1), `size`(20), `status?`, `keyword?`, `startDate?`, `endDate?` | `IPage<OrderVO>`                | ✅      |
| AD13 | GET | `/api/trade/admin/stats/trend`        | 🔑  | Query: `days`(30)                                                             | `List<TrendDataVO>` (每日订单数+销售额) | ✅ V4.0 |
| AD14 | GET | `/api/trade/admin/stats/top-products` | 🔑  | Query: `limit`(10), `days?`(30)                                               | `List<ProductStatVO>`           | ✅ V4.0 |
| AD15 | GET | `/api/trade/admin/stats/sales`        | 🔑  | Query: `from`, `to` (yyyy-MM-dd)                                              | `List<DailySalesVO>`            | ✅ V4.0 |

**`AdminDashboardVO`**:

```json
{
  "totalUsers": 12580,
  "totalOrders": 45620,
  "totalRevenue": 892450000,
  "todayNewUsers": 128,
  "todayOrders": 356,
  "todayRevenue": 5680000,
  "pendingSellerCount": 3
}
```

#### 4.13.5 AdminAfterSaleController — `/api/admin/after-sale` (trade-service)

| #    | 方法  | 路径                                  | 认证  | 请求                                            | 响应                   | 阶段  |
| ---- | --- | ----------------------------------- | --- | --------------------------------------------- | -------------------- | --- |
| AD16 | GET | `/api/admin/after-sale`             | 🔑  | Query: `status?`, `page`(1), `size`(20)       | `IPage<AfterSaleVO>` | ✅   |
| AD17 | PUT | `/api/admin/after-sale/{id}/review` | 🔑  | Path: `id` (Long), Query: `status`, `remark?` | `Void`               | ✅   |

#### 4.13.6 AdminApplicationController — `/api/admin/application` (trade-service)

| #    | 方法  | 路径                                   | 认证  | 请求                                            | 响应                          | 阶段  |
| ---- | --- | ------------------------------------ | --- | --------------------------------------------- | --------------------------- | --- |
| AD18 | GET | `/api/admin/application`             | 🔑  | Query: `status`(0)                            | `List<SellerApplicationVO>` | ✅   |
| AD19 | PUT | `/api/admin/application/{id}/review` | 🔑  | Path: `id` (Long), Query: `status`, `remark?` | `Void`                      | ✅   |

#### 4.13.7 AdminFinanceController — `/api/admin/finance` (trade-service)

| #    | 方法   | 路径                                          | 认证  | 请求                                                   | 响应                    | 阶段  |
| ---- | ---- | ------------------------------------------- | --- | ---------------------------------------------------- | --------------------- | --- |
| AD20 | POST | `/api/admin/finance/settlement/generate`    | 🔑  | Query: `sellerId`, `periodStart`, `periodEnd`        | `SettlementVO`        | ✅   |
| AD21 | GET  | `/api/admin/finance/settlement/page`        | 🔑  | Query: `page`(1), `size`(20), `sellerId?`, `status?` | `IPage<SettlementVO>` | ✅   |
| AD22 | GET  | `/api/admin/finance/withdrawal/page`        | 🔑  | Query: `status?`, `page`(1), `size`(20)              | `IPage<WithdrawalVO>` | ✅   |
| AD23 | PUT  | `/api/admin/finance/withdrawal/{id}/review` | 🔑  | Path: `id` (Long), Query: `status`, `remark?`        | `Void`                | ✅   |

#### 4.13.8 AdminCouponController — `/api/admin/coupon` (marketing-service)

| #    | 方法   | 路径                                     | 认证  | 请求                                                         | 响应                | 阶段        |
| ---- | ---- | -------------------------------------- | --- | ---------------------------------------------------------- | ----------------- | --------- |
| AD24 | POST | `/api/admin/coupon`                    | 🔑  | `CouponCreateReq` (完整券模板配置)                                | `CouponVO`        | ✅ V4.0    |
| AD25 | PUT  | `/api/admin/coupon/{couponId}/disable` | 🔑  | Path: `couponId` (String)                                  | `Void`            | ✅         |
| AD26 | GET  | `/api/admin/coupon`                    | 🔑  | Query: `status?`, `couponCategory?`, `page`(1), `size`(20) | `IPage<CouponVO>` | 🆕 ⚪ V5.0 |

#### 4.13.9 MerchantCouponController — `/api/seller/coupon` (marketing-service)

| #    | 方法   | 路径                                      | 认证  | 请求                        | 响应               | 阶段  |
| ---- | ---- | --------------------------------------- | --- | ------------------------- | ---------------- | --- |
| AD27 | POST | `/api/seller/coupon`                    | 🏪  | `CouponCreateReq` (店铺券)   | `CouponVO`       | ✅   |
| AD28 | GET  | `/api/seller/coupon`                    | 🏪  | —                         | `List<CouponVO>` | ✅   |
| AD29 | PUT  | `/api/seller/coupon/{couponId}/disable` | 🏪  | Path: `couponId` (String) | `Void`           | ✅   |

#### 4.13.10 RoleController — `/api/admin/roles` (user-service, V3.1 RBAC)

| #    | 方法     | 路径                                  | 认证  | 请求                                                    | 响应                        | 阶段     |
| ---- | ------ | ----------------------------------- | --- | ----------------------------------------------------- | ------------------------- | ------ |
| AD30 | GET    | `/api/admin/roles`                  | 🔑  | —                                                     | `List<RoleVO>`            | ✅ V3.1 |
| AD31 | POST   | `/api/admin/roles`                  | 🔑  | `{ name, code, description }`                         | `RoleVO`                  | ✅ V3.1 |
| AD32 | PUT    | `/api/admin/roles/{id}`             | 🔑  | Path: `id` (Long), Body: `{ name?, description? }`    | `Void`                    | ✅ V3.1 |
| AD33 | DELETE | `/api/admin/roles/{id}`             | 🔑  | Path: `id` (Long)                                     | `Void` (is_system=1 不可删除) | ✅ V3.1 |
| AD34 | GET    | `/api/admin/roles/{id}/permissions` | 🔑  | Path: `id` (Long)                                     | `List<Long>` (权限ID列表)     | ✅ V3.1 |
| AD35 | POST   | `/api/admin/roles/{id}/permissions` | 🔑  | Path: `id` (Long), Body: `{ permissionIds: [1,2,3] }` | `Void`                    | ✅ V3.1 |

#### 4.13.11 PermissionController — `/api/admin/permissions` (user-service, V3.1 RBAC)

| #    | 方法  | 路径                            | 认证  | 请求  | 响应                       | 阶段     |
| ---- | --- | ----------------------------- | --- | --- | ------------------------ | ------ |
| AD36 | GET | `/api/admin/permissions`      | 🔑  | —   | `List<PermissionVO>`     | ✅ V3.1 |
| AD37 | GET | `/api/admin/permissions/tree` | 🔑  | —   | `List<PermissionTreeVO>` | ✅ V3.1 |

#### 4.13.12 Admin Product Management (🆕 V5.0 计划)

| #    | 方法  | 路径                               | 认证  | 请求                                                               | 响应                 | 阶段     |
| ---- | --- | -------------------------------- | --- | ---------------------------------------------------------------- | ------------------ | ------ |
| AD38 | PUT | `/api/admin/product/{id}/status` | 🔑  | Path: `id` (Long), Query: `status`                               | `Void` (审核/强制上下架)  | ⚪ V5.0 |
| AD39 | GET | `/api/admin/product/page`        | 🔑  | Query: `status?`, `keyword?`, `sellerId?`, `page`(1), `size`(20) | `IPage<ProductVO>` | ⚪ V5.0 |

#### 4.13.13 Admin Flash Sale Management (🆕 V5.0 计划)

| #    | 方法     | 路径                      | 认证  | 请求                                            | 响应            | 阶段     |
| ---- | ------ | ----------------------- | --- | --------------------------------------------- | ------------- | ------ |
| AD40 | POST   | `/api/admin/flash`      | 🔑  | `FlashSaleCreateReq`                          | `FlashSaleVO` | ⚪ V5.0 |
| AD41 | PUT    | `/api/admin/flash/{id}` | 🔑  | Path: `id` (Long), Body: `FlashSaleUpdateReq` | `Void`        | ⚪ V5.0 |
| AD42 | DELETE | `/api/admin/flash/{id}` | 🔑  | Path: `id` (Long)                             | `Void`        | ⚪ V5.0 |

---

### 4.14 内部 Feign 端点 (`/internal/**`)

> 以下端点仅供集群内微服务间通过 Feign 调用。**不经过 Gateway**，返回原始类型（不包装 Result）。异常直接透传给调用方。

#### 4.14.1 InternalAuthController — `/internal/user` (user-service)

| #   | 方法   | 路径                              | 请求                    | 响应 (原始)        | 阶段  |
| --- | ---- | ------------------------------- | --------------------- | -------------- | --- |
| I1  | POST | `/internal/user/register`       | `RegisterReqDTO`      | `LoginRespDTO` | ✅   |
| I2  | POST | `/internal/user/login/password` | `PasswordLoginReqDTO` | `LoginRespDTO` | ✅   |
| I3  | POST | `/internal/user/login/sms`      | `SmsLoginReqDTO`      | `LoginRespDTO` | ✅   |
| I4  | POST | `/internal/user/login/wechat`   | `WechatLoginReqDTO`   | `LoginRespDTO` | ✅   |
| I5  | POST | `/internal/user/reset-password` | `ResetPasswordReqDTO` | `void`         | ✅   |
| I6  | GET  | `/internal/user/{id}`           | Path: `id` (Long)     | `UserInfoResp` | ✅   |
| I7  | GET  | `/internal/user/count`          | —                     | `long`         | ✅   |

#### 4.14.2 InternalAddressController — `/internal/address` (user-service)

| #   | 方法  | 路径                       | 请求                | 响应 (原始)       | 阶段  |
| --- | --- | ------------------------ | ----------------- | ------------- | --- |
| I8  | GET | `/internal/address/{id}` | Path: `id` (Long) | `AddressResp` | ✅   |

#### 4.14.3 InternalBalanceController — `/internal/balance` (user-service)

| #   | 方法   | 路径                         | 请求                                       | 响应 (原始) | 阶段     |
| --- | ---- | -------------------------- | ---------------------------------------- | ------- | ------ |
| I9  | POST | `/internal/balance/deduct` | Query: `userId`(Long), `amount`(Integer) | `void`  | ✅ V2.3 |

#### 4.14.4 InternalPointsController — `/internal/points` (user-service)

| #   | 方法   | 路径                     | 请求                                             | 响应 (原始)              | 阶段  |
| --- | ---- | ---------------------- | ---------------------------------------------- | -------------------- | --- |
| I10 | POST | `/internal/points/add` | Query: `userId`, `points`, `type`(2), `source` | `long` (new balance) | ✅   |

#### 4.14.5 InternalSkuController — `/internal/item/sku` (item-service)

| #   | 方法   | 路径                              | 请求                                             | 响应 (原始)        | 阶段  |
| --- | ---- | ------------------------------- | ---------------------------------------------- | -------------- | --- |
| I11 | GET  | `/internal/item/sku/list/batch` | Query: `ids` (List\<Long\>)                    | `List<SkuDTO>` | ✅   |
| I12 | POST | `/internal/item/sku/deduct`     | `List<StockOpReq> [{ skuId(Long), quantity }]` | `void`         | ✅   |
| I13 | POST | `/internal/item/sku/restore`    | `List<StockOpReq> [{ skuId(Long), quantity }]` | `void`         | ✅   |

#### 4.14.6 InternalCartController — `/internal/cart` (cart-service)

| #   | 方法     | 路径                        | 请求                                                     | 响应 (原始)             | 阶段     |
| --- | ------ | ------------------------- | ------------------------------------------------------ | ------------------- | ------ |
| I14 | GET    | `/internal/cart/selected` | Query: `userId` (Long)                                 | `List<CartItemDTO>` | ✅      |
| I15 | DELETE | `/internal/cart/clear`    | Query: `userId` (Long)                                 | `void`              | ✅      |
| I16 | DELETE | `/internal/cart/items`    | Query: `userId` (Long), Body: `List<Long>` cartItemIds | `void`              | ✅ V4.0 |

#### 4.14.7 InternalOrderController — `/internal/trade/order` (trade-service)

| #   | 方法   | 路径                                       | 请求                                                                                     | 响应 (原始)           | 阶段     |
| --- | ---- | ---------------------------------------- | -------------------------------------------------------------------------------------- | ----------------- | ------ |
| I17 | PUT  | `/internal/trade/order/{orderNo}/status` | Path: `orderNo` (String), Query: `status` (Integer)                                    | `void`            | ✅      |
| I18 | GET  | `/internal/trade/order/{orderNo}`        | Path: `orderNo` (String)                                                               | `OrderSummaryDTO` | ✅      |
| I19 | POST | `/internal/trade/order/create`           | `CreateOrderInternalReq { userId, skuId, quantity, addressId, flashId?, flashPrice? }` | `OrderSummaryDTO` | ✅ V4.1 |

#### 4.14.8 InternalCouponController — `/internal/coupon` (marketing-service)

| #   | 方法   | 路径                          | 请求                                                                                                                | 响应 (原始)        | 阶段     |
| --- | ---- | --------------------------- | ----------------------------------------------------------------------------------------------------------------- | -------------- | ------ |
| I20 | POST | `/internal/coupon/use`      | Query: `userId`(Long), `userCouponId`(Long), `orderNo`(String), `orderAmount`(Integer), `orderType?`, `sellerId?` | `int` (抵扣金额，分) | ✅ V4.2 |
| I21 | POST | `/internal/coupon/rollback` | Query: `orderNo`(String)                                                                                          | `void`         | ✅      |
| I22 | POST | `/internal/coupon/grant`    | Query: `userId`(Long), `couponId`(String)                                                                         | `void` (支付后发券) | ✅ V4.4 |

#### 4.14.9 InternalLogisticsController — `/internal/logistics` (logistics-service)

| #   | 方法   | 路径                                     | 请求                                                | 响应 (原始) | 阶段  |
| --- | ---- | -------------------------------------- | ------------------------------------------------- | ------- | --- |
| I23 | POST | `/internal/logistics/create`           | `CreateLogisticsDTO`                              | `void`  | ✅   |
| I24 | PUT  | `/internal/logistics/{orderId}/status` | Path: `orderId` (Long), Query: `status` (Integer) | `void`  | ✅   |

#### 4.14.10 InternalSearchController — `/internal/search` (search-service)

| #   | 方法   | 路径                         | 请求                                                   | 响应 (原始)                 | 阶段  |
| --- | ---- | -------------------------- | ---------------------------------------------------- | ----------------------- | --- |
| I25 | POST | `/internal/search/reindex` | —                                                    | `Map<String,Object>`    | ✅   |
| I26 | POST | `/internal/search/vector`  | `VectorSearchRequest { embedding: [Double], size? }` | `List<ProductSearchVO>` | ✅   |

#### 4.14.11 WebSocketNotifyController — `/internal/ws` (gateway-service)

| #   | 方法   | 路径                          | 请求                                                                   | 响应 (原始)                       | 阶段  |
| --- | ---- | --------------------------- | -------------------------------------------------------------------- | ----------------------------- | --- |
| I27 | POST | `/internal/ws/order/status` | `OrderStatusChangedEvent { orderNo, oldStatus, newStatus, message }` | `void` (当前仅日志；未来推送 WebSocket) | 🔵  |

---

### 4.15 发票服务 (🆕 V5.0 计划 — 京东标准)

| #    | 方法   | 路径                     | 认证  | 请求                                                            | 响应                 | 阶段     |
| ---- | ---- | ---------------------- | --- | ------------------------------------------------------------- | ------------------ | ------ |
| INV1 | POST | `/api/invoice/apply`   | 🔒  | `{ orderNo, type: PERSONAL\|COMPANY, title, taxNo?, email? }` | `InvoiceVO`        | ⚪ V5.0 |
| INV2 | GET  | `/api/invoice/history` | 🔒  | Query: `page`(1), `size`(20)                                  | `IPage<InvoiceVO>` | ⚪ V5.0 |
| INV3 | GET  | `/api/invoice/{id}`    | 🔒  | Path: `id` (Long)                                             | `InvoiceVO`        | ⚪ V5.0 |

---

## 五、WebSocket / 实时 API

### 5.1 连接

```
ws://localhost:8080/ws/chat
```

通过 STOMP over WebSocket。客户端需携带 JWT Token 进行认证握手。

### 5.2 订阅 (客户端 → 服务端)

| 目标                       | 说明                     |
| ------------------------ | ---------------------- |
| `/user/queue/messages`   | 接收发给当前用户的实时消息          |
| `/topic/order/{orderNo}` | 订阅订单状态变更通知（🆕 V5.0 计划） |

### 5.3 发送 (客户端 → 服务端)

| 目标               | 消息体           | 说明        |
| ---------------- | ------------- | --------- |
| `/app/chat.send` | `ChatMessage` | 发送消息给指定用户 |

### 5.4 SSE 流式响应 (AI)

```
GET /api/ai/chat/stream
Content-Type: text/event-stream

data: {"token": "为您"}
data: {"token": "找到"}
...
data: [DONE]
```

---

## 六、Gateway 路由速查

| 路径前缀                           | 目标服务                  | 认证              |
| ------------------------------ | --------------------- | --------------- |
| `/api/auth/**`, `/oauth2/**`   | authorization-service | 公开              |
| `/api/user/**`, `/user/**`     | user-service          | 需要 JWT (部分公开)   |
| `/api/item/**`, `/api/home/**` | item-service          | 浏览公开            |
| `/api/shop/**`                 | user-service          | 浏览公开            |
| `/api/cart/**`                 | cart-service          | 需要 JWT          |
| `/api/trade/**`                | trade-service         | 需要 JWT          |
| `/api/pay/**`                  | pay-service           | 回调公开            |
| `/api/logistics/**`            | logistics-service     | 公开              |
| `/api/search/**`               | search-service        | 公开              |
| `/api/coupon/**`               | marketing-service     | 需要 JWT (模板公开)   |
| `/api/flash/**`                | marketing-service     | 浏览公开            |
| `/api/ai/**`                   | ai-service            | 需要 JWT          |
| `/api/chat/**`                 | user-service          | 需要 JWT          |
| `/api/after-sale/**`           | trade-service         | 需要 JWT          |
| `/api/admin/**`                | 多服务                   | 需要 JWT (admin)  |
| `/api/seller/**`               | 多服务                   | 需要 JWT (seller) |
| `/api/invoice/**`              | (🆕 invoice-service)  | 需要 JWT          |
| `/internal/**`                 | —                     | 网关拦截，仅内部        |
| `/ws/**`                       | gateway-service       | WebSocket 升级    |

**路由规则**：精确路径优先匹配，兜底路由放最后。

---

## 七、前端页面 ↔ API 映射

> 此映射表供 Next.js 前端开发对照使用。按页面路由组织，列出每个页面需要调用的 API。

### 7.1 公共页面

| 前端页面    | 路由                 | API 调用                                                                                                                                                          |
| ------- | ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 首页      | `/`                | `GET /api/home/config`, `GET /api/flash`, `GET /api/item/product/page`                                                                                          |
| 商品搜索/列表 | `/search`          | `GET /api/search/product`, `GET /api/search/hot`, `GET /api/search/suggest`                                                                                     |
| 商品分类    | `/categories`      | `GET /api/item/category/tree`                                                                                                                                   |
| 商品详情    | `/product/[id]`    | `GET /api/item/product/{id}/detail`, `GET /api/item/product/{id}/skus`, `GET /api/item/review/product/{id}/filter`, `GET /api/item/review/product/{id}/summary` |
| 商家店铺页   | `/shop/[sellerId]` | `GET /api/shop/{sellerId}`, `GET /api/shop/{sellerId}/products`, `GET /api/shop/follow/{sellerId}/status`, `GET /api/shop/follow/{sellerId}/count`              |
| 登录      | `/login`           | `POST /api/auth/login`, `POST /api/auth/login/wechat`                                                                                                           |
| 注册      | `/register`        | `POST /api/user/code`, `POST /api/auth/register`                                                                                                                |
| 找回密码    | `/reset-password`  | `POST /api/auth/reset-password`                                                                                                                                 |

### 7.2 用户端页面 (需登录)

| 前端页面    | 路由                       | API 调用                                                                                                                                                                        |
| ------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 个人中心    | `/user`                  | `GET /api/user/info`                                                                                                                                                          |
| 个人资料编辑  | `/user/profile`          | `PATCH /api/user/profile`, `POST /api/user/profile/avatar`                                                                                                                    |
| 地址管理    | `/user/address`          | `GET /api/user/address/list`, `POST /api/user/address/add`, `PUT /api/user/address/update/{id}`, `DELETE /api/user/address/delete/{id}`, `PUT /api/user/address/default/{id}` |
| 我的订单    | `/user/orders`           | `GET /api/trade/order/page`, `POST /api/trade/order/{orderNo}/cancel`, `POST /api/trade/order/{orderNo}/confirm`                                                              |
| 订单详情    | `/user/orders/[orderNo]` | `GET /api/trade/order/{orderNo}`, `GET /api/logistics/{orderId}`                                                                                                              |
| 购物车     | `/cart`                  | `GET /api/cart`, `GET /api/cart/grouped`, `POST /api/cart/item`, `PUT /api/cart/item`, `DELETE /api/cart/item/{skuId}`, `PATCH /api/cart/item/{skuId}/selected`               |
| 结算/确认订单 | `/checkout`              | `GET /api/user/address/list`, `GET /api/cart`, `POST /api/coupon/available/filter`                                                                                            |
| 支付      | `/pay/[orderNo]`         | `POST /api/pay/order/{orderNo}`, `GET /api/pay/order/{orderNo}/status`                                                                                                        |
| 支付结果    | `/pay/result/[orderNo]`  | `GET /api/pay/order/{orderNo}/status`, `GET /api/trade/order/{orderNo}`                                                                                                       |
| 我的收藏    | `/user/favorites`        | `GET /api/user/favorite`, `DELETE /api/user/favorite`                                                                                                                         |
| 浏览历史    | `/user/history`          | `GET /api/user/history`, `DELETE /api/user/history`                                                                                                                           |
| 签到      | `/user/sign`             | `POST /api/user/sign`, `GET /api/user/sign/status`                                                                                                                            |
| 优惠券     | `/user/coupons`          | `GET /api/coupon/available`, `GET /api/coupon/used`, `GET /api/coupon/count`                                                                                                  |
| 领券中心    | `/coupons`               | `GET /api/coupon/template`, `POST /api/coupon/claim`                                                                                                                          |
| 积分      | `/user/points`           | `GET /api/user/points/balance`, `GET /api/user/points/history`                                                                                                                |
| 余额      | `/user/balance`          | `GET /api/user/balance`, `POST /api/user/balance/recharge`                                                                                                                    |
| 售后申请    | `/after-sale/apply`      | `POST /api/after-sale`                                                                                                                                                        |
| 售后列表    | `/user/after-sales`      | `GET /api/after-sale`                                                                                                                                                         |
| 账号安全    | `/user/security`         | `PUT /api/user/security/password`, `PUT /api/user/security/phone`                                                                                                             |
| 消息中心    | `/user/notifications`    | `GET /api/user/notifications`, `GET /api/user/notifications/unread-count`                                                                                                     |
| 聊天/客服   | `/chat`                  | WS `/ws/chat`, `GET /api/chat/conversations`, `GET /api/chat/conversations/{id}/messages`                                                                                     |
| AI 助手   | `/ai`                    | `POST /api/ai/chat/stream`, `GET /api/ai/conversations`                                                                                                                       |
| 秒杀      | `/flash`                 | `GET /api/flash`, `GET /api/flash/timeline`, `POST /api/flash/buy`                                                                                                            |
| 发票      | `/user/invoices`         | `POST /api/invoice/apply`, `GET /api/invoice/history`                                                                                                                         |

### 7.3 商家端页面 (需登录 + SELLER 角色)

| 前端页面  | 路由                    | API 调用                                                                                                                           |
| ----- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 商家仪表盘 | `/seller`             | `GET /api/trade/seller/dashboard`, `GET /api/trade/seller/dashboard/product-stats`                                               |
| 商品管理  | `/seller/products`    | `GET /api/item/product/page`, `POST /api/item/product`, `PUT /api/item/product/{id}`, `PUT /api/item/product/{id}/status`        |
| 订单管理  | `/seller/orders`      | `GET /api/trade/seller/order/page`, `GET /api/trade/seller/order/{orderNo}`, `POST /api/trade/seller/order/{orderNo}/ship`       |
| 店铺设置  | `/seller/shop`        | `GET /api/seller/shop`, `PUT /api/seller/shop`                                                                                   |
| 优惠券管理 | `/seller/coupons`     | `POST /api/seller/coupon`, `GET /api/seller/coupon`, `PUT /api/seller/coupon/{couponId}/disable`                                 |
| 结算管理  | `/seller/finance`     | `GET /api/seller/finance/settlement/page`, `GET /api/seller/finance/balance`, `POST /api/seller/finance/withdrawal`              |
| 售后处理  | `/seller/after-sales` | `GET /api/admin/after-sale`, `PUT /api/admin/after-sale/{id}/review`                                                             |
| 回复模板  | `/seller/templates`   | `GET /api/seller/templates`, `POST /api/seller/templates`, `PUT /api/seller/templates/{id}`, `DELETE /api/seller/templates/{id}` |
| 知识库   | `/seller/knowledge`   | `GET /api/seller/knowledge`, `POST /api/seller/knowledge`, `PUT /api/seller/knowledge/{id}`, `DELETE /api/seller/knowledge/{id}` |
| 商家入驻  | `/seller/apply`       | `POST /api/seller/apply`, `GET /api/seller/apply`                                                                                |

### 7.4 管理端页面 (需登录 + ADMIN 角色)

| 前端页面  | 路由                   | API 调用                                                                                                                                                                             |
| ----- | -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 管理仪表盘 | `/admin`             | `GET /api/trade/admin/dashboard`, `GET /api/trade/admin/stats/trend`, `GET /api/trade/admin/stats/sales`                                                                           |
| 用户管理  | `/admin/users`       | `GET /api/admin/user/page`, `PUT /api/admin/user/{id}/status`, `PUT /api/admin/user/{id}/role`                                                                                     |
| 商家审核  | `/admin/sellers`     | `GET /api/admin/seller/pending`, `PUT /api/admin/seller/{id}/approve`, `GET /api/admin/application`                                                                                |
| 商品管理  | `/admin/products`    | `GET /api/admin/product/page`, `PUT /api/admin/product/{id}/status`                                                                                                                |
| 订单管理  | `/admin/orders`      | `GET /api/trade/admin/orders/page`                                                                                                                                                 |
| 分类管理  | `/admin/categories`  | `POST /api/item/category`, `PUT /api/item/category/{id}`, `DELETE /api/item/category/{id}`                                                                                         |
| 首页装修  | `/admin/home`        | `GET /api/admin/home`, `POST /api/admin/home`, `PUT /api/admin/home/{id}`, `DELETE /api/admin/home/{id}`                                                                           |
| 优惠券管理 | `/admin/coupons`     | `POST /api/admin/coupon`, `GET /api/admin/coupon`, `PUT /api/admin/coupon/{couponId}/disable`                                                                                      |
| 秒杀管理  | `/admin/flash`       | `POST /api/admin/flash`, `PUT /api/admin/flash/{id}`, `DELETE /api/admin/flash/{id}`                                                                                               |
| 售后管理  | `/admin/after-sales` | `GET /api/admin/after-sale`, `PUT /api/admin/after-sale/{id}/review`                                                                                                               |
| 财务管理  | `/admin/finance`     | `POST /api/admin/finance/settlement/generate`, `GET /api/admin/finance/settlement/page`, `GET /api/admin/finance/withdrawal/page`, `PUT /api/admin/finance/withdrawal/{id}/review` |
| 角色权限  | `/admin/roles`       | `GET /api/admin/roles`, `POST /api/admin/roles`, `PUT /api/admin/roles/{id}`, `DELETE /api/admin/roles/{id}`, `GET /api/admin/permissions/tree`                                    |
| 操作日志  | `/admin/logs`        | `GET /api/admin/log`                                                                                                                                                               |
| 品牌管理  | `/admin/brands`      | `POST /api/item/brand`, `PUT /api/item/brand/{id}`, `DELETE /api/item/brand/{id}`                                                                                                  |

---

## 八、核心 DTO/VO 快速参考

### 8.1 认证

```json
// OAuth2TokenResp (snake_case)
{ "access_token":"eyJ...", "token_type":"Bearer", "expires_in":1800, "refresh_token":"xxx", "user_id":1, "username":"buy123" }

// SessionStatus
{ "valid": true, "ttlSeconds": 1500, "idleTimeoutMinutes": 30 }
```

### 8.2 用户

```json
// UserInfoResp
{ "userId":"usr_abc", "username":"buyer001", "phone":"138****8000", "avatar":"https://...", "status":1, "registerTime":"...", "balance":50000, "roleType":0 }

// AddressResp
{ "id":1, "userId":1001, "receiver":"张三", "phone":"13800138000", "province":"北京市", "city":"北京市", "district":"朝阳区", "street":"望京街道", "detail":"SOHO T1 1205", "defaulted":true, "label":"公司", "createTime":"...", "updateTime":"..." }

// SignResultVO
{ "signed":true, "earnedPoints":10, "monthCount":15, "continuousDays":5 }
```

### 8.3 商品

```json
// ProductVO
{ "id":1, "productId":"prod-001", "sellerId":5, "shopName":"冰美精选陶瓷", "categoryId":1, "name":"手工陶瓷茶杯", "mainImage":"https://...", "images":[...], "description":"...", "brand":"冰美精选", "soldCount":1280, "commentCount":86, "rating":4.7, "status":1, "salesTags":["热销"], "publishTime":"...", "skus":[...] }

// SkuVO
{ "id":1, "skuId":"sku-001", "productId":1, "spec":"天青/标准", "price":12800, "originalPrice":15800, "stock":50, "stockType":1, "isHot":true, "image":"https://...", "soldCount":320, "status":1 }

// ProductDetailVO
{ "product":ProductVO, "shop":{...}, "skus":[...], "specDimensions":[...], "reviewSummary":{...}, "serviceTags":[...], "availableCoupons":[...] }

// CategoryTreeVO
{ "id":1, "name":"陶瓷器具", "level":1, "sortOrder":1, "children":[...] }
```

### 8.4 购物车

```json
// CartVO
{ "items":[...], "totalPrice":38400, "selectedPrice":25600, "allSelected":false }

// CartItemResp
{ "skuId":10, "productId":1, "productName":"手工陶瓷茶杯", "spec":"天青/标准", "image":"https://...", "price":12800, "originalPrice":15800, "quantity":2, "selected":true, "subTotal":25600, "stock":50, "status":1 }

// CartGroupedVO
{ "groups":[{ "sellerId":5, "shopName":"...", "items":[...], "shopTotalPrice":25600 }], "totalPrice":38400, "selectedPrice":25600 }
```

### 8.5 订单

```json
// CreateOrderReq
{ "addressId":1, "cartItemIds":[10,11], "userCouponIds":[5,8], "remark":"请发顺丰" }

// DirectOrderReq
{ "skuId":10, "quantity":1, "addressId":1, "userCouponIds":[5] }

// OrderVO
{ "id":1, "orderNo":"ord-...", "orderType":1, "userId":1001, "sellerId":5, "shopName":"...", "totalAmount":25600, "discountAmount":2000, "payAmount":23600, "status":1, "statusText":"待付款", "paymentType":1, "receiverName":"张三", "receiverPhone":"138...", "receiverAddress":"...", "remark":"...", "createTime":"...", "payTime":null, "consignTime":null, "endTime":null, "payTimeoutAt":"...", "items":[...] }

// OrderItemVO
{ "id":1, "skuId":10, "productId":1, "productName":"手工陶瓷茶杯", "skuSpec":"天青/标准", "price":12800, "quantity":2, "subTotal":25600, "image":"https://..." }
```

### 8.6 支付

```json
// PayOrderVO
{ "payOrderNo":"pay-...", "bizOrderNo":"ord-...", "amount":23600, "payChannelCode":"WECHAT", "status":1, "statusText":"待支付", "qrCodeUrl":"weixin://...", "payUrl":"https://...", "createTime":"...", "payTime":null, "expireTime":"..." }
```

### 8.7 物流

```json
// LogisticsVO
{ "orderId":1, "orderNo":"ord-...", "logisticsNumber":"SF1234567890", "logisticsCompany":"顺丰速运", "address":"...", "status":2, "statusText":"运输中", "traces":[{ "time":"...", "status":"已揽收", "detail":"..." }] }
```

### 8.8 搜索

```json
// ProductSearchVO
{ "id":1, "productId":"prod-001", "categoryId":1, "name":"手工陶瓷茶杯", "description":"...", "brand":"冰美精选", "mainImage":"https://...", "price":12800, "originalPrice":15800, "soldCount":1280, "rating":4.7, "shopName":"...", "salesTags":["热销"] }
```

### 8.9 营销

```json
// CouponVO (模板)
{ "couponId":"cpn-001", "name":"618满200减30", "discountType":1, "couponCategory":1, "scopeType":1, "priceInCents":3000, "minAmountInCents":20000, "startTime":"...", "endTime":"...", "totalStock":10000, "claimedCount":6523, "status":1 }

// UserCouponVO (已领取)
{ "userCouponId":5, "couponId":"cpn-001", "name":"618满200减30", "discountType":1, "couponCategory":1, "scopeType":1, "priceInCents":3000, "minAmountInCents":20000, "status":1, "statusText":"可用", "claimTime":"...", "expireTime":"..." }

// FlashSaleVO
{ "id":1, "skuId":10, "productName":"手工陶瓷茶杯", "productImage":"https://...", "flashPrice":9900, "originalPrice":12800, "stock":100, "soldCount":35, "startTime":"...", "endTime":"...", "status":1, "progressPercent":35 }

// FlashBuyVO
{ "orderNo":"ord-xxx", "flashId":1, "flashPrice":9900, "payAmount":9900, "status":1 }
```

### 8.10 AI

```json
// AiChatRequest
{ "message":"500以内适合夏天穿的透气跑鞋", "conversationId":"conv-uuid-xxx" }

// AiChatResponse
{ "reply":"为您找到3款...", "conversationId":"conv-uuid-xxx", "products":[...] }
```

### 8.11 消息

```json
// ChatMessage (STOMP)
{ "messageId":"uuid", "conversationId":"buyer_1001_seller_5_product_P001", "senderId":1001, "senderRole":"BUYER", "senderName":"买家张三", "content":"这个商品还有货吗？", "contentType":"TEXT", "timestamp":"..." }

// ChatConversationVO
{ "conversationId":"...", "targetUserId":5, "targetUserName":"冰美精选陶瓷", "productName":"手工陶瓷茶杯", "lastMessage":"好的，今天发货", "lastMessageTime":"...", "unreadCount":2 }
```

---

## 九、订单状态流转 (Order State Machine)

```
┌──────────────┐    支付成功     ┌──────────────┐    商家发货     ┌──────────────┐
│   PENDING_PAY │──────────────→│ PENDING_SHIP │──────────────→│PENDING_RECEIPT│
│    (待付款)    │               │   (待发货)    │               │   (待收货)     │
│   status=1    │               │   status=2   │               │   status=3    │
└──────┬───────┘               └──────────────┘               └──────┬───────┘
       │                                                             │
       │ 超时30min / 用户取消                                          │ 用户确认收货
       ↓                                                             ↓
┌──────────────┐                                            ┌──────────────┐
│   CANCELLED   │                                            │  COMPLETED   │
│    (已取消)    │                                            │   (已完成)    │
│   status=5    │                                            │   status=4   │
└──────────────┘                                            └──────┬───────┘
                                                                    │
                                                                    │ 用户评价后
                                                                    ↓
                                                            ┌──────────────┐
                                                            │PENDING_REVIEW │
                                                            │   (待评价)    │
                                                            │   status=6   │
                                                            └──────────────┘
```

**状态值映射**:

| status | 状态文本 | 可执行操作          |
| ------ | ---- | -------------- |
| 1      | 待付款  | 取消、支付          |
| 2      | 待发货  | (等待商家)         |
| 3      | 待收货  | 确认收货、延长收货、查看物流 |
| 4      | 已完成  | 评价、再次购买、申请售后   |
| 5      | 已取消  | 删除、再次购买        |
| 6      | 待评价  | 发表评价           |

---

## 十、变更记录

| 版本       | 日期             | 变更内容                                                                                                                                                                                                                                                                                      |
| -------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V4.5     | 2026-07-31     | 优惠券 V4.5 去重保护；文档全面更新至 175 端点                                                                                                                                                                                                                                                              |
| **V5.0** | **2026-08-02** | **全面重构：新增 35+ 端点，完善 DTO 定义，补充前端页面映射，补齐京东标准功能**                                                                                                                                                                                                                                            |
|          |                | **新增端点**：账号安全(U24-U27)、通知中心(U28-U32)、评论增强(P16-P18)、品牌管理(P19-P22)、商品对比(P24)、到货通知(P25-P26)、购物车批量操作(C8-C10)、订单生命周期(T7-T9)、商家改价(T13-T14)、售后增强(T17-T19)、支付历史(PAY5)、搜索建议(SE5)+清除历史(SE4)、优惠券计数(M7)、秒杀详情(M9)+时间线(M11)、营销活动(M12-M13)、聊天已读(CH5)、发票(INV1-INV3)、管理员商品管理(AD38-AD39)、管理员秒杀管理(AD40-AD42) |
|          |                | **文档增强**：全部端点补充认证标识(🌐🔒🔑🏪🔌)、完善 DTO/VO JSON 示例、新增前端页面↔API映射表、补充订单状态流转图、补充业务错误码速查、补充 API 版本策略、补充日期时间格式约定                                                                                                                                                                                |

---

> **文档维护规则**：
> 
> 1. 新增/修改 API 端点时，必须同步更新本文档
> 2. DTO/VO 字段变更时，必须更新 §八 的 JSON 示例
> 3. 前端页面变更时，必须更新 §七 的映射表
> 4. 错误码新增时，必须更新 §二 的错误码表
> 5. 本文档是前后端唯一契约——后端 Controller 实现必须与本文档一致，前端 TanStack Query hook 必须基于本文档定义
