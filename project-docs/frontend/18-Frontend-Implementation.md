# 18 — 前端实施方案文档（总览）

> 最后更新: 2026-10-18 | 版本: V2.1（多平台拆分：总览 + `18-platforms/`；2026-10-18 按实测订正 §二 规模列的文件数与其统计口径）
>
> 依赖: [16-Frontend-Product-Design](../product/16-Frontend-Product-Design.md) · [17-Frontend-Design-System](./17-Frontend-Design-System.md) · [05-API-Specification](../contract/05-API-Specification.md) · [15-Front-End-Technology-Selection](./15-Front-End-Technology-Selection.md)
>
> 前置条件: 设计系统 (§17) 和产品设计 (§16) 已定稿，本文档进入工程实施规划。
> 归属域: frontend | Owner: doc-engineer | 更新触发条件: 任一平台实施阶段推进时

> **V2.0 变更**：原单篇 Web 实施方案拆为「总览（本文）+ `18-platforms/<平台>.md`」。
> Web 的平台专属内容**整体平移**至 [18-platforms/web.md](./18-platforms/web.md)，本文只保留跨平台共享内容。
> 结构依据：[ADR-054](../adr/054-frontend-multi-platform-architecture-and-doc-axis.md) §一/§三（前端多平台化 —— 平台轴与文档结构）。

---

## 一、文档结构与读法

### 1.1 两级结构

| 层 | 文件 | 管什么 | 更新触发条件 |
| :-- | :-- | :-- | :-- |
| **总览** | 本文 | Phase 模型定义、平台优先级与排期、跨平台共享物、Git 工作流、跨平台风险、全局 DoD | 阶段模型 / 平台优先级变更时 |
| **平台细则** | `18-platforms/<平台>.md` | 该平台的环境准备、脚手架与目录、阶段拆解、CI/CD、签名与发布、灰度与回滚、平台 DoD | **该平台**实施阶段推进时 |

### 1.2 平台文件清单

| 平台 | 文件 | 状态 |
| :-- | :-- | :-- |
| Web | [web.md](./18-platforms/web.md) | 🟢 实稿（原 `18` 正文已平移，含现状校正） |
| 桌面端 | [desktop.md](./18-platforms/desktop.md) | ⚪ 占位骨架 |
| Android | [android.md](./18-platforms/android.md) | ⚪ 占位骨架 |
| iOS | [ios.md](./18-platforms/ios.md) | ⚪ 占位骨架 |
| 小程序 | [miniprogram.md](./18-platforms/miniprogram.md) | ⚪ 占位骨架（实码与阶段另见 [21 §五../21-Frontend-Refactor-Readiness.md)） |

> **平台集合的单一来源**：上表的五个平台与 [15 §二 平台矩阵](./15-Front-End-Technology-Selection.md) 的行**必须一一对应**。
> 任一平台选型定稿后，此处必须同步加行 —— 反之亦然。
>
> ⚠️ **`apps/mobile`（React Native）不在上表**：它是「承载方式」而非平台轴条目（Android / iOS 的候选实现之一，见 `15 §二` 的候选 A）。
> 它的激活状态记录在 §二 的实施状态表，不单列平台文件。

> **为什么用「编号 + `18-platforms/` 子目录」而不是新编号**：保住「一个编号 = 一个关注点」——
> `18` 仍然是「实施方案」；不用申请新编号、不用重排 `00-README` 的编号顺序、不影响其他域的引用。
> 与 `adr/` 的既有形态一致（一个概念下挂多个文件）。

### 1.3 不要做的事

- ❌ **不要在总览里复制平台细则**（单一事实来源：细则只在 `18-platforms/<平台>.md`）
- ❌ **不要合并选型与实施**：选型（做什么技术选择）归 `15`，归 frontend-engineer；实施（怎么把选择落地）归 `18` + `18-platforms/`，归 doc-engineer
- ❌ **不要为未开工的平台填内容**：占位骨架保持骨架，填入实稿的准入条件写在各文件 §九

> **与 `15` 的结构差异（按 ADR-054 分阶段落地，不是不一致）**：
> [ADR-054](../adr/054-frontend-multi-platform-architecture-and-doc-axis.md) §一 决定平台轴用 `NN-platforms/` 子目录承载，
> 但**分阶段落地**：本轮只有 `18` 物理拆分（`18-platforms/`）；`15` 本轮以**文档内平台小节**（§八–§十二）承载，其 `15-platforms/` 物理拆分**推迟到对应平台开工时**。
> 理由是四个平台尚无事实依据，立即建满会产生长期空壳并把「占位」误读成「已立项」。
>
> 代价是**迁移期同一份文档体系存在两种承载形态** —— 索引必须说明，否则子目录会被当成「未登记文件」。
> 登记见 [00-README.md](../00-README.md) 的 frontend 域索引表。
> **两处的平台集合必须始终一致**（Web / 桌面端 / Android / iOS / 小程序）。

---

## 二、平台矩阵（实施状态）

> 技术选型（生态、语言框架、构建、包管理、最低支持版本）的权威表在 [15-Front-End-Technology-Selection.md](./15-Front-End-Technology-Selection.md) 的**平台矩阵**节，本文不复制。
> 本节只记录**实施状态**（代码事实，取证 [20 §七](../quality/20-Doc-Code-Consistency-Audit.md) / [21 §一../21-Frontend-Refactor-Readiness.md)）。

| 平台 | 仓库位置 | 实施状态 | 规模（实测） | 平台细则 |
| :-- | :-- | :-- | :-- | :-- |
| **Web** | `apps/web`（用户端）+ `apps/admin` + `apps/seller` | 🟡 **骨架已实现，API 层需重写** —— Phase 1 已落地；Phase 2/3 页面骨架在（原判「8 组前缀指向后端不存在的路径」→ ✅ **已收口 2026-10-18**，见 [21 §三](./21-Frontend-Refactor-Readiness.md)） | 59 文件 / 5,128 行（约 25 路由页，**时点快照 2026-10-14**，行数未复测）；admin **11 文件**、seller **10 文件**（纯骨架，**时点快照 2026-10-18**；口径见下表注） | [web.md](./18-platforms/web.md) |
| **小程序** | `apps/miniprogram`（Taro 4，微信 + 支付宝） | 🟡 **实码已存在，待对齐 API 层** —— 后端小程序增量已全量补齐（V5.18：微信小程序登录、PAY7 JSAPI 拉起支付、P37 分享码） | 48 文件 / 1,977 行 | [miniprogram.md](./18-platforms/miniprogram.md) |
| **移动端（RN）** | `apps/mobile`（React Native 0.76+） | ⚪ **空占位，未激活** —— 策略是 PWA 先行；激活触发条件见 `Implementation/front-end/apps/mobile/README.md` | 1 文件 / 0 行 | 无独立平台细则（PWA 并入 web.md §4.5） |
| **Android** | 无独立工程 | ⚪ **未开工** | — | [android.md](./18-platforms/android.md) |
| **iOS** | 无独立工程 | ⚪ **未开工** | — | [ios.md](./18-platforms/ios.md) |
| **桌面端** | 无 | ⚪ **未排期** | — | [desktop.md](./18-platforms/desktop.md) |

> **规模列的两个口径（必读 —— 口径不明会数出 0）**：
>
> 1. **统计口径：排除 `node_modules`**（含 `app/` 源码、构建与配置文件；`tsconfig.tsbuildinfo` 是构建产物，虽被 `.gitignore` 忽略但仍计入文件数）。
>    量法：`find Implementation/front-end/apps/{admin,seller} -type f -not -path '*/node_modules/*' | wc -l` → 2026-10-18 实测 **admin 11 / seller 10**。
> 2. **`apps/admin`、`apps/seller` 的真实源码目录是 `app/`，不是 `src/`** —— 按 `apps/*/src/` 去数会得到 **0 文件**。旧值「admin 9 文件 / 93 行、seller 8 文件 / 85 行」**口径未声明且已作废**；**逐文件行数本轮未复测**（[23 §8.2](./23-Frontend-Platform-Gap-Matrix.md) 明确「行数统计本轮未做」），故本列不再引用旧行数。
> 3. 文件数随开发漂移：**引用规模前先跑上面那条命令**，不要把本文数字当当前真值。
>
> 2026-10-18 复核的一处差异待裁决：[23 §8.2](./23-Frontend-Platform-Gap-Matrix.md) 记「各 11 个文件」，本文按同一命令测得 seller 为 **10**（`apps/seller` 无 `next-env.d.ts`，该文件仅 `apps/admin` 有）——以命令输出为准，差异已上报主 agent。

### 2.1 平台优先级与排期

```
P0  Web（web / admin / seller）   ← 唯一在生产路径上的平台，当前唯一有排期的平台
P1  小程序                        ← 实码已在，后端已就绪；工作量集中在 API 层对齐
P2  移动端（RN，经 apps/mobile）   ← 仅在 PWA 能力不足时激活
P3  Android / iOS 原生             ← 未决策（是否另立原生工程尚无人定）
P4  桌面端                         ← 未排期，是否设立未决策
```

> ⚠️ **优先级不是人已确认的决策，而是从代码事实反推的现状描述**。
> 正式的优先级与排期需 frontend-engineer / product-manager 确认（见 §六 待决表 #1）。
> 依据多平台方案 §9：阶段 1 只建结构，其余平台开工时再填实稿。

---

## 三、Phase 模型（跨平台通用定义）

> **这是 Phase 的唯一权威定义**，平台细则只填「该平台在每个 Phase 做什么、工期多少」，不重新定义 Phase。

### 3.1 核心理念

```
不让 AI 一次生成全部。
分阶段、分层次、步步验证。
```

### 3.2 Phase 定义

| Phase | 主题 | 完成判据（通用形式） |
| :-- | :-- | :-- |
| **1 地基** | 设计系统落地 | 该平台有可运行的 token 层 + 基础组件，视觉规范可验证 |
| **2 品牌** | 品牌体验入口 | 品牌主页/首屏完成，视觉 100% 还原设计稿 |
| **3 交易** | 商城核心链路 | 浏览 → 搜索 → 详情 → 加购 → 结算 → 支付 端到端可走通 |
| **4 数据** | 接入真实 API | 该平台脱离 Mock 能跑通核心链路；类型/客户端来自契约而非手写 |
| **5 优化** | 性能 + 测试 | 该平台性能预算达标 + 核心 E2E 通过 |
| **6 扩展** | 个人中心 + 后台 + 特色能力 | 计划内的业务域全部可用 |

> **Phase 是模型，不是日历**：各平台的 Phase 1-6 可以并行、可以跳过（如小程序无「品牌主页」需求时可合并 2→3）。
> 但**每个平台都必须走完 4（接入真实 API）与 5（性能 + 测试）**——这两项是质量门禁，不可跳过。

### 3.3 各平台进度总览

| Phase | Web | 小程序 | Android | iOS | 桌面端 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 1 设计系统 | 🟢 已落地 | 🟡 `app.scss` 充当事实 token 层 | ⚪ | ⚪ | ⚪ |
| 2 品牌 | 🟡 组件在，未验收 | — | ⚪ | ⚪ | ⚪ |
| 3 交易 | 🟡 骨架在，API 层需重写 | 🟡 页面在（20 页） | ⚪ | ⚪ | ⚪ |
| 4 接入真实 API | 🔴 未开始 | 🔴 未开始 | ⚪ | ⚪ | ⚪ |
| 5 优化 + 测试 | 🔴 未开始 | 🔴 未开始 | ⚪ | ⚪ | ⚪ |
| 6 扩展 | 🟡 个人中心在；后台纯骨架 | 🟡 部分 | ⚪ | ⚪ | ⚪ |

> 小程序的 `app.scss` 作为 token 层见 `Implementation/front-end/scripts/check-design-tokens.mjs` R3/R4 的现状注释（逐平台迁移中）。

---

## 四、跨平台必须统一的四件事

> 这四条是**硬约束**：平台可以有自己的导航范式与实现，但以下四项**只允许一处定义**。

| # | 事项 | 唯一事实来源 | 各平台做什么 | 禁止 |
| :-- | :-- | :-- | :-- | :-- |
| 1 | **设计 token** | [17-Frontend-Design-System.md](./17-Frontend-Design-System.md)（代码侧为 `apps/web/app/globals.css` 的 `@theme`） | 各平台只做**注入**：CSS 变量 / Compose Theme / SwiftUI Environment / 小程序样式变量 | 硬编码色值与尺寸；自建 token 副本 |
| 2 | **API 契约** | [contract/05-API-Specification.md](../contract/05-API-Specification.md) | 各平台的**网络层封装不同**（fetch client / URLSession / Retrofit / Taro.request），但路径、报文、错误码语义一致 | 复制端点表到本文档；硬编码 `/api/` 路径字面量 |
| 3 | **认证语义** | [12-Security-Model.md](../backend/12-Security-Model.md) + `contract/05` | 语义一致（登录/刷新/登出/失效），**存储介质按平台安全能力选**：httpOnly Cookie / Keychain / Android Keystore / 小程序 storage | 各平台自定 token 生命周期 |
| 4 | **信息架构** | [16-Frontend-Product-Design.md](../product/16-Frontend-Product-Design.md) | 主流程一致；**允许平台特有的导航范式**（iOS Tab Bar、Android 返回手势、小程序无 URL 导航），但差异必须登记进 [17 §十二 平台适配差异](./17-Frontend-Design-System.md) 表 | 平台各自发明主流程 |

### 4.1 设计 token 的跨平台注入

| 平台 | 注入机制 | 状态 |
| :-- | :-- | :-- |
| Web | Tailwind v4 `@theme`（CSS 变量） | 🟢 已落地 |
| 小程序 | 样式变量（现为 `apps/miniprogram/src/app.scss` 充当事实 token 层） | 🟡 迁移中 |
| Android | Compose Theme（Material 3 Theme / `ColorScheme`） | ⚪ 未开始 |
| iOS | SwiftUI Environment / Asset Catalog | ⚪ 未开始 |
| 桌面端 | 取决于所选生态（JS 生态可复用 Web 的 CSS 变量路线） | ⚪ 未决策 |

> 强制比对手段：`node Implementation/front-end/scripts/check-design-tokens.mjs`
> —— R1 死配置、R2 文档不撒谎（`17 §2.4` vs 线上 CSS 同值）、R3 不裸写色值、R4 平台覆盖。
> 触发条件：**修改任何 token（代码或 `17 §2.4`）后必跑**；`17 §2.4` 是代码的镜像，顺序必须是**先改代码再改文档**。

---

## 五、跨平台工程一致性

### 5.1 Monorepo 与共享层边界

单一 Monorepo（`Implementation/front-end/`，pnpm workspace + Turborepo）承载全部平台：

| 共享包 | 可被谁消费 | 注意 |
| :-- | :-- | :-- |
| `packages/api` | 全部平台 | 类型与查询逻辑可共享；**fetch 实现不可共享**（DOM `fetch` / RN `fetch` / 小程序 `Taro.request` 各不同） |
| `packages/utils` | 全部平台 | 纯函数（金额格式化、校验、常量）—— 必须零平台依赖 |
| `packages/auth` | 仅 Web 生态 | 含 Next.js `middleware` 与浏览器 Cookie 操作，**不是跨平台包** |
| `packages/ui` | 仅 Web 生态 | 基于 DOM + Tailwind，**RN / Compose / SwiftUI 均不可复用**（DOM vs Native View） |
| `packages/config` | 构建期 | 各平台构建配置共享策略 |

> ⚠️ **最大的跨生态陷阱**：把 `packages/ui` 当成"通用组件库"往 RN/小程序里搬。
> 组件只能**按设计规范重写**，共享的是 **token 与交互规范**，不是组件代码。

### 5.2 Git 工作流

```
main               # 生产就绪
  └── develop       # 集成分支
        ├── feat/<platform>-<scope>     # 如 feat/miniprogram-pay
        ├── fix/<platform>-<scope>      # 如 fix/web-cart-quantity
        └── chore/<scope>
```

**Commit 规范**（`<type>(<scope>): <subject>`）

```
feat(ui): add GlassCard component
feat(marketplace): implement product search page
feat(miniprogram): wire JSAPI pay
fix(cart): handle quantity debounce edge case
perf(image): add blurDataURL for progressive loading
test(e2e): add order placement flow
```

> `scope` 用**功能域或平台名**。平台名建议用与 `18-platforms/` 一致的词（`web` / `desktop` / `android` / `ios` / `miniprogram`），
> 但不含 `mobile`（RN 是承载方式，不是平台轴上的独立条目 —— 见 §二 表）。

**PR 要求**

- 一个 PR 只做一件事；跨平台改动拆分为多个 PR
- 触碰 token 的 PR 必附 `check-design-tokens.mjs` 输出
- 触碰 `packages/*` 的 PR 必须说明影响哪些平台

### 5.3 CI 策略（跨平台）

| 检查 | 能上通用 Linux runner？ | 说明 |
| :-- | :-- | :-- |
| lint / type-check / 单元测试 | ✅ | 各平台 |
| 设计 token 一致性 | ✅ | `check-design-tokens.mjs` |
| Web 构建 + E2E + Lighthouse | ✅ | 见 [web.md §六](./18-platforms/web.md) |
| 小程序构建（Taro） | 🟡 | CLI 构建可上 CI；**预览与真机调试需开发者工具** |
| Android / iOS 构建 | 🟡 | 需相应 SDK；iOS 签名与 TestFlight 需 macOS runner |
| 桌面端构建 | ⚪ | 取决于所选生态 |

> **原则**：能在 CI 上跑的检查必须在 CI 上跑；跑不了的（真机、签名、商店提交）必须在平台细则里**显式列出人工步骤与责任人**，不能默认"有人会做"。

---

## 六、跨平台风险与待决事项

### 6.1 跨平台风险

| 风险 | 概率 | 影响 | 缓解措施 |
| :-- | :-- | :-- | :-- |
| **跨生态套用写法** —— 把 React/Web 惯用法搬进 SwiftUI / Compose / RN | 高 | 代码风格割裂、评审成本高、AI 生成错误代码 | 各平台编码规范已写入 [08-agents.md](../collaboration/08-agents.md) §十，AI 必须遵守 |
| **token 多副本** —— 各平台各自维护一套色值 | 中 | 设计系统失去一致性，改一处要改处处 | `check-design-tokens.mjs` 强制比对；R3 禁裸写色值 |
| **`*-platforms/` 长期空壳** —— 平台迟迟不开工 | 中 | 目录冗余，读者误以为有内容 | 占位骨架显式标注「尚无事实依据」+ §九 准入条件 |
| **平台间 API 层重复漂移** —— 各平台各写一套请求 | 高 | 同一契约在 N 处漂移 | 单一 OpenAPI 生成源（阶段 1）；`packages/api` 共享类型 |
| **发布能力不对称被忽视** —— 把"随时回滚"当默认 | 中 | 应用商店版本发出去收不回，事故无法回滚 | 服务端开关优先策略；各平台细则 §六「灰度与回滚」必写 |
| **文档可信度** —— 前端域文档停在 V1.0（2026-08-02），早于契约 V5.7–V5.18 | 高 | 照文档写出的代码必然错 | 见 [21 §七.3../21-Frontend-Refactor-Readiness.md)：**API 与页面映射以 `contract/05` + 代码为准** |

### 6.2 待决事项（**非 doc-engineer 可定**）

| # | 事项 | 归属 | 影响 |
| :-- | :-- | :-- | :-- |
| 1 | 平台优先级与排期（决定阶段 2 的先后） | product-manager + frontend-engineer | 决定哪个占位骨架先填实 |
| 2 | 桌面端是否设立；走 JS 生态（Electron/Tauri）还是原生 | frontend-engineer | 决定桌面端并入 JS 生态还是独立一类 |
| 3 | Android / iOS 是走 RN（`apps/mobile`）还是另立原生工程 | frontend-engineer | 决定 `android.md` / `ios.md` 是否长期保持骨架 |
| 4 | 小程序是否同时做微信与支付宝（宿主 API 差异需在 `15` 显式分支） | product-manager + frontend-engineer | 影响 `miniprogram.md` 的分支结构与测试矩阵 |
| 5 | 多平台前端架构与文档轴决策的 ADR | **frontend-engineer**（文档轴部分 doc-engineer 会签） | 本结构属结构性决定，按仓库惯例应留档（见多平台方案 §7） |
| 6 | 契约收敛（33 条「计划中」端点移出、13 条更正、`/api/verification/codes` 补录） | product-manager | **所有平台 Phase 4 的硬前置** |
| 7 | React 版本（`15` 声明 19 / 代码锁 18.2.0） | frontend-engineer | 影响 Web 与小程序（共用 React）的可用特性 |

---

## 七、全局完成定义（Definition of Done）

**每个 Phase 完成时必须满足**（叠加在平台专属 DoD 之上）：

- [ ] 代码通过 Lint + TypeScript/等价静态检查
- [ ] 相关单元/组件/E2E 测试通过
- [ ] 手动测试：金路径（Golden Path）可走通
- [ ] 响应式 / 多分辨率：目标设备形态布局正确（矩阵见 [11-Test-Strategy.md](../quality/11-Test-Strategy.md)）
- [ ] 可访问性：键盘/辅助技术基本可用（原生平台对应 a11y 语义）
- [ ] 性能：无新增性能退化（各平台指标见各自细则）
- [ ] PR Review：至少 1 人审核通过
- [ ] 文档：新增组件/页面时更新对应平台细则；改 token 时更新 `17 §2.4` 并跑 `check-design-tokens.mjs`

**进入代码的顺序**：各平台按 Phase 1 → 6 推进，每个 Phase 完成后 review 再进入下一 Phase。

---

## 八、文档维护规则

| 变更类型 | 要改哪里 |
| :-- | :-- |
| 新增/调整 Phase 定义 | 本文 §三（唯一权威） |
| 某平台的实施细节 | 该平台的 `18-platforms/<平台>.md` |
| 平台状态/规模变化 | 本文 §二 平台矩阵 |
| 平台优先级调整 | 本文 §二.1 |
| 某平台开工（骨架填实） | 该平台文件（先满足其 §九 准入条件） |
| 新增平台 | 先定 `15` 的平台矩阵与选型，再建 `18-platforms/<平台>.md`，最后登记 `00-README` |

> **触发条件**（本文头部声明）：**任一平台**实施阶段推进时。
> 单平台的日常推进只需更新该平台文件 + 本文 §三.3 的进度格。

---

> **上游文档**: [15-Front-End-Technology-Selection.md](./15-Front-End-Technology-Selection.md)（选型）· [17-Frontend-Design-System.md](./17-Frontend-Design-System.md)（设计系统）· [21-Frontend-Refactor-Readiness.md../21-Frontend-Refactor-Readiness.md)（准入基线）
> **平台细则**: [web](./18-platforms/web.md) · [desktop](./18-platforms/desktop.md) · [android](./18-platforms/android.md) · [ios](./18-platforms/ios.md) · [miniprogram](./18-platforms/miniprogram.md)
