# ADR-010: 文档体系分域重组（七域结构 + 元信息头 + 治理规则）

## 背景

`project-docs/` 原为 00–19 扁平编号结构，前后端与流程文档混在一层：前端 4 篇（15/17/18）与后端 10 余篇混排、`16-Frontend-Product-Design` 实为产品设计却按受众归入前端、`05-API-Specification`（契约域）1900+ 行手写且已发生多轮文档-代码漂移、`prompt.md`/`开发密钥生成说明.md` 无归属。同时多 agent 协作体系落地（product-manager / doc-engineer / java-architect），需要与文档所有权对齐，否则出现双重所有权与文档三不管。

## 决策

1. **七域目录结构**（对标阿里 Confluence 空间树 / 字节飞书知识库的"按归属域组织"）：

```
project-docs/
├── 00-README.md          # 文档地图（唯一留在根目录）
├── product/              # 需求域（01-PRD、16-产品设计）—— Owner: product-manager，不分端
├── contract/             # 契约域（05-API-Specification）—— 前后端唯一正式边界
├── backend/              # 02/03/04/12/13/14 —— Owner: doc-engineer
├── frontend/             # 15/17/18 —— Owner: doc-engineer
├── quality/              # 06/10/11/19 —— Owner: doc-engineer
├── collaboration/        # 07/08/09 + prompt.md —— Owner: doc-engineer
└── ops/                  # 开发密钥生成说明 —— Owner: 主 agent
```

2. **编号保留在文件名中**（01-PRD.md 而非 PRD.md）：存量交叉引用量巨大，编号即稳定标识；阅读顺序职责移交 00-README 地图，文件名编号仅作历史锚点。
3. **元信息头三行**：每篇文档头部引用块内追加 `归属域 | Owner | 更新触发条件`——AGENTS.md「文档-代码一致性规则」按触发条件执行，防漂移从自觉变为机制。
4. **文档治理规则**（写入 00-README）：归属单一（每篇唯一 Owner 域）、SSOT（05 契约唯一事实来源，他处链接引用）、按触发条件同步。
5. **与 agent 编制对齐**：java-architect 对 project-docs 写权仅限 project-docs/adr/（ADR-009 已定），代码变更事实经【变更交接摘要】分派 PM（需求/契约域）与 doc-engineer（四域）同步；跨域冲突由主 agent 仲裁（星型拓扑）。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 维持扁平编号 | 零迁移成本 | 归属混乱、按受众而非归属分类、agent 所有权无法落地图 |
| 前后端两大目录 | 简单 | 契约域/质量域/流程文档无归属，治标不治本 |
| **七域结构（采用）** | 与 agent 所有权一一对应、读者按角色直达、治理规则可执行 | 一次性迁移成本（已付：22 git mv + 48 处链接修复） |

## 后果

- ✅ 读者按角色直达（README 提供按角色阅读路径）；agent 按域认领，杜绝双重所有权
- ✅ 元信息头让"何时必须同步"显式化，契约漂移可被触发条件捕获
- ⚠️ 契约域手写 1900 行的现状未变——长期方向为由 knife4j OpenAPI 生成 + Markdown 只留设计决策（待中间件可启动后推进）
- ⚠️ 外部若有人引用旧路径 `project-docs/05-API-Specification.md` 会 404（全仓已修正；worktree 外引用无法覆盖）
- ⚠️ 契约域对照 knife4j 的漂移审计依赖服务启动（中间件未起），暂缓

## 日期

2026-09-29

## 参考

- `project-docs/00-README.md`（文档地图 + 文档治理）
- `C:/Users/18279/.zcode/agents/{product-manager,doc-engineer,java-architect-and-development-expert}.md`（域所有权定义）
- `ADR-009`（包名归位——同日的"所有权对齐"姊妹决策）
