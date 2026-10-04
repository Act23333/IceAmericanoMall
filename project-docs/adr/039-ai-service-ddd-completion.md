# ADR-039: ai-service 四层 DDD 收口补完 —— 顶层游离包溶解与仓储归位

## 背景

ADR-038 宣布全仓四层收口完成，但 ai-service 收口时遗留了两类结构性欠账：

1. **顶层游离包未溶尽**：`agent/ config/ evaluation/ rag/ routing/ security/ service/ tool/` 仍在基础包顶层与四层并列（且 `domain/dto/` 名不副实），与 ADR-038 的结构约定冲突；
2. **仓储缺口**：会话（chat_message）与商家知识库（merchant_knowledge_base）两个聚合的读写由应用层 Manager 直用 MyBatis-Plus Mapper/PO/LambdaQueryWrapper，会话记忆清理由 Manager 直用 `RedisTemplate`，违反 ADR-038 的结构约定（PO↔聚合互转内联 RepositoryImpl、Redis 防腐实现 domain.port）。

## 决策

ai-service 按 ADR-038 结构约定补完收口，语义零变化：

1. **游离包逐类归位**（整类按依赖画像判定）：
   - LangChain4j 装配体 `agent/`（AiServices + 流式变体）→ `infrastructure/agent/`；LLM 工具防腐 `tool/`（只调 domain.port，不直调 Feign）→ `infrastructure/adapter/tool/`；
   - RestClient/外部服务耦合的 `rag/`（Embedding/Reranker/检索编排）→ `infrastructure/rag/`；Redis 耦合的 `routing/ModelRouter` → `infrastructure/routing/`；LangChain4j ChatModel 耦合的 `evaluation/LLMJudge`（仅离线评估，无生产调用方）→ `infrastructure/evaluation/`；
   - 纯规则的 `security/ContentSafetyFilter`、`service/` 中的访问策略 → `domain/service/`（Spring 注解移除，Bean 由 `infrastructure/config/AiDomainConfig` 注册）；`HandoffService`（单调用方）并入 `app/CsManager`；会话查询服务并入 `app/chat/ChatMessageManager`；
   - `config/` → `infrastructure/config/`；`domain/dto/` 逐类去向：领域概念（会话摘要/详情/命中结果/消息/订单概要/商品信息/用户画像）→ `domain/model/<聚合>/` 值对象（record），跨层传输载体（AiChatRequest/Response、ChatConversation/ChatMessage/Knowledge DTO）→ `app/dto/`；
   - WS 握手/鉴权拦截器（`ChatHandshakeInterceptor`/`StompAuthInterceptor`）随 WebSocket 装配整体落 `infrastructure/config/`，仅挪包、校验逻辑不动。
2. **仓储归位**：新增 `domain/repository/ChatMessageRepository`、`MerchantKnowledgeRepository` 两端口（入参/出参均为领域对象，按 `conversationId` / `id+sellerId` 业务键操作），实现落 `infrastructure/persistence/<聚合>/`，PO↔领域实体互转内联；新增领域实体 `ChatMessage`、`KnowledgeEntry`（纯 POJO，状态常量 2=生效/3=软删随实体）。应用层 Manager 不再出现 Mapper/PO/LambdaQueryWrapper/RedisTemplate。
3. **记忆清理端口化**：新增 `domain/port/AgentMemoryPort` + `infrastructure/adapter/RedisAgentMemoryAdapter`，会话删除时清两条 Agent 记忆（键布局经 `RedisChatMemoryStore` 唯一入口，`ai:memory:{agentType}:{userId}:{conversationId}`）。
4. **保留项（审计结论，非缺口）**：app 层直用 `infrastructure/agent`、`AiProperties`、`ModelRouter`、`RAGRetriever`——与 marketing 标杆先例一致（app 允许直用基础设施协作者/常量，Feign 亦直调）；`ModelRouter` 整类 Redis 耦合按整类归 `infrastructure/routing`，其纯启发式 `estimateComplexity` 若未来拆分可下沉 domain/service，本期不拆。

### 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 游离包整体并入最邻近四层（如 agent→domain/service） | 改动小 | 违反"领域零框架依赖"：LangChain4j/RestClient 耦合类进领域层即技术污染 |
| 为 Agent/RAG/ModelRouter 全部抽 domain.port | 依赖方向最纯 | Agent 接口即 LangChain4j 契约（@UserId/@MemoryId 语义），抽象层零收益；本期无第二实现，过度设计 |
| 维持 Mapper 直用，仅挪包 | 零风险 | Manager 持 PO/查询构造，聚合边界与 ADR-038 双轨，属应收口项 |

## 后果

- 正面：ai-service 与全仓结构约定对齐；领域层零框架依赖可机器验证（import 检查）；持久化契约按业务键表达；测试 49 → 52（覆盖只增不减），新增用例锁定详情分页上限、null-safe 合并、记忆键格式三处语义。
- 负面/风险：新增 6 个类（2 实体 + 2 端口 + 2 实现 + 1 适配器 + 1 端口），一次性心智负担；仓储实现内 lambdaQuery 链沿用既有测试限制（Mockito 无法打桩，靠 Manager 层端口打桩测试兜底，与其他服务口径一致）。
- 行为红线逐项保留：RAG `ai.rag.enabled` opt-in + fail-open、向量入库 search-service `knowledge_vectors`、检索 topK 注入客服回复；IM 发送者身份取自会话 Principal、WS 拦截器仅挪包；网关 `AiTokenQuotaFilter` 契约不变；6 个领域端口降级语义不变。

## 日期

2026-10-04

## 参考

ADR-038（全仓四层收口与结构约定）、ADR-023（adapter 结构）、ADR-025（聚合分包与用例族）、ADR-016（认证边界与客服域）
