# IceAmericanoMall — 项目文档

## 这是什么？

`doc/` 包含两类内容：

| 目录                                 | 类型          | 说明                      |
| ---------------------------------- | ----------- | ----------------------- |
| **[methodology/](./methodology/)** | 分析方法论（通用模板） | 可复用于任何项目的分析流程，告诉你「怎么分析」 |
| **[knowledge/](./knowledge/)**     | 技术知识库       | 本项目遇到的技术问题、决策推演、学习笔记    |
| **[adr/](./adr/)**                 | 架构决策记录      | 关键设计决策的 WHY（为什么这样选）     |
| **[references/](./references/)**   | 参考资料        | 项目原始构想、命名规范、流程说明        |

## 文档在哪里？

**项目交付物**（PRD、架构文档、领域模型、API 规格等）在：
→ **[../project-docs/](../project-docs/00-README.md)**

`doc/` 是「分析方法论 + 知识库」，`project-docs/` 是「项目产物」。

## 阅读路径

### 如果你是新加入的开发者

1. **[references/IDEA.md](./references/IDEA.md)** — 了解项目起源与愿景
2. **[../project-docs/00-README.md](../project-docs/00-README.md)** — 浏览项目文档索引
3. **[../project-docs/01-PRD.md](../project-docs/01-PRD.md)** — 理解产品需求
4. **[../project-docs/02-Architecture.md](../project-docs/02-Architecture.md)** — 理解系统架构

### 如果你想学习分析方法论

1. **[methodology/00-analysis-pipeline.md](./methodology/00-analysis-pipeline.md)** — 从需求到代码的完整管线
2. 按顺序阅读 methodology/00 ~ 10
3. 对照 `project-docs/` 看冰美商城的实践产出

### 如果你想理解某个技术决策

→ **[adr/](./adr/)** — 每条 ADR 记录了「背景 → 决策 → 后果」

### 如果你遇到了类似的技术问题

→ **[knowledge/](./knowledge/)** — 按主题分类的技术深潜

## 与开发方法论的对应

| 方法论          | 对应 methodology/                                            | 对应 project-docs/                       |
| ------------ | ---------------------------------------------------------- | -------------------------------------- |
| **BDD**      | 01-requirements-elicitation                                | 01-PRD, 05-API-Specification           |
| **DDD**      | 02-event-storming, 03-use-case, 04-microservice, 05-domain | 02-Architecture, 03-Domain-Model       |
| **SDD**      | 06-api-design                                              | 05-API-Specification                   |
| **TDD**      | 10-test-strategy, 00-pipeline (Phase 11)                   | 07-Tasks, 11-Test-Strategy             |
| **SPC**      | 00-pipeline (Phase 11)                                     | 06-Workflow, 09-constraints, 10-review |
| **Security** | 09-security-modeling                                       | 12-Security-Model                      |

## 约定

- 文档语言：中文
- 技术术语：保留英文，首次出现加注中文
- 文档模板：methodology/ 下所有文件使用统一的五段式结构
