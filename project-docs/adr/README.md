# Architecture Decision Records (ADR)

每条 ADR 记录一个关键的技术决策，遵循格式：**背景 → 决策 → 备选方案 → 后果 → 日期 → 参考**。

| 编号                                                 | 决策                      | 日期         |
| -------------------------------------------------- | ----------------------- | ---------- |
| [001](./001-alibaba-lightweight-ddd.md)            | 采用 Alibaba 轻量化 DDD 分层架构 | 2026-03-01 |
| [002](./002-dual-primary-key-and-money.md)         | 双主键策略 + 金额用分 + 订单快照模式   | 2026-03-15 |
| [003](./003-object-isolation-do-dto-vo.md)         | DO/DTO/VO 三层对象隔离        | 2026-03-15 |
| [004](./004-internal-api-exception-passthrough.md) | 内部接口异常透传 + 外部接口统一包装     | 2026-03-20 |
| [005](./005-domain-purity-ports-and-eventual-consistency.md) | 领域层零框架依赖与端口化 + 无事务最终一致性方案 | 2026-09-29 |
| [006](./006-cross-service-userid-semantics.md)     | 跨服务 userId 语义（业务 UUID vs 技术主键） | 2026-09-29 |
| [007](./007-verification-capability-convergence.md) | 校验能力收口 verification-service + Feign 熔断降级 fail-closed | 2026-09-29 |
| [008](./008-email-code-login-and-credential-routing.md) | 邮箱验证码登录 —— 复用 `/web/login` 并按 credentialType 路由 | 2026-09-29 |
| [009](./009-package-namespace-unification.md)     | 全模块包名归位（服务段拆分 + ia-common 统一 + scan/imports 二选一） | 2026-09-29 |
| [010](./010-document-domain-reorganization.md)     | 文档体系分域重组（七域结构 + 元信息头 + 治理规则） | 2026-09-29 |
| [011](./011-feign-fallback-degradation-semantics.md) | Feign fallback 降级语义三分法（全 Client 覆盖 + 判别型查询 fail-closed） | 2026-09-29 |
| [012](./012-conditional-on-class-guards-for-optional-deps.md) | ia-common 可选依赖的 `@ConditionalOnClass` 守卫策略 | 2026-09-29 |
| [013](./013-payment-conditional-namespace-migration-deferred.md) | 支付渠道条件装配命名空间暂不迁移（记录风险与迁移路径） | 2026-09-29 |
| [014](./014-doc-template-purification.md)         | 模板纯化 —— ADR 与图示归位 project-docs，doc/ 只留通用 | 2026-09-30 |
| [015](./015-package-and-contract-naming.md)        | 应用层包名 `app` + 契约对象 `XxxCO` 命名规范 | 2026-10-01 |
| [016](./016-user-service-ddd-and-sms-migration.md) | user-service DDD 四层重构 + 短信直发链路迁移 verification-service | 2026-10-01 |
| [017](./017-user-domain-split-and-auth-boundary.md) | user 域资料/凭证分离（PII 加密）+ 认证边界收敛（user_auth 唯一所有权）+ RBAC 收尾 | 2026-10-01 |
