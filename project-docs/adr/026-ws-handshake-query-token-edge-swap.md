# ADR-026: WebSocket 握手鉴权 —— 网关边缘 query token 换头

## 背景

ai-service 的实时人工客服走 STOMP over WebSocket（SockJS 兼容降级），端点 `/ws/chat`，由 gateway-service 的 `ai-service` 路由块（`Path=/ws/chat/**`）转发。下游
`ChatHandshakeInterceptor` / `StompAuthInterceptor` **只信任网关注入的 `X-User-Id` 等可信上下文头**（与普通 HTTP 接口同一套下游约定），自身不解析、不校验令牌。

该设计中存在一个链路断点：

1. `/ws/chat/**` 不在网关白名单内，落到 Spring Security 的 `anyExchange().authenticated()`；
2. 浏览器发起 WebSocket 握手（`new WebSocket(...)` / SockJS 的 `info`、`xhr_streaming`、`websocket` 三段请求）**无法自定义 `Authorization` 请求头** —— 浏览器 WebSocket API 不支持设置任意请求头（子协议 `Sec-WebSocket-Protocol` 是唯一可自由使用的握手字段，但被 STOMP 子协议占用）；
3. 结果：握手在网关被 401 拒绝，实时客服通道完全不可用；同时 ai-service 拿不到 `X-User-Id`，即使放开 `authenticated()` 也会退化为匿名。

安全边界约束（不可破坏）：下游只信任网关注入的身份头，因此令牌校验**必须**留在网关边缘；不能把「验签 + 查 Redis」下沉到 ai-service（那会让 ai-service 依赖公钥/Redis 并与 `X-User-Id` 口径重复）。

## 决策

**在网关边缘把握手请求 query 中的令牌换写成 `Authorization` 头，鉴权仍由既有安全链完成。**

1. **新增 `WsHandshakeTokenFilter`（`WebFilter`，order = `-900`）**：仅对配置路径（`gateway.ws.token-paths`，默认 `/ws/chat/**`；魔法值全部外置）生效。当
   **入站无 `Authorization` 头** 且 **query 含 `token` 参数**（非空）时：
   - 设置请求头 `Authorization: Bearer <token>`；
   - **重建向下游的请求 URI，剔除 query 中的 `token` 参数**（其余参数逐字保留、不二次编码）；
   - 两条件任一不满足时**原样放行**（不改头、不改写 URI）。
2. **鉴权本身不做**：换头后完全复用现有链路 —— Spring Security（验签 + 时效）→ `JwtBlacklistFilter`（jti 吊销）→ `UserContextHeaderFilter`（注入 `X-User-Id` / `X-User-Roles` 等）。
   `/ws/chat/**` **不进白名单**，维持 `authenticated()`，因此握手与普通接口鉴权口径完全一致。
3. **必须早于 Spring Security 安全链**：Spring Security 的 `WebFilterChainProxy` 由
   `WebFluxSecurityConfiguration.WEB_FILTER_CHAIN_FILTER_ORDER` 取值 `-100`；本过滤器取 `-900` 先于它执行。**不能写成 `GlobalFilter`** —— Spring Cloud Gateway 的
   `GlobalFilter` 在路由阶段（`FilteringWebHandler`）执行，晚于全部 `WebFilter`，那时 Security 早已返回 401。网关现有 `UntrustedHeaderSanitizerFilter(-1100)` /
   `TraceIdFilter(-1000)` 等 `-1000` 量级序位是 `GlobalFilter` 内部的相对序位，与 `WebFilter` 序位不冲突（且二者都不改写 `Authorization`）。
4. **前端约定**：SockJS/STOMP 连接的每个 HTTP 请求 URL 都需带上 `?token=<accessToken>`（SockJS 的 `info` / 传输请求各自独立走网关）。
5. **令牌不落下游**：过滤器在转发前已剔除 query 参数，下游服务、下游访问日志、反代日志都不会看到令牌；网关本体的入口访问日志与浏览器历史仍会记录 URL（见「后果」）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 把 `/ws/chat/**` 加入白名单，由 ai-service 自验令牌 | 网关零改动 | 破坏「下游只信任网关注入身份头」边界：ai-service 需引入公钥/JWKS 与吊销名单依赖，安全逻辑散落两处；白名单放行将同时放过匿名握手，越权面扩大 |
| 网关自验 CONNECT 帧（STOMP 层） | 令牌可不进 query（可放 STOMP `CONNECT` 帧头） | 握手升级发生在 STOMP 帧之前：安全链在校验 `Upgrade` 请求时已 401，除非把路径改白名单；且要求网关理解 STOMP 协议并逐帧透传/改帧，越过网关职责边界，AI 侧仍需重建身份 |
| 子协议 `Sec-WebSocket-Protocol` 携带令牌 | 不进 URL / 日志 | 与 STOMP 子协议名（`v12.stomp` 等）冲突，多值拼接语义脆弱；下游反向代理对子协议透传行为不一致 |
| Cookie 携带令牌 | 浏览器自动带上 | 与本项目"令牌走 `Authorization` 头、不落 Cookie"的既有口径冲突（CSRF 面扩大） |
| **query token 边缘换头（采纳）** | 下游与前端协议都不动；鉴权仍单一出口（网关）；实现局部（一个过滤器） | 令牌出现在握手 URL（可能进入口日志/浏览器历史），但已被网关剔出下游请求 |
| 令牌放 query 且**不**剔除参数 | 改动更小 | 令牌流入下游服务入参、业务日志与反代日志，暴露面成倍扩大，不可接受 |

## 后果

**正面**

- 实时客服 WS 通道可用：握手经网关完成签名/时效/jti 校验，`X-User-Id` 等身份头照常注入，ai-service 零改动；
- 鉴权保持单一出口：`/ws/chat/**` 不进白名单，HTTP 与 WS 的认证口径、吊销口径完全一致（`JwtBlacklistFilter` 对二者同时生效）；
- 换头在 `WebFilter` 阶段完成，**先于**所有 `GlobalFilter` 与路由，避免"路由阶段换头但安全链已拒绝"的时序陷阱；
- 令牌被剔出向下游的 URI，下游服务/日志不受污染。

**负面 / 风险**

- **令牌会出现在网关入口的请求 URL 里**：网关自身访问日志、浏览器历史、以及客户端到网关之间的中间代理可能记录该 URL。缓解：下游转发前剔除参数；网关日志等级与脱敏策略按既有口径控制（本项目未在网关打印请求 URL 的 query）。彻底消除需改为「一次性握手票据」方案（前端先换票再握手，票据短 TTL 且单次使用），本次不做；
- 令牌在 TLS 终止点明文可见（与 `Authorization` 头等价的信任假设，依赖传输层加密）；
- SockJS 多段请求均需带 `token`，前端需在传输 URL 上统一注入（前端改动项，见交接摘要）；
- `gateway.ws.token-paths` 若被配置成宽泛模式（如 `/**`），会把任意接口的 `token` query 参数当作令牌换头 —— 该风险由配置评审与默认值（仅 `/ws/chat/**`）约束。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/gateway-service/src/main/java/org/icedamericanomall/gateway/filter/WsHandshakeTokenFilter.java`
- `Implementation/back-end/gateway-service/src/main/java/org/icedamericanomall/gateway/config/SecurityConfig.java`（`/ws/chat/**` 不在白名单的说明）
- `Implementation/back-end/gateway-service/src/main/resources/application.yml`（`gateway.ws.token-paths`）
- `Implementation/back-end/gateway-service/src/test/java/org/icedamericanomall/gateway/filter/WsHandshakeTokenFilterTest.java`
- ADR-027（jti 黑名单写侧：握手/普通请求共用的吊销口径）、ADR-009（包命名与 scan）
