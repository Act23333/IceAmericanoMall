# 冰美商城 (IcedAmericanoMall) — 项目文档索引

## 阅读顺序

| 序号  | 文档                                                                             | 说明                               | 面向角色        |
| --- | ------------------------------------------------------------------------------ | -------------------------------- | ----------- |
| 1   | [01-PRD.md](./01-PRD.md)                                                       | 产品需求文档 — 做什么、为什么做                | 全员          |
| 2   | [02-Architecture.md](./02-Architecture.md)                                     | 系统架构文档 — 怎么做（宏观）                 | 架构师、开发      |
| 3   | [03-Domain-Model.md](./03-Domain-Model.md)                                     | 领域模型文档 — 业务概念建模                  | 开发、领域专家     |
| 4   | [04-Data-Model.md](./04-Data-Model.md)                                         | 数据模型文档 — 数据库设计                   | 后端开发、DBA    |
| 5   | [05-API-Specification.md](./05-API-Specification.md)                           | 接口规格文档 — API 契约                  | 前后端开发       |
| 6   | [06-Workflow.md](./06-Workflow.md)                                             | 开发流程文档 — 怎么干、规范                  | 开发、PM       |
| 7   | [07-Tasks.md](./07-Tasks.md)                                                   | 任务分解文档 — 具体做什么                   | 开发、PM       |
| 8   | [08-agents.md](./08-agents.md)                                                 | AI Agent 开发指令 — 如何写代码            | AI Agent、开发 |
| 9   | [09-constraints.md](./09-constraints.md)                                       | 技术约束与反模式 — 不能做什么                 | 开发、评审者      |
| 10  | [10-review.md](./10-review.md)                                                 | 代码评审标准 — 怎么检查质量                  | 开发、评审者      |
| 11  | [11-Test-Strategy.md](./11-Test-Strategy.md)                                   | 测试策略文档 — 测什么、怎么测、测到什么程度          | 开发、QA       |
| 12  | [12-Security-Model.md](./12-Security-Model.md)                                 | 安全威胁模型 — 威胁识别与防护策略               | 开发、安全评审     |
| 13  | [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)               | AI 技术选型文档 — LLM/向量数据库/Agent 方案对比 | 架构师、开发      |
| 14  | [14-Service-Config-Guide.md](./14-Service-Config-Guide.md)                     | 服务配置指南 — 全部中间件和第三方平台搭建步骤         | 运维、开发       |
| 15  | [15-Front-End-Technology-Selection.md](./15-Front-End-Technology-Selection.md) | 前端技术选型文档 — React/Next.js 全栈技术方案  | 前端开发、架构师    |

## 与开发方法论的对应关系

| 方法论                       | 对应文档                                                     | 说明                                           |
| ------------------------- | -------------------------------------------------------- | -------------------------------------------- |
| **TDD** (测试驱动开发)          | 05-API-Specification, 07-Tasks, 11-Test-Strategy         | 接口契约 → 编写测试 → 实现                             |
| **SDD** (规格驱动开发)          | 05-API-Specification                                     | 精确的输入/输出契约即规格                                |
| **DDD** (领域驱动设计)          | 02-Architecture, 03-Domain-Model                         | 限界上下文、聚合根、通用语言                               |
| **BDD** (行为驱动开发)          | 01-PRD, 05-API-Specification, 07-Tasks                   | Given-When-Then 验收场景                         |
| **SPC** (软件过程控制)          | 06-Workflow, 07-Tasks, 09-constraints, 10-review         | 质量门禁、检查清单、阶段度量、约束检查                          |
| **Security** (安全设计)       | 12-Security-Model                                        | STRIDE 威胁建模、OWASP 评估                         |
| **Agent** (AI 编码规范)       | 08-agents, 09-constraints                                | AI 生成代码的行为约束和禁止模式                            |
| **Event Storming** (事件风暴) | 02-Architecture, 03-Domain-Model (来自 doc/methodology/02) | 领域事件 → 聚合 → 限界上下文                            |
| **AI** (人工智能)             | 13-AI-Technology-Selection, 02-Architecture §二           | RAG + Agentic Search 技术选型                    |
| **Frontend** (前端架构)       | 15-Front-End-Technology-Selection, 02-Architecture §2.10 | React 19 + Next.js 15 全栈方案, RSC/ISR/SSG 渲染策略 |

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

当前阶段：**V2.5 安全+CI+功能收尾**（14 模块含 database / 133+ 端点 / 150+ 测试）—— 在 V2.4 观测+测试基础上完成安全加固（keystore-git 修复 / JWT 密钥轮换 / SM2 / 空闲超时 / PII 加密 / RabbitMQ TLS / MinIO presigned）、CI/CD Pipeline（Stage 2 Docker / Stage 3 CD + smoke / Checkstyle）、功能收尾（首次/回归登录奖励 / 管理员详细统计 / 商家浏览分析）。下一阶段：Phase 5 前端 / V3.0 知识驱动。**RBAC、积分商城、任务中心、店铺装修、多币种已延期至 V3.0+ backlog。**

## 原始文档位置

本文件夹的文档提炼自 `doc/` 目录下的分析文档。原始文档保留了分析方法论说明、技术学习笔记和决策推演过程，如有疑问可回溯查阅。

## 约定

- 文档语言：中文
- 技术术语：保留英文（如 JWT、SKU、SPU），首次出现时加注中文解释
- 金额单位：统一使用「分」（整数存储，避免浮点精度问题）
- 标识策略：每张表同时拥有技术主键（自增ID）和业务唯一标识（UUID）
