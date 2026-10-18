# 20 — 文档-代码一致性审计报告

> 归属域: quality | Owner: doc-engineer | 更新触发条件: 契约/镜像文档/代码任一漂移收敛后重跑
>
> **审计日期**：2026-10-14 | **审计范围**：`Implementation/back-end/**`（19 个 Maven 模块）、`Implementation/front-end/**`、`project-docs/backend/*.md`、`project-docs/contract/05-API-Specification.md`（API 使用说明书）、`project-docs/**` 其余文档、根 `AGENTS.md` / `CLAUDE.md`
> **审计方法**：静态取证（`grep`/`node` 脚本全量提取 + 逐条 `read` 复核）；可复现脚本见 §十一，位于 `Implementation/back-end/scripts/`
> **关联**：[21-Frontend-Refactor-Readiness.md](../frontend/21-Frontend-Refactor-Readiness.md)（本报告的前端重构落地版）
>
> ⚠️ **读数须知（2026-10-15 追加）**：本报告是**修复前的证据基线** —— 文中「实测 286 端点 / 内部 61 / 45 张表 / 47 条 docOnly / 6 条 codeOnly / 590 测试」等数字描述的是**审计时点**的事实。
> 其后已按 §八 修复清单与 §九 决策分批收敛，**绝大多数条目已闭环**，因此这些数字**不再代表仓库现状**，请勿据本报告判断当前状态。现状以以下**实测值**为准（命令见 §十一）：
>
> | 指标 | 审计时点 | 2026-10-15 收敛后 |
> | --- | --- | --- |
> | 代码端点 / 内部端点 | 286 / 61 | **287 / 62** |
> | 契约端点行（distinct） | 326 | **332 行 / 330 distinct** |
> | `codeOnly`（代码有、契约无） | 7 | **0** ✅ |
> | `docOnly`（契约有、代码无） | 47 | **43**（`⚪` 35 + `⚠️` 8，全部已就地标注） |
> | 命名「同名异形」组数 | 10 | **0** ✅ |
> | 表数 / Maven 模块数 | 62 实测（文档写 45） | 文档已订正为 **62** / **19** |
> | 全量测试 | 1600+（口径混乱） | **1775 tests / 0 failures / 0 errors / 1 skipped** |
>
> 本报告**本身不再改动叙事**（保留为基线证据）；后续批次落地记录见 §八 之后的「后续批次落地记录」小节。

---

## 一、审计口径与方向判定

按 `00-README.md`「文档治理」的一致性规则，先分清文档性质再定漂移方向——**否则漂移审计的方向是反的**：

| 性质 | 文档 | 漂移含义 | direction |
| --- | --- | --- | --- |
| ① 决策记录（本文仅涉服务清单/路由等技术描述部分） | `backend/02-Architecture.md` | 描述部分按镜像处理 | `fix-doc` |
| ② **规格**（契约，代码须向文档对齐） | `contract/05-API-Specification.md`、`product/01-PRD.md` | 代码没跟上 | `fix-code` |
| ③ **镜像**（文档须向代码对齐） | `backend/03`、`backend/04`、`backend/12`、`backend/14`、`quality/*`、`collaboration/*` | 文档没跟上 | `fix-doc` |

**关键前提**：`contract/05-API-Specification.md` 是②类规格，但**它的端点表当前混装了「已实现」与「计划中」两种事实**（见 §三·A2/A3）。在这一混杂被分离之前，②类不能机械地执行「以文档为准」——否则会把 33 个未实现端点当规格去补代码。

**取证方式（两轮）**：

| 轮次 | 方法 | 产出 |
| --- | --- | --- |
| 第一轮 | **确定性静态取证**：`grep`/`node` 全量提取 + 逐条 `read` 复核，所有计数可重跑复现 | §三、§四、§五机械项、§六、§七、§A8 |
| 第二轮 | **10 切片并行深度审计**（30 个 agent）+ 对全部 blocker/major 的**证伪式对抗复核** | §C2 横切运行时缺陷、§四/§七 逐条镜像差集（见 §十 覆盖边界） |

> 第二轮的价值在于它抓到第一轮结构性漏掉的一类缺陷：**跨服务 / 跨网关 / 跨序列化边界**的问题——它们不由任何单个文件的对错构成，因此既不在「契约 vs 代码路径 diff」里，也逃过了 1699 个单元测试（Mockito 把 Feign 与网关都 mock 掉了）。

---

## 二、结论摘要

| 审计面 | 判定 | 核心证据 |
| --- | --- | --- |
| **A. 契约 §四 端点表 vs 代码** | 🔴 **不同步（含 13 条阻断级）** | 代码实测 286 个端点 vs 契约 326 行；13 条契约标「已实现」但代码不存在 |
| **B. 契约头部计数 / §六 网关表 / §二 错误码** | 🔴 **内部自相矛盾 + 明显滞后** | 头部「内部 57 个」与「内部 39+」同行冲突；§六 缺 6 个路由前缀、2 行目标服务写错；错误码 98 个只文档化 27 个 |
| **C. `backend/*` 镜像文档** | 🟠 **滞后 3–13 个版本** | `02`/`03` 停在 V5.9、`04` 声明 45 张表（实际 62）、`13` 停在 V2.0（2026-07-19） |
| **D. 根 `AGENTS.md`/`CLAUDE.md`/`00-README.md`** | 🟠 **自相矛盾** | 同一文件内「57 张表」与「62 tables」并存；「18 Maven modules」实际 19；测试数 1690 / 590 / 实测 1699 |
| **E. 代码侧硬约束** | 🟢 **基本合规（1 项例外）** | `@Transactional` 缺 `rollbackFor` 0 例、裸 `RuntimeException` 0 例、Controller 注入 Mapper 0 例、`/internal/**` 返回 `Result` 0 例；**例外：18 个 domain 文件 import MyBatis-Plus** |
| **E2. 横切运行时缺陷**（§C2） | 🔴 **5 条阻断级 + 5 条 major** | 12 个营销管理端点经网关 **404**；12 个管理端点**无鉴权**；命名策略不一致致**站内信失效**；地址 **IDOR**；人机验证参数口径不符致登录**死循环**。全部逃过 1699 个单元测试 |
| **F. 前端代码 vs 契约** | 🔴 **骨架 + 9 条死路径** | 159 个源文件；前端调用的 9 条路径在后端**不存在**；错误码表映射了不存在的 `1004`；Web 端当前**无法登录** |
| **G. 前端文档（15/17/18/16）** | 🔴 **严重滞后（V1.0 / 2026-08-02）** | 声明 React 19，实际 `react ~18.2.0` 且被 pnpm override 强制锁 18.2.0 |

**一句话结论**：**后端代码的分层与约束执行度高（`@Transactional`/异常/Mapper 注入等机械项 0 违规），但存在 5 条只有跨服务、跨网关、跨序列化边界才会暴露的阻断级缺陷，它们全部逃过了单元测试；同时「契约文档」与「镜像文档」两条链路都已明显落后于代码。前端现有工程既不是按契约写的，也不是按代码写的——它引用了一批两者都不存在的路径，Web 端当前甚至无法完成登录。因此前端重构的前置条件不是「改前端」，而是先做一轮契约收敛（把 33 个计划端点移出端点表）+ 修掉 §C2 的跨服务缺陷（否则前端接上去也是 404/鉴权失败）。**

---

## 三、A 类：契约 §四 端点表 vs 代码

### A0. 实测计数（可复现）

提取方式：`node Implementation/back-end/scripts/extract-endpoints.mjs`（解析 `adapter/**/*Controller.java` 的 Spring 映射注解并还原类级前缀；解析契约 Markdown 端点表行）。

| 指标 | 代码实测 | 契约声明/实测 |
| --- | --- | --- |
| 端点数（method + path 去重） | **286** | 头部声明 **278**；表内 **326** 行（去重 326） |
| Controller 类 | 89 | — |
| `/internal/**` | **61** | 头部声明 **57**；表内 59 |
| `/api/admin/**` | **55** | 表内 60 |
| 非 `/internal/**` | 225 | 头部声明「公共 195+」 |
| 两侧匹配 | **279** | — |
| 代码有 / 契约无 | **7** | — |
| 契约有 / 代码无 | **47**（33 计划 + 14 标注已实现） | — |

> 286（代码）− 7（未文档化）= 279 = 契约 326 − 47。数据自洽，说明两侧提取覆盖完整。

### A1. 🔴 契约标注「已实现」但代码中不存在（13 条，阻断级）

判定依据：契约 §4 表末「阶段」列图例（契约 :216）= `✅ 已实现 | 🔵 已实现待完善 | ⚪ 计划中 | 🆕 V5.0 新增`。下列各条阶段列为 ✅/🔵，但全仓 `grep` 找不到任何映射，**前端照契约编码必然 404**。

| ID | 契约路径 | 阶段 | 代码真实路径 | 证据 |
| --- | --- | --- | --- | --- |
| A10 | `POST /api/auth/email/code` | 🆕 V5.1.1 | **`POST /api/verification/codes`**（能力已合并） | 契约 :248；`VerificationCodeController.java:46,58` 且 :55-56 注释明写「承接原 authorization-service `/api/auth/email/code` 与 user-service `/api/user/code`」 |
| U5 | `POST /api/user/code` | ✅ | **`POST /api/verification/codes`** | 契约 :324；同上 |
| U12 | `POST /api/user/sign` | ✅ | **`POST /api/sign`** | 契约 :388；`SignController.java:34,42` |
| U13 | `GET /api/user/sign/status` | ✅ | **`GET /api/sign/status`** | 契约 :389；`SignController.java:48` |
| C7 | `GET /api/cart/grouped` | ✅ V3.5 | **无**（读时装配已收敛为 `GET /api/cart`，ADR-021） | 契约 :895；`CartController.java:17,26` 仅 6 个端点，无 `grouped` |
| M6 | `POST /api/coupon/available/filter` | ✅ V4.3 | **无**（`GET /api/coupon/available`） | 契约 :1349；`CouponController.java:31,52`；全仓无 `available/filter`、无 `CouponFilterReq` |
| CH6 | `GET /api/seller/templates` | ✅ V3.3 | **无**（客服话术已由知识库取代：`/api/seller/knowledge`） | 契约 :1630（§4.12.3）；`KnowledgeBaseController.java:39,47,56,64,73`；全仓无 `"templates"` |
| CH7 | `POST /api/seller/templates` | ✅ V3.3 | **无** | 契约 :1631 |
| CH8 | `PUT /api/seller/templates/{id}` | ✅ V3.3 | **无**（对应 `PUT /api/seller/knowledge/{id}`） | 契约 :1632 |
| CH9 | `DELETE /api/seller/templates/{id}` | ✅ V3.3 | **无**（对应 `DELETE /api/seller/knowledge/{id}`） | 契约 :1633 |
| AD18 | `GET /api/admin/application` | ✅ | **`GET /api/admin/seller/pending`** | 契约 :1721；`AdminMerchantController.java:32,39`；全仓无 `admin/application` |
| AD19 | `PUT /api/admin/application/{id}/review` | ✅ | **`PUT /api/admin/seller/{id}/approve`** | 契约 :1722；`AdminMerchantController.java:48` |
| I29 | `POST /internal/ws/order/status` | 🔵 已实现待完善 | **无**（全仓无 `/internal/ws`） | 契约 :2147 |

> **注**：`CH1 WS /ws/chat`（契约 :1583，标 ✅）经复核**不是漂移**——STOMP 端点不在 `@*Mapping` 注解体系内，故未被提取器捕获；实际存在于 `ChatWebSocketConfig.java:40`（`registry.addEndpoint("/ws/chat")`）与 `ChatMessageController.java:53`（`@MessageMapping("/chat-send")`）。此项为**提取器已知局限**，非缺陷。

**修复方向**：13 条中 11 条属 `fix-doc`（把契约路径更正为代码真实路径），CH6–CH9 属 `discuss`（话术模板功能是被知识库取代还是待补，需 product-manager 定论），I29 属 `fix-doc`（退回 `⚪ 计划中`）。

### A2. 🟠 契约端点表混入 33 个「计划中」端点

契约 :216 图例把 `⚪ 计划中` 与 `✅ 已实现` 混排在同一张端点表里，而头部（:3）只给了一个**未拆分**的「端点总数 278」。33 个 `⚪` 端点为：

`U24`–`U27`（账户安全：改密/换绑/注销/复活的 4 个）、`S5`、`P6`、`P19`–`P22`（品牌 CRUD）、`P24`、`P25`/`P26`（到货通知）、`C8`–`C10`、`T7`–`T9`、`T13`/`T14`、`T17`–`T19`、`T26`、`PAY5`、`SE4`、`M9`、`M11`、`CH5`、`AD26`、`AD38`、`AD39`。

其中 `S5`、`P6`、`T26` 三条契约正文已自注「未实现（移入 §十二 roadmap）」——即**文档自己知道没实现，却仍留在端点表内**。

**修复方向**：`fix-doc`。**⚠️ 2026-10-14 修订**：用户已确认契约是**规格（spec）**而非现状快照，故 **33 条 `⚪` 应当保留在端点表内**（规格本就应包含计划项），正确做法是**保留 + 显式分区**——新增「状态」列或按状态分区成表，并把头部「端点总数」按状态拆成「已实现 N / 计划中 M」，使读者一眼可辨「可调」与「待实现」。**不要移出端点表**（会让规格丢失未来端点）。详见 [22 §五 D7](22-API-Spec-BigTech-Conformance-Review.md)。这是前端重构最需要的一次契约收敛（见 [21](21-Frontend-Refactor-Readiness.md) §二）。

### A3. 🟠 代码有、契约无（未文档化 7 条）

| 路径 | 位置 | 判定 |
| --- | --- | --- |
| `POST /api/verification/codes` | `VerificationCodeController.java:58` | **最严重**：它是 A10/U5 两条死路径的**唯一活替身**，却完全未文档化 |
| `GET /internal/auth/context/{userId}` | `InternalAuthController.java:46` | 契约 :2643（V5.5.1 变更记录）**自认**「代码已实现但尚未收录 §4.14 端点目录」 |
| `GET /internal/auth/roles` | `InternalAuthController.java:52` | 同上（补录待办） |
| `PUT /internal/auth/user/{id}/role` | `InternalAuthController.java:58` | 同上（补录待办） |
| `POST /api/sign` | `SignController.java:42` | 与 U12 同一漂移的另一面 |
| `GET /api/sign/status` | `SignController.java:48` | 与 U13 同一漂移的另一面 |
| `DELETE /api/user/history/{productId}` | `HistoryController.java:44` | 契约 U23 只写 `DELETE /api/user/history`（清空），漏了按商品删除 |

**修复方向**：`fix-doc`（前 4 条 + 后 3 条补录 §4.14 / §4.2）。其中 4 条已被契约自己的变更记录承认为待办——属**已知未闭环项**，不是新发现。

### A4. 🔴 契约头部计数自相矛盾

契约 :3 单行内同时声明：

- 「**端点总数 278**（方法级映射实测，含 `/internal/**` **57 个**）」
- 「**公共 195+ | 内部 39+ | 已实现 229+**」

`内部 57` 与 `内部 39+` 同行冲突；`57 + 195 = 252 ≠ 278`；`已实现 229+` 与「端点总数 278」的关系未定义。代码实测：总数 286、内部 61、非内部 225。

**修复方向**：`fix-doc`。改为一行可核验的三元组，例如「已实现 279（公共 218 + 内部 61）/ 计划中 33 / 合计 312」，并注明统计口径与 `Implementation/back-end/scripts/extract-endpoints.mjs` 的复核方式。

### A5. 🟠 §六 网关路由速查表 vs `gateway-service/application.yml`

代码实测：**19 个 route block / 44 个 path predicate**（根 `CLAUDE.md` 声明「17 route blocks / 34 path predicates」——两项均过期）。

| 契约 §六 行 | 实际（application.yml） | 判定 |
| --- | --- | --- |
| `/api/auth/**`, **`/oauth2/**`** → authorization-service | 只有 `Path=/api/auth/**`；**网关无 `/oauth2/**` 路由**（:167 仅把 `http://localhost:9000/oauth2/jwks` 当作网关**自身**校验 JWKS 的地址） | `fix-doc`：删 `/oauth2/**`，注明 JWKS 只从 authorization-service:9000 直取 |
| `/api/user/**`, **`/user/**`** → user-service | 只有 `Path=/api/user/**` | `fix-doc` |
| `/api/item/**`, `/api/home/**`, `/api/admin/home/**` | 另有 **`/api/notes/**`、`/api/admin/kg/**`** | `fix-doc`（补 2 行） |
| `/api/coupon|flash|points|sign/**` → marketing | 另有 **`/api/task/**`、`/api/activity/**`、`/api/group-buy/**`** | `fix-doc`（补 3 行；V5.7–V5.14 新增路由未进表） |
| `/api/wallet/**` → wallet | 另有 **`/api/currency/**`** | `fix-doc` |
| （缺行） | **`/api/admin/currency/**`** → wallet-service | `fix-doc`（补行） |
| `/api/trade|after-sale|admin/after-sale|admin/finance|seller/finance/**` → trade | 另有 **`/api/invoice/**`、`/api/admin/invoice/**`** | `fix-doc` |
| `/api/invoice/**` → **「(🆕 invoice-service)」** | 实际 `lb://trade-service`；**不存在 invoice-service 模块** | `fix-doc`：目标服务写错 |
| `/ws/**` → **gateway-service** | 实际 `Path=/ws/chat/**` → **`lb://ai-service`**（:146） | `fix-doc`：目标服务写错 |

> **路由顺序**（前端易踩坑，代码正确故不需改）：`/api/admin/**` → user-service 兜底被刻意排在 `/api/admin/{roles,permissions,seller,pay,currency}/**` 之后；`/api/seller/**` → merchant-service 被排在 `/api/seller/{coupon,knowledge,finance}/**` 之后。**这是顺序敏感的隐式契约，契约 §六 只用一句「精确路径优先匹配，兜底路由放最后」带过，建议在文档中显式列出顺序表**（`fix-doc`）。

### A6. 🟠 §二 错误码体系覆盖率 27/98

- `ia-common` `ErrorCode` 枚举实测 **98 个**；契约 §二（含 §2.1 速查、§2.2 AI 专项）共文档化 **27 个**；**71 个未文档化**。
- **无幽灵错误码**（契约提到的都在枚举里）——方向单一，属纯遗漏。
- 典型遗漏：`1015 ILLEGAL_REQUEST`、`1016 CAPTCHA_REQUIRED`（**§4.1 :234 正文引用了 1016，却未进 §二 表**）、`1017 ACCOUNT_FROZEN`、`3004`、`3007 PASSWORD_ERROR`、`3008`、`3009`、`3011`、`3016`–`3018`、`3020`–`3024`、`3030`–`3038`、全部 `4xxx` 第三方码、`5000`–`5002` 加密码。
- 契约 :50/:102 称「业务码 **3000-3039** 的 HTTP 状态为 200」，但枚举中 `3040 FREIGHT_TEMPLATE_NAME_EXISTS`、`3041 FREIGHT_TEMPLATE_NOT_FOUND` 亦为 `HttpStatus.OK` → 区间表述过期。

**修复方向**：`fix-doc`（把区间改为 `3000-3041` 或改为按「HttpStatus.OK 的均返回 200」表述；补齐 71 个码或明确「仅列举前端需要分流的子集」并声明该口径）。

### A7. 契约 §4.15 发票小节严重滞后

§4.15 仅约 22 行（:2250–2271），而 trade-service 实际已有 `InvoiceController`（`/api/invoice`，含 `apply`/`history`/`title` CRUD 等）+ `AdminInvoiceController`（`/api/admin/invoice`）+ `InternalTradeInvoiceController`（`/internal/trade/invoice`），对应 PRD §十五 INV1–9 / AD57–60 / I56 / I60。**修复方向**：`fix-doc`。

### A8. 🔴 契约 §七「前端页面 ↔ API 映射」逐行校验：148 个引用中 19 个端点不存在

校验方式：`node Implementation/back-end/scripts/check-section7.mjs`（提取 §七 :2344–2429 全部 `` `METHOD /path` `` 引用，与代码实测端点取差集）。

| 结果 | 数量 |
| --- | --- |
| §七 引用的端点（去重） | **148** |
| ✔ 代码中存在 | 127 |
| ✘ 不存在 | **19** |
| ⚠ 路径存在但 HTTP 方法不符 | 2 |

**19 个不存在中，10 个属真实缺陷**（§4 中标注 ✅ 却无实现，即 §A1 同一批问题在 §七 页面层的二次暴露）：

`POST /api/user/code`（注册页 :2360）、`GET /api/cart/grouped`（购物车 :2373）、`POST /api/coupon/available/filter`（结算页 :2374）、`POST /api/user/sign` + `GET /api/user/sign/status`（签到页 :2379）、`GET|POST|PUT|DELETE /api/seller/templates`（回复模板页 :2405，4 条）、`GET /api/admin/application`（商家审核页 :2415）。

**其余 9 个属「计划端点泄漏进页面映射」**（§4 已标 `⚪`，但 §七 页面表未标注，前端看起来像能用）：`/api/user/security/{password,phone}`（:2386）、`GET /api/flash/timeline`（:2390）、`GET /api/admin/product/page` + `PUT /api/admin/product/{id}/status`（:2416）、`/api/item/brand` 三条（:2428）。→ 与 §A2 同源，属**同一处修复**。

**2 个方法不符**均落在计划端点上：`GET /api/seller/apply`（:2407，实际仅 `POST`）、`GET /api/admin/coupon`（:2420，实际仅 `POST`）。

> **已在文档内自我警示、但仍被前端违反的一例**：§七 :2348 明确写着「历史路径 `POST /api/auth/login` 已废弃（服务端无此路由，调用会 404）」——而现有前端**仍在调用它**（见 [21 §三](../frontend/21-Frontend-Refactor-Readiness.md)），说明前端代码早于该警示。**修复方向**：`fix-doc`（10 条真实缺陷更正、9 条计划项加 `⚪` 标注）。

---

## 四、B 类：`backend/*` 镜像文档 vs 代码

### B1. 🔴 `04-Data-Model.md` 仍描述已被废除的迁移机制，且表数声明错误

**事实源**：`database/Initialize.sql` 是唯一结构真源（表数以 `node Implementation/back-end/scripts/sync-doc-counts.mjs --list-tables` 为准）；`database/` 目录实际只有 `Initialize.sql` 与 `middleware/`——**`migrations/` 目录不存在**（ADR-045，2026-10-05 废除迁移机制）。**`seed-data.js` 已于 2026-10-18 退役删除**（U7 裁决：它与 `Initialize.sql` 的种子写同一个 `USERNAME/'admin'` 唯一键、口令 `Dev123456`，且业务 ID 仍是 ADR-050 前的非 hex 形态；种子真源统一为 `Initialize.sql`）。同批 U3 还删除了 5 张零代码引用的死表（见 `04-Data-Model.md` 头部裁决注记）。。

`04-Data-Model.md` 中的残留（全部 `fix-doc`）：

| 位置 | 原文 | 问题 |
| --- | --- | --- |
| :28 | 「`Initialize.sql` 为全量最新快照（**45 张表**，一次装库到位）；`migrations/`（**30 个版本化文件**，V1.1.0 → V5.6）是存量库升级路径」 | 表数错（45 vs 62）+ 引用不存在的目录 + 与 ADR-045 直接冲突 |
| :7 | 「全库 **45 张表、30 个迁移（V1.1.0→V5.6）**」 | 同上 |
| :200 | 「结构变更脚本 `database/migrations/V5.2__user_profile_split_and_encryption.sql`」 | 死链 |
| :439 | 「建表脚本：`database/migrations/V5.1__add_user_auth.sql`」 | 死链 |
| :654 | 「DDL 保留于 `V2.0__add_after_sale_application.sql`」 | 死链 |
| :753 | 「`merchant_knowledge_base` 已建（**V3.4 迁移**）」 | 死链 |
| :900/:904 | 「随 `V5.5__refund_wallet_tx_and_reconciliation.sql` / `V5.6__freight_template_and_package_split.sql` 迁移落库」 | 死链 |

**内部矛盾**：同一文档 :28 说 45 张表，而正文现状快照写「**23+1+38**」（= 62）——文档与自身冲突。

### B2. 🟠 `04-Data-Model.md` 表清单缺口

- **`shop_decoration`（ADR-048 店铺装修，S1c/S1d）全文 0 次出现** → 完全未收录（`fix-doc`）。
- `note_like`、`note_product`、`task_def`、`task_record` 表名 0 次出现，仅被 `note*` / `task_*` 通配串一笔带过（:120）→ 建议补表级条目（`fix-doc`）。
- RBAC 五表（`sys_*`）**已**在 §八 8.1–8.3 收录 ✓（初筛误报，已复核修正）。
- `review_like`、`merchant_reply_template` 已收录 ✓。

### B3. 🟠 死表：`merchant_reply_template`

- `Initialize.sql` 建了 `merchant_reply_template`；`04-Data-Model.md` §10.2 有专节；**但全仓 `grep` 无任何 Java 代码引用该表**（无 PO / Mapper / Repository）。
- 与之对应，契约 §4.12.3 CH6–CH9（`/api/seller/templates`）标 ✅ V3.3 却无实现（见 §A1）。
- 三者叠加 = **一张建了但无代码的表 + 一节文档 + 一节标「已实现」的契约端点，功能实际不存在**。→ `discuss`（补实现 / 删表删契约，需产品定论）。

### B4. 🟠 镜像文档版本滞后

| 文档 | 声明版本 | 声明日期 | 相对契约（V5.18.0 / 2026-10-06） |
| --- | --- | --- | --- |
| `backend/02-Architecture.md` | V5.9 | 2026-10-05 | 落后 9 个小版本 |
| `backend/03-Domain-Model.md` | V5.9 | 2026-10-05 | 落后 9 个小版本 |
| `backend/04-Data-Model.md` | V5.5 | 2026-10-14 | 版本号落后，但内容有 10-14 批次更新（见 B1 不一致） |
| `backend/12-Security-Model.md` | V5.4 | 2026-10-03 | 落后 |
| `backend/13-AI-Technology-Selection.md` | **V2.0** | **2026-07-19** | **落后约 3 个月**（ai-service 其后经历 ADR-039 四层收口、ADR-046 向量推荐、RAG 接入） |
| `backend/14-Service-Config-Guide.md` | — | 2026-10-05 | 端口表**与代码完全一致** ✓（14 个服务端口逐一核对通过） |
| `frontend/15` | — | **2026-06-28** | 最旧 |
| `frontend/17` / `frontend/18` / `product/16` | V1.0 | 2026-08-02 | 落后（详见 §六） |

> **未在本轮逐条验证**：`03-Domain-Model.md` 的聚合/端口清单逐类差集、`12-Security-Model.md` 的威胁条目逐条差集、`02-Architecture.md` 的服务描述与代码逐条差集。这三项的版本滞后已确认，**逐条差集建议作为下一轮任务**（见 §八）。

---

## 五、C 类：代码侧硬约束核查（`collaboration/09-constraints.md`）

| 约束 | 实测 | 判定 |
| --- | --- | --- |
| `@Transactional` 必须带 `rollbackFor = Exception.class` | 带 `rollbackFor` 84 处；**不带 0 处**（初筛 14 处经复核全为 javadoc 注释） | 🟢 合规 |
| 禁止裸 `throw new RuntimeException` | 0 处 | 🟢 合规 |
| Controller 不得注入 Mapper / RedisTemplate | 0 个 Controller 命中 | 🟢 合规 |
| `/internal/**` 不得返回 `Result<T>`（ADR-004） | 0 处返回 `Result` | 🟢 合规 |
| 金额禁止 float/double/BigDecimal | 18 处 `BigDecimal`，**全部合法**：`AddressP*`/`AddressReq`/`AddressResp` 的经度/纬度；`ExchangeRatePullJob` 汇率解析（文件内明写「定点整数纪律唯一豁免边界」并立即 `movePointRight(6).longValueExact()`）；`WechatBillCsvParser` 注释声明刻意不用 | 🟢 合规 |
| **domain 层不得 import Spring/MyBatis/Feign** | 🔴 **18 个文件违规** | 🔴 违规 |
| 方法 ≤ 50 行 / 类 ≤ 300 行 | 未逐文件统计（本轮未做） | ⚪ 未验证 |

**domain 层 MyBatis-Plus 泄漏清单**（`import com.baomidou.*`，即 `IPage`/`Page` 出现在领域层）：

```
item-service      :: domain/repository/{Note,PairingRule,Product,Review,Sku}Repository.java
marketing-service :: domain/repository/{GroupBuyActivity,PointsLog,TaskDef}Repository.java
pay-service       :: domain/repository/ReconciliationRepository.java
trade-service     :: domain/enums/{OrderStatusEnum,SettlementStatusEnum,WithdrawalStatusEnum}.java
trade-service     :: domain/repository/{AfterSale,Invoice,Order,Settlement,Withdrawal}Repository.java
wallet-service    :: domain/repository/CurrencyRepository.java
```

**判定**：与 `CLAUDE.md`「Domain aggregates are plain POJOs (no Spring/MyBatis/Feign imports)」「`domain/enums/`（framework-free）」直接冲突。其中 `domain/repository/*` 是端口接口，引入 `IPage` 使分页形状绑定到 MyBatis（前端契约 §1.4 的 `records/total/size/current/pages` 正源于此）。**direction**：`fix-code`（领域端口改用自定义 `PageResult<T>`）；**若团队有意接受该务实豁免，则须在 `09-constraints.md` 显式登记豁免**（`discuss`）。注意 `architecture-tests` 模块（ArchUnit，19 个模块之一）当前**未拦下**这类依赖。

### C2. 🔴 横切运行时缺陷（第二轮并行审计发现，本节逐条由本报告复核）

> 上表是**机械可查的约束合规**；本节是**运行时才会暴露、静态约束拦不住**的缺陷——它们全部逃过了 1699 个单元测试（跨服务/跨网关/序列化边界不被 Mockito 覆盖）。每条均已由本报告用命令复核。

| # | 严重度 | direction | 缺陷 | 复核证据 |
| --- | --- | --- | --- | --- |
| C2-1 | 🔴 blocker | `fix-code` | **12 个营销管理端端点经网关不可达**。marketing-service 有 5 个 `/api/admin/*` 控制器，但网关只为 `/api/admin/coupon/**` 声明了谓词；`/api/admin/{flash,promotion,task,group-buy}/**` **谓词数 = 0**，会落入兜底路由 `admin-user-routes`（`Path=/api/admin/**` → `lb://user-service`，application.yml:138-140）→ user-service 无此 handler → **404**。契约 §4.13 中 AD40-42 / AD48-50 / AD51-53 / AD54-56 全部标 ✅ | 受影响端点实测 12 个：`POST|PUT|DELETE /api/admin/{flash,group-buy,promotion}` + `GET|POST /api/admin/task` + `PUT /api/admin/task/{code}`（`AdminFlashController.java:53,60,69` 等）；网关 5 个前缀谓词计数 `admin/flash=0 admin/promotion=0 admin/task=0 admin/group-buy=0 admin/coupon=1` |
| C2-2 | 🔴 blocker | `fix-code` | **12 个管理端端点无鉴权**：4 个 admin 控制器缺 `@PreAuthorize`（其余 admin 控制器均有）。任一经认证用户即可生成结算单、审批提现、重试退款、审批售后、执行对账、读操作日志 | 缺 `@PreAuthorize`：`pay/adapter/admin/AdminPayController`（4 端：退款重试/对账批次/批次明细/对账执行）、`trade/adapter/admin/AdminAfterSaleController`（3 端）、`trade/adapter/admin/AdminFinanceController`（4 端）、`user/adapter/web/admin/AdminLogController`（1 端）= 12 |
| C2-3 | 🔴 blocker | `fix-code` | **跨服务序列化命名策略不一致，站内信链路失效**：4 个服务配 `spring.jackson.property-naming-strategy: SNAKE_CASE`（authorization / notification / verification / wallet），其余 11 个不配。站内信接收方 notification-service 是 SNAKE_CASE，而**全部 4 个调用方都不配**（item / merchant / pay / trade）→ 发 camelCase、收 snake_case | `InAppMessageSendReqCO` 无 `@JsonProperty`/`@JsonNaming` 兜底；`recipientId` 带 `@NotNull` → 反序列化为 null → 400。调用方：`item ReviewManager`、`merchant StoreFollowManager`、`pay PayEventNotifier`、`trade TradeEventNotifier`；接收方 `InternalNotifyController.java:58-59` + notification `application.yml` SNAKE_CASE。**影响 ADR-042 买家站内信（支付/退款/发货/售后）与店铺关注通知** |
| C2-4 | 🔴 blocker | `fix-code` | **`GET /api/user/address/{id}` 无归属校验（IDOR）**：仅接 path id，无 `UserContext`；仓储为裸 `selectById(id)`；同文件其余 5 个方法均取 `userId` | `AddressController.java:48-50`；`AddressManager.getById`→`AddressRepositoryImpl` 裸查询；`AddressRepository` javadoc 自认「归属校验由调用方按需进行」。`AddressResp` 回吐 receiver/phone/省市区街道详址全量 PII；`12-Security-Model.md:122` 声称有「仓储 SQL WHERE userId 兜底」——**与代码不符** |
| C2-5 | 🔴 blocker | `fix-code` | **人机验证参数口径与实现不符 → 开启风控后登录死循环 1016**：契约写扁平 `lotNumber/captchaOutput/passToken/genTime`，代码只认**嵌套** `captchaParams` Map；`captchaParams` 在契约全文 **0 次出现** | 契约 :240/:277-279/:324 均为扁平；`grep -c captchaParams 05-API-Specification.md` → 0；`BaseLoginReq` 仅 `captchaProvider` + `Map captchaParams`；`CaptchaVerifyParam.of()` 空 Map 返回 null → `RiskAssessment.captchaVerified=false` → `CaptchaHandler` 抛 1016。**附带**：authorization-service 亦为 SNAKE_CASE，实际线字段名为 `captcha_provider`/`captcha_params`，而契约 A1 请求体写的是 camelCase（`identityType`/`credentialType`/`deviceId`）——**第二层漂移** |
| C2-6 | 🟠 major | `fix-doc` | **契约 §5.3 STOMP 目的地写错**：契约 `/app/chat.send` + 消息体 `ChatMessage`；代码 `@MessageMapping("/chat-send")` → `/app/chat-send`，body 为 `ChatSendReq` | 契约 :2293-2295 vs `ChatMessageController.java:53`。按契约实现 IM 发消息必然失败 |
| C2-7 | 🟠 major | `fix-code` | **`PUT /api/user/address/update/{id}` 强制 body 携带 `id`**：`AddressReq.id` 标了 `groups = UpdateGroup.class` + `@NotNull`，而 id 由 path 提供 → 按契约（body 不含 id）实现必 400 | `AddressReq.java:13-14`、`AddressController.java:54-57`、`GlobalExceptionHandler` 映射为 1101。现有前端 `use-address.ts:27-28` 正是从 body 剔除 id → 稳定 400 |
| C2-8 | 🟠 major | `fix-code` | **两处越权写**：`PUT /api/item/spu/{spuId}/status`（上下架/删除他人商品，无 sellerId 校验）、`/api/item/category` CRUD（无管理员校验，P11 为物理删除） | `ProductController.java:113-117` + `ProductManager.updateProductStatus` 签名无 sellerId（对比 `updateProduct` 有归属断言）；`CategoryController.java:14-40` 全类无 `@PreAuthorize`。契约 :622 P5 标 🏪、:686-688 P9-P11 标 🔑；`09-constraints.md:256` 明列「商家只能操作自己的商品」 |
| C2-9 | 🟠 major | `fix-code` | **Feign 声明落空**：ia-api `CouponClient.batchGet` 指向 `/internal/coupon/batch`，服务端 `InternalCouponController` **0 个 batch 方法** → 404 → 被 fallback 吞掉 → 多券叠加校验静默跳过 | `CouponClient.java` `@GetMapping("/batch")`；`grep batch InternalCouponController.java` → 0 |
| C2-10 | 🟠 major | `discuss` | **类目枚举/取值口径漂移**：契约 P5 写 `status: 1上架/0下架`，代码与库表为 `1上架/2下架/3删除`（无 0） | 契约 :622 同行；`ProductStatusEnum.java:16-18`；`Initialize.sql:266` 列注释 |

**修复方向汇总**：C2-1 / C2-2 / C2-3 / C2-4 / C2-5 / C2-7 / C2-8 / C2-9 为 `fix-code`（网关补 4 条路由谓词、补 `@PreAuthorize`、统一命名策略或给跨服务 DTO 加显式 `@JsonProperty`、补归属校验）；C2-6 为 `fix-doc`；C2-10 需产品定枚举口径。

---

## 六、D 类：根文档自相矛盾

| 事实 | 各处声明 | 实测 | direction |
| --- | --- | --- | --- |
| 表数 | `CLAUDE.md:71` / `AGENTS.md:71`「**57 张表**」；`CLAUDE.md:245,325` / `AGENTS.md:246,327`「**62 tables**」；`product/01-PRD.md:622`「57 张表、240 端点」；`04-Data-Model.md:28`「45 张表」 | **62** | `fix-doc`（全仓统一为 62） |
| Maven 模块数 | `CLAUDE.md`/`AGENTS.md`/`00-README.md`「**18 Maven modules = 15 服务 + 3 共享库**」 | pom `<modules>` = **19**（15 服务 + 3 共享库 + **architecture-tests**）；`database` 非模块 | `fix-doc`（改 19 并点名 architecture-tests） |
| 测试数 | `CLAUDE.md`「1690 tests / 18 modules」；`00-README.md:152`「**590 测试全绿**」 | `@Test` 注解 **1699** | `fix-doc`（`00-README` 的 590 严重过期） |
| 端点数 | `CLAUDE.md`「~278 HTTP endpoints (incl. 57 `/internal/**`)」；契约头部 278/57 | **286**（内部 61） | `fix-doc` |
| 网关路由 | `CLAUDE.md`「17 route blocks / 34 path predicates」 | **19 / 44** | `fix-doc` |
| `architecture-tests` 模块 | 三处服务地图**均未提及** | 存在且含 ArchUnit 规则 | `fix-doc` |
| ADR 索引 | `adr/README.md` 头部日期 **2026-03-01** | `adr/` 实际有 001–053 | `fix-doc`（索引未随 ADR-045–053 更新） |

> `AGENTS.md` 与 `CLAUDE.md` 大量内容逐字重复（含同一处「57 张表」错误），建议确立单一事实源、另一份改为引用（`discuss`）。

---

## 七、E 类：前端现状（摘要）

完整分析见 [21-Frontend-Refactor-Readiness.md](../frontend/21-Frontend-Refactor-Readiness.md)。核心事实：

| 项 | 实测 |
| --- | --- |
| 源文件总数 | **159**（无 `node_modules` 完整安装，部分 `packages/*/node_modules` 存在） |
| `apps/web` | **59 文件 / 5,128 行**（约 25 个路由页：marketplace / product / search / cart / checkout / orders / pay / after-sale / user 8 页 / auth 2 页） |
| `apps/miniprogram` | 48 文件 / 1,977 行（Taro） |
| `apps/admin` | **9 文件 / 93 行 — 纯骨架** |
| `apps/seller` | **8 文件 / 85 行 — 纯骨架** |
| `apps/mobile` | 1 文件 / 0 行 — 空占位 |
| `packages/api` | 23 文件 / 1,178 行（15 个 hooks + **仅 24 个 TS 类型声明**） |
| DTO 代码生成 | **无**（无 openapi/orval/swagger codegen 配置）→ 手写类型，必然漂移 |
| 前端调用的后端路径 | 其中 **9 条调用在后端不存在**（见 21 文 §三） |
| `error-codes.ts` | 映射 `1004`（**枚举中不存在**；真实流控码为 `1014`）；`1102` 语义可接受 |
| Token 存储 | `access_token` + **7 天有效期的 `refresh_token` 均存 JS 可读 Cookie**（`token.ts` 自注 XSS 暴露面） |
| React 版本 | 实际 `react ~18.2.0` + 根 `pnpm.overrides` 强制锁 `18.2.0`；而 `frontend/15` 声明 **React 19** |

---

## 八、修复清单（按优先级）

| # | 严重度 | direction | 位置 | 修复动作 |
| --- | --- | --- | --- | --- |
| 0 | 🔴 blocker | `fix-code` | 网关 `application.yml`（§C2-1） | 为 `/api/admin/{flash,promotion,task,group-buy}/**` 补 4 条采购路由谓词（须先于 `Path=/api/admin/**` 兜底）；否则 12 个管理端点在网关 404 |
| 1 | 🔴 blocker | `fix-code` | 4 个 admin 控制器（§C2-2） | 补 `@PreAuthorize`：`AdminPayController` / `AdminAfterSaleController` / `AdminFinanceController` / `AdminLogController`（12 个端点当前任一经认证用户可调） |
| 2 | 🔴 blocker | `fix-code` | 命名策略 / 跨服务 DTO（§C2-3） | 统一 `property-naming-strategy`，或给 `InAppMessageSendReqCO` 等跨服务 DTO 加显式 `@JsonProperty`；否则站内信链路（ADR-042）失效 |
| 3 | 🔴 blocker | `fix-code` | `AddressController`（§C2-4） | `GET /api/user/address/{id}` 补归属校验（或新增带 userId 作用域的读方法，注意 `/internal/address/{id}` 复用同一 VO） |
| 4 | 🔴 blocker | `fix-code` | 人机验证参数口径（§C2-5） | 契约与代码二选一：改代码收扁平四要素，或改契约写嵌套 `captcha_params` + snake_case 字段名 |
| 5 | 🔴 blocker | `fix-doc` | 契约 §4 A10/U5/U12/U13/C7/M6/CH6-9/AD18/AD19/I29 | 13 条标「已实现」但代码不存在的端点：更正为真实路径或退回 `⚪` |
| 6 | 🔴 blocker | `fix-doc` | 契约 §4 缺失 | 补录 `POST /api/verification/codes`（A10/U5 的活替身） |
| 7 | 🔴 blocker | `fix-doc` | 契约 §4 端点表 + §七 | 33 条 `⚪ 计划中` **保留**在端点表内但**显式分区/加状态列**（契约=规格，不移出）；§七 的 9 条计划项加 `⚪` 标注；头部计数按状态拆分为「已实现/计划中」 |
| 8 | 🔴 blocker | `fix-doc` | 契约 :3 | 头部「内部 57 个」与「内部 39+」矛盾，改为实测三元组 |
| 9 | 🟠 major | `fix-code` | 商品/类目控制器（§C2-8） | `PUT /api/item/spu/{spuId}/status` 补 sellerId 归属校验；`/api/item/category` 写操作补管理员校验 |
| 10 | 🟠 major | `fix-code` | ia-api `CouponClient`（§C2-9） | `/internal/coupon/batch` 服务端缺失 → 补实现或移除声明（当前被 fallback 静默吞掉） |
| 11 | 🟠 major | `fix-code` | `AddressReq`（§C2-7） | `PUT /api/user/address/update/{id}` 的 body 强制 `id` → 改为 path 优先或去掉 `UpdateGroup` 的 `@NotNull` |
| 12 | 🟠 major | `fix-doc` | 契约 §六 | 补 6 个缺失路由前缀、更正 `/api/invoice/**` 与 `/ws/**` 目标服务、删 `/oauth2/**` 与 `/user/**`、显式化路由顺序 |
| 13 | 🟠 major | `fix-doc` | 契约 §二 | 错误码区间 `3000-3039` → `3000-3041`；补 71 个未文档化码或声明子集口径 |
| 14 | 🟠 major | `fix-doc` | `04-Data-Model.md` | 45 → 62 张表；清除 7 处 `migrations/V*.sql` 死链；补 `shop_decoration` 等表 |
| 15 | 🟠 major | `fix-doc` | `CLAUDE.md`/`AGENTS.md`/`00-README.md`/`PRD` | 统一 62 张表 / 19 模块 / 286 端点 / 19-44 路由 / 1699 测试；提及 architecture-tests |
| 16 | 🟠 major | `fix-code` 或 `discuss` | 18 个 domain 文件 | 领域端口去除 MyBatis-Plus `IPage`；或登记豁免并让 ArchUnit 放行 |
| 17 | 🟠 major | `discuss` | CH6–CH9 + `merchant_reply_template` + 契约 §4.12.3 | 话术模板功能：补实现，还是删表删契约 |
| 18 | 🟠 major | `fix-doc` | `backend/13` | V2.0（2026-07-19）落后约 3 个月，需对齐 ADR-039/046 与 RAG 现状 |
| 19 | 🟡 minor | `fix-doc` | 契约 §4.15 | 发票小节补 INV1–9 / AD57–60 / I56 / I60 |
| 20 | 🟡 minor | `fix-doc` | `adr/README.md` | 索引补 045–053 |
| 21 | 🟡 minor | `fix-doc` | 契约 §4.14 | 补录 3 个 `/internal/auth/*`（契约自己已登记为待办） |
| 22 | 🟡 minor | `fix-doc` | `frontend/15` | React 19 声明 → React 18.2（或反过来升级代码，二选一） |

### 实施进度（批次① 已落地，2026-10-15）

用户确认执行「批次①（纯缺陷、不依赖命名/规格决策）」后已改动如下，**全部通过编译与全量测试**（888 个测试：ia-common 76 / ia-integration 66 / ia-api 14 / user 78 / pay 128 / trade 343 / item 183，0 失败；另 gateway-service 74 个亦全绿）：

| # | 状态 | 改动 | 验证 |
| --- | --- | --- | --- |
| 0 | ✅ 已修 | `gateway-service/application.yml` 新增 `marketing-admin-routes` 块（`/api/admin/{flash,promotion,task,group-buy}/**` → `lb://marketing-service`），置于 `admin-user-routes` 兜底之前；路由块 19 → **20** | YAML 解析校验 + gateway 74 测试 |
| 1 | ✅ 已修 | `AdminAfterSaleController` / `AdminFinanceController` / `AdminLogController` 类级补 `@PreAuthorize("@ss.hasPermi('user:admin')")` | trade/user 测试全绿 |
| 1 | ✅ 已修（**范式不同**） | `AdminPayController`：pay-service **未引入 `spring-boot-starter-security`**（ia-common 中该依赖 `<optional>true</optional>` 不传递），`@PreAuthorize` 在此不可用 → 按 merchant-service 既有范式改用 `UserContext.hasPermission("user:admin")` + `ForbiddenException`（4 个方法各加 `assertAdmin()`） | pay 128 测试全绿 |
| 3 | ✅ 已修（**含新发现的第二向量**） | 除公开端点 `GET /api/user/address/{id}` 改走新增的 `AddressManager.getOwnedByUser` 外，**另发现下单路径同款 IDOR**：trade 的 4 个下单策略以 `addressId` 调 `/internal/address/{id}` 且不校验归属，可把他人收货人 PII 快照进自己订单。修复：`AddressCO` 增 `userId`（增量、向后兼容）+ `OrderCreateContext.bindAddress()` 单点校验（含 null 与归属两重断言） | trade 343 测试（含新增越权回归用例）；user 78 全绿 |
| 9 | ✅ 已修 | `ProductManager.updateProductStatus` 增加 `operatorId`/`isAdmin` 与归属断言（复用既有 `Product.isOwnedBy`；管理员放行，对应契约 P5）；`CategoryController` 三个写操作补**方法级** `@PreAuthorize`（**不能用类级**——会连带封掉公开的 `GET /tree`） | item 183 测试（含新增越权拒绝 + 管理员放行 2 个用例） |
| 11 | ✅ 已修（**顺带补第二个缺陷**） | `AddressReq`：移除 `id` 的 `@NotNull(groups = UpdateGroup)`（契约 U7/U9 的 body 无 id，按契约实现此前必 400）；**并发现六个必填字段（phone/province/city/district/street/detail）未标 groups → 属 Default 组，而两端点均按显式组校验 → 等于从未被校验**，已显式归入 Create/Update 两组对齐契约 U7 | user 78 测试全绿（无测试引用该 DTO） |
| 10 | ⛔ **未实施，需决策** | 见下方 | — |

### ⛔ #10 的实际情况与 ADR-038 的错误陈述（**需用户决策**）

原计划「补 `/internal/coupon/batch` 服务端实现或移除 Feign 声明」。核查后发现这**不是缺陷修复，而是一个跨层未实现的功能**：

1. `coupon` 表**已有** `stack_rule` / `stack_group` 两列（`Initialize.sql:543-544`）；
2. 但 `ADR-038:39` 自己承认：「`coupon` 表的 scope_type/scope_values/**stack_rule/stack_group** 四列**从未映射进 CouponPO/聚合**——建券时这些维度静默落 DB 默认值（scope_type=1 ALL、stack_rule=1 MUTUAL_EXCLUSIVE）」；
3. **而 ADR-038 同句的推论是错的**：它写「下单侧 stack 校验走 marketing 的 `batchGet` 内部接口返回的 PO 字段，**不受影响**」——实测 `InternalCouponController` **根本没有 `/batch` 端点**，Feign 调用 404 后被 `CouponClientFallback.batchGet` 吞成空列表，`PricingService.validateStackRules` 首行即 `return`。**即该叠加校验自 V4.3 起从未执行过**。
4. 入参语义也已查清：`batchGet` 收到的是 **`user_coupon.id`**（与 `/use` 的 `userCouponId` 同口径），而 `CouponStackInfoCO.id` 注释写「coupon.id」——两者不一致。

**因此有三条互斥的路，需你选**：

- **(a) 全量补实现**：补 `coupon` 四列的 PO/聚合映射 + `couponRepository` 批量查询 + `/internal/coupon/batch` + 修正 CO 注释 + 测试。**副作用：会首次激活沉睡的叠加校验**，此前能成功的「互斥券叠加」下单将开始被拒（这正是 V4.3 的本意，但属行为变更，需与 QA 同步）。
- **(b) 仅删死代码**：移除 `CouponClient.batchGet` 声明 + `validateStackRules` 调用 + fallback 分支。零行为变更，代码变诚实，但放弃既定业务规则。
- **(c) 暂缓并登记**：保持现状，在 `ADR-038` 更正「不受影响」的错误陈述，并把「券叠加维度未落地」登记为显式技术债（含 `scope_type`/`scope_values` 同属未映射）。

> 我倾向 **(a)**，因为它同时修掉「管理员建券输入的 stackRule/stackGroup 被静默丢弃」这一数据丢失问题；但正因涉及行为变更，按你的要求先沟通再实现。

> **⚠️ 工作区并发变更提示（2026-10-15 核实）**：本次改动之外，工作区还存在**非本报告作者所产生**的未提交变更——`ADR-054: 前端多平台化 —— 平台轴与文档结构`（未跟踪）、`project-docs/frontend/18-platforms/`（web/desktop/android/ios/miniprogram 五篇）、`Implementation/front-end/scripts/check-design-tokens.mjs`、以及被删除的 4 个 `tailwind.config.ts`。即**前端域正在被另一个会话重构为五平台**。
>
> 两点影响：① 本报告 §七 与 [21](../frontend/21-Frontend-Refactor-Readiness.md) 的前端文件清单/技术栈结论以**审计时点**为准，此后前端树已开始变动，引用前需复核；② 命名统一（[22 §4.6](../quality/22-API-Spec-BigTech-Conformance-Review.md)）的**后端部分与前端部分应拆开排期**——后端 4 个服务 + 契约不与之冲突，但前端类型/hooks/页面的字段改名应**并入** ADR-054 的多平台重构，而不是与之并行改同一批文件。

### 后续批次落地记录（2026-10-15 续）

**#10 优惠券适用范围与叠加规则** —— 用户选 (a) 全量补实现，已完成（详见 [ADR-055](../adr/055-coupon-scope-and-stack-rule-materialization.md)）：

- 打通 `coupon` 四列 `scope_type`/`scope_values`/`stack_rule`/`stack_group` 的 PO/聚合映射（此前**建券时被静默丢弃**）；
- 新增 `GET /internal/coupon/batch`（I46，入参为 `user_coupon.id`），补上 `CouponClient.batchGet` 长期 404 的服务端；
- `/internal/coupon/use` 补齐 `productIds`/`categoryIds` 两参数并实施**适用范围校验**（此前该两参数被 Spring 静默忽略 → 范围限定券可用于任意商品）；
- 另新增领域服务 `CouponScope`（JSON 形态容错，零框架）、`UserCouponRepository.findByIds`（避免 N+1）；
- 验证：marketing **229**（基线 190 + 39 新增）/ trade **343**（零回归）。
- ⚠️ **行为收紧，需业务确认**：叠加校验与范围校验自 V4.3 起**从未生效**，本次首次激活 —— 存量券因历史默认值（`scope_type=1 ALL`、`stack_rule=1 MUTUAL_EXCLUSIVE`）将使**存量多券订单开始被拦截**。
- ⚠️ 子代理登记但**未修**的缺口：`GET /internal/coupon/batch` 缺 `ids` 时返回 **500**（`GlobalExceptionHandler` 无 `MissingServletRequestParameterException` 出口）→ 应为 400；修点在共享内核、影响全部服务，建议另立任务。

**批次②（命名统一到 snake_case）后端部分** —— 用户选方案 B + 先做后端，已完成：

| 项 | 结果 |
| --- | --- |
| 契约迁移 | 两轮共 **673 处**（323 行 + 256 行）；`naming-audit.mjs` 复核 **同名异形 = 0** |
| 服务配置 | **15/15** 声明 `SNAKE_CASE`（新增 11 个：ai/cart/item/logistics/marketing/merchant/pay/search/trade/user/gateway） |
| 清理冲突钉名 | 删 wallet 的 4 处 camel 强制钉名 + `CurrencyWireNamingConfig` mixin 类（全平台 snake 后该桥接反向冲突） |
| 新增守卫 | `architecture-tests/.../WireNamingConventionTest`（断言 15 服务均 snake；补上「**全仓 0 个 `@WebMvcTest`、21 个 MockMvc 全走 `standaloneSetup` → 真实报文格式无任何测试覆盖**」这一盲区） |
| 全量验证 | `mvn clean test` → **1772 tests / 0 failures / 0 errors / 1 skipped**，20 模块全 SUCCESS |
| 未做（有意） | 前端字段改名（并入 ADR-054）；path 变量名保持 camelCase（跟随 `@PathVariable`，不属线上字段，已写入 [22 §六](../quality/22-API-Spec-BigTech-Conformance-Review.md) 口径说明） |

**审计工具归位** —— 用户指出原 `audit-out/`（我新建的顶层目录）未被登记也未忽略，`git add -A` 会一并提交。已按方案处置：

- 3 个可复用脚本迁至既有登记位置 `Implementation/back-end/scripts/`（先例 `check-id-invariants.sh`），并改为**以自身位置推导仓库根**（任意 cwd 可跑）+ **默认只打 stdout**（需要落盘用 `--out=<dir>`）；
- 6 个生成物与 3 个一次性脚本已删除；`audit-out/` 目录已移除；
- 3 篇文档的 5 处引用已同步改写，报告不再引用任何提交进仓库的生成物。

**fix-doc 批次交付与独立复核**（2026-10-15）

- 契约头部改为**可核验三元组 + 复核表**；全部端点行新增**「状态」列**（`✅` 已实现 / `⚪` 计划中 / `⚠️` 标已实现但缺失），**33 条计划端点按要求保留在表内**未移出；
- **`codeOnly`（代码有、契约无）7 → 0** —— 13 条「标已实现但不存在」逐条更正或退回 `⚪`，另补录 I46/I66–I69、`POST /api/verification/codes`、`/api/sign*` 等；
- §六 网关表按 `application.yml` 实测重写（20 route blocks / 48 谓词、删虚构的 `invoice-service` 与 `/oauth2/**`、补路由顺序警示）；§二 错误码区间 `3000-3039 → 3000-3041`；
- `04-Data-Model` 45 → **62 张表**、`migrations/V*.sql` 死链清零、补 5 张缺条目表；根文档计数统一为 62 表 / 19 模块 / 287 端点 / 20 路由块 / 1775 测试；
- 复核方式：以上计数均由本报告 §十一 的三个脚本**独立重跑**确认，非采信执行方自述。

**本轮发现并修复的 SSOT 损坏（原审计未覆盖）**

- 契约 §四 的**表格列错位**：91/92 张端点表的**数据行带了「状态」值，表头却没有「状态」列**。GFM 按位置渲染并丢弃多余单元格 ⇒ 状态值会顶到「阶段」表头下、**真正的阶段值在渲染中丢失**。脚本读行数据一切正常，只有人眼看文档才会发现；
- 修复方式：给 91 个表头 + 91 个分隔行**插入完整单元格**（置于 `阶段` 之前，末列非阶段则追加），**保留原有列对齐空格与 CRLF**。改动 **182 行 / Δ1365 字节**；文件仍是 2818 行 / 2818 CRLF；`extract-endpoints.mjs` 语义不变（matched 287 / codeOnly 0 / docOnly 43）；
- 残留 1 张「错位」经查是**未转义 `|` 的解析假象**（如 `FULL_REDUCE\|LIMIT_DISCOUNT`），非文档损坏。

**校验脚本可移植性**（两条线独立发现的缺陷）

- 三个前端脚本对 cwd 的假设互不一致：`check-frontend-api-paths.mjs` 用 `process.cwd()`；`check-error-codes.mjs` 的后端根与 TS 路径均为 cwd 相对；`crosscheck-api-paths.mjs` 无参即崩。**均已改为按脚本自身位置推导**，参数可省略；
- 现在 **6 个校验脚本（后端 3 + 前端 3）都能从仓库根直接运行**，且从 `Implementation/front-end` 调用仍兼容。

**命名决策已建档**：新增 `adr/056-wire-naming-snake-case.md`（三类载体、例外清单、大厂依据、守卫、未覆盖项），并登记入 `adr/README.md` 与根 `CLAUDE.md`/`AGENTS.md`（区间 001–056，56 文件 = 56 索引条目）。**此前 ADR 001–055 中无任何一篇记录该约定。**

### 字段级一致性：从「真空」到「有闸」（2026-10-18，C2 批次登记）

**两条审计发现（此前未登记，本次按代码与门禁脚本复核）**：

| # | 发现 | 证据 | 处置 |
| :-- | :-- | :-- | :-- |
| ① | **字段级一致性此前是真空**：仓库有 6 道机械闸，但它们只查**路径**（`check-frontend-api-paths.mjs` / `crosscheck-api-paths.mjs`）、**错误码**（`check-error-codes.mjs`）、**查询参数**（`check-query-param-naming.mjs`）、**端点漂移**（`extract-endpoints.mjs`）与**标识字段的类型 / 路径变量 / 编码出口**（`check-id-invariants.sh` 检查 1–6）—— **没有任何一道看「字段名/键名」的一致性** | [ADR-064](../adr/064-spu-name-wire-key-unification.md) §背景 2（逐闸列出检查面）+ `check-id-invariants.sh` 检查 7 注释块 | 已补第 7 道闸（见下节） |
| ② | **线上键缺陷在多数测试里不可见**：全仓 MockMvc 测试**绝大多数走 `standaloneSetup`**（Spring 上下文与 `spring.jackson.property-naming-strategy` **不加载**），报文里看到的只是 Java 字段名；`@WebMvcTest` 用例极少 ⇒ 「后端叫 `spuName`、线上键却没渲染成 `spu_name`」这类缺陷在常规单测里**不会红** | 现有测试树（`standaloneSetup` 广泛使用）；对照：断言线上键的用例**必须显式**装配 `new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)` | 已按此口径补专门断言：`SpuNameWireKeyTest`（item / search 各一份）、`SpuDetailWireShapeTest`、`IdWireRoundTripTest`（往返）、`InternalItemSpuControllerTest` |

**第 7 道门禁（`check-id-invariants.sh` 检查 7）—— SPU 名称字段线上键恒为 `spu_name`**

| 断言 | 内容 |
| :-- | :-- |
| A 命中面 | 线格式面（`adapter/{response,request}` + `ia-api` `dto`）内，「文件名或其中嵌套类型名含 `Spu`」的类 = SPU 名载体 |
| B 旧键禁令 | 载体类内不得再出现 `name` / `productName`（POJO 与 record 组件两种形态都抓；同行显式钉名 `spu_name` 例外）—— **真正的坑是两个键并存**，故 B 是负向断言 |
| C 清单齐全 | 10 个必检载体逐个点名（`SpuVO`/`RecommendSpuVO`/`NoteDetailVO`/`SpuSearchVO`/`SpuVectorUpsert{CO,Req}`/`SpuDetailCO`/`SpuSearchItemCO`/`CreateSpuReq`/`UpdateSpuReq`），文件缺失或未声明 `spuName` 即失败 |
| D 白名单陈旧即失败 | 唯一例外 `HistoryVO.productName` + `@JsonProperty("product_name")` 必须仍成立，被改名/删注解即红 |
| E 例外不得扩散 | 除 `HistoryVO` 外任何 `@JsonProperty("product_name")` 一律失败 |
| **F 命中面自证** | **载体类数 = 0 即失败** —— 防「零匹配却报 `[OK]`」的假绿（同检查 4/5 的自证纪律）；`[OK]` 后的自证行会打印命中数与「点名核对 10 个必检载体 / 白名单例外 1 条」 |

**两组变异验证方式**（证明门禁有捕获力，非空转 —— 与 [ADR-062](../adr/062-external-identifier-and-addressing.md) §B5、[ADR-063](../adr/063-external-channel-number-separation.md) §守卫 4 同一纪律）：

| # | 变异手法 | 期望 |
| :-- | :-- | :-- |
| ① | 把任一载体（如 `SpuVO`）的名字字段改回 `name` → 跑门禁 | 断言 **B** 报红并点名「文件:行」 |
| ② | 删掉某必检载体的 `spuName` 声明（或把载体文件改名到不再匹配 `*Spu*`）→ 跑门禁 | 断言 **C** 或 **F** 报红 |

> 两组都要跑「改回即红」**和**「恢复即绿」；只跑一半会分别漏掉假红与假绿。**本批（C2 文档批次）未执行变异**（`Implementation/**` 不含本批写权）。

**纪律：字段改名必须两端同 PR + 配置跨模块往返断言**

1. **两端同 PR**：字段改名必须在**同一个 PR** 内改完「发送端 + 接收端」—— 后端 VO/CO/DTO 与前端 TS 类型/读取点同批（[ADR-064](../adr/064-spu-name-wire-key-unification.md) §决策 1/6）。分开提交时两侧**各自都「合规」**（编译过、静态扫描过），而 Feign 解码只会**静默落 `null`**，不抛错。
2. **跨模块往返断言**：每次改报文名必须配一条「**发送端报文 → 接收端解码**」的往返用例，当前样板是 `IdWireRoundTripTest` 的 **`SpuVO ⇄ SpuDetailCO`**：`SpuVO` 渲染出的 JSON 必须**含顶层键 `spu_name`**（且**不含 `name`**），喂给 `SpuDetailCO` 后 `spuName` 解码结果**必须非空**。
   > 为什么必须是**顶层键**断言而不是「键集包含」：`SkuVO` 早就有 `spu_name`，只断言「键集里有」会掩盖 `SpuVO` 自身漏改。
3. **不要用前端兼容读**（`p.spu_name ?? p.name`）「修」这类问题：它把缺陷搬进前端并**永久化双键**，旧键永不删除、新缺陷永不暴露，同时让「字段级一致性」失去可断言性（ADR-064 备选方案 C / D 均被否决）。
4. **禁止顺手统一**：唯一登记例外 `HistoryVO.product_name` 是**故意的反向标记**（契约 §1.9④），门禁 D 项让该例外**必须持续成立**。


---

## 九、待决策事项（需人定，非审计可决）

1. **契约的「端点总数」是规格还是快照？** 若为规格（含未来端点），则本次不做项 3 的「移出」；若为快照（现状镜像），则 33 条必须移出。**这决定了后续「以文档为准生成代码」是否安全**。
2. **CH6–CH9 话术模板**：补实现，还是承认被 `/api/seller/knowledge` 取代并删除？
3. **domain 层 `IPage` 泄漏**：改代码（引入 `PageResult<T>` 端口）还是登记豁免？注意这会影响分页响应形状，进而影响前端。
4. **前端 Token 存储**：保持 JS 可读 Cookie（接受 XSS 面），还是改 httpOnly + BFF 代理？后者影响整个前端认证架构（见 21 文 §四）。
5. **`AGENTS.md` 与 `CLAUDE.md` 去重**：保留哪一份为单一事实源？
6. **跨服务线格式（§C2-3）**：统一 `property-naming-strategy`（改动面大、影响 4 个服务的对外报文），还是给跨服务 Feign DTO 逐个加显式 `@JsonProperty`（局部、可控）？**对外报文是否保持 snake_case 直接影响前端字段名**，须与 21 文 §四 决策 2 一并定。
7. **管理端鉴权模型（§C2-2）**：补 `@PreAuthorize` 到 4 个控制器即可，还是改为网关统一按 `/api/admin/**` 强制角色（防同类遗漏再次发生）？
8. **人机验证契约方向（§C2-5）**：改代码收扁平四要素（前端已按扁平实现），还是改契约写嵌套 `captcha_params`（改前端 + 修 snake_case 字段名）？
9. **`03-Domain-Model` / `12-Security-Model` / `02-Architecture` 的逐条差集**是否需要正式归档（见 §十）。

---

## 十、审计覆盖边界与未验证项

本审计采用**两轮**：第一轮为确定性静态取证（脚本 + 逐条 `read`，§三/§四/§五机械项/§六/§八 的计数全部可复现）；第二轮为 10 个切片并行的深度审计（30 个 agent，含对全部 blocker/major 的**证伪式对抗复核**），补出 §C2 的横切运行时缺陷与 §四/§七 的逐条镜像差集。

**第一轮已逐条取证**：契约 §四 全部端点（与代码双向 diff）、契约 §六 网关表、契约 §七 前端页面映射 148 个引用（§A8）、契约 §二 错误码（与枚举全量 diff）、`04-Data-Model` 表清单（与 SQL diff）、`14-Service-Config-Guide` 端口表（14 个服务全对）、根文档计数类声明、代码侧 6 条硬约束、前端源文件清单 + 前端调用的后端路径。

**第二轮已覆盖并复核**（结论见 §C2 与前文对应小节）：

| 覆盖项 | 结论位置 |
| --- | --- |
| 网关路由 × 端点归属（首命中路由 vs 控制器归属） | §C2-1 |
| 管理端鉴权普查（`@PreAuthorize` 逐控制器） | §C2-2 |
| 跨服务线格式（命名策略 × 调用方/接收方矩阵） | §C2-3 |
| 越权读/写普查（地址 / 商品状态 / 类目 CRUD） | §C2-4、§C2-8 |
| 人机验证参数口径（契约字段 vs DTO 字段） | §C2-5 |
| Feign 闭环（31 个 client / 62 方法 ↔ 服务端 handler） | §C2-9 |
| `03-Domain-Model` 聚合清单、`12-Security-Model` 威胁条目、`13-AI-Technology-Selection`、`09-constraints` 未实现清单 | 版本滞后已确认（§B4）；逐条差集结论以第二轮报告为准，**本报告未逐条转录** |
| DB 列级：53 个 PO / 567 字段 ↔ SQL 列（0 不匹配）、`BINARY(16)` 27 列、金额列类型、`@Version`/`@TableLogic` 清单 | 无漂移（与 §B1 的表清单缺口互补） |
| 方法 >50 行 / 类 >300 行 | 1 个方法超限、4 个类超限（均未落在 §九 修复清单） |
| 前端 DTO 覆盖（24 个手写类型 vs 契约 §八 35 个 CO/VO） | §七 |

**仍未取得结论（不得推断为「已同步」）**：

1. 契约 §八 核心 CO/VO 快速参考的**逐字段**比对（第二轮仅给出「35 个 VO 中 11 个同名对齐 / 3 个改后缀 / 24 个无对应类型」的覆盖度统计，未逐字段核字段名）。
2. `12-Security-Model.md` 与 `02-Architecture.md` 的**逐条**差集（版本滞后与若干具体不符点已确认，完整清单未转录）。
3. 各服务 `application.yml` 与 `14-Service-Config-Guide` 的**配置键级**全量差集（第二轮给出若干具体不符点，未穷举）。
4. 62 张表的**索引/约束**级比对（列级与主键已核，索引与 FK 未穷举）。
5. `architecture-tests` 的 ArchUnit 规则与 `09-constraints` 声明之间的**逐条**对应（已确认 ArchUnit 仅落地 4 条规则、多数声明无构建期守卫）。

> 第二轮完整发现（186 条，含 27 blocker / 89 major）体量超出本文档承载范围，原始输出为会话内产物。**如需正式归档，建议按切片拆分为 `quality/20a`–`20j`，或作为 §九 修复清单的工单来源。**

---

## 十一、可复现附件

审计工具已并入仓库既有的**校验脚本位置** `Implementation/back-end/scripts/`（先例：`check-id-invariants.sh`），
**不新增顶层目录、不在仓库留下生成物**（脚本默认只打 stdout；需要落盘时用 `--out=<dir>`，由使用者自行决定写到哪）。

| 脚本 | 用途 |
| --- | --- |
| `Implementation/back-end/scripts/extract-endpoints.mjs` | 提取代码侧 Spring 端点 + 契约侧端点表 → 输出双向差集（代码有/契约无、契约有/代码无）与 `docOnly` 分类（计划中 vs 标注已实现但缺失） |
| `Implementation/back-end/scripts/check-section7.mjs` | 校验契约 §七 前端页面映射引用的 148 个端点是否真实存在 |
| `Implementation/back-end/scripts/naming-audit.mjs` | 契约字段命名一致性分析（camelCase/snake_case 分布、同名异形组） |

```bash
# 从仓库根执行（脚本以自身位置推导仓库根，任意 cwd 均可）
node Implementation/back-end/scripts/extract-endpoints.mjs
node Implementation/back-end/scripts/check-section7.mjs
node Implementation/back-end/scripts/naming-audit.mjs
# 需要留存证据时（写到构建目录，勿提交）：
node Implementation/back-end/scripts/extract-endpoints.mjs --out=Implementation/back-end/target/audit
```

> 报告正文中的计数（286 / 326 / 47 / 19 等）均由上述脚本产出，重跑即可复现；
> 本报告不再引用任何提交进仓库的生成物。
