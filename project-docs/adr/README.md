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
| [018](./018-unified-identity-rbac-ownership.md) | 统一身份 + RBAC 归属授权域 + 用户档案域边界收敛 | 2026-10-02 |
| [019](./019-merchant-shop-1n-split.md) | 商家 / 店铺 1:N 拆分（seller 瘦身 + shop 承载店铺档案） | 2026-10-03 |
| [020](./020-sku-stock-atomic-sql-deduction.md) | SKU 库存扣减/恢复改用单条原子 SQL（放弃 version 读-比-写） | 2026-10-03 |
| [021](./021-cart-readtime-enrichment-in-app-layer.md) | 购物车商品信息与金额在读时由应用层编排回填 | 2026-10-03 |
| [022](./022-feign-circuitbreaker-enablement-marketing-ai.md) | marketing-service / ai-service 开启 Feign 熔断（resilience4j）与参数口径 | 2026-10-03 |
| [023](./023-adapter-layer-structure-and-request-object-naming.md) | 四层服务 adapter 层结构统一（按受众分子包 + 入参对象化 `XxxReq`） | 2026-10-03 |
| [024](./024-distributed-lock-consolidation-in-ia-common.md) | 业务键互斥锁能力归并到 ia-common 共享内核（条件装配） | 2026-10-03 |
| [025](./025-domain-aggregate-packaging-and-app-usecase-granularity.md) | 领域模型按聚合分包 + 应用层 Manager 按用例族收敛粒度 | 2026-10-03 |
| [026](./026-ws-handshake-query-token-edge-swap.md) | WebSocket 握手鉴权 —— 网关边缘 query token 换头 | 2026-10-03 |
| [027](./027-jwt-blacklist-writer-and-contract-key.md) | access token 吊销 —— jti 黑名单写侧语义与契约键豁免 | 2026-10-03 |
| [028](./028-trade-sentinel-wiring-over-resilience4j.md) | trade-service 走 Sentinel 接线修复而非迁移 resilience4j | 2026-10-03 |
| [029](./029-channel-callback-protocol-over-result-envelope.md) | 渠道回调协议优先于平台 Result 信封（微信 v3 裸 JSON + 500 触发重推） | 2026-10-03 |
| [030](./030-metaobjecthandler-consolidation-autoconfig.md) | MyMetaObjectHandler 收口 ia-common 自动装配唯一实现（user 自有变体退避共存） | 2026-10-03 |
| [031](./031-multi-granularity-token-revocation.md) | 多粒度 token 吊销 —— 用户级令牌版本 `auth:tokenver:{userId}` + 全端会话拆除（网关合并为一次 MGET；收敛 ADR-027 残余风险） | 2026-10-03 |
| [032](./032-operation-audit-dual-pipeline.md) | 操作审计双管道 —— JSON 审计日志（全服务）+ `operation_log` 落库（仅 user 域） | 2026-10-03 |
| [033](./033-wechat-callback-decrypt-in-adapter.md) | 微信 v3 回调验签 + AES-GCM 解密 + 字段提取归通道适配层（修复 out_trade_no 断链） | 2026-10-03 |
| [034](./034-cross-module-utility-consolidation-and-boundaries.md) | 账号掩码归并 ia-common + 网关对端 IP 解析器更名 `EdgePeerIpResolver` + 「必要差异」登记 | 2026-10-03 |
| [035](./035-refund-fund-flow-and-refunded-activation.md) | 买家退款资金流 —— 三通道原路退回、`refund_order` 幂等锚点与 `REFUNDED` 状态机激活（F1） | 2026-10-03 |
| [036](./036-channel-reconciliation-record-only-and-rewrite-idempotency.md) | 渠道对账 —— 四态差异只记录不调账、先删后写幂等与账单适配口径（F4） | 2026-10-03 |
| [037](./037-freight-model-package-split-and-refund-trigger.md) | 履约线 —— 金额不变式 V2（`payAmount` 计入 `postFee`）+ 运费模板计价口径 + 拆单发货包语义 + 退款触发时机纠正（修订 ADR-035） | 2026-10-03 |
| [038](./038-full-ddd-migration.md) | 全面四层 DDD 收口（废止轻量双轨；14 业务服务全部落地） | 2026-10-03 |
| [039](./039-ai-service-ddd-completion.md) | ai-service 四层收口完成（游离包归位 + 仓储归位，49→52 测试） | 2026-10-04 |
| [040](./040-ia-common-shared-kernel-package-rename.md) | ia-common 共享内核包更名（domain 拆分归位 api / security） | 2026-10-04 |
| [041](./041-refund-resume-aftersale-guard-freight-cache.md) | P0 资金线风险收敛 —— 退款悬挂补偿、售后审核守卫收紧、I43 计价缓存 | 2026-10-04 |
| [042](./042-buyer-notification-wiring.md) | 买家消息触达接线 —— 四事件站内信、事务外 fail-open、单出口 notification-service | 2026-10-04 |
| [043](./043-pricing-center-and-promotion-engine.md) | 价格计算中心与促销引擎 —— 满减/满折统一计价、叠加口径与分摊边界 | 2026-10-05 |
| [044](./044-member-level-and-benefits.md) | 会员等级升降级与权益 —— 成长值只升不降、会员折扣接入定价中心、积分倍率接线 | 2026-10-05 |
| [045](./045-db-snapshot-single-source.md) | 数据库结构演进口径变更 —— 废除迁移文件机制，Initialize.sql 为唯一结构真相源 | 2026-10-05 |
| [046](./046-product-vector-recommendation.md) | 商品向量推荐 —— 复用向量管道的单向量相似检索与 item 侧新鲜组装 | 2026-10-05 |
