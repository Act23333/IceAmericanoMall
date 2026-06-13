# 13 — AI 技术选型文档

> 为 V1.2 / V2.0 规划的两大 AI 能力：商家 AI 客服、商品 AI 助手。本文档记录技术选型的对比分析和最终决策。

---

## 一、场景分析

| 维度 | 商家客服（人工+AI） | 商品 AI 助手 |
|------|-------------------|-------------|
| **领域边界** | 封闭域 — 知识来自商家上传/编辑 | 开放域 — 跨商家、跨品类全平台 |
| **核心任务** | FAQ 问答、退换货政策、订单查询 | 商品检索、多条件对比、个性化推荐 |
| **检索复杂度** | 低 — 单步检索即可定位答案 | 高 — 需要多步推理+工具调用 |
| **人机协作** | 需要 — 低置信度自动转人工 | 不需要 — 纯 AI 交互 |
| **知识更新** | 低频率 — 商家手动维护 | 实时 — 依赖现有商品/库存/价格数据 |
| **典型问题** | "我的订单什么时候发货？" | "500以内适合夏天穿的透气跑鞋" |

**结论：两个场景本质不同，不能用同一套方案解决。**

---

## 二、推荐方案

### 2.1 商家 AI 客服：RAG + ES 混合检索

**为什么不是 Agentic Search：** 商家客服的知识边界明确——答案就在商家的 FAQ、商品信息、退换货政策里。不需要 Agent 去"探索"多个未知来源，需要的是精准检索+可靠回答。

**方案架构：**

```
用户问题 → 意图识别（FAQ/订单/商品/政策）
              ├── FAQ/政策类 → RAG 向量检索 (Milvus) → LLM 生成回答
              ├── 订单类 → 直接调用 trade-service 查订单状态
              ├── 商品类 → RAG 向量检索 (Milvus) + ES 关键词检索
              └── 低置信度 → 转人工（携带对话摘要）
```

**关键组件：**

| 组件 | 选择 | 说明 |
|------|------|------|
| 向量数据库 | Milvus | Java SDK 成熟，性能比 ES 向量检索高一个数量级 |
| 关键词检索 | ElasticSearch（复用 search-service） | 精确匹配商品名、SKU、订单号 |
| LLM | 通义千问 / DeepSeek API | 中文电商场景最优，成本可控 |
| Embedding | DeepSeek/通义千问 Embedding API | MVP 用 API（按量付费，零运维）；日调用超百万次后自建 bge-large-zh |
| Reranker | bge-reranker-v2-base | 对召回结果重排序，提升 Top-3 准确率（Reranker 调用频率低，API 即可） |

**人机转接策略：**
- AI 连续 2 轮回答置信度 < 0.7 → 自动转人工
- 用户消息包含"转人工"、"客服"、"投诉" → 立即转人工
- 转人工时携带：对话摘要 + 用户意图 + AI 已检索到的相关信息

### 2.2 商品 AI 助手：Agentic Search

**为什么不是纯 RAG：** 用户的问题不是单次检索能回答的——"有没有500以内适合跑步的轻便鞋，比我现在穿的飞马38透气性更好"需要：

1. 检索"跑步鞋"+"轻便"+"<500元"（多条件过滤）
2. 识别"飞马38"是实体（实体识别）
3. 查"飞马38"的透气性参数
4. 找透气性更好的替代品
5. 对比价格、库存、评价

这是典型的多步推理+工具调用场景。

**方案架构（ReAct Pattern）：**

```
用户问题 → LLM Agent（规划+推理）
              ├── Thought: 需要查跑步鞋+轻便+<500元
              │   Action: 调用 search-service 检索商品
              │   Result: 返回 23 条结果
              ├── Thought: 需要知道飞马38的透气性作为对比基准
              │   Action: 调用 item-service 查飞马38 详情
              │   Result: 透气性等级="优秀"、网面材质
              ├── Thought: 需要筛选透气性≥优秀的商品
              │   Action: 调用 RAG 检索 23 条结果的详细参数
              │   Result: 8 条符合
              ├── Thought: 需要对比价格和库存
              │   Action: 调用 item-service 批量查 SKU 库存/价格
              │   Result: 6 条有库存
              └── Thought: 可以给出最终推荐了
                  → 返回对比表格 + 推荐理由
```

**关键组件：**

| 组件 | 选择 | 说明 |
|------|------|------|
| LLM Agent 框架 | Spring AI 或自研轻量 Agent | Spring AI 与现有栈天然兼容；若功能简单也可自研 |
| 推理模式 | ReAct (Reasoning + Acting) | 行业标准，LLM 交替进行"思考→工具调用→观察" |
| 工具层 | 复用现有微服务 | search-service、item-service 的查询接口直接作为 Agent Tool |
| LLM | DeepSeek V3 / 通义千问 Qwen-Max | DeepSeek 推理能力强且成本低，适合多步推理 |
| Embedding | DeepSeek/通义千问 Embedding API | 与客服共用，MVP 用 API |

**Agent 工具清单：**

| 工具 | 对应服务 | 调用方式 |
|------|---------|---------|
| search_products | search-service | Feign `/api/search/product` |
| get_product_detail | item-service | Feign `/api/item/product/{id}` |
| get_sku_stock | item-service | Feign `/api/item/sku/{id}` |
| get_product_reviews | item-service (未来) | 商品评价摘要 |
| check_coupons | 营销服务 (未来) | 可用优惠券 |

### 2.3 为什么暂缓知识图谱

知识图谱擅长多跳实体推理（"商品A适用于场景B → 场景B需要配件C → C有型号D和E"），但：

- **电商 MVP 阶段用不到**：用户的查询通常在 1-2 跳内解决，Agentic Search 已经能覆盖
- **构建成本高**：需要实体抽取、关系抽取、图谱维护，ROI 不足以支撑 MVP
- **先跑通再优化**：RAG + Agentic Search 覆盖不了的长尾问题，才是引入知识图谱的时机

---

## 三、LLM 选型对比

| 维度 | 通义千问 Qwen-Max | DeepSeek V3 | GPT-4o |
|------|------------------|-------------|--------|
| 中文电商理解 | ★★★★★ | ★★★★☆ | ★★★☆☆ |
| 推理能力 | ★★★★☆ | ★★★★★ | ★★★★★ |
| API 价格 | ~¥2/百万 tokens | ~¥1/百万 tokens | ~¥70/百万 tokens |
| 数据安全 | 阿里云，国内合规 | 国内合规 | 需评估数据出境 |
| 工具调用 | ★★★★☆ | ★★★★☆ | ★★★★★ |
| **推荐** | 生产环境首选 | 高推理场景首选 | 备选/对比测试 |

**决策：主力使用 DeepSeek V3（推理能力强、成本最低），备选通义千问 Qwen-Max（中文理解更好）。通过 LLM Gateway 统一管理 API Key 和路由，支持 A/B 切换。**

---

## 四、向量数据库选型

| 维度 | Milvus | ElasticSearch 向量 | Qdrant |
|------|--------|-------------------|--------|
| 向量检索性能 | ★★★★★ | ★★★☆☆ | ★★★★★ |
| Java SDK 成熟度 | ★★★★☆ | ★★★★★ | ★★★☆☆ |
| 已有基础设施 | 需新部署 | ✅ 已部署 search-service | 需新部署 |
| 混合检索（向量+关键词） | ★★★★★ | ★★★★☆ | ★★★☆☆ |
| 运维成本 | 中等（独立服务） | 低（复用现有 ES） | 中等 |
| **推荐** | 生产环境首选 | MVP 阶段可先用 | 备选 |

**决策：MVP 阶段先用已有 ES 做向量检索（减少新基础设施），验证场景后再引入 Milvus。**

---

## 五、整体 AI 服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                        AI 服务层                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   ┌───────────────┐     ┌──────────────────┐                 │
│   │  AI Gateway    │     │  LLM Gateway      │                │
│   │ (路由/限流/Auth)│     │ (API Key管理/切换)   │               │
│   └───────┬───────┘     └────────┬─────────┘                 │
│           │                      │                           │
│   ┌───────▼──────────────────────▼─────────┐                 │
│   │              ai-service                  │                │
│   ├────────────────┬────────────────────────┤                 │
│   │  客服 Agent     │  商品助手 Agent         │                │
│   │  ┌──────────┐  │  ┌──────────────────┐  │                │
│   │  │ RAG 引擎  │  │  │ ReAct Agent      │  │                │
│   │  │ + 转人工   │  │  │ + 工具链编排      │  │                │
│   │  └──────────┘  │  └──────────────────┘  │                │
│   └────────────────┴────────────────────────┘                 │
│           │                      │                           │
│   ┌───────▼──────────────────────▼─────────┐                 │
│   │          共享基础设施                     │                │
│   │  ┌────────┐ ┌──────┐ ┌─────────────┐   │                │
│   │  │ Milvus │ │  ES  │ │ LLM API     │   │                │
│   │  │ (向量)  │ │(关键词)│ │ (DeepSeek等) │   │                │
│   │  └────────┘ └──────┘ └─────────────┘   │                │
│   └────────────────────────────────────────┘                 │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 六、分阶段落地计划

### Phase 2 — V1.2：商品 AI 助手

**优先级最高。用户感知最强，是差异化竞争力。**

- [ ] 部署 LLM Gateway（统一管理 API Key、限流、日志）
- [ ] 基于 ES 向量检索实现基础商品语义搜索
- [ ] 实现 ReAct Agent 框架（自研轻量版本，不引入 Spring AI 重依赖）
- [ ] 接入 search-service + item-service 作为 Agent Tool
- [ ] 支持「自然语言搜索 → 多条件筛选 → 商品对比」完整链路
- [ ] 接入 DeepSeek/通义千问 Embedding API（MVP 不自建，按量付费）

**技术栈 MVP 版：ES 向量检索 + 自研 Agent + DeepSeek API（含 Embedding API）**

### Phase 3 — V2.0：商家 AI 客服

**依赖商家后台先有内容管理能力。**

- [ ] 部署 Milvus 集群
- [ ] 商家知识库管理（上传 FAQ、产品手册、退换货政策）
- [ ] RAG 引擎实现（文档切片 → Embedding → 向量检索 → 重排序 → LLM 生成）
- [ ] 人机转接系统（低置信度检测 + 对话摘要生成）
- [ ] 接入 trade-service 查询订单状态
- [ ] 部署 bge-reranker-v2 重排序服务（Reranker 也可先用 API，需评估延迟）

### Phase 4 — V3.0+：知识图谱 + Embedding 自建

**触发条件（满足任一项即可启动）：**
- 日 Embedding 调用超百万次，API 成本超过自建
- 对检索延迟要求 < 50ms，API 网络往返无法满足

**当平台商品量和用户量达到一定规模后，RAG + Agentic Search 的"单跳检索"无法满足深层推理需求。知识图谱是必然演进方向。**

触发条件（满足任一项即可启动）：
- 平台 SKU 超过 10 万，跨品类推荐需求增长
- 用户频繁问跨实体的关系类问题（"A 商品适合什么场景？该场景还需要什么配件？"）
- RAG 召回率在关系类查询上持续低于 80%

核心任务：
- [ ] 知识图谱 schema 设计（商品、品类、品牌、场景、属性、配件关系）
- [ ] 实体识别 + 关系抽取 pipeline（基于商品标题、描述、评价 NLU）
- [ ] 图数据库选型（Neo4j / NebulaGraph）与部署
- [ ] RAG + KG 混合检索：向量检索负责"模糊匹配"，KG 负责"精确关系推理"
- [ ] 场景化推荐引擎（"买手机 → 需要贴膜+壳+耳机"这类搭配推荐）
- [ ] 知识图谱可视化后台（运营/商家查看实体关系，手动修正错误抽取）

**技术栈预选：NebulaGraph（分布式，适合大规模图）或 Neo4j（单机友好，MVP 起步快）**

---

## 七、与现有服务的关系

| 现有服务 | AI 场景中的角色 |
|---------|---------------|
| `search-service` | 商品 AI 助手的核心 Tool — 关键词+语义搜索 |
| `item-service` | 商品 AI 助手的 Tool — 查详情、SKU、库存、价格 |
| `trade-service` | 商家 AI 客服的 Tool — 查订单状态（需 `/internal/` 接口） |
| `user-service` | 用户身份验证、地址查询（Agent 上下文） |
| `gate-service` | AI 请求入口限流、JWT 验证 |
| `ia-common` | AI 服务的 Result 包装、异常处理 |

**关键规则：AI 服务通过 Feign 调用其他服务的 `/internal/` 接口，不绕过 Gateway。**

---

## 八、参考依据

- ReAct Pattern: Yao et al., "ReAct: Synergizing Reasoning and Acting in Language Models" (ICLR 2023)
- RAG: Lewis et al., "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks" (NeurIPS 2020)
- Spring AI: https://spring.io/projects/spring-ai
- Milvus: https://milvus.io
- bge-large-zh: BAAI general embedding (MTEB leaderboard, Chinese SOTA)
- 通义千问 API: https://help.aliyun.com/zh/model-studio
- DeepSeek API: https://platform.deepseek.com/api-docs
