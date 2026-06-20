# IceAmericanoMall 冰美式商城 — 简历项目描述

## 项目概述

基于 **Spring Cloud Alibaba 微服务架构**的 B2B2C 电商平台（对标淘宝/京东模式），实现从用户注册到商家结算的完整交易闭环。后端 **14 个模块、115+ API 端点、82 单元测试**，支持千万级用户扩展。

**技术栈**: Java 21 · Spring Boot 3.5.4 · Spring Cloud 2025.0.1 · Spring Cloud Alibaba 2025.0.0.0 · MyBatis-Plus 3.5.11 · MySQL 9.0 · Redis 7 · Nacos 2.4 · ElasticSearch 7.17 · RabbitMQ 3.13 · XXL-Job 2.4 · Sentinel · Seata · MinIO · Docker Compose · Spring AI + LangChain4j · JWT (RS256) · OAuth2 · Knife4j

---

## 主要工作

### 1. 微服务架构设计与治理
- 按业务域拆分为 **12 个独立微服务**（gateway/auth/user/item/cart/trade/pay/logistics/search/marketing/ai-service），遵循阿里 DDD 分层体系（Controller→Manager→Service→Mapper）
- 使用 **Nacos** 实现服务注册发现与配置中心，支持配置热更新
- 使用 **OpenFeign + Spring Cloud LoadBalancer** 实现声明式服务调用，Feign 接口统一管理在 ia-api 模块，内置 fallback 降级
- 通过 **Spring Cloud Gateway** 集成统一认证（JWT OAuth2）+ 精确路由分发（13 条路由规则）+ IP/手机号维度限流

### 2. 分布式稳定性保障
- 使用 **Sentinel** 对核心交易接口配置 QPS 限流（100 QPS）和异常比例熔断（50%），`@ConditionalOnProperty` 实现 Sentinel / 自研 `@RateLimit` AOP（Redis Lua 脚本）双模式可切换
- 采用 **Seata AT 模式**管理「下单→扣库存→清购物车」分布式事务，降级方案为手动 Saga 补偿（`OrderManager` 编排：失败时 Feign 回滚库存）
- **XXL-Job** 替换 `@Scheduled` 实现分布式任务调度（订单超时取消/支付超时关闭），`@XxlJob` + `@Scheduled` 双模并存，开发环境无需部署调度中心

### 3. 高并发查询优化
- 使用 **ElasticSearch** 构建商品搜索引擎（IK 分词 + 关键词高亮），`@ConditionalOnProperty` 实现 ES / MySQL LIKE 降级双模式
- 使用 **Redis** 缓存高频数据：Bitmap 实现每日签到（`BITFIELD` 批量读取月度数据）、Lua 脚本实现原子化限流计数（`rate_limit.lua`）
- 多级分类使用 **内存递归树构建**（一次查询全部 category → 按 parentId 分组 → 递归填充 children），避免 N+1 查询

### 4. 异步消息解耦
- 使用 **RabbitMQ** 实现核心领域事件异步化：订单创建 → 支付服务消费自动创建支付单、支付成功 → 物流服务消费自动创建物流记录、订单发货 → 日志归档
- 设计 `DomainEvent` 基类 + 4 种事件子类型（`OrderCreatedEvent` / `PaymentSucceededEvent` / `OrderShippedEvent` / `OrderStatusChangedEvent`），Jackson2Json 序列化
- 降级方案：MQ 关闭时保留同步 Feign 调用链路，`@ConditionalOnProperty(rabbitmq.enabled)` 控制切换

### 5. 防止重复下单（幂等性设计）
- 下单前校验购物车项 + 库存（`SELECT ... FOR UPDATE` 行锁），**乐观锁**（`@Version`）防止并发超卖
- 支付订单生成前查 `pay_order` 表进行**数据库唯一约束幂等校验**：已存在 SUCCESS 状态直接返回、已存在 PENDING 状态复用流水
- 支付回调采用**状态机 + 事务**保证幂等：`status == PENDING_PAY` 才更新为 SUCCESS，重复回调直接返回

### 6. AI 智能导购（Spring AI + LangChain4j）
- 基于 **Spring AI 1.0.0-M5** + **LangChain4j 1.0.0-beta1** 构建 AI 商品助手和智能客服
- 使用 LangChain4j `AiServices` 框架生成 **ReAct Agent** 代理（LLM 推理→工具选择→结果汇总），`@Tool` 注解将 search-service / trade-service 封装为 Agent 工具
- **DeepSeek V3** 作为 LLM（OpenAI 兼容协议），`MessageWindowChatMemory` 支持 10-20 轮对话上下文
- `@ConditionalOnProperty(ai.enabled)` 实现 AI 功能可插拔

### 7. 第三方集成与安全
- 集成 **微信支付 API v3**（Native 扫码支付）：证书签名、回调验签、幂等处理、超时关闭
- 集成 **阿里云短信 SDK** + **极验人机验证**，异步发送不阻塞主流程
- **AES-256-CBC** 静态加密方案设计（PII 字段：手机号/地址/姓名，V1.1 计划实施）
- JWT **RS256 非对称加密**签名 + JWKS 端点公钥暴露 + Refresh Token 轮换 + 退出黑名单

---

## 项目亮点

| 亮点 | 量化 |
|------|------|
| 微服务模块 | 14 个 Maven 模块，12 个独立部署服务，评分 9.4/10（阿里标准） |
| API 端点 | 115+ 个 RESTful 接口（含 15 个内部 Feign 接口） |
| 单元测试 | 82 个，0 失败，16 个测试类覆盖 7 个模块 |
| 数据库 | 11 张业务表 + 10 个版本迁移 SQL，双主键策略（自增+UUID） |
| 技术集成 | 15+ 种中间件/第三方服务，全部 `@ConditionalOnProperty` 可插拔 |
| AI 能力 | Spring AI + LangChain4j 双引擎，ReAct Agent + @Tool 工具链 |
