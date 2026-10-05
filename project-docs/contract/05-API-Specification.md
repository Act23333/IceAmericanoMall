# 05 — API 规格文档 (API Specification — SDD)

> **最后更新**: 2026-10-03 | **版本**: V5.6.1 | **端点总数**: 229+（V5.6 全量落地：FT1-FT4 / PAY6 / AD43-AD47 / I40-I43 均已实现） | **公共**: 195+ | **内部**: 39+ | **已实现**: 229+
>
> **V5.6.1 实现落地说明**：V5.6.0 规格先行的四个 P0 功能（F1 买家退款资金流 / F2 运费模型 / F3 拆单发货 / F4 渠道对账）已**全量实现并通过全量测试**，全部 V5.6 规划标注已收敛为"已实现（V5.6）"；实现方上报的规格偏差已按 ADR-035/036/037 收敛进正文（错误码 3039/3040/3041、AD47 confirm-return 退款触发分派、I41 并发幂等、I43 fail-closed、AD46 批次列表）。数据模型见 `04-Data-Model.md` §十二（已建库）。
> 
> **角色**: Frontend (Next.js 15 + React 19) ↔ Backend (Spring Cloud Microservices) 唯一 API 契约。
> 本文件是 SDD (Specification-Driven Development) 的核心产物——前后端并行开发的唯一真相来源。
> 归属域: contract | Owner: product-manager | 更新触发条件: 对外契约变更时

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
- `/internal/**` 路径**不包装** Result。**V5.4 修正**：内部接口错误响应的 HTTP 状态码 = `ErrorCode` 对应状态（不再一律 500），body 为极简 `{"code":...,"msg":...}`（不含 `data`，不构成 `Result` 信封契约）；注意业务码 3000-3039 的 HTTP 状态本身为 200，调用方需按 body 内 `code` 判定业务失败。
- 全平台方法参数校验失败（`@Valid` 请求体 / `@Validated` 方法参数 / 表单绑定）统一映射为 `400 PARAM_ERROR(1101)`（**V5.4 收紧**，此前部分场景被兜底为 500）。

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

> **V5.4 对齐实现**（`ia-common` `ErrorCode` 枚举）：基础码 1xxx / 业务码 3xxx / 第三方码 4xxx / AI 码 31xx。
> 业务码 3000-3039 的 HTTP 状态为 200（业务失败不等于传输失败），前端按 body 内 `code` 分流。

| 错误码 | 名称 | HTTP | 含义 | 前端处理建议 |
| ----- | ---- | ---- | --- | ---------------- |
| 1000 | INTERNAL_ERROR | 500 | 系统内部错误 | 提示"系统繁忙，请稍后再试" |
| 1011 | UNAUTHORIZED | 401 | 未认证（Token 缺失） | 跳转登录页 |
| 1012 | TOKEN_EXPIRED | 401 | Token 过期 | 跳转登录页 + 刷新 Token |
| 1013 | FORBIDDEN | 403 | 无权限 | 提示"无操作权限" |
| 1014 | FREQUENT_ERROR | 429 | 操作过于频繁（**流控统一出口**：`@RateLimit` 注解流控 block、网关 `RequestRateLimiter`、验证码尝试过频等一律抛 1014，不另设限流错误码） | 倒计时后允许重试 |
| 1101 | PARAM_ERROR | 400 | 参数校验失败 | 表单字段高亮 + 错误提示 |
| 1102 | USER_NOT_FOUND | 404 | 用户不存在 | 提示后刷新 |
| 1103 | NO_RESOURCE_FOUND | 404 | 资源/静态资源不存在 | 展示 404 页面或空状态 |

### 2.1 业务错误码速查

| 错误码 | 场景       | 提示文案             |
| ----- | -------- | ---------------- |
| 3001 | 验证码错误    | "验证码错误"      |
| 3002 | 验证码已过期 | "验证码已过期，请重新获取" |
| 3003 | 手机号已注册 | "该手机号已注册，请直接登录"  |
| 3005 | 余额不足     | "账户余额不足，请更换支付方式" |
| 3006 | 用户已存在（并发重复入驻申请等） | "请勿重复提交" |
| 3010 | 订单状态不支持此操作（不可取消等） | "当前订单状态不允许该操作"    |
| 3012 | 库存不足     | "该商品库存不足，请调整数量"  |
| 3013 | 购物车为空   | "先去逛逛挑点好物吧" |
| 3014 | 跨店下单     | "暂不支持跨店下单" |
| 3015 | 店铺不存在   | "店铺不存在或已关闭" |
| 3019 | 优惠券不存在/不可用 | "该优惠券不适用于当前订单"   |
| 3020-3024 | 秒杀（活动不存在/未开始/售罄/超限购/失败） | "秒杀活动已结束"等 |
| 3039 | 退款状态不支持此操作（`REFUND_STATUS_INVALID`，✅ V5.6：重试仅限"失败"退款单、回写仅限"同意(2)/已退货(4)"售后单、已退款(5)售后单不可再审核/再确认退货等状态类拒绝） | "当前退款状态不允许该操作" |
| 3040 | 运费模板名称已存在（`FREIGHT_TEMPLATE_NAME_EXISTS`，✅ V5.6：同商家模板名唯一 `uk_seller_template`） | "运费模板名称已存在" |
| 3041 | 运费模板不存在（`FREIGHT_TEMPLATE_NOT_FOUND`，✅ V5.6：模板不存在 / 非本商家模板统一按此拒绝，含商品绑定他人模板） | "运费模板不存在" |

> **V5.4 验证码校验失败错误码映射**（verification-service 结果码 → 对外错误码，authorization-service/user-service 调用侧统一）：
> `CODE_MISMATCH → CAPTCHA_ERROR(3001)`；`CODE_EXPIRED → CAPTCHA_EXPIRED(3002)`；`TOO_MANY_ATTEMPTS → FREQUENT_ERROR(1014, HTTP 429)`。

### 2.2 AI 专项错误码

| 错误码                         | HTTP | 说明                    |
| --------------------------- | ---- | --------------------- |
| `AI_SERVICE_UNAVAILABLE` (3100) | 503  | AI 服务未启用或 API Key 未配置 |
| `AI_TIMEOUT` (3101)         | 504  | LLM 调用超时（60s）         |
| `AI_CONTENT_FILTERED` (3102) | 400  | 输入或输出被内容安全策略拦截        |
| `AI_RATE_LIMITED` (3103)    | 429  | AI 调用频率超限             |
| `AI_CONVERSATION_NOT_FOUND` (3104) | 404  | 会话不存在或不属于当前用户         |

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

### 3.5 Token 吊销与 WebSocket 握手鉴权（🆕 V5.5，ADR-027；🆕 V5.5.1 多粒度吊销，ADR-031）

**多粒度吊销模型（🆕 V5.5.1，ADR-031）**：token 吊销按三级粒度运作——**单 token**（`jti` 黑名单）、**全用户**（`tokenVer` 版本号）、**refresh 会话拆除**。网关 `JwtBlacklistFilter`（order -700）以**一次 `MGET auth:blacklist:{jti} auth:tokenver:{userId}`** 同时完成两级判定，**每请求 Redis 往返次数不变**（仍为 1 次）；Redis 异常 / 值不可解析 **fail-open**（放行 + WARN，与 ADR-027 口径一致）。

**吊销触发点矩阵**：

| 端点 / 流程 | 粒度 | 行为 |
| --- | --- | --- |
| `POST /api/auth/logout` | 单 token | 吊销本端 refresh 会话 + 拉黑当前 access token `jti`（`auth:blacklist:{jti}`，TTL = 令牌剩余有效期，网关鉴权链查键拒答）——**口径不变（ADR-027）** |
| `POST /api/auth/reset-password` | **全用户** | **用户级令牌版本吊销（ADR-031，替代原"按 jti 拉黑当前令牌"口径）**：`INCR auth:tokenver:{userId}` + 拆除该用户**全部** refresh 会话（按 `PlatformEnum` 全端遍历，**全端下线**）——该用户此前签发的**全部** access token 立即失效；目标 userId 取自凭证行，匿名重置同样生效；**不再消费 `Authorization` 头**（原本可选，客户端无需改动） |
| 管理端禁用用户 `PUT /api/admin/user/{id}/status`（user-service） | **全用户** | user-service 经 ia-api `AuthContextClient.revokeUserTokens` 调内部端点 `POST /internal/auth/token-version/revoke`（I39，§4.14.14），语义同上——被禁用账号**即时全端下线** |

**网关版本校验（401 判定顺序）**：access token 签发时写入 `ver` claim（登录与 refresh 换发同一签发链路，均携带**签发时刻**的版本值）。网关校验：

1. `auth:blacklist:{jti}` 键存在 → **401**（单 token 吊销）；
2. `auth:tokenver:{userId}` 键存在且 ≠ token `ver` claim（缺失 / 类型异常按 `0`）→ **401**（改密 / 被禁用后旧 token **全端即时 401**）；
3. 版本键缺失 → 视为从未被用户级吊销，跳过版本比对；token 无 `userId` claim → 只查黑名单；token 无 `jti` → 维持 ADR-027 语义放行（记 DEBUG）。

**兼容规则**：版本键缺失 = 版本 `0`；存量 token 无 `ver` claim 按 `0` 处理，**自然过期，无需迁移**。`auth:tokenver:{userId}` **不设 TTL**（与黑名单刻意相反——版本键若过期，"版本落后"的令牌会被重新放行，等于吊销被回滚）。该键为第 4 个跨服务契约键，与 `user:info:{userId}` / `user:perms:{userId}` / `auth:blacklist:{jti}` 同级豁免「跨服务不共享 Redis 键」约束，豁免范围仅限 **auth 写、网关只读**（ADR-031 §7）。

**WebSocket 握手鉴权**：浏览器 STOMP/SockJS 无法自定义握手请求头，约定把令牌放在 query 参数：

```
ws(s)://<gateway>/ws/chat/...?token=<accessToken>
```

网关 `WsHandshakeTokenFilter` 对配置路径（`/ws/chat/**`）**仅做搬运不做鉴权**：入站无 `Authorization` 且 query 含 `token` 时，改写为 `Authorization: Bearer <token>` 请求头，并**重建 URI 剔除 query 中的令牌**（token 不透传下游服务、不落下游访问日志）。换头后由既有安全链验签 + 时效校验（`/ws/chat/**` 维持 `authenticated()`，**不加入白名单**）。已带 `Authorization` 头的调用方无需 `?token=`。

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

> V5.1 DDD 重构后 URL 保持不变；登录/注册/刷新响应 `data` 新增 `token_type: "Bearer"`（增量字段，向后兼容）。
> 平台类型（platform）由服务端根据请求头 / UA 识别（`DeviceTypeResolver`），不信任前端传值；`clientIp` 解析 `X-Forwarded-For` 首段。
>
> **V5.1.1 增量**：
> - A1 的 `credentialType` 由"仅语义"改为**服务端路由依据**（白名单 `PASSWORD | SMS_CODE | EMAIL_CODE`，缺省 `PASSWORD`）——
>   同时修复了 `SmsLoginStrategy`（手机号+短信码）此前无 HTTP 入口的缺陷。
> - A1 新增可选人机验证字段（极验 v4 四要素）；风控命中时返回 `1016 CAPTCHA_REQUIRED`，`data` 携带重试参数（见下方说明）。
> - 新增 A10 邮箱验证码签发端点。
> - **V5.2.3 注册错误语义（ADR-017）**：已注册手机号 → `PHONE_ALREADY_REGISTERED(3003)`；`USERNAME_ALREADY_TAKEN(3004)` 用户名占用校验退役（昵称降级为展示字段）。

| #   | 方法   | 路径                         | 认证  | 请求                                                                                                                              | 响应                                                                           | 阶段      |
| --- | ---- | -------------------------- | --- | ------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ------- |
| A1  | POST | `/api/auth/web/login`      | 🌐  | `WebLoginReq { identityType:"PHONE"\|"USERNAME"\|"EMAIL", account, credential, credentialType?:"PASSWORD"\|"SMS_CODE"\|"EMAIL_CODE"（缺省 PASSWORD）, deviceId?, captchaProvider?, lotNumber?, captchaOutput?, passToken?, genTime?, captcha?(废弃) }` | `LoginResp`（见下方 JSON）                                                     | ✅ V5.1.1 |
| A2  | POST | `/api/auth/register`       | 🌐  | `RegisterReq { phone, password, code, username?, deviceId? }`                                                                   | `LoginResp`（注册即登录，直接签发令牌）                                          | ✅ V5.1  |
| A3  | POST | `/api/auth/refresh`        | 🌐  | Query: `refresh_token`                                                                                                          | `OAuth2TokenResp`（轮换不延长绝对有效期）                                       | ✅ V5.1  |
| A4  | POST | `/api/auth/logout`         | 🌐  | Query: `refresh_token`                                                                                                          | `Void`                                                                       | ✅       |
| A5  | POST | `/api/auth/reset-password` | 🌐  | `ResetPasswordReq { phone, code, newPassword }`                                                                                 | `Void`                                                                       | ✅       |
| A6  | POST | `/api/auth/web/wechat/login` | 🌐 | `WechatWebLoginReq { code, state }`（OAuth2 授权回调，委托 user-service 换 openid）                                            | `LoginResp`                                                                  | ✅ V5.1  |
| A7  | GET  | `/api/auth/check-session`  | 🔒  | Header: `Authorization: Bearer <token>`                                                                                         | `SessionStatus { valid(Boolean), ttlSeconds(Long), idleTimeoutMinutes(30) }` | ✅ V4.0  |
| A9  | POST | `/api/auth/wechat/mini/login` | 🌐 | `WechatMiniLoginReq { code, phoneCode? }`（wx.login() 临时凭证换取 openid）                                                   | `LoginResp`                                                                  | ✅ V5.1  |
| A10 | POST | `/api/auth/email/code`     | 🌐  | `EmailCodeReq { email, scene?:"LOGIN"\|"REGISTER"\|"RESET_PASSWORD"（缺省 LOGIN，非法取值 → 1101 参数错误） }`                     | `EmailCodeResp { message, expire_seconds, email(脱敏) }`                       | 🆕 V5.1.1 |

> **A1 登录方式路由**（身份类型 + 凭证类型，服务端按组合选择领域策略）：
>
> | credentialType | identityType | 说明 |
> | --- | --- | --- |
> | `PASSWORD`（缺省） | `USERNAME` / `PHONE` / `EMAIL` | 密码登录（PHONE/EMAIL 走盲索引） |
> | `SMS_CODE` | `PHONE` | 手机号 + 短信验证码（验证码由 `/api/user/code` 发送） |
> | `EMAIL_CODE` | `EMAIL` | 邮箱 + 邮箱验证码；**邮箱首次验证码登录即注册**（注册即登录，返回令牌） |
>
> 其余凭证类型（微信类）由专属端点承担，在 A1 上会被拒绝（`PARAM_ERROR`）。
>
> **A10 邮箱验证码**：由 verification-service 生成并经 notification-service 投递邮件；
> 重发限流（60 秒窗口、错误 5 次作废）由 verification-service 内建。
>
> **A4/A5 令牌吊销（🆕 V5.5，ADR-027；🆕 V5.5.1 修订，ADR-031）**：A4 登出携带 `Authorization` 头时，成功后当前 access token 按 `jti` 写入网关黑名单（`auth:blacklist:{jti}`）**立即失效**（口径不变）；A5 重置密码改为**用户级吊销**——`INCR auth:tokenver:{userId}` + 拆除该用户全部 refresh 会话（**全端下线**，旧 token 经网关 `ver` 比对即时 401），**不再消费 `Authorization` 头**（客户端无需改动）。详见 §3.5。
>
> ```json
> // POST /api/auth/email/code
> { "email": "buyer@example.com", "scene": "LOGIN" }
>
> // 200 OK
> {
>   "code": 200,
>   "msg": "success",
>   "data": { "message": "验证码已发送，请注意查收", "expire_seconds": 300, "email": "bu***@example.com" }
> }
> ```
>
> **A1/A6/A9 人机验证（R3）**：请求可携带极验 v4 二次校验四要素
> （`captchaProvider?` / `lotNumber` / `captchaOutput` / `passToken` / `genTime`，全部可选，
> `captchaId` 与签名由服务端生成，不接受前端传入）。
> 风控命中时返回 `1016`，`data` 即前端重试所需信息，前端拿到后渲染验证组件并带四要素重试：
>
> ```json
> {
>   "code": 1016,
>   "msg": "需要人机验证",
>   "data": { "captchaRequired": true, "provider": "GEETEST", "captchaId": "xxxx" }
> }
> ```
>
> 注：人机验证开关 `captcha.enabled` 缺省为 `false`（未接入极验的环境跳过校验并 WARN，不阻断登录），生产必须显式开启。
>
> **`LoginResp` / `OAuth2TokenResp`** (snake_case JSON):
>
> ```json
> {
>   "access_token": "eyJ...",
>   "token_type": "Bearer",       // 🆕 V5.1 新增增量字段
>   "expires_in": 1800,
>   "refresh_token": "xxx-uuid",
>   "user_id": 1
> }
> ```
>
> 注：A1/A2/A6/A9 返回 `LoginResp`（含 `user_id`），A3 返回 `OAuth2TokenResp`（不含 `user_id`）。

#### 4.1.2 JwkSetController — `/oauth2/jwks`

| #   | 方法  | 路径             | 认证  | 请求  | 响应                                         | 阶段  |
| --- | --- | -------------- | --- | --- | ------------------------------------------ | --- |
| A8  | GET | `/oauth2/jwks` | 🌐  | —   | `Map<String,Object>` (JWK Set, 不包装 Result) | ✅   |

---

### 4.2 用户/账户域 (user-service 为主；积分=marketing-service、余额=wallet-service，见各小节归属)

#### 4.2.1 UserController — `/api/user`

| #   | 方法    | 路径                         | 认证  | 请求                                                                                                                  | 响应                               | 阶段     |
| --- | ----- | -------------------------- | --- | ------------------------------------------------------------------------------------------------------------------- | -------------------------------- | ------ |
| U1  | GET   | `/api/user/info`           | 🔒  | — (JWT)                                                                                                             | `UserInfoResp`                   | ✅      |
| U2  | PATCH | `/api/user/profile`        | 🔒  | `UpdateProfileReq { nickname?, avatar?, updateMask?: ["nickname"\|"avatar"] }`                                      | `UserInfoResp` (全量)              | ✅ V3.2 |
| U3  | POST  | `/api/user/profile/avatar` | 🔒  | `multipart/form-data: file` (≤5MB, jpg/png/webp)                                                                    | `String` (头像 URL)                | ✅ V3.2 |
| U4  | GET   | `/api/user/profile/avatar` | 🌐  | Query: `width?`, `height?` (默认200x200)                                                                              | `image/jpeg` (Cache-Control: 1h) | ✅ V3.2 |
| U5  | POST  | `/api/user/code`           | 🌐  | `SmsCodeSendReq { phone, requestId, captchaTicket, captchaRandStr?, lotNumber, captchaOutput, passToken, genTime }` | `Void`                           | ✅      |

> **U5 内部行为注记（ADR-016）**：发码/人机验证已收口 verification-service（生成→限流→投递，内部透传 `scene=USER_CODE`）；对外报文无变化。`000000` 灰度后门仅存在于 user-service 本地校验路径，真实短信通道接入前不得上线生产。

**`UserInfoResp`** (V5.4：删除 `balance`——余额归 wallet-service，user 域冻结不再读/写):

```json
{
  "userId": "usr_abc123",
  "username": "buyer001",
  "phone": "138****8000",
  "email": "bu***@example.com",
  "avatar": "https://cdn.icedmall.com/avatar/xxx.jpg",
  "gender": null,
  "birthday": null,
  "memberLevel": null,
  "status": 1,
  "registerTime": "2026-06-28T10:00:00",
  "roleType": 0,
  "roles": ["ROLE_USER"],
  "permissions": []
}
```

> 余额查询/充值改走 wallet-service：`GET /api/wallet`、`POST /api/wallet/recharge`（见 §4.2.10）。

#### 4.2.2 AddressController — `/api/user/address`

| #   | 方法     | 路径                               | 认证  | 请求                                                                                                                    | 响应                  | 阶段  |
| --- | ------ | -------------------------------- | --- | --------------------------------------------------------------------------------------------------------------------- | ------------------- | --- |
| U6  | GET    | `/api/user/address/list`         | 🔒  | —                                                                                                                     | `List<AddressResp>` | ✅   |
| U7  | POST   | `/api/user/address/add`          | 🔒  | `AddressReq { receiver, phone, province, city, district, street, detail, defaulted?, label?, longitude?, latitude? }` | `Void`              | ✅   |
| U8  | GET    | `/api/user/address/{id}`         | 🔒  | Path: `id` (Long)                                                                                                     | `AddressResp`       | ✅   |
| U9  | PUT    | `/api/user/address/update/{id}`  | 🔒  | Path: `id` (Long), Body: `AddressReq`                                                                                 | `Void`              | ✅   |
| U10 | PUT    | `/api/user/address/default/{id}` | 🔒  | Path: `id` (Long)                                                                                                     | `Void`              | ✅   |
| U11 | DELETE | `/api/user/address/delete/{id}`  | 🔒  | Path: `id` (Long)                                                                                                     | `Void`              | ✅   |

**`AddressResp`** (V5.5：删除 `updateTime`——前端全仓无任何读取点，最小暴露面；领域模型内部审计口径不变，仅出参不投影):

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
  "createTime": "2026-06-01T10:00:00"
}
```

> 该 VO 为 U6/U8（§4.2.2）与 I10 `/internal/address/{id}`（§4.14.2）共用契约。

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

#### 4.2.4 BalanceController — `/api/user/balance` — ⚠️ 已整体迁移（V5.3 边界重构）

> 余额域已从 user-service 迁出至 wallet-service（`user.balance` 冻结），本节 U14/U15 两端点**已下线**，新契约见 §4.2.10 `/api/wallet`。

#### 4.2.5 PointsController — `/api/points` (marketing-service, V5.4 路径更正)

> 积分域归属 marketing-service（网关路由 `/api/points/**` → marketing-service）；历史文档路径 `/api/user/points/*` 已不存在。

| #   | 方法  | 路径                         | 认证  | 请求                           | 响应                                       | 阶段  |
| --- | --- | -------------------------- | --- | ---------------------------- | ---------------------------------------- | --- |
| U16 | GET | `/api/points/balance` | 🔒  | —                            | `PointsBalanceVO { userId(Long), balance(long) }`（V5.4 弱类型 Map → 强类型） | ✅   |
| U17 | GET | `/api/points/history` | 🔒  | Query: `page`(1), `size`(20) | `IPage<PointsLogVO>`                     | ✅   |

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
| U22 | GET    | `/api/user/history` | 🔒  | Query: `size`(20)         | `List<HistoryVO>`（V5.4 弱类型 `List<Map>` → 强类型，JSON 键名不变） | ✅   |
| U23 | DELETE | `/api/user/history` | 🔒  | —                         | `Void`（清空全部）               | ✅   |

**`HistoryVO`**（snake_case JSON 键名沿用迁移前报文，显式 `@JsonProperty` 固定）:

```json
{
  "id": 1,
  "product_id": 1001,
  "view_time": "2026-08-02T10:30:00",
  "product_name": "手工陶瓷茶杯",
  "main_image": "https://...",
  "brand": "冰美精选",
  "price": 12800
}
```

#### 4.2.8 Account Security (🆕 V5.0 计划)

| #   | 方法   | 路径                                  | 认证  | 请求                             | 响应                 | 阶段     |
| --- | ---- | ----------------------------------- | --- | ------------------------------ | ------------------ | ------ |
| U24 | PUT  | `/api/user/security/password`       | 🔒  | `{ oldPassword, newPassword }` | `Void`             | ⚪ V5.0 |
| U25 | PUT  | `/api/user/security/phone`          | 🔒  | `{ newPhone, code, password }` | `Void`             | ⚪ V5.0 |
| U26 | POST | `/api/user/security/delete-account` | 🔒  | `{ password, code, reason? }`  | `Void`（软删除，30天冷静期） | ⚪ V5.0 |
| U27 | POST | `/api/user/security/reactivate`     | 🌐  | `{ phone, code }`              | `Void`（冷静期内恢复账号）   | ⚪ V5.0 |

#### 4.2.9 Notification Center — `/api/notifications` (notification-service, ✅ 实现承接)

> **V5.5 补录（原 U28-U32 草案下线）**：站内信已由 notification-service 承接（网关路由 `/api/notifications/**`）。
> 用户身份取网关透传 `UserContext`；归属校验在仓储条件内兜底（只能读/删自己的通知）。

| #   | 方法     | 路径                                     | 认证  | 请求                                                             | 响应                      | 阶段     |
| --- | ------ | ---------------------------------------- | --- | ---------------------------------------------------------------- | ----------------------- | ------ |
| U28 | GET    | `/api/notifications`                     | 🔒  | Query: `type?`(String 业务类型), `pageNum`(1), `pageSize`(10) | `IPage<InAppMessageVO>` | ✅ V5.2+ |
| U29 | GET    | `/api/notifications/unread-count`        | 🔒  | —                                                              | `Map<String,Integer>` `{"unread": n}` | ✅ V5.2+ |
| U30 | PUT    | `/api/notifications/{id}/read`           | 🔒  | Path: `id` (Long)                                              | `Void`                  | ✅ V5.2+ |
| U31 | PUT    | `/api/notifications/read-all`            | 🔒  | —                                                              | `Void`                  | ✅ V5.2+ |
| U32 | DELETE | `/api/notifications/{id}`                | 🔒  | Path: `id` (Long)                                              | `Void`                  | ✅ V5.2+ |

**`InAppMessageVO`**:

```json
{
  "id": 1,
  "recipientId": 1001,
  "senderId": null,
  "senderName": "系统",
  "senderAvatar": null,
  "type": "ORDER",
  "title": "订单已发货",
  "content": "您的订单 ord-xxx 已由顺丰速运发出",
  "linkUrl": "/orders/ord-xxx",
  "isRead": 0,
  "createTime": "2026-08-02T15:30:00"
}
```

> 跳转链接口径（ADR-019）：店铺相关通知 `linkUrl=/shop/{shopId}`（新 shopId 口径），订单类通知仍指向订单页。
> 系统通知无发起者时 `senderId`/`senderAvatar` 为 null；`isRead` 为 Integer（0 未读 / 1 已读）。

#### 4.2.10 WalletController — `/api/wallet` (wallet-service, 🆕 V5.4 承接余额域)

> 承接原 user-service `/api/user/balance`（§4.2.4 已下线）。金额单位分。

| #   | 方法   | 路径                           | 认证  | 请求                                   | 响应              | 阶段     |
| --- | ---- | ---------------------------- | --- | ------------------------------------ | --------------- | ------ |
| W1  | GET  | `/api/wallet`                | 🔒  | —                                    | `Integer`（余额，分） | ✅      |
| W2  | POST | `/api/wallet/recharge`       | 🔒  | Query/表单参数: `amount` (Integer, 分)    | `Integer`（最新余额） | ✅      |

> **W2 校验收紧（V5.4）**：入参对象化为 `WalletRechargeReq`（参数名 `amount` 与 HTTP 契约不变，非 JSON body）；`amount` 缺失或 ≤0 由 500 改为 **400 `PARAM_ERROR(1101)`**。
> **幂等说明**：充值当前无幂等键，重复提交会重复入账；接入真实支付渠道时需补幂等（代码内已标注待办）。

---

### 4.3 商家/店铺服务 (merchant-service)

> **V5.4（ADR-019）商家/店铺 1:N**：一个商家（seller，归属商家 user_id）可拥有多间店铺（shop，以 `shopId` 寻址）；
> 对外 `sellerId` 口径不变（= 商家 user_id）。新接入方一律使用 `shopId` 口径端点；
> 遗留 sellerId/默认店铺路径标注 `@Deprecated`，保留一个版本周期（见 §1.6）。

#### 4.3.1 MerchantController — `/api/seller`

| #    | 方法   | 路径                         | 认证  | 请求                                                                                                   | 响应              | 阶段  |
| ---- | ---- | -------------------------- | --- | ---------------------------------------------------------------------------------------------------- | --------------- | --- |
| S1   | POST | `/api/seller/apply`        | 🔒  | `MerchantApplyReq { shopName, contactPhone, province?, city?, district?, detailAddress? }`（语义见 §4.6.6 T25） | `Void`     | ✅ V5.4（原 `/api/seller/register` 更名） |
| S1a  | GET  | `/api/seller/shops`        | 🔒  | —                                                                                                    | `List<ShopVO>`（本人全部店铺；单店铺商家返回长度 1） | 🆕 V5.4 |
| S1b  | PUT  | `/api/seller/shops/{shopId}` | 🔒 | Path: `shopId` (Long), Body: `UpdateShopReq { shopName?, shopLogo?, contactPhone?, province?, city?, district?, detailAddress? }` | `Void`（null-safe 部分更新；非本人店铺 → 403 FORBIDDEN） | 🆕 V5.4 |
| S1c  | GET  | `/api/seller/shops/{shopId}/decoration` | 🔒 | Path: `shopId` (Long) | `ShopDecorationVO { announcement?, banners:[String], hotProductIds:[Long] }`（未装修 → 各字段空值/空数组） | ✅ V5.12 |
| S1d  | PUT  | `/api/seller/shops/{shopId}/decoration` | 🔒 | Path: `shopId` (Long), Body: `ShopDecorationReq { announcement?(≤200), banners?(≤5 个 URL), hotProductIds?(≤8 个商品 id，去重) }` | `Void`（归属校验同 S1b；UPSERT；**hotProductIds Phase 1 不校验商品归属**——展示端过滤失效 id，ADR-048） | ✅ V5.12 |
| S2   | GET  | `/api/seller/shop`         | 🔒  | —                                                                                                    | `ShopVO`（默认店铺） | ✅ @Deprecated V5.4 |
| S3   | PUT  | `/api/seller/shop`         | 🔒  | `UpdateShopReq { shopName?, shopLogo?, contactPhone?, province?, city?, district?, detailAddress? }` | `Void`（更新默认店铺） | ✅ @Deprecated V5.4 |

> **S1 入驻语义细化（V5.4）**：`POST /api/seller/apply` 在**同一事务**内创建 商家（审核中 status=0）+ 默认店铺（审核中）；
> 并发重复申请命中唯一键 → `USER_ALREADY_EXISTS(3006)`，事务整体回滚。审批通过（AD5）走聚合：商家与默认店铺同批生效。
> **S2/S3 遗留别名**：单店铺时代口径（默认店铺 = `shop.id` 最小者），保留一个版本周期，新代码请用 S1a/S1b。

**`ShopVO`**（对外统一店铺契约，ADR-019；`address` 为省市区摘要，不含详细地址，避免公开页泄漏发货明细）:

```json
{
  "shopId": 1,
  "sellerId": 5,
  "shopName": "冰美精选陶瓷",
  "shopLogo": "https://cdn.icedmall.com/shop/logo_5.jpg",
  "contactPhone": "13900139000",
  "address": "江西省景德镇市珠山区",
  "status": 1
}
```

> 状态：0=审核中，1=正常，2=冻结。原 `SellerVO`（province/city/district/detailAddress/createTime 明细）已由 `ShopVO` 取代。

#### 4.3.2 ShopsController / ShopController — 店铺公开页

| #    | 方法  | 路径                              | 认证  | 请求                                                                                   | 响应                                 | 阶段     |
| ---- | --- | ------------------------------- | --- | ------------------------------------------------------------------------------------ | ---------------------------------- | ------ |
| SH1  | GET | `/api/shops/{shopId}`           | 🌐  | Path: `shopId` (Long)                                                                | `ShopVO`（**仅 shop.status=1 可见**；不存在/非正常 → `SHOP_NOT_FOUND(3015)`；**V5.11 增量 `decoration`**: `ShopDecorationVO`——店铺装修（公告/轮播/推荐商品位），未装修 → null） | 🆕 V5.4 |
| S4   | GET | `/api/shop/{sellerId}`          | 🌐  | Path: `sellerId` (Long)                                                              | `ShopVO`（遗留解析：sellerId → 该商家默认店铺） | ✅ @Deprecated V5.4 |
| S5   | GET | `/api/shop/{sellerId}/products` | 🌐  | Path: `sellerId` (Long), Query: `page`(1), `size`(20), `sort?`(sales\|price\|newest) | `IPage<ProductVO>`                 | ⚪ 未实现（移入 §十二 roadmap——Phase A 店铺页商品列表用 P1 `sellerId` 过滤参数；shopId 口径待 Phase B） |

> **SH1（新规范，ADR-019）**：按店铺主键查询公开店铺页，仅正常营业店铺可见；网关 `/api/shops/**` 仅 GET 匿名（§六）。
> **S4（遗留兼容）**：语义为 `sellerId`（商家 user_id）→ 默认店铺解析，响应与旧契约字段兼容（返回 `ShopVO`）。

#### 4.3.3 StoreFollowController — `/api/shop/follow`

> **V5.4 起以 `shopId` 记账**（ADR-019）。参数二选一：新接入方传 Query 参数 `shopId`；遗留前端走 `/{sellerId}` 路径（解析为该商家默认店铺）。

| #    | 方法     | 路径                                   | 认证  | 请求                      | 响应        | 阶段     |
| ---- | ------ | ------------------------------------ | --- | ----------------------- | --------- | ------ |
| SF1  | POST   | `/api/shop/follow?shopId=`           | 🔒  | Query: `shopId` (Long)  | `Boolean` | 🆕 V5.4 |
| SF2  | DELETE | `/api/shop/follow?shopId=`           | 🔒  | Query: `shopId` (Long)  | `Boolean` | 🆕 V5.4 |
| SF3  | GET    | `/api/shop/follow/status?shopId=`    | 🌐  | Query: `shopId` (Long)；未登录恒返回 `false` | `Boolean` | 🆕 V5.4 |
| SF4  | GET    | `/api/shop/follow/count?shopId=`     | 🌐  | Query: `shopId` (Long)  | `Long`    | 🆕 V5.4 |
| S6   | POST   | `/api/shop/follow/{sellerId}`        | 🔒  | Path: `sellerId` (Long) | `Boolean` | ✅ @Deprecated V5.4（sellerId → 默认店铺） |
| S7   | DELETE | `/api/shop/follow/{sellerId}`        | 🔒  | Path: `sellerId` (Long) | `Boolean` | ✅ @Deprecated V5.4 |
| S8   | GET    | `/api/shop/follow/{sellerId}/status` | 🌐  | Path: `sellerId` (Long) | `Boolean` | ✅ @Deprecated V5.4 |
| S9   | GET    | `/api/shop/follow/{sellerId}/count`  | 🌐  | Path: `sellerId` (Long) | `Long`    | ✅ @Deprecated V5.4 |

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
| P2  | GET  | `/api/item/product/{id}`        | 🌐  | Path: `id` (Long)                                                                                                                               | `ProductVO`（**V5.11 增量 `promotions`**: `List<PromotionTagVO> { type, name, threshold, discountValue, maxDiscount, endTime }`——该商品所属店铺**进行中**满减/满折标签；fail-open：marketing 不可用/无活动 → 空列表不报错） | `ProductVO`        | ✅   |
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
  "hotReason": "best_seller",
  "salesTags": ["热销"],
  "image": "https://cdn.icedmall.com/sku/sku_001.jpg",
  "soldCount": 320,
  "status": 1
}
```

> **hotReason 口径（V5.4）**：取值为枚举码 `discount`(限时优惠) / `new_arrival`(新品上市) / `best_seller`(热卖爆款) / `clearance`(清仓特价)（`HotReasonEnum`，非法码服务端归一为 null 丢弃，不阻断发布）。

#### 4.4.2 ProductDetailController — `/api/item/product` (V3.5 详情页增强)

| #   | 方法  | 路径                              | 认证  | 请求                | 响应                                    | 阶段     |
| --- | --- | ------------------------------- | --- | ----------------- | ------------------------------------- | ------ |
| P6  | GET | `/api/item/product/{id}/detail` | 🌐  | Path: `id` (Long) | `ProductDetailVO`                     | ⚪ 未实现（移入 §十二 roadmap；详情由 P2 `GET /{id}` 返回 `ProductVO` 承担） |
| P7  | GET | `/api/item/product/{id}/skus`   | 🌐  | Path: `id` (Long) | `List<SkuVO>` (含 specDimensions 维度信息) | ✅ V3.5 |

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
| P27 | GET  | `/api/item/product/{productId}/similar` | 🌐  | Path: `productId` (Long), Query: `size`(缺省5, ≤20) | `List<RecommendProductVO>`（相似商品卡：按向量相似度降序，**恒过滤在售**并排除自身；ES 关闭/无相似命中 → 空列表不报错） | ✅ V5.9 |

#### 4.4.xa NoteController — `/api/notes` (item-service, ✅ V5.12 — F12 内容社区 Phase 1，[ADR-048](../adr/048-content-notes-in-item-domain.md))

> **种草笔记**：与 review 同属商品 UGC，落位 item-service（商品摘要卡同库直查，零跨服务）；Phase 1 纯文本+图集（≤9）、关联商品 ≤5（发布时校验存在且在售）、点赞 uk 幂等、作者软删。鉴定体系/富文本/评论 Phase 2 立项。

| #   | 方法   | 路径                     | 认证 | 请求                                                                                                                                    | 响应                                                                                     | 阶段      |
| --- | ---- | ---------------------- | --- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | ------- |
| P28 | POST | `/api/notes`             | 🔒  | `NotePublishReq { title(必填, ≤50), content(必填, ≤2000), coverImage?, images?(≤9 个 URL), productIds?(≤5，去重) }`                     | `NoteVO { id }`（发布即上架）                                                              | ✅ V5.12 |
| P29 | GET  | `/api/notes`             | 🌐  | Query: `page`(1), `size`(20)                                                                                                          | `Page<NoteCardVO>`（已发布，按发布时间倒序；`NoteCardVO { id, title, coverImage, likeCount, authorId, createTime }`） | ✅ V5.12 |
| P30 | GET  | `/api/notes/{id}`        | 🌐  | Path: `id` (Long)                                                                                                                     | `NoteDetailVO { id, title, content, images, likeCount, liked(登录用户视角，匿名恒 false), authorId, createTime, products: [{ productId, name, mainImage, minPrice }] }`（已发布；关联商品**在售过滤**，本地库直查） | ✅ V5.12 |
| P31 | POST | `/api/notes/{id}/like`   | 🔒  | Path: `id` (Long)                                                                                                                     | `Void`（uk 幂等：重复点赞空操作；首次原子 +1）                                              | ✅ V5.12 |
| P32 | DELETE | `/api/notes/{id}/like` | 🔒  | Path: `id` (Long)                                                                                                                     | `Void`（未点赞空操作；取消原子 -1 且不为负）                                                | ✅ V5.12 |
| P33 | DELETE | `/api/notes/{id}`      | 🔒  | Path: `id` (Long)                                                                                                                     | `Void`（**仅作者本人**，非本人 → 403；软删 status=2，流与详情不可见）                          | ✅ V5.12 |

#### 4.4.9 FreightTemplateController — `/api/item/freight-template` (item-service)

> **已实现（V5.6）**——F2 运费模型（ADR-037）。归属 item-service（商品域）；
> **路径刻意用 `/api/item/freight-template/**` 而非 `/api/seller/freight-template`**：网关 `/api/seller/**` 路由到 merchant-service（§六），而 `/api/item/**` 已有路由零网关改动（本节全为商家写/读，需 🏪）。数据模型见 04-Data-Model §十二 12.2。

| #   | 方法   | 路径                                  | 认证  | 请求                                                                                                        | 响应                    | 阶段       |
| --- | ---- | ----------------------------------- | --- | --------------------------------------------------------------------------------------------------------- | --------------------- | --------- |
| FT1 | POST | `/api/item/freight-template`        | 🏪  | `FreightTemplateSaveReq { templateName(必填, ≤50), chargeMode?(缺省1=固定运费, Phase 1 唯一值), fee(必填, ≥0, 分), freeThreshold?(缺省0=不参与满额包邮, ≥0, 分), status?(缺省1启用) }` | `FreightTemplateVO`   | ✅（V5.6） |
| FT2 | PUT  | `/api/item/freight-template/{id}`   | 🏪  | Path: `id` (Long), Body: `FreightTemplateSaveReq` 同上（全字段可局部更新）                                            | `Void`                | ✅（V5.6） |
| FT3 | GET  | `/api/item/freight-template/list`   | 🏪  | —（按当前商家过滤；无分页）                                                                                             | `List<FreightTemplateVO>`（含停用模板，按创建顺序） | ✅（V5.6） |
| FT4 | DELETE | `/api/item/freight-template/{id}` | 🏪  | Path: `id` (Long)                                                                                           | `Void`（物理删除；绑定商品经 `ON DELETE SET NULL` 自动回落商家默认模板） | ✅（V5.6） |

**`FreightTemplateVO`**:

```json
{
  "id": 1,
  "templateNo": "0c9d8b7a-6f5e-4d3c-2b1a-0f9e8d7c6b5a",
  "templateName": "全场满99包邮",
  "chargeMode": 1,
  "fee": 800,
  "freeThreshold": 9900,
  "status": 1,
  "createTime": "2026-10-03T10:00:00"
}
```

> - **错误语义（V5.6 按实现对齐，ADR-037）**：同商家模板名重复（唯一键 `uk_seller_template`）→ `3040 FREIGHT_TEMPLATE_NAME_EXISTS`；必填缺失 / `fee`<0 / `freeThreshold`<0 → 400 `PARAM_ERROR(1101)`；模板不存在 / **非本商家模板**（编辑、删除、商品绑定他人模板）→ 统一 `3041 FREIGHT_TEMPLATE_NOT_FOUND`（不区分"不存在"与"归属他人"，防探测）。原草拟的 3006/1103/403 语义不符，已作废。
> - **计价消费方**：trade 下单计价经内部端点 I43（§4.14.17）批量取模板（**降级 fail-closed**：查询失败下单抛错，不默认免邮）；下单快照 `postFee` 后模板改价/停用不影响已建订单。计价规则（最高 fee 计一次 / 满额包邮 / 默认模板回落）见 04 §十二 12.2。

**F2 验收场景（BDD，V5.6 已实现——每条 Scenario ≥1 测试）**：

```gherkin
Feature: 运费模型（F2）

  Scenario: 满包邮阈值生效
    Given 商家 5 的启用模板 fee=800 分、freeThreshold=9900 分，商品 A 绑定该模板
    When 买家下单商品 A 且订单商品总额 totalAmount = 10000 分
    Then 订单 postFee = 0，payAmount = totalAmount − discountAmount + 0

  Scenario: 多商品取最高费率模板计一次运费
    Given 商家 5 的启用模板 T1(fee=500) 与 T2(fee=1200)，商品 A 绑定 T1、商品 B 绑定 T2
    When 买家同单下单商品 A 与 B（同商家），totalAmount 低于两模板任一 freeThreshold
    Then 订单 postFee = 1200（取最高 fee 计一次，不按商品叠加）

  Scenario: 未绑定模板的商品回落商家默认模板
    Given 商家 5 先后创建启用模板 T0(fee=600)、T1(fee=900)，商品 A 的 freightTemplateId = NULL
    When 买家下单商品 A 且未达满额阈值
    Then 按 T0（该商家最早创建的启用模板）计费，postFee = 600

  Scenario: 停用模板不参与计价（回落默认）
    Given 商品 A 绑定的模板 T1(fee=900) 已被商家停用，商家仍有启用模板 T0(fee=600)
    When 买家下单商品 A
    Then 按 T0 计费 postFee = 600（不按已停用的 T1 计费）

  Scenario: 商家无任何启用模板时运费兜底为 0
    Given 商家 5 无启用模板（或仅有停用模板），商品 A 未绑定模板
    When 买家下单商品 A
    Then postFee = 0，下单不被阻断（Phase 1 兜底口径）
```

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

**`CartVO`** (V5.4: `CartItemResp` 更名 `CartItemVO`，删除 `createTime`):

```json
{
  "items": [ CartItemVO, ... ],
  "totalPrice": 38400,
  "selectedPrice": 25600,
  "allSelected": false
}
```

> - **读时回填（V5.4 口径）**：购物车表不存商品快照，`GET /api/cart` / `GET /api/cart/grouped` 读时经 item-service 批量回填 `productName / price / spec / image / subTotal`，并计算 `totalPrice / selectedPrice`。
> - **allSelected 语义**：购物车**非空** 且 全部条目已选中 且 全部可计价（回填命中）才为 `true`；空购物车为 `false`。

**`CartItemVO`**:

```json
{
  "skuId": 10,
  "productName": "手工陶瓷茶杯",
  "spec": "天青/标准",
  "image": "https://...",
  "price": 12800,
  "quantity": 2,
  "selected": true,
  "subTotal": 25600
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
      "items": [ CartItemVO, ... ],
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
| T1  | POST | `/api/trade/order`                   | 🔒  | `CreateOrderReq { addressId(必填), userCouponId?(单券, 可选), remark?(买家留言, ≤200, 可选) }`（**V5.4 契约收缩**：删除从未被读取的 `cartItemIds` 与死字段 `remark`；**V5.7 重新立项**：`remark` 作为真实买家留言回归——落 `orders.remark` 快照并随 OrderVO 回显；**V5.5 已决**：删除未接线的 `userCouponIds`，多券叠加移入 §十二 roadmap；下单商品恒取服务端购物车已勾选项；`addressId` `@NotNull`，缺失 → 400 `PARAM_ERROR`） | `OrderVO`        | ✅ V4.4 |
| T2  | GET  | `/api/trade/order/{orderNo}`         | 🔒  | Path: `orderNo` (String)                                                             | `OrderVO`        | ✅      |
| T3  | GET  | `/api/trade/order/page`              | 🔒  | Query: `status?`, `keyword?`, `startDate?`, `endDate?`, `page`(1), `size`(20)        | `IPage<OrderVO>` | ✅      |
| T4  | POST | `/api/trade/order/{orderNo}/cancel`  | 🔒  | Path: `orderNo` (String), Body: `{ reason? }`                                        | `Void`           | ✅      |
| T5  | POST | `/api/trade/order/{orderNo}/confirm` | 🔒  | Path: `orderNo` (String)                                                             | `Void`           | ✅      |
| T6  | POST | `/api/trade/order/direct`            | 🔒  | `DirectOrderReq { skuId(Long, 必填), quantity(≥1, 缺省1), addressId(必填), userCouponId?(单券, 可选), remark?(买家留言, ≤200, 可选——V5.7) }`（**V5.5 已决**：删除未接线的 `userCouponIds`） | `OrderVO`        | ✅ V4.0 |

**`CreateOrderReq`**:

```json
{
  "addressId": 1,
  "userCouponId": 5
}
```

> **多券叠加（V5.5 已决）**：`userCouponIds` 字段已从 `CreateOrderReq`/`DirectOrderReq` 删除（Controller 从未装配）；策略层多券逻辑保留，接线前置条件为《多券金额分摊规则》PRD——挂账 §十二 roadmap、§十一 开放项 O1。

**`OrderVO`** (V5.4 字段修剪：删除 `id`（技术主键不外泄，对外一律用业务键 `orderNo`）与 `paymentType`（恒 null 死字段）):

```json
{
  "orderNo": "ord-20260802-001",
  "userId": 1001,
  "sellerId": 5,
  "totalAmount": 25600,
  "payAmount": 24400,
  "discountAmount": 2000,
  "postFee": 800,
  "remark": "请包装牢固，谢谢",
  "status": 1,
  "orderType": 1,
  "receiverName": "张三",
  "receiverPhone": "13800138000",
  "receiverAddress": "北京市朝阳区望京SOHO T1 1205",
  "createTime": "2026-08-02T10:30:00",
  "payTime": null,
  "consignTime": null,
  "endTime": null,
  "items": [ OrderItemVO, ... ]
}
```

> - `items` 仅详情接口装配，列表接口为 `null`。秒杀订单（orderType=3）`sellerId=0`（平台活动无店铺归属，见 §4.10.2 M10）。
> - **`postFee`（✅ V5.6 已实现增量，ADR-037）**：运费（分）。**金额不变式（V5.6 起生效）**：`payAmount = totalAmount − discountAmount + postFee`（`totalAmount` 仍 = Σ orderItem.subTotal，运费不进商品小计；计价口径见 §4.4.9 与 04-Data-Model §十二 12.2；存量库须先执行 V5.6 迁移补 `orders.post_fee` 列）。
> - **`items[].packageNo`（✅ V5.6 已实现增量）**：见 `OrderItemVO`。

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
  "image": "https://...",
  "packageNo": null
}
```

> **`packageNo`（✅ V5.6 已实现增量）**：明细归属包裹号（对应物流记录 `package_no`，§4.8.1）；`null`=尚未发货（一明细只发一次）。

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
| T12 | POST | `/api/trade/seller/order/{orderNo}/ship`   | 🏪  | Path: `orderNo` (String), Body: `ShipOrderReq { logisticsNumber(必填), logisticsCompany(必填), skuIds?: [Long]（✅ V5.6 已实现增量——本包裹发货明细；空/缺省=全部剩余明细，兼容单包裹语义） }`（**V5.4 收紧**：logisticsNumber/logisticsCompany 缺失 → 400 `PARAM_ERROR`） | `Void`           | ✅（skuIds 增量 V5.6） |
| T13 | PUT  | `/api/trade/seller/order/{orderNo}/remark` | 🏪  | Path: `orderNo` (String), Body: `{ sellerRemark }`                      | `Void`           | 🆕 ⚪ V5.0 |
| T14 | PUT  | `/api/trade/seller/order/{orderNo}/price`  | 🏪  | Path: `orderNo` (String), Body: `{ discountAmount }` (商家改价，仅限待付款)       | `Void`           | 🆕 ⚪ V5.0 |

> **T12 拆单发货语义（✅ V5.6 已实现——F3，ADR-037，数据模型见 04-Data-Model §十二 12.3）**：
> - `skuIds` 为**本包裹**发货明细（对应 `order_item.package_no` 标记 + 新增一条 `order_logistics` 包裹记录）；空/缺省 = 全部剩余未发货明细（存量单包裹行为兼容）。发货为 trade 本地事务（校验 → 条件更新打 `package_no` → 判定发完 → 状态迁移），物流记录（Feign）与领域事件在事务外完成。
> - **部分发货订单保持"待发货(2)"**，全部明细发货后才迁移"待收货(3)"（不新增订单状态，复用 §九 状态机；`consignTime` 首包写入）。
> - **错误语义**：`skuIds` 含不属于本订单的 sku → 400 `PARAM_ERROR(1101)`；含已发货明细（`package_no` 已标记）→ `ORDER_STATUS_INVALID(3010)`（一明细只发一次）；订单状态不支持发货 / 并发下条件更新未全命中 → `3010`（刷新重试）。
> - 物流查询端点 L1（§4.8.1）随之改为按包裹返回**列表**（破坏性变更，已随 V5.6 切换）。

**F3 验收场景（BDD，V5.6 已实现——每条 Scenario ≥1 测试）**：

```gherkin
Feature: 拆单发货（F3）

  Scenario: 一单两包裹先后发货
    Given 订单 O 含明细 sku1、sku2，状态=待发货(2)
    When 商家第一次发货本包裹 skuIds=[sku1]（运单 SF001）
    Then 订单仍为待发货(2)，order_item(sku1).packageNo=1，产生 packageNo=1 的物流记录
    When 商家第二次发货本包裹 skuIds=[sku2]（运单 SF002）
    Then 全部明细已发货，订单迁移为待收货(3)，产生 packageNo=2 的物流记录

  Scenario: 空 skuIds 全量发货兼容单包裹语义
    Given 订单 O 含明细 sku1、sku2，状态=待发货(2)，此前无任何包裹
    When 商家发货且请求体不传 skuIds（或传空数组）
    Then 全部剩余明细打上同一 packageNo=1，订单直接迁移为待收货(3)（与存量单包裹行为一致）

  Scenario: 重复发货同一 sku 被拒
    Given 订单 O 的明细 sku1 已归属 packageNo=1
    When 商家再次发货 skuIds=[sku1]
    Then 返回 ORDER_STATUS_INVALID(3010)，不新增物流记录、订单状态不变

  Scenario: 发货明细校验归属
    Given 订单 O（商家 5）与订单 O'（商家 6）各自含明细
    When 商家 5 对订单 O 发货 skuIds 含订单 O' 的 skuId
    Then 返回 400 PARAM_ERROR(1101)，无任何状态变更
```

#### 4.6.4 AfterSaleController — `/api/after-sale`

| #   | 方法   | 路径                               | 认证  | 请求                                                                                                       | 响应                   | 阶段        |
| --- | ---- | -------------------------------- | --- | -------------------------------------------------------------------------------------------------------- | -------------------- | --------- |
| T15 | POST | `/api/after-sale`                | 🔒  | `AfterSaleApplyReq { orderNo(必填), type(必填, Integer: 1退货退款/2仅退款), reason?, refundAmount?(≥1), logisticsNumber?(退货场景) }`（**V5.4 收紧**：必填缺失/退款金额 ≤0 → 400 `PARAM_ERROR`） | `AfterSaleVO`        | ✅         |
| T16 | GET  | `/api/after-sale`                | 🔒  | Query: `status?`, `page`(1), `size`(20)                                                                  | `IPage<AfterSaleVO>` | ✅         |
| T17 | GET  | `/api/after-sale/{id}`           | 🔒  | Path: `id` (Long)                                                                                        | `AfterSaleVO`        | 🆕 ⚪ V5.0 |
| T18 | POST | `/api/after-sale/{id}/cancel`    | 🔒  | Path: `id` (Long)                                                                                        | `Void`               | 🆕 ⚪ V5.0 |
| T19 | POST | `/api/after-sale/{id}/ship-back` | 🔒  | Path: `id` (Long), Body: `{ logisticsNumber, logisticsCompany }`                                         | `Void` (退货填写物流)      | 🆕 ⚪ V5.0 |

**`AfterSaleVO`** (V5.5 按实现对齐：删除 `evidenceImages`/`statusText`/`sellerRemark`/`updateTime` 草案字段，补 `userId`/`logisticsNumber`):

```json
{
  "id": 1,
  "orderNo": "ord-xxx",
  "userId": 1001,
  "type": 1,
  "reason": "商品与描述不符",
  "refundAmount": 12800,
  "status": 1,
  "adminRemark": null,
  "logisticsNumber": null,
  "createTime": "2026-08-02T15:00:00"
}
```

**售后类型枚举（V5.5 已决）**：

| 取值 | 含义 | 说明 |
| --- | --- | --- |
| `1` | 退货退款 | 需退回商品，`logisticsNumber` 退货场景填写 |
| `2` | 仅退款 | 不退回商品 |

> `type` 为 Integer，`@Range(1..2)` 校验，非法取值（如 9）→ 400 `PARAM_ERROR(1101)`（Adapter 层拒绝，不再落库脏数据）。
> **状态口径（✅ V5.6 资金线激活）**：`REFUNDED(5)` 由退款成功经 I42 回写激活（资金终态，见 §4.14.16）；退货退款的退款在其被**确认收到退货**（`APPROVED→RETURNED`）后触发——管理端触发点分派与守卫见 §4.13.5（AD17/AD47，ADR-037）。
> **凭证图 `evidenceImages`（V5.5 已决）**：代码未实现（请求体不接受该字段），按"不保留无排期承诺"从契约正文移除，挂账 §十二 roadmap。

#### 4.6.5 SellerFinanceController — `/api/seller/finance`

| #   | 方法   | 路径                                    | 认证  | 请求                                                                  | 响应                                         | 阶段  |
| --- | ---- | ------------------------------------- | --- | ------------------------------------------------------------------- | ------------------------------------------ | --- |
| T20 | GET  | `/api/seller/finance/settlement/page` | 🏪  | Query: `page`(1), `size`(20)                                        | `IPage<SettlementVO>`                      | ✅   |
| T21 | GET  | `/api/seller/finance/settlement/{id}` | 🏪  | Path: `id` (Long)                                                   | `SettlementVO`                             | ✅   |
| T22 | GET  | `/api/seller/finance/balance`         | 🏪  | —                                                                   | `Map<String,Object>` `{sellerId, balance}` | ✅   |
| T23 | POST | `/api/seller/finance/withdrawal`      | 🏪  | `WithdrawalApplyReq { amount(必填, ≥1), bankAccount?, bankName? }`（**V5.4 收紧**：amount 缺失/≤0 → 400 `PARAM_ERROR`） | `WithdrawalVO`（仅脱敏账号）                             | ✅   |
| T24 | GET  | `/api/seller/finance/withdrawal/page` | 🏪  | Query: `page`(1), `size`(20)                                        | `IPage<WithdrawalVO>`（仅脱敏账号）                      | ✅   |

**`WithdrawalVO`**（V5.5 已决——收款账号分端口径，明文不下商家端）:

```json
{
  "id": 1,
  "withdrawalNo": "5f8a9c0d1e2b3a4f6c7d8e9f0a1b2c3d",
  "sellerId": 5,
  "amount": 1280000,
  "status": 1,
  "bankAccount": null,
  "bankAccountMasked": "****1234",
  "bankName": "中国工商银行",
  "adminRemark": null,
  "createTime": "2026-08-02T10:00:00"
}
```

> - **商家端**（T23/T24，`/api/seller/finance/withdrawal*`）：仅返回 `bankAccountMasked`（保留末 4 位），`bankAccount=null`——防商家端页面/抓包泄露完整收款账号。
> - **管理端**（AD22，`/api/admin/finance/withdrawal/page`，权限保护）：`bankAccount` 明文与 `bankAccountMasked` **都返回**（打款需完整账号核对）。
> - 两端共用同一 VO，装配差异由服务端承担；`withdrawalNo` 为 UUID；`status`：1=待审核 / 2=已打款 / 3=已拒绝（`adminRemark` 为拒绝原因，商家端可见）。

#### 4.6.6 SellerApplicationController — `/api/seller/apply` (merchant-service)

> **V5.4（ADR-016/019）**：入驻申请域自 trade-service 迁入 merchant-service；`POST /api/seller/apply` 即 §4.3.1 S1。

| #   | 方法   | 路径                  | 认证  | 请求                                  | 响应                           | 阶段  |
| --- | ---- | ------------------- | --- | ----------------------------------- | ---------------------------- | --- |
| T25 | POST | `/api/seller/apply` | 🔒  | `MerchantApplyReq { shopName, contactPhone, province?, city?, district?, detailAddress? }` | `Void`（**同事务**创建商家(审核中) + 默认店铺(审核中)；并发重复申请 → `USER_ALREADY_EXISTS(3006)` 回滚） | ✅   |
| T26 | GET  | `/api/seller/apply` | 🔒  | —                                   | `SellerApplicationVO` (最新申请) | ⚪ 未实现（移入 §十二 roadmap） |

---

### 4.7 支付服务 (pay-service)

#### 4.7.1 PayController — `/api/pay`

| #    | 方法   | 路径                                | 认证  | 请求                                                                         | 响应                   | 阶段     |
| ---- | ---- | --------------------------------- | --- | -------------------------------------------------------------------------- | -------------------- | ------ |
| PAY1 | POST | `/api/pay/order/{orderNo}`        | 🔒  | Path: `orderNo` (String); Query: `channel`=`WECHAT`(默认)`\|ALIPAY\|BALANCE` | `PayOrderVO`         | ✅ V2.3 |
| PAY2 | POST | `/api/pay/callback/wechat`        | 🌐  | Headers: `Wechatpay-Signature/Nonce/Timestamp/Serial`, Body: 原始回调报文（经通道适配层验签解密后提取商户订单号）        | 裸 JSON（v3 规范，见下方说明） | ✅      |
| PAY3 | POST | `/api/pay/callback/alipay`        | 🌐  | Form params (含 `out_trade_no`)                                             | `String` ("success") | ✅ V2.3 |
| PAY4 | GET  | `/api/pay/order/{orderNo}/status` | 🔒  | Path: `orderNo` (String)                                                   | `PayOrderVO`         | ✅      |

**`PayOrderVO`** (V5.4: 删除 `bizUserId` 死字段):

```json
{
  "payOrderNo": "pay-20260802-001",
  "bizOrderNo": "ord-20260802-001",
  "payChannelCode": "WECHAT",
  "amount": 23600,
  "payType": 1,
  "status": 1,
  "qrCodeUrl": "weixin://wxpay/bizpayurl?pr=xxx",
  "payOverTime": "2026-08-02T11:00:00",
  "paySuccessTime": null
}
```

> - 微信支付: `qrCodeUrl` 包含支付链接，前端生成二维码
> - 支付宝 Mock: 返回虚拟支付链接
> - 余额支付: 即时扣款，`status` 直接为成功态，无二维码
>
> **PAY2 应答体（✅ V5.5 已决，微信支付 v3 规范）**：裸 `code/message` JSON，**不包 `Result` 信封**——
> 处理成功 HTTP 200 + `{"code":"SUCCESS","message":"成功"}`；验签/解密失败或业务处理失败 HTTP 500 +
> `{"code":"FAIL","message":"处理失败，请重试"}`（非 200 触发微信按策略重推）；**幂等重复通知命中幂等分支仍 200 + SUCCESS**（避免微信把已处理通知当失败无限重推）。
> 回调报文经通道适配层（ia-integration `PaymentClient.parseWechatCallback`）验签+AES-GCM 解密后提取商户订单号（out_trade_no 断链已修复落地，ADR-033，不影响本契约）。

#### 4.7.2 Payment History (🆕 V5.0 计划)

| #    | 方法  | 路径                 | 认证  | 请求                                                                 | 响应                  | 阶段     |
| ---- | --- | ------------------ | --- | ------------------------------------------------------------------ | ------------------- | ------ |
| PAY5 | GET | `/api/pay/history` | 🔒  | Query: `page`(1), `size`(20), `channel?`, `startDate?`, `endDate?` | `IPage<PayOrderVO>` | ⚪ V5.0 |

#### 4.7.3 RefundController — 退款 (pay-service)

> **已实现（V5.6）**——F1 买家退款资金流（ADR-035 资金链路 + ADR-037 触发点修订）。
> 数据模型：`refund_order`（04-Data-Model §十二 12.1）；触发链路：**管理端售后审核同意（AD17，仅 type=2 仅退款）或确认收到退货（AD47，type=1 退货退款，§4.13.5）**→ trade 编排层（manager，禁 Service 直调 Feign；事务外触发）→ I40 `POST /internal/pay/refund`（§4.14.15）→ 渠道原路退回 → 成功后 I42 回写售后单（§4.14.16，来源 APPROVED|RETURNED）。

| #    | 方法   | 路径                                       | 认证  | 请求                                                                                   | 响应                          | 阶段       |
| ---- | ---- | ---------------------------------------- | --- | ------------------------------------------------------------------------------------ | --------------------------- | --------- |
| PAY6 | POST | `/api/pay/callback/wechat-refund`        | 🌐  | Headers: `Wechatpay-Signature/Nonce/Timestamp/Serial`, Body: 原始回调报文（经通道适配层验签+AES-GCM 解密，**复用 ADR-033 口径**） | 裸 JSON（v3 规范，同 PAY2 应答协议） | ✅（V5.6） |

> **PAY6 语义**：微信退款成功通知——解密提取退款单号（`out_refund_no` = `refund_order.refund_no`）与渠道退款单号（`refund_id` → `channel_refund_no`）→ `refund_order` 条件置成功（仅"处理中"可迁移，重复通知幂等）→ 经 I42 通知 trade 回写售后单。应答体与 PAY2 同协议（ADR-029）：成功 200 + `{"code":"SUCCESS","message":"成功"}`，失败 500 + `{"code":"FAIL","message":"处理失败，请重试"}`（触发渠道重推），幂等重复通知仍 200 + SUCCESS。入参保持原始报文（ADR-023 显式例外）。

**`RefundVO`**（管理端重试 AD43 返回；内部端点 I40 返回其子集 `{refundNo, status}`）:

```json
{
  "refundNo": "6f2c1a3e-8b4d-4e5f-9a0b-1c2d3e4f5a6b",
  "afterSaleId": 1,
  "orderNo": "ord-20260802-001",
  "channel": "WECHAT",
  "refundAmount": 12800,
  "status": 0,
  "channelRefundNo": null,
  "failReason": null,
  "createTime": "2026-10-03T10:00:00"
}
```

> `status`：0-处理中 / 1-成功 / 2-失败。**售后单状态口径**：退款处理中/失败时售后单停留 `APPROVED(2)`（可重试）；仅成功后经 I42 回写 `REFUNDED(5)`（此前 REFUNDED 为死状态，本功能激活；I42 来源含 APPROVED|RETURNED，见 §4.14.16）。

**F1 验收场景（BDD，V5.6 已实现——每条 Scenario ≥1 测试）**：

```gherkin
Feature: 买家退款资金流（F1）

  Scenario: 余额渠道退款成功加余额且流水幂等
    Given 售后单 S（type=2 仅退款）已审核同意(APPROVED)，原支付渠道=BALANCE，订单实付 12800 分
    When trade 触发退款 I40（afterSaleId=S.id）→ pay 创建 refund_order(处理中) 并调 I41 /internal/balance/refund
    Then 买家 wallet_account.balance 增加 12800 分，wallet_transaction 产生一条 type=1(收入)、bizNo=refundNo 的流水
    When 同一 transactionNo 的入账请求重复到达
    Then 幂等成功返回，余额不再增加、流水不重复（transaction_no 唯一键兜底）
    And refund_order 置成功 → I42 回写售后单 S 为 REFUNDED(5)

  Scenario: 微信回调成功激活 REFUNDED
    Given 售后单 S(APPROVED) 的退款单 R 渠道=WECHAT、状态=处理中
    When 微信退款成功通知到达 PAY6（合法签名 + AES-GCM 密文，out_refund_no=R.refundNo）
    Then 验签解密通过，refund_order 置成功并回填 channelRefundNo，售后单 S 经 I42 迁移 APPROVED→REFUNDED(5)
    And PAY6 应答 200 + {"code":"SUCCESS","message":"成功"}

  Scenario: 同售后单重复退款请求幂等
    Given 售后单 S 已存在退款单 R（处理中）
    When trade 再次触发 I40（同一 afterSaleId=S.id）
    Then 不创建新退款单，返回既有 R 的 {refundNo, status}（uk_after_sale 唯一约束兜底，天然幂等）

  Scenario: 退货退款（type=1）确认收到退货后才触发退款（ADR-037）
    Given 售后单 S（type=1 退货退款，APPROVED）原支付渠道=BALANCE
    When 管理员调用 AD17 审核同意（review status=2）
    Then 售后单停留 APPROVED，**不触发退款**（review 同意仅对 type=2 触发）
    When 管理员调用 AD47 PUT /api/admin/after-sale/{S.id}/confirm-return
    Then 售后单条件迁移 APPROVED→RETURNED(4)，trade 事务外触发 I40 退款
    And 退款成功后 I42 回写 REFUNDED(5)；对已 REFUNDED 的重复确认 → REFUND_STATUS_INVALID(3039)

  Scenario: 退款失败售后单停留 APPROVED 可重试
    Given 售后单 S(APPROVED) 的退款单 R 渠道=WECHAT 请求渠道退款失败（渠道返回错误）
    Then R 置为失败(2)并记录 failReason，售后单 S 停留 APPROVED(2)（不迁移 REFUNDED）
    When 管理员调用 AD43 POST /api/admin/pay/refund/{R.refundNo}/retry
    Then R 重新发起渠道退款（状态回处理中）；对非失败状态的重试返回 REFUND_STATUS_INVALID(3039)

  Scenario: 退款金额超过实付被拒（已有校验）
    Given 订单实付 payAmount = 12800 分
    When 买家申请售后 refundAmount = 13800 分
    Then 申请被拒：PARAM_ERROR(1101)"退款金额非法，需大于 0 且不超过实付金额"（T15 既有校验，退款链路不再重复放行超额退款）
```

---

### 4.8 物流服务 (logistics-service)

#### 4.8.1 LogisticsController — `/api/logistics`

| #   | 方法  | 路径                         | 认证  | 请求                     | 响应            | 阶段  |
| --- | --- | -------------------------- | --- | ---------------------- | ------------- | --- |
| L1  | GET | `/api/logistics/{orderId}` | 🔒  | Path: `orderId` (Long) | `List<LogisticsVO>`（✅ V5.6 列表化：按 `packageNo` 升序；无包裹时返回空列表——原单对象响应已随 V5.6 移除，破坏性变更生效） | ✅（列表化 V5.6） |

**`LogisticsVO`** (V5.4: 删除 `updateTime`; ✅ V5.6 增量 `packageNo`):

```json
{
  "orderId": 1,
  "packageNo": 1,
  "logisticsNumber": "SF1234567890",
  "logisticsCompany": "顺丰速运",
  "contact": "张三",
  "mobile": "13800138000",
  "province": "北京市",
  "city": "北京市",
  "district": "朝阳区",
  "street": "望京街道",
  "detail": "SOHO T1 1205",
  "status": 2,
  "createTime": "2026-08-02T15:00:00"
}
```

> **L1 列表化（✅ V5.6 已实现——F3 拆单发货，语义见 §4.6.3）**：一订单多包裹后本端点返回**包裹列表**（`packageNo` 升序，无包裹时空列表）；`packageNo` 与订单明细 `items[].packageNo`（§4.6.1）对应。前端按数组消费（破坏性变更已随 V5.6 联调切换）。

---

### 4.9 搜索服务 (search-service)

#### 4.9.1 SearchController — `/api/search`

| #   | 方法     | 路径                    | 认证  | 请求                                                                                                                                    | 响应                      | 阶段        |
| --- | ------ | --------------------- | --- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | --------- |
| SE1 | GET    | `/api/search/product` | 🌐  | Query: `keyword?`, `categoryId?`, `brand?`, `minPrice?`, `maxPrice?`, `sort?`(sales\|price\|newest\|relevance), `page`(1), `size`(20)（**V5.10 补齐实现**：brand/minPrice/maxPrice/sort 过滤此前声明未实现——ES 链 filter+sort，DB 链同口径过滤；`newest` 按 `publishTime`） | `Page<ProductSearchVO>` | ✅         |
| SE2 | GET    | `/api/search/hot`     | 🌐  | Query: `limit`(10)                                                                                                                    | `List<String>`          | ✅         |
| SE3 | GET    | `/api/search/history` | 🔒  | Query: `limit`(10)                                                                                                                    | `List<String>`          | ✅         |
| SE4 | DELETE | `/api/search/history` | 🔒  | —                                                                                                                                     | `Void` (清空搜索历史)         | 🆕 ⚪ V5.0 |
| SE5 | GET    | `/api/search/suggest` | 🌐  | Query: `keyword` (前缀)                                                                                                                 | `List<String>` (搜索建议)   | 🆕 ⚪ V5.0 |
| SE6 | GET    | `/api/search/facets`  | 🌐  | Query: `keyword?`, `categoryId?`                                                                                                        | `SearchFacetsVO { brands:[{name,count}], priceRanges:[{min,max,count}] }`（品牌 terms 聚合 + 固定价格档位计数：0-50/50-100/100-200/200-500/500+ 元；**ES 链实现，DB 链返回空对象**） | ✅ V5.10 |

**`ProductSearchVO`** (V5.4: 删除 `embedding` 字段):

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
  "soldCount": 1280
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
| M7  | GET  | `/api/coupon/count`            | 🔒  | —                                                          | `Map<String,Integer>` `{available, used, expired}`（我的券三态计数） | ✅ V5.11 |

**`CouponVO`** (券模板；V5.4 按实现修正字段名：`minAmount` / `totalQty` / `issuedQty`):

```json
{
  "couponId": "cpn-001",
  "name": "618满200减30",
  "type": 1,
  "value": 3000,
  "minAmount": 20000,
  "sellerId": null,
  "totalQty": 10000,
  "issuedQty": 6523,
  "startTime": "2026-06-01T00:00:00",
  "endTime": "2026-06-18T23:59:59",
  "status": 1,
  "discountType": 1,
  "couponCategory": 1,
  "grantType": 1,
  "stockType": 1,
  "grabType": 1,
  "priceInCents": 3000,
  "scopeType": 1,
  "scopeValues": null,
  "stackRule": 1,
  "stackGroup": null
}
```

**`UserCouponVO`** (用户持有的券；V5.4 按实现修正字段名：`id` / `createTime`):

```json
{
  "id": 5,
  "couponId": "cpn-001",
  "status": 1,
  "usedOrderNo": null,
  "useTime": null,
  "createTime": "2026-06-10T10:00:00"
}
```

> 券的 `userCouponId` 对外口径 = `UserCouponVO.id`。

#### 4.10.2 FlashSaleController — `/api/flash`

| #   | 方法   | 路径                    | 认证  | 请求                                          | 响应                             | 阶段        |
| --- | ---- | --------------------- | --- | ------------------------------------------- | ------------------------------ | --------- |
| M8  | GET  | `/api/flash`          | 🌐  | —                                           | `List<FlashSaleVO>` (进行中的秒杀场次) | ✅         |
| M9  | GET  | `/api/flash/{id}`     | 🌐  | Path: `id` (Long)                           | `FlashSaleVO` (秒杀详情)           | 🆕 ⚪ V5.0 |
| M10 | POST | `/api/flash/buy`      | 🔒  | Query: `flashId` (Long), `addressId` (Long) | `FlashBuyVO`（**V5.4 口径**：`orderNo` 为真实订单号；链路 = Redis 预扣 → trade-service 落单 → MQ 异步统计；秒杀订单 `sellerId=0`——平台活动无店铺归属） | ✅ V4.1    |
| M11 | GET  | `/api/flash/timeline` | 🌐  | —                                           | `List<FlashSessionVO>` (秒杀时间段) | 🆕 ⚪ V5.0 |

#### 4.10.3 TaskController — `/api/task` (marketing-service, ✅ V5.10)

> **F9 每日任务体系（[ADR-047](../adr/047-daily-task-system.md)）**：表驱动任务定义（`task_def` seed：DAILY_ORDER 确认收货 +20 / DAILY_REVIEW 发表评价 +10）+ 每日幂等完成记录（`uk_user_task_date`）。积分经 `PointsManager` 单出口（`points_log` type=3 任务奖励），与任务记录同事务。

| #   | 方法 | 路径             | 认证 | 请求 | 响应                                                                 | 阶段      |
| --- | -- | -------------- | --- | --- | ------------------------------------------------------------------ | ------- |
| M14 | GET | `/api/task/daily` | 🔒  | —   | `List<DailyTaskVO> { code, name, rewardPoints, completed }`（今日任务 + 完成状态） | ✅ V5.10 |

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

**`FlashBuyVO`** (V5.4: `orderNo` 修复为真实订单号——此前落单链路断裂恒空):

```json
{
  "success": true,
  "orderNo": "ord-20260802-001",
  "flashId": 1,
  "flashPrice": 9900,
  "productId": 1,
  "remainingStock": 65
}
```

#### 4.10.3 Marketing Activities (🆕 V5.0 计划 — 京东标准)

| #   | 方法  | 路径                   | 认证  | 请求                                                    | 响应                 | 阶段     |
| --- | --- | -------------------- | --- | ----------------------------------------------------- | ------------------ | ------ |
| M12 | GET | `/api/activity`      | 🌐  | Query: `type?`(FULL_REDUCE\|LIMIT_DISCOUNT\|NEW_USER，缺省=全部) | `List<ActivityVO>`（**进行中**活动；数据源=promotion 表：1满减→FULL_REDUCE、2满折→LIMIT_DISCOUNT；**NEW_USER 暂无活动数据源 → 空列表**，Phase 2 接新人礼活动） | ✅ V5.11 |
| M13 | GET | `/api/activity/{id}` | 🌐  | Path: `id` (Long)                                     | `ActivityVO`（按 promotion id 查详情；不存在 → `NO_RESOURCE_FOUND(1103)`）       | ✅ V5.11 |

**`ActivityVO`**（V5.11 定稿，源自 `promotion` 表进行中活动）:

```json
{
  "id": 1,
  "type": "FULL_REDUCE",
  "name": "春季家装满减",
  "sellerId": 5,
  "threshold": 30000,
  "discountValue": 4000,
  "maxDiscount": 0,
  "startTime": "2026-10-01T00:00:00",
  "endTime": "2026-10-31T23:59:59"
}
```

> - 满减（FULL_REDUCE）：`discountValue` = 减免金额（分）；满折（LIMIT_DISCOUNT）：`discountValue` = 折扣率万分比（8500=8.5折），`maxDiscount` = 封顶减免（0=不封顶）。
> - 下单计价口径（ADR-043）与活动展示同源（同 `promotion` 表），用户所见即所得。

---

### 4.11 AI 服务 (ai-service)

> 当前实现: V2.5 生产就绪 ✅ | 完整选型见 [13-AI-Technology-Selection.md](../backend/13-AI-Technology-Selection.md)
> AI 服务通过 `ai.enabled` 配置开关，关闭时返回静态提示。

#### 4.11.1 AiAssistantController — `/api/ai`

| #   | 方法   | 路径                    | 认证  | 请求                                           | 响应                      | 阶段     |
| --- | ---- | --------------------- | --- | -------------------------------------------- | ----------------------- | ------ |
| AI1 | POST | `/api/ai/chat`        | 🔒  | `AiChatRequest { message, conversationId? }` | `AiChatResponse`        | ✅ V2.0 |
| AI2 | POST | `/api/ai/chat/stream` | 🔒  | `AiChatRequest { message, conversationId? }` | SSE `text/event-stream` | ✅ V2.5 |

**`AiChatRequest`** (AI1-AI4 共用；`sellerId` 为 V5.5 新增可空字段，仅 AI3/AI4 客服会话消费):

```json
{
  "message": "500以内适合夏天穿的透气跑鞋",
  "conversationId": "conv-uuid-xxx",
  "sellerId": null
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
| AI3 | POST | `/api/ai/cs/chat`        | 🔒  | `AiChatRequest { message, conversationId?, sellerId? }` | `AiChatResponse`        | ✅ V2.0 |
| AI4 | POST | `/api/ai/cs/chat/stream` | 🔒  | `AiChatRequest { message, conversationId?, sellerId? }` | SSE `text/event-stream` | ✅ V2.5 |

> **sellerId（🆕 V5.5，向后兼容）**：可空——商家端客服会话传入，作为 RAG 检索范围（限定该商家知识库）。
> `ai.rag.enabled=true` 时按 `sellerId` 检索商家知识向量注入上下文；RAG 关闭 / 未带 `sellerId` / 检索异常或无命中时**降级为无 RAG 直答**，绝不因检索问题打断客服应答。
> `sellerId` 仅 AI3/AI4 客服会话消费（AI1/AI2 购物助手会话忽略该字段）。

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

**`ConversationDetail`** (V5.4: messages 删除 `toolCalls`——内部诊断信息且原恒 null，消息项仅 `role/content/timestamp`):

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
      "timestamp": "2026-07-19T10:30:05"
    }
  ]
}
```

---

### 4.12 买家-商家消息服务 (ai-service)

> **大厂对标**: 淘宝旺旺/京东咚咚。WebSocket + STOMP 协议。**V5.4 归属更正**：IM 域实现于 ai-service（网关 `/api/chat/**` → ai-service，见 §六），非 user-service。

#### 4.12.1 Chat WebSocket

| #   | 方法  | 路径         | 说明                                                          | 阶段     |
| --- | --- | ---------- | ----------------------------------------------------------- | ------ |
| CH1 | WS  | `/ws/chat` | STOMP 端点。订阅 `/user/queue/messages` 接收消息；发送 `/app/chat.send` | ✅ V3.3 |

> **握手鉴权（🆕 V5.5）**：浏览器 STOMP/SockJS 连接 URL 需带 `?token=<accessToken>`——网关 `WsHandshakeTokenFilter` 将其换写为 `Authorization` 头后走标准 JWT 校验，token 不透传下游（详见 §三 3.5 / §五 5.1）；`/ws/chat/**` 需认证，不在白名单。

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

**`ChatConversationVO`** (V5.4 弱类型 → 强类型归位，字段以下述四个为准):

```json
{
  "conversationId": "buyer_1001_seller_5_product_P001",
  "lastMessage": "好的，今天发货",
  "lastTime": "2026-08-02T10:35:00",
  "unreadCount": 2
}
```

> 草案键名 `lastMessageTime` 以实现 `lastTime` 为准（V5.4 强类型化已固化为上述四字段）。

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
| CH11 | POST   | `/api/seller/knowledge`      | 🏪  | `KnowledgeSaveReq { title(必填), content(必填), category? }`（**V5.4 收紧**：缺失 → 400 `PARAM_ERROR`） | `MerchantKnowledgeVO`       | ✅ V3.3 |
| CH12 | PUT    | `/api/seller/knowledge/{id}` | 🏪  | Path: `id` (Long), Body: `KnowledgeUpdateReq { title?, content?, category? }`（**V5.4 对齐**：三字段均可缺省，null-safe 局部更新） | `Void`                      | ✅ V3.3 |
| CH13 | DELETE | `/api/seller/knowledge/{id}` | 🏪  | Path: `id` (Long)                                          | `Void`                      | ✅ V3.3 |

> `MerchantKnowledgeVO` 已删除 `embeddingId` 死字段（V5.4）。

---

### 4.13 管理员端 (多服务)

#### 4.13.1 AdminUserController — `/api/admin` (user-service)

| #   | 方法  | 路径                               | 认证  | 请求                                                  | 响应                    | 阶段  |
| --- | --- | -------------------------------- | --- | --------------------------------------------------- | --------------------- | --- |
| AD1 | GET | `/api/admin/user/page`           | 🔑  | Query: `page`(1), `size`(20), `keyword?`, `status?` | `IPage<UserInfoResp>` | ✅   |
| AD2 | PUT | `/api/admin/user/{id}/status`    | 🔑  | Path: `id` (Long), Query: `status` (Integer)        | `Void`                | ✅   |
| AD3 | PUT | `/api/admin/user/{id}/role`      | 🔑  | Path: `id` (Long), Query: `roleType` (Integer)      | `Void`                | ✅   |
| AD4 | GET | `/api/admin/seller/pending`      | 🔑  | Query: `page`(1), `size`(20)                        | `IPage<MerchantVO>`（**V5.4 报文收窄**：仅 `id/userId/status/createTime`，不再含店铺资料；店铺资料走 §4.3.2） | ✅   |
| AD5 | PUT | `/api/admin/seller/{id}/approve` | 🔑  | Path: `id` (Long)                                   | `Void`（**V5.4 聚合生效**：商家 + 默认店铺同批批准，并经授权域授予 `ROLE_SELLER`） | ✅   |

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

> 审计注记（ADR-032）：AD7/AD8/AD9 写操作产生审计留痕（ELK AUDIT 日志）。

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
| AD47 | PUT | `/api/admin/after-sale/{id}/confirm-return` | 🔑 | Path: `id` (Long), Query: `remark?`（可选） | `Void`（✅ V5.6 新增，ADR-037——确认收到退货） | ✅（V5.6） |

> 审计注记（ADR-032）：AD17/AD47 写操作产生审计留痕（ELK AUDIT 日志）。
>
> **退款触发点分派（✅ V5.6，ADR-037 修订 ADR-035 的触发时机；资金链路不变）**：
> - **type=2 仅退款**：AD17 审核同意（`APPROVED(2)`）后事务外立即触发退款（I40）——不需退货，同意即放款；
> - **type=1 退货退款**：AD17 同意**只落状态不触发退款**；待管理员调用 **AD47 confirm-return** 确认收到退货（条件迁移 `APPROVED(2) → RETURNED(4)`，事务内）后，事务外触发退款（I40）——货未退回不放款；
> - **守卫**：AD17/AD47 对已 `REFUNDED(5)`（资金终态）的操作均拒绝 → `REFUND_STATUS_INVALID(3039)`；AD47 对已 `RETURNED` 重复调用为幂等空操作；非退货退款类型调用 AD47 → `PARAM_ERROR(1101)`；资金侧另有 `refund_order.uk_after_sale`（一售后单一次退款）兜底幂等；
> - 退款发起失败异常上抛（fail-closed，ADR-011）：售后单停留原状态，管理端可见并经 AD43 重试收敛。

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

> 审计注记（ADR-032）：AD20/AD23 写操作产生审计留痕（ELK AUDIT 日志）。
> AD22 提现列表返回**明文 + 脱敏**收款账号（`WithdrawalVO` 分端口径见 §4.6.5）。

#### 4.13.8 AdminCouponController — `/api/admin/coupon` (marketing-service)

| #    | 方法   | 路径                                     | 认证  | 请求                                                         | 响应                | 阶段        |
| ---- | ---- | -------------------------------------- | --- | ---------------------------------------------------------- | ----------------- | --------- |
| AD24 | POST | `/api/admin/coupon`                    | 🔑  | `CouponCreateReq` (完整券模板配置)                                | `CouponVO`        | ✅ V4.0    |
| AD25 | PUT  | `/api/admin/coupon/{couponId}/disable` | 🔑  | Path: `couponId` (String)                                  | `Void`            | ✅         |

> 审计注记（ADR-032）：AD24/AD25 写操作产生审计留痕（ELK AUDIT 日志）。
| AD26 | GET  | `/api/admin/coupon`                    | 🔑  | Query: `status?`, `couponCategory?`, `page`(1), `size`(20) | `IPage<CouponVO>` | 🆕 ⚪ V5.0 |

#### 4.13.8b AdminPromotionController — `/api/admin/promotion` (marketing-service, ✅ V5.8)

> **F5 促销引擎（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）**：店铺级满减/满折活动管理。活动状态由 `startTime`/`endTime` 时间窗推导（1未开始/2进行中/3已结束，复用 AD40-42 秒杀先例）。计价口径：同一订单命中多个生效活动取**减免金额最大者**（同类型不叠加），与会员折扣（I45）、用户券三者可叠加，总额守卫 `discountAmount ≤ totalAmount`。数据模型 `promotion` 见 04 §十二 12.5。

| #    | 方法   | 路径                        | 认证 | 请求                                                                                                                                                                                                | 响应         | 阶段      |
| ---- | ---- | ------------------------- | --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------- |
| AD48 | POST | `/api/admin/promotion`      | 🔑  | `PromotionCreateReq { name(必填, ≤50), sellerId(必填), type(必填, 1满减/2满折), threshold(必填, >0, 分), discountValue(必填, >0), maxDiscount?(缺省0=不封顶, 满折用), startTime(必填), endTime(必填, >startTime) }` | `PromotionVO` | ✅ V5.8 |
| AD49 | PUT  | `/api/admin/promotion/{id}` | 🔑  | Path: `id` (Long), Body: `PromotionUpdateReq`（同 CreateReq 全可选，null-safe 局部更新；已结束活动不可编辑）                                                                                                            | `Void`       | ✅ V5.8 |
| AD50 | DELETE | `/api/admin/promotion/{id}` | 🔑  | Path: `id` (Long)                                                                                                                                                                                 | `Void`（**进行中活动禁删** → 400；物理删除） | ✅ V5.8 |

> - **错误口径**：字段非法（`threshold`≤0 / `discountValue`≤0 / 满折 `discountValue`>10000 / `endTime`≤`startTime` / 整体已过期窗口）→ 400 `PARAM_ERROR(1101)`；活动不存在 → `NO_RESOURCE_FOUND(1103)`；进行中删除 / 已结束编辑 → 400 `PARAM_ERROR`。
> - **C 端商品页促销标签** Phase 2（roadmap）；Phase 1 优惠在下单时自动应用并在 `OrderVO.discountAmount` 体现。
> - 审计：三写操作标注 `@OperationLog`（ADR-032）。

#### 4.13.9 MerchantCouponController — `/api/seller/coupon` (marketing-service)

| #    | 方法   | 路径                                      | 认证  | 请求                        | 响应               | 阶段  |
| ---- | ---- | --------------------------------------- | --- | ------------------------- | ---------------- | --- |
| AD27 | POST | `/api/seller/coupon`                    | 🏪  | `CouponCreateReq` (店铺券)   | `CouponVO`       | ✅   |
| AD28 | GET  | `/api/seller/coupon`                    | 🏪  | —                         | `List<CouponVO>` | ✅   |
| AD29 | PUT  | `/api/seller/coupon/{couponId}/disable` | 🏪  | Path: `couponId` (String) | `CouponVO`（**V5.4 行为修正**：非本店/不存在券由 200 空改为 `COUPON_NOT_FOUND(3019)`——不泄露他人券存在性） | ✅   |

#### 4.13.10 RoleController — `/api/admin/roles` (user-service, V3.1 RBAC)

| #    | 方法     | 路径                                  | 认证  | 请求                                                    | 响应                        | 阶段     |
| ---- | ------ | ----------------------------------- | --- | ----------------------------------------------------- | ------------------------- | ------ |
| AD30 | GET    | `/api/admin/roles`                  | 🔑  | —                                                     | `List<RoleVO>`            | ✅ V3.1 |
| AD31 | POST   | `/api/admin/roles`                  | 🔑  | `RoleReq { name(必填, ≤64), code(必填, ≤64), description?(≤256) }`（**V5.4 类型化**，校验失败 → 400 `PARAM_ERROR`） | `RoleVO`                  | ✅ V3.1 |
| AD32 | PUT    | `/api/admin/roles/{id}`             | 🔑  | Path: `id` (Long), Body: `UpdateRoleReq { name?(≤64), description?(≤256) }`（两字段均可选，保持 AD32 契约）    | `Void`                    | ✅ V3.1 |
| AD33 | DELETE | `/api/admin/roles/{id}`             | 🔑  | Path: `id` (Long)                                     | `Void` (is_system=1 不可删除) | ✅ V3.1 |
| AD34 | GET    | `/api/admin/roles/{id}/permissions` | 🔑  | Path: `id` (Long)                                     | `List<Long>` (权限ID列表)     | ✅ V3.1 |
| AD35 | POST   | `/api/admin/roles/{id}/permissions` | 🔑  | Path: `id` (Long), Body: `RolePermissionAssignReq { permissionIds: [1,2,3] (必填) }` | `Void`                    | ✅ V3.1 |

> 审计注记（ADR-032）：AD31/AD32/AD33/AD35 写操作产生审计留痕（ELK AUDIT 日志；user 域管理操作另写 operation_log 台账，AD6 可查）。

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

#### 4.13.13 Admin Flash Sale Management (🆕 V5.0 计划 — 保持未实现)

> **V5.7 已实现**：AD40-AD42 管理端秒杀活动管理上线（创建/编辑/删除；活动状态由 `startTime`/`endTime` 时间窗推导，库存继续走 Redis 惰性预热兜底）。历史 V5.4「未实现」告警作废。错误口径：字段非法（`flashPrice`≤0 / `stock`≤0 / `endTime`≤`startTime`）→ 400 `PARAM_ERROR(1101)`；活动不存在 → `NO_RESOURCE_FOUND(1103)`；进行中活动删除 → 400 `PARAM_ERROR`。

| #    | 方法     | 路径                      | 认证  | 请求                                            | 响应            | 阶段     |
| ---- | ------ | ----------------------- | --- | --------------------------------------------- | ------------- | ------ |
| AD40 | POST   | `/api/admin/flash`      | 🔑  | `FlashSaleCreateReq { productId(必填), skuId(必填), flashPrice(必填, >0, 分), stock(必填, >0), startTime(必填), endTime(必填, >startTime) }` | `FlashSaleVO`（活动状态由时间窗推导：1未开始/2进行中/3已结束） | ✅ V5.7 |
| AD41 | PUT  | `/api/admin/flash/{id}` | 🔑  | Path: `id` (Long), Body: `FlashSaleUpdateReq`（同 CreateReq 字段全可选，null-safe 局部更新；已结束活动不可编辑） | `Void`        | ✅ V5.7 |
| AD42 | DELETE | `/api/admin/flash/{id}` | 🔑  | Path: `id` (Long)                             | `Void`（**进行中活动禁删** → 400；物理删除，表无逻辑删除列） | ✅ V5.7 |

#### 4.13.14 AdminPayController — `/api/admin/pay` (pay-service)

> **已实现（V5.6）**——F1 退款重试 + F4 渠道对账的管理端入口（ADR-035/036）。
> **网关路由（已落地）**：`/api/admin/pay/**` → pay-service 专属路由已声明（先于 `/api/admin/**`，见 §六）。

| #    | 方法  | 路径                                            | 认证  | 请求                                                                     | 响应                             | 阶段       |
| ---- | --- | --------------------------------------------- | --- | ---------------------------------------------------------------------- | ------------------------------ | --------- |
| AD43 | POST | `/api/admin/pay/refund/{refundNo}/retry`     | 🔑  | Path: `refundNo` (String, 退款单号)                                         | `RefundVO`（重试后的退款单，见 §4.7.3）  | ✅（V5.6） |
| AD44 | GET  | `/api/admin/pay/reconciliation/batches`      | 🔑  | Query: `page`(1), `size`(20), `channel?`, `status?`, `billDate?`(yyyy-MM-dd) | `IPage<ReconciliationBatchVO>` | ✅（V5.6） |
| AD45 | GET  | `/api/admin/pay/reconciliation/batches/{id}/details` | 🔑 | Path: `id` (Long), Query: `page`(1), `size`(20), `result?`(1\|2\|3\|4) | `IPage<ReconciliationDetailVO>` | ✅（V5.6） |
| AD46 | POST | `/api/admin/pay/reconciliation/run?billDate=` | 🔑  | Query: `billDate` (yyyy-MM-dd，必填；channel 缺省=当日全部渠道)                | `List<ReconciliationBatchVO>`（本次批次列表——channel 缺省=全部渠道一次产出多条，指定单渠道时长度 1，ADR-036）  | ✅（V5.6） |

> - **AD43 退款重试**：仅 `status=失败(2)` 的退款单可重试（重新发起渠道退款，状态回处理中；微信渠道走原 refund_no 幂等）——非失败状态 → `REFUND_STATUS_INVALID(3039)`；`refundNo` 不存在 → `NO_RESOURCE_FOUND(1103)`。幂等：重复点击对同一失败单重试安全（渠道侧按退款单号幂等）。
> - **AD46 手动触发对账**：`billDate` 缺失/格式非法 → 400 `PARAM_ERROR(1101)`；同日同渠道重复执行**幂等（先删后写）**。**账单拉取失败 → 落 `status=2 失败` 批次并继续其它渠道**（可区分"账单未生成"与"当日零交易"，ADR-036）。日终自动任务走 XXL-Job（dev 默认关闭）。
> - **AD44/AD45 差异查看**：`result` 四态——1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致；**Phase 1 仅记录差异供查看，不自动调账**（本地侧只读，ADR-036）。
> - 审计注记（ADR-032）：AD43/AD46 写操作产生审计留痕（ELK AUDIT 日志）。

**`ReconciliationBatchVO`**:

```json
{
  "id": 1,
  "billDate": "2026-10-02",
  "channel": "WECHAT",
  "status": 1,
  "channelTotal": 1520000,
  "localTotal": 1520000,
  "createTime": "2026-10-03T02:00:00"
}
```

**`ReconciliationDetailVO`**:

```json
{
  "id": 100,
  "batchId": 1,
  "payOrderNo": "pay-20261002-018",
  "channelTxnId": "4200002318202610021234567890",
  "channelAmount": 9900,
  "localAmount": 12800,
  "result": 4,
  "createTime": "2026-10-03T02:00:05"
}
```

**F4 验收场景（BDD，V5.6 已实现——每条 Scenario ≥1 测试）**：

```gherkin
Feature: 渠道对账（F4）

  Scenario: 当日账单比对生成四态明细
    Given 2026-10-02 本地支付成功 3 笔（pay-001 9900 分、pay-002 12800 分、pay-003 5000 分），
      渠道账单含 pay-001(9900)、pay-002(12800)、pay-00X(6600，本地无)、pay-003 金额为 5500
    When 执行 AD46 POST /api/admin/pay/reconciliation/run?billDate=2026-10-02（WECHAT 渠道）
    Then 返回批次列表（指定单渠道时长度 1），生成批次（status=1 完成），明细四态齐备：
      | payOrderNo | result |
      | pay-001    | 1 匹配 |
      | pay-002    | 1 匹配 |
      | pay-00X    | 2 渠道有本地无 |
      | pay-003    | 4 金额不一致（channelAmount=5500, localAmount=5000） |
    And 若本地有一笔渠道账单缺失的单 → 该单 result=3 本地有渠道无

  Scenario: 重复执行同日对账幂等（先删后写）
    Given 2026-10-02 的 WECHAT 批次已存在（含 4 条明细）
    When 再次执行 AD46 POST /api/admin/pay/reconciliation/run?billDate=2026-10-02
    Then 旧批次与明细被删除并重写，仍只有一组 (billDate=2026-10-02, channel=WECHAT) 批次（uk_date_channel 兜底），管理端查询无重复

  Scenario: 仅记录差异不自动调账
    Given 对账批次存在 result=4（金额不一致）明细
    When 管理员查看 AD44/AD45
    Then 差异仅落库展示；本地 pay_order / 渠道侧均无任何自动变更（Phase 1 口径，人工处理）

  Scenario: 管理端无法触达时路由前置条件
    Given 网关未声明 /api/admin/pay/** 专属路由
    When 调用 AD44-AD46
    Then 请求被兜底路由 /api/admin/** 送至 user-service 而 404——实现必须先落 §六 新路由（/api/admin/pay/** → pay-service，先于 /api/admin/** 声明）
```

---

### 4.14 内部 Feign 端点 (`/internal/**`)

> 以下端点仅供集群内微服务间通过 Feign 调用。**不经过 Gateway**，返回原始类型（不包装 Result）。
>
> **V5.4 错误响应口径（全量 `/internal/**`）**：业务异常的 HTTP 状态码 = `ErrorCode` 对应状态（**不再一律 500**——修复了此前内部 BizException 一律伪装成"服务宕机"的 P1 缺陷，429/400 等确定性失败可被 Feign 调用方区分），body 为极简 `{"code":...,"msg":...}`（不包 Result 信封、不含 data）。注意：业务码 3000-3039 的 HTTP 状态本身为 200，调用方需按 body 内 `code` 判定业务失败。

#### 4.14.1 InternalAuthController — `/internal/auth` (user-service) — ⚠️ 已整体下线（ADR-017）

> **V5.2.3 认证边界收敛**：`user_auth` 凭证唯一所有权归 authorization-service，本节 I1-I6 六端点与 ia-api `AuthClient` 契约**整体废弃删除**（凭证读写全部收敛至 auth 本地 `user_auth`；注册/重置密码的错误语义见 §4.1）。保留本节仅作历史索引。
> 用户行建档入口收敛为 I9 `/internal/user/getOrCreate`（幂等，可选 `nickname`；WECHAT 类标识由 user-service 写 `wx_openid` 列）。已知取舍：端点内对 WECHAT 无二次幂等确认，依赖 auth 侧 `user_auth` 唯一键 + 建档锁（ADR-017 后果·风险 1）。

#### 4.14.1a InternalUserController — `/internal/user` (user-service)

> 纯用户查询（auth/points/balance 已拆至各自领域 InternalController）。

| #   | 方法   | 路径                          | 请求                       | 响应 (原始)        | 阶段     |
| --- | ---- | --------------------------- | ------------------------ | -------------- | ------ |
| I7  | GET  | `/internal/user/{id}`       | Path: `id` (Long)        | `UserInfoResp` | ✅      |
| I8  | GET  | `/internal/user/count`      | —                        | `long`         | ✅      |
| I9  | POST | `/internal/user/getOrCreate` | `UserAuthCO { identifier, identityType }` | `Long` (userId，必须幂等) | ✅ V5.1 |

#### 4.14.1b InternalMemberController — `/internal/user/member` (user-service, ✅ V5.8)

> **F6 会员等级与权益（[ADR-044](../adr/044-member-level-and-benefits.md)）**：调用方 trade-service——I45 定价中心取会员折扣率（下单计价）、I46 订单完成成长值入账（确认收货后）。**降级 fail-open（与 I43/I44 fail-closed 刻意不同）**：等级是准静态数据，查询失败降级为默认 NORMAL（不折扣/1倍积分）+ 告警——方向是用户少得优惠（体验损失、可补偿）而非平台资损，下单可用性优先；成长值入账失败不阻断交易（Phase 1 不补账）。幂等：I46 以 `orderNo` 唯一键锚定（一单一次成长）。

| I45 | GET  | `/internal/user/member/level?userId=` | Query: `userId` (Long, 必填) | `MemberLevelCO { level(NORMAL/SILVER/GOLD/PLATINUM), discountRate(万分比, 10000=不折扣), pointsRate(百分比, 100=1倍) }` | ✅（V5.8） |
| I46 | POST | `/internal/user/member/growth` | Body: `MemberGrowthCO { userId(必填), orderNo(必填, 幂等锚点), payAmount(必填, >0, 分=成长值) }` | `MemberLevelCO`（入账后等级权益；重复请求返回当前值） | ✅（V5.8） |

> - **升级口径（Phase 1 只升不降）**：成长值 = 累计实付金额（分），等级重算取 `max(当前等级, 门槛匹配等级)`；年度降级考核留 roadmap。规则 seed：NORMAL(0/不折/1倍)、SILVER(1000元/99折/1.1倍)、GOLD(5000元/98折/1.2倍)、PLATINUM(20000元/95折/1.5倍)，见 04 §十二 12.6。

#### 4.14.2 InternalAddressController — `/internal/address` (user-service)

| #   | 方法  | 路径                       | 请求                | 响应 (原始)       | 阶段  |
| --- | --- | ------------------------ | ----------------- | ------------- | --- |
| I10 | GET | `/internal/address/{id}` | Path: `id` (Long) | `AddressResp` | ✅   |

> ⚠️ 客户端路径修正（ADR-016）：ia-api `AddressClient` 原误拼为 `/internal/user/address/address/{id}`（运行期 404），已更正为本表路径；`AddressCO` 为服务端 `AddressResp` 的字段子集。`AddressResp` 已删 `updateTime`（V5.5，见 §4.2.2）。

#### 4.14.3 InternalWalletController — `/internal/balance` (wallet-service)

> **归属更正（V5.6.0 同步）**：余额域已随 V5.3 边界重构迁至 wallet-service（ia-api `BalanceClient` 即指向 `wallet-service`，ADR-016 收尾）；本节原标注 "(user-service)" 为文档漂移，代码实现一直在 wallet-service `InternalWalletController`。

| #   | 方法   | 路径                         | 请求                                       | 响应 (原始) | 阶段     |
| --- | ---- | -------------------------- | ---------------------------------------- | ------- | ------ |
| I11 | POST | `/internal/balance/deduct` | Query: `userId`(Long), `amount`(Integer) | `void`  | ✅ V2.3 |
| I41 | POST | `/internal/balance/refund` | Body: `BalanceRefundCO { transactionNo(CHAR36, 必填, 幂等键), userId(Long, 必填), amount(Integer, 必填, >0, 分), bizNo(String, 必填, 退款单号/订单号) }` | `void`（顺序重放按 `transaction_no` 幂等成功；真并发同号抛异常整体回滚——见下注） | ✅（V5.6） |

> - **I41 语义（✅ V5.6 已实现——F1 余额渠道退款入账，ADR-035）**：`wallet_account.balance` 增量 + `wallet_transaction`（type=1 收入，`bizNo`=退款单号）**同事务**写入；ia-api 契约对象 `BalanceRefundCO`（ADR-015 命名；线协议字段名两侧 `@JsonProperty` 显式钉死 snake_case，防 wallet 侧全局 SNAKE_CASE 策略下字段静默为 null）。调用方：pay-service（退款单渠道=BALANCE 时，见 §4.7.3 / §4.14.15）。
> - **幂等语义细化（ADR-035，按实现收敛）**：**顺序重放**（`existsByTransactionNo` 前置判定命中）视为重复请求，幂等成功返回、不重复加余额；**真并发同号**（两请求同时穿过前置判定，其一撞 `uk_transaction_no` 唯一键）→ 抛异常使本次「加余额 + 写流水」**整体回滚**——pay 侧将退款单置失败(2)，管理端 AD43 重试即命中幂等分支转成功，**资金不双记**（数据正确性优先于调用方一次成功，ADR-035「后果」登记的取舍）。
> - **错误口径（ADR-004）**：用户无钱包账户 / amount 非法 → 原始 HTTP 异常透出（400/404 族），不包 `Result`。

#### 4.14.4 InternalPointsController — `/internal/points` (user-service)

| #   | 方法   | 路径                     | 请求                                             | 响应 (原始)              | 阶段  |
| --- | ---- | ---------------------- | ---------------------------------------------- | -------------------- | --- |
| I12 | POST | `/internal/points/add` | Query: `userId`, `points`, `type`(2), `source` | `long` (new balance) | ✅   |

#### 4.14.5 InternalSkuController — `/internal/item/sku` (item-service)

| #   | 方法   | 路径                              | 请求                                             | 响应 (原始)        | 阶段  |
| --- | ---- | ------------------------------- | ---------------------------------------------- | -------------- | --- |
| I13 | GET  | `/internal/item/sku/list/batch` | Query: `ids` (List\<Long\>)                    | `List<SkuCO>` | ✅   |
| I14 | POST | `/internal/item/sku/deduct`     | `List<StockOpReq> [{ skuId(Long), quantity }]` | `void`         | ✅   |
| I15 | POST | `/internal/item/sku/restore`    | `List<StockOpReq> [{ skuId(Long), quantity }]` | `void`         | ✅   |

#### 4.14.6 InternalCartController — `/internal/cart` (cart-service)

| #   | 方法     | 路径                        | 请求                                                     | 响应 (原始)             | 阶段     |
| --- | ------ | ------------------------- | ------------------------------------------------------ | ------------------- | ------ |
| I16 | GET    | `/internal/cart/selected` | Query: `userId` (Long)                                 | `List<CartItemCO>` | ✅      |
| I17 | DELETE | `/internal/cart/clear`    | Query: `userId` (Long)                                 | `void`              | ✅      |
| I18 | DELETE | `/internal/cart/items`    | Query: `userId` (Long), Body: `List<Long>` cartItemIds | `void`              | ✅ V4.0 |

#### 4.14.7 InternalOrderController — `/internal/trade/order` (trade-service)

| #    | 方法   | 路径                                       | 请求                                                                                     | 响应 (原始)           | 阶段     |
| ---- | ---- | ---------------------------------------- | -------------------------------------------------------------------------------------- | ----------------- | ------ |
| I19  | PUT  | `/internal/trade/order/{orderNo}/status` | Path: `orderNo` (String), Query: `status` (Integer)                                    | `void`            | ✅ V5.4 收紧 |
| I20  | GET  | `/internal/trade/order/{orderNo}`        | Path: `orderNo` (String)                                                               | `OrderSummaryCO` | ✅      |
| I36  | GET  | `/internal/trade/order/id/{orderId}`     | Path: `orderId` (Long)                                                                 | `OrderSummaryCO`（供 logistics-service 归属校验） | 🆕 V5.4 |
| I21  | POST | `/internal/trade/order/create`           | `CreateOrderInternalReq { userId, skuId, quantity, addressId, flashId?, flashPrice? }` | `OrderSummaryCO` | ✅ V4.1 |

> - **I19 语义收紧（V5.4）**：走条件更新，仅接受两条合法迁移边——`待付款(1) → 待发货(2)`（支付成功）与 `待付款(1) → 已取消(5)`（超时/取消）；其余（含重复回写）抛 `ORDER_STATUS_INVALID(3010)`，由调用方感知失败。回写调用方：pay-service（支付成功 / 超时关单）。
> - **编号说明**：I36 为 V5.4 新增分配编号（顺延既有 I35，避免重排既有编号）。
>
> **`OrderSummaryCO`** (ia-api 共享契约对象):

```json
{
  "orderNo": "ord-20260802-001",
  "userId": 1001,
  "totalAmount": 25600,
  "payAmount": 23600,
  "status": 1
}
```

> **金额口径（V5.4 补充；✅ V5.6 不变式已修订落地）**：`totalAmount` = 订单**原价**（未扣券的商品合计），`payAmount` = **应付金额**。**不变式（V5.6 起）**：`payAmount = totalAmount − discountAmount + postFee`（运费单列 `orders.post_fee` 快照，不进 `totalAmount`）。`userId` = 订单归属用户（供下游数据归属校验）。**扣款/结算必须使用 `payAmount`，禁止用 `totalAmount` 发起支付**（代码注释已强制该口径）。

#### 4.14.8 InternalCouponController — `/internal/coupon` (marketing-service)

| #   | 方法   | 路径                          | 请求                                                                                                                | 响应 (原始)        | 阶段     |
| --- | ---- | --------------------------- | ----------------------------------------------------------------------------------------------------------------- | -------------- | ------ |
| I22 | POST | `/internal/coupon/use`      | Query: `userId`(Long), `userCouponId`(Long), `orderNo`(String), `orderAmount`(Integer), `orderType?`, `sellerId?` | `int` (抵扣金额，分) | ✅ V4.2 |
| I23 | POST | `/internal/coupon/rollback` | Query: `orderNo`(String)                                                                                          | `void`         | ✅      |
| I24 | POST | `/internal/coupon/grant`    | Query: `userId`(Long), `couponId`(String)                                                                         | `void` (支付后发券) | ✅ V4.4 |

#### 4.14.8b InternalPromotionController — `/internal/promotion` (marketing-service, ✅ V5.8)

> **F5 促销引擎**：trade 价格计算中心取生效促销。调用方：trade-service（下单计价，Feign 不入事务；ia-api `PromotionClient`）。**降级 fail-closed（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)，与 I43 同口径）**：查询失败 → 下单抛错——静默空集合等于「故障伪装成无促销」，用户应得优惠被吞且与促销配置口径价格不一致；「无生效活动 → 空集合」是合法业务态，与查询失败严格区分。
> **调用方扩展（🆕 V5.11）**：item-service 商品详情促销标签（P2 增量 `promotions`）复用本端点——**展示场景由调用方侧 try/catch fail-open 包装**（标签缺失=空列表，不阻断详情页），fallback 仍为 fail-closed 抛异常、语义不变；item 侧按 ADR-041 先例加 60s Caffeine 缓存（促销分钟级生效可接受）。

| I44 | POST | `/internal/promotion/query` | Body: `PromotionQueryCO { sellerId(Long, 必填) }` | `List<PromotionCO>`（该商家进行中活动：id/name/sellerId/type/threshold/discountValue/maxDiscount；空集合=无生效活动） | ✅（V5.8） |

#### 4.14.9 InternalLogisticsController — `/internal/logistics` (logistics-service)

#### 4.14.8c InternalTaskController — `/internal/task` (marketing-service, ✅ V5.10)

> **F9 任务完成触发口（[ADR-047](../adr/047-daily-task-system.md)）**：调用方——trade（确认收货 → `DAILY_ORDER`）、item（评价落库 → `DAILY_REVIEW`），均**事务外 fail-open**（旁路，与触达接线 ADR-042 同口径：失败仅告警，不影响主流程）。幂等在服务端：同用户同任务同业务日唯一，重复完成返回 `completed=false`。

| I49 | POST | `/internal/task/complete` | Body: `TaskCompleteCO { userId(必填), taskCode(必填) }` | `TaskCompleteResultCO { completed(bool), rewardPoints(int) }`（首次=true+实发积分；重复/任务停用/不存在=false+0） | ✅（V5.10） |

| #   | 方法   | 路径                                     | 请求                                                | 响应 (原始) | 阶段  |
| --- | ---- | -------------------------------------- | ------------------------------------------------- | ------- | --- |
| I25 | POST | `/internal/logistics/create`           | `CreateLogisticsCO`                              | `void`  | ✅   |
| I26 | PUT  | `/internal/logistics/{orderId}/status` | Path: `orderId` (Long), Query: `status` (Integer) | `void`  | ✅   |

#### 4.14.10 InternalSearchController — `/internal/search` (search-service)

| #   | 方法   | 路径                                        | 请求                                                   | 响应 (原始)                 | 阶段  |
| --- | ---- | ------------------------------------------- | ------------------------------------------------------ | --------------------------- | --- |
| I27 | POST | `/internal/search/reindex`                  | —                                                      | `Map<String,Object>`    | ✅   |
| I28 | POST | `/internal/search/vector`                   | `VectorSearchReq { embedding: [Double], size?, sellerId?, index? }` | `List`（按 `index` 分流） | ✅ 扩展 V5.5 |
| I37 | POST | `/internal/search/vector/knowledge/upsert`  | `KnowledgeVectorUpsertReq { knowledgeId(必填), sellerId?, title?, content? }` | `int` (写入 chunk 数) | 🆕 V5.5 |
| I38 | POST | `/internal/search/vector/knowledge/delete`  | Query: `knowledgeId` (Long)                            | `boolean` (是否处理)        | 🆕 V5.5 |
| I47 | POST | `/internal/search/vector/product/upsert`    | `ProductVectorUpsertReq { productId(必填), name(必填), description?, brand?, categoryId?, mainImage?, price?, soldCount? }` | `int`（1=已写入向量；**0=ES/Embedding 未启用静默跳过**；Feign 降级哨兵 `-1`） | 🆕 V5.9 |
| I48 | GET  | `/internal/search/vector/product/similar`   | Query: `productId` (Long, 必填), `size`(缺省5, ≤20)     | `List<ProductVectorHitCO> { productId, score }`（自身向量 kNN，排除自身；ES 关闭/自身无向量 → 空列表） | 🆕 V5.9 |

> - **I28 索引扩展（🆕 V5.5，向后兼容）**：请求体为超集——`index=products`（缺省，历史行为）走商品索引返回 `List<ProductSearchVO>`；`index=knowledge_vectors` 走知识库 chunk 索引返回 `List<KnowledgeChunkHitVO>`（支持 `sellerId` 过滤，null=全站）。旧报文（仅 `embedding`/`size`）行为完全不变；`size` 缺省或 ≤0 归一为 10。
> - **向量索引归属**：`knowledge_vectors` 索引（含分块与 Embedding 调用）归 search-service 所有；ai-service 经 ia-api `KnowledgeVectorClient` 提交**原文**并取回 chunk 文本，不感知维度与切片策略（Feign 调用方降级时 upsert 哨兵返回 `-1`）。
> - **渐进启用/降级**：ES 关闭（`search.elasticsearch.enabled=false`）时知识库向量端点按「未启用」处理——I37 返回 `0`、I38 返回 `false`、I28 知识库检索返回空，**不抛异常**，不阻断 ai-service 的知识库 CRUD。
> - **商品向量推荐（🆕 V5.9，[ADR-046](../adr/046-product-vector-recommendation.md)）**：I47/I48 复用同一向量底座（`ProductDocument.embedding` + I28 `index=products` kNN）。**单向量不分块**（商品短文本，不走 DocumentChunker）；I47 幂等覆盖写（同 productId 重写整文档，顺带补齐日常增量进 ES 的缺口，与 reindex 全量重建共存）；I48 只出 `productId + score`，**C 端组装归 item**（P27 批量查本地库出新鲜在售商品卡，ES 文档可能滞后）。**全链路 fail-open**：推荐是纯旁路，缺失=体验损失非资损（与 I43/I44 fail-closed 刻意不同，ADR-046）。
> - 调用方：ai-service（商家知识库 CH10-CH13 增删改后同步摄取/删除向量；RAG 检索见 §4.11.2）。

#### 4.14.11 WebSocketNotifyController — `/internal/ws` (gateway-service)

| #   | 方法   | 路径                          | 请求                                                                   | 响应 (原始)                       | 阶段  |
| --- | ---- | --------------------------- | -------------------------------------------------------------------- | ----------------------------- | --- |
| I29 | POST | `/internal/ws/order/status` | `OrderStatusChangedEvent { orderNo, oldStatus, newStatus, message }` | `void` (当前仅日志；未来推送 WebSocket) | 🔵  |

#### 4.14.12 InternalNotifyController — `/internal/notify` (notification-service:9400, 🆕 V5.2)

> 通信通道发送服务（纯通道，无验证码逻辑）。第三方实现在 ia-integration（`sms/` Aliyun、`email/` SMTP+Mock）。入参 `requestId` 透传落日志（幂等友好）；失败抛 BizException 由 Feign 调用方感知（`/internal/**` 裸异常直通）。

| #    | 方法   | 路径                       | 请求                                                | 响应 (原始)  | 阶段      |
| ---- | ---- | ------------------------ | ------------------------------------------------- | -------- | ------- |
| I30  | POST | `/internal/notify/sms`   | `NotificationSendReqCO { target, content/templateParams…, requestId, traceId }` | `void`   | ✅ V5.2  |
| I31  | POST | `/internal/notify/email` | `NotificationSendReqCO { target, subject, content, requestId }` | `void`   | ✅ V5.2  |
| I32  | POST | `/internal/notify/in-app` | `InAppMessageSendReqCO`（recipientId/type/title/content/linkUrl 等） | `void`（**落库持久化**，self-send 忽略） | ✅ V5.5 |

> ⚠️ 旧 `POST /internal/notify/send`（`Map` 弱类型，`sendSmsCode`）**已废弃**，由强类型 I30-I32 取代（破坏性契约变更）。
>
> **业务触达接线（V5.7，[ADR-042](../adr/042-buyer-notification-wiring.md)）**：支付成功（pay）/ 包裹发货（trade）/ 退款成功（pay）/ 售后审核结果（trade）四个业务事件 → 站内信（I32，收件人=买家 userId），由状态变更服务在**本地事务外** fail-open 发送（复用 item `ReviewManager` / merchant `StoreFollowManager` 先例，每服务一个触达协作器收敛样板）；SMS/email 模板 Phase 2（需渠道模板审批），MQ 事件化解耦留 roadmap。

#### 4.14.13 InternalVerificationController — `/internal/verification` (verification-service:9401, 🆕 V5.2)

> 校验收口服务：验证码签发/校验消费 + 人机验证。验证码存 Redis（短信沿用 `sms:code:` 契约与 user-service 直发互通，见 09-constraints）；**降级 fail-closed**——服务不可用时校验一律失败，绝不放行。

| #    | 方法   | 路径                              | 请求                                                              | 响应 (原始)                                                                                                  | 阶段      |
| ---- | ---- | ------------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ------- |
| I33  | POST | `/internal/verification/code/issue`   | `VerificationCodeIssueReqCO { channel: SMS\|EMAIL, target, scene }`   | `VerificationCodeIssueRespCO { success, reason, expireSeconds }`                                        | ✅ V5.2  |
| I34  | POST | `/internal/verification/code/verify`  | `VerificationCodeVerifyReqCO { channel, target, code }`               | `VerificationCodeVerifyRespCO { success, reason }`（结果码词汇 `VerificationResultCode`）                       | ✅ V5.2  |
| I35  | POST | `/internal/verification/captcha/verify` | `CaptchaVerifyReqCO`（Geetest v4 四要素）                                 | `CaptchaVerifyRespCO { success, reason }`                                                               | ✅ V5.2  |

> 校验策略：6 位数字 / TTL 5 分钟 / 重发限流 60s / 错误 5 次作废（窗口 30min）/ 一次性消费（Redis Lua 原子）。`VerificationResultCode`：SUCCESS / CODE_MISMATCH / CODE_EXPIRED / TOO_MANY_ATTEMPTS / RATE_LIMITED / SEND_FAILED / SERVICE_UNAVAILABLE / CAPTCHA_FAILED / NOT_SUPPORTED / PARAM_INVALID。调用方：authorization-service（登录校验、邮箱发码；user-service 直发链路已随边界重构解散）。

#### 4.14.14 InternalAuthController — `/internal/auth` (authorization-service, 🆕 V5.5.1)

> 授权域内部端点（ia-api `AuthContextClient`，path `/internal/auth`）。本节当前收录 ADR-031 用户级令牌吊销端点；ADR-018 的授权上下文端点（`GET /context/{userId}`、`GET /roles?userIds=`、`PUT /user/{id}/role`）暂未收录本节（补录待办，见 §十 变更记录 V5.5.1）。

| #   | 方法   | 路径                                    | 请求                                        | 响应 (原始) | 阶段        |
| --- | ---- | ------------------------------------- | ----------------------------------------- | ------- | --------- |
| I39 | POST | `/internal/auth/token-version/revoke` | Body: `TokenRevokeCO { userId (Long) }` | `void`  | 🆕 V5.5.1 |

> - **语义（ADR-031）**：用户级令牌吊销 = `INCR auth:tokenver:{userId}`（该用户此前签发的**全部** access token 经网关 `ver` 比对失败即时 401，见 §3.5）+ 拆除该用户**全部** refresh 会话（按 `PlatformEnum` 全端遍历，**全端下线**）。两者缺一不可：只推进版本挡不住旧 refresh token 轮换"续命"，只拆会话挡不住已签发 access token 存活至过期。
> - **错误口径（ADR-004）**：成功无返回体；失败以原始 HTTP 异常透出，不包 `Result`。
> - **调用方**：user-service 管理端禁用 / 冻结用户（`PUT /api/admin/user/{id}/status`）**成功路径**调用（本地状态变更提交后经 ia-api `AuthContextClient.revokeUserTokens`，Feign 置于事务之后）；**降级 fail-open**——调用失败仅 `log.warn` 告警、不阻断禁用主流程（与同接口 `setUserRole` 的 fail-closed 刻意不同：授权变更不允许静默丢失，吊销是"登录态失效的加速器"，ADR-011）。

#### 4.14.15 InternalPayRefundController — `/internal/pay/refund` (pay-service, ✅ V5.6)

> **已实现（V5.6）**——F1 买家退款资金流的退款发起入口。调用方：trade-service（售后审核同意 type=2 / 确认收到退货 type=1 后经编排层 manager 触发，禁 Service 直调 Feign；ia-api `RefundClient`，降级 **fail-closed**——见下）。数据模型 `refund_order` 见 04-Data-Model §十二 12.1。

| #   | 方法   | 路径                      | 请求                                                                                                                              | 响应 (原始)                          | 阶段       |
| --- | ---- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------- | -------------------------------- | --------- |
| I40 | POST | `/internal/pay/refund`  | Body: `RefundCreateCO { afterSaleId(Long, 必填), orderNo(String, 必填), refundAmount(Integer, 必填, >0, 分), reason(String, 可选, 摘要) }` | `RefundResultCO { refundNo, status }`（status：0-处理中/1-成功/2-失败） | ✅（V5.6） |

> - **幂等**：同 `afterSaleId` 重复请求**不创建新退款单**，返回既有退款单的 `{refundNo, status}`（`refund_order.uk_after_sale` 唯一约束兜底，天然幂等）。
> - **渠道路由**：按原支付单渠道原路退回——`BALANCE` 同步入账（I41）后置成功；`WECHAT`/`ALIPAY` 异步，成功由渠道回调确认（PAY6，§4.7.3）。
> - **金额校验**：`refundAmount` 超过订单实付 `payAmount` → `PARAM_ERROR(1101)`（申请时已有校验的双保险）。pay 侧不持有售后域：售后单存在性由 trade 侧保证，pay 以**支付单存在性 + 金额 ≤ 支付单金额**代理校验（ADR-035）。
> - **错误口径（ADR-004）**：失败以原始 HTTP 异常透出，不包 `Result`；调用方降级**fail-closed**——退款发起失败不允许静默丢失（售后单停留 APPROVED/RETURNED，由管理端重试 AD43 收敛），与吊销类 fail-open 刻意不同（ADR-011 三分法）。

#### 4.14.16 InternalAfterSaleController — `/internal/trade/after-sale` (trade-service, ✅ V5.6)

> **已实现（V5.6）**——pay-service 退款成功后的售后单状态回写。调用方：pay-service（退款单置成功后；ia-api `AfterSaleClient`，降级 fail-open + 告警——状态回写丢失靠对账/重试收敛，不阻断退款主流程，ADR-011 定类）。

| #   | 方法   | 路径                                        | 请求                       | 响应 (原始) | 阶段       |
| --- | ---- | ----------------------------------------- | ------------------------ | ------- | --------- |
| I42 | PUT  | `/internal/trade/after-sale/{id}/refunded` | Path: `id` (Long, 售后单ID) | `void`  | ✅（V5.6） |

> - **语义（✅ V5.6 按实现收敛，ADR-037）**：售后单 `APPROVED(2) | RETURNED(4) → REFUNDED(5)` 条件迁移（`UPDATE ... WHERE id=? AND status IN (2, 4)`，0 行 = 状态已迁移/非两类来源，幂等空操作成功返回）——激活此前为死状态的 `REFUNDED(5)`（`AfterSaleStatusEnum`，见 04-Data-Model §7.3）。**两条来源边共用资金终态**：仅退款（审核同意后）与退货退款（确认收到退货后，AD47 触发点，§4.13.5）。
> - **状态口径**：退款处理中/失败时售后单**停留原状态**（APPROVED/RETURNED，可重试）；仅退款成功才调用本端点。
> - **错误口径（ADR-004）**：售后单不存在 → 404 原始透出；其余幂等分支不抛异常。

#### 4.14.17 InternalFreightTemplateController — `/internal/item/freight-template` (item-service, ✅ V5.6)

> **已实现（V5.6）**——F2 运费模型：trade 下单计价取模板。调用方：trade-service（下单策略校验阶段经 `FreightPostageResolver` 解析生效模板，Feign 不入事务；ia-api `FreightTemplateClient`）。**降级 fail-closed（ADR-037，按 ADR-011 判别型查询定类；原草拟的"降级为空 → postFee=0"作废）**：模板查询失败 → 抛异常使**下单失败**，**不得默认免邮**——防止"故障伪装成免邮"的资损；「商家无任何启用模板 → `postFee=0`」是合法业务态（解析器空集合短路），与查询失败严格区分。

| #   | 方法   | 路径                                    | 请求                                                                     | 响应 (原始)                                   | 阶段       |
| --- | ---- | ------------------------------------- | ---------------------------------------------------------------------- | ----------------------------------------- | --------- |
| I43 | POST | `/internal/item/freight-template/query` | Body: `FreightTemplateQueryCO { sellerId(Long, 必填), templateIds(List\<Long\>, 必填, 可空数组=仅按 sellerId 查) }` | `List<FreightTemplateCO>`（id/templateNo/fee/freeThreshold/status/isDefault） | ✅（V5.6） |

> **返回口径（✅ V5.6 按实现收敛，ADR-037）**：返回该商家**启用**模板集合（入参 `templateIds` 中已停用/不存在的不返回），并**兜底补入**商家默认模板（最早创建的启用模板，`isDefault=true`）——调用方**一次往返**即可完成「绑定模板命中 → 回落默认」解析（避免二次查询与竞态窗口）。`sellerId` 口径 = 商家 `user_id`（ADR-019 Phase A）。

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
ws(s)://<gateway>/ws/chat/...?token=<accessToken>
```

通过 STOMP over WebSocket。**握手鉴权（🆕 V5.5）**：浏览器 STOMP/SockJS 无法自定义握手请求头，约定把 access token 放在 query 参数 `?token=`；网关 `WsHandshakeTokenFilter` 将其换写为 `Authorization: Bearer <token>` 请求头后走标准 JWT 校验，并**剔除 query 中的令牌**（token 不透传下游服务、不落下游/反代日志）。`/ws/chat/**` 需认证（不在白名单），无令牌或令牌无效握手 401；已带 `Authorization` 头的调用方（如非浏览器端）无需 `?token=`。详见 §三 3.5。

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

> **V5.4 对齐 gateway `application.yml` + `SecurityConfig` 白名单**（补 `/api/verification|notifications|points|sign|wallet|shops/**` 等缺失行，更正 logistics/chat/shop 归属与认证）。
> 匿名口径分两级：**完全匿名**（`PUBLIC_WRITE_PATHS` 白名单）与**仅 GET 匿名**（`PUBLIC_GET_PATHS`：`/api/item/**`、`/api/home/**`、`/api/search/**`、`/api/shop/**`、`/api/shops/**`、`/api/flash/**`、`/api/coupon/template`）。

| 路径前缀                           | 目标服务                  | 认证              |
| ------------------------------ | --------------------- | --------------- |
| `/api/auth/**`, `/oauth2/**`   | authorization-service | 完全匿名（白名单）       |
| `/api/verification/**`         | verification-service  | `/api/verification/codes` 匿名（限流） |
| `/api/notifications/**`        | notification-service  | 需要 JWT          |
| `/api/user/**`, `/user/**`     | user-service          | 需要 JWT (部分公开)   |
| `/api/item/**`, `/api/home/**`, `/api/admin/home/**` | item-service | 仅 GET 匿名（写需 JWT/权限） |
| `/api/item/freight-template/**`（✅ V5.6，随既有 `/api/item/**` 路由零网关改动） | item-service（**单列此行以明确**：不落入"仅 GET 匿名"口径，全端点需商家 JWT 🏪；**勿用 `/api/seller/freight-template`，该前缀路由到 merchant-service**） | 需要 JWT (seller) |
| `/api/cart/**`                 | cart-service          | 需要 JWT          |
| `/api/trade/**`, `/api/after-sale/**`, `/api/admin/after-sale/**`, `/api/admin/finance/**`, `/api/seller/finance/**` | trade-service | 需要 JWT |
| `/api/pay/**`                  | pay-service           | 回调公开（签名自证）      |
| `/api/logistics/**`            | logistics-service     | 需要 JWT（V5.4 更正：非公开） |
| `/api/search/**`               | search-service        | 仅 GET 匿名        |
| `/api/coupon/**`, `/api/flash/**`, `/api/points/**`, `/api/sign/**` | marketing-service | 仅 GET 匿名（`POST /api/flash/buy` 等写操作需 JWT） |
| `/api/admin/coupon/**`, `/api/seller/coupon/**` | marketing-service | 需要 JWT (admin/seller) |
| `/api/wallet/**`               | wallet-service        | 需要 JWT          |
| `/api/admin/roles/**`, `/api/admin/permissions/**` | authorization-service | 需要 JWT (admin，须先于 `/api/admin/**`) |
| `/api/admin/seller/**`         | merchant-service      | 需要 JWT (admin，须先于 `/api/admin/**`) |
| `/api/admin/pay/**`（✅ V5.6 已落地专属路由） | pay-service           | 需要 JWT (admin，**先于 `/api/admin/**` 声明**——避免被兜底吞到 user-service；同 `/api/admin/seller/**` 模式) |
| `/api/admin/**`                | user-service          | 需要 JWT (admin)  |
| `/api/ai/**`, `/api/chat/**`, `/api/seller/knowledge/**` | ai-service | 需要 JWT (knowledge 需商家) |
| `/api/seller/**`, `/api/shop/**`, `/api/shops/**` | merchant-service | shop/shops 仅 GET 匿名；seller 写需 JWT (seller) |
| `/api/invoice/**`              | (🆕 invoice-service)  | 需要 JWT          |
| `/internal/**`                 | —                     | 网关拦截，仅内部        |
| `/ws/**`                       | gateway-service       | WebSocket 升级    |

**路由规则**：精确路径优先匹配，兜底路由放最后。

---

## 七、前端页面 ↔ API 映射

> 此映射表供 Next.js 前端开发对照使用。按页面路由组织，列出每个页面需要调用的 API。
>
> ⚠️ **V5.1.1 契约提醒**：历史路径 `POST /api/auth/login` 已废弃（服务端无此路由，调用会 404）。前端重构时一律以本表与 §4.1 为准（`/api/auth/web/login`、`/api/auth/web/wechat/login`、`/api/auth/wechat/mini/login`、`/api/auth/email/code` 等）；网关白名单中的历史条目仅为兼容残留，前端迁移完成后将移除。

### 7.1 公共页面

| 前端页面    | 路由                 | API 调用                                                                                                                                                          |
| ------- | ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 首页      | `/`                | `GET /api/home/config`, `GET /api/flash`, `GET /api/item/product/page`                                                                                          |
| 商品搜索/列表 | `/search`          | `GET /api/search/product`, `GET /api/search/hot`, `GET /api/search/suggest`                                                                                     |
| 商品分类    | `/categories`      | `GET /api/item/category/tree`                                                                                                                                   |
| 商品详情    | `/product/[id]`    | `GET /api/item/product/{id}`（详情由 P2 承担；`/{id}/detail` 聚合端点未实现，见 §十二）, `GET /api/item/product/{id}/skus`, `GET /api/item/review/product/{id}/filter`, `GET /api/item/review/product/{id}/summary` |
| 商家店铺页   | `/shop/[sellerId]` | `GET /api/shop/{sellerId}`（遗留，🆕 新接入改 `GET /api/shops/{shopId}`）, `GET /api/shop/follow/{sellerId}/status`, `GET /api/shop/follow/{sellerId}/count` |
| 登录      | `/login`           | `POST /api/auth/web/login`, `POST /api/auth/web/wechat/login`                                                                                                    |
| 注册      | `/register`        | `POST /api/user/code`, `POST /api/auth/register`                                                                                                                |
| 找回密码    | `/reset-password`  | `POST /api/auth/reset-password`                                                                                                                                 |
| 微信小程序登录 | `/login` (小程序)   | `POST /api/auth/wechat/mini/login`                                                                                                                              |

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
| 积分      | `/user/points`           | `GET /api/points/balance`, `GET /api/points/history`                                                                                                                          |
| 余额      | `/user/balance`          | `GET /api/wallet`, `POST /api/wallet/recharge`（V5.4 迁移 wallet-service）                                                                                                        |
| 售后申请    | `/after-sale/apply`      | `POST /api/after-sale`                                                                                                                                                        |
| 售后列表    | `/user/after-sales`      | `GET /api/after-sale`                                                                                                                                                         |
| 账号安全    | `/user/security`         | `PUT /api/user/security/password`, `PUT /api/user/security/phone`                                                                                                             |
| 消息中心    | `/user/notifications`    | `GET /api/notifications`, `GET /api/notifications/unread-count`, `PUT /api/notifications/read-all`（V5.5 归属 notification-service）                                                                                                     |
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
| 店铺设置  | `/seller/shop`        | `GET /api/seller/shops`, `PUT /api/seller/shops/{shopId}`（🆕 V5.4 多店铺；`GET/PUT /api/seller/shop` 为遗留默认店铺别名 @Deprecated）                                                        |
| 运费模板  | `/seller/freight`     | `GET /api/item/freight-template/list`, `POST /api/item/freight-template`, `PUT /api/item/freight-template/{id}`, `DELETE /api/item/freight-template/{id}`（✅ V5.6——注意路径在 item 域，非 `/api/seller/**`） |
| 优惠券管理 | `/seller/coupons`     | `POST /api/seller/coupon`, `GET /api/seller/coupon`, `PUT /api/seller/coupon/{couponId}/disable`                                 |
| 结算管理  | `/seller/finance`     | `GET /api/seller/finance/settlement/page`, `GET /api/seller/finance/balance`, `POST /api/seller/finance/withdrawal`              |
| 售后处理  | `/seller/after-sales` | `GET /api/admin/after-sale`, `PUT /api/admin/after-sale/{id}/review`                                                             |
| 回复模板  | `/seller/templates`   | `GET /api/seller/templates`, `POST /api/seller/templates`, `PUT /api/seller/templates/{id}`, `DELETE /api/seller/templates/{id}` |
| 知识库   | `/seller/knowledge`   | `GET /api/seller/knowledge`, `POST /api/seller/knowledge`, `PUT /api/seller/knowledge/{id}`, `DELETE /api/seller/knowledge/{id}` |
| 商家入驻  | `/seller/apply`       | `POST /api/seller/apply`（`GET /api/seller/apply` ⚪ 未实现，见 §十二）                                                                                |

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
| 支付对账  | `/admin/reconciliation` | `GET /api/admin/pay/reconciliation/batches`, `GET /api/admin/pay/reconciliation/batches/{id}/details`, `POST /api/admin/pay/reconciliation/run?billDate=`（✅ V5.6，Phase 1 仅查看差异） |
| 退款重试  | `/admin/refunds`（并入支付对账页亦可） | `POST /api/admin/pay/refund/{refundNo}/retry`（✅ V5.6，仅失败单可重试） |
| 角色权限  | `/admin/roles`       | `GET /api/admin/roles`, `POST /api/admin/roles`, `PUT /api/admin/roles/{id}`, `DELETE /api/admin/roles/{id}`, `GET /api/admin/permissions/tree`                                    |
| 操作日志  | `/admin/logs`        | `GET /api/admin/log`                                                                                                                                                               |
| 品牌管理  | `/admin/brands`      | `POST /api/item/brand`, `PUT /api/item/brand/{id}`, `DELETE /api/item/brand/{id}`                                                                                                  |

---

## 八、核心 CO/VO 快速参考

### 8.1 认证

```json
// LoginResp (登录/注册/微信登录, snake_case, V5.1 新增 token_type)
{ "access_token":"eyJ...", "token_type":"Bearer", "expires_in":1800, "refresh_token":"xxx", "user_id":1 }

// OAuth2TokenResp (刷新令牌, snake_case, V5.1 新增 token_type)
{ "access_token":"eyJ...", "token_type":"Bearer", "expires_in":1800, "refresh_token":"xxx" }

// SessionStatus
{ "valid": true, "ttlSeconds": 1500, "idleTimeoutMinutes": 30 }
```

### 8.2 用户

```json
// UserInfoResp (V5.4 删除 balance——余额归 wallet-service /api/wallet)
{ "userId":"usr_abc", "username":"buyer001", "phone":"138****8000", "email":"bu***@example.com", "avatar":"https://...", "gender":null, "birthday":null, "memberLevel":null, "status":1, "registerTime":"...", "roleType":0, "roles":["ROLE_USER"], "permissions":[] }

// AddressResp (V5.5 删除 updateTime；U6/U8/I10 共用)
{ "id":1, "userId":1001, "receiver":"张三", "phone":"13800138000", "province":"北京市", "city":"北京市", "district":"朝阳区", "street":"望京街道", "detail":"SOHO T1 1205", "defaulted":true, "label":"公司", "createTime":"..." }

// PointsBalanceVO (GET /api/points/balance, V5.4 强类型化)
{ "userId":1001, "balance":150 }

// HistoryVO (GET /api/user/history, snake_case 键名不变)
{ "id":1, "product_id":1001, "view_time":"...", "product_name":"手工陶瓷茶杯", "main_image":"https://...", "brand":"冰美精选", "price":12800 }

// SignResultVO
{ "signed":true, "earnedPoints":10, "monthCount":15, "continuousDays":5 }
```

### 8.3 商品

```json
// ProductVO
{ "id":1, "productId":"prod-001", "sellerId":5, "shopName":"冰美精选陶瓷", "categoryId":1, "name":"手工陶瓷茶杯", "mainImage":"https://...", "images":[...], "description":"...", "brand":"冰美精选", "soldCount":1280, "commentCount":86, "rating":4.7, "status":1, "salesTags":["热销"], "publishTime":"...", "skus":[...] }

// SkuVO
{ "id":1, "skuId":"sku-001", "productId":1, "spec":"天青/标准", "price":12800, "originalPrice":15800, "stock":50, "stockType":1, "isHot":true, "image":"https://...", "soldCount":320, "status":1 }

// CategoryTreeVO
{ "id":1, "name":"陶瓷器具", "level":1, "sortOrder":1, "children":[...] }
```

### 8.4 购物车

```json
// CartVO (allSelected: 非空且全选中且全可计价；读时回填 productName/price/spec/image/subTotal)
{ "items":[...], "totalPrice":38400, "selectedPrice":25600, "allSelected":false }

// CartItemVO (原 CartItemResp 更名；V5.4 删除 createTime)
{ "skuId":10, "productName":"手工陶瓷茶杯", "spec":"天青/标准", "image":"https://...", "price":12800, "quantity":2, "selected":true, "subTotal":25600 }

// CartGroupedVO
{ "groups":[{ "sellerId":5, "shopName":"...", "items":[...], "shopTotalPrice":25600 }], "totalPrice":38400, "selectedPrice":25600 }
```

### 8.5 订单

```json
// CreateOrderReq (V5.4 删 cartItemIds/remark；V5.5 删 userCouponIds；addressId 必填；userCouponId 为单券可选)
{ "addressId":1, "userCouponId":5 }

// DirectOrderReq (V5.5 删 userCouponIds，多券叠加挂账 §十二/§十一 O1)
{ "skuId":10, "quantity":1, "addressId":1, "userCouponId":5 }

// OrderVO (V5.4 删除 id/paymentType；详情含 items，列表 items=null；✅ V5.6 增量 postFee——金额不变式 payAmount=totalAmount−discountAmount+postFee，见 §4.6.1)
{ "orderNo":"ord-...", "userId":1001, "sellerId":5, "totalAmount":25600, "payAmount":24400, "discountAmount":2000, "postFee":800, "status":1, "orderType":1, "receiverName":"张三", "receiverPhone":"138...", "receiverAddress":"...", "createTime":"...", "payTime":null, "consignTime":null, "endTime":null, "items":[...] }

// OrderItemVO (✅ V5.6 增量 packageNo——明细归属包裹，null=未发货，对应 §8.7 LogisticsVO.packageNo)
{ "id":1, "skuId":10, "productId":1, "productName":"手工陶瓷茶杯", "skuSpec":"天青/标准", "price":12800, "quantity":2, "subTotal":25600, "image":"https://...", "packageNo":null }

// WithdrawalVO (V5.5 收款账号分端口径：商家端仅脱敏、管理端明文+脱敏，见 §4.6.5)
{ "id":1, "withdrawalNo":"5f8a9c0d...(uuid)", "sellerId":5, "amount":1280000, "status":1, "bankAccount":null, "bankAccountMasked":"****1234", "bankName":"中国工商银行", "adminRemark":null, "createTime":"..." }

// AfterSaleVO (V5.5 对齐实现：type 为 Integer 1退货退款/2仅退款；无 evidenceImages/statusText/sellerRemark/updateTime)
{ "id":1, "orderNo":"ord-...", "userId":1001, "type":1, "reason":"...", "refundAmount":12800, "status":1, "adminRemark":null, "logisticsNumber":null, "createTime":"..." }
```

### 8.6 支付

```json
// PayOrderVO (V5.4 删除 bizUserId；实现字段如下)
{ "payOrderNo":"pay-...", "bizOrderNo":"ord-...", "payChannelCode":"WECHAT", "amount":23600, "payType":1, "status":1, "qrCodeUrl":"weixin://...", "payOverTime":"...", "paySuccessTime":null }

// RefundVO (✅ V5.6——退款单，AD43 返回；I40 返回子集 {refundNo,status}；status 0-处理中/1-成功/2-失败；售后单口径见 §4.7.3)
{ "refundNo":"6f2c1a3e-...", "afterSaleId":1, "orderNo":"ord-...", "channel":"WECHAT", "refundAmount":12800, "status":0, "channelRefundNo":null, "failReason":null, "createTime":"..." }

// ReconciliationBatchVO (✅ V5.6——对账批次，AD44/AD46；AD46 返回 List<ReconciliationBatchVO>，ADR-036)
{ "id":1, "billDate":"2026-10-02", "channel":"WECHAT", "status":1, "channelTotal":1520000, "localTotal":1520000, "createTime":"..." }

// ReconciliationDetailVO (✅ V5.6——差异明细，result: 1-匹配/2-渠道有本地无/3-本地有渠道无/4-金额不一致，AD45)
{ "id":100, "batchId":1, "payOrderNo":"pay-...", "channelTxnId":"42000023...", "channelAmount":9900, "localAmount":12800, "result":4, "createTime":"..." }

// FreightTemplateVO (✅ V5.6——运费模板，FT1-FT4；chargeMode Phase 1 恒 1-固定运费；freeThreshold 0=不参与满额包邮)
{ "id":1, "templateNo":"0c9d8b7a-...", "templateName":"全场满99包邮", "chargeMode":1, "fee":800, "freeThreshold":9900, "status":1, "createTime":"..." }
```

### 8.7 物流

```json
// LogisticsVO (V5.4 删除 updateTime；无 orderNo/statusText/traces；✅ V5.6 增量 packageNo，L1 返回 List<LogisticsVO> 按 packageNo 升序——破坏性变更已随 V5.6 切换生效，见 §4.8.1)
{ "orderId":1, "packageNo":1, "logisticsNumber":"SF1234567890", "logisticsCompany":"顺丰速运", "contact":"张三", "mobile":"138...", "province":"北京市", "city":"北京市", "district":"朝阳区", "street":"望京街道", "detail":"SOHO T1 1205", "status":2, "createTime":"..." }
```

### 8.8 搜索

```json
// ProductSearchVO (V5.4 删除 embedding；无 originalPrice/rating/shopName/salesTags)
{ "id":1, "productId":"prod-001", "categoryId":1, "name":"手工陶瓷茶杯", "description":"...", "brand":"冰美精选", "mainImage":"https://...", "price":12800, "soldCount":1280 }
```

### 8.9 营销

```json
// CouponVO (模板；V5.4 字段名对齐实现：minAmount/totalQty/issuedQty)
{ "couponId":"cpn-001", "name":"618满200减30", "type":1, "value":3000, "minAmount":20000, "sellerId":null, "totalQty":10000, "issuedQty":6523, "startTime":"...", "endTime":"...", "status":1, "discountType":1, "couponCategory":1, "grantType":1, "stockType":1, "grabType":1, "priceInCents":3000, "scopeType":1, "scopeValues":null, "stackRule":1, "stackGroup":null }

// UserCouponVO (已领取；V5.4 字段名对齐实现：id/createTime)
{ "id":5, "couponId":"cpn-001", "status":1, "usedOrderNo":null, "useTime":null, "createTime":"..." }

// FlashSaleVO
{ "id":1, "skuId":10, "productName":"手工陶瓷茶杯", "productImage":"https://...", "flashPrice":9900, "originalPrice":12800, "stock":100, "soldCount":35, "startTime":"...", "endTime":"...", "status":1, "progressPercent":35 }

// FlashBuyVO (V5.4 orderNo 为真实订单号；sellerId=0 口径见 M10)
{ "success":true, "orderNo":"ord-...", "flashId":1, "flashPrice":9900, "productId":1, "remainingStock":65 }
```

### 8.10 AI

```json
// AiChatRequest (V5.5 新增可空 sellerId；仅 AI3/AI4 客服会话消费，AI1/AI2 忽略)
{ "message":"500以内适合夏天穿的透气跑鞋", "conversationId":"conv-uuid-xxx", "sellerId":null }

// AiChatResponse
{ "reply":"为您找到3款...", "conversationId":"conv-uuid-xxx", "products":[...] }
```

### 8.11 消息

```json
// ChatMessage (STOMP)
{ "messageId":"uuid", "conversationId":"buyer_1001_seller_5_product_P001", "senderId":1001, "senderRole":"BUYER", "senderName":"买家张三", "content":"这个商品还有货吗？", "contentType":"TEXT", "timestamp":"..." }

// ChatConversationVO (V5.4 强类型化，四字段为准；草案键名 lastMessageTime 以实现 lastTime 为准)
{ "conversationId":"...", "lastMessage":"好的，今天发货", "lastTime":"...", "unreadCount":2 }
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

> **V5.4 内部回写收紧**：跨服务状态回写端点 `PUT /internal/trade/order/{orderNo}/status`（I19）仅接受 `1→2`（支付成功）与 `1→5`（超时/取消）两条迁移边，其余（含重复回写）返回 `ORDER_STATUS_INVALID(3010)`。
>
> **✅ V5.6 拆单发货对状态机的影响（已实现，ADR-037）**：**不新增状态**——部分发货订单保持"待发货(2)"，全部明细发货后才迁移"待收货(3)"（迁移仍由商家发货链路触发，与现口径一致）；售后单侧同理，`REFUNDED(5)` 由退款成功回写激活（I42，§4.14.16，来源 APPROVED|RETURNED），状态枚举不变。

---

## 十、变更记录

| 版本       | 日期             | 变更内容                                                                                                                                                                                                                                                                                      |
| -------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V4.5     | 2026-07-31     | 优惠券 V4.5 去重保护；文档全面更新至 175 端点                                                                                                                                                                                                                                                              |
| **V5.0** | **2026-08-02** | **全面重构：新增 35+ 端点，完善 DTO 定义，补充前端页面映射，补齐京东标准功能**                                                                                                                                                                                                                                            |
|          |                | **新增端点**：账号安全(U24-U27)、通知中心(U28-U32)、评论增强(P16-P18)、品牌管理(P19-P22)、商品对比(P24)、到货通知(P25-P26)、购物车批量操作(C8-C10)、订单生命周期(T7-T9)、商家改价(T13-T14)、售后增强(T17-T19)、支付历史(PAY5)、搜索建议(SE5)+清除历史(SE4)、优惠券计数(M7)、秒杀详情(M9)+时间线(M11)、营销活动(M12-M13)、聊天已读(CH5)、发票(INV1-INV3)、管理员商品管理(AD38-AD39)、管理员秒杀管理(AD40-AD42) |
|          |                | **文档增强**：全部端点补充认证标识(🌐🔒🔑🏪🔌)、完善 DTO/VO JSON 示例、新增前端页面↔API映射表、补充订单状态流转图、补充业务错误码速查、补充 API 版本策略、补充日期时间格式约定                                                                                                                                                                                |
| **V5.1** | **2026-09-29** | **authorization-service DDD 四层重构配套同步**：①§4.1 端点路径核对更正（A1 `/api/auth/web/login`、A6 `/api/auth/web/wechat/login`、A9 新增小程序 `/api/auth/wechat/mini/login`，响应统一含 `token_type` 增量字段）；②§4.14.1 内部端点路径更正：认证域迁移至 `/internal/auth/*`（I1-I6），新增 `/internal/user/getOrCreate`（I9），后续内部端点编号顺延；③§8.1 DTO 示例区分 LoginResp/OAuth2TokenResp；④§7.1 前端映射登录路径更正 |
| **V5.2** | **2026-09-29** | **通信/校验能力抽离**：①新增微服务 notification-service:9400（纯通道发送，§4.14.12 I30-I32）与 verification-service:9401（验证码签发/校验+人机验证收口，§4.14.13 I33-I35）；②ia-common SMS/Geetest 工具迁至 ia-integration；③**破坏性变更**：`NotificationClient.sendSmsCode(Map)` → 强类型 `sendSms/sendEmail/sendInAppMessage`，旧 `/internal/notify/send` 废弃；④新增 `VerificationClient`（fail-closed 降级）；⑤§4.1 A1 `credentialType` 改为服务端路由依据（PASSWORD/SMS_CODE/EMAIL_CODE），新增 A10 邮箱发码端点；⑥authorization-service 人机验证改经 verification-service 裁决，启用 Feign 熔断（resilience4j） |
| **V5.2.1** | **2026-09-30** | **契约对象按 ADR-015 更名**：ia-api 契约对象 `XxxDTO` → `XxxCO`（24 类，§4.14 内部端点表同步；报文格式/路由/JSON 字段零变化，仅 Java 类名）；`CreateOrderInternalReq` 等无 DTO 后缀类保留原名 |
| **V5.2.2** | **2026-09-30** | **user-service DDD 重构契约同步（ADR-016）**：I3 路径更正 `/internal/auth/web/sms/login`（文档残留错配）；I10 标注 AddressClient 客户端路径修正；U5 发码/人机验证收口 verification-service 注记（报文无变化） |
| **V5.2.3** | **2026-09-30** | **认证边界收敛（ADR-017）契约同步**：①§4.14.1 `/internal/auth` 六端点与 `AuthClient` 整体废弃（凭证唯一所有权归 auth）；②I9 getOrCreate 增量可选 `nickname`，标注 WECHAT 幂等取舍；③注册错误语义变更：`PHONE_ALREADY_REGISTERED(3003)` 取代用户名占用校验；④前端行为变化：`/api/user/info` 的 phone/email 返回掩码、`registerTime` 回退 `create_time`、奖励统一为建档奖励、微信登录不再因 user 冻结被拒（拦截点移至下游业务 API） |
| **V5.4.0** | **2026-10-03** | **商家 1:N 与契约大扫除（ADR-019）同步**：①§4.3 商家/店铺 1:N——新增 `GET /api/shops/{shopId}`（SH1，仅 status=1）、`GET /api/seller/shops`（S1a）、`PUT /api/seller/shops/{shopId}`（S1b，归属校验 403）；`GET /api/shop/{sellerId}`、`GET/PUT /api/seller/shop`、关注类 sellerId 路径标注 @Deprecated（→默认店铺）；关注端点新增 shopId 参数行（SF1-SF4）；SellerVO → ShopVO；②`POST /api/seller/apply` 语义细化（同事务商家+默认店铺；并发重复→3006）、`GET /api/admin/seller/pending` 报文收窄（MerchantVO id/userId/status/createTime）、approve 聚合生效；③报文收缩：`POST /api/trade/order` 删 `cartItemIds`/`remark` 并 `addressId` 必填、OrderVO 删 `id`/`paymentType`、LogisticsVO 删 `updateTime`、PayOrderVO 删 `bizUserId`、ProductSearchVO 删 `embedding`、CartItemResp→CartItemVO 删 `createTime`、P18 like 对齐 `Result<Void>`、MerchantKnowledgeVO 删 `embeddingId`、AI 会话消息删 `toolCalls`、UserInfoResp 删 `balance`（余额归 wallet-service）；④弱类型→强类型：`/api/points/balance`（PointsBalanceVO）、`/api/chat/conversations`（ChatConversationVO）、`/api/user/history`（HistoryVO）；⑤校验收紧：角色三端点入参类型化（RoleReq/UpdateRoleReq/RolePermissionAssignReq，64/64/256）、`/api/wallet/recharge` amount 缺失/≤0 → 400、知识库 title/content 必填 + PUT 局部更新、售后/发货/提现必填校验、全平台方法参数校验失败统一 400 `PARAM_ERROR(1101)`；验证码失败映射 3001/3002/1014(429)；⑥§4.14 内部契约：全部 `/internal/**` 错误响应 HTTP 状态=ErrorCode 状态、body 极简 `{"code","msg"}`；新增 `GET /internal/trade/order/id/{orderId}`（I36）；I19 仅接受 1→2 / 1→5 两条迁移；OrderSummaryCO 补 `payAmount`/`userId` 与金额口径说明；⑦行为修正：`POST /api/flash/buy` orderNo 真实订单号（预扣→落单→MQ，sellerId=0）、`PUT /api/seller/coupon/{couponId}/disable` 3019、hotReason 枚举码、券 VO 字段名对齐实现（minAmount/totalQty/issuedQty/id/createTime）；⑧§二错误码表对齐 ErrorCode 枚举、§六路由表补 verification/notifications/points/sign/wallet/shops 等行、§4.2.4 余额迁移注记 + §4.2.10 wallet 新节、积分/聊天归属更正；⑨新增 §十一待决策小节 |
| **V5.5.0** | **2026-10-03** | **待决策收敛（11 条 → 10 已决 + 1 开放）与 ADR-026/027/028/029/032 契约同步**：①§三 新增 3.5 Token 吊销与 WS 握手鉴权——`POST /api/auth/logout`、`POST /api/auth/reset-password` 携带 Authorization 时按 `jti` 写网关黑名单（`auth:blacklist:{jti}`）即时失效（ADR-027）；浏览器 STOMP/SockJS 握手约定 `?token=<accessToken>`，网关 `WsHandshakeTokenFilter` 换写为 Authorization 头并剔除 query（§4.1 A4/A5、§4.12.1 CH1、§五 5.1 同步）；②提现/下单/售后已决落地：`WithdrawalVO` 收款账号分端口径（商家端仅 `bankAccountMasked`、管理端明文+脱敏，§4.6.5 新增 VO 定义）、`CreateOrderReq`/`DirectOrderReq` 删 `userCouponIds`（多券挂账 roadmap）、售后 `type` Integer 枚举值表（1退货退款/2仅退款，非法 400）、`evidenceImages` 移出正文；③PAY2 微信回调应答体改为 v3 规范裸 JSON（200+SUCCESS / 500+FAIL，幂等重复仍 SUCCESS）；④AI 客服 AI3/AI4 请求体新增可空 `sellerId`（RAG 检索范围，`ai.rag.enabled=true` 时注入商家知识上下文，失败降级直答）；⑤§4.14.10 新增内部端点 I37 `POST /internal/search/vector/knowledge/upsert`（原文摄取，返回 chunk 数）、I38 `POST /internal/search/vector/knowledge/delete?knowledgeId=`，I28 扩展 `index=products\|knowledge_vectors` 与 `sellerId` 过滤（旧报文兼容）；⑥管理端 12 个写端点（AD7-9/17/20/23/24/25/31-33/35）补审计留痕注记（ELK AUDIT；user 域另写 operation_log，ADR-032）；⑦站内信按 notification-service 实现补录 §4.2.9（U28-U32 重定义，`InAppMessageVO`）；⑧`AddressResp` 删 `updateTime`（U6/U8/I10 与 §8.2）；⑨错误码表补 1014 流控统一出口说明；⑩S5/T26/P6 与 V5.0 草案未实现字段族全部移出正文，新增 §十二 Roadmap；§十一收敛为 10 条已决归档 + 1 条开放项 O1（多券接线待 PRD） |
| **V5.5.1** | **2026-10-03** | **多粒度令牌吊销（ADR-031）契约同步**：①§3.5 吊销口径升级为三级粒度模型（单 token jti 黑名单 / 全用户 tokenVer 版本号 / refresh 会话拆除）——`POST /api/auth/reset-password` 由"按 jti 拉黑当前令牌"改为**用户级令牌版本吊销**（`INCR auth:tokenver:{userId}` + 拆除该用户全部 refresh 会话，**全端下线**，已发 access token 立即失效），**不再消费 Authorization 头**（客户端无需改动）；登出仍为单 token jti 黑名单（口径不变）；管理端禁用用户（`PUT /api/admin/user/{id}/status`）经 ia-api `AuthContextClient.revokeUserTokens` 触发同款吊销，**即时全端下线**；②§4.14.14 新增内部端点 I39 `POST /internal/auth/token-version/revoke`（Body `TokenRevokeCO { userId: Long }`，返回 void，raw 异常透出不包 Result；调用方 user-service，降级 fail-open）；③网关 `JwtBlacklistFilter` 以一次 `MGET auth:blacklist:{jti} auth:tokenver:{userId}` **合并校验**（每请求 Redis 往返不变，Redis 异常 fail-open）：黑名单命中 401、`ver` claim 与版本键不一致 401、版本键缺失跳过比对；存量无 `ver` claim 令牌按 `0` 自然过期，**无需迁移**；`auth:tokenver:{userId}` 不设 TTL、成为第 4 个跨服务契约键（auth 写 / 网关只读豁免，ADR-031 §7）；④§4.1 A4/A5 注记同步修订；⑤补录待办：ADR-018 授权上下文三端点（`GET /internal/auth/context/{userId}`、`GET /internal/auth/roles?userIds=`、`PUT /internal/auth/user/{id}/role`，ia-api `AuthContextClient`）代码已实现但尚未收录 §4.14 端点目录 |
| **V5.6.0** | **2026-10-03** | **P0 功能规格先行（✅ 已实现——落地情况与实现偏差收敛见 V5.6.1 行）**：①**F1 买家退款资金流**：§4.7.3 新增 PAY6 `POST /api/pay/callback/wechat-refund`（微信退款回调，复用 ADR-033 验签/AES-GCM 解密与 ADR-029 裸 JSON 应答协议）、§4.14.15 I40 `POST /internal/pay/refund`（trade 售后审核同意后经编排层触发；同 afterSaleId 幂等，uk_after_sale 一售后单一次退款）、§4.14.3 I41 `POST /internal/balance/refund`（transaction_no 幂等入账；本节归属由 user-service 更正为 wallet-service——文档漂移修复）、§4.14.16 I42 `PUT /internal/trade/after-sale/{id}/refunded`（APPROVED→REFUNDED 条件迁移，激活死状态 REFUNDED）、AD43 `POST /api/admin/pay/refund/{refundNo}/retry`（失败单重试，3039）；状态口径：退款处理中/失败售后单停留 APPROVED，成功才 REFUNDED；②**F2 运费模型**：§4.4.9 新增 FT1-FT4 商家运费模板 CRUD——**路径定 `/api/item/freight-template/**`（item-service）**，规避 `/api/seller/**` 路由到 merchant-service 的归属冲突（零网关改动）；§4.14.17 I43 模板批量查询（trade 计价）；**金额不变式变更（旧→新）**：`payAmount = totalAmount − discountAmount` → `payAmount = totalAmount − discountAmount + postFee`（§4.6.1 OrderVO、§4.14.7 金额口径同步）；③**F3 拆单发货**：T12 `ShipOrderReq` 增量 `skuIds[]`（部分发货保持待发货，全量发货转待收货）、L1 `GET /api/logistics/{orderId}` **响应由单对象改为列表**（按 packageNo 升序，破坏性变更）、OrderVO.items 增量 `packageNo`；④**F4 渠道对账**：§4.13.14 新增 AD44/AD45 对账批次/差异查询 + AD46 手动触发（同日幂等先删后写，Phase 1 仅记录差异不自动调账）；§二 新增 `3039 REFUND_STATUS_INVALID`（V5.6.1 勘误：本行原记码号有误，实为 3039）；⑤§六 路由表补 `/api/admin/pay/**` → pay-service（**新增网关路由，须先于 /api/admin/** 声明**）与 `/api/item/freight-template/**` 明确行；§七 补商家端运费模板/管理端对账映射；§八 补 RefundVO/ReconciliationBatchVO/ReconciliationDetailVO/FreightTemplateVO；§九 补拆单发货状态口径注记；数据模型配套见 04-Data-Model §十二（V5.5 资金线：refund_order/wallet_transaction/reconciliation 两表；V5.6 履约线：freight_template + product/orders/order_logistics/order_item 变更）；⑥§4.7.1 PAY2 与 §12.2 的"out_trade_no 断链修复进行中"表述过时，按 ADR-033 已落地更正为已完成 |
| **V5.6.1** | **2026-10-03** | **V5.6 全量落地同步 + 实现方上报偏差收敛（以实现为准，ADR-035/036/037；四个 P0 功能实现完成且全量测试通过）**：①**规划标注转已实现**：V5.6.0 全部 🆕 规划条目改 ✅——FT1-FT4（§4.4.9）、PAY6（§4.7.3）、AD43-AD46（§4.13.14）、I40-I43（§4.14.15-17）、T12 `skuIds` 增量、L1 列表化、`OrderVO.postFee` / `OrderItemVO.packageNo` / `LogisticsVO.packageNo`（"字段上线前报文不含该键"等过渡期表述全部移除）；**新增端点 AD47** `PUT /api/admin/after-sale/{id}/confirm-return`（§4.13.5，端点总数 215+ → 229+）；②**退款触发点纠正（ADR-037）**：AD17 `review` 同意仅对 **type=2 仅退款** 触发退款；**type=1 退货退款**在 AD47 确认收到退货（条件迁移 APPROVED→RETURNED；已 REFUNDED → 3039 守卫、已 RETURNED 幂等空操作、非 type=1 → 1101）后触发；I42 回写来源扩为 `APPROVED|RETURNED → REFUNDED`（§4.7.3 触发链路、§4.6.4 状态口径、§4.14.15/16、§九 同步）；③**错误码勘误**：`REFUND_STATUS_INVALID` 实现预置为 **3039**（原草拟码号全文勘误清零——§二/§4.7.3 BDD/§4.13.14 与 V5.6.0 行内旧记一并更正为 3039）；§4.4.9 错误语义改按 ia-common 预置——重名 → **3040 FREIGHT_TEMPLATE_NAME_EXISTS**、模板不存在/**非本商家**（含商品绑定他人模板，防探测）→ **3041 FREIGHT_TEMPLATE_NOT_FOUND**（原草拟 3006/1103/403 语义不符作废）；§二 补 3039/3040/3041 三行；④**I41 幂等语义细化（ADR-035）**：顺序重放幂等成功；真并发同号 → 抛异常使「加余额+写流水」整体回滚，pay 侧退款单置失败(2)、AD43 重试收敛，资金不双记；⑤**I43 降级口径改 fail-closed（ADR-037）**：模板查询失败 → 下单抛错，**不得默认免邮**（原草拟"降级为空 → postFee=0"作废）；返回口径补**兜底补入默认模板**（`isDefault=true`）与响应字段 `isDefault`；FT3 明确 `GET /api/item/freight-template/list` → `List<FreightTemplateVO>`（无分页，含停用模板按创建顺序，维持定稿）；⑥**AD46 响应改 `List<ReconciliationBatchVO>`**（channel 缺省=当日全部渠道一次多条，指定单渠道时长度 1，ADR-036），补「账单拉取失败落 status=2 失败批次」口径；⑦§4.14.7/§九 拆单发货与金额不变式 `payAmount = totalAmount − discountAmount + postFee` 落地标注（总不变式已实现）；§六 `/api/admin/pay/**` 专属路由标注已落地（网关 application.yml 已声明）；F1-F4 BDD 标注转"已实现"，F1 补退货退款触发场景 |
| **V5.7.0** | **2026-10-04** | **P1 第一批（消息触达 + 订单留言 + 秒杀管理端）契约先行**：①§4.6.1 T1/T6 请求增量 `remark?`（买家留言 ≤200，可选）+ `OrderVO` 增量 `remark`（**V5.4 删除的死字段 remark 重新立项接线**，落 `orders.remark` 快照）；②§4.13.14 AD40-AD42 管理端秒杀活动管理定稿并实现标注（`FlashSaleCreateReq { productId, skuId, flashPrice, stock, startTime, endTime }` / `FlashSaleUpdateReq` null-safe 局部更新 / 进行中禁删；错误口径 1101/1103）；③§4.14.12 I32 站内信行勘误（V5.5 已落库实现，非"契约预留"）+ 业务触达矩阵注记（支付成功/发货/退款成功/售后审核结果 → 站内信，事务外 fail-open，[ADR-042](../adr/042-buyer-notification-wiring.md)）；数据模型见 04 §3.7 / §十二（V5.7 迁移 `orders.remark`） |
| **V5.8.0** | **2026-10-05** | **P1 第二批（促销引擎 + 价格计算中心 / 会员等级与权益）契约先行**：①§4.13.8b 新增 AD48-AD50 管理端店铺满减/满折活动管理（`PromotionCreateReq`/`PromotionUpdateReq`，时间窗推导状态、已结束禁编、进行中禁删，复用 AD40-42 先例）；②§4.14.8b 新增 I44 `POST /internal/promotion/query`（trade 定价中心取生效促销，**fail-closed** 与 I43 同口径）；③§4.14.1b 新增 I45 `GET /internal/user/member/level` + I46 `POST /internal/user/member/growth`（会员折扣率/积分倍率 + 成长值入账，**fail-open**，与 I43/I44 刻意不同——[ADR-044](../adr/044-member-level-and-benefits.md)）；④计价口径（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）：促销与会员折扣与券三者可叠加、同类型促销取减免最大者、总额守卫 `discountAmount ≤ totalAmount`、秒杀单不叠加（维持 discountAmount=0）、Phase 1 优惠汇总在订单头（item 级分摊与多券 PRD 一并挂账 O1）；数据模型见 04 §十二 12.5/12.6（V5.8 迁移 `promotion`/`member_level_rule`/`member_growth_log`） |
| **V5.9.0** | **2026-10-05** | **P2 第一批（商品向量推荐）契约先行**：①§4.4.x 新增 P27 `GET /api/item/product/{productId}/similar`（相似商品卡，向量相似度降序、恒过滤在售、排除自身，空列表不报错）；②§4.14.10 新增 I47 `POST /internal/search/vector/product/upsert`（商品原文摄取，单向量幂等覆盖写，未启用返回 0/降级哨兵 -1）与 I48 `GET /internal/search/vector/product/similar`（kNN 命中 id+score，组装归 item）；③全链路 fail-open（[ADR-046](../adr/046-product-vector-recommendation.md)）；MySQL 零 schema 变更（向量在 ES，`ProductDocument.embedding` 既有字段） |
| **V5.10.0** | **2026-10-05** | **P2 第二批（搜索 facet / 每日任务体系）契约先行**：①**F8 搜索 facet**——SE1 声明未实现的 `brand/minPrice/maxPrice/sort` 过滤参数补齐实现（ES 链 filter+sort+publishTime 字段新增，DB 链同口径）；新增 SE6 `GET /api/search/facets`（品牌 terms 聚合 + 固定价格档位计数，独立端点零破坏，DB 链返回空对象）；②**F9 每日任务体系**——新增 M14 `GET /api/task/daily`（今日任务+状态）与 I49 `POST /internal/task/complete`（幂等完成+积分单出口发放，触发方 trade 确认收货/item 评价，事务外 fail-open，[ADR-047](../adr/047-daily-task-system.md)）；数据模型 `task_def`/`task_record` 按 ADR-045 口径直接入 Initialize.sql 快照（无迁移文件） |
| **V5.11.0** | **2026-10-05** | **P2 第三批（活动中心 + 促销可见性）契约先行**：①§4.13.10 M12/M13 活动中心实现定稿（`ActivityVO` 字段定稿：数据源=promotion 进行中活动，1→FULL_REDUCE/2→LIMIT_DISCOUNT，NEW_USER 暂空列表 Phase 2；M13 按 id 详情 1103）；②M7 我的券三态计数实现；③§4.4.x P2 商品详情响应增量 `promotions` 促销标签（item 复用 I44，调用方 fail-open 包装 + 60s Caffeine 缓存，ADR-041 先例；与下单计价同源 promotion 表——用户所见即所得） |
| **V5.12.0** | **2026-10-05** | **P2 第四批（店铺装修 / 内容社区 Phase 1）契约先行**：①**F11 店铺装修**——S1c/S1d `/api/seller/shops/{shopId}/decoration`（ShopDecorationVO/Req：公告 + 轮播 ≤5 + 推荐商品位 ≤8；归属校验同 S1b；hotProductIds Phase 1 不校验商品归属）+ SH1 ShopVO 增量 `decoration`；②**F12 内容社区 Phase 1**——新增 P28-P33 `/api/notes/**`（发布/流/详情/点赞取消/作者软删；纯文本+图集 ≤9、关联商品 ≤5 发布时校验在售、点赞 uk 幂等原子计数；落位 item-service，[ADR-048](../adr/048-content-notes-in-item-domain.md)）；数据模型 `shop_decoration`/`note`/`note_product`/`note_like` 按 ADR-045 口径直接入 Initialize.sql 快照（50→54 表）；鉴定体系/富文本/评论 Phase 2 立项 |

---

## 十一、待决策事项（V5.5 收敛）

> V5.4 记录的 11 项待决策已在 V5.5 全部落档（ADR-026/027/028/029/032 及对应实现均已合入并测试通过）。
> 下表为**已决归档**（结论 → 正文落点）；仅保留 1 条真开放项，待产品评审。

### 11.1 已决归档（10 条）

| # | 事项 | 结论（V5.5 已决） | 正文落点 |
| - | ---- | ------------------ | -------- |
| 1 | `AddressResp.updateTime` | **删除**（前端全仓仅类型声明、无读取点，已实施；领域模型内部审计口径不变） | §4.2.2、§4.14.2 I10、§8.2 |
| 2 | 售后 `evidenceImages` | **代码未实现，不保留无排期承诺**：请求体不接受该字段，正文标注删除；凭证图上传移入 roadmap | §4.6.4、§十二 12.2 |
| 3 | `WithdrawalApplyReq` 字段名 | **以代码为准** `bankAccount`/`bankName`（前端同步改名） | §4.6.5 T23 |
| 4 | 下单多券 `userCouponIds` | **字段已删除**（已实施）；多券叠加需《多券金额分摊规则》PRD → 开放项 O1 + roadmap | §4.6.1、§十一 11.2、§十二 12.2 |
| 5 | `WithdrawalVO.bankAccount` 明文 | **分端口径**：商家端（T23/T24）仅 `bankAccountMasked`（`bankAccount=null`）；管理端（AD22）明文+脱敏都返回（权限保护） | §4.6.5、§4.13.7、§8.5 |
| 6 | PAY2 微信回调应答体 | **v3 规范裸 JSON**：成功 HTTP 200 + `{"code":"SUCCESS","message":"成功"}`；失败 HTTP 500 + `{"code":"FAIL","message":"处理失败，请重试"}`（触发微信重推）；幂等重复通知仍 200+SUCCESS | §4.7.1 |
| 7 | S5 / T26 / P6 | **移入 roadmap"未规划端点"**，正文标 ⚪ 未实现（Phase A 店铺页商品列表用 P1 `sellerId` 过滤；shopId 口径待 Phase B `product.shop_id`） | §4.3.2、§4.4.2、§4.6.6、§十二 12.1 |
| 8 | V5.0 草案未实现字段族 | **全部删除，以实现为准**（shopName/traces/targetUser*/specDimensions 等；`ProductDetailVO` 草案整体移出正文） | 各 VO 小节 |
| 9 | 售后 `type` | **Integer 保留**，仅允许 1=退货退款 / 2=仅退款（`@Range` 校验，非法 → 400 `PARAM_ERROR`）；枚举值表已补 | §4.6.4 |
| 10 | 站内信端点 | **按 notification-service 实现补录**（U28-U32 草案行下线重定义为已实现端点） | §4.2.9、§7.2 |

### 11.2 开放项（待产品评审，1 条）

| # | 事项 | 现状 | 归属 |
| - | ---- | ---- | ---- |
| O1 | 多券叠加接线（`userCouponIds`） | 策略层多券逻辑已就绪，请求体字段已删（V5.5）；重新接线需先出《多券金额分摊规则》PRD（金额分摊、跨券互斥、退款回滚口径） | **待产品评审 → PRD**；挂账 §十二 12.2 |

---

## 十二、Roadmap — 未规划端点与字段（🆕 V5.5）

> 按"不保留无排期承诺"原则，从正文契约移出的未实现能力集中挂账于此。
> 启动任一项前**必须先更新 PRD/规格**；前端**不得对接**以下端点/字段。

### 12.1 未规划端点

| 原编号 | 端点 | 现状口径 |
| ------ | ---- | -------- |
| S5 | `GET /api/shop/{sellerId}/products`（店铺商品分页） | **Phase A**：店铺页商品列表用现有 P1 `GET /api/item/product/page` 的 `sellerId` 过滤参数；**shopId 口径待 Phase B**（`product.shop_id` 落地）后重开评估 |
| T26 | `GET /api/seller/apply`（最新入驻申请） | 未实现；重开需排期 |
| P6 | `GET /api/item/product/{id}/detail`（商品详情聚合） | 未实现；详情由 P2 `GET /api/item/product/{id}` 返回 `ProductVO` 承担 |

### 12.2 未排期字段 / 能力

| 事项 | 说明 |
| ---- | ---- |
| 售后凭证图 `evidenceImages` | `AfterSaleApplyReq` 不接受该字段；凭证图上传与存储方案待排期 |
| 多券叠加 `userCouponIds` | 已从下单请求体删除（V5.5）；策略层多券逻辑保留，接线需先出《多券金额分摊规则》PRD（§十一 11.2 O1） |
| 微信回调 `out_trade_no` 断链修复 | **已完成**（ADR-033 落地：验签+AES-GCM 解密+字段提取收口 ia-integration 通道适配层，V5.5）；PAY2 对外契约不变（§4.7.1） |

---

> **文档维护规则**：
> 
> 1. 新增/修改 API 端点时，必须同步更新本文档
> 2. CO/VO 字段变更时，必须更新 §八 的 JSON 示例
> 3. 前端页面变更时，必须更新 §七 的映射表
> 4. 错误码新增时，必须更新 §二 的错误码表
> 5. 本文档是前后端唯一契约——后端 Controller 实现必须与本文档一致，前端 TanStack Query hook 必须基于本文档定义
