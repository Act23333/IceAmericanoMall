# ADR-051: 注册前置快拒绝 + 验证码场景隔离

## 背景

用户审视注册/登录语义时提出三个具体问题，核对代码后定性：

1. **拒绝时机太晚**：`AuthManager.register` 原顺序为「消费验证码 → 跨服务建档 → 才在凭证层抛 `PHONE_ALREADY_REGISTERED`」——老用户误走注册页会白白烧掉一条验证码并多一次跨服务调用，才收到拒绝。拒绝策略本身存在且三态精确（带密码→拒绝 / 仅验证码登录过→补齐密码 / 不存在→新建），缺的是**前置快拒绝**；
2. **验证码场景未隔离**：issue 侧虽有 `scene` 字段但仅做日志归因，Redis key 为 `sms:code:{target}`——注册码可用于登录、登录码可用于重置密码（高危互通），且三个场景共享同一份重发限流与失败计数预算（一个接口被刷耗尽全部短信额度）；
3. （讨论中否决）`getOrCreateUser` 返回 `Long` 无 created 信号——因方案 A 的前置本地检查已给 register 完整三态信号，ProvisionResult 契约改造无消费方，按 YAGNI 否决。

## 决策

### A. 注册前置快拒绝（authorization-service 内部，零跨服务）

`register` 在**消费验证码之前**查本地 `user_auth`（`findByIdentityHash`，无 Feign）：

- 凭证已存在**且带密码** → 直接抛 `PHONE_ALREADY_REGISTERED`（"该手机号已注册，请直接登录或使用验证码登录"）——不烧验证码、不建档；
- 凭证存在**无密码**（仅用过验证码登录的老用户）→ 不拦截，走注册=补齐密码的合法路径（ADR-017 既有语义）；
- 不存在 → 走原注册流程。

原凭证层拒绝（`findByHashOrCreateWithCredential` → `attachCredentialOrReject`）保留为并发兜底（分布式锁 + 唯一约束）。

**账号枚举权衡**：前置拒绝使未验证身份者可探测"手机号是否已注册"。大厂（淘宝/京东注册页）同样直接提示"已注册"，以 UX 优先、限流兜底（网关对 auth/verification 的 Redis `RequestRateLimiter` + `@RateLimit`）。

### B. 验证码场景隔离（authorization + verification 两侧）

- **Key 布局**：`sms:code:{scene}:{target}` / `sms:limit:{scene}:{target}` / `sms:fail:{scene}:{target}`（邮件同构）——验证码、重发限流、失败计数全部按场景隔离；
- **契约**：`VerificationCodeVerifyReqCO` 新增必填 `scene`；issue 侧 `scene` 由"日志归因"升级为"必填且参与键隔离"（缺失 → `PARAM_INVALID`）；
- **场景词汇**（auth 领域枚举 `VerificationSceneEnum`）：`REGISTER` / `LOGIN` / `RESET_PASSWORD`。调用点绑定：register→REGISTER、短信/邮箱验证码登录→LOGIN、重置密码→RESET_PASSWORD；user-service 遗留 `USER_CODE` 场景自然成为独立码池；
- **前端契约**：发码（`/code/issue`，`VerificationCodeController` 公开端点）必须按页面流程传对应 `scene`，且与后端校验场景一致——已同步 05-API-Specification 与 02-Frontend-Integration-Checklist。

### 修订：全局日配额 + 场景编码契约 + 机械守卫（2026-10-10，CR 后加固）

1. **全局日配额**（恢复被场景隔离拆掉的成本控制维度）：新增 `sms:daily:{target}` / `email:daily:{target}`（**无 scene 段**，滚动 24h 窗口），`VerificationPolicy.dailySendCap` 默认 10 条/目标/日；签发路径 INCR 先行、超限回退计数并释放重发配额，投递失败同步回补（计数 ≈ 实际发送量）。场景隔离只作用于「验证码 TTL / 失败计数 / 60s 重发窗口」，**单目标成本控制保持全局**；
2. **场景编码契约**：`scene` trim 后必须匹配 `[A-Z][A-Z0-9_]{0,31}`，非法即 `PARAM_INVALID`——拒绝错大小写/特殊字符（防 Key 结构注入；前端错场景 fail-loud，而非静默落入新码池）；
3. **机械守卫**：`Implementation/back-end/scripts/check-id-invariants.sh` 已接入 CI（ADR-050 三类不变量：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令）。

## 后果

- ✅ 老用户误入注册：秒级拒绝、零验证码消耗、零跨服务调用；
- ✅ 场景互通攻击面关闭：注册码/登录码/重置码互不可用；各场景独立频控预算，短信轰炸损害被限制在单一场景；
- ✅ 领域语义显性化：注册三态（拒绝/补齐/新建）有独立测试锚点；
- ⚠️ 上线瞬间各场景存量未消费验证码（TTL 5 分钟）全部失效——开发环境无影响；
- ⚠️ 前端发码必须传对 `scene`（错场景 = 校验必然失败），属契约破坏式变更，前端对接清单已同步；
- ⚠️ 注册接口存在账号枚举泄漏（accepted trade-off，见上）。

## 日期

2026-10-08

## 参考

- [ADR-007](./007-verification-capability-convergence.md)（验证能力单出口，本 ADR 不改变该边界）
- [ADR-016](./016-user-service-ddd-and-sms-migration.md) / [ADR-017](./017-user-domain-split-and-auth-boundary.md)（注册链路凭证所有权，三态语义来源）
- `09-constraints.md` §三·五（业务标识与验证码场景约束）
- `05-API-Specification.md` §4.14.13（I33/I34 契约）
