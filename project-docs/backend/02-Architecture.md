# 02 — 系统架构文档

> 来源：`doc/项目难题/冰美商的最终架构.md`、`doc/项目难题/架构分层、工程结构、微服务规范、分布式设计选择.md`、`doc/IDEA.md`、`doc/项目流程/登录流程详解.md`、`doc/项目的分析流程/IcedAmericanoMall的MVP.md`、`doc/项目难题/大厂的微服务项目结构.md`
> 最后更新: 2026-10-05（**V5.9→V5.13 五批交付（P2 增长线 + 数据库口径变更，[ADR-045](../adr/045-db-snapshot-single-source.md)/[ADR-046](../adr/046-product-vector-recommendation.md)/[ADR-047](../adr/047-daily-task-system.md)/[ADR-048](../adr/048-content-notes-in-item-domain.md)）**：商品向量推荐（item P27 相似商品卡 + search I47 向量摄取/I48 相似 kNN，`products` 索引 knn mapping + reindex 重建带向量）；搜索 facets 聚合与 brand/minPrice/maxPrice/sort 过滤双链补齐（SE6/SE1）+ 搜索联想（SE5 热搜/商品名前缀双源）；表驱动每日任务体系（`task_def`/`task_record`：M14 任务中心 + I49 幂等完成 + AD51-53 管理端 CRUD）+ 店铺促销引擎（AD48-50 管理端/I44 计价，ADR-043 能力补记）；活动中心（M12/M13 `/api/activity` + M7 券三态计数）与商品促销标签（P2 详情/P1 分页，I44 fail-open + 60s 缓存）；店铺装修（S1c/S1d，`shop_decoration` 1:1）+ 内容社区种草笔记（P28-P34，`note`/`note_spu`/`note_like`，item `content` 聚合分包）；会员等级引擎（I45/I46，ADR-044）与 pay 退款悬挂补偿（`RefundResumeJob`，ADR-041）能力补记；网关补挂 `/api/activity/**`、`/api/task/**`、`/api/notes/**`（现 18 路由块 / 38 条 Path 谓词）+ GET 匿名口径；数据库口径切换（ADR-045）：迁移机制废除，`Initialize.sql` 全量唯一结构真相源（54 表）。此前——**P0 四项落地批次（[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)/[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)/[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：退款资金流三通道原路退回 + `REFUNDED(5)` 激活（pay `RefundManager`，refund_order 幂等）、渠道对账四态只记录不调账、金额不变式 V2（`payAmount` 计入 `postFee`）运费模型（item 运费模板 + trade `PostageCalculator`）、拆单发货 1:N 包裹（order_logistics 代理主键 + package_no）与退款触发纠正（confirm-return）；网关补挂 `/api/admin/pay/**` 前置路由；数据库 45 表/30 迁移（V1.1.0→V5.6）。此前——多粒度令牌吊销批次（T19，[ADR-031](../adr/031-multi-granularity-token-revocation.md)，修订 ADR-027）：tokenVer 用户级版本吊销（改密/禁用全端下线）+ 网关黑名单/版本一次 MGET 合并 + 管理端禁用闭环，ADR-027 残余风险关闭；结构规范化 + 数据库整治 + 商家/店铺 1:N 拆分同步：adapter 层按受众统一（ADR-023）、Lua/AOP 死代码清理、gateway 限流/黑名单/白名单收紧、Feign 熔断扩至 8 服务（ADR-022）、SKU 原子扣减（ADR-020）、购物车读时回填（ADR-021）、shop 表拆分（ADR-019）、分布式锁归并 ia-common（ADR-024）、domain/model 聚合分包 + app 层用例族收口（ADR-025）；遗留风险清零批次：WS 握手 query token 换头（ADR-026）、jti 黑名单写侧（ADR-027）、trade Sentinel 接线修复（ADR-028）、渠道回调协议（ADR-029）、RAG 向量链路接线（T14）、操作审计双管道（ADR-032）；工具类收口批次（ADR-034 + ADR-030）：§3.3 补 ia-common 审计字段填充器 `AuditFieldMetaObjectHandler`（自动装配+退避）、`SecurityHeaders` 头名常量、`DesensitizeUtil` 掩码族，`IpUtil` 与网关 `EdgePeerIpResolver` 口径分工注记）
> **2026-10-16（SPU 词汇统一同步，ADR-059 落地批次，后端 `578fac27`）**：§三 服务能力表类名与表名同步——item-service 的运费绑定列 `product.freight_template_id` → `spu.freight_template_id`、内容聚合表 `note_product` → `note_spu`；search-service 的 ES 文档对象 `ProductDocument` → `SpuDocument`、出参 `ProductSearchVO` → `SpuSearchVO`（**ES 索引名 `products` 刻意不变**；**HTTP 路径原记「刻意不变」已失效** —— B4 契约 / B5 代码已把 `product` 段改为 `spu`，现为 `/api/item/spu/**`，见 [ADR-062](../adr/062-external-identifier-and-addressing.md) §决策 2）；ai-service `domain/port` 五接口 `ProductDetail` → `SpuDetail`、聚合分包 `product` → `spu`。服务数 15 / 端点数 287 / 路由块数按本文件既有口径不变（表结构改名不改端点面）。表结构权威定义见 [04-Data-Model](./04-Data-Model.md)，线格式见 [05 §4](../contract/05-API-Specification.md)。
> 归属域: backend | Owner: doc-engineer | 更新触发条件: 新增/下线服务、技术栈变更、ADR 落地时

---

## 一、架构风格

**分布式微服务架构**，采用 **阿里轻量化 DDD 分层体系**作为底层结构规范，融合 **字节跳动代码风格**（小类、小方法、单一职责）作为编码习惯。

参考标准：

- 架构分层、工程结构、微服务规范、分布式设计 → **学阿里**
- 代码整洁、类设计、方法拆分 → **学字节**
- 开发效率、不过度设计 → **借鉴腾讯**

---

## 二、技术栈

> 每个技术的「使用场景」说明了哪个功能/服务需要它，确保技术选型有明确的业务锚点。

### 2.1 核心框架与基础设施

| 层级      | 技术                   | 版本         | 使用场景                           |
| ------- | -------------------- | ---------- | ------------------------------ |
| **语言**  | Java                 | 21 LTS     | 所有后端服务                         |
| **框架**  | Spring Boot          | 3.5.4      | 微服务基础框架                        |
| **云原生** | Spring Cloud         | 2025.0.1   | 服务治理（注册/配置/网关/负载均衡）            |
|         | Spring Cloud Alibaba | 2025.0.0.0 | Nacos 注册中心 + 配置中心              |
| **网关**  | Spring Cloud Gateway | —          | API 网关：统一入口、路由转发、Token 校验、CORS |
|         | Nginx                | 1.25+      | 反向代理、静态资源、TLS 终结               |

### 2.2 认证与安全

| 层级       | 技术                                            | 版本     | 使用场景                                                                      |
| -------- | --------------------------------------------- | ------ | ------------------------------------------------------------------------- |
| **认证框架** | Spring Security + OAuth2 Authorization Server | 1.3.0  | 认证授权：OAuth2 Resource Server (JWT 验签) + JWKS 端点 + Strategy 模式登录（密码/验证码/邮箱） |
| **令牌**   | JWT (JJWT)                                    | 0.13.0 | HS256（内部服务）+ RS256（外部客户端）双模式                                              |
| **密码加密** | BCrypt                                        | —      | 用户密码哈希（`PasswordEncoder`）                                                 |
| **数据加密** | AES-256-GCM（`AesGcmUtil`）                   | —      | 敏感标识静态加密（认证上下文 cipher+hash+mask 三字段模型已落地）；user/address 等 PII 字段迁移（⚠️ 计划中，实现必须用 GCM 而非 CBC，详见 12-Security-Model §六） |
| **密钥管理** | Vault / K8s Secret                            | —      | 生产环境密钥存储（计划中，详见 §五安全架构 + 12-Security-Model §五）                            |
| **人机验证** | 极验 Geetest（provider 可扩展）                                    | —      | 注册/登录防机器人：唯一出口 verification-service；Feign 契约厂商中立（`provider` + `params`），厂商解包只在该服务内                                                           |
| **限流熔断** | resilience4j (Feign circuitbreaker) | 随 Spring Cloud 2025.0.1 BOM | Feign 熔断降级（TimeLimiter 5s + 失败率阈值 50%）：**authorization / user / merchant / verification / marketing / ai / pay / logistics 8 服务已启用** `circuitbreaker.enabled=true`（[ADR-011](../adr/011-feign-fallback-degradation-semantics.md)、[ADR-022](../adr/022-feign-circuitbreaker-enablement-marketing-ai.md)）；降级语义由 ia-api fallbackFactory 三分法决定 |
|          | Sentinel                                      | —      | Gateway `spring.cloud.sentinel.enabled=false`（未落地）；**trade-service 规则已注册生效**（[ADR-028](../adr/028-trade-sentinel-wiring-over-resilience4j.md)）：此前 `SentinelConfig` 激活键写错（`sentinel.enabled` ≠ 真正生效的 `spring.cloud.sentinel.enabled`）导致从未装配、规则从未注册（V5.4 修复）；现为 `createOrder` flow QPS 100 + 异常比例 0.5 degrade（10s 窗口），blockHandler 抛 `FREQUENT_ERROR`(429)；规则进程内静态注册（Dashboard 默认关），`feign.sentinel.enabled` 与 ia-api fallback 组合不变 |
|          | `@RateLimit` (Redis Lua)                      | —      | 接口级限流：短信/登录/注册（已在 ia-common 实现）；限流键含方法维度 `rate:limit:{类.方法}:{业务键}`，超限返回 `FREQUENT_ERROR`(429)（V5.3 修正） |

### 2.3 数据存储

| 层级            | 技术                    | 版本      | 使用场景                                         |
| ------------- | --------------------- | ------- | -------------------------------------------- |
| **关系型数据库**    | MySQL (InnoDB)        | 9.3.0   | 所有业务数据：用户/商品/订单/购物车/支付                       |
| **ORM**       | MyBatis-Plus          | 3.5.11  | 数据访问：LambdaQueryWrapper 参数化查询，`@Version` 乐观锁 |
| **缓存**        | Redis                 | 7.x     | 缓存（Token/验证码/用户会话）、分布式锁（Redisson）、限流计数器（Lua） |
| **Redis 客户端** | Redisson              | 3.26.0  | 分布式锁能力已归并 ia-common 共享内核（`common/lock/DistributedLock` + `RedissonDistributedLockAdapter`，条件装配；[ADR-024](../adr/024-distributed-lock-consolidation-in-ia-common.md)）——调用方 authorization-service（登录/建档并发控制）与 user-service（幂等建档），服务内重复副本已删 |
| **搜索引擎**      | ElasticSearch         | 7.17.25 | 商品全文检索（V2.5 已启用 search-service，ES+MySQL双实现）  |
| **对象存储**      | MinIO / 阿里云 OSS       | —       | 商品图片、用户头像、商家 Logo（计划 V1.1）                   |
| **分库分表**      | Apache ShardingSphere | —       | 分库分表中间件（计划 V2.0，触发条件：单表 > 500 万行）            |

### 2.4 消息与异步

| 层级       | 技术                 | 版本    | 使用场景                                                  |
| -------- | ------------------ | ----- | ----------------------------------------------------- |
| **消息队列** | RocketMQ           | 5.3   | ✅ V4.0+：秒杀异步削峰(Redis→RocketMQ→Consumer→DB) + 订单超时延迟消息(level=16/30min)。Topics 定义于 `ia-common` 的 `RocketMqTopics`：`order-timeout-topic:timeout-cancel`（30分钟延迟取消）和 `flash-order-topic:create`（秒杀异步统计+补偿对账） |
| **领域事件** | RocketMQ (domain-event-topic) | 5.3 | 领域事件解耦（事件契约定义于 `ia-common`）：订单创建(`order.created`，`OrderCreatedEvent`，trade→pay 已落地)、订单发货(`order.shipped`，`OrderShippedEvent`)；支付成功(`payment.succeeded`)**尚未实现**（`RocketMqTopics` 常量已预留，`PaymentSucceededEvent` 事件类已删除）。原 RabbitMQ 领域事件通道（`RabbitMqConfig`）已随重构移除 |
| **CDC**  | Apache Flink CDC   | 3.2+  | MySQL binlog → Kafka → ES/Redis/PG 实时同步（V4.0 学习，替代 Canal） |
| **实时推送** | WebSocket (Spring) | —     | 订单状态变更实时通知、商家新订单提醒（V3.3 已实现）                           |
| **事件流**   | Apache Kafka       | 3.9+  | 用户行为埋点流(浏览/点击/加购)、订单事件溯源、实时数据管道（V4.0 学习）  |
| **流计算**   | Apache Flink       | 1.20+ | 实时大屏(秒级GMV/订单数)、用户行为实时聚合、异常检测（V4.0 学习）    |
| **CDC**     | Apache Flink CDC   | 3.2+  | MySQL binlog → Kafka → ES/Redis/PG 实时同步（V4.0 替代 Canal） |

### 2.5 分布式协调

| 层级        | 技术                | 版本   | 使用场景                                                                    |
| --------- | ----------------- | ---- | ----------------------------------------------------------------------- |
| **分布式事务** | Apache Seata      | 2.x  | AT 模式（强一致）用于支付回调；TCC 模式用于库存扣减；Saga 模式用于下单全链路（计划 V1.1，当前 MVP 手动 Saga 补偿） |
| **分布式调度** | XXL-Job           | 2.4+ | 订单超时取消、支付超时关闭、每日签到重置、定时报表（计划 V1.1；当前 `@Scheduled` 单机，多实例会重复执行）          |
| **分布式追踪** | Apache SkyWalking | 9.x  | 跨 11 个微服务的调用链追踪、性能瓶颈定位、服务拓扑图（计划 V1.1，Agent 无侵入）                         |

### 2.6 AI 平台

> 完整选型分析见 [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)

| 层级            | 技术                          | 版本   | 使用场景                                       |
| ------------- | --------------------------- | ---- | ------------------------------------------ |
| **LLM**       | DeepSeek V3 / 通义千问 Qwen-Max | —    | ✅ AI 商品助手（ReAct多步推理）、AI 客服（FAQ+订单查询）（V1.2/V2.0已实现）；计划 V3.0 混合路由（DeepSeek主力80% / Qwen备选20%） |
| **AI 框架**     | LangChain4j (Agent编排) / Spring AI (ChatClient+Embedding基建) | LangChain4j 1.0+ / Spring AI 1.0+ | LangChain4j 负责 AiServices Agent + @Tool + ChatMemory；Spring AI 负责自动配置 + VectorStore + MCP Server（V3.0） |
| **向量数据库**     | ES 8.x dense_vector (V2.5 MVP) → Milvus 2.4+ (V3.0 生产) | —    | MVP 阶段复用已有 ES 做 kNN+BM25 混合检索；V3.0 独立 Milvus 集群（GPU加速，十亿级向量） |
| **Embedding** | DeepSeek Embedding API (V2.5) → bge-large-zh 自建 (V3.x) | —    | MVP 用 API 按量付费；日调用超 10 万次后 GPU 自建降低成本 |
| **Reranker**  | bge-reranker-v2-base        | —    | 召回结果重排序，提升 MRR 从 ~0.65 到 ~0.85（V2.5） |
| **AI Gateway** | Spring Cloud Gateway filter (V2.5) → APISIX AI Plugin (V3.0) | —    | 多Provider路由、Token配额限流、语义缓存、故障转移 |
| **会话管理**    | Redis ChatMemoryStore       | —    | Per-User ChatMemory 隔离 + TTL 自动过期（V2.5） |
| **流式协议**    | SSE (Server-Sent Events)     | —    | LangChain4j TokenStream + WebFlux Flux<ServerSentEvent>（V2.5） |
| **可观测性**    | Langfuse (OSS) + OpenTelemetry | —    | AI 专项 Trace：Token消耗、Tool调用链、用户反馈、质量评估（V2.5） |
| **内容安全**    | 输入过滤 + System Prompt 加固 + 输出审核 | —    | Prompt Injection 防护、敏感词过滤、PII 脱敏（V2.5） |
| **HTTP 客户端** | RestClient (Spring Boot 3.2+) | —    | ia-common 全局 Bean，替代 RestTemplate（V3.2） |
| **DTO 模式**    | Java Record (JDK 21) | —    | 不可变数据传输对象，替代 Lombok @Data（V3.2） |
| **知识图谱**    | NebulaGraph (V3.x 前瞻)      | —    | RAG+KG 混合检索；触发条件：SKU>10万 且 关系类查询>20%流量 |

### 2.7 开发与测试

| 层级         | 技术                                | 版本      | 使用场景                                                      |
| ---------- | --------------------------------- | ------- | --------------------------------------------------------- |
| **对象映射**   | MapStruct                         | 1.5.5   | Entity ↔ DTO ↔ VO 编译期转换（零反射，性能最优）                         |
| **代码简化**   | Lombok                            | 1.18.42 | `@Data`/`@Slf4j`/`@RequiredArgsConstructor`               |
| **工具库**    | Hutool                            | 5.8.43  | 通用工具：日期/字符串/加密/HTTP                                       |
| **API 文档** | Knife4j (OpenAPI 3)               | 4.5.0   | Swagger 增强：接口调试 + 文档导出                                    |
| **单元测试**   | JUnit 5 + Mockito                 | —       | Service/Domain 层单元测试（详见 11-Test-Strategy）                 |
| **集成测试**   | Spring Boot Test + Testcontainers | —       | Controller → DB 全链路集成测试，MySQL/Redis 容器化                   |
| **契约测试**   | Pact                              | —       | Feign 接口 Consumer-Driven Contract（详见 11-Test-Strategy §3） |

### 2.8 可观测性

| 层级       | 技术                                      | 版本  | 使用场景                             |
| -------- | --------------------------------------- | --- | -------------------------------- |
| **指标监控** | Prometheus + Grafana                    | —   | JVM 指标、接口 QPS/RT、业务指标大盘（V2.4 已接入：Actuator + Micrometer 暴露 `/actuator/prometheus`，compose 含 Prometheus/Grafana） |
| **日志采集** | ELK (ElasticSearch + Logstash + Kibana) | —   | 集中日志查询、错误告警（V2.4 配置就绪：logback 带 traceId + Logstash grok 管道 + Kibana） |
| **链路追踪** | Apache SkyWalking                       | 9.x | 同 §2.5 — 调用链拓扑 + 性能瓶颈定位（V2.4 配置就绪：compose 含 OAP/UI，Agent 以 -javaagent 附加） |

### 2.9 服务调用与部署

| 层级         | 技术                                    | 版本  | 使用场景                                 |
| ---------- | ------------------------------------- | --- | ------------------------------------ |
| **内部 RPC** | OpenFeign + Spring Cloud LoadBalancer | —   | 微服务间声明式调用（Feign 接口定义在 `ia-api` 模块）   |
| **本地开发**   | Docker Compose                        | —   | 一键启动 MySQL + Redis + Nacos 开发环境（已完成） |
| **生产部署**   | Docker                                | —   | 容器化部署，单机 MVP                         |
|            | Kubernetes                            | —   | 容器编排、滚动更新、自动伸缩（计划 V2.0）              |
| **CI/CD**  | GitHub Actions                        | —   | 自动化构建 + 测试 + 镜像推送（计划 V1.1）           |

### 2.10 前端

> 完整选型分析见 [15-Front-End-Technology-Selection.md](../frontend/15-Front-End-Technology-Selection.md)

| 层级          | 技术                             | 版本        | 使用场景                                          |
| ----------- | ------------------------------ | --------- | --------------------------------------------- |
| **全栈框架**    | Next.js (App Router)           | 15+       | SSR/ISG/SSG 混合渲染，React Server Components 默认开启 |
| **UI 库**    | React                          | 19        | Server Components + `use()` hook + actions    |
| **类型系统**    | TypeScript                     | 5.x       | strict 模式，前后端共享 DTO 类型                        |
| **样式方案**    | Tailwind CSS                   | 4         | 原子化 CSS，design tokens 约束设计系统                  |
| **通用组件**    | Shadcn/ui                      | latest    | 代码拥有式组件库，移动端适配，基于 Radix 原语                    |
| **后台组件**    | Ant Design 5 + ProComponents   | 5.20+     | Table/Form/ProTable 开箱即用，仅用于 PC 后台            |
| **服务端状态**   | TanStack Query                 | v5        | 缓存/去重/乐观更新/无限滚动，与 RSC 完美配合                    |
| **客户端状态**   | Zustand                        | v5        | 购物车/认证/UI 状态，极简 API                           |
| **表单校验**    | React Hook Form + Zod          | 7.x / 3.x | 非受控高性能表单 + 类型安全校验                             |
| **构建工具**    | Turbopack (Rust)               | —         | Next.js 内置，比 Webpack 快 10x                    |
| **包管理**     | pnpm (Monorepo)                | 9+        | 严格依赖解析 + workspace 原生支持                       |
| **移动端**     | PWA → Taro 4                   | —         | Service Worker 离线优先；后期用 Taro 编译小程序            |
| **E2E 测试**  | Playwright                     | latest    | 多浏览器并行，trace viewer 调试                        |
| **单元/组件测试** | Vitest + React Testing Library | latest    | Vite 原生速度，用户视角测试                              |
| **错误追踪**    | Sentry                         | latest    | React Error Boundary + Session Replay         |
| **代码检查**    | ESLint 9 + Prettier 3          | latest    | Flat config + Tailwind class 自动排序             |

### 2.11 PostgreSQL + 缓存架构 (V4.0 学习)

| 层级 | 技术 | 版本 | 使用场景 |
|------|------|------|---------|
| **第二数据库** | PostgreSQL | 16+ | pgvector 向量搜索、JSONB 文档存储、tsvector 中文全文搜索（V4.0 学习双库架构） |
| **连接池** | PgBouncer | 1.23+ | PG 连接池化（PG fork 进程 5-10MB/连接 → PgBouncer 50连接池） |
| **本地缓存** | Caffeine | 3.x | JVM 进程内热点数据缓存（<1μs延迟），多级缓存第一层 |
| **告警** | Prometheus AlertManager | 0.27+ | 订单异常/RT飙升/AI Token超量 → 钉钉/邮件通知 |

### 2.12 大数据平台 (V4.0 学习)

| 层级 | 技术 | 版本 | 使用场景 |
|------|------|------|---------|
| **分布式存储** | HDFS (Hadoop) | 3.4+ | 用户行为数据/订单日志/搜索日志离线存储，TB 级数据仓库基础 |
| **批量计算** | Apache Spark | 3.5+ | 离线报表(日活/转化率/热销榜)、用户画像计算、推荐模型训练 |
| **协调服务** | Apache Zookeeper | 3.9+ | Kafka/Flink/Hadoop 集群协调（Kafka 3.7+ 可选 KRaft 模式去 Zk） |
| **数据仓库** | Apache Hive | 4.0+ | SQL-on-Hadoop，离线数据查询（配合 Spark SQL） |

> ⚠️ **V4.0 学习目的**：以上大数据组件用于学习大厂全链路数据处理流程。
> **V4.x 精简**：
> - Zookeeper → Kafka 3.7+ KRaft 模式去除（-1 组件）
> - Hadoop/Hive → MinIO 数据湖 + Spark 替代（-2 组件）
> - Flink → Spring 事件 + RabbitMQ 覆盖大部分流场景（-1 组件）
> - Spark → PG 聚合查询覆盖报表需求（-1 组件，保留为可选）

### 2.13 组件演进路线

| 阶段 | 组件数 | 说明 |
|------|--------|------|
| **V4.5 (当前)** | 14 | MySQL+ES+Redis+RocketMQ+RabbitMQ(已退役，compose 已注释)+Nacos+XXL-Job+Sentinel(trade 已落地，ADR-028)+MinIO+Seata(未接入)+SkyWalking+Prometheus+Grafana+ELK |
| **V4.0 (学习)** | 22 | +PG+PgBouncer+Caffeine+AlertManager+Kafka+Zookeeper+Flink+Hadoop+Hive |
| **V4.x (收敛)** | 10 | PG→替代MySQL+ES向量, KRaft→去Zk, MinIO+Spark→去Hadoop/Hive, 事件驱动→去Flink, 事务表→去Seata |

---

## 三、微服务拆分

### 3.1 服务清单

| 服务 | 域 / 职责（边界重构后） | 优先级 | 关键依赖 / 数据源 |
| --- | --- | --- | --- |
| `gateway-service`          | API 网关：路由转发、统一入口、JWT 校验（含吊销校验：`JwtBlacklistFilter` 一次 MGET 合并 `auth:blacklist:{jti}` + `auth:tokenver:{userId}`，[ADR-031](../adr/031-multi-granularity-token-revocation.md)）、**WS 握手 query token 换头（`WsHandshakeTokenFilter`，WebFilter order -900，ADR-026）**、可信头剥离（含 XFF 剥离与 `X-Client-Ip` 覆写）、IP 限流（auth/verification）、AI Token 配额（`AiTokenQuotaFilter`）、**`/api/admin/pay/**` 前置路由（pay-service 管理端退款/对账，`pay-admin-routes` 须先于 `/api/admin/**` 声明，ADR-035/036）** | P0         | Spring Cloud Gateway + Nacos + Redis                |
| `authorization-service` | 认证+授权域：OAuth2 登录/注册/找回密码，Token 签发；**`user_auth` 凭证唯一所有权**（[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)）；**RBAC 五表角色/权限管理与权限缓存（写入/主动失效/登录注册预热）**（[ADR-018](../adr/018-unified-identity-rbac-ownership.md)）；**jti 黑名单写侧（登出拉黑当前 access token，TTL=剩余有效期，[ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)）；tokenVer 用户级版本吊销（改密/重置密码、管理端禁用 → `auth:tokenver:{userId}` INCR + 全端拆 refresh 会话，[ADR-031](../adr/031-multi-granularity-token-revocation.md)）**；`AuthContextClient` 委托契约（含 `revokeUserTokens` 吊销委托）；存量迁移器 `LegacyCredentialMigrationRunner` | P0         | Spring Security + JWT + Redis + Geetest                |
| `user-service`          | 用户档案域：资料（PII 三字段加密）/头像/地址簿；外围：收藏/浏览历史/操作日志（**`operation_log` 台账写入方恢复：user 域管理操作显式落库，[ADR-032](../adr/032-operation-audit-dual-pipeline.md) 管道二**）；**会员等级引擎（`member_level_rule` 门槛规则 + `member_growth_log` 成长值流水：`uk_order_no` 幂等入账、`MemberLevelPolicy` 重算只升不降；I45 等级权益/I46 成长值，trade 确认收货 fail-open 触发，[ADR-044](../adr/044-member-level-and-benefits.md)）**。DDD 四层（adapter/app/domain/infrastructure，app 按聚合 7 子包）。**2026-10 边界重构已迁出**：签到/积分→marketing、余额→wallet、站内信→notification、聊天/知识库→ai、RBAC 写侧→auth（ADR-018）、发码/本地校验链（含 `000000` 后门）→删除（→verification）、商家档案与店铺（入驻申请+审批）/店铺关注→merchant-service（第六轮） | P0         | MySQL + Redis（分布式锁取 ia-common 共享内核 `DistributedLock`，ADR-024） |
| `merchant-service`      | 🆕 商家域：入驻申请/审核（审批通过经 `AuthContextClient.setUserRole` 自动授予 ROLE_SELLER）、商家身份（**`seller` 表仅身份+生命周期**）/店铺档案（**`shop` 表 1 商家:N 店铺**，V5.4 拆分，[ADR-019](../adr/019-merchant-shop-1n-split.md)）/公开店铺页（`GET /api/shops/{shopId}` 新口径 + `GET /api/shop/{sellerId}` 遗留默认店铺别名）/店铺关注（Redis Set，本服务私有 Key：`shop:followers:{shopId}` / `merchant:follow:shops:{userId}`）；**店铺装修（`shop_decoration` 与 shop 1:1：公告 + 轮播图 ≤5 + 推荐商品位 ≤8，`GET/PUT /api/seller/shops/{shopId}/decoration`，[ADR-048](../adr/048-content-notes-in-item-domain.md)）**；DDD 四层（adapter/app/domain/infrastructure），领域类 `Merchant` / `Shop` / `ShopDecoration` | P1         | MySQL + Redis                                           |
| `item-service`          | 商品 (SPU)、规格 (SKU)、类目管理；**库存原子扣减/恢复（单条 SQL，条件内嵌 WHERE，行数为唯一判定，[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)）**；**运费模板（`freight_template` 商家端 CRUD `/api/item/freight-template/**` + 商品绑定 `spu.freight_template_id` + 内部计价查询 I43，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**；列表页最低价 `minPrice`；**相似商品推荐（P27 详情页相似商品卡，向量装配 fail-open，[ADR-046](../adr/046-product-vector-recommendation.md)）+ 促销标签（P2 详情/P1 分页 `promotions` 角标：I44 活动查询 fail-open + 60s 缓存，[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）**；**种草笔记（P28-P33 `/api/notes/**`：`note`/`note_spu`/`note_like`，`content` 聚合分包；发布/软删笔记向量摄取 afterCommit 钩子 + P34 相似笔记，[ADR-048](../adr/048-content-notes-in-item-domain.md)）；评价完成后每日任务触发（afterCommit，[ADR-047](../adr/047-daily-task-system.md)）**；V4.0: stockType(LIMITED/UNLIMITED/PRESALE)+isHot+hotReason+salesTags | P1         | MySQL + MinIO/OSS (商品图片，计划 V1.1)                       |
| `cart-service`          | 购物车（读时由应用层 `CartManager` 编排回填商品信息与金额/`allSelected`，[ADR-021](../adr/021-cart-readtime-enrichment-in-app-layer.md)；并发加购唯一键冲突幂等合并）                                | P1         | MySQL + Feign（→item-service 批量取 SKU）                                                  |
| `trade-service`         | 统一订单中心：OrderTypeEnum(NORMAL/DIRECT/FLASH_SALE/PRESALE)+策略模式(OrderCreateStrategy+OrderCreateContext+OrderCreateStrategyFactory)；Saga补偿；OrderManager编排；售后+结算（**平行入驻审核流程已于第六轮退役**，详见 §10.2）；**运费计价（`PostageCalculator` 纯函数，Phase 1 取最高费率模板计一次 + 满额包邮阈值；秒杀单免邮；I43 fail-closed）+ 拆单发货（`order_item.package_no` 归属标记、部分发货停留待发货，ADR-037）+ 售后退款触发纠正（仅退款审核同意即触发 / 退货退款确认收到退货后触发）**；**下单入口 Sentinel 流控+熔断已生效（`createOrder` QPS 100 / 异常比例 0.5，blockHandler → `FREQUENT_ERROR`(429)，[ADR-028](../adr/028-trade-sentinel-wiring-over-resilience4j.md)）** | P1         | MySQL + RocketMQ (订单超时延迟消息 level=16/30min) + Seata (分布式事务，计划 V1.1) + XXL-Job (兜底，计划 V1.1) |
| `pay-service`           | 支付处理，微信支付 API v3，多渠道路由(WECHAT/ALIPAY/BALANCE)——余额渠道经 `BalanceClient` → wallet-service；**退款执行（`RefundManager` 三通道原路退回：BALANCE 退钱包 / WECHAT v3 退款+异步回调 / ALIPAY mock 同步；`refund_order` `uk_after_sale` 幂等锚点，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）+ 渠道对账（日终账单四态差异只记录不调账，`uk_date_channel` 先删后写幂等，[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)）**；**退款悬挂补偿（`RefundResumeJob`：微信退款查询结算 / 余额·支付宝幂等重放，[ADR-041](../adr/041-refund-resume-aftersale-guard-freight-cache.md)）** | P1         | MySQL + wechatpay-java 0.2.17 + RocketMQ (支付超时延迟消息) |
| `logistics-service`     | 物流信息管理，状态追踪，快递员API V1.1            | P1         | MySQL + WebSocket (物流状态推送，计划 V1.2)                     |
| `search-service`        | 商品全文检索：ES双实现(ES全面搜索+DBSearch MySQL LIKE回退)；**ES 文档对象 `SpuDocument`（infrastructure/es）与出参 `SpuSearchVO`（adapter/response）分离**；**知识库向量摄取（`/internal/search/vector/knowledge/upsert`：分块 `DocumentChunker` + Embedding 在本服务完成，索引 `knowledge_vectors` 归 ES 域，见 §3.4）**；**商品向量推荐（I47 upsert 摄取 / I48 相似 kNN：`products` 索引 knn mapping、reindex 重建带向量，[ADR-046](../adr/046-product-vector-recommendation.md)）+ 笔记向量（I50/I51，`notes` 索引，[ADR-048](../adr/048-content-notes-in-item-domain.md)）**；**搜索 facets 聚合（SE6）+ brand/minPrice/maxPrice/sort 过滤（SE1，ES+DB 双链对齐）**；**搜索联想（SE5：热搜前缀 + 商品名前缀双源，fail-open）** | P1         | ElasticSearch 7.17 + MySQL LIKE (双实现, @Primary 为 DB)            |
| `ai-service`            | 客服域：AI 客服 + 人工 IM 会话（STOMP `/ws/chat` + REST `/api/chat/**`，chat_message；2026-10 迁入；**STOMP 发送者身份取自会话 Principal，客户端无法伪造**）+ 商家知识库 RAG（`/api/seller/knowledge`，merchant_knowledge_base；2026-10 迁入；**`ai.rag.enabled` 渐进启用的向量摄取 + RAG 应答接线，见 §3.4**）+ AI 商品助手；LangChain4j AiServices Agent+@Tool+ChatMemory；**domain/port 五接口（Order/SpuDetail/Search/UserProfile/VectorSearch）+ infrastructure/adapter Feign 防腐** | P2         | LangChain4j(Agent) + Spring AI(基建) + DeepSeek V3 + ES向量(P1) |
| `marketing-service`     | 优惠券V4.0 五维模型(discountType FIXED/PERCENTAGE/CASH_COUPON × couponCategory PLATFORM/SHOP/FLASH_SALE/EXCLUSIVE × grantType FREE_CLAIM/PAID_PURCHASE/INVITATION/AUTO_ISSUE × stockType LIMITED/UNLIMITED × grabType NORMAL/NEED_GRAB/PLATFORM_EXCLUSIVE)；Redis Lua抢券 + **DB落库失败抢券补偿（INCR+SREM 回滚预扣）**；smartClaim智能路由；V4.2 couponCategory+卖家匹配校验；V4.5 Redis SISMEMBER去重；秒杀活动（**`/api/flash/buy` 走 MarketingManager：Redis Lua 预扣（显式预热+惰性兜底）→ Feign 落单（trade）→ RocketMQ 异步统计**；秒杀商品无店铺归属 sellerId=0，平台活动口径）；**积分账本（`points_log`，`/internal/points/add`）+ 签到活动（SignInStore/SignInPointsPolicy/SignManager；2026-10 迁入）**；**促销引擎（店铺级满减/满折 `promotion`：状态由时间窗实时推导，AD48-50 管理端 `/api/admin/promotion` + I44 计价查询 fail-closed，[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）**；**每日任务体系（`task_def`/`task_record` 表驱动：M14 任务中心 `/api/task/daily` + I49 幂等完成（`uk_user_task_date`）+ AD51-53 管理端 CRUD `/api/admin/task`，[ADR-047](../adr/047-daily-task-system.md)）**；**活动中心（M12/M13 `/api/activity` 聚合进行中促销 + M7 可领券三态计数）**；四层归位（persistence PO 化、`CouponPricing` 领域服务） | P1         | MySQL + Redis + RocketMQ |
| `ia-integration`        | 外部集成SDK：微信支付V3、支付宝Mock、阿里云短信、邮件(SMTP+Mock)、极验Geetest V4、MinIO对象存储、微信OAuth（V5.2 起 SMS/Geetest 工具类统一收口于此） | 基础模块       | wechatpay-java + dysmsapi + spring-boot-starter-mail(optional) + MinIO SDK |
| `ia-common`             | 共享库：异常、Result、工具、注解、i18n；RocketMqTopics；DomainEvents；**`@OperationLog` 审计切面（单行 JSON → logger `AUDIT`，[ADR-032](../adr/032-operation-audit-dual-pipeline.md)）**（V5.2 起 SMS/Geetest 工具已迁出至 ia-integration） | 基础模块       | Hutool + Knife4j + RocketMQ |
| `ia-api`                | 共享 Feign 接口定义（V5.2：新增 `VerificationClient` fail-closed 降级；`NotificationClient` 强类型化；`AuthClient` 契约随 `/internal/auth/**` 整体退役删除，见 [ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)） | 基础模块       | OpenFeign + LoadBalancer                               |
| `notification-service`  | 触达域：短信/邮件（纯通道）+ **站内信（`notification` 表落库；公开 `/api/notifications/**`、内部 `/internal/notify/in-app` self-send 忽略）**；第三方请求全部在 ia-integration | P1         | MySQL（`notification` 表，站内信落库）+ ia-integration (SmsClient/EmailClient)，端口 9400 |
| `verification-service`  | 验证域：验证码签发/校验消费 + 人机验证（**唯一出口**；厂商中立 Feign 契约 `provider`+`params`）；降级 fail-closed | P1         | Redis + ia-api Feign(→notification-service)，无库表，端口 9401 |
| `wallet-service`        | 🆕 资金域：余额账户（`wallet_account`，V5.3 存量自 user.balance 迁入、列冻结；`/internal/balance/deduct`）；**余额流水（`wallet_transaction`，幂等键 `transaction_no`，入账与流水同事务；退款入账经 `/internal/balance/refund`，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）** | P1         | MySQL，端口 9500 |

**域归属（一个业务域一个服务；同一事实只允许一个所有者）**：

| 域 | 归属服务 | 边界要点 |
| --- | --- | --- |
| 认证 + 授权域 | authorization-service | 登录/凭证（`user_auth`）/RBAC 五表/权限缓存 |
| 用户档案域 | user-service | 资料/头像/地址簿；不持有凭证、授权数据、余额、商家档案 |
| 商家域 | merchant-service | 入驻申请/审核（审批自动授予 ROLE_SELLER）、商家身份（`seller` 表）+ 店铺档案（`shop` 表，1:N，ADR-019）、公开店铺页、店铺关注（Redis Set，私有 Key） |
| 验证域 | verification-service | 验证码与人机验证唯一出口（fail-closed） |
| 触达域 | notification-service | 短信/邮件/站内信（站内信落库） |
| 资金域 | wallet-service | 余额账户（`user.balance` 冻结） |
| 营销域 | marketing-service | 优惠券/秒杀/积分账本/签到 |
| 客服域 | ai-service | AI 客服/人工 IM/商家知识库 |
| 支付域 | pay-service | 支付处理（余额渠道委托资金域） |
| 商品 / 购物车 / 交易 / 物流 / 搜索域 | item / cart / trade / logistics / search | 维持原边界 |
| 共享内核 | ia-common / ia-api / ia-integration | 非业务域：异常与工具 / Feign 契约 / 第三方集成，单向被依赖 |

**已删除的反模式（禁止回退，2026-10 边界重构定稿）**：

- ❌ **跨服务共享 Redis Key**：原 `sms:code:` 跨服务契约已解除——任何服务不得直读其他服务的 Key 空间，校验一律经 verification-service 在线完成；
- ❌ **档案域持有授权数据**：RBAC 五表与权限缓存的读写只属于 authorization-service（ADR-018）；
- ❌ **本地验证码校验链**：user-service 本地校验（含 `000000` 灰度后门）与 `/api/user/code` 发码端点已整体删除。

### 3.2 服务关系图

```
gateway-service（API 网关：路由 / JWT 校验 / WS 握手换头 / 可信头剥离 / 限流）
  ├── authorization-service   认证+授权域（登录凭证 user_auth / RBAC 五表 / 权限缓存）
  ├── user-service            用户档案域（资料/地址/收藏/历史/会员等级）
  ├── merchant-service        商家域（入驻/店铺/装修/公开页/关注；seller + shop + shop_decoration 表 + Redis 关注）
  ├── item-service            商品域（SPU/SKU/类目/库存/评价/首页装修/种草笔记/向量推荐）
  ├── cart-service            购物车域
  ├── trade-service           交易域（订单/Saga/售后/结算）
  │     ├── pay-service       支付域（微信/多渠道路由，余额渠道 → wallet）
  │     └── logistics-service 物流域
  ├── marketing-service       营销域（优惠券/秒杀/积分账本/签到/促销引擎/每日任务/活动中心）
  ├── wallet-service          资金域（余额账户）
  ├── ai-service              客服域（AI 客服/人工 IM/商家知识库）
  ├── search-service          搜索域（ES + MySQL LIKE 双实现 + 向量推荐/搜索联想）
  ├── verification-service    验证域（验证码/人机验证唯一出口）
  │     └── notification-service 触达域（短信/邮件/站内信）
  └── 共享内核：ia-common / ia-api / ia-integration

Nacos ← 注册中心 + 配置中心（所有服务）
```

**V5.2 通信/校验调用链**（能力抽离后）：

```
authorization-service 等调用方（已收口；user-service 发码链已整体删除）
  → Feign verification-service  /internal/verification/**   （校验收口，fail-closed）
      → Feign notification-service /internal/notify/**      （通道投递：sms / email / in-app）
          → ia-integration (sms/ email/ captcha/) → 第三方（阿里云短信 / SMTP / 极验）
```

> 迁移状态（[ADR-016](../adr/016-user-service-ddd-and-sms-migration.md) / [ADR-017](../adr/017-user-domain-split-and-auth-boundary.md) + 2026-10 边界重构）：**迁移已完成收口**——user-service 的本地验证码校验链（含 `000000` 灰度旁路）与 `/api/user/code` 发码端点整体删除，`sms:code:` 跨服务 Redis Key 契约解除；验证码签发/校验一律经 verification-service 在线完成（Feign，fail-closed），任何调用方不再直读该校验 Key 空间。authorization-service 人机验证收口 verification-service（不再直连极验；Feign 熔断已启用 resilience4j，fail-closed，TimeLimiter 5s；厂商中立契约 `provider`+`params`）。

### 3.3 共享模块

**ia-common** 包含：

- 统一返回体 `Result<T>`
- 异常体系：`CommonException`, `BadRequestException`, `BizException`, `DBException`, `ForbiddenException`, `UnauthorizedException`
- 全局异常处理器 `GlobalExceptionHandler`（返回 `ResponseEntity<Result<Void>>`）；**`/internal/**` 业务异常返回真实 HTTP 状态码 + 极简 `{"code","msg"}` JSON**（ADR-004 修订落地：不再二次抛异常冒充 500，Feign 调用方可区分业务失败与故障）；补 `ConstraintViolationException` → `PARAM_ERROR`(400) 映射
- `ErrorCode` 枚举（业务码 + HTTP 状态码映射；限流超限 = `FREQUENT_ERROR`(1014/429)）
- `@RateLimit` 限流注解 + AOP（**限流键含方法维度 `rate:limit:{类.方法}:{业务键}`**，V5.3 修正）
- 分布式锁共享内核：`common/lock/DistributedLock` + `RedissonDistributedLockAdapter`（条件装配 `@ConditionalOnClass`；auth/user 服务内重复副本已删，[ADR-024](../adr/024-distributed-lock-consolidation-in-ia-common.md)）
- 通用工具：JWT 辅助、用户上下文、`IpUtil`（下游消费口径：只信任网关注入的 `X-Client-Ip`；代理头回落分支复核为**非死代码**——dev 直连/解析失败兜底仍命中，保留。与网关边缘口径 `EdgePeerIpResolver`（TCP 对端、不信任任何代理头）分属两种口径、不可合并，[ADR-034](../adr/034-cross-module-utility-consolidation-and-boundaries.md)）（V5.2 起 SMS/Geetest 工具已迁至 `ia-integration`；V5.3 起仅保留 `lua/rate_limit.lua` 一个脚本，零调用的 `del_redisKey/check_limit/login_rate_limit` 已删）
- **掩码族 `DesensitizeUtil`（2026-10 收口，[ADR-034](../adr/034-cross-module-utility-consolidation-and-boundaries.md)）**：`maskPhone` / `maskEmail` / `maskAccount`——**全仓掩码规则单点定义**（账号 ≤4 全遮 `****`、>4 保留末 4 位，如 `****1234`）；trade 本地 `SensitiveMaskUtil` 已删（MapStruct 表达式改指本类），ai-service 手机号掩码同调 `maskPhone`；`maskPhone` / `maskEmail` 既有签名零改动
- **`SecurityHeaders` 头名常量（2026-10，[ADR-034](../adr/034-cross-module-utility-consolidation-and-boundaries.md)）**：14 个可信上下文头 / 代理头名称的跨模块唯一定义（`X-User-Id` / `X-Client-Ip` / `X-Trace-Id` 等，含「仅网关注入 / 可伪造代理头 / 可透传追踪头」三类信任边界注释）；替换 ia-common / ia-api / gateway / ai-service 8 个文件的字面量（值逐字不变）
- MyBatis-Plus 配置：分页拦截器 + **`OptimisticLockerInnerInterceptor`（V5.3 补齐，orders 等 `@Version` 实体生效）**
- **审计字段填充器 `AuditFieldMetaObjectHandler`（2026-10 收口，[ADR-030](../adr/030-metaobjecthandler-consolidation-autoconfig.md)）**：`createTime` / `updateTime` 自动填充（严格模式 `strictInsertFill` / `strictUpdateFill`）的共享内核唯一实现，经 `MyMetaObjectHandlerAutoConfiguration` 自动装配（`@ConditionalOnClass(MetaObjectHandler.class)` + `@ConditionalOnMissingBean` 退避，注册 `AutoConfiguration.imports`）——cart / item / logistics / merchant / pay / search / trade 七份服务内副本已删；user-service 因额外填充 `registerTime` 保留自有实现（退避共存）；authorization / marketing / notification / wallet / ai 五服务首次获得应用侧真实填充（口径统一，非 HTTP 契约变化）
- Redis 可选化（见 [ADR-012](../adr/012-conditional-on-class-guards-for-optional-deps.md)）：`RedisConfig` / `RedisLuaScriptConfig` / `RateLimitUtils` / `RateLimitAspect` 均已加 `@ConditionalOnClass` 守卫，类路径缺 Redis 时正常启动（notification-service 已摘除 Redis 依赖）
- **V5.3 死代码清理**：原 SMS 直发/限流工具、FieldPermission AOP 连同对应残留整体删除；OperationLog 审计能力当时未采用，已于 2026-10 以「审计双管道」形态重建（[ADR-032](../adr/032-operation-audit-dual-pipeline.md)，见 §3.5）
- **操作审计切面（2026-10 恢复，ADR-032 管道一）**：`@OperationLog` 注解 + `OperationLogAspect`（`AutoConfiguration.imports` 登记、全服务装配、**零业务依赖**：无 Mapper/Redis/Feign，userId 取自静态 `UserContext`）——成功/失败各输出**单行 JSON** 到专用 logger **`AUDIT`**（字段：timestamp / module / action / method / user / success / costMs；失败仅记异常类名不含 message；默认不记参数值，`recordParams=true` 仅输出「类型 + id 摘要」且截断 200 字符）；切面自身异常降级 WARN，绝不改变业务返回值；**注解只写日志、不落库**（落库见 §3.5 管道二）

**ia-api** 包含：

- 各服务间调用的 Feign 客户端接口定义
- 共享 DTO（避免每个服务重复定义）
- Feign 降级统一规范（`fallback/package-info.java`，见 [ADR-011](../adr/011-feign-fallback-degradation-semantics.md)）：降级语义三分法——① 展示型查询返回空值/非成功 `Result`；② 判别型查询 fail-closed 抛 `BizException`（防止故障伪装成业务事实）；③ 命令（写操作）一律抛异常禁止静默成功——附 15 个 Client 降级语义速查表

### 3.4 RAG 知识库向量链路（2026-10 接线，T14）

> 无独立 ADR，按代码现状记录。全链路 **fail-open**：任一环节未启用/失败只记 WARN 并降级，绝不阻断知识库 CRUD 与客服应答。**默认关闭 = 与接线前现状一致**。

**摄取链路**（商家知识库 → 向量索引）：

```
ai-service 知识库 CRUD（/api/seller/knowledge）
  → Feign KnowledgeVectorClient POST /internal/search/vector/knowledge/upsert
      → search-service 分块（DocumentChunker，自 ai-service 迁入）
      → Embedding（OpenAI 兼容 API，配置化；未启用时返回 0，不报错）
      → ES 索引 knowledge_vectors（每 chunk 一 doc，dense_vector；按 knowledgeId 先删后写，幂等）
  → 摄取成功后 ai-service 回填 merchant_knowledge_base.embedding_id = es:{knowledgeId}
```

**检索链路**（客服应答注入）：

```
CsManager 应答前 →（ai.rag.enabled=true 且请求带 sellerId 时）RAGRetriever 检索 topK
  → 命中知识 chunk 拼入 prompt；检索失败/未启用 → 降级为无 RAG 应答
```

**边界与归属**：

- **向量索引 `knowledge_vectors` 归 search-service（ES 域）所有**：分块、Embedding、索引读写在 search-service 内完成；ai-service 只提交原文、取回 chunk 文本（摄取经 `KnowledgeVectorPort`、检索经 `VectorSearchPort`，Feign 防腐），不在 ai 侧直连 ES；
- 开关：`ai.rag.enabled`（默认 **false**，= 现状）/ `ai.rag.top-k`（默认 5）；search 侧 `search.embedding.*` 未配置（enabled=false 或 api-key 空）= 摄取端点返回 0（等价「向量链路未接入」）；完整配置项见 14-Service-Config-Guide §5.4.4；
- **未实机联调**：以「开启开关 → 知识库增删改 → 客服检索」做端到端验证（ES/Embedding 就绪后执行）。

### 3.5 操作审计双管道（[ADR-032](../adr/032-operation-audit-dual-pipeline.md)）

| 管道 | 载体 | 覆盖范围 | 写入方式 |
| :--- | :--- | :--- | :--- |
| 管道一：`AUDIT` JSON 日志 | ELK 日志流（单行 JSON → logger `AUDIT`） | **全平台所有服务**的管理端写操作（已铺设：authorization / trade / item / marketing 共 12 个写端点注解） | `@OperationLog` 注解 + `OperationLogAspect`（ia-common，`AutoConfiguration.imports` 装配，零业务依赖，**默认不记参数值**） |
| 管道二：`operation_log` 落库 | user-service 本地表（`GET /api/admin/log` 查询） | **仅 user 域管理操作**（用户启用/禁用、角色授予） | 业务代码**显式**调用 `OperationLogManager.record(...)`（不经 AOP） |

- **口径声明**：`operation_log` 表**不再**是全平台审计台账，`AdminLogController` 查询范围收窄为 user 域管理操作；查询「全平台谁改了什么」走 ELK（`logger=AUDIT`）；
- **事务语义**：`updateStatus`（纯本地写）状态变更与审计留痕**同事务**；`setUserRole`（含 Feign 调用）审计自成独立事务（禁事务内调 Feign）；
- **已知偏差**：`operation_log.target` 列未被写入方使用（`action` = `MODULE:ACTION`、目标标识并入 `detail`；收敛需 DDL，详见 ADR-032）。

---

## 四、分层架构（四层 DDD —— 全服务统一，ADR-038 收口）

### 4.1 调用链路

```
adapter(web/admin/internal) → app/*Manager(编排) → domain(聚合/领域服务/仓储端口) → infrastructure(persistence/adapter)
```

**单向依赖，严禁反向调用；Feign 调用与分布式锁收口在编排层。**

### 4.2 各层职责

> 2026-10-04 起全服务**单一四层形态**（[ADR-038](../adr/038-full-ddd-migration.md) 收口，废止轻量 COLA 双轨）；轻量形态专属的顶层 `service/ mapper/ entity/ convert/ dto/` 层已不存在。

| 层         | 包名            | 职责                                                        | 必选                       |
| --------- | ------------- | --------------------------------------------------------- | ------------------------ |
| **接入层**   | `adapter`     | 请求入口，参数校验（`@Validated`），调用 Manager，封装 `Result<T>`；**按受众分子包：`web`（前台与商家端）/ `admin`（/api/admin/**）/ `internal`（/internal/**）** | ✅                        |
| **入参/出参** | `adapter/request`、`adapter/response` | 入站对象必须为类型化 `XxxReq`（禁止 `Map`/裸标量 body）；出站 `XxxVO` 不得暴露 `version/cipher/hash` 等技术字段 | ✅                        |
| **转换层**   | `adapter/assembler` | Req/VO ↔ Command/DTO 转换（MapStruct；2026-10 由 `convert` 更名，用户裁决见 ADR-038） | 🟡 该服务有转换需要时           |
| **编排层**   | `app`（`XxxManager`） | 流程编排、分布式锁、限流、幂等、多服务组合、跨服务调用聚合（Feign/MQ 只允许出现在本层）；**Manager 按聚合/用例族收敛：一个 Manager 只归属一个聚合或一个用例族（混装职责必拆），沿用 `XxxManager` 命名（[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）** | ✅                        |
| **领域层**   | `domain` | `model/<聚合名小写>/`（按聚合分包，聚合根/实体/值对象；[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）、`service`（领域服务/纯函数）、`repository`（仓储端口）、`port`（端口）、`enums`；包内零框架依赖 | ✅ |
| **基础设施层** | `infrastructure` | `persistence`（PO + Mapper + RepositoryImpl，多语句写在此携带 `@Transactional`）、`adapter`（Redis/HTTP 防腐）、`messaging`（producer + consumer）、`scheduler`（定时任务）、`config` | ✅（DB/外部集成服务）            |
| **配置层**   | `infrastructure/config` | Spring 配置、切面配置                                            | ✅                        |

### 4.3 包结构（四层 DDD 单一形态 —— 2026-10-04 全服务收口 [ADR-038](../adr/038-full-ddd-migration.md)；聚合分包口径沿 [ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）

**14 个业务服务统一四层 DDD**——Web 契约类（Req/VO）统一在 `adapter/request` / `adapter/response`，转换器统一在 `adapter/assembler`（[ADR-023](../adr/023-adapter-layer-structure-and-request-object-naming.md)）：

```
org.icedamericanomall.<service>
├── adapter
│   ├── web        ← 前台与商家端 REST 控制器
│   ├── admin      ← /api/admin/** 管理端控制器
│   ├── internal   ← /internal/** 集群内 Feign 控制器
│   ├── request    ← 入参对象 XxxReq（类型化校验，禁 Map/裸标量 body）
│   ├── response   ← 出参对象 XxxVO
│   └── assembler  ← MapStruct 转换器
├── app            ← 编排 Manager（按聚合/用例族收敛，一个 Manager 一个用例族；锁/Feign 聚合/多服务流程）
├── domain         ← model / service / repository / port / enums（零框架依赖）
│   └── model      ← 按聚合分包 model/<聚合名小写>/（单聚合平铺、共享值对象留根、枚举例外 —— 见下方 ADR-025 口径）
├── infrastructure ← persistence(PO+Mapper+RepositoryImpl) / adapter(防腐) / messaging / scheduler / config
└── …
```

**`domain/model` 聚合分包与 `app` 用例族口径（[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)，2026-10-03 收口）**：

| 维度 | 规则 | 现状示例（2026-10） |
| :--- | :--- | :--- |
| **聚合分包** | `domain/model/<聚合名小写>/` 一个聚合一个包；包边界即聚合边界（未来服务拆分边界） | authorization `model/userauth/`（+ 既有 `model/rbac/`）；user `model/user/`、`model/address/`、`model/favorite/`、`model/member/`（会员等级，ADR-044）；merchant `model/merchant/`、`model/shop/`（含 `ShopDecoration`，ADR-048）；item `model/content/`（种草笔记，ADR-048）；marketing `model/promotion/`（ADR-043）、`model/task/`（ADR-047）；notification `model/inapp/`（聚合根 `InAppMessage`）+ `model/channel/`（`SmsMessage` / `EmailMessage` 同族通道值对象） |
| **例外① 单聚合平铺** | 仅 1 个聚合（或单族值对象）时保持平铺，不强行嵌套 | verification `model/` 平铺（`VerificationCode` / `VerificationPolicy`） |
| **例外② 跨族共享值对象** | 登录策略 / 风控 / 会话等多能力族共享、被 app/infrastructure 消费的领域值对象留 `model/` 根，不为单类造包 | authorization `LoginSpec` / `EmailAddress` / `WechatSession` / `RiskAssessment` / `RefreshTokenInfo`；notification `PageResult` 留 `model/` 根 |
| **例外③ 枚举归属** | 默认留 `domain/enums/`（服务级共享词汇）；仅聚合私有状态机随聚合下沉 | merchant `ShopStatusEnum` → `model/shop/`（仅 Shop 聚合及持久化引用）；`MerchantStatusEnum`（被 app 引用）留 `domain/enums/` |
| **不强行造** | 无领域模型的模块不强行造聚合 | 全部 14 业务服务均已建 `domain/model`（ai 5 聚合 conversation/knowledge/order/spu/userprofile；marketing coupon/flash/points/promotion/task；wallet WalletTransaction；余额不变式仍由仓储条件 UPDATE 承载）；gateway 无业务领域模型不建 |
| **app 粒度** | 一个 Manager 只归属一个聚合或一个用例族，混装职责必拆（>300 行必拆）；命名沿用 `XxxManager`，**不引入** `CmdExe` / `QryExe`；已单一职责者不动 | user `UserManager` → `UserQueryManager`（读取+统计）/ `UserAdminManager`（管理端运营）/ `UserProvisionManager`（幂等开通）；notification `NotificationManager` → `ChannelDispatchManager`（SMS/Email 投递）/ `InAppMessageManager`（站内信）；marketing `CouponService` → `CouponClaimService`（领取/抢券/支付后发券）/ `CouponUsageService`（核销/回滚/持券查询）；auth `AuthManager`、merchant 三 Manager、wallet `WalletManager`、ai 四 Manager、verification `VerificationManager` 复核后不动 |

> 本次收口为纯结构归位：HTTP 契约（URL / 报文 / 错误码）零变化，590 项测试保持全绿。

> 原"形态二：轻量 COLA"（item / cart / trade / pay / logistics / search 六服务的顶层 `service/ mapper/ entity/ convert/` 结构）已于 2026-10-04 全部完成四层改造并废止（[ADR-038](../adr/038-full-ddd-migration.md)），结构示意不再保留。

### 4.4 分层铁律

1. **领域层不依赖基础设施/DB**
2. **数据库 DO 只允许在 mapper/infra 出现，禁止外泄到 Controller**
3. **外部入参、出参、微服务调用全部用 DTO 隔离**
4. **分布式问题（锁/限流/幂等）统一收口到 Manager**
5. **业务核心规则内聚到 Domain Service**
6. **Controller 绝不写业务逻辑、绝不直接操作 Redis/锁**

---

## 五、安全架构

### 5.1 认证体系

```
            ┌─────────────┐
            │  前端 (H5)   │
            └──────┬──────┘
                   │ POST /api/auth/login
            ┌──────▼──────────────┐
            │ authorization-service│
            │  OAuth2 Auth Server  │
            │  + JWT Token 签发    │
            └──────┬──────────────┘
                   │ JWT (HS256/RS256)
                   │ + Refresh Token (Redis)
            ┌──────▼──────┐
            │ gateway-service │ ← Token 校验
            └──────┬──────┘
                   │ 用户上下文传递
            ┌──────▼──────────┐
            │  各业务微服务     │
            │  (Resource Server)│
            └─────────────────┘
```

### 5.2 登录架构

- **用户端**：`https://mall.icedamericano.com/login` → H5 应用
- **商家端**：`https://seller.icedamericano.com/login` → PC 后台应用
- **两套前端、同一套账号、统一认证后端**
- 商家端所有接口需校验 `seller` 角色，且只能操作自己店铺数据
- Token 包含角色信息，前端/网关根据角色判断权限

**V5.1 DDD 重构后的登录编排链路**（authorization-service，Adapter → Application → Domain ← Infrastructure）：

```
AuthController(adapter) 参数校验 → LoginCommand
  └─ AuthManager(app) 编排：
       1. 人机验证（若请求携带 captcha payload，CaptchaService Port → 极验）
       2. 风控评估（RiskControlService Port → 设备信任/失败计数/IP 信誉/风控模型维度评分）
       3. 风控处置（RiskHandler 策略：Pass / Captcha / SmsVerify / AccountFreeze，阻断即抛 BizException）
       4. 登录策略（LoginStrategy 领域策略，按 identityType+credentialType 分发）
            └─ 领域服务 UserAuthProvisioner：查 user_auth → 分布式锁 → 查/建凭证
               （建档经 UserAccountGateway 防腐层调 user-service /internal/user/getOrCreate 幂等建号；
                 V5.2 起凭证只写本地 user_auth，不再回写 user.password）
       5. 签发令牌（LoginTokenService Port：access + refresh，refresh 轮换不延长绝对有效期）
```

事务边界（V5.1，见 ADR-005）：模块内无多语句本地写，**不使用 `@Transactional`**；跨服务一致性靠远端幂等端点 + `user_auth` 业务唯一索引 + 冲突回查重放（`DuplicateIdentityException`），无 MQ 故不引入本地消息表。

> **V5.2 认证边界收敛**（[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)）：`user_auth` 凭证唯一所有权归 authorization-service。注册链路 = 本地校验并消费短信验证码（verification-service）→ `UserAccountGateway.getOrCreateUserId`（user-service 幂等建档 + 新用户建档奖励）→ 本地 `UserAuthProvisioner.findByHashOrCreateWithCredential`（PHONE 三字段 + credential）→ 签发 token；已注册手机号返回 `PHONE_ALREADY_REGISTERED`（用户名占用校验退役，昵称降级为展示字段）。`/internal/auth/**` 六端点与 ia-api `AuthClient` 契约整体废弃删除；重置密码不再回调 user-service；微信网页登录收归 auth 本地（`WechatWebSessionService` 直连 ia-integration 换 openid，冻结拦截移至下游业务 API）。

### 5.3 密钥管理

- RS256 非对称加密：私钥签发，公钥通过 JWKS 端点暴露（`/.well-known/jwks.json`）
- HS256 对称加密：共享密钥，用于内部服务间调用
- 支持多密钥切换（大厂做法：平滑滚动更新）
- PII 数据加密密钥（`ia.crypto.aes-key` / `ia.crypto.hmac-key`，V5.2 起由 ia-common `CryptoKeyConfig` 装载）：见 12-Security-Model §5.4

### 5.4 RBAC 权限缓存失效、令牌吊销与网关可信头剥离（V5.2 ADR-017；RBAC 归属迁移见 [ADR-018](../adr/018-unified-identity-rbac-ownership.md)）

- **权限缓存主动失效（2026-10 起归 authorization-service，ADR-018）**：网关实时权限读 `user:perms:{userId}`（Redis SET，TTL 30 分钟）。授权变更（authorization-service `RoleManageManager` 的 `setUserRole` / `assignPermissions` / `deleteRole`）经 `PermissionCacheEvictor` 主动删除受影响用户的 `user:perms:` 与 `user:info:` 两键，撤销的权限即时生效；**登录/注册成功即预热缓存**（消除角色空窗）。失效失败仅 WARN（缓存可自愈）。user-service 的角色设置（`PUT /api/admin/user/{id}/role`）与 merchant-service 入驻审批（`PUT /api/admin/seller/{id}/approve` → ROLE_SELLER）均经 ia-api `AuthContextClient`（`PUT /internal/auth/user/{id}/role`）委托授权域（后者为 2026-10 第六轮审批缝合，消除"审批后需管理员手动授角色"缺口）；`sys_user_permission` 当前无写入端点，新增直授端点时必须接入本失效器。
- **令牌吊销（三级粒度：jti / tokenVer / refresh 会话，[ADR-031](../adr/031-multi-granularity-token-revocation.md)，修订 [ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)）**：网关 `JwtBlacklistFilter`（order -700，非阻塞 `ReactiveStringRedisTemplate`；类名保留）用**一次 `MGET`** 合并查询 **`auth:blacklist:{jti}` + `auth:tokenver:{userId}`**——黑名单命中，或版本键存在且与 token 的 `ver` claim（缺失/异常按 `0`）不一致 → **401**；版本键缺失视为「从未被用户级吊销」跳过比对；无 `jti` 维持原整体放行语义；Redis 异常 → **fail-open**（每请求 Redis 往返次数不变，仍为 1 次）。access token 签发（登录与 refresh 换发走同一链路）写入 `ver` claim = 签发时刻的当前版本，键缺失 / 无 claim 均按 `0`（存量令牌无需迁移）；版本键 `INCR` 即令该用户全部旧令牌失效，**不设 TTL**（TTL 过期会把「版本落后」的令牌重新放行 = 吊销回滚）。触发点：**登出** = 拆本端 refresh 会话 + 拉黑本次请求的 access token（jti 粒度，**不变**，TTL = max(令牌剩余有效期, 1s)）；**改密 / 重置密码** = 版本 INCR + 全端拆 refresh 会话（由 ADR-027 的「拉黑当前令牌」改为此口径，匿名改密同样生效）；**管理端禁用** = user-service `UserAdminManager` 状态置为禁用后经 ia-api `AuthContextClient.revokeUserTokens` → `POST /internal/auth/token-version/revoke`（版本 INCR + 全端拆会话；调用位于本地事务之后，降级 fail-open 不阻断禁用主流程）。`auth:tokenver:{userId}` 为**网关依赖的跨服务契约键**（auth 写 / 网关只读），与 `user:info:` / `user:perms:` / `auth:blacklist:` 同级（豁免清单见 09-constraints §四）；WS 握手（ADR-026）与普通 HTTP 共用同一吊销口径。**ADR-027 两项残余风险（改密后其他设备存活、管理端禁用不拆会话）已关闭**；残余风险仅剩 Redis 故障窗口 fail-open 放行 + 未实机验证（ADR-031 记录）。
- **入站可信头剥离**：网关 `UntrustedHeaderSanitizerFilter`（order = **-1100**，GlobalFilter 段最早）无条件移除入站 `X-User-Id` / `X-Username` / `X-User-Roles` / `X-User-Permissions` / `X-Tenant-Id` / `X-Client-Ip`，并剥离可伪造的代理头 `X-Forwarded-For` / `X-Real-IP` / `Forwarded`、由网关按真实对端地址**覆写 `X-Client-Ip`**（下游 `IpUtil` 只信任该头；`X-Trace-Id` 保留透传）。可信上下文头**只由网关在 JWT 校验通过后注入**（`UserContextHeaderFilter`）。**链序（两段式，2026-10-03 复核）**：**WebFilter 段**（路由前）：**`WsHandshakeTokenFilter`(-900，仅 WS 握手路径：query `token` 换写 `Authorization: Bearer` 并剔出该参数，见 ADR-026) → Spring Security 安全链（`WebFilterChainProxy`，order -100；`/ws/chat/**` 不进白名单、维持 `authenticated()`）**；**GlobalFilter 段**（路由阶段，晚于全部 WebFilter）：`UntrustedHeaderSanitizerFilter`(-1100) → `TraceIdFilter`(-1000) → **`JwtBlacklistFilter`(-700，响应式非阻塞吊销校验：黑名单 + 用户级版本号一次 MGET 合并，fail-open)** → `UserContextHeaderFilter`(-600) → 路由。网关路由级 `RequestRateLimiter`（`ipKeyResolver`，真实 TCP 对端口径）：`/api/auth/**` 20/s·burst 40、`/api/verification/**` 5/s·burst 10（阈值外置可配）；`AiTokenQuotaFilter` 对 `/api/ai/**` 做每日 Token 配额**原子计数**（次日零点 TTL）。

---

## 六、数据架构决策

| 决策   | 方案                           | 依据                  |
| ---- | ---------------------------- | ------------------- |
| 主键策略 | 技术主键（自增 ID）+ 业务标识（UUID）      | 自增 ID 性能好，UUID 对外友好 |
| 金额存储 | INT（单位：分）；**订单金额不变式 V2：`payAmount = totalAmount − discountAmount + postFee`**（旧口径无 `postFee` 项；`totalAmount` 仍 = Σ `order_item.subTotal`，运费不进商品小计、单列 `orders.post_fee` 下单快照，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)） | 避免浮点精度问题；「订单原价」与「应付」语义分离 |
| 地址快照 | 下单时复制地址到订单表                  | 防止用户修改地址影响历史订单可追溯性  |
| 商品快照 | 下单时复制商品名、价格、图片到订单项           | 保证历史订单数据准确          |
| 逻辑删除 | `deleted` 字段 + `@TableLogic` | 数据可恢复               |
| 扩展字段 | `expand_json` (JSON 类型)      | 预留灵活性，避免频繁改表        |
| 对象隔离 | DO / DTO / VO 三层隔离           | 禁止将 DO 直接返回给前端      |

---

## 七、部署架构（演进路线）

### MVP 阶段（当前）

```
Docker Compose 单机部署 (本地开发)
├── Nginx (反向代理 + 静态资源)
├── MySQL / Redis / Nacos / ES / RocketMQ / MinIO / 可观测性栈（compose 编排，见 docker-compose.yml）
├── 后端 15 个服务模块（由 IDE / 命令行启动，非 compose 托管）
│   gateway / authorization / user / merchant / item / cart / trade / pay /
│   logistics / search / ai / marketing / notification / verification / wallet
└── 前端（H5 + admin/seller 后台）

基础设施依赖（需提前部署）:
├── MySQL 9.3.0 — 所有业务数据
├── Redis 7.x — Token 缓存 / 分布式锁 / 限流
└── Nacos 2.x — 服务注册 + 配置中心
```

### 后续演进

- **V1.1**：
  - MySQL 读写分离，Redis Cluster
  - Sentinel 流量控制 + 熔断降级上线（trade 已落地，[ADR-028](../adr/028-trade-sentinel-wiring-over-resilience4j.md)）
  - XXL-Job 替换 `@Scheduled` 定时任务
  - SkyWalking 分布式链路追踪上线
  - RocketMQ 异步消息上线（订单超时延迟消息、秒杀削峰）
  - MinIO/OSS 对象存储上线（商品图片）
  - GitHub Actions CI/CD 流水线
  - Prometheus + Grafana + ELK 可观测性上线
  - Docker Compose 一键开发环境
- **V1.2**：
  - Nacos 集群，服务多实例 + 负载均衡
  - ElasticSearch 上线（商品搜索 + 向量检索）
  - Canal CDC MySQL → ES/Redis 同步
  - ✅ AI 商品助手（ReAct Agent + DeepSeek）
  - WebSocket 实时推送（订单状态通知）
- **V2.0**：
  - K8s 容器编排，CI/CD 流水线，ElasticSearch 集群
  - Seata 分布式事务（AT/TCC/Saga 三模式）
  - ✅ AI 客服系统 + 人机转接
  - ShardingSphere 分库分表（单表 > 500 万行时触发）
- **V2.5**（AI 生产就绪）：
  - AI 会话隔离（Per-User Redis ChatMemory）
  - SSE 流式响应 + ES 向量索引 RAG 基础管线
  - Langfuse 可观测性 + AI Gateway 初始版
- **V3.0**：
  - Milvus 向量数据库集群 + BGE-reranker 重排序
  - 多模型路由（DeepSeek/Qwen）+ 主-子Agent 编排
  - 知识图谱引擎（NebulaGraph）
  - RAG + KG 混合检索
  - RocketMQ 全面替换 RabbitMQ（高吞吐场景）
- **V3.1**（RBAC 授权体系）：
  - @PreAuthorize 方法级授权 + PermissionService(@Service("ss"))
  - 角色/权限管理 CRUD API (RoleController + PermissionController)
  - Admin 端点权限保护 (user:admin)
- **V3.2**（现代化 + DDD）✅ 已实现：
  - RestTemplate → RestClient (Spring Boot 3.2+ 标准)
  - Java Record 替代 Lombok @Data DTO
  - UserProfileService DDD 分层 + @Version 乐观锁
  - MinIO 头像上传/下载/多尺寸缩略图
- **V3.3**（买家-商家消息系统）：
  - WebSocket + STOMP 实时通讯（gateway 统一入口）
  - `chat_message` 消息持久化 + Redis 热缓存
  - RocketMQ 离线消息推送
- **V3.4**（店铺 AI）：
  - 商家知识库 CRUD + 自动分块 + ES 索引
  - 商家 AI 自动代答（限定本店知识库）
  - 商家对话模板管理
- **V3.5**（商品详情页京东标准改造）：
  - 商品卡片店铺信息 + 多图轮播 + 结构化SKU
  - 原价/到手价 + 立即购买 + 关注商家
  - 评论增强(图/视频/筛选) + 购物车店铺分组
- **V3.6**（秒杀+订单超时大厂优化）：
  - 秒杀: Redis Lua 预扣库存 + 用户限购 + RocketMQ 异步
  - 订单: RocketMQ 延迟消息(level=16/30min) 替代 @Scheduled 60s 轮询
- **V3.7**（秒杀漏斗模型+订单四层保障）：
  - 漏斗: 网关限流→Redis热点分片(10片)→MQ削峰→DB乐观锁
  - 订单: TTL主链路→flash_order_log事务表幂等→XXL-Job兜底→人工后台
- **V4.0**（SKU库存类型+优惠券多维度模型+RocketMQ迁移）：
  - SKU: stockType(LIMITED/UNLIMITED/PRESALE)+isHot+hotReason+salesTags
  - 优惠券: 五维模型(discountType×couponCategory×grantType×stockType×grabType)+priceInCents
  - MQ: RocketMQ 替代 RabbitMQ TTL/DLX(订单超时延迟+秒杀异步削峰)
- **V4.1**（统一订单中心+营销-交易职责分离）：
  - 订单: OrderTypeEnum(NORMAL/DIRECT/FLASH_SALE/PRESALE)+策略模式(OrderCreateStrategy+OrderCreateContext+OrderCreateStrategyFactory)
  - 秒杀下单: 营销Redis预扣→Feign调用trade-service InternalOrderController.createOrder 创建订单
  - 路由: gateway 精确路由兜底规则
- **V4.2**（优惠券使用强制校验）：
  - useCoupon: couponCategory+orderType+sellerId 三维校验（京东标准：PLATFORM通用/SHOP店铺限定/FLASH_SALE秒杀限定/EXCLUSIVE平台独占）
- **V4.4**（优惠券智能领取）：
  - smartClaim: 根据 grabType 自动路由 DB普通领取/RedisLua高并发通道
  - 付费券: trade订单→支付→grantAfterPayment 发放（京东标准）
- **V4.5**（优惠券去重保护）：
  - 所有券领取增加 Redis SISMEMBER 去重 + DB 双保险兜底

---

## 八、关键设计原则

1. **去形容词化**：不用「高可用」「易维护」等空洞词，用技术方案和数据说话
2. **大厂对标**：分层架构对标阿里，代码风格对标字节
3. **不过度设计**：中小服务使用精简版分层，超大复杂模块才开完整 DDD
4. **内部接口隔离**：`/internal/**` 路径的 Feign 内部调用不包装 `Result` —— 业务异常以真实 HTTP 状态码 + 极简 `{"code","msg"}` JSON 应答（ADR-004 修订，V5.3），调用方可区分业务失败与故障
5. **配置外挂**：通过 Nacos 统一管理，配置变更无需重启
6. **分布式事务最终一致**：MVP 阶段手动 Saga 补偿，V1.1 引入 Seata（详见 §2.5）
7. **定时任务可水平扩展**：当前 `@Scheduled` 仅适合单机，V1.1 引入 XXL-Job 支持分片和故障转移
8. **全链路可观测**：V1.1 引入 SkyWalking（追踪）+ Prometheus（指标）+ ELK（日志）三位一体
9. **数据同步实时化**：V1.2 引入 Canal CDC 实现 MySQL → ES/Redis 近实时同步
10. **安全纵深防御**：Nginx (TLS) → Gateway (JWT 验签 + 吊销校验：黑名单/用户级版本 + IP 限流 + 可信头剥离) → Manager (归属校验) → DB (PII 加密)，详见 12-Security-Model

---

## 九、架构合规审计（2026-06-20）

> 2026-10-03 复核：服务模块 18 个、网关路由 17 块 / **34 条 Path 谓词**、测试 **590 项**（0 失败）；RBAC/校验/资金/触达/商家等边界随六轮边界重构迁移（第六轮：merchant-service 拆分 + trade 平行入驻流程退役），本节数字已按代码现状更新；2026-10 增量：孤儿路由补挂（item/marketing/trade 共 7 条 Path 谓词）+ ai-service 重复路由块合并为单一 id + **`/api/shops/**`（店铺 shopId 口径）与 `/api/home/**`（此前遗留缺口关闭）补挂**；2026-10-05 复核（V5.9→V5.13 后）：网关路由 **18 块 / 38 条 Path 谓词**（task/activity/notes 三路由补挂）、测试 **1317 项**（0 失败）。

### 9.1 服务划分评估

| 维度     | 评估                                 | 对标                                   |
| ------ | ---------------------------------- | ------------------------------------ |
| 服务拆分粒度 | 18 个模块按业务域划分（2026-10 六轮边界重构后），粒度合理                  | 阿里中台标准（用户/商家/商品/交易/支付/物流/搜索/营销/集成）            |
| 共享库抽离  | ia-common（基础设施）+ ia-api（契约）+ ia-integration（外部集成）        | 阿里 MAR (Middleware Asset Repository) |
| 网关统一入口 | Spring Cloud Gateway + JWT 鉴权 + 限流 | 阿里 API Gateway                       |
| 注册中心   | Nacos 服务发现+配置中心                    | 阿里 Diamond + ConfigServer            |
| 负载均衡   | Spring Cloud LoadBalancer          | Ribbon 替代方案                          |

### 9.2 分层合规状态

| 规则                     | 状态      | 详情                                                          |
| ---------------------- | ------- | ----------------------------------------------------------- |
| Controller→Mapper 禁止   | ✅ 已修复   | History/HomeConfig/FlashSale 已抽取 Service 层                  |
| Controller→Redis 禁止    | ✅ 已修复   | HistoryService 封装 Redis 操作                                  |
| Service→Feign 禁止       | 🟡 已知豁免 | OrderServiceImpl 调 Feign（Saga 回滚需在事务内），标记为架构债               |
| Entity 不泄露至 Controller | 🟡 已知例外 | Admin/Internal 接口直接使用 Entity（无前端展示需求），外部接口已使用 VO/DTO        |
| 类不超过 300 行             | ✅       | UserServiceImpl 已拆分为 AuthService + SmsService + UserService |
| 方法不超过 50 行             | ✅       | 所有方法 ≥ 50 行已拆分                                              |
| 服务 Controller 上限 (≤8)  | ✅       | marketing/商家域拆出后，trade-service 降至 9 个（订单聚合域内，豁免见 §10.1）               |

### 9.3 Gateway 路由架构

> 依据 `gateway-service/src/main/resources/application.yml`（2026-10-02 第六轮核对 + 增量：孤儿路由补挂、ai-service 重复路由块合并为单一 id）。

```
/api/auth/**           → authorization-service （登录/注册/找回密码；IP 限流 20/s·burst 40）
/api/verification/**   → verification-service  （验证码签发公开端点；IP 限流 5/s·burst 10）
/api/notifications/**  → notification-service  （站内信中心，需登录）
/api/user/**           → user-service          （档案域）
/api/item/** + /api/home/** + /api/notes/** + /api/admin/home/** → item-service （商品/类目/评价 + 首页配置 + 首页装修管理 + 种草笔记）
/api/cart/**           → cart-service
/api/trade/** + /api/after-sale/** + /api/admin/after-sale/** + /api/admin/finance/** + /api/seller/finance/** → trade-service （订单/售后/结算；精路由须先于 /api/admin/**、/api/seller/** 声明）
/api/pay/** + /api/admin/pay/** → pay-service （支付/退款/对账；/api/admin/pay/** = `pay-admin-routes`，须先于 /api/admin/** 声明，ADR-035/036）
/api/logistics/**      → logistics-service
/api/coupon/** + /api/flash/** + /api/points/** + /api/sign/** + /api/task/** + /api/activity/** + /api/admin/coupon/** + /api/seller/coupon/** → marketing-service （营销域：优惠券/秒杀/积分/签到 + 每日任务/活动中心）
/api/wallet/**         → wallet-service        （余额账户，需登录）
/api/admin/roles/** + /api/admin/permissions/** → authorization-service （须先于 /api/admin/** 声明，ADR-018）
/api/admin/seller/**   → merchant-service      （商家审核；须先于 /api/admin/** 声明）
/api/admin/**          → user-service          （管理端兜底：用户/日志）
/api/chat/** + /ws/chat/** + /api/seller/knowledge/** + /api/ai/** → ai-service （客服域 + AI 助手；单一路由块 4 条 Path，须先于 /api/seller/** 声明）
/api/seller/** + /api/shop/** + /api/shops/** → merchant-service （商家域：入驻申请/店铺资料/公开店铺页/关注；/api/shop/**=sellerId 兼容口径，/api/shops/**=shopId 新口径，ADR-019）
/api/search/**         → search-service
```

**路由规则**：精确路径优先匹配，兜底路由放最后。与阿里 API Gateway 的 "精确→前缀→兜底" 策略一致。

**2026-10 增量**：补挂 7 条孤儿 Path 谓词（控制器存在但此前经网关不可达）：`/api/admin/home/**`（item-service）、`/api/admin/coupon/**` + `/api/seller/coupon/**`（marketing-service）、`/api/after-sale/**` + `/api/admin/after-sale/**` + `/api/admin/finance/**` + `/api/seller/finance/**`（trade-service）；重复的 `ai-service` 路由块已合并为单一 id（4 条 Path）；**结构规范化增量**：`/api/shops/**`（店铺 shopId 口径公开页，ADR-019）与 `/api/home/**`（item-service 公开首页配置）已补挂——前者此前仅白名单放行无路由，后者此前为遗留缺口，现均已可达。**V5.9→V5.13 增量（P2 增长线）**：`/api/task/**` + `/api/activity/**`（marketing-service：每日任务 M14 / 活动中心 M12-M13）与 `/api/notes/**`（item-service：种草笔记）三条路由补挂，现 **18 个路由块 / 38 条 Path 谓词**。JWKS（authorization-service `/oauth2/jwks`）不在网关路由内：网关以 `jwk-set-uri` 直连授权服务获取公钥。

**白名单方法级收紧（2026-10）**：浏览类公开路径（`/api/item/**`、`/api/home/**`、`/api/search/**`、`/api/shop/**`、`/api/shops/**`、`/api/flash/**`、`/api/coupon/template`、`/api/activity/**`、`/api/notes/**`）**仅 GET 匿名放行**，同路径写方法（如 `POST /api/flash/buy`、`POST /api/item/spu`、`POST|DELETE /api/shop/follow`）落到 `authenticated()`；`/api/ai/**` 匿名可用但受 `AiTokenQuotaFilter` 每日 Token 配额约束。**V5.11/V5.12 注**：`/api/activity/**` 与 `/api/notes/**` 进入 GET 匿名白名单（浏览类只读，写方法落 `authenticated()`）；`/api/task/**` 不进白名单（个人任务中心需登录）。**V5.6 注记（待办）**：`/api/item/**` 的 GET 匿名放行使**运费模板读取端点**（`/api/item/freight-template/**`）对匿名可达（内容为商家运费策略）；控制器侧已有 fail-closed 守卫兜底（归属校验 + 仅启用模板出参，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)），**白名单收敛为精确路径**列为待办。

**WS 握手鉴权（2026-10 修复，[ADR-026](../adr/026-ws-handshake-query-token-edge-swap.md)）**：`/ws/chat/**` **不进白名单**（维持 `authenticated()`）；浏览器 WS 握手无法自定义 `Authorization` 头，由 `WsHandshakeTokenFilter`（WebFilter，order **-900**，早于 Spring Security 安全链）把 URL query 中的 `token` 换写为 `Authorization: Bearer` 并**剔出该参数**（不落下游）后再进安全链——签名 + 时效 + 吊销校验（jti 黑名单 + 用户级版本，ADR-031）口径与普通接口完全一致；前端 SockJS/STOMP 的每个传输请求 URL 都需携带 `?token=<accessToken>`；路径范围由 `gateway.ws.token-paths` 配置（默认 `/ws/chat/**`）。

### 9.4 当前合规评分

| 维度       | 得分         | 说明                                                           |
| -------- | ---------- | ------------------------------------------------------------ |
| 服务拆分     | 10/10      | 18 个模块按域归位（2026-10 六轮边界重构）；服务↔域归属见 §3.1 |
| DDD 分层   | 9/10       | 核心链路合规，边缘接口有已知例外                                             |
| 网关路由     | 10/10      | 精确路径优先 + 兜底，18 块 / 38 条 Path 谓词覆盖全部服务（2026-10 第六轮 + 孤儿路由补挂 + `/api/shops/**`、`/api/home/**` + `/api/task|activity|notes/**` 增量）                                          |
| 异常处理     | 10/10      | 统一 GlobalExceptionHandler + ErrorCode 体系；`/internal/**` 真实 HTTP 状态码 + 极简 JSON（ADR-004 修订）                     |
| Feign 契约 | 10/10      | ia-api 集中管理，fallback 全覆盖（统一降级规范见 `ia-api` 的 `fallback/package-info.java`，ADR-011）；8 服务已开启 resilience4j 熔断（ADR-022） |
| 测试覆盖     | 6/10       | 1317 项测试（18 模块全部有测试，0 失败；含 H2 持久化集成测试）                                       |
| **综合**   | **9.4/10** | 生产就绪，测试覆盖待提升                                                 |

---

## 十、阿里微服务标准对照

### 10.1 模块拆分原则（阿里中台标准）

| 原则                | 阿里标准                                      | 本项目实施                                                        |
| ----------------- | ----------------------------------------- | ------------------------------------------------------------ |
| **单一职责**          | 每个服务一个限界上下文，不超过 3 个子域                     | ✅ 拆分 marketing-service 后全部合规                                 |
| **Controller 上限** | P0 核心服务 ≤ 8 controllers，P1 服务 ≤ 5         | ✅ trade-service 9 个但属同一聚合根（订单域），豁免                          |
| **共享库抽离**         | 公共组件抽离为 MAR (Middleware Asset Repository) | ✅ ia-common（基础设施）+ ia-api（契约）                                |
| **网关统一入口**        | API Gateway 统一鉴权/限流/路由                    | ✅ gateway-service: JWT + @RateLimit + 精确路由                      |
| **内部接口隔离**        | `/internal/**` 路径禁止外网访问，不包装 Result        | ✅ Gateway 拦截 + 真实 HTTP 状态码 + 极简 `{"code","msg"}` JSON（ADR-004 修订） |
| **Feign 契约集中管理**  | Feign 接口定义在独立模块，内置 fallback               | ✅ ia-api 模块：UserClient/SkuClient/LogisticsClient/OrderClient |
| **双主键策略**         | 技术主键 (自增ID) + 业务主键 (UUID)                 | ✅ 全部表：id BIGINT + xxx_id/no VARCHAR UNIQUE                   |

### 10.2 模块拆分决策记录

| 决策                           | 理由                                         | 结果                          |
| ---------------------------- | ------------------------------------------ | --------------------------- |
| 拆分 marketing-service         | 优惠券+秒杀是独立营销域，非交易核心                         | ✅ V2.2 完成                   |
| **不拆分** after-sale-service   | 售后与订单强耦合，取消→退款需同一事务                        | 保留在 trade-service           |
| **不拆分** settlement-service   | 结算依赖订单完成状态，跨服务会导致分布式事务                     | 保留在 trade-service           |
| **不拆分** points-service（历史决策，2026-10 推翻） | 签到/积分系统 < 3 controllers，拆分引入 Feign 开销 > 收益 | ~~保留在 user-service~~ → 积分账本/签到已迁 marketing-service（边界重构） |
| **不拆分** notification-service（历史决策，2026-10 推翻） | WebSocket 当前仅 1 个内部接口                      | ~~保留在 gateway-service~~ → notification-service 独立为触达域（站内信落库） |
| 拆分 wallet-service          | 余额独立为资金域（`wallet_account`，user.balance 冻结） | ✅ 2026-10 边界重构         |
| 签到/积分迁 marketing-service  | 营销域统一积分账本与签到活动                         | ✅ 2026-10 边界重构         |
| 聊天/知识库迁 ai-service        | 人工 IM 与商家知识库归客服域                        | ✅ 2026-10 边界重构         |
| RBAC 迁 authorization-service | 授权数据与凭证同域（ADR-018）                       | ✅ 2026-10 边界重构         |
| 拆分 merchant-service          | 商家域独立成服务：入驻申请/审核、店铺档案（`seller` 表所有权迁入；**V5.4 起 `seller` 瘦身为身份+生命周期 + `shop` 表 1:N，ADR-019**）、公开店铺页、店铺关注（Redis Set）；审批缝合自动授予 ROLE_SELLER（原缺口：审批后需管理员手动授角色，经 `AuthContextClient` 补上） | ✅ 2026-10 第六轮边界重构（V5.4 拆分跟进）         |
| trade 平行入驻流程退役 | SellerApplicationController / AdminApplicationController / ApplicationService / SellerApplicationEntity 等与商家域入驻流程平行重复，且网关 `/api/seller/**`、`/api/admin/**` 均路由不到 trade（早已不可达） | ✅ 2026-10 第六轮删除；`seller_application` 表保留在库（deprecated 数据备份） |
| 入驻端点定名 `/api/seller/apply` | 原 `/api/seller/register` 更名，消除「认证注册」误导；**前端契约变化**（调用方须改用 apply），文档一律按 apply 记录 | ✅ 2026-10（第六轮仲裁定稿）         |

### 10.3 服务清单（最终）

```
gateway-service           (API 网关)                        ✅ 0 controller（纯路由+过滤器）
authorization-service  (认证+授权: 登录/凭证/RBAC/权限缓存)  ✅ 5 controllers
user-service           (档案: 资料/地址/收藏/历史/会员等级)            ✅ 10 controllers
merchant-service       (商家域: 入驻/店铺/装修/关注)               ✅ 5 controllers
item-service           (商品/SKU/类目/评价/首页/运费模板/笔记)            ✅ 9 controllers
cart-service           (购物车)                           ✅ 2 controllers
trade-service          (订单/售后/结算)                     ✅ 10 controllers
pay-service            (支付/退款/对账: 微信/支付宝Mock/余额)          ✅ 4 controllers
logistics-service      (物流)                             ✅ 2 controllers
search-service         (搜索: ES+DB 双实现+向量推荐/联想)                 ✅ 2 controllers
ai-service             (AI 助手/客服/IM/知识库)             ✅ 5 controllers
marketing-service      (优惠券/秒杀/积分/签到/促销/任务/活动)               ✅ 15 controllers
notification-service   (短信/邮件/站内信)                   ✅ 2 controllers
verification-service   (验证码/人机验证)                    ✅ 2 controllers
wallet-service         (余额账户)                          ✅ 2 controllers
─── 共享内核 ───
ia-integration         (第三方集成: 微信支付/短信/邮件/极验/MinIO/OAuth)
ia-common              (异常/Result/工具/AOP/RocketMQ/CryptoKeyConfig)
ia-api                 (Feign 契约: 15 Client + DTO + fallback)
database               (SQL: Initialize 全量快照唯一真相源（54 表）；迁移机制已废除，ADR-045)
─────────────────────────────────────────────
18 模块 / 15 服务模块 / 约 240 端点（方法级映射实测，含 `/internal/**` 47 个）/ 1317 测试（2026-10-05）
```
