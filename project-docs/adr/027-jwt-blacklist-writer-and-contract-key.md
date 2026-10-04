# ADR-027: access token 吊销 —— jti 黑名单写侧语义与契约键豁免

> **状态：已修订（2026-10-03，见 ADR-031）**
> 本 ADR 的读/写侧契约（键名 `auth:blacklist:{jti}`、TTL 语义、契约键豁免、fail-open）**保持有效**。
> 两处口径已被 ADR-031 取代：
> 1. **重置密码**不再"拉黑当前请求的令牌"，改为**用户级吊销**（推进 `auth:tokenver:{userId}` + 拆除全部 refresh 会话）；
>    jti 黑名单**仅保留给登出**（单 token 粒度）。
> 2. 下方「残余风险」第 1、2 条**已由 ADR-031 收敛**。

## 背景

网关的吊销校验读侧早已落地：`JwtBlacklistFilter`（order `-700`，非阻塞 `ReactiveStringRedisTemplate`）按
**`auth:blacklist:{jti}`** 查询，命中即 401，无 `jti` 放行，Redis 异常 fail-open（见该类的可用性权衡注释与 ADR 中既有的吊销实现说明）。

但**写侧一直缺失**，因此读侧是死代码：

1. **登出只拆 refresh 会话**：`LoginTokenServiceImpl.logout` 删除 `login:user:refresh:{userId}:{deviceType}` 与
   `login:user:token:key:{refreshToken}`。jti 与会话中 `accessTokenJti` 的比对（`isAccessTokenValid`）**只被本服务的 `/api/auth/check-session` 使用**，网关完全不知道会话状态 —— 结论：**登出后 access token 在剩余有效期内仍可通过网关访问全部受保护接口**。
2. **重置密码不使任何令牌失效**：改密只更新 `user_auth.credential`，旧 access token / refresh 会话照常可用。
3. **管理端禁用用户不通知认证域**：`user-service` 的 `PUT /api/admin/user/{id}/status` → `UserAdminManager.updateStatus` 只写 `user.status`，既未拆除 refresh 会话，也未拉黑任何令牌；被禁用账号的已签发 access token 在过期前仍可访问（`user.status` 只在档案域读取路径上被消费）。

同时存在一条硬约束冲突需要正面处理：`project-docs/collaboration/09-constraints.md` 规定「跨服务状态不得共享 Redis 键」。`user:info:{userId}` /
`user:perms:{userId}`（ADR-018，auth 写、网关读）已是该规则的**显式豁免契约键**；本决策需要把 `auth:blacklist:{jti}` 加入同一豁免清单。

## 决策

**在 authorization-service 补齐 jti 黑名单写侧，键与 TTL 语义固化为跨服务契约。**

1. **契约键**：`auth:blacklist:{jti}`（前缀常量 `RedisKeyConstants.JWT_BLACKLIST_PREFIX`）。值与判定无关 —— 网关"键存在即已吊销"。
   该键是**网关依赖的跨服务契约键**，与 `user:info:` / `user:perms:` 同级：**豁免**「跨服务不得共享 Redis 键」约束，但豁免范围仅限"auth 写、网关只读"，任何第三方服务不得读写；改名或改 TTL 语义必须两侧同步发布。
   （ADR-031 在该豁免清单上追加同级契约键 `auth:tokenver:{userId}`，同一豁免口径。）
2. **TTL 语义**：`TTL = max(access token 剩余有效期, 1s)`。吊销名单只需覆盖令牌自身的剩余生命期：令牌自然过期后，网关的签名 + 时效校验自会拒绝，键随之回收，避免黑名单无限膨胀；`1s` 下限用于抵消剩余秒数取整为 0 的临界情况，防止写入即被清除导致吊销形同虚设。
3. **接入点（写侧语义：只拉黑"当前请求携带的那一个令牌"）**：

   | 流程 | 端点 | 行为 |
   | :--- | :--- | :--- |
   | 登出 | `POST /api/auth/logout` | 先拆除 refresh 会话（既有语义），再拉黑请求头中的 access token |
   | 重置密码（改密） | `POST /api/auth/reset-password` | ~~拉黑请求头中的 access token~~ → **已改由 ADR-031 接管**：推进用户级令牌版本（`auth:tokenver:{userId}`）+ 拆除该用户全部 refresh 会话，**不再写 jti 黑名单** |

   登出仍在 `AuthManager` 编排，经应用层端口 `TokenBlacklistService`（`app/port`）调用基础设施实现
   `TokenBlacklistServiceImpl`（`infrastructure/adapter`），复用既有 `JwtUtils.getJti` / `getRemainingTtl`
   （其 Javadoc 早已声明为黑名单 TTL 用途）。改密链路见 ADR-031。
4. **失败与边界语义（与读侧对称）**：
   - `jti` 缺失 / 空白 → 跳过并记 DEBUG（无键可比对，与读侧"无 jti 放行"一致）；
   - 未携带 `Authorization`（含匿名重置密码）→ 跳过；
   - Redis 写入异常 → `log.warn` 后返回，**不阻断登出/改密主流程**（fail-open）：吊销是"登录态失效的加速器"，不得让缓存故障阻断用户操作；
   - 无本地事务：黑名单写入是缓存侧旁路动作，不参与登出/改密的原子性语义（与 ADR-017 认证边界收敛后"单条本地写入"的口径一致）。
5. **本决策不改读侧与网关口径**：`JwtBlacklistFilter` 一行不改，`/ws/chat/**`（握手换头后，见 ADR-026）与普通 HTTP 请求共用同一吊销校验。
   （同一次改动**之后**，ADR-031 在该过滤器内扩展出用户级版本比对，仍保持"一次 Redis 调用"与同一 fail-open 口径。）

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持现状（只拆 refresh 会话，靠 `check-session` 兜底） | 零改动 | access token 在过期前始终可用；登出/改密/禁用**都不真正下线**；读侧 `JwtBlacklistFilter` 永远是死代码 |
| 把吊销校验塞回 `TokenValidator` | 校验与认证同点，天然早于路由 | 需在解码器里 `.block()` 等 Redis，运行在 Netty 事件循环上会造成全站排队（已被否决过的实现，见 `JwtBlacklistFilter` 类注释） |
| 缩短 access token TTL 至分钟级 + 只依赖 refresh 轮换 | 无需黑名单 | 仍需为"主动登出/改密即刻生效"提供通道；TTL 过短会放大刷新压力与失败面 |
| 网关自建吊销表（本地/独立存储） | 与 auth 解耦 | 与 `user:perms:` 的既有契约模式背离；两处状态源易漂移；网关需新增写通道 |
| 按 userId 全量拉黑该用户所有活跃令牌 | 一次性彻底下线 | access token 无状态、不落库，**无法枚举**用户全部活跃 jti；Redis 中仅存"每端最后一次签发的 jti"（且被同端后续登录覆盖），据此拉黑既不完整又会误伤他人 |
| **契约键 + 逐令牌写入 + TTL 随令牌过期（采纳）** | 复用既有读侧契约与 `JwtUtils` 能力；改动局部；失败不阻断主流程 | 只覆盖"当前请求的令牌"；跨设备残留窗口见下 |

## 后果

**正面**

- 登出与改密**即刻生效**：当前 access token 的剩余有效期内被网关拒绝，不再只依赖 `check-session` 这一客户端自觉行为；
- 读侧 `JwtBlacklistFilter` 从死代码变为有效防线，WS（ADR-026）与 HTTP 共用同一吊销口径；
- 键/TTL 语义与实现同处（`RedisKeyConstants` + 实现类 Javadoc + 端口 Javadoc），无魔法值；
- 缓存故障不影响用户可感知的登出/改密结果（fail-open，与读侧一致）。

**负面 / 风险**

- 黑名单键在 auth 与网关心目中成为共享契约，键名变更需两侧同步（同 `user:perms:` 的既有约束）；
- fail-open 窗口：Redis 不可用时已吊销令牌在网关侧被放行，直到 key 恢复可读或令牌自然过期；
- 逐令牌写入意味着"登出/改密"的收敛范围是**当前请求这一个令牌**，见下方残余风险
  （改密一侧已由 ADR-031 收敛为用户级吊销）。

## 残余风险（本次不收敛；第 1、2 条已由 ADR-031 收敛）

> ADR-031（多粒度 token 吊销）引入用户级令牌版本 `auth:tokenver:{userId}` 与全端 refresh 会话拆除，
> 并新增 `POST /internal/auth/token-version/revoke` 内部端点，下列第 1、2 条**已收敛**；
> 第 1 条中"改密走 jti 黑名单"的口径由 ADR-031 取代。第 3 条（未做实机验证）仍然开放。

1. ~~**改密后其他设备的 access token 存活至过期**~~ → **已于 ADR-031 收敛**：改密/重置密码改为
   用户级吊销（推进 `auth:tokenver:{userId}`，全端 access token 即刻 401；同时拆除该用户全部 refresh 会话），
   匿名改密同样生效（userId 取自凭证行）。**改密不再写 jti 黑名单。**
2. ~~**管理端禁用用户不触及认证域**~~ → **已于 ADR-031 收敛**：user-service `UserAdminManager.updateStatus`
   在状态置为冻结/禁用时调用 `AuthContextClient.revokeUserTokens` → `POST /internal/auth/token-version/revoke`
   （令牌版本 INCR + 拆除该用户全部 refresh 会话）；调用位于本地事务提交之后，降级 fail-open 不阻断禁用主流程。
3. **未做实机验证**：本次仅完成单元测试（键形状、TTL、跳过条件、fail-open）；建议后续以「登出后立即用旧 access token 调受保护接口 → 期望 401」做一次端到端验证。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/infrastructure/adapter/TokenBlacklistServiceImpl.java`
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/app/port/TokenBlacklistService.java`
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/infrastructure/constant/RedisKeyConstants.java`
- `Implementation/back-end/authorization-service/src/main/java/org/icedamericanomall/auth/app/AuthManager.java`（登出 / 重置密码编排）
- `Implementation/back-end/gateway-service/src/main/java/org/icedamericanomall/gateway/filter/JwtBlacklistFilter.java`（读侧契约与 fail-open 语义）
- ADR-018（`user:info:` / `user:perms:` 契约键与 RBAC 归属授权域）、ADR-017（认证边界收敛、无本地事务口径）、ADR-026（WS 握手换头，同享本吊销口径）
- ADR-031（多粒度 token 吊销 —— 用户级令牌版本 + 全端会话拆除；修订本 ADR 的残余风险第 1、2 条与改密口径）
