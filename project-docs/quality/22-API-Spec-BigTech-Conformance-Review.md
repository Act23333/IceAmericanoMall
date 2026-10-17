# 22 — API 规格大厂规范符合度评估与字段命名方案

> 归属域: quality | Owner: doc-engineer | 更新触发条件: 契约规格修订 / 命名方案定稿（定稿后应转 ADR）时
>
> **日期**：2026-10-14 | **前提**：用户确认 `contract/05-API-Specification.md` 是**规格（spec）**，不是现状快照
> **性质**：**评估与建议**——本文只判断与推荐，不含实现。结论需与用户确认后再落地（用户明确要求「如果不符优先与我沟通，然后再实现」）
> **上游**：[20-Doc-Code-Consistency-Audit.md](20-Doc-Code-Consistency-Audit.md)（漂移事实）、[21-Frontend-Refactor-Readiness.md](../frontend/21-Frontend-Refactor-Readiness.md)（重构准入）
> **附件**：`Implementation/back-end/scripts/naming-audit.mjs`（契约命名一致性分析）、Implementation/back-end/scripts/naming-audit.mjs（snake 标识符归因：线上字段 vs DB/ES/第三方名），均可重跑

---

## 一、评估基准（仅用可查证的一手规范）

| 基准 | 关键结论 | 出处 |
| --- | --- | --- |
| **Google AIP-122** | 集合标识符**必须**用**复数**、**必须** camelCase（`publishers`/`books`） | [AIP-122](https://google.aip.dev/122) |
| **Google AIP-140** | 字段在 proto 中**必须** `lower_snake_case`，这些名字**再映射**为 JSON 与生成代码的命名约定；字段名**必须**是名词，不能是动词 | [AIP-140](https://google.aip.dev/140) |
| **ProtoJSON** | 消息字段名**映射为 lowerCamelCase** 作为 JSON key；解析器同时接受原 proto 名 | [ProtoJSON](https://protobuf.dev/programming-guides/json/) |
| **Microsoft / .NET** | 「Web 默认命名策略是 camelCase」 | [MS Learn](https://learn.microsoft.com/en-us/dotnet/standard/serialization/system-text-json/customize-properties) |
| **Stripe / GitHub / OpenAI / Twilio** | JSON key 用 **snake_case**（`created_at`）；AWS 用 PascalCase | [Moesif](https://www.moesif.com/blog/technical/rest-api-naming-conventions/) |
| **综合判断（业界共识）** | camelCase 与 snake_case **都可行**；**唯一不可接受的是同一个 API 面里混用** | [Apidog](https://apidog.com/blog/rest-api-naming-conventions/) |
| **RFC 6749 §5.1** | OAuth2 令牌响应字段固定为 `access_token`/`token_type`/`expires_in`/`refresh_token` —— **协议强制，不可改** | RFC 6749 |

**结论先行**：命名风格**没有唯一正确答案**，但有三条所有大厂都遵守的硬规则，本项目**三条都违反了**：

1. 同一 API 面内命名风格**统一**（本项目：4 个服务 snake、11 个服务 camel，且契约文档自身混用）；
2. **线上格式与存储/语言格式解耦**，在边界做一次转换（本项目：靠全局配置，没有边界转换层）；
3. **例外必须显式且可枚举**（本项目：例外是隐式的、且有一个漏了——见 §四·4.2）。

---

## 二、符合度结论摘要

| 维度 | 判定 | 一句话依据 |
| --- | --- | --- |
| 资源命名（集合名） | 🔴 **不符合** | 契约 §1.1 明文规定「资源名使用**单数**名词（`/api/item/spu`）」；AIP-122 要求**复数**。代码中 53 个端点为此形态 |
| 标准方法（CRUD 语义） | 🟠 **部分不符合** | 54/255 个端点路径含动词段（`/update/`、`/delete/`、`/page`、`/list`） |
| 分页 | 🔴 **不符合（框架泄漏）** | 契约 §1.4 的 `records`/`current`/`pages` 是 **MyBatis-Plus `IPage` 的内部字段名**直接透出；28 个端点返回 `Result<IPage<…>>`。大厂用游标（`pageToken`/`nextPageToken`）或 `offset`/`limit` |
| 错误语义 | 🟠 **有意偏离（需登记为决策）** | 契约 §1.3：业务错误一律 **HTTP 200 + body `code`**（3000-3041）。Google AIP / Microsoft 要求真实 HTTP 状态码。这是国内互联网常见风格，但代价真实（见 §三·4） |
| **字段命名** | 🔴 **不符合（最严重）** | 契约内部：**197 个 lowerCamelCase 与 58 个 snake_case 并存，10 个同一概念两种写法**（58 个 snake 中**仅约 11 个是线上字段**，其余是 DB 表/列/索引名、ES 索引名、第三方字段名、枚举值——见 §3.5）。服务间：4 snake / 11 camel。§四 专章 |
| 版本策略 | 🔴 **声明的机制完全未实现** | 契约 §1.6 给了 URL 路径版本 + `API-Version` 头两套机制，实测 **0/286 端点含版本段、代码 0 处实现该头**（§3.6） |
| 金额表示 | 🟢 **符合** | 整数「分」（Stripe 同款），禁浮点 |
| 时间格式 | 🟠 **部分不符合** | 线上 ISO 8601 ✓（289 个 `LocalDateTime`、0 个 `Date`）；但 4 处 `date-format: yyyy-MM-dd HH:mm:ss` 是**无效死配置**，且 `LocalDateTime` 无时区偏移（RFC 3339 要求带偏移）（§3.7） |
| 认证 | 🟢 **符合** | Bearer JWT + refresh 轮换 + JWKS（标准）；但**前端 token 存储**不符（见 21 文） |
| 幂等 | 🟠 **部分不符合** | 内部有 `transaction_no`/`biz_ref` 幂等键，但**客户端无 `Idempotency-Key` 通道**——`POST /api/trade/order` 双击/重发无保护 |
| 限流响应 | 🟠 **部分不符合** | 有 429 + 1014，但未见 `Retry-After` 响应头 |
| 枚举取值 | 🟢 **符合** | `UPPER_SNAKE`（`WECHAT`/`REFUND_STATUS_INVALID`） |
| 统一响应信封 | 🟢 **符合（风格选择）** | `Result<T>` 一致；`/internal/**` 例外单独定义（ADR-004） |

---

## 三、不符合项清单（逐条：基准 → 为什么不符 → 建议 → 改动面）

### 3.1 集合名用单数（AIP-122 违反）

- **契约**：§1.1 `资源名使用单数名词（/api/item/spu）`；代码 53 个端点为此形态。
- **基准**：[AIP-122](https://google.aip.dev/122) —— 集合标识符**必须**复数、**必须** camelCase。Stripe（`/v1/charges`）、GitHub（`/repos`）同为复数。
- **为什么不符**：单数集合名无法区分「一个资源」与「一个集合」，且与业界客户端/文档工具的默认假设冲突（OpenAPI 的 tag/operationId 生成、SDK 命名）。
- **建议**：**不建议现在改**。理由：286 个端点里 53 个受影响，且 URL 是前端已大面积依赖的面；改集合名属**破坏性变更**，收益（规范性）远小于成本（全前端 + 全契约 + 网关路由 + 所有文档）。
- **折中**：在契约 §1.1 把「单数」从**规范**降级为**已决偏离（documented deviation）**，并记录理由与未来 `/api/v2` 的迁移方向。这是大厂对历史包袱的标准处理方式。
- **改动面**：0（仅文档登记）。

### 3.2 分页字段泄漏 MyBatis-Plus 内部结构（最值得改的一条）

- **契约**：§1.4 响应体声明 `records`/`total`/`size`/`current`/`pages`。
- **代码**：28 个端点返回 `Result<IPage<…>>`；`IPage` 是 MyBatis-Plus 的分页对象。
- **基准**：Google AIP 用 `page_size`/`page_token`/`next_page_token`；Microsoft 用 `@odata.nextLink`；Stripe 用 `has_more`/`starting_after`。
- **为什么不符**：**框架类型直接成为对外契约**——升级 MyBatis-Plus 或换持久层就会改报文。这违反 AIP-140 的「字段名是契约、不是实现」精神，也是本报告 §四 命名混乱的**同源病根**（都是"让实现细节决定线上格式"）。
- **建议**：**列入改造清单，但不必全量替换**。做法：新增 `PageResult<T>{items, total, page, pageSize, hasNext}`，新端点用它；存量 28 个端点保留 `IPage` 形状但在契约中**显式声明为已冻结的兼容形状**（并注明它的字段名来源）。避免"为了规范而全量破坏性改动"。
- **改动面**：中（新增 1 个 DTO + 契约 §1.4 补注；存量零改动）。

### 3.3 路径含动词 / 动作段（54/255）

- **契约**：§1.1 允许「动作使用动词或子资源名」，示例 `POST /api/trade/order/{orderNo}/cancel`；实际出现 `/update/{id}`、`/delete/{id}`、`/add`、`/page`、`/list`、`/confirm`、`/claim`、`/grab`。
- **基准**：AIP 的标准方法用 HTTP 方法表达 CRUD；自定义动作建议用 `:verb` 后缀（`POST /orders/{id}:cancel`）。`/update/{id}`（PUT 里再写 update）、`/delete/{id}`（DELETE 里再写 delete）是**双重表达**。
- **建议**：分两类处理——
  - **冗余动词**（`/update/`、`/delete/`、`/add`、`/page`、`/list`）：**契约登记为偏离**，因为 `AddressController` 这类路径已被前端使用；但**新端点不得再引入**（写进 `09-constraints`）。
  - **业务动作**（`/cancel`、`/confirm`、`/claim`、`/grab`）：**可接受**，与 Stripe 的 `POST /v1/payment_intents/{id}/confirm` 同构。
- **改动面**：0（文档 + 约束登记）。

### 3.4 业务错误用 HTTP 200 + body `code`（有意偏离）

- **契约**：§1.3 / §二 —— 业务码 3000-3041 的 HTTP 状态为 200。
- **基准**：Google AIP-193、Microsoft REST Guidelines 要求真实 HTTP 状态。
- **为什么不符（代价具体化）**：
  1. 网关/APM/日志的**错误率统计恒为 0**，告警失效；
  2. 标准客户端与 SDK 的重试/熔断策略不触发（无论 4xx/5xx）；
  3. HTTP 缓存与中间件无法区分成功/失败；
  4. 前端每个调用点都必须显式判 `body.code`（现在确实如此，但任一处漏判即静默失败）。
- **建议**：**不改**（全平台已按此实现，`Result<T>` 是既定架构决策），但**必须补齐两件事**：
  1. 在契约 §1.3 把「为什么用 200+code」写成**显式决策**（而非隐式事实），并列出上述 4 项代价与补偿措施（如：网关按 body `code` 埋点、前端统一在 `apiClient` 处判码——现有 `client.ts` 已做，属正确补偿）；
  2. 补偿第 1 项代价：**网关/日志按 body code 统计错误率**，否则运维面永久失明。
- **改动面**：小（文档 + 运维埋点）。

### 3.5 字段命名混用（详见 §四）

契约内部 197 camel vs 58 snake，**10 组同名异形**：`userId/user_id`、`productId/product_id`、`productName/product_name`、`mainImage/main_image`、`createTime/create_time`、`displayCurrency/display_currency`、`packageNo/package_no`、`afterSaleId/after_sale_id`、`channelRefundNo/channel_refund_no`、`rateScaled/rate_scaled`。

**⚠️ 口径修正（避免高估改动面）**：58 个 snake 标识符**不等于 58 个待改字段**。逐条归因（Implementation/back-end/scripts/naming-audit.mjs + 人工复核）后：

| 归因 | 数量 | 处置 |
| --- | --- | --- |
| **真正的线上字段** | **约 11** | 需改：`expire_seconds`（EmailCodeResp）+ 上表 10 组中的 snake 侧 |
| DB 表名 | 23 | 不属于命名决策范围 |
| DB 列名 | 26 | 同上 |
| DB 唯一键名（`uk_*`，Initialize.sql 中 53 处） | 8+ | 同上 |
| ES 索引名（`kg_edges`、`knowledge_vectors`） | 2 | 同上（`KnowledgeVectorClient.INDEX_KNOWLEDGE_VECTORS`） |
| **第三方（支付宝/微信支付）字段**：`out_trade_no`、`out_refund_no`、`refund_id`、`stable_token` | 4 | **不可改**（`AlipayPaymentClient.PARAM_OUT_TRADE_NO` 等） |
| 枚举**取值**：`best_seller`、`new_arrival`（`hot_reason` 取值） | 2 | 非字段名 |
| 散文用词（`snake_case` 本身） | 1 | — |

结论：Option A 的**契约侧改动是约 11 个线上字段**，而非 58 个标识符。

### 3.6 声明的版本策略**完全未实现**（不只是"未定义优先级"）

**实测**：契约 §1.6 给出两套版本机制，但代码里**一套都没落地**——

| 契约声明 | 实测 | 证据 |
| --- | --- | --- |
| URL 路径版本 `/api/v2/item/spu` | **0 / 286** 个端点含版本段 | `extract-endpoints.mjs` 输出的代码端点清单（286 条） |
| Header 版本 `API-Version: 2026-08` | 代码 **0 处**提及（契约仅 1 处，在 §1.6 示例里） | 全仓 grep `API-Version`/`api-version` = 0 |

**为什么不符**：Google 对公开 API 用路径主版本、Microsoft 用 `api-version` 查询参数；**两者都要求"声明的机制真实生效"**。当前是**规格声明了能力但无实现**——比"优先级不清"更严重，因为客户端会按 §1.6 设计版本协商，而服务端根本不认这个头。

**建议（二选一，且都要落文档）**：
- (a) **务实**：契约 §1.6 改为「当前不提供版本机制；破坏性变更通过新增端点或 `Deprecated` 过渡两个版本」——与 §1.6 已有的「字段兼容」原则一致；
- (b) **补实现**：网关解析 `API-Version` 并按版本路由（成本高，当前无多版本并存的真实需求）。
推荐 **(a)**。改动面：0（文档）。

### 3.7 时间格式：契约正确，但配置有误导 + 缺时区

**实测**：289 个字段用 `LocalDateTime`，**0 个 `java.util.Date`**；未配置 `write-dates-as-timestamps`。

| 项 | 结论 |
| --- | --- |
| 线上格式 | Jackson `JavaTimeModule` 默认 ISO-8601 → **契约 §1.7 的 `2026-08-02T10:30:00` 成立** 🟢 |
| `date-format: yyyy-MM-dd HH:mm:ss`（4 个 SNAKE_CASE 服务各一行） | **对该格式无效**（`spring.jackson.date-format` 只作用于 `java.util.Date`）→ 属**误导性死配置**：读 yml 的人会以为线上是空格分隔 |
| 时区偏移 | `LocalDateTime` **不含偏移**，而契约 §1.7 只说「时区 Asia/Shanghai (UTC+8)」→ 客户端必须**假定**时区。RFC 3339 / AIP-142 要求带偏移 |

**建议**：删 4 行 `date-format`（或改为注释说明其无效）；契约 §1.7 明确「线上无偏移，一律按 Asia/Shanghai 解释」或改传带偏移的 `OffsetDateTime`（后者是破坏性变更，不推荐）。改动面：小（删 4 行配置 + 契约补一句）。

### 3.8 客户端幂等键缺失

- **现状**：内部有 `transaction_no`（wallet）、`biz_ref`（refund）幂等键，但**对外无 `Idempotency-Key`**（契约与代码均 **0** 处提及）。
- **风险点**：`POST /api/trade/order`（下单）、`POST /api/pay/order/{orderNo}`（支付）、`POST /api/coupon/claim`（领券）—— 网络重发/双击会造成重复下单或重复领券。
- **基准**：Stripe 的 `Idempotency-Key` 头是事实标准。
- **建议**：为上述 3 类写操作引入可选 `Idempotency-Key` 头（服务端用 Redis 短时去重）。改动面：中（网关透传 + 3 个 Manager 去重）。

### 3.9 其余（低优先）

- **时间字段名不统一**（`createTime` vs `view_time` / `effectTime`）→ 随 §四 命名统一一并收敛。
- **缺 `Retry-After` 头**（契约与代码均 0 处）→ 契约 §二 补一行（限流 429 场景）。
- **网关兜底路由** `/api/admin/** → user-service` 已两次造成真实缺陷（[20 §C2-1](20-Doc-Code-Consistency-Audit.md) 的 12 个营销管理端点 404、以及 AD18/AD19 幽灵端点）。**大厂做法是显式路由，不用跨域兜底**。建议：把 marketing/trade 的管理端前缀补齐后，评估将 `/api/admin/**` 兜底改为主线服务显式前缀集合。


---

## 四、字段命名专题

### 4.1 大厂怎么做：三层解耦

三个层次**各有各的惯例**，混为一谈是问题的根源：

| 层 | 惯例 | 本项目 |
| --- | --- | --- |
| ① 数据库列名 | `snake_case`（SQL 事实标准） | ✅ snake_case |
| ② 语言内部字段 | Java `lowerCamelCase` / TS `lowerCamelCase` | ✅ 均为 camelCase |
| ③ **线上报文（JSON）** | **二选一，全平台统一**：Google/MS/ProtoJSON 默认 `lowerCamelCase`；Stripe/GitHub/OpenAI/Twilio 用 `snake_case` | 🔴 **混用**（4 服务 snake + 11 服务 camel + 契约自身混用） |
| ④ 边界转换 | 在 ③ 与 ①② 之间做**一次显式转换**（proto→JSON 映射、`naming strategy`、codegen） | 🟠 有全局策略但**无边界契约**，靠人记得 |

**Google 的标准做法值得注意**：源码里写 `snake_case`（proto 规范），**JSON 出口映射为 lowerCamelCase**——即"源码风格"与"线上风格"**故意不同**，由工具保证。这说明「前后端字段名必须一致」是个**伪要求**；真正的要求是**线上风格统一 + 转换由工具完成**。

### 4.2 本项目现状（量化）

| 事实 | 数值 | 证据 |
| --- | --- | --- |
| 全局 `SNAKE_CASE` 的服务 | **4**（authorization / notification / verification / wallet），其余 11 个为 camelCase | 各服务 `application.yml` |
| 受影响的**公开**端点 | **29 / 225（12.9%）**（authorization 17、wallet 6、notification 5、verification 1） | `extract-endpoints.mjs` 输出的代码端点清单（286 条） |
| 显式钉名注解 | `@JsonProperty` **37 处**；`@JsonNaming`/`@JsonAlias` **0 处** | 全仓 grep |
| 契约内部命名 | **197 camel / 58 snake**，**10 组同名异形**；58 个 snake 中**仅约 11 个是线上字段** | `Implementation/back-end/scripts/naming-audit.mjs`、Implementation/back-end/scripts/naming-audit.mjs |
| 前端 | TS/TSX 全 camelCase（24 个类型 + 15 个 hooks + 25 个页面） | `Implementation/front-end` |

**关键发现：项目已有一个可复用的、有测试保护的「钉名」范式**，只是应用不一致：

- `LoginVO` / `OAuth2TokenVO` → 用 `@JsonProperty` 钉住 RFC 6749 的 snake_case 字段（**正确：协议强制例外**）；
- `ia-api BalanceRefundCO` + `wallet BalanceRefundReq` → 钉住 `transaction_no`/`user_id`/`biz_no`，并有单测断言「**无论发送方全局策略如何，序列化结果都是 snake_case（由 `@JsonProperty` 钉死）**」（**正确：跨服务契约显式化**）;
- `user HistoryVO` → user-service **不是** SNAKE_CASE，却把 `product_id`/`view_time`/`product_name`/`main_image` 钉成 snake_case（**这是契约要求的，但属隐式例外**）；
- `wallet CurrencyVO` / `CurrencyAdminVO` / `RateUpdateReq` + `CurrencyWireNamingConfig`（Jackson **mixin**）→ 钉成 **camelCase** 来**反向覆盖**本服务的全局 SNAKE_CASE（**说明团队已意识到混用的痛，正在手工打补丁**）；
- 🔴 **漏网的**：`InAppMessageSendReqCO`（站内信跨服务）**零钉名**，接收方 notification-service 是 SNAKE_CASE、4 个调用方都不是 → **线上字段全为 null、站内信失效**（[20 §C2-3](20-Doc-Code-Consistency-Audit.md)）。

> 也就是说：**混用的成本已经在付了，只是付在"每个跨边界 DTO 都要记得手工钉名"上**，并且已经漏了一个（且这个漏网造成的功能故障，1699 个单测没抓到）。

### 4.3 三个方案对比

| 方案 | 线上风格 | 代码改动 | 契约改动 | **前端改动** | 与基准的一致性 |
| --- | --- | --- | --- | --- | --- |
| **A** | 全平台 `lowerCamelCase` | **4 个服务去掉全局 SNAKE_CASE**；OAuth2 令牌 VO 保留钉名（已存在）；`HistoryVO` 4 个钉名去掉；currency 的 camel 钉名变冗余可删 | **约 11 个线上字段**改 camelCase（10 组同名异形的 snake 侧 + `expire_seconds`）；DB/ES/第三方名不动（见 §3.5） | **0**（前端本就 camelCase） | ✅ Google AIP / ProtoJSON / MS 默认；✅ 11/15 服务零改动 |
| **B** | 全平台 `snake_case` | 11 个服务加全局 SNAKE_CASE；已有 37 处钉名多数变冗余 | 契约 197 个 camel 标识符改 snake | **全部**（24 类型 + 15 hooks + 25 页 + 所有组件字段） | ✅ Stripe/GitHub 风格；🔴 与 TS 生态惯例冲突 |
| **C** | 维持混用 + 强制钉名 | 只补漏（如 `InAppMessageSendReqCO`） | 契约登记"按服务而异" | 0 现在，但**需按服务记忆命名**（生成客户端要按服务配置） | 🔴 违反"不可混用"这一唯一共识 |

### 4.4 推荐：**方案 A（全平台 lowerCamelCase）+ 显式例外清单**

**理由**

1. **增量最小**：只需动 4 个服务的**一行配置**（外加已存在的 OAuth2 钉名），11/15 服务与整个前端零改动。方案 B 要动 11 个服务 + 整个契约 + 整个前端。
2. **有权威默认背书**：Google AIP-140 的 proto→JSON 映射、ProtoJSON、Microsoft/.NET「web default = camelCase」都指向 lowerCamelCase；Spring/Jackson 默认也是 camelCase（现在是**逆默认**打了 4 处配置）。
3. **前端零风险**：前端与 TS 生态（ESLint `camelcase`、React props、现有 24 类型/15 hooks）天然是 camelCase。
4. **例外少且标准**：唯一必须保留的 snake_case 例外是 **RFC 6749 令牌信封**（`access_token`/`token_type`/`expires_in`/`refresh_token`）——它**已经**用 `@JsonProperty` 钉住，正好成为"例外必须显式"的合规范例，与 Google 的 `json_name` 覆盖机制同构。

**例外清单（全平台唯一允许的 snake_case 线上字段）**

| 例外 | 依据 | 落法 |
| --- | --- | --- |
| OAuth2 令牌信封：`access_token` / `token_type` / `expires_in` / `refresh_token`（+ `user_id` 项目扩展） | RFC 6749 §5.1 | `LoginVO`/`OAuth2TokenVO` 上保留 `@JsonProperty`（已存在） |
| 内部 Feign 契约中的既有钉名（`BalanceRefundCO` 等） | 跨服务契约已冻结 | 保留；**新增**跨服务 DTO 一律用 camelCase，不再新增 snake 钉名 |
| DB 列名 / Redis key（`rate_scaled`、`uk_order_package`） | 非线上字段，不受本决策约束 | 不变 |

**实施要点（避免"只改配置"留下隐患）**

1. **去掉 4 处全局 SNAKE_CASE**，但要先确认这 4 个服务的**对外报文改动的兼容性**（前端 `/api/notifications*`、`/api/wallet*`、`/api/currency*`、`/api/verification/codes`、`/api/auth/*` 的请求体字段名会变）。由于前端**当前这些调用大多是死路径或本就用错**（[21 §三](../frontend/21-Frontend-Refactor-Readiness.md)），所以**现在改的边际成本近乎为零**——这是难得的窗口期。
2. **`user HistoryVO` 的 4 个 snake 钉名去掉**（改为 camelCase），并同步契约 §4.2 对应行。
3. **`wallet currency` 的 camel 钉名与 `CurrencyWireNamingConfig` mixin 变为冗余**，可删（减少 37 处钉名中的 11 处）。
4. **补构建期守卫**（否则会再次漂移）：
   - 契约测试：对每个 `adapter/response/*VO` + `adapter/request/*Req` 做序列化快照，断言字段名与契约一致（现有 `BalanceRefundCOTest` 已是此范式的雏形）；
   - 或 ArchUnit 规则：除白名单（`LoginVO`/`OAuth2TokenVO`/`*CO` 既有钉名）外，禁止 `@JsonProperty` 出现在 adapter 层。
5. **`InAppMessageSendReqCO` 的修复随方案 A 自然消失**（接收方不再 SNAKE_CASE）——但**仍建议加一条跨服务契约测试**，因为同类问题不止站内信一处。

### 4.5 直接回答：「前端的字段统一使用下划线如何？」

**不建议。** 这个提法把**两个不同层次**合并了，需要拆开：

| 如果「前端字段」指的是… | 结论 |
| --- | --- |
| **前端 TS 代码里的变量/属性名**（`user.user_id`、`cartItemIds`） | ❌ **反生态**。JS/TS 惯例是 camelCase（ESLint `camelcase`、React props、DOM API `dataset.userId`、现有 24 类型/15 hooks）。全改下划线要重写前端全部标识符，且此后每次对接第三方 SDK 都要来回转换 |
| **线上 JSON 报文的字段名** | ⚠️ **可行但代价最大**（方案 B）。此时前端内部**仍应保持 camelCase**，只在生成的客户端处做一次 `snake→camel` 转换（这正是 codegen 的职责） |

**关键点**：如果选 snake_case 报文，**前端内部也不该改成下划线**——大厂（含 Stripe 的官方 SDK）的做法都是**报文 snake_case、SDK 内部 camelCase、边界转换**。所以「前端统一用下划线」既不是达成统一的必要手段，也不是充分手段。

**真正的"统一"含义**：不是"所有层的名字长得一样"，而是「**线上风格全平台唯一** + **转换由工具做** + **例外显式且可枚举**」。按此标准，方案 A 是当前成本最低、后患最小的选择。

---

### 4.6 若选方案 A：文件级影响面（已实测，供你判断成本）

**① 配置：4 个文件、各删 1 行**

| 文件 | 行 |
| --- | --- |
| `authorization-service/src/main/resources/application.yml` | :40 |
| `notification-service/src/main/resources/application.yml` | :24 |
| `verification-service/src/main/resources/application.yml` | :26 |
| `wallet-service/src/main/resources/application.yml` | :21 |

（均为 `spring.jackson.property-naming-strategy: SNAKE_CASE`；同段落的 `date-format` 保持不动）

**② 受影响的端点：42 个**（这 4 个服务的**全部**端点，29 公开 + 13 内部）——报文格式会从 snake 变 camel。

**③ 37 处 `@JsonProperty` 的处置**

| 处置 | 处数 | 文件 | 理由 |
| --- | --- | --- | --- |
| **保留** | 9 | `LoginVO`(5)、`OAuth2TokenVO`(4) | RFC 6749 令牌信封，**唯一保留的例外** |
| **删除（冗余）** | 12 | `CurrencyVO`(3)、`CurrencyAdminVO`(3)、`RateUpdateReq`(2)、`CurrencyWireNamingConfig` mixin(4) | 这些是**为覆盖全局 SNAKE_CASE 而钉的 camelCase**；去掉全局策略后自然即 camelCase。**`CurrencyWireNamingConfig.java` 整个文件可删** |
| **删除（改回 camel）** | 4 | `user HistoryVO` | Java 字段本就是 `productId`/`viewTime`/`productName`/`mainImage`；去掉 pin 即得 camelCase（该服务本无全局策略，pin 是反向的） |
| **删除（跨服务内部契约改 camel）** | 8 | `ia-api BalanceRefundCO`(4)、`wallet BalanceRefundReq`(4) | 两侧同属 `ia-api` 共享模块、**同时编译**，改 CO 即两侧同步；但会破坏下方 2 个测试（需同步改） |
| 无需处置（仅 javadoc 提及） | 2 | `ia-api RefundCreateCO`、`RerankerResponse` | 无实际 pin |

**④ 测试影响：仅 2 个文件需要改**

| 文件 | 改什么 |
| --- | --- |
| `ia-api/src/test/.../BalanceRefundCOTest.java` | :33/:37/:39 断言 snake JSON → 改 camel |
| `wallet-service/src/test/.../BalanceRefundReqTest.java` | :24/:27 反序列化 snake JSON → 改 camel |

**已核实无需改动**的另外 5 个命中文件（避免误伤）：`WechatMiniApiClientTest`（断言**微信**的 `access_token`，第三方报文）、`item ProductSearchMapperH2Test` / `ProductSuggestMapperH2Test` / `marketing FlashSaleManagerH2Test`（H2 DDL 里的 **DB 列** `product_id`）、`wallet WalletManagerTest`（`uk_transaction_no` 是 **DB 唯一键名**）。

**⑤ 契约影响：约 11 个线上字段**（见 §3.5）——10 组同名异形的 snake 侧 + `expire_seconds`。

**⑥ 前端影响：0**（前端现有代码全 camelCase；且它当前对这几个服务的调用多为死路径或本就用错，见 [21 §三](../frontend/21-Frontend-Refactor-Readiness.md)）。

**⑦ 建议同时加的守卫（否则必然再漂移）**
- 序列化快照测试：为 4 个服务的 `adapter/response/*VO` 与 `adapter/request/*Req` 各加一条「字段名与契约一致」断言（现成范式：`BalanceRefundCOTest`）。
- ArchUnit：除白名单（`LoginVO`/`OAuth2TokenVO`）外，禁止 `@JsonProperty` 出现在 `adapter`/`dto` 层——把「例外必须显式」变成构建期约束。

> **净成本小结**：改 **4 行配置 + 删除 1 个自定义命名类 + 调整 24 处冗余注解 + 改 2 个测试 + 契约 11 个字段**，换来「全平台线上风格唯一」。前端与其余 11 个服务**零改动**。**这是三个方案里唯一能在当前窗口内低成本完成的。**

---

## 五、需要你拍板的决策

| # | 决策 | 我的推荐 | 不确认的后果 |
| --- | --- | --- | --- |
| **D1** | 线上报文风格：方案 A（全 camelCase）/ B（全 snake_case）/ C（维持混用+钉名） | **A** | 不定则前端 codegen 无法产出正确字段名，阶段 1 阻塞 |
| **D2** | 集合名单数（§3.1）与冗余动词（§3.3）：改，还是登记为「已决偏离」？ | **登记偏离**（改的收益远小于破坏面） | 契约 §1.1 与代码持续矛盾 |
| **D3** | 分页（§3.2）：新增 `PageResult` 供新端点，存量 28 个冻结保留？ | **同意** | 新端点继续泄漏 `IPage` |
| **D4** | 业务错误 200+code（§3.4）：保留但补「决策记录 + 网关按 body code 埋点」？ | **保留 + 补两件事** | 运维错误率永久为 0，告警失效 |
| **D5** | 客户端幂等键（§3.8）：为下单/支付/领券引入 `Idempotency-Key`？ | **引入**（下单至少要有） | 重复下单/重复领券风险 |
| **D6** | 命名统一是否落为 **ADR**（如 ADR-054）？ | **是**（跨前后端 + 4 个服务的破坏性配置变更，必须留决策记录） | 后续无人知道为何是 camelCase、例外为何存在 |
| **D7** | 契约里 33 条「计划端点」如何处置？ | **保留在端点表**（规格理应包含计划项），但**新增「状态」列 + 头部计数按状态拆分**；**不要移出** | ——（此项已按你「契约=规格」的定位修正我 [20](20-Doc-Code-Consistency-Audit.md) 原先的建议） |

---

## 六、确认后的实施顺序与复审

### 实施顺序（我建议）

你的问题是「先 fix-doc 批次（[20 §八](../quality/20-Doc-Code-Consistency-Audit.md) #5–#8、#12–#22），还是先做阶段 0b 后端放行面修复（#0–#4）」。**我建议三者并行分批，但按下列顺序落地**，理由是它们**互不阻塞**且**风险等级不同**：

| 批次 | 内容 | 为什么这个顺序 | 前置 |
| --- | --- | --- | --- |
| **① 立即做（纯 bug，无设计争议）** | [20 §八](../quality/20-Doc-Code-Consistency-Audit.md) **#0 网关路由缺口**（12 个管理端点 404）、**#1 管理端鉴权**（12 个端点无鉴权）、**#3 地址 IDOR**、**#9 商品/类目越权**、**#10 Feign 落空**、**#11 address id** | 这些不是"设计选择"，是缺陷：**404 / 越权 / 数据泄露**。不依赖任何命名或规格决策，且拖一天风险多一天 | 无 |
| **② 定 D1 后做（破坏性，需窗口期）** | 命名统一（方案 A）+ `InAppMessageSendReqCO` 修复 + 37 处钉名清理 + 构建期守卫 + **ADR-054** | **必须趁前端还是"死路径状态"时改**——前端现在本就在调用错路径，此时改报文格式的边际成本最低。等前端接上再改就要双倍工作 | **D1 + D6** |
| **③ 与 ② 并行（纯文档纠错）** | [20 §八](../quality/20-Doc-Code-Consistency-Audit.md) **#5–#8、#12–#22**（13 条更正、§七 修正、错误码区间、04-Data-Model 的 45→62 与死链、根文档计数统一、ADR 索引） | 与代码改动零冲突；做完后契约才可作为 codegen 输入 | 无（但 #7 的"33 条处置"需 **D7**） |
| **④ 最后做（契约规格增强）** | §3.2 `PageResult`、§3.4 决策记录 + 网关埋点、§3.6 版本策略（建议收敛为「不提供版本机制」）、§3.7 时间格式、§3.9 `Retry-After`、§3.8 幂等键 | 属规格演进，不阻塞前端启动；幂等键（D5）可与下单链路一起做 | **D2/D3/D4/D5** |
| **⑤ 此后** | 转 [21](21-Frontend-Refactor-Readiness.md) 阶段 1（OpenAPI 聚合 + 生成 TS 客户端） | 只有 ①②③ 完成后，"前端调用的每一个路径都存在、字段名唯一"才成立，codegen 才有意义 | ①②③ |

**一句话**：**先修 bug（①）与文档硬错（③），同时把命名决策（D1/D6）定下来，再动破坏性改动（②），最后做规格增强（④）。不要先做 fix-doc 全量、也不要把 0b 全量排到最后**——因为 #0/#1/#3 是安全与可用性缺陷，而命名统一有个正在关闭的时间窗口（前端尚未接真后端）。

### 复审计划（"然后再审计"）

命名方案确认并实施后，做一次**专门的命名复审**，可机械校验：

| 复审项 | 判据 | 2026-10-15 实测 |
| --- | --- | --- |
| 契约内部无同名异形 | `node Implementation/back-end/scripts/naming-audit.mjs` 的「同一概念两种写法」组数 | **0** ✅（迁移前 10） |
| 契约线上字段风格唯一 | `naming-audit.mjs` 的 `lowerCamelCase` 只剩非字段项（Java 方法名 / 第三方协议名 / 内部概念 / 删除注记） | **12 项，全部非字段** ✅ |
| 服务间一致 | `WireNamingConventionTest`（`architecture-tests`）断言 15 个服务均声明 `SNAKE_CASE` | **15/15 ✅ 测试通过** |
| 线上字段不再靠散落钉名 | camel 强制型钉名清零（wallet 4 处 + mixin 类已删）；其余钉名与全局策略同向，不再冲突 | ✅ |
| 跨服务契约受测 | 每个 `ia-api` `*CO` 都有序列化快照测试 | ⛔ **未做**（仅 `BalanceRefundCO` 有）——建议下一批 |
| 前端字段可生成 | codegen 输出与契约一致；前端手写类型无 camel 残留 | ⛔ **未做**——已并入 ADR-054 多平台重构排期 |

> **口径说明（避免误判为遗漏）**：**URL path 变量名**（`{orderNo}`）与 `Path: orderNo` 提及**有意保持 camelCase** —— 它跟随 Java `@PathVariable` 参数名，不属「线上字段」命名范围；`naming-audit.mjs` 已把该类别与 **DB 表名**一并排除，故其「同名异形」判据才可归零。此规则需写进契约 §1.x，成为明示约定而非偶然。

---

## 七、边界说明

1. 本文原为**评估与建议**；**2026-10-15 用户确认执行方案 B 与批次②后端部分后，已按下述范围实施**：
   - 契约 673 处 camelCase→snake_case 迁移（两轮：JSON key/字段列表 323 行 + Query/散文语境 256 行），经 `naming-audit.mjs` 复核 **同名异形 = 0**；
   - **15 个服务**全部声明 `spring.jackson.property-naming-strategy: SNAKE_CASE`（新增 11 个），并由新增守卫测试 `WireNamingConventionTest` 锁定；
   - 删除 wallet 的 camel 强制钉名 4 处与 `CurrencyWireNamingConfig` mixin 类（全平台 snake 后该桥接失效且反向冲突）；
   - 前端字段改名**未做**，并入 ADR-054 多平台重构。
   全量验证：`mvn -f Implementation/back-end/pom.xml clean test` → **1772 tests / 0 failures / 0 errors / 1 skipped（既有 @Disabled）**，20 个反应堆模块全 SUCCESS。
2. 文中所有计数均可复现：`Implementation/back-end/scripts/naming-audit.mjs`（契约命名）、Implementation/back-end/scripts/naming-audit.mjs（snake 标识符归因）、`Implementation/back-end/scripts/extract-endpoints.mjs`（端点与路径形态）；§4.6 的文件行号与测试影响面均已逐个 `read`/`grep` 复核。
3. 引用的规范仅限本文明列的一手来源（Google AIP-122/140、ProtoJSON、Microsoft/.NET、RFC 6749，以及一篇业界命名调查）。**未引用我未查证的规范条文**。
4. D1–D7 未确认前，不应开始 §六 批次 ②。
