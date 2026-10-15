# ADR-053: 文档 Owner 按「文档性质」分权 + 规范的双层模型

## 背景

`ADR-010` 把文档体系重组为七域结构，并确立「每篇文档唯一归属域与唯一 Owner」。但在角色从 3 个扩展到 4 个（新增 frontend-engineer）并细化职责后，审计发现八处重叠或冲突，其中三处是结构性的：

1. **决策者与编写者错配**。`backend/02-Architecture.md`、`backend/13-AI-Technology-Selection.md`、`frontend/15-Front-End-Technology-Selection.md` 的 Owner 都是 doc-engineer，但：

   - doc-engineer 的定位是文档工程师，其角色定义中**没有任何技术判断依据**（无技术栈知识、无选型方法论、无架构原则）；
   - 真正持有这些知识的是 java-architect（23 节 DDD 架构规范）与 frontend-engineer（五平台技术栈）；
   - 而这两者**没有对应文档的写权**（java-architect 仅有 `adr/`，frontend-engineer 为零）。

   结果：**要求一个角色编写它没有能力判断的文档**，决策理由（为什么选 A 不选 B）在「变更交接摘要」这一层丢失 —— 而 ADR 与选型文档的全部价值恰是理由。

2. **`13-AI-Technology-Selection.md` 的决策真空**。LLM / 向量库 / Agent 选型，四个角色（product-manager、java-architect、doc-engineer、frontend-engineer）**没有一个**具备该能力域，形成「决策无人负责、只有编写有人负责」。

3. **`00-README.md` 的写权自相矛盾，且违反自身治理规则**。product-manager 的角色定义声称该文件在其「唯一写权」内（含「项目阶段概览」节），doc-engineer 声称写入「仅限索引同步」，两者又都表述为「共同义务」。而 `00-README.md` 的治理规则第 166 行明写「每篇文档有唯一归属域与唯一 Owner」—— **「共同义务」直接违反该规则**。主 agent 可以并行派发子 agent，两端同时编辑同一张索引表是真实风险。

此外还有五处次要问题：`adr/` 归属覆盖不到非后端决策（仓库内实际存在 `010` 文档结构决策、`014` 文档模板决策）；编码规范在 `collaboration/08-agents.md` 与 `java-architect` 角色定义中**双源**且无优先级规则；漂移审计范围在 product-manager 与 doc-engineer 之间重叠；「文档定稿时以文档为准」与「从代码提取事实源再比对」两种审计方向相反；域分工描述在三处重复。

**触发点**：新增 frontend-engineer 时，其「文档写权 = 无」与 doc-engineer「前端三篇全部归我」的组合，把上述错配暴露成必须解决的问题。

## 决策

### 一、Owner 按「文档性质」确定，域只决定文档位置

| 性质 | 文档 | Owner |
| --- | --- | --- |
| ① 决策记录（产物＝一个判断） | `backend/02`、`backend/13`、`frontend/15`、`frontend/17`、`adr/` | **决策者本人**：后端/架构/AI → java-architect；前端/设计系统 → frontend-engineer；文档结构与模板 → doc-engineer |
| ② 契约与需求（产物＝一个约定） | `product/01`、`product/16`、`product/02`、`contract/05` | product-manager |
| ③ 派生描述与流程（产物＝从事实源派生） | `backend/03`、`backend/04`、`backend/12`、`backend/14`、`frontend/18`、`quality/06`、`quality/10`、`quality/11`、`quality/19`、`collaboration/07`、`collaboration/08`、`collaboration/09` | doc-engineer |
| ④ 结构与索引 | `00-README.md`、目录重组 | doc-engineer（独有） |

据此，doc-engineer 的定位从「所有文档的编写者」调整为：**派生类文档的 Owner ＋ 全库结构与索引的唯一持有者 ＋ 漂移审计者 ＋ 格式与模板的裁判**。

### 二、`13-AI-Technology-Selection.md` 暂由 java-architect 兼任

该能力域当前无角色覆盖。先由 java-architect 兼任其决策与编写，待 AI 选型进入实际排期时再评估是否拆出专用角色。`00-README.md` 中该行标注为「java-architect（暂兼）」。

### 三、`00-README.md` 的 Owner 与节级例外

- **Owner = doc-engineer**（索引、文档地图、全局约定）。
- **唯一节级例外**：`## 项目阶段概览` 归 product-manager。两节物理分开，互不越界。
- 废除 product-manager 与 doc-engineer 之间的「共同义务」表述 —— 它与「唯一 Owner」规则不相容。

### 四、规范采用双层模型（解决编码规范双源）

角色定义（`~/.dsh/agent-roles/*/SKILL.md`）与项目文档（`collaboration/08-agents.md`）**不是竞争关系，而是不同抽象层级**：

| 层 | 载体 | 管什么 |
| --- | --- | --- |
| 方法论层 | 角色 `SKILL.md` | 原则与模式：DDD 分层原则、为何小聚合、为何不按技术主键 `save` —— **通用、可跨项目复用** |
| 项目层 | `AGENTS.md` → `collaboration/08-agents.md` → `collaboration/09-constraints.md` | 本项目的具体写法、实际包名与目录、边界与禁止项 —— **项目特化与收窄** |

**冲突裁决规则**：

1. 项目层若是方法论层的**有意收窄/特化** → **项目层生效**；
2. 项目层若与方法论层的**原则相悖**（矛盾而非收窄）→ 视为**缺陷**，上报主 agent，由对应方法论持有者判定修正哪一侧；
3. 项目层**未规定**的事项 → 回落到方法论层。

**去重要求**：方法论层不得逐字复制项目层已有的具体内容（包名、目录树、实际禁用项），只保留原则与理由；项目层不得重述方法论。两侧各只写一份，避免「改一处要记着改两处」。

> 现状偏差已记录：`java-architect` 的角色定义中混有项目事实（`com.company.project` 示例包根、authorization-service / user-service 迁移状态、`project-docs/...` 路径），按本节应逐步迁往项目层。这是后续清理项，不阻塞本 ADR 生效。

### 五、漂移审计的方向按性质二分

- ①② 类（决策与契约）是**规格**：代码向文档对齐，漂移＝代码没跟上；
- ③ 类（派生描述与流程）是**代码的镜像**：文档向事实源对齐，漂移＝文档没跟上。

不先分清这一点，漂移审计的结论方向就是相反的。

## 备选方案

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| **A. 维持 doc-engineer 统一编写**（变更前） | 格式与索引单点、代码 agent 上下文开销小、模板一致 | 编写者无决策能力；改一处 `contract/05` 需读 249 KB、改 `04` 需读 77 KB；doc-engineer 独占 16 篇约 653 KB 成为串行瓶颈；决策理由在摘要层丢失 |
| **B. 谁改代码谁写文档，doc-engineer 只审计** | 写者上下文完整、无往返、无信息丢失 | 上下文成本转移到最贵的角色（代码 agent 需额外加载文档模板与被改文档）；模板与索引一致性失守；并发编辑同一文档 |
| **C. 按文档性质分权**（本决策） | 决策由决策者写，理由不丢失；派生文档保持格式统一；doc-engineer 聚焦结构与审计 | 需要一次性的归属重排；所有权分散到 4 个角色，依赖清晰的转派规则 |
| D. 按文档大小分权（大文档归技术角色） | 缓解上下文成本 | 划分标准与职责能力无关，产生任意边界 |

## 后果

**正面**

- 决策类文档由具备判断力的角色编写，权衡理由得以保留。
- doc-engineer 的职责收窄为它真正擅长的部分（结构、索引、格式、审计），不再是全库串行瓶颈。
- `00-README.md` 恢复「唯一 Owner」，消除并行派发时的并发写风险。
- 编码规范双源问题由双层模型消解，并给出明确的冲突裁决方向。
- 漂移审计方向明确，不再出现「同一现象两边结论相反」。

**负面**

- 文档写权分散到 4 个角色，转派规则必须被严格遵守，否则会出现越权修改。
- `frontend-engineer` 与 `java-architect` 获得了 `project-docs/` 的写权，其角色定义需同时说明「能写什么」与「不能写什么」。

**风险**

- `13-AI-Technology-Selection.md` 由 java-architect 兼任属于过渡安排，若 AI 选型长期无人专责，该文档可能停滞。**缓解**：在 `00-README.md` 中保留「暂兼」标注，供后续评审识别。
- 角色定义中的项目事实（决策四所述）若长期不迁出，双层边界会再次模糊。**缓解**：列为清理项，不阻塞生效。

## 日期

2026-10-14

## 参考

- [ADR-010](./010-document-domain-reorganization.md) — 文档体系分域重组（本 ADR 的治理基础）
- [ADR-014](./014-doc-template-purification.md) — 模板纯化
- [../00-README.md](../00-README.md) — §文档治理（本决策的落地条款）
- 角色定义：`~/.dsh/agent-roles/{product-manager,java-architect,doc-engineer,frontend-engineer}/SKILL.md`
