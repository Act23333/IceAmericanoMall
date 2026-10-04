# 冰美商城 (IcedAmericanoMall) — 项目文档地图

IceAmericanoMall 是一个 B2B2C 电商平台（类淘宝/京东），后端为 Java 21 + Spring Cloud 微服务，前端为 Next.js + React。本目录是项目文档的唯一可信规格来源（SSOT）。

## 文档体系（七域 + 决策与图示）

文档按「域」组织，每篇文档归属唯一域、唯一 Owner。`project-docs/` 根目录仅保留本地图，其余文档全部位于域目录内：

| 域 | 定位 | Owner |
| --- | --- | --- |
| `product/` | 需求域 — 做什么、为什么做 | product-manager |
| `contract/` | 契约域 — 前后端唯一 API 事实来源 | product-manager |
| `backend/` | 后端架构、领域模型、数据模型、安全、选型 | doc-engineer |
| `frontend/` | 前端选型、设计系统、实施方案 | doc-engineer |
| `quality/` | 开发流程、评审、测试 | doc-engineer |
| `collaboration/` | 任务分解、AI 协作规范、硬约束 | doc-engineer |
| `ops/` | 运维与开发环境笔记 | 主agent |
| `adr/` | 架构决策记录（跨域决策的 WHY） | java-architect（写）/ 全员（引用） |
| `drawio/` | 架构图 / ER / 时序 / UML / 安全 DFD | 主agent |

## product 需求域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [01-PRD.md](./product/01-PRD.md) | 产品需求文档 — 做什么、为什么做 | product-manager | 需求/产品设计变更时 |
| [16-Frontend-Product-Design.md](./product/16-Frontend-Product-Design.md) | 前端产品设计文档 — 品牌定位、信息架构与用户流程 | product-manager | 需求/产品设计变更时 |

## contract 契约域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [05-API-Specification.md](./contract/05-API-Specification.md) | 接口规格文档 — API 契约（SDD 唯一真相来源） | product-manager | 对外契约变更时 |

## backend 后端域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [02-Architecture.md](./backend/02-Architecture.md) | 系统架构文档 — 怎么做（宏观） | doc-engineer | 新增/下线服务、技术栈变更、ADR 落地时 |
| [03-Domain-Model.md](./backend/03-Domain-Model.md) | 领域模型文档 — 业务概念建模 | doc-engineer | 新增/变更聚合、端口、领域服务、领域事件时 |
| [04-Data-Model.md](./backend/04-Data-Model.md) | 数据模型文档 — 数据库设计 | doc-engineer | 新增/变更表结构时必须同步 |
| [12-Security-Model.md](./backend/12-Security-Model.md) | 安全威胁模型 — 威胁识别与防护策略 | doc-engineer | 认证/加密/脱敏方案变更时 |
| [13-AI-Technology-Selection.md](./backend/13-AI-Technology-Selection.md) | AI 技术选型文档 — LLM/向量数据库/Agent 方案对比 | doc-engineer | ai-service 技术栈变更时 |
| [14-Service-Config-Guide.md](./backend/14-Service-Config-Guide.md) | 服务配置指南 — 全部中间件和第三方平台搭建步骤 | doc-engineer | 新增中间件/配置项/端口时 |

## frontend 前端域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [15-Front-End-Technology-Selection.md](./frontend/15-Front-End-Technology-Selection.md) | 前端技术选型文档 — React/Next.js 全栈技术方案 | doc-engineer | 前端技术栈变更时 |
| [17-Frontend-Design-System.md](./frontend/17-Frontend-Design-System.md) | 前端设计系统文档 — 色彩/字体/组件/动效/布局规范 | doc-engineer | 设计系统变更时 |
| [18-Frontend-Implementation.md](./frontend/18-Frontend-Implementation.md) | 前端实施方案文档 — Phase 1-6 分阶段实施与 CI/CD | doc-engineer | 实施阶段推进时 |

## quality 质量域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [06-Workflow.md](./quality/06-Workflow.md) | 开发流程文档 — 怎么干、规范 | doc-engineer | 流程/门禁变更时 |
| [10-review.md](./quality/10-review.md) | 代码评审标准 — 怎么检查质量 | doc-engineer | 评审规则变更时 |
| [11-Test-Strategy.md](./quality/11-Test-Strategy.md) | 测试策略文档 — 测什么、怎么测、测到什么程度 | doc-engineer | 测试策略变更时 |
| [19-Test-Flow-Guide.md](./quality/19-Test-Flow-Guide.md) | 测试流程说明书 — 五层测试金字塔实操与速查表 | doc-engineer | 测试实操流程变更时 |

## collaboration 协作域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [07-Tasks.md](./collaboration/07-Tasks.md) | 任务分解文档 — 具体做什么 | doc-engineer | 任务状态变化时 |
| [08-agents.md](./collaboration/08-agents.md) | AI Agent 开发指令 — 如何写代码 | doc-engineer | 分层/命名/约束模板变化时 |
| [09-constraints.md](./collaboration/09-constraints.md) | 技术约束与反模式 — 不能做什么 | doc-engineer | 新增硬约束时（须有 ADR 或主 agent 依据） |
| [prompt.md](./collaboration/prompt.md) | 设计需求输入笔记 — 前端品牌世界观与交互原则 | doc-engineer | 设计输入/协作约定内容变更时 |

## ops 运维域

| 文件 | 说明 | Owner | 更新触发条件 |
| --- | --- | --- | --- |
| [开发密钥生成说明.md](./ops/开发密钥生成说明.md) | 开发环境 JWT 签名密钥（keystore）本地生成步骤 | 主agent | 中间件/密钥变更时 |

## adr 架构决策记录（跨域）

> 归属说明：ADR 是项目专属产物（迁移自 doc/adr，见 ADR-014），由 java-architect 在架构决策落地时新增，所有 agent 引用。完整索引见 [adr/README.md](./adr/README.md)。

## drawio 图示

> 归属说明：项目架构图 / ER / 时序 / UML / 安全 DFD（`.drawio` + `.mmd` 双格式），随架构演进由主 agent 维护。

## 与开发方法论的对应关系

| 方法论 | 对应文档 | 说明 |
| --- | --- | --- |
| **TDD** (测试驱动开发) | contract/05, collaboration/07, quality/11 | 接口契约 → 编写测试 → 实现 |
| **SDD** (规格驱动开发) | contract/05 | 精确的输入/输出契约即规格 |
| **DDD** (领域驱动设计) | backend/02, backend/03 | 限界上下文、聚合根、通用语言 |
| **BDD** (行为驱动开发) | product/01, contract/05, collaboration/07 | Given-When-Then 验收场景 |
| **SPC** (软件过程控制) | quality/06, collaboration/07, collaboration/09, quality/10 | 质量门禁、检查清单、阶段度量、约束检查 |
| **Security** (安全设计) | backend/12 | STRIDE 威胁建模、OWASP 评估 |
| **Agent** (AI 编码规范) | collaboration/08, collaboration/09 | AI 生成代码的行为约束和禁止模式 |
| **Event Storming** (事件风暴) | backend/02, backend/03 (来自 doc/methodology/02) | 领域事件 → 聚合 → 限界上下文 |
| **AI** (人工智能) | backend/13, backend/02 §二 | RAG + Agentic Search 技术选型 |
| **Frontend** (前端架构) | frontend/15, backend/02 §2.10 | React 19 + Next.js 15 全栈方案, RSC/ISR/SSG 渲染策略 |
| **Test** (测试流程) | quality/19 | 五层测试金字塔: 基础设施→单元→API冒烟→E2E→性能 |

## 按角色阅读路径

### 新人（全栈）

1. [product/01-PRD.md](./product/01-PRD.md) — 理解做什么
2. [backend/02-Architecture.md](./backend/02-Architecture.md) — 理解系统全貌
3. [backend/03-Domain-Model.md](./backend/03-Domain-Model.md) — 理解业务概念
4. [contract/05-API-Specification.md](./contract/05-API-Specification.md) — 理解前后端契约
5. [collaboration/09-constraints.md](./collaboration/09-constraints.md) — 记住不能做什么
6. [quality/06-Workflow.md](./quality/06-Workflow.md) — 了解怎么干

### 前端开发

1. [product/16-Frontend-Product-Design.md](./product/16-Frontend-Product-Design.md) — 产品设计
2. [frontend/15-Front-End-Technology-Selection.md](./frontend/15-Front-End-Technology-Selection.md) — 技术选型
3. [frontend/17-Frontend-Design-System.md](./frontend/17-Frontend-Design-System.md) — 设计系统
4. [frontend/18-Frontend-Implementation.md](./frontend/18-Frontend-Implementation.md) — 实施方案
5. [contract/05-API-Specification.md](./contract/05-API-Specification.md) — 对接契约

### 后端开发

1. [backend/02-Architecture.md](./backend/02-Architecture.md) — 架构与服务拆分
2. [backend/04-Data-Model.md](./backend/04-Data-Model.md) — 数据库设计
3. [contract/05-API-Specification.md](./contract/05-API-Specification.md) — 端点契约
4. [collaboration/09-constraints.md](./collaboration/09-constraints.md) — 硬约束与反模式
5. [backend/14-Service-Config-Guide.md](./backend/14-Service-Config-Guide.md) — 本地环境搭建
6. [quality/11-Test-Strategy.md](./quality/11-Test-Strategy.md) — 测试要求

### AI Agent

1. 根目录 [AGENTS.md](../AGENTS.md) — 全局行为规范与项目约定（入口）
2. [collaboration/08-agents.md](./collaboration/08-agents.md) — 分层模板、命名、测试模式
3. [collaboration/09-constraints.md](./collaboration/09-constraints.md) — 禁止模式与硬约束
4. [contract/05-API-Specification.md](./contract/05-API-Specification.md) — API 契约（唯一事实来源）
5. [collaboration/07-Tasks.md](./collaboration/07-Tasks.md) — 当前任务分解

## 项目阶段概览

```
MVP (Phase 1)          精细化运营 (Phase 2)        平台化 (Phase 3)           智能化 (Phase 4)
───────────────────    ──────────────────────     ──────────────────────────  ────────────────────────────
用户注册/登录           优惠券                      商家入驻流程                 知识图谱引擎
商品浏览                秒杀活动                    财务结算                    RAG+KG混合检索
购物车                  首页装修                    AI客服系统                  场景化搭配推荐
下单支付                商品搜索(ES)                多语言多币种
商家管理后台            积分系统
管理员后台              小程序端
                       AI商品助手
```

当前阶段：**2026-10 微服务边界重构完成 + 三轮审计修复与结构收口**（18 Maven 模块 = 15 服务 + 3 共享库 / 590 测试全绿）—— 六轮模块化 DDD 迁移：① 站内信→notification-service；② 积分→marketing-service、余额→wallet-service（新建）；③ 签到→marketing-service；④ 聊天/商家知识库→ai-service（客服域）；⑤ RBAC 归 authorization-service + 统一身份模型（ADR-018）；⑥ 商家域→merchant-service（新建，含入驻审批自动授角色），trade 平行入驻流程退役。验证码/人机验证唯一出口收敛于 verification-service；网关孤儿路由全量补齐。**Merchant/Shop 1:N 多店铺模型（Phase A）已随 V5.4 落地（ADR-019，seller 瘦身 + shop 表；Phase B 下游口径切换待排期）**；2026-10-03 完成全仓审计修复（交易链路资金安全、网关加固、数据库快照整治）、包结构规范化（ADR-023）与聚合分包（ADR-025）。下一阶段：前端迁移（端点路径与报文变化见 05-API-Specification V5.4.0 变更记录）。**多币种、店铺装修仍延期至 backlog。**

## 原始文档位置

本文件夹的文档提炼自 `doc/` 目录下的分析文档。原始文档保留了分析方法论说明、技术学习笔记和决策推演过程，如有疑问可回溯查阅。

## 全局约定

- 文档语言：中文
- 技术术语：保留英文（如 JWT、SKU、SPU），首次出现时加注中文解释
- 金额单位：统一使用「分」（整数存储，避免浮点精度问题）
- 标识策略：每张表同时拥有技术主键（自增ID）和业务唯一标识（UUID）

## 文档治理

- **归属单一**：每篇文档有唯一归属域与唯一 Owner（见各域表格）；同一事实只在一处定义，他处链接引用，不复制。
- **元信息头**：每篇文档头部引用块包含「归属域 | Owner | 更新触发条件」一行；`00-README.md`（本文件）作为地图本身不加。
- **SSOT**：[contract/05-API-Specification.md](./contract/05-API-Specification.md) 是 API 契约的唯一事实来源；其他文档提及端点时一律链接引用，不得复制端点表。
- **一致性规则**：遵循根目录 [AGENTS.md](../AGENTS.md) 的「文档-代码一致性规则」——文档定稿且符合规范时以文档为准生成代码；文档自相矛盾先讨论；文档与代码矛盾先修正文档再改代码。
