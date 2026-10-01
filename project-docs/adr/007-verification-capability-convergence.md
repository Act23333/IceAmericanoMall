# ADR-007: 校验能力收口 verification-service + Feign 熔断降级 fail-closed

## 背景

V5.1 认证服务 DDD 重构后暴露三处结构性问题：

1. **验证码校验的隐式跨服务契约（R5）**：authorization-service 直读 Redis `sms:code:{phone}` 校验短信验证码，Key 布局/TTL 与 user-service 写入端必须逐字符一致，任何一端调整都会静默失效（用户侧只看到"验证码已过期"）。契约没有代码边界，也没有编译期或测试期保护。
2. **人机验证第三方适配散落**：authorization-service 自行持有极验密钥、做 HMAC-SHA256 签名并直连极验校验地址，与 ia-integration 的第三方适配职责重叠；密钥随服务配置扩散。
3. **Feign 降级形同虚设（R6）**：各 `@FeignClient` 声明了 `fallbackFactory`，但 `spring.cloud.openfeign.circuitbreaker.enabled` 未开启、resilience4j 未引入。Feign 在未开启熔断时走 `DefaultTargeter`，**完全忽略** fallback/fallbackFactory —— 即"降级代码从未执行过"。

## 决策

1. **校验能力唯一出口 = verification-service**：验证码签发/校验（短信、邮件）与人机验证二次校验统一由其承担；authorization-service 通过 ia-api 的 `VerificationClient` 调用，不再直读验证码 Redis Key、不再持有第三方密钥、不再做签名。
2. **删除 authorization-service 内的重复实现**：`SmsCodeServiceImpl`（直读 Redis）、`RedisSmsConstants`、`CaptchaServiceImpl` + `CaptchaAdapterFactory` + `GeetestCaptchaAdapter`（直连极验）全部下线；领域端口 `SmsCodeService` 泛化为 `VerificationCodeService(channel, target, code)`（新增 `VerificationChannelEnum`）。
3. **业务失败用结构化结果码，不用异常**：`/internal/**` 的业务异常会被 `GlobalExceptionHandler` 直接 rethrow，Feign 侧只能看到 HTTP 500、无法区分"验证码错误"与"验证码过期"，因此校验类端点返回 `success + reason`（`VerificationResultCode`），调用方按 `VerificationResultMapper` 映射为本上下文 `ErrorCode`（不新增枚举值）。
4. **降级 fail-closed**：`VerificationClientFallback` 对 `verifyCode`/`verifyCaptcha` 一律返回 `success=false`（服务不可用 → `SYSTEM_BUSY`），**绝不默认放行**；`issueCode` 失败返回 `success=false` 而非伪装成功。
5. **仅 authorization-service 开启熔断**（`spring-cloud-starter-circuitbreaker-resilience4j` + `spring.cloud.openfeign.circuitbreaker.enabled=true`），并补齐 fallbackFactory 的 Bean 可达性（启动类组件扫描包含 `org.icedamericanomall.fallback`）。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 保持直读 Redis（R5 现状） | 无远程调用，登录延迟最低 | 隐式契约裸奔；Key/TTL 变更即线上故障；校验逻辑多副本 |
| 让 user-service 承担校验（而非新建 verification-service） | 少一个服务 | 校验能力被绑死在用户上下文，邮件/人机验证仍无处安放 |
| **收口 verification-service + 结构化结果码（采用）** | 契约显式、错误可区分、第三方适配集中在 ia-integration | 登录链路多一次内部调用；结果码映射需要维护 |
| 降级 fail-open（服务不可用时放行） | 可用性更好 | 攻击者打挂校验服务即可绕过验证码/人机验证 —— 验收不可接受 |
| **降级 fail-closed（采用）** | 安全优先，故障窗口不开门 | 校验服务故障期间登录不可用（需靠熔断 + 快速恢复兜底） |

## 后果

- ✅ 验证码 Key/TTL 契约只在 user-service 与 verification-service 两处维护，且由 verification-service 的契约测试锁定
- ✅ 极验密钥只在 verification-service 配置，认证服务配置面收敛
- ✅ 降级行为可测：单元测试可注入 ia-api 的 `VerificationClientFallback` 直接驱动"服务不可用"分支
- ⚠️ 登录链路新增一次内部 Feign 调用（短信/邮箱验证码登录）——已由熔断 + 超时控制风险，且验证码登录非高频路径
- ⚠️ 校验服务不可用期间，验证码/人机验证登录不可用（fail-closed 的必然代价，属预期）
- ⚠️ ia-integration 的 `AutoConfiguration.imports` 存在历史残留项（大小写与真实包名不一致、指向已删除包），在 jar/Docker 形态下会启动失败，需单独清理

## 日期

2026-09-29

## 参考

- `005-domain-purity-ports-and-eventual-consistency.md`（领域端口化原则）
- `004-internal-api-exception-passthrough.md`（内部接口异常透传 → 本 ADR 补"结构化结果码"例外）
- `project-docs/contract/05-API-Specification.md` §4.1（认证服务端点）
