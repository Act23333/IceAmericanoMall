# ADR-031: 多粒度 token 吊销 —— 三级模型（jti / tokenVer / refresh 会话）

## 背景

ADR-027 补齐了 jti 黑名单写侧（登出 / 重置密码拉黑"当前请求携带的那一个令牌"），并把
`auth:blacklist:{jti}` 固化为网关依赖的跨服务契约键。该决策**明确记录了两个未收敛的残余风险**：

1. **改密后其他设备的 access token 存活至过期**：改密只拉黑当前请求的令牌，其他端此前签发的
   access token 在各自剩余有效期内仍可通过网关；且改密**未拆除**该用户的 refresh 会话，
   持有旧 refresh token 的设备还能轮换出新 access token —— 即"改密不等于全端下线"。
2. **管理端禁用用户不触及认证域**：`PUT /api/admin/user/{id}/status`（user-service
   `UserAdminManager.updateStatus`）只写 `user.status`；被禁用账号的 access token 在过期前仍可访问。
   该流程**无法取得用户的活跃 jti** —— access token 无状态、不落库，Redis 里只有"每端最后一次签发
   的 jti"（且被同端后续登录覆盖），据此逐个拉黑既不完整又会误伤。

两个风险的共同根因：**jti 是"单令牌"粒度，而语义上需要的是"该用户的全部令牌"粒度**；
而无状态 JWT 无法枚举活跃 jti，因此必须在令牌里携带一个可被服务端统一推进的**用户级版本**。

同时，ADR-027 已确立的可用性口径必须保持：网关侧吊销校验是 `WebFilter`/`GlobalFilter` 里的
**非阻塞** Redis 查询，Redis 异常 **fail-open**，且**每请求的 Redis 往返次数不得增加**
（网关是全局咽喉，禁止为新增能力再叠一次往返或引入 `.block()`）。

## 决策

**引入三级吊销模型，并把握销判定的成本保持不变。**

### 1. 三级粒度与各自职责

| 粒度 | 载体 | 写入时机 | 覆盖范围 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| **单 token**（jti） | Redis `auth:blacklist:{jti}`（TTL = 令牌剩余有效期） | **仅登出** | 登出请求携带的那一个 access token | 语义不变，见 ADR-027；`jti` 粒度最适合"只下线这一次会话" |
| **全用户**（tokenVer） | Redis `auth:tokenver:{userId}`（**不设 TTL**） | 改密 / 重置密码、管理端禁用（经内部端点） | 该用户此前签发的**全部** access token（全端、全设备） | 本 ADR 新增；access token 签发时写入 `ver` claim |
| **会话拆除**（refresh） | 删除 `login:user:refresh:{userId}:{deviceType}` + 反查索引 | 与用户级吊销同时（改密 / 禁用） | 该用户**全部端**的 refresh 会话 | 复用既有会话拆除能力，按 `PlatformEnum` 全端遍历 |

### 2. tokenVer 语义（契约固化）

- **键**：`auth:tokenver:{userId}`（前缀常量 `RedisKeyConstants.JWT_TOKEN_VERSION_PREFIX`），
  值是可解析的十进制长整型（`INCR` 产出）。
- **claim**：access token 写入 `ver` claim（`JwtUtils.TOKEN_VERSION_CLAIM = "ver"`），
  取值为**签发时刻**的 `currentVersion(userId)`；登录与 refresh 换发走同一签发链路，故两者都带当前版本。
- **键缺失 = 版本 0**；**token 无 `ver` claim = 0**。
- **不设 TTL**（与黑名单刻意相反）：版本键若过期，读侧会把版本视为 `0`，
  "因改密/禁用而版本落后"的令牌将被**重新放行**，等于吊销被回滚。故版本键常驻
  （每个"被吊销过"的用户一个键，体量可控）。
- **推进即失效**：`INCR` 后所有旧令牌的 `ver` 落后于当前版本 → 网关 401；新令牌携带新版本 → 正常。

### 3. 为什么 refresh 会话拆除**不等于**吊销（两者必须同时做）

- 只推进 tokenVer：旧 access token 立刻失效，但**持有旧 refresh token 的设备可立即轮换**出
  携带"新版本"的 access token —— 攻击者/旧设备被"续命"，等于没下线。
- 只拆除 refresh 会话：已签发的 access token 在其剩余有效期内仍然可用（无状态、无法撤回）。
- 因此用户级吊销 = **推进 tokenVer（管已签发的 access token）+ 拆除全部 refresh 会话（管续期通道）**，
  两者互补，缺一不可。这也是"改密后旧设备必须真正下线"这一产品要求的完整实现。

### 4. 网关侧：两级校验**合并为一次** Redis 调用

`JwtBlacklistFilter`（order `-700`，类名保留以减小文档面）用**一次 `MGET`** 同时取：

```
MGET auth:blacklist:{jti}  auth:tokenver:{userId}
```

判定顺序：

1. 黑名单值非空（键存在）→ **401**；
2. 版本键存在 → 与 token 的 `ver` claim（缺失 / 类型异常按 `0`）比较，**不相等 → 401**；
3. 版本键缺失 → 视为"从未被用户级吊销"，**跳过版本比对**；
4. token 无 `userId` claim → 只查黑名单（`MGET` 单键），跳过版本比对；
5. token 无 `jti` → 维持 ADR-027 原语义整体放行（无法比对，记 DEBUG）；
6. Redis 异常 / 值不可解析 → **fail-open**（放行 + WARN），与 ADR-027 一致。

**为什么 MGET 而非 Lua**：两个键的判定是"读两个值后在 JVM 内做纯比较"，没有需要原子性的
读-改-写；`MGET` 一轮往返即可完成，且天然可测（`ReactiveValueOperations.multiGet`）、
不引入脚本运维面。Lua 只有在"判定与写入必须原子"时才值得。

### 5. 触发点矩阵（谁走黑名单、谁走版本号）

| 流程 | 粒度 | 动作 |
| :--- | :--- | :--- |
| 登出 `POST /api/auth/logout` | 单 token | 拆本端 refresh 会话 + 拉黑本次请求的 access token（**不变**） |
| 改密 / 重置密码 `POST /api/auth/reset-password` | **全用户** | `INCR auth:tokenver:{userId}` + 拆全部 refresh 会话（**由 ADR-027 的"拉黑当前令牌"改为本口径**）；目标 userId 取自凭证行，**匿名改密同样生效** |
| 管理端禁用 `PUT /api/admin/user/{id}/status` | **全用户** | user-service 经 `AuthContextClient.revokeUserTokens` 调内部端点，同上 |

### 6. 新增内部端点契约（ADR-004：raw 透出，不包 `Result`）

```
POST /internal/auth/token-version/revoke
Body: { "userId": <Long> }        （ia-api 契约对象 TokenRevokeCO）
返回:  无（void；失败以原始 HTTP 异常透出）
动作:  incrementVersion(userId) + revokeAllSessions(userId)
```

调用侧降级语义（`AuthContextClientFallback`）：**fail-open** —— `log.warn` 后返回，不抛异常。
禁用主流程（已提交的本地状态变更）不因认证域短暂不可用被判失败，但**必须告警**以便人工跟进。
（与同接口的 `setUserRole` fail-closed 语义刻意不同：授权变更不允许静默丢失，
吊销是"登录态失效的加速器"，两者取舍不同。）

### 7. 契约键豁免

`auth:tokenver:{userId}` 与 `user:info:{userId}`、`user:perms:{userId}`、`auth:blacklist:{jti}` **同级**：
**豁免** `09-constraints.md`「跨服务状态不得共享 Redis 键」约束，但豁免范围仅限
**"auth 写、网关只读"**；任何第三方服务不得读写。改名或改语义必须两侧同步发布。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持 ADR-027 现状（只拉黑当前令牌） | 零改动 | 两个残余风险保持开放：改密/禁用都不真正下线 |
| 缩短 access token TTL 至分钟级 | 无需版本号 | 放大刷新压力与失败面；仍需为"即时下线"提供通道 |
| 把 access token 落库并枚举活跃 jti 逐个拉黑 | 粒度最细、可精确到设备 | 引入有状态令牌服务，写放大与查询成本高；与既有无状态 JWT 口径背离 |
| 网关把"黑名单 + 版本"两次查询分开做 | 改动更小 | 每请求多一轮 Redis 往返（网关咽喉成本翻倍） |
| 用 Lua 脚本一次判定 | 一次往返 + 服务端原子 | 判定本身无需原子性；引入脚本运维与可测性成本 |
| **tokenVer + 一次 MGET + 全端拆会话（采纳）** | 每请求成本不变；无状态令牌上实现"全用户下线"；兼容存量令牌无需迁移 | 版本键常驻（不可 TTL）；fail-open 窗口仍在 |

## 后果

**正面**

- ADR-027 的两个残余风险同时收敛：改密/重置密码、管理端禁用**即刻全端下线**（access token 失效 + refresh 会话拆除）；
- 网关每请求的 Redis 往返次数**不变**（仍是 1 次），认证链路性能与可用性口径不变；
- 兼容规则清晰、**无需迁移**：存量令牌无 `ver` claim 按 `0`，从未被吊销过的用户版本恒为 `0`；
- 内部端点零传播成本：`/internal/**` 已放行，不新增网关路由；调用侧 fail-open 不阻断管理流程。

**负面 / 风险**

- `auth:tokenver:{userId}` 成为第 4 个跨服务契约键，键名变更需 auth 与网关同步发布；
- **fail-open 窗口**：Redis 不可用时，已推进版本的用户在网关侧仍被放行（直到键恢复可读或令牌过期）。
  与 ADR-027 的取舍一致：吊销是加速器而非唯一防线（签名 + 过期时间仍在）；
- 敏感键被运维误删（或 `maxmemory-policy=allkeys-*` 下被淘汰）会使版本**从 1+ 回落**：
  该类用户"版本落后"的残留令牌会被放行（不设 TTL 已把该风险压到最低）；
- 拆会话按 `PlatformEnum` 有界遍历（≤ 8 次 GET），不使用 `KEYS`/`SCAN`；
  新增平台枚举值时必须同步 `TokenGenerateParam.deviceType()` 的口径（否则该端会话漏拆）。

## 残余风险（明确记录，本次不收敛）

1. **未做实机验证**：本次为单元测试（claim 形状、MGET 合并判定、401 分支、fail-open、全端拆会话）；
   建议后续端到端验证两条链路 ——「改密后旧 access token 调受保护接口 → 期望 401」与
   「管理端禁用后旧令牌 → 期望 401」，并确认 refresh 换发同样被拒。
2. **单端登出仍是"单 token + 本端会话"粒度**：这是刻意设计（登出语义即"退出当前会话"）；
   若产品需要"登出全部设备"，应复用 `revokeUserTokens` 的能力而不是扩大登出语义。
3. **版本键无回收策略**：版本号只增不减，每个"被吊销过"的用户留下一个常驻键。
   量级可控；若未来需要回收，应配合"令牌最大存活时间"推导安全 TTL，不可简单 `DEL`。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/app/port/TokenVersionService.java`
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/infrastructure/adapter/TokenVersionServiceImpl.java`
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/infrastructure/utils/JwtUtils.java`（`ver` claim 语义与兼容规则）
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/infrastructure/adapter/LoginTokenServiceImpl.java`（签发链路 + `revokeAllSessions`）
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/app/AuthManager.java`（`revokeUserTokens` 编排）
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/adapter/web/internal/InternalAuthController.java`（内部端点）
- `Implementation/back-end/gateway-service/src/main/java/org/icedamericanomall/gateway/filter/JwtBlacklistFilter.java`（合并 MGET 判定）
- `Implementation/back-end/user-service/src/main/java/org/icedamericanomall/user/app/user/UserAdminManager.java`（禁用触发，Feign 置于事务之后）
- `Implementation/back-end/ia-api/src/main/java/org/icedamericanomall/fallback/AuthContextClientFallback.java`（吊销降级 fail-open）
- ADR-027（jti 黑名单写侧 + 契约键豁免，本 ADR 修订其残余风险两节）、ADR-026（WS 握手换头，同享本吊销口径）、
  ADR-018（`user:info:` / `user:perms:` 契约键与 RBAC 归属）、ADR-004（内部端点 raw 异常透出）、
  ADR-011（Feign 降级语义三分法）
