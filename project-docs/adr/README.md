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
| [047](./047-daily-task-system.md) | 每日任务体系 —— 表驱动任务定义、每日幂等完成、积分单出口与旁路触发 | 2026-10-05 |
| [048](./048-content-notes-in-item-domain.md) | 内容社区落位 item 域 —— 种草笔记与 review 同属商品 UGC，鉴定体系 Phase 2 立项 | 2026-10-05 |
| [049](./049-group-buy-full-loop.md) | 拼团完整闭环 —— 两段式参团、幂等锚点泛化（修订 ADR-035）、结团退款联动 | 2026-10-05 |
| [050](./050-business-id-binary16-uuidv7.md) | 业务标识存储升级 —— BINARY(16) + UUIDv7 + byte[] 持久化边界（修订 ADR-002 存储形态；27 列转换 / 11 项保留清单；🔧 2026-10-14 三批修订：+transaction_no、+coupon_id/ai 会话列契约收紧、+review 订单引用收敛（技术 id→业务键）） | 2026-10-07 |
| [051](./051-register-fast-reject-and-verification-scene.md) | 注册前置快拒绝 + 验证码场景隔离（REGISTER/LOGIN/RESET_PASSWORD 独立码池与频控预算；否决 ProvisionResult 契约；🔧 修订：全局日配额 + scene 编码契约 + CI 守卫） | 2026-10-08 |
| [052](./052-search-db-fallback-exemption.md) | search-service DB 降级直读 item 域表 —— 受控豁免登记与退出条件（分库分表前硬红线退出） | 2026-10-10 |
| [053](./053-doc-ownership-by-nature-and-two-layer-standards.md) | 文档 Owner 按「文档性质」分权 + 规范双层模型 —— 决策类归决策者、派生类归 doc-engineer、`00-README` 唯一 Owner、方法论层与项目层冲突裁决规则 | 2026-10-14 |
| [054](./054-frontend-multi-platform-architecture-and-doc-axis.md) | 前端多平台化 —— 平台轴与文档结构（编号文件 + `NN-platforms/` 子目录分阶段落地；`17` 保持跨平台单篇；token 单一来源不容破坏、CI 强制） | 2026-10-15 |
| [055](./055-coupon-scope-and-stack-rule-materialization.md) | 优惠券适用范围与叠加规则落地 —— scope/stack 四列映射打通、I46 `/batch` 补齐、范围校验补位（订正 ADR-038「下单侧不受影响」的错误推论） | 2026-10-15 |
| [056](./056-wire-naming-snake-case.md) | 线上字段与查询参数命名统一为 snake_case —— 三类载体（Jackson / `@RequestParam` / POJO 绑定）、例外清单与大厂依据 |
| [057](./057-display-field-read-model-ports.md) | 契约声明的展示字段回填 —— 应用层读模型端口 + 按 ID 批量 + fail-open；事务型 Manager 只能调本地只读 |
| [058](./058-vo-identifier-semantics.md) | VO 标识字段的语义唯一性 —— 技术主键/外键禁出 VO；读模型须带出业务标识；MapStruct 跨层同名异义必须显式 ignore |
| [059](./059-spu-sku-vocabulary-disambiguation.md) | SPU/SKU 词汇消歧 —— `product` 实为 SPU，线格式统一 `spu_id`/`sku_id`；技术键与技术外键仅留 PO/领域层 |
| [060](./060-coupon-consumption-recovery.md) | 券核销「三网」—— 事实标记驱动 Saga 补偿（解耦推导金额）+ 定时兜底对账（`coupon:consumed:pending`）+ 判定方向纪律（仅查不到订单才回滚）；登记标记写入 fail-open 与降级空操作两项残余风险 | 2026-10-16 |
| [061](./061-order-item-business-key-snapshot-and-price-compare.md) | 订单项 = 业务键标识 + 快照 —— `order_item` 存 `spu_id`/`sku_id` 业务键（BINARY(16)）与快照同批冻结；线格式标识一律 32-hex `String`（补 ADR-059 缺失的类型维度）；比价/失效提示走 ADR-057 读模型（按业务键批量 + fail-open + 不缩行）；`product_name` → `spu_name`；DB 只改 `order_item`；登记路径变量/内部契约/券 scope 取值三项残余风险 | 2026-10-16 |
| [062](./062-external-identifier-and-addressing.md) | 对外标识与寻址口径 —— 四位置四形态（存储 `BINARY(16)` / 编解码唯一出口 `toWire`·`parseWire` / 线格式永远 `String` / 由路径与字段名承担类型）；SPU 资源段 `product`→`spu`（27 处改名对照表 + 8 条「非 SPU 实体故保留」）；路径变量去歧义 + 单形态业务键；**删除 I65 双形态**并立纪律（双形态是绕门禁的垫片，门禁的目的是暴露决策）；值前缀 `spu_` 不加但保留能力（须先收敛编解码）；记录三项被否决/被纠正做法；登记 P37 `scene` ≤32 字符等 6 项残余风险 | 2026-10-17 |
| [063](./063-external-channel-number-separation.md) | 对外渠道号与内部业务键分离 —— `pay_order.channel_trade_no` / `refund_order.out_refund_no` 独立成列（20 位随机 hex、80 bit 熵、无时间序、跨重试稳定、`UNIQUE`、余额渠道 NULL）；发起/回调/对账**唯一按渠道号寻址**且**不留按内部键兜底的第二口径**；对外单号禁 UUIDv7（可反推下单时刻 + 32 位对渠道上限零余量），凭据 `secureToken` 与对外单号 `randomHex` 为两个显式入口；退款 `out_trade_no` = 原交易的对外渠道号；对账「渠道有本地无」不伪造本地单号；**留档命名冲突**（任务原要求新增 `channel_refund_no`，该列已存在且语义是渠道 `refund_id` → 复用会覆盖对外退款项号使重试/查询失效，故改用协议原名）；一并裁决**承载层解耦规则**（`scene` 短码 / objectKey 内容哈希或短随机 / 短链分享码；加值前缀须与承载解耦同批，现有三处余量已归零或为负） | 2026-10-18 |
| [064](./064-spu-name-wire-key-unification.md) | SPU 名称字段线上键统一 `spu_name` —— 「同一实体只许一个名字」：读出侧 7 个报文载体（`SpuVO`/`RecommendSpuVO`/`NoteDetailVO.SpuSummaryVO`/`SpuSearchVO`/`SpuDetailCO`/`SpuSearchItemCO`/`SpuVectorUpsertCO`）与**写侧**（O2：`CreateSpuReq`/`UpdateSpuReq`）同批收口、I47 入参 `SpuVectorUpsertReq` 随 CO 同改；`name` 在域/PO/ES 索引/ai 领域 record 与「别的实体自己的名字」14 类中**一字不动**（MapStruct 映射显式化，不靠同名巧合）；**新增 I70** `GET /internal/item/spu/{spuId}`（裸 CO 无信封、复用 P2 装配、`requireWire` 400·1101 / 404·1103）并把 `SpuDetailClient` 迁内网（ADR-062 §E7 根治）；**新增第 7 道机械闸**补「字段级一致性」真空（载体清单点名校验 + 旧键禁令 + 白名单陈旧必败 + `product_name` 不得扩散），并补三条往返/线格式护栏（两端同 PR）；登记 5 项残余风险（含小程序仍读 `name` 已知未修、404 经 Feign 降级成 `SYSTEM_BUSY` 的既有语义） | 2026-10-18 |
| [065](./065-login-failure-disclosure-and-anti-enumeration.md) | 登录失败口径收敛与抗账号枚举 —— 四类失败（账号不存在 / 密码错 / 状态不可登录 / 风控冻结）统一 `401 + 3007 + 中性文案`；`3007` 状态 `200→401` 且成唯一出口、`3008` 改 401 并禁止用于登录、`1017` 改 401 且退役；披露策略下沉唯一决策点（删 `UserAuth#assertActive`，改 `isActive` 查询）；原因细分只进日志（`LoginFailureReason`）；**dummy verify**（账号不存在/无密码凭证时对预置 BCrypt 哈希做一次假比对）消除可观测时间差 —— 明确写明它只关时序、不提供安全强度；前端同口径（`isLoginFailure`/`getLoginFailureMessage` + 认证端点 401 不触发刷新重放） | 2026-10-18 |
| [066](./066-http-status-and-business-code-layering.md) | HTTP 状态码与业务码的分层规范 —— 判据「这次请求在协议层有没有被处理完」+ 5 问决策树（**补齐「凭证无效属请求无法被处理」**，据此 `3001`/`3002` 决策为 400、待 F 批次后落地）；三看分工（状态码→网关/缓存/熔断/SLO、`code`→前端文案、`category`→跨服务透传或降级）与混用代价；`HttpStatus → ErrorCategory` **逐行源码取证**（`408/429/502/503/504`+`null`⇒TRANSIENT、`≥500`⇒BUG、`≥400`⇒CLIENT、`2xx`⇒RULE；覆盖表 3 条 `4007/4008/4999`、上限 5、启动期自检；判定顺序 异常类型→状态码→500 才是 BUG；`retryable = category==TRANSIENT` 单向派生）+ `category` **不按码段划分**（4xxx 段内 CLIENT 19 / TRANSIENT 22）；9 个状态码的唯一含义与禁止用法（**`409`/`422` 本项目刻意不用**：业务规则拒绝一律 200，且 409 会被派生为 CLIENT 而给出错误的重试建议）；**23 条 200 业务码按类别归并的理由**（兜底总码 / 资源占用冲突 / 状态机冲突 / 重复操作 / 业务前置条件不足 / 凭证无效待改 400，含 6 条死码），并证明白名单里**没有**资源不存在/未认证/无权；前端三种情形伪代码（`httpStatus` 定动作、`code` 定文案，`ApiError.code = body.code \|\| res.status` 的双源缺口与 `deriveCategory` 镜像口径）；迁移三纪律 + **19 行状态变更记录**（P2·3011 / 批次 ①12 个 not-found / 批次 ④3007·3008·1017 / 批次 R2 码名对齐 / 本 ADR 3001·3002）+ 三条禁令及其已登记例外（网关 3 处、渠道回调 2 处） | 2026-10-18 |
