# 05 — 接口规格文档 (API Specification — SDD)

> 最后更新: 2026-07-31 | 端点总数: 175 | 公共: 160 | 内部: 27 | 已实现: 174

---

## 一、API 设计约定

### 1.1 URL 规范

```
/{领域资源}/{动作或子资源}
```

公共端点通过 Gateway 统一入口 (`/api/*`)。内部 Feign 端点走 `/internal/*`，不经 Gateway，不包装 Result。

### 1.2 HTTP 方法

| 方法     | 用途          | 幂等  |
| ------ | ----------- | --- |
| GET    | 查询资源        | ✅   |
| POST   | 创建资源 / 业务动作 | ❌   |
| PUT    | 全量更新        | ✅   |
| PATCH  | 部分更新        | ✅   |
| DELETE | 删除          | ✅   |

### 1.3 统一响应 `Result<T>`

所有公共端点返回:

```json
{ "code": 200, "msg": "success", "data": { ... } }
```

`/internal/**` 路径**不包装** Result，异常直抛。

### 1.4 分页

```json
// GET /xxx/page?page=1&size=20&sort=create_time,desc
{ "code": 200, "data": { "records": [...], "total": 100, "size": 20, "current": 1, "pages": 5 } }
```

---

## 二、错误码

| 错误码   | HTTP | 含义      |
| ----- | ---- | ------- |
| 200   | 200  | 成功      |
| 40000 | 400  | 参数校验失败  |
| 40001 | 400  | 业务规则不满足 |
| 40100 | 401  | 未认证     |
| 40300 | 403  | 无权限     |
| 40400 | 404  | 资源不存在   |
| 40900 | 409  | 冲突      |
| 42900 | 429  | 限流      |
| 50000 | 500  | 系统错误    |

---

## 三、认证

### Token

| 类型                       | 存储             | 有效期    |
| ------------------------ | -------------- | ------ |
| Access Token (JWT RS256) | 前端内存           | 30 min |
| Refresh Token            | Redis + Cookie | 7 days |

### 请求头

```
Authorization: Bearer <JWT>
```

---

## 四、端点目录

> 标记: ✅ MVP | 🔵 V1.1 | ⚪ V2.0+  
> 返回类型列: 公共端点写 `Result<T>` 的 inner T，内部端点写原始返回类型

---

### 4.1 认证服务 (authorization-service)

#### AuthController — `/api/auth`

| #   | 方法   | 路径                         | 请求                                                                                                                            | 响应                                                                                                                 | 阶段  |
| --- | ---- | -------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | --- |
| 1   | POST | `/api/auth/login`          | `{ identityType:"PHONE"\|"USERNAME"\|"EMAIL", credentialType:"PASSWORD"\|"SMS_CODE", account:"138...", credential:"123456" }` | `OAuth2TokenResp` (snake_case JSON: `access_token`,`token_type`,`expires_in`,`refresh_token`,`user_id`,`username`) | ✅   |
| 2   | POST | `/api/auth/register`       | `{ phone, password, code, username? }`                                                                                        | `OAuth2TokenResp`                                                                                                  | ✅   |
| 3   | POST | `/api/auth/refresh`        | Query: `refresh_token`                                                                                                        | `OAuth2TokenResp`                                                                                                  | ✅   |
| 4   | POST | `/api/auth/logout`         | Query: `refresh_token`                                                                                                        | `Void`                                                                                                             | ✅   |
| —   | POST | `/api/auth/reset-password` | `{ phone, code, newPassword }`                                                                                                | `Void`（找回密码，公开接口，经 UserClient→`/internal/user/reset-password`）                                                     | ✅   |
| —   | POST | `/api/auth/login/wechat`   | `{ code }`                                                                                                                    | `OAuth2TokenResp`（微信 OAuth 登录，公开；经 `WechatLoginStrategy`→UserClient→`/internal/user/login/wechat`；未配置时走 Mock 虚拟 openid） | ✅   |

#### JwkSetController — `/oauth2/jwks`

| #   | 方法  | 路径             | 请求  | 响应                                         | 阶段  |
| --- | --- | -------------- | --- | ------------------------------------------ | --- |
| 5   | GET | `/oauth2/jwks` | —   | `Map<String,Object>` (JWK Set, 不包装 Result) | ✅   |

---

### 4.2 用户服务 (user-service)

#### UserController — `/user` (Gateway: `/api/user/*`)

| #   | 方法   | 路径              | 请求                                                                                                   | 响应                                                                                                          | 阶段  |
| --- | ---- | --------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- | --- |
| 6   | GET  | `/user/info`    | — (JWT)                                                                                              | `UserInfoResp { userId(String), username, phone, avatar, status(Integer), registerTime, balance(Integer) }` | ✅   |
| 7   | PATCH | `/user/profile` | `UpdateProfileReq { nickname?, avatar?, updateMask? }` (updateMask 白名单: `["nickname","avatar"]`) | `UserInfoResp` (全量返回)                         | ✅ V3.2 |
| 8   | POST | `/user/profile/avatar` | `MultipartFile`                                                                    | `String` (头像 URL)                                       | ✅ V3.2 |
| 9   | GET  | `/user/profile/avatar` | `?width=200&height=200`                                                           | `image/jpeg` (缩略图, Cache-Control: 1h)                  | ✅ V3.2 |
| 8   | POST | `/user/code`    | `{ phone, requestId, captchaTicket, captchaRandStr?, lotNumber, captchaOutput, passToken, genTime }` | `Void`                                                                                                      | ✅   |

#### AddressController — `/user/address` (Gateway: `/api/user/address/*`)

| #   | 方法     | 路径                           | 请求                                                                                                         | 响应                  | 阶段  |
| --- | ------ | ---------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------- | --- |
| 9   | GET    | `/user/address/list`         | — (JWT)                                                                                                    | `List<AddressResp>` | ✅   |
| 10  | POST   | `/user/address/add`          | `{ receiver, phone, province, city, district, street, detail, defaulted?, label?, longitude?, latitude? }` | `Void`              | ✅   |
| 11  | GET    | `/user/address/{id}`         | Path: `id` (Long)                                                                                          | `AddressResp`       | ✅   |
| 12  | PUT    | `/user/address/update/{id}`  | Path: `id` (Long), Body: 同 add                                                                             | `Void`              | ✅   |
| 13  | PUT    | `/user/address/default/{id}` | Path: `id` (Long)                                                                                          | `Void`              | ✅   |
| 14  | DELETE | `/user/address/delete/{id}`  | Path: `id` (Long)                                                                                          | `Void`              | ✅   |

AddressResp: `{ id(Long), userId(Long), receiver, phone, province, city, district, street, detail, defaulted(Boolean), label?, longitude?, latitude?, createTime, updateTime }`

#### SignController — `/user/sign`

| #   | 方法   | 路径                  | 请求      | 响应                   | 阶段  |
| --- | ---- | ------------------- | ------- | -------------------- | --- |
| 15  | POST | `/user/sign`        | — (JWT) | `SignResultVO { signed(boolean), earnedPoints(long), monthCount(long), continuousDays(long) }` | ✅   |
| 16  | GET  | `/user/sign/status` | — (JWT) | `SignResultVO` | ✅   |

#### BalanceController — `/user/balance`（V2.3 余额支付）

| #   | 方法   | 路径                       | 请求                     | 响应                | 阶段  |
| --- | ---- | ------------------------ | ---------------------- | ----------------- | --- |
| —   | GET  | `/user/balance`          | — (JWT)                | `Integer`（余额，分）   | ✅ V2.3 |
| —   | POST | `/user/balance/recharge` | Query: `amount` (int，分) | `Integer`（最新余额）；简易充值/Mock | ✅ V2.3 |


#### FavoriteController — `/user/favorite`

| #   | 方法     | 路径               | 请求                           | 响应                      | 阶段  |
| --- | ------ | ---------------- | ---------------------------- | ----------------------- | --- |
| 17  | POST   | `/user/favorite` | Query: `productId` (Long)    | `Void`                  | 🔵  |
| 18  | DELETE | `/user/favorite` | Query: `productId` (Long)    | `Void`                  | 🔵  |
| 19  | GET    | `/user/favorite` | Query: `page`(1), `size`(20) | `IPage<FavoriteEntity>` | 🔵  |

#### HistoryController — `/user/history`

| #   | 方法     | 路径              | 请求                        | 响应                         | 阶段  |
| --- | ------ | --------------- | ------------------------- | -------------------------- | --- |
| 20  | POST   | `/user/history` | Query: `productId` (Long) | `Void`                     | 🔵  |
| 21  | GET    | `/user/history` | Query: `size`(20)         | `List<Long>` (product ids) | 🔵  |
| 22  | DELETE | `/user/history` | — (JWT)                   | `Void`                     | 🔵  |

#### PointsController — `/user/points`

| #   | 方法  | 路径                     | 请求                           | 响应                   | 阶段  |
| --- | --- | ---------------------- | ---------------------------- | -------------------- | --- |
| 23  | GET | `/user/points/balance` | — (JWT)                      | `Map<String,Object>` | 🔵  |
| 24  | GET | `/user/points/history` | Query: `page`(1), `size`(20) | `IPage<?>`           | 🔵  |

#### SellerController — `/api/seller`

| #   | 方法   | 路径                     | 请求                                                                                     | 响应             | 阶段  |
| --- | ---- | ---------------------- | -------------------------------------------------------------------------------------- | -------------- | --- |
| 25  | POST | `/api/seller/register` | `{ shopName, contactPhone, province?, city?, district?, detailAddress? }`              | `Void`         | ✅   |
| 26  | GET  | `/api/seller/shop`     | — (JWT)                                                                                | `SellerEntity` | ✅   |
| 27  | PUT  | `/api/seller/shop`     | `{ shopName?, shopLogo?, contactPhone?, province?, city?, district?, detailAddress? }` | `Void`         | ✅   |

#### AdminUserController — `/api/admin`

| #   | 方法  | 路径                               | 请求                                             | 响应         | 阶段  |
| --- | --- | -------------------------------- | ---------------------------------------------- | ---------- | --- |
| 28  | GET | `/api/admin/user/page`           | Query: `page`(1), `size`(20)                   | `IPage<?>` | ✅   |
| 29  | PUT | `/api/admin/user/{id}/status`    | Path: `id` (Long), Query: `status` (Integer)   | `Void`     | ✅   |
| 30  | PUT | `/api/admin/user/{id}/role`      | Path: `id` (Long), Query: `roleType` (Integer) | `Void`     | ✅   |
| 31  | GET | `/api/admin/seller/pending`      | Query: `page`(1), `size`(20)                   | `IPage<?>` | ✅   |
| 32  | PUT | `/api/admin/seller/{id}/approve` | Path: `id` (Long)                              | `Void`     | ✅   |

#### AdminLogController — `/api/admin/log`

| #   | 方法  | 路径               | 请求                           | 响应                         | 阶段  |
| --- | --- | ---------------- | ---------------------------- | -------------------------- | --- |
| 33  | GET | `/api/admin/log` | Query: `page`(1), `size`(20) | `List<Map<String,Object>>` | 🔵  |

#### RoleController — `/api/admin/roles` (V3.1 RBAC)

| #   | 方法    | 路径                               | 请求                                         | 响应                    | 阶段  |
| --- | ----- | -------------------------------- | ------------------------------------------ | --------------------- | --- |
| 34  | GET   | `/api/admin/roles`               | —                                          | `List<Role>`          | ✅ V3.1 |
| 35  | POST  | `/api/admin/roles`               | `{ name, code, description }`              | `Role`                | ✅ V3.1 |
| 36  | PUT   | `/api/admin/roles/{id}`          | `{ name?, description? }`                  | `Void`                | ✅ V3.1 |
| 37  | DELETE | `/api/admin/roles/{id}`         | — (is_system=1 的系统角色不可删除)             | `Void`                | ✅ V3.1 |
| 38  | GET   | `/api/admin/roles/{id}/permissions` | —                                      | `List<Long>` (权限ID)  | ✅ V3.1 |
| 39  | POST  | `/api/admin/roles/{id}/permissions` | `{ permissionIds: [1,2,3] }`            | `Void`                | ✅ V3.1 |

#### PermissionController — `/api/admin/permissions` (V3.1 RBAC)

| #   | 方法  | 路径                          | 请求 | 响应                     | 阶段  |
| --- | --- | --------------------------- | -- | ---------------------- | --- |
| 40  | GET | `/api/admin/permissions`     | —  | `List<Permission>`     | ✅ V3.1 |
| 41  | GET | `/api/admin/permissions/tree` | — | `List<PermissionTree>` | ✅ V3.1 |

#### InternalUserController — `/internal/user` (Feign)

| #   | 方法   | 路径                              | 请求                                               | 响应 (原始)                       | 阶段  |
| --- | ---- | ------------------------------- | ------------------------------------------------ | ----------------------------- | --- |
| 34  | POST | `/internal/user/register`       | `{ username?, phone, password, code, deviceId }` | `LoginRespDTO`                | ✅   |
| 35  | POST | `/internal/user/login/password` | `{ phone?, username?, password }`                | `LoginRespDTO`                | ✅   |
| 36  | POST | `/internal/user/login/sms`      | `{ phone, code }`                                | `LoginRespDTO`                | ✅   |
| —   | POST | `/internal/user/reset-password` | `{ phone, code, newPassword }`                   | `void`（找回密码，校验短信码后 BCrypt 重写） | ✅   |
| —   | POST | `/internal/user/login/wechat`   | `{ code }`                                       | `LoginRespDTO`（微信登录：code→openid→查/建用户） | ✅   |
| —   | POST | `/internal/user/balance/deduct` | Query: `userId`(Long), `amount`(Integer)         | `void`（余额支付原子扣减；余额不足抛异常透传） | ✅ V2.3 |
| 37  | GET  | `/internal/user/address/{id}`   | Path: `id` (Long)                                | `AddressResp`                 | ✅   |
| 38  | GET  | `/internal/user/{id}`           | Path: `id` (Long)                                | `UserInfoResp`                | ✅   |
| 39  | GET  | `/internal/user/count`          | —                                                | `long`                        | ✅   |
| 40  | POST | `/internal/user/points/add`     | Query: `userId`, `points`, `type`(2), `source`   | `long`                        | 🔵  |

LoginRespDTO: `{ userId(Long), username(String), phone(String), role(String) }`

---

### 4.3 商品服务 (item-service)

#### ProductController — `/api/item/product`

| #   | 方法   | 路径                              | 请求                                                                                                  | 响应                 | 阶段  |
| --- | ---- | ------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------ | --- |
| 41  | GET  | `/api/item/product/page`        | Query: `categoryId?`, `keyword?`, `sort?`(sales\|price), `order?`(asc\|desc), `page`(1), `size`(20) | `IPage<ProductVO>` | ✅   |
| 42  | GET  | `/api/item/product/{id}`        | Path: `id` (Long)                                                                                   | `ProductVO`        | ✅   |
| 43  | POST | `/api/item/product`             | `{ categoryId, name, mainImage?, description?, brand?, skus: [{ spec, price, stock, image? }] }`    | `Void`             | ✅   |
| 44  | PUT  | `/api/item/product/{id}`        | Path: `id` (Long), Body: `{ name?, mainImage?, description?, brand?, categoryId? }`                 | `Void`             | ✅   |
| 45  | PUT  | `/api/item/product/{id}/status` | Path: `id` (Long), Query: `status` (Integer)                                                        | `Void`             | ✅   |

ProductVO: `{ id(Long), productId(String), sellerId(Long), categoryId(Long), name, mainImage, description, brand, soldCount, commentCount, status(Integer), publishTime, skus: List<SkuVO> }`
SkuVO: `{ id(Long), skuId(String), productId(Long), spec, price(Integer), stock(Integer), image, soldCount, status(Integer) }`

#### CategoryController — `/api/item/category`

| #   | 方法     | 路径                        | 请求                                       | 响应                     | 阶段  |
| --- | ------ | ------------------------- | ---------------------------------------- | ---------------------- | --- |
| 46  | GET    | `/api/item/category/tree` | —                                        | `List<CategoryTreeVO>` | ✅   |
| 47  | POST   | `/api/item/category`      | `{ name, sortOrder? }`                   | `Void`                 | ✅   |
| 48  | PUT    | `/api/item/category/{id}` | Path: `id` (Long), Body: category fields | `Void`                 | ✅   |
| 49  | DELETE | `/api/item/category/{id}` | Path: `id` (Long)                        | `Void`                 | ✅   |

CategoryTreeVO: `{ id(Long), name, sortOrder(Integer), children: List<CategoryTreeVO> }`

#### ReviewController — `/api/item/review`

| #   | 方法   | 路径                                     | 请求                                                     | 响应                    | 阶段  |
| --- | ---- | -------------------------------------- | ------------------------------------------------------ | --------------------- | --- |
| 50  | POST | `/api/item/review`                     | `{ orderId, productId, score, content? }`              | `ReviewEntity`        | 🔵  |
| 51  | GET  | `/api/item/review/product/{productId}` | Path: `productId` (Long), Query: `page`(1), `size`(10) | `IPage<ReviewEntity>` | 🔵  |

#### HomeController — `/api/home`

| #   | 方法  | 路径                 | 请求  | 响应                       | 阶段  |
| --- | --- | ------------------ | --- | ------------------------ | --- |
| 52  | GET | `/api/home/config` | —   | `List<HomeConfigEntity>` | 🔵  |

#### AdminHomeController — `/api/admin/home`

| #   | 方法     | 路径                     | 请求                                          | 响应                       | 阶段  |
| --- | ------ | ---------------------- | ------------------------------------------- | ------------------------ | --- |
| 53  | POST   | `/api/admin/home`      | `HomeConfigEntity`                          | `HomeConfigEntity`       | 🔵  |
| 54  | PUT    | `/api/admin/home/{id}` | Path: `id` (Long), Body: `HomeConfigEntity` | `HomeConfigEntity`       | 🔵  |
| 55  | DELETE | `/api/admin/home/{id}` | Path: `id` (Long)                           | `Void`                   | 🔵  |
| 56  | GET    | `/api/admin/home`      | —                                           | `List<HomeConfigEntity>` | 🔵  |

#### InternalSkuController — `/internal/item/sku` (Feign)

| #   | 方法   | 路径                              | 请求                            | 响应 (原始)        | 阶段  |
| --- | ---- | ------------------------------- | ----------------------------- | -------------- | --- |
| 57  | GET  | `/internal/item/sku/list/batch` | Query: `ids` (List\<Long\>)   | `List<SkuDTO>` | ✅   |
| 58  | POST | `/internal/item/sku/deduct`     | `[{ skuId(Long), quantity }]` | `void`         | ✅   |
| 59  | POST | `/internal/item/sku/restore`    | `[{ skuId(Long), quantity }]` | `void`         | ✅   |

SkuDTO: `{ skuId(Long), productId(Long), sellerId(Long), productName, spec, price(Integer), stock(Integer), image, status(Integer) }`

---

### 4.4 购物车服务 (cart-service)

#### CartController — `/api/cart`

| #   | 方法     | 路径                                | 请求                                                | 响应       | 阶段  |
| --- | ------ | --------------------------------- | ------------------------------------------------- | -------- | --- |
| 60  | GET    | `/api/cart`                       | — (JWT)                                           | `CartVO` | ✅   |
| 61  | POST   | `/api/cart/item`                  | `{ skuId(Long), quantity(1) }`                    | `Void`   | ✅   |
| 62  | PUT    | `/api/cart/item`                  | `{ skuId(Long), quantity }`                       | `Void`   | ✅   |
| 63  | DELETE | `/api/cart/item/{skuId}`          | Path: `skuId` (Long)                              | `Void`   | ✅   |
| 64  | PATCH  | `/api/cart/item/{skuId}/selected` | Path: `skuId` (Long), Query: `selected` (Boolean) | `Void`   | ✅   |
| 65  | DELETE | `/api/cart/clear`                 | — (JWT)                                           | `Void`   | ✅   |

CartVO: `{ items: List<CartItemResp>, totalPrice(Integer), selectedPrice(Integer), allSelected(Boolean) }`
CartItemResp: `{ skuId(Long), productName, spec, image, price(Integer), quantity, selected(Boolean), subTotal(Integer) }`

#### InternalCartController — `/internal/cart` (Feign)

| #   | 方法     | 路径                        | 请求                     | 响应 (原始)             | 阶段  |
| --- | ------ | ------------------------- | ---------------------- | ------------------- | --- |
| 66  | GET    | `/internal/cart/selected` | Query: `userId` (Long) | `List<CartItemDTO>` | ✅   |
| 67  | DELETE | `/internal/cart/clear`    | Query: `userId` (Long) | `void`              | ✅   |

---

### 4.5 交易服务 (trade-service)

#### OrderController — `/api/trade/order`

| #   | 方法   | 路径                                   | 请求                                                 | 响应               | 阶段  |
| --- | ---- | ------------------------------------ | -------------------------------------------------- | ---------------- | --- |
| 68  | POST | `/api/trade/order`                   | `{ addressId?, cartItemIds: List<Long>, remark? }` | `OrderVO`        | ✅   |
| 69  | GET  | `/api/trade/order/{orderNo}`         | Path: `orderNo` (String, 业务号)                      | `OrderVO`        | ✅   |
| 70  | GET  | `/api/trade/order/page`              | Query: `status?`, `page`(1), `size`(20)            | `IPage<OrderVO>` | ✅   |
| 71  | POST | `/api/trade/order/{orderNo}/cancel`  | Path: `orderNo` (String)                           | `Void`           | ✅   |
| 72  | POST | `/api/trade/order/{orderNo}/confirm` | Path: `orderNo` (String)                           | `Void`           | ✅   |
| —   | POST | `/api/trade/order/direct`         | `{ skuId(Long), quantity, addressId, couponId? }` | `OrderVO`        | ✅ V4.0 |

OrderVO: `{ id(Long), orderNo(String), userId(Long), sellerId(Long), totalAmount(Integer), payAmount(Integer), discountAmount(Integer), status(Integer)["PENDING_PAY"/"PENDING_SHIP"/"PENDING_RECEIPT"/"COMPLETED"/"CANCELLED"/"PENDING_REVIEW"], paymentType(Integer), receiverName, receiverPhone, receiverAddress, createTime, payTime?, consignTime?, endTime?, items: List<OrderItemVO> }`
OrderItemVO: `{ id(Long), skuId(Long), productName, skuSpec, price(Integer), quantity, subTotal(Integer), image }`

#### SellerOrderController — `/api/trade/seller/order`

| #   | 方法   | 路径                                       | 请求                                                                      | 响应               | 阶段  |
| --- | ---- | ---------------------------------------- | ----------------------------------------------------------------------- | ---------------- | --- |
| 73  | GET  | `/api/trade/seller/order/page`           | Query: `status?`, `page`(1), `size`(20)                                 | `IPage<OrderVO>` | ✅   |
| 74  | GET  | `/api/trade/seller/order/{orderNo}`      | Path: `orderNo` (String)                                                | `OrderVO`        | ✅   |
| 75  | POST | `/api/trade/seller/order/{orderNo}/ship` | Path: `orderNo` (String), Body: `{ logisticsNumber, logisticsCompany }` | `Void`           | ✅   |

#### SellerDashboardController — `/api/trade/seller/dashboard`

| #   | 方法  | 路径                            | 请求             | 响应                   | 阶段  |
| --- | --- | ----------------------------- | -------------- | -------------------- | --- |
| 76  | GET | `/api/trade/seller/dashboard` | — (JWT seller) | `Map<String,Object>` | ✅   |

#### SellerApplicationController — `/api/seller/apply`

| #   | 方法   | 路径                  | 请求                        | 响应                        | 阶段  |
| --- | ---- | ------------------- | ------------------------- | ------------------------- | --- |
| 77  | POST | `/api/seller/apply` | `SellerApplicationEntity` | `SellerApplicationEntity` | 🔵  |
| 78  | GET  | `/api/seller/apply` | — (JWT)                   | `SellerApplicationEntity` | 🔵  |

#### SellerFinanceController — `/api/seller/finance`

| #   | 方法   | 路径                                    | 请求                           | 响应                        | 阶段  |
| --- | ---- | ------------------------------------- | ---------------------------- | ------------------------- | --- |
| 79  | GET  | `/api/seller/finance/settlement/page` | Query: `page`(1), `size`(20) | `IPage<SettlementEntity>` | 🔵  |
| 80  | GET  | `/api/seller/finance/settlement/{id}` | Path: `id` (Long)            | `SettlementEntity`        | 🔵  |
| 81  | GET  | `/api/seller/finance/balance`         | — (JWT)                      | `Map<String,Object>`      | 🔵  |
| 82  | POST | `/api/seller/finance/withdrawal`      | `{ amount, bankCard? }`      | `WithdrawalEntity`        | 🔵  |
| 83  | GET  | `/api/seller/finance/withdrawal/page` | Query: `page`(1), `size`(20) | `IPage<?>`                | 🔵  |

#### AfterSaleController — `/api/after-sale`

| #   | 方法   | 路径                | 请求                           | 响应                | 阶段  |
| --- | ---- | ----------------- | ---------------------------- | ----------------- | --- |
| 84  | POST | `/api/after-sale` | `{ orderNo, reason? }`       | `AfterSaleEntity` | 🔵  |
| 85  | GET  | `/api/after-sale` | Query: `page`(1), `size`(20) | `IPage<?>`        | 🔵  |

#### AdminController — `/api/trade/admin`

| #   | 方法  | 路径                             | 请求                                      | 响应                   | 阶段  |
| --- | --- | ------------------------------ | --------------------------------------- | -------------------- | --- |
| 86  | GET | `/api/trade/admin/dashboard`   | —                                       | `Map<String,Object>` | ✅   |
| 87  | GET | `/api/trade/admin/orders/page` | Query: `page`(1), `size`(20), `status?` | `IPage<OrderEntity>` | ✅   |
| 88  | GET | `/api/trade/admin/stats/trend` | Query: `days`(30)                       | `?`                  | 🔵  |

#### AdminAfterSaleController — `/api/admin/after-sale`

| #   | 方法  | 路径                                  | 请求                                            | 响应         | 阶段  |
| --- | --- | ----------------------------------- | --------------------------------------------- | ---------- | --- |
| 89  | GET | `/api/admin/after-sale`             | Query: `status?`, `page`(1), `size`(20)       | `IPage<?>` | 🔵  |
| 90  | PUT | `/api/admin/after-sale/{id}/review` | Path: `id` (Long), Query: `status`, `remark?` | `Void`     | 🔵  |

#### AdminApplicationController — `/api/admin/application`

| #   | 方法  | 路径                                   | 请求                                            | 响应        | 阶段  |
| --- | --- | ------------------------------------ | --------------------------------------------- | --------- | --- |
| 91  | GET | `/api/admin/application`             | Query: `status`(0)                            | `List<?>` | 🔵  |
| 92  | PUT | `/api/admin/application/{id}/review` | Path: `id` (Long), Query: `status`, `remark?` | `Void`    | 🔵  |

#### AdminFinanceController — `/api/admin/finance`

| #   | 方法   | 路径                                          | 请求                                            | 响应     | 阶段  |
| --- | ---- | ------------------------------------------- | --------------------------------------------- | ------ | --- |
| 93  | POST | `/api/admin/finance/settlement/generate`    | Query: `sellerId`, `periodStart`, `periodEnd` | `?`    | 🔵  |
| 94  | GET  | `/api/admin/finance/settlement/page`        | Query: `page`(1), `size`(20)                  | `?`    | 🔵  |
| 95  | GET  | `/api/admin/finance/withdrawal/page`        | Query: `status?`, `page`(1), `size`(20)       | `?`    | 🔵  |
| 96  | PUT  | `/api/admin/finance/withdrawal/{id}/review` | Path: `id` (Long), Query: `status`, `remark?` | `Void` | 🔵  |

#### InternalOrderController — `/internal/trade/order` (Feign)

| #   | 方法  | 路径                                       | 请求                                                  | 响应 (原始)           | 阶段  |
| --- | --- | ---------------------------------------- | --------------------------------------------------- | ----------------- | --- |
| 97  | PUT | `/internal/trade/order/{orderNo}/status` | Path: `orderNo` (String), Query: `status` (Integer) | `void`            | ✅   |
| 98  | GET | `/internal/trade/order/{orderNo}`        | Path: `orderNo` (String)                            | `OrderSummaryDTO` | ✅   |
| —   | POST | `/internal/trade/order/create`        | `{ userId, skuId, quantity, addressId, couponId?, orderType }` | `OrderSummaryDTO` (内部秒杀订单创建, marketing→trade Feign, V4.1) | ✅ V4.1 |

---

### 4.6 支付服务 (pay-service)

#### PayController — `/api/pay`

| #   | 方法   | 路径                                | 请求                                                                        | 响应               | 阶段  |
| --- | ---- | --------------------------------- | ------------------------------------------------------------------------- | ---------------- | --- |
| 99  | POST | `/api/pay/order/{orderNo}`        | Path: `orderNo` (String); Query: `channel`=`WECHAT`(默认)`\|ALIPAY\|BALANCE` | `PayOrderVO`（余额支付即时 SUCCESS 无二维码；微信/支付宝返回支付链接） | ✅ V2.3 |
| 100 | POST | `/api/pay/callback/wechat`        | Headers: `Wechatpay-Signature/Nonce/Timestamp/Serial`, Body: raw XML/JSON | `String`         | ✅   |
| —   | POST | `/api/pay/callback/alipay`        | Form params（含 `out_trade_no`）                                            | `String`（"success"，Mock 验签恒通过） | ✅ V2.3 |
| 101 | GET  | `/api/pay/order/{orderNo}/status` | Path: `orderNo` (String)                                                  | `PayOrderVO` | ✅   |

---

### 4.7 物流服务 (logistics-service)

#### LogisticsController — `/api/logistics`

| #   | 方法  | 路径                         | 请求                     | 响应                     | 阶段  |
| --- | --- | -------------------------- | ---------------------- | ---------------------- | --- |
| 102 | GET | `/api/logistics/{orderId}` | Path: `orderId` (Long) | `OrderLogisticsEntity` | ✅   |

#### InternalLogisticsController — `/internal/logistics` (Feign)

| #   | 方法   | 路径                                     | 请求                                                                                                          | 响应 (原始) | 阶段  |
| --- | ---- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ------- | --- |
| 103 | POST | `/internal/logistics/create`           | `{ orderId, logisticsNumber, logisticsCompany, contact, mobile, province, city, district, street, detail }` | `void`  | ✅   |
| 104 | PUT  | `/internal/logistics/{orderId}/status` | Path: `orderId` (Long), Query: `status` (Integer)                                                           | `void`  | ✅   |

---

### 4.8 搜索服务 (search-service)

#### SearchController — `/api/search`

| #   | 方法  | 路径                    | 请求                                                      | 响应                      | 阶段  |
| --- | --- | --------------------- | ------------------------------------------------------- | ----------------------- | --- |
| 105 | GET | `/api/search/product` | Query: `keyword?`, `categoryId?`, `page`(1), `size`(20) | `Page<ProductSearchVO>` | 🔵  |
| 106 | GET | `/api/search/hot`     | Query: `limit`(10)                                      | `List<String>`          | 🔵  |
| 107 | GET | `/api/search/history` | Query: `limit`(10)                                      | `List<String>`          | 🔵  |

ProductSearchVO: `{ id(Long), productId(String), categoryId(Long), name, description, brand, mainImage, price(Integer), soldCount }`

#### InternalSearchController — `/internal/search` (运维)

| #   | 方法   | 路径                         | 请求  | 响应 (原始)              | 阶段  |
| --- | ---- | -------------------------- | --- | -------------------- | --- |
| 108 | POST | `/internal/search/reindex` | —   | `Map<String,Object>` | 🔵  |

---

### 4.9 营销服务 (marketing-service)

#### CouponController — `/api/coupon`

| #   | 方法   | 路径                      | 请求                         | 响应                       | 阶段  |
| --- | ---- | ----------------------- | -------------------------- | ------------------------ | --- |
| 109 | POST | `/api/coupon/claim`     | Query: `couponId` (String) | `UserCouponEntity`       | 🔵  |
| 110 | GET  | `/api/coupon/available` | — (JWT)                    | `List<UserCouponEntity>` | 🔵  |
| 111 | GET  | `/api/coupon/used`      | — (JWT)                    | `List<UserCouponEntity>` | 🔵  |
| 112 | GET  | `/api/coupon/template`  | —                          | `List<CouponEntity>`     | 🔵  |
| —   | POST | `/api/coupon/grab`     | Query: `couponId` (String) | `UserCouponEntity` (Redis Lua 原子领取，防超发) | ✅ V4.0 |
| —   | POST | `/api/coupon/available/filter` | `{ skuIds: List<Long>, totalAmount: Integer }` | `List<UserCouponEntity>` (结算页预过滤可用券，V4.3 京东标准) | ✅ V4.3 |

> **V4.4 变更**: `POST /api/coupon/purchase` 已移除，优惠券仅通过 claim/grab 领取。

#### InternalCouponController — `/internal/coupon`（供 trade-service 下单抵扣，Feign 内部调用，异常透传不包 Result）

| #   | 方法   | 路径                          | 请求                                                                                     | 响应            | 阶段  |
| --- | ---- | --------------------------- | -------------------------------------------------------------------------------------- | ------------- | --- |
| —   | POST | `/internal/coupon/use`      | Query: `userId`(Long), `userCouponId`(Long), `orderNo`(String), `orderAmount`(Integer) | `int`（抵扣金额，分） | ✅   |
| —   | POST | `/internal/coupon/rollback` | Query: `orderNo`(String)                                                               | `void`        | ✅   |

> 下单接口 `POST /api/trade/order` 的 `CreateOrderReq` 新增可选字段 `userCouponId`(Long)：传入则由 `OrderManager` 通过 `CouponClient` 抵扣，`payAmount = totalAmount - discountAmount`；下单失败/取消/超时按订单号回滚优惠券。

#### AdminCouponController — `/api/admin/coupon`

| #   | 方法   | 路径                                     | 请求                        | 响应             | 阶段  |
| --- | ---- | -------------------------------------- | ------------------------- | -------------- | --- |
| 113 | POST | `/api/admin/coupon`                    | `CouponEntity`            | `CouponEntity` | 🔵  |
| 114 | PUT  | `/api/admin/coupon/{couponId}/disable` | Path: `couponId` (String) | `Void`         | 🔵  |

#### MerchantCouponController — `/api/seller/coupon`

| #   | 方法   | 路径                                      | 请求                        | 响应                   | 阶段  |
| --- | ---- | --------------------------------------- | ------------------------- | -------------------- | --- |
| 115 | POST | `/api/seller/coupon`                    | `CouponEntity`            | `CouponEntity`       | 🔵  |
| 116 | GET  | `/api/seller/coupon`                    | — (JWT seller)            | `List<CouponEntity>` | 🔵  |
| 117 | PUT  | `/api/seller/coupon/{couponId}/disable` | Path: `couponId` (String) | `Void`               | 🔵  |

#### FlashSaleController — `/api/flash`

| #   | 方法   | 路径               | 请求                      | 响应                      | 阶段  |
| --- | ---- | ---------------- | ----------------------- | ----------------------- | --- |
| 118 | GET  | `/api/flash`     | —                       | `List<FlashSaleEntity>` | 🔵  |
| 119 | POST | `/api/flash/buy` | Query: `flashId` (Long) | `Map<String,Object>`    | 🔵  |

---

### 4.10 AI 服务 (ai-service)

> 当前实现: V2.0 MVP ✅ | V2.5 生产就绪 ✅ | 完整选型见 [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)

#### 4.10.1 AI 商品助手

| #   | 方法   | 路径                         | 请求                                                    | 响应                          | 阶段  |
| --- | ---- | --------------------------- | ------------------------------------------------------- | ----------------------------- | --- |
| 120 | POST | `/api/ai/chat`              | `AiChatRequest { message: String, conversationId?: String }` | `AiChatResponse { reply: String, conversationId: String }` | ✅ V2.0 |
| 122 | POST | `/api/ai/chat/stream`       | `AiChatRequest { message: String, conversationId?: String }` | SSE `text/event-stream` (逐token返回) | ✅ V2.5 |
| 123 | GET  | `/api/ai/conversations`     | —                                                       | `Result<List<ConversationSummary>>` | ✅ V2.5 |
| 124 | GET  | `/api/ai/conversations/{id}`| —                                                       | `Result<ConversationDetail>` | ✅ V2.5 |
| 125 | DELETE | `/api/ai/conversations/{id}` | —                                                     | `Result<Void>`                | ✅ V2.5 |

#### 4.10.2 AI 客服

| #   | 方法   | 路径                          | 请求                                                    | 响应                          | 阶段  |
| --- | ---- | ---------------------------- | ------------------------------------------------------- | ----------------------------- | --- |
| 121 | POST | `/api/ai/cs/chat`            | `AiChatRequest { message: String, conversationId?: String }` | `AiChatResponse { reply: String, conversationId: String }` | ✅ V2.0 |
| 126 | POST | `/api/ai/cs/chat/stream`     | `AiChatRequest { message: String, conversationId?: String }` | SSE `text/event-stream` (逐token返回) | ✅ V2.5 |

#### 4.10.3 DTO 定义

**`AiChatRequest`**:
```json
{
  "message": "500以内适合夏天穿的透气跑鞋",
  "conversationId": "conv-uuid-xxx"  // 可选，继续已有会话
}
```

**`AiChatResponse`**:
```json
{
  "reply": "为您找到以下商品...",
  "conversationId": "conv-uuid-xxx"
}
```

**`ConversationSummary`**（会话列表）:
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

**`ConversationDetail`**（会话详情）:
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
          "input": {"keyword": "透气跑鞋 500以内"},
          "output": "[P001] Nike ZoomX — ¥499.00 (销量:1523)..."
        }
      ],
      "timestamp": "2026-07-19T10:30:05"
    }
  ]
}
```

#### 4.10.4 AI 错误码

| 错误码 | HTTP状态 | 说明 |
|--------|---------|------|
| `AI_SERVICE_UNAVAILABLE` | 503 | AI 服务未启用（`ai.enabled=false`）或 API Key 未配置 |
| `AI_TIMEOUT` | 504 | LLM 调用超时（当前 60s） |
| `AI_CONTENT_FILTERED` | 400 | 输入或输出被内容安全策略拦截 |
| `AI_RATE_LIMITED` | 429 | 用户或全局限流触发 |
| `AI_CONVERSATION_NOT_FOUND` | 404 | 会话不存在或不属于当前用户 |

---

### 4.11 买家-商家消息服务 (V3.3 已实现 ✅)

> **大厂对标**: 淘宝旺旺/京东咚咚。消息系统独立于 AI 服务，WebSocket + STOMP 协议。

#### 4.11.1 消息 WebSocket

| #   | 方法 | 路径 | 说明 | 阶段 |
| --- | --- | --- | --- | --- |
| 142 | WS | `/ws/chat` | STOMP 端点，订阅 `/user/queue/messages` 接收消息，发送 `/app/chat.send` | ✅ V3.3 |

**STOMP 帧格式**:
```json
{
  "messageId": "uuid",
  "conversationId": "buyer_1001_seller_5_product_P001",
  "senderId": 1001,
  "senderRole": "BUYER",
  "content": "这个商品还有货吗？",
  "contentType": "TEXT",
  "timestamp": "2026-07-23T10:30:00"
}
```

#### 4.11.2 消息 REST API

| #   | 方法 | 路径 | 说明 | 阶段 |
| --- | --- | --- | --- | --- |
| 143 | GET | `/api/chat/conversations` | 当前用户的对话列表（按最后消息时间排序） | ✅ V3.3 |
| 144 | GET | `/api/chat/conversations/{id}/messages` | 分页查询对话历史（`?page=1&size=50`） | ✅ V3.3 |
| 145 | GET | `/api/chat/unread-count` | 未读消息计数 | ✅ V3.3 |

#### 4.11.3 商家回复模板（V3.4 计划 ⚪）

| #   | 方法 | 路径 | 说明 | 阶段 |
| --- | --- | --- | --- | --- |
| 146 | GET | `/api/seller/templates` | 商家回复模板列表 | ⚪ V3.4 |
| 147 | POST | `/api/seller/templates` | 创建模板 | ⚪ V3.4 |
| 148 | PUT | `/api/seller/templates/{id}` | 更新模板 | ⚪ V3.4 |
| 149 | DELETE | `/api/seller/templates/{id}` | 删除模板 | ⚪ V3.4 |

#### 4.11.4 商家知识库（V3.4 计划 ⚪）

| #   | 方法 | 路径 | 说明 | 阶段 |
| --- | --- | --- | --- | --- |
| 150 | GET | `/api/seller/knowledge` | 知识库条目列表 | ⚪ V3.4 |
| 151 | POST | `/api/seller/knowledge` | 上传 FAQ/政策（自动分块+Embedding+ES索引） | ⚪ V3.4 |
| 152 | DELETE | `/api/seller/knowledge/{id}` | 删除知识条目 | ⚪ V3.4 |

### 4.12 商品详情增强 (V3.5 已实现 ✅)

| #   | 方法 | 路径 | 说明 | 阶段 |
| --- | --- | --- | --- | --- |
| 153 | GET | `/api/item/product/{id}/detail` | 完整详情(商品+店铺+SKU+参数+评论摘要+优惠券) | ✅ V3.5 |
| 154 | GET | `/api/item/product/{id}/skus` | 结构化SKU数据(维度+选项+价格+库存) | ✅ V3.5 |
| 155 | POST | `/api/trade/order/direct` | 立即购买 `{ skuId, quantity, addressId, couponId? }` | ✅ V3.5 |
| 156 | GET | `/api/shop/{sellerId}` | 商家公开店铺页(店铺信息+评分) | ✅ V3.5 |
| 157 | GET | `/api/shop/{sellerId}/products` | 商家全部商品(分页) | ✅ V3.5 |
| 158 | POST | `/api/shop/follow/{sellerId}` | 关注商家 | ✅ V3.5 |
| 159 | DELETE | `/api/shop/follow/{sellerId}` | 取消关注 | ✅ V3.5 |
| 160 | GET | `/api/item/review/product/{id}/filter` | 评论筛选(?rating=5&hasMedia=true&sort=newest) | ✅ V3.5 |
| 161 | GET | `/api/item/review/product/{id}/summary` | 评论摘要(均分+星级分布+总评数) | ✅ V3.5 |
| 162 | GET | `/api/cart/grouped` | 按店铺分组购物车 | ✅ V3.5 |

### 4.13 网关服务 (gateway-service)

#### WebSocketNotifyController — `/internal/ws` (内部)

| #   | 方法   | 路径                          | 请求                                           | 响应 (原始) | 阶段  |
| --- | ---- | --------------------------- | -------------------------------------------- | ------- | --- |
| 122 | POST | `/internal/ws/order/status` | `{ orderNo, oldStatus, newStatus, message }` | `void`  | 🔵  |

---

## 五、关键 DTO/VO 类型速查

### OAuth2TokenResp (认证响应, snake_case JSON)

```json
{ "access_token":"eyJ...", "token_type":"Bearer", "expires_in":1800, "refresh_token":"xxx", "user_id":1, "username":"buy123" }
```

### ProductVO (商品)

```json
{ "id":1, "productId":"prod-001", "sellerId":1, "categoryId":1, "name":"手工陶瓷茶杯", "mainImage":"https://...", "description":"...", "brand":"冰美精选", "soldCount":1280, "commentCount":86, "status":1, "publishTime":"2026-06-28T10:00:00", "skus":[...] }
```

### SkuVO (SKU)

```json
{ "id":1, "skuId":"sku-001", "productId":1, "spec":"天青/标准", "price":12800, "stock":50, "image":"https://...", "soldCount":320, "status":1 }
```

### OrderVO (订单)

```json
{ "id":1, "orderNo":"ord-xxx", "userId":1, "sellerId":1, "totalAmount":25600, "payAmount":25600, "discountAmount":0, "status":1, "paymentType":1, "receiverName":"张三", "receiverPhone":"138...", "receiverAddress":"北京市...", "createTime":"...", "payTime":null, "consignTime":null, "endTime":null, "items":[...] }
```

### 金额约定

所有金额字段（price/totalAmount/payAmount/balance 等）均为 **Integer（分）**。前端展示需除以 100。

---

## 六、Gateway 路由速查

| 路径前缀                       | 目标服务                 | 认证              |
| -------------------------- | -------------------- | --------------- |
| `/api/auth/**`             | authorization-server | 公开              |
| `/api/user/**`, `/user/**` | user-service         | 需要 JWT          |
| `/api/item/**`             | item-service         | 商品浏览公开          |
| `/api/cart/**`             | cart-service         | 需要 JWT          |
| `/api/trade/**`            | trade-service        | 需要 JWT          |
| `/api/pay/**`              | pay-service          | 回调公开            |
| `/api/logistics/**`        | logistics-service    | 需要 JWT          |
| `/api/search/**`           | search-service       | 公开              |
| `/api/coupon/**`           | marketing-service    | 需要 JWT          |
| `/api/flash/**`            | marketing-service    | 公开              |
| `/api/admin/**`            | 多服务                  | 需要 JWT (admin)  |
| `/api/seller/**`           | 多服务                  | 需要 JWT (seller) |
| `/internal/**`             | —                    | 网关拦截，仅内部        |
