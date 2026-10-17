# ADR-065: 登录失败口径收敛与抗账号枚举（唯一出口 + dummy verify）

## 背景

只读审计实测 `authorization-service` 的密码登录链路，四类失败各自走不同的对外口径：

| 失败原因 | 抛出点（修复前） | `ErrorCode` | HTTP | 对外文案 |
| :-- | :-- | :-- | :-- | :-- |
| 账号不存在 | `PasswordLoginStrategy#findUserAuth` | `USER_NOT_FOUND(1102)` | **404** | "账号或密码错误" |
| 密码错误 | `PasswordLoginStrategy#login` | `PASSWORD_ERROR(3007)` | **200** | "账号或密码错误" |
| 账号状态不可登录 | `UserAuth#assertActive` | `USER_STATUS_ABNORMAL(3008)` | **200** | "账号状态异常" |
| 极高风险阻断（"冻结"） | `AccountFreezeHandler#handle` | `ACCOUNT_FROZEN(1017)` | **403** | "账号存在安全风险" |

三个独立缺陷：

1. **状态码即枚举信道**：`404 / 200 / 403` 三者互不相同 —— 攻击者**不猜密码、不看文案**，只读
   HTTP 状态码即可判定「某账号是否存在」「是否存在但被冻结」。这与
   `collaboration/09-constraints.md` §三·六「未认证 / 无权限 → 401」直接冲突。
2. **业务码 + 文案同样可区分**：即使统一状态码，`1102/3007/3008/1017` 与四种文案仍可区分。
3. **响应时间可区分**：BCrypt 校验（cost=10）约几十毫秒，是登录最重的一段 CPU，而「账号不存在」
   路径**一次哈希都不做**（直接抛异常；`verifyPassword` 对空 credential 也提前返回 false）——
   两条失败路径耗时相差一个数量级，构成经典 timing oracle。

文案层面已有意识（三处硬编码"账号或密码错误"）但**没有机器守护**，且口径分散在领域聚合、
策略、风控处理器三处，任一处改动即回退。

## 决策

### 1. 唯一对外出口：`401 + PASSWORD_ERROR(3007) + 中性文案`

四类原因**一律**抛 `new BizException(ErrorCode.PASSWORD_ERROR)`（**不传自定义文案**，
让 `ErrorCode` 的中性 `message` 成为唯一文案来源）：

- `ErrorCode.PASSWORD_ERROR` 的 `HttpStatus`：`OK(200)` → **`UNAUTHORIZED(401)`**，`message`
  由 "登录密码错误" → **"账号或密码不正确"**；
- `ErrorCode.USER_STATUS_ABNORMAL` 的 `HttpStatus`：`200` → **`401`**（**安全默认**：即便遗漏调用
  也不会退化成 200），并在 javadoc 明确「**登录链路禁止使用本码**」，仅保留给非登录域
  （user-service `User#assertActive` / `UserStatusEnum#of`）；
- `ErrorCode.ACCOUNT_FROZEN`：`403` → **`401`** 且标 **`@Deprecated`**（登录链路改抛 3007，
  当前零生产引用）；保留枚举项是因为契约 §二 与历史文档仍引用该码。

**为什么不新增一个 `LOGIN_FAILED` 码**：前端 `packages/api/error-codes.ts` 与契约 §二 早已把
`3007` 登记为「登录失败最高频分支」，并已建议中性文案 —— 换码是**纯 churn**（前后端 + 契约
三处同步）而无新增表达力；改为在枚举 javadoc 上把它的语义显性化（"登录失败唯一对外出口"）。

### 2. 披露策略下沉到唯一决策点，聚合只回答"是否可登录"

- `UserAuth#assertActive()`（抛 3008）**删除**，改为纯查询 `UserAuth#isActive()`；
- `UserAuth#hasPasswordCredential()` **新增**（`verifyPassword` 复用它，`UserAuthProvisioner`
  与 `AuthManager#register` 的重复内联判断一并收口）。

理由：**失败如何对外表达属披露策略**，必须由登录链路的唯一策略持有者（`PasswordLoginStrategy`）
决定；若聚合自己抛"状态异常"，"账号存在但不可用"就天然可区分。

### 3. 原因细分只进日志：新增 `domain/enums/LoginFailureReason`

`ACCOUNT_NOT_FOUND / PASSWORD_MISMATCH / STATUS_NOT_ACTIVE / RISK_BLOCKED`，**硬约束：不得出现在
任何 Response / VO / `Result.msg` 中**，只允许作为服务端 WARN 日志字段（标识经
`DesensitizeUtil.mask` 脱敏）。这满足"内部可排障、对外不可区分"的双重要求。

### 4. dummy verify：消除可观测时间差（**不是安全强度**）

账号不存在、或该凭证行**从未设过密码**（仅验证码/微信登录建的行）时，仍用**预置的合法 BCrypt
哈希**（`PasswordLoginStrategy.DUMMY_PASSWORD_HASH`，cost=10，对应一个随机生成、从未使用、
不在任何库表中的口令）对提交的密码做一次真实 `matches`，**再丢弃结果**。并**不得提前 return**：
`login()` 写成「定位 → 比对 → 状态 → 唯一失败出口」一条顺序流。

三条工程纪律：

- 哈希必须**形态合法**：Spring Security 对非法形态的 encoded 会**跳过**整个计算只打警告，
  时间差原地复发；
- 常量**不是秘密**，可以 `public`（暴露不降低安全性），公开的目的是让测试能断言"确实用了它"；
- 注释必须写明"它不增加抗暴力破解能力、不改变判定、不参与业务分支" —— 防止后来者误以为
  "多了一次哈希 = 更安全"而据此放宽限流等真实防护。

### 5. 「被冻结」的提示改走旁路

用户可见的"账号异常/被冻结"提示只能通过**登录成功后**的一次性提示或站内信；**不得**在登录失败
响应里区分 —— 否则第 1/4 条白做。

### 6. 前端同口径（不是"后端改完前端自然对"）

- `packages/api/error-codes.ts`：`3007/3008/1017` 的文案统一为 `LOGIN_FAILED_MESSAGE`；新增
  `isLoginFailure()`（认 **HTTP 401**、`3007`、兼容 `3008`/`1017`）与
  `getLoginFailureMessage()`（**恒定中性**，非登录失败类错误回落 `getUserMessage`）；
- 登录页两处失败分支（密码/验证码登录、微信登录）改用 `getLoginFailureMessage`，**禁止**按
  `code` 分支提示"该账号不存在/密码错误/账号被冻结"；
- `packages/api/client.ts`：认证端点（`/api/auth/**` 登录类）的 401 **不参与**"刷新令牌 + 重放"
  被动兜底 —— 否则一次输错密码会触发 `handleRefreshFailure()` → 广播 `auth:logout` →
  布局层 `router.replace('/auth/login?redirect=…')`，登录页被无谓重导航。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| :-- | :-- | :-- | :-- |
| **A. 统一 401 + 唯一码 3007 + dummy verify**（本 ADR） | 状态码/码/文案/时间四维不可区分；前端已有 3007 文案位；改动面最小 | `PASSWORD_ERROR` 名称与"账号不存在"语义不符，靠 javadoc 澄清 | **采纳** |
| B. 新增 `LOGIN_FAILED(3043)` 专用码，语义最正 | 命名准确 | 前后端 + 契约三处同步的纯 churn；`3043` 需新占号；对攻击面零改善 | 否决 |
| C. 保留 3007/3008/1017 三码但统一 401 + 统一文案 | 改动更小 | 业务码仍可区分（前端/日志/第三方 SDK 都能读 body）⇒ 枚举信道只关闭了一半 | 否决 |
| D. 只做固定时长（sleep 到常量耗时，如恒 200ms） | 不依赖 BCrypt 常量 | 计时器抖动大、CPU 抢占下不稳定，且把并发容量绑到常量时延；dummy verify 天然贴合真实路径成本 | 否决（成本模型更差） |
| E. 账号不存在时**不做**任何补偿，仅靠限流 | 零改动 | 限流是频率维度，不解时序维度；单次探测即泄漏 | 否决 |

## 后果

**正面**

- 四维（HTTP 状态码 / 业务码 / 文案 / 响应时间）对攻击者不可区分；登录失败口径单点定义，
  且被 5 个用例锁死（单元 ×3 + 集成 ×2），任一漂移即 CI 红；
- 与 `09-constraints.md` §三·六 对齐（未认证 = 401），修掉一处"用 200 承载未认证"的偏离；
- 披露策略与状态判定解耦：聚合不再知道"失败了怎么对外说"。

**负面 / 风险**

- **非登录域副作用（已知、已登记）**：`USER_STATUS_ABNORMAL` 状态改 401 后，user-service
  `GET /api/user/info`（冻结用户）与 `UserStatusEnum#of`（管理端非法状态值）也变为 401 ——
  前者会让前端走 401 分支（刷新失败 → 清空本地凭证 → 跳登录页），后者语义上更接近参数错误。
  本批**未**改动这两处，属"全局状态纠正"的可接受外溢，若产品要求区分需另立批次；
- dummy verify 使**每次**登录失败都多消耗一次 BCrypt CPU（约几十毫秒）—— 这是"抗枚举"的
  直接成本，需由既有 429 频控（`@RateLimit` + 网关 `RequestRateLimiter`）兜住放大风险；
- 中间层剥离 body 时，前端只能看到 HTTP 401：`isLoginFailure()` 已把 401 计入，但会**误把
  "登录页上的令牌过期提示"**也当中性失败（登录页本身无 token，实际影响趋零）；
- 契约 §二 的"3xxx 一律 200"表述必须与 3007/3008 的 401 并存说明，已在 §2.0 末补硬约束段，
  避免后人按旧口径回退。

## 日期

2026-10-18

## 参考

- `project-docs/collaboration/09-constraints.md` §三·六（错误承载的四类划分：未认证 → 401）
- `project-docs/contract/05-API-Specification.md` §2.0 末「登录失败口径」+ §4.1.1「登录失败语义」
- [ADR-017](./017-user-domain-split-and-auth-boundary.md)（凭证唯一所有权归 authorization-service）
- [ADR-051](./051-register-fast-reject-and-verification-scene.md)（注册前置快拒绝：同为"把能省的
  成本与不能省的一致性分开"的先例；该 ADR 已登记"注册接口存在账号枚举"为 accepted trade-off，
  本 ADR 处理的是**登录侧**的同一风险，二者不冲突）
- [ADR-004](./004-internal-api-exception-passthrough.md)（外部包 `Result` / 内部裸状态码的承载分工）
