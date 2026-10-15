# ADR-054: 前端多平台化 —— 平台轴与文档结构

## 背景

前端从**单一 Web（React / Next.js）**扩展到 **Web / 桌面端 / Android / iOS / 小程序**五平台。代码侧现状（实测，2026-10-15）：

| 平台 | 仓库现状 | 判据 |
| --- | --- | --- |
| Web（用户端 `web` + 商家 `seller` + 平台 `admin`） | 已实现 | 三个 Next.js 15.5.20 应用，根 `pnpm.overrides` 锁 React 18.2.0 |
| 小程序（微信 / 支付宝） | 早期实现 | `apps/miniprogram`：Taro 4 + 18 个页面骨架，双宿主插件已配，未接契约 |
| Android / iOS | 未开工 | 仅 `apps/mobile`（React Native 0.76 占位，源码 0 行，`README` 自述「PWA 不够用时激活」） |
| 桌面端 | 未开工 | 无任何代码；PC 需求当前由 Web 后台覆盖 |

文档侧的问题：`frontend/15`（687 行）、`17`（1008 行）、`18`（847 行）都是**单平台写法** —— `15`/`18` 的每一节都默认只有一个平台（原文用 `apps/storefront`、`React 19`），`17` 的设计 token / 组件 / 动效规范虽天然跨平台，但**没有任何地方说明这些 token 在其它平台怎么注入**。平台轴一旦开工，三篇文档都没有承载位置。

**触发点**：需要在不新增编号、不破坏「一个编号 = 一个关注点」与单一事实来源原则的前提下，给三篇文档加平台轴。

## 决策

### 一、承载方式：编号文件 + `NN-platforms/` 子目录（不新增编号）

平台轴用**子目录**承载，不申请新编号：

```
project-docs/frontend/
├── 15-Front-End-Technology-Selection.md     # 总览（跨平台）+ 平台细则
├── 15-platforms/  web.md desktop.md android.md ios.md miniprogram.md
├── 17-Frontend-Design-System.md             # 保持跨平台单篇
├── 18-Frontend-Implementation.md            # 总览（阶段模型/优先级/全局 DoD）
└── 18-platforms/  web.md desktop.md android.md ios.md miniprogram.md
```

理由：编号已从全局唯一变为**域内唯一**，新开编号会导致编号膨胀、`00-README` 重排、其它域的引用失效；子目录形态与 `adr/` 一致（一个概念下挂多个文件）。

**落地节奏（分阶段，不是一次建满）**：

| 阶段 | 内容 |
| --- | --- |
| 本轮 | `18` 物理拆出 `18-platforms/web.md` + 总览（归 doc-engineer）；`15` 以**文档内平台细则分节**承载平台轴，并新增「平台矩阵」 |
| 某平台开工时 | 建立该平台的 `15-platforms/<平台>.md` / `18-platforms/<平台>.md`，把对应小节迁出；同步 `quality`、`product/16`、`collaboration/08-agents` 的平台差异 |

**为什么本轮的 `15` 不直接建满五个文件**：目前只有 Web 有事实依据，其余四个平台选型未决。立即建满会产生四个只有 TODO 的长期空壳文件，并把「文档说有」误读成「平台已立项」。

### 二、`17` 保持跨平台单篇，只新增「平台适配差异」一节

`17` 的色彩 / 字体 / 间距 / 图标 / 动效是**跨平台共享物**，按平台切分会摧毁其价值 —— 设计系统会退化成五份互不相干的设计说明，一致性将无人守。

因此 `17` 的 11 节结构不动，新增 **§十二 平台适配差异**：token 注入机制（CSS 变量 / Compose Theme / SwiftUI Environment / 小程序样式变量）、`token/规范 × 五平台` 的差异登记表（含偏离原因）、以及登记规则。

**明确拒绝**：把 `17` 按平台拆成五份。

### 三、`15` 与 `18` 采用「总览 + 各平台细则」两级结构

| 文档 | 总览保留 | 下沉到平台细则 |
| --- | --- | --- |
| `15` | 选型原则、**平台矩阵**、跨平台统一项、Web 端选型对比、认证语义、与后端契约对接、禁止事项 | 各平台的核心栈、组件架构、认证落地、性能、测试、部署、依赖锁定（Web 见 §八） |
| `18` | 阶段模型、平台优先级与排期、Git 工作流、跨平台风险、全局 DoD | 各平台的环境准备、脚手架、目录结构、阶段拆解、CI/CD、签名与发布、灰度、平台 DoD |

总览**不复制**平台细节 —— 每个平台一行指向对应小节/文件（单一事实来源）。

### 四、编号约定：域内唯一，跨域引用必须带域前缀

编号是**域内唯一**而非全局唯一（现存撞号实例：`backend/02-Architecture.md` 与 `product/02-Frontend-Integration-Checklist.md`）。跨域引用必须写 `backend/02`、`product/02` 这样的域前缀；域内引用可省。

### 五、多平台不得破坏设计 token 的单一来源

- 运行时唯一来源仍是 `Implementation/front-end/apps/web/app/globals.css` 的 `@theme`；`17` §2.4 是它的**镜像**，由 `Implementation/front-end/scripts/check-design-tokens.mjs` 强制比对（**已接入 CI**）。
- 改 token 的顺序固定：**先改代码 → 再改 `17` §2.4 → 跑检查**。
- 非 Web 平台**不必用 `@theme`**，但必须有一个**与 Web 同源**的 token 模块（同一批数值），**禁止各端自定色板**。检查脚本的 R4 负责报告各 app 是否已有 token 层。

### 六、未开工的平台只写占位骨架与待决项

遵守仓库「不编造、核实不了标注未核实」的规范：未选型的平台在文档中**明写「未决」**，只登记骨架与决策前置条件，**不预写实稿**。平台矩阵用状态列区分 `已实现 / 早期实现 / 计划中`，并给出判定标准。

**两个子目录的骨架形状不同** —— 它们回答的问题不同，**不要互相套用**（本条为 054 落地后的补充澄清）：

| 子目录 | 视角 | 骨架节 |
| :-- | :-- | :-- |
| `15-platforms/` | **选型**：这个平台用什么、为什么 | 生态与语言 / 框架与构建 / 状态与路由 / 认证落地 / 性能预算 / 平台限制 |
| `18-platforms/` | **实施**：这个平台怎么落地、怎么发布 | 环境准备 / 脚手架 / 阶段拆解 / CI-CD / 签名与发布 / 灰度回滚 / 平台 DoD 等，以该子目录现有骨架为准 |

未开工平台的骨架文件**必须附一份「开工前置」准入清单**，写明该平台开工前必须先定哪些决策 —— 否则占位文件会被读成「已立项」。

## 备选方案

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| **A. 每平台新开编号** | 隶属清楚、单文件单一平台 | 编号膨胀、`00-README` 需重排、其它域引用失效 |
| **B. `17` 也按平台拆** | 平台自治 | 设计一致性无人守，跨平台价值丧失 |
| **C. 全部塞进现有单篇** | 零结构变更 | `18` 将超 4000 行，超出单次可读范围，「实施阶段推进时」这一更新触发条件失效 |
| **D. A/B/C 之外：只加一张平台矩阵表，不做两级结构** | 改动最小 | 平台细则无处安放，总览会被细则淹没；新增平台时无法增量扩展 |
| **E. 本轮就为五平台建满 `*-platforms/` 文件** | 与目标态一致、结构一次到位 | 四个平台无事实依据 → 长期空壳 + 把「占位」误读成「已立项」 |
| **F. 编号文件 + `NN-platforms/` 子目录，分阶段落地（本决策）** | 保住「一编号一关注点」、不动编号与索引顺序、平台轴可增量扩展 | 同一份文档存在两种承载形态（in-file 与子文件），迁移期需在索引里说明，否则会被当成「未登记文件」 |

## 后果

**正面**

- 编号稳定、关注点不破：`15` 仍是「技术选型」、`18` 仍是「实施方案」，其它域的引用不受影响。
- 平台轴可扩展：新平台开工时只新增一个子文件 + 一行矩阵，不用改结构。
- 设计系统保持单篇，一致性有唯一守门人（`17` + `check-design-tokens.mjs`）。
- 五平台的技术栈、状态、理由第一次有了一处总览（`15` §二 平台矩阵），且**区分「已实现」与「未决」**，不会把未选型写成已选型。

**负面**

- 迁移期 `15` 的平台细则在文档内、`18` 的在子目录里，承载形态不统一，需要索引说明。
- 文档写权分散（`15`/`17` 归 frontend-engineer、`18` 归 doc-engineer），平台轴推进需要跨角色同步，转派规则必须被遵守。

**风险**

- `*-platforms/` 若长期不建（平台迟迟不开工）或建了长期空壳，都会让「平台轴」沦为纸面结构。**缓解**：`15` 本轮不预建子文件；已建占位必须在文件头标注状态与未决项。
- 平台矩阵中的「最低支持版本」等字段目前**未定义**（Web 的浏览器基线、小程序的基础库版本）。**缓解**：矩阵中明写「未定义（待确认项）」，不填猜测值。
- 本轮核查发现 `15` 原正文与代码存在漂移（React 19 vs 实装 18.2.0；Vitest / Playwright / ESLint / Husky 未安装；`apps/storefront` vs `apps/web`）。**缓解**：已登记在 `15` §8.1「漂移登记」，**本轮不做静默改写**（改写属独立决策，见 `21` §四）。

### 本轮落地状态

| 文件 | 本轮落地 | 归属 |
| --- | --- | --- |
| `frontend/15` | ✅ 新增 §二 平台矩阵、§三 跨平台统一项；Web 细则下沉为 §八（8.1–8.9）；新增 §九–§十二 四平台小节；`15-platforms/` 物理拆分**推迟** | frontend-engineer |
| `frontend/17` | ✅ 新增 §十二 平台适配差异（跨平台单篇结构不变；§2.4 token 镜像与 §十一 检查清单未改） | frontend-engineer |
| `frontend/18` | ⏳ 拆为总览 + `18-platforms/web.md`，其余平台建占位骨架 | doc-engineer（另一任务） |
| `00-README.md` | ⏳ 域表/逐文档表加「五平台」、登记 `*-platforms/` 子目录归属、补编号域内唯一约定 | doc-engineer |
| `adr/README.md` | ⏳ 登记本 ADR 一行 | doc-engineer（索引） |
| `quality/11`、`quality/19`、`product/16`、`collaboration/08-agents` | ⏳ 设备矩阵、平台测试流程、平台信息架构差异、各平台编码规范 | doc-engineer / product-manager |

## 日期

2026-10-15

## 参考

- [ADR-053](./053-doc-ownership-by-nature-and-two-layer-standards.md) — 文档 Owner 按性质分权（本 ADR 的写权依据：`15`/`17` 与前端决策的 `adr/` 归 frontend-engineer）
- [ADR-010](./010-document-domain-reorganization.md) — 文档体系分域重组（本 ADR 的结构基础）
- [ADR-014](./014-doc-template-purification.md) — 模板纯化
- [../frontend/15-Front-End-Technology-Selection.md](../frontend/15-Front-End-Technology-Selection.md) — §二 平台矩阵、§三 跨平台统一项、§八–§十二 平台细则
- [../frontend/17-Frontend-Design-System.md](../frontend/17-Frontend-Design-System.md) — §十二 平台适配差异
- [../frontend/18-Frontend-Implementation.md](../frontend/18-Frontend-Implementation.md) — 实施方案的平台轴（总览 + `18-platforms/`）
- [../frontend/21-Frontend-Refactor-Readiness.md](../frontend/21-Frontend-Refactor-Readiness.md) — 前端重构准入与四项架构决策（含 React 版本、token 存储、类型生成、多端策略）
- [../00-README.md](../00-README.md) — §文档治理（索引登记与 Owner 表）
- 结构方案：`frontend-multiplatform-doc-plan.md`（工作区文件，非仓库内文件；本 ADR 的结构决策源）
- **会签说明**：本文的**文档轴部分**（`00-README` 索引登记、`*-platforms/` 子目录归属与命名）由 **doc-engineer 会签**；`adr/README.md` 的索引行同样归其维护。
