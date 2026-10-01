# ADR-014: 模板纯化 —— ADR 与图示归位 project-docs，doc/ 只留通用

## 背景

仓库存在两套文档树：`doc/`（定位为「大厂标准项目的通用模板」——methodology 分析方法论、knowledge 知识库，目标是项目完成后沉淀为可复用模板）与 `project-docs/`（本项目专用规格文档，V5.2 已按七域重组，见 ADR-010）。但 `doc/adr/`（001–013 全部是本项目架构决策）与根目录 `drawio/`（本项目架构图/ER/时序/安全 DFD）实为**项目专属产物**，放在通用模板树中违背其定位；`doc/methodology/` 管道图与文档中写死的 `project-docs/...` 路径也使模板无法跨项目复用。

## 决策

1. **迁移**：`doc/adr/` → `project-docs/adr/`（13 篇 + 索引 README，git mv 保留历史）；根目录 `drawio/` → `project-docs/drawio/`。`doc/` 只保留通用内容（methodology/knowledge/references）。
2. **methodology 参数化**：所有写死的 `project-docs/...` 路径替换为 `{project-docs}` 占位符（Markdown 链接降级为代码行内码，避免模板态死链）；ASCII 框图替换后按显示宽度（CJK=2）重排对齐。实例化项目时全局替换 `{project-docs}` 为实际文档目录即可。
3. **所有权不变**：ADR 由 java-architect 在架构决策落地时新增（对 project-docs 的唯一写权即为 `project-docs/adr/`，见 ADR-009/010 的 agent 分工）；`drawio/` 由主 agent 维护；doc-engineer 域外禁改清单同步更新。
4. **doc/README.md** 保留为 doc/ 树索引，adr 条目改为指向 `../project-docs/adr/` 的跨树指针，并明确「doc/ = 通用模板、project-docs/ = 项目产物」的定位声明。

## 备选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| 维持现状（混合树） | 零迁移成本 | 模板不可复用；「通用」与「专属」边界永久模糊 |
| ADR 留 doc/、仅迁 drawio | 改动小 | 模板中仍含项目决策，纯化目标落空 |
| **全部迁入 project-docs（采用）** | doc/ 成为可整体拷贝的通用模板；项目产物单树自洽 | 存量引用需修正（约 30 处，已全部完成） |

## 后果

- ✅ `doc/` 目录实现「拷走即用」：方法论 + 知识库不含任何本项目路径（占位符 `{project-docs}` 参数化）
- ✅ 项目专属产物（决策记录 + 图）单树自洽于 project-docs，agent 写权边界与目录一致
- ⚠️ 旧引用 `doc/adr/...`、根 `drawio/...` 已失效——仓内全部修正；仓外引用（如有）需自行更新
- ⚠️ `doc/` 内 `references/`、`项目代码/`、`项目的分析流程/` 三个目录本轮未动（不在本次决策范围），模板纯化是否覆盖它们留待后续评估

## 日期

2026-09-30

## 参考

- `doc/adr/010`（原编号：文档体系分域重组）→ 现 `project-docs/adr/010-document-domain-reorganization.md`
- `project-docs/00-README.md`（文档地图 + 文档治理）
- `C:/Users/18279/.zcode/agents/*.md`（agent 写权边界定义）
