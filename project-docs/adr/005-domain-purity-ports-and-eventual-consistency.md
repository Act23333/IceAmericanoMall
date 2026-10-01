# ADR-005: 领域层零框架依赖与端口化 + 无事务一致性方案

## 背景

authorization-service V5.1 重构前，领域层直接依赖技术框架：领域实体 `UserAuth` import Spring Security `PasswordEncoder`、登录策略注入 Feign Client（`AuthClient`）与 Redisson、领域策略方法上挂 `@Transactional`，且 `resetPassword` 用事务包住纯 Feign 调用（反模式）。领域层无法脱离容器单测，业务规则与技术实现耦合。

## 决策

1. **domain 层零框架依赖**：禁止 `org.springframework.*`、`com.baomidou.*`、`org.redisson.*`、Feign、ia-integration 任何 import（grep 门禁可验证）。领域对象为纯 POJO，由 `infrastructure/config/DomainBeanConfig` 显式装配。
2. **依赖倒置端口化**：领域需要的技术能力收敛为领域自有端口，由基础设施层适配：

| 端口 | 领域语义 | 基础设施实现 |
|---|---|---|
| `PasswordVerifier` | 密码哈希比对 | BCrypt |
| `DistributedLock` | 同一业务键串行 | Redisson |
| `SmsCodeService` / `WechatMiniSessionService` | 验证码/小程序会话 | Redis / ia-integration |
| `UserAccountGateway` / `WechatWebLoginGateway` | 跨上下文调用（防腐层） | ia-api Feign |

3. **跨上下文调用统一走 gateway 防腐层**：Feign DTO 不进领域层，gateway 参数/返回值全为领域对象。
4. **全模块不使用 `@Transactional`**：现有写操作均为单语句（天然原子）；跨服务一致性靠远端幂等端点（`/internal/auth/*`、`/getOrCreate`）+ `user_auth` 业务唯一索引（`uk_type_hash` / `uk_type_plain`）+ 冲突回查重放（持久层 `DuplicateKeyException` 翻译为领域异常 `DuplicateIdentityException`）。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 保持领域直依赖框架（原状） | 代码量少 | 领域不可单测、规则与实现耦合、违反 COLA 依赖方向 |
| `@Transactional` + 拆 Feign 出事务（两段式） | 保留声明式事务 | 引入"半成品状态"而无补偿机制，比单语句原子性更弱 |
| 本地消息表 / MQ 事务消息 | 强最终一致性 | 项目无 MQ（RabbitMQ 属 V1.1 规划），MVP 阶段过度设计 |
| **端口化 + 无事务 + 幂等回查（采用）** | 领域纯净可单测、依赖方向正确 | 需维护端口与适配器（数量有限，成本可控） |

## 后果

- ✅ 领域策略/领域服务可纯 JUnit 单测（已验证 29 用例全绿）
- ✅ 密码算法、锁实现、远程调用均可替换而不动领域
- ✅ 消灭"事务内调 Feign"反模式
- ⚠️ 多一步 DTO 转换（防腐层职责，集中且可测）
- ⚠️ 一致性依赖幂等端点纪律：新增跨服务写端点必须幂等

## 日期

2026-09-29

## 参考

- `project-docs/backend/02-Architecture.md` §5.2（V5.1 登录编排链路）
- `project-docs/backend/03-Domain-Model.md` §3.14（UserAuth 聚合）
- `project-docs/collaboration/09-constraints.md`（事务内禁调 Feign）
