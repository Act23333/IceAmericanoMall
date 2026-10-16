# ADR-056: 线上字段与查询参数命名统一为 snake_case

## 状态

已实施（前后端 + 契约三侧落地；`categoryId`/`hasMedia` 两处 POJO 绑定查询参数待定夺，见「未覆盖项」）

## 背景

2026-10-15 文档-代码一致性审计（`quality/20`）与契约大厂规范性评估（`quality/22`）发现：**同一份 DTO 在不同服务上线格式不同**。

- 15 个服务中仅 **4 个**（authorization / notification / verification / wallet）配置了 `spring.jackson.property-naming-strategy: SNAKE_CASE`，其余 **11 个**为默认 camelCase；
- 为跨过这个不一致，代码里累积了 **37 处** `@JsonProperty` 钉名（其中 wallet 的 `CurrencyVO`/`CurrencyAdminVO`/`RateUpdateReq` 是**反向钉 camelCase**，`CurrencyWireNamingConfig` 甚至用 mixin 把共享 DTO 钉回 camelCase，只为迁就 trade-service 的 camelCase 解码）；
- **已造成真实故障**：站内信链路因字段命名不一致，消费方字段全为 `null`，功能静默失效；
- **且无任何测试能发现这类缺陷**：全仓 **21 个 MockMvc 测试全部使用 `standaloneSetup`**（不加载 `application.yml`，用默认 camelCase ObjectMapper），**`@WebMvcTest` 数量为 0** —— 即**真实报文格式此前完全无测试覆盖**。

同时，契约 `contract/05` 内部存在 **197 个 camelCase 与 58 个 snake_case 字段并存、10 组「同名异形」**，前端只能逐个猜。

## 决策

**线上（跨进程、跨端）字段与查询参数一律 `snake_case`；进程内标识符保持各语言惯例。**

| 层次 | 命名 | 示例 |
| --- | --- | --- |
| JSON 请求体 / 响应体字段 | **snake_case** | `order_no`、`pay_amount`、`create_time`、`rate_scaled` |
| 查询参数（`@RequestParam`） | **snake_case**（由 `@RequestParam(name = "snake_name")` 显式钉死，**Java 形参名保持 camelCase**） | `user_id`、`page_num`、`product_ids`（**该例现为 `spu_ids`**，ADR-059） |
| DTO / VO / CO 的 Java 字段名 | camelCase（Java 惯例） | `private Integer payAmount;` |
| TS/JS 变量、函数、props、state、`queryKey` | camelCase（TS 生态惯例） | `const cartItems = ...` |
| 前端本地域模型（非报文） | camelCase | `cart-store.CartItem.skuId` |

**边界转换只允许一处、必须显式**：前端把报文 DTO 映射为本地模型时转换一次（现有唯一实例：`apps/web/app/product/[id]/add-to-cart.tsx` 的 `sku_id → skuId`）。**禁止全局自动转换层** —— 它会把「字段名写错」变成静默 `undefined`，掩盖真实漂移。

### 线上字段命名的三类载体（关键认知）

Jackson 配置**只覆盖其中第一类**，因此落地时必须三类分别处理，否则会出现「JSON 已 snake、查询串仍 camel」的半统一状态：

| # | 载体 | 生效机制 | 是否受 `property-naming-strategy` 影响 |
| --- | --- | --- | --- |
| 1 | JSON 请求体 / 响应体 | Jackson | ✅ 是 |
| 2 | `@RequestParam` 查询参数 | 注解 `name`（缺省取 Java 形参名） | ❌ 否 —— 必须显式 `name = "..."` |
| 3 | **POJO 查询绑定**（`@ModelAttribute`，如 `ProductPageReq`/**现名 `SpuPageReq`**，ADR-059） | **Java 字段名** | ❌ 否 —— 两者皆不管，需改字段名或加显式别名 |

## 理由（大厂依据）

选定 snake_case 而非 camelCase，依据是**跟随本项目最接近的对标族**，而非“谁更正确”：

| 依据 | 内容 |
| --- | --- |
| Google AIP-140 | proto 字段一律 `lower_snake_case`；ProtoJSON 再映射为 lowerCamelCase —— 即 **Google 也把 snake_case 视为「源」** |
| Stripe / GitHub / OpenAI / Twilio | 公开 REST API **字段与查询参数均为 snake_case**（`?per_page=`、`?starting_after=`、`payment_intent`） |
| 反面参照：Google 自家 REST | 查询参数用 camelCase（`pageSize`/`pageToken`）—— 说明**两种都存在于大厂**，关键是**不混用** |
| 反面参照：Microsoft/.NET | Web 默认 camelCase |
| 行业结论 | Apidog 等实践总结：**「混用才是唯一不行的」** —— 本决策的首要目标正是消除混用 |

**为何不选全平台 camelCase（方案 A）**：需改动更多服务、且与「文档/契约内以 snake_case 记录字段」的既有习惯冲突；成本对比见 `quality/22` §4.3。**为何不维持混用（方案 C）**：这正是已造成站内信故障与 10 组同名异形的根因。

## 例外（有意保留 camelCase，**不是漏改**）

| 例外 | 原因 |
| --- | --- |
| **URL path 变量名**（`{orderNo}`、`Path: orderNo`） | 跟随 Java `@PathVariable` 形参名，**不属线上字段**；HTTP 层占位符名不影响路由 |
| **DB 表名 / 列名** | 非线上字段（`order_item` 等） |
| **第三方协议字段原样透传** | 如微信 JSAPI 的 `prepayWithRequestPayment`、`appId`/`timeStamp`/`nonceStr`/`signType`/`paySign` —— 取其厂商拼写以免多一层映射风险 |
| **Redis key / JWT claim 等内部概念名** | 如 `tokenVer`（`auth:tokenver:{userId}`），非 HTTP 线上字段 |
| **「字段已删除」的历史注记** | 如 `evidenceImages`/`bizUserId`/`toolCalls` —— 记录的是已移除的名称，非现行契约 |

> **OAuth2 无需例外**：RFC 6749 §5.1/§6 的 `access_token`/`token_type`/`expires_in`/`refresh_token` **本就是 snake_case**，与本决策天然一致。（这是方案 B 相对“全平台 camelCase”的一项额外收益——后者必须为 OAuth2 开例外。）

## 后果

**正面**
- 契约内部 **10 组「同名异形」→ 0**；跨服务调用不再需要 `@JsonProperty`/mixin 钉名（wallet 的 4 处反向钉名与 `CurrencyWireNamingConfig` 已删除）；
- 前端可对契约做 codegen，无需逐字段猜命名；
- 站内信一类「命名不一致导致字段全 null」的故障模式被消除。

**负面 / 风险**
- **破坏性变更**：约 287 个端点的报文格式改变，无向后兼容期（库无存量数据、前端同期重构，故可接受）；
- 契约改动面大（约 1,100 处 / 约 600 行，跨 JSON 示例、字段列表、`Query:` 段落），已用脚本两轮完成并复核；
- **POJO 查询绑定（第三类载体）一度留分叉（现已闭合，2026-10-16 更正）**：原状为 `categoryId`（`ProductPageReq`/**现名 `SpuPageReq`**（ADR-059）/`SearchPageReq`）与 `hasMedia`（`ReviewFilterReq`）在代码中是 camelCase，而契约写 `category_id?`/`has_media?`。**后续批次已按 §实现选型 A1（`@QueryModel` + Jackson）收口** —— 三处控制器（`SpuController` P1 / `ReviewController` P14 / `SearchController` SE1）的实际形态见下条 §未覆盖项 1；该分叉**不再是未决项**。

## 守卫（防复发）

| 守卫 | 位置 | 断言 |
| --- | --- | --- |
| `WireNamingConventionTest` | `architecture-tests` | 15 个 HTTP 服务均声明 `spring.jackson.property-naming-strategy: SNAKE_CASE`（新增服务漏配即构建失败） |
| `naming-audit.mjs` | `back-end/scripts/` | 契约「同一概念两种写法」= **0**；camelCase 仅剩非字段项 |
| `check-frontend-api-paths.mjs`、`crosscheck-api-paths.mjs`、`check-error-codes.mjs` | `front-end/scripts/` | 死路径 0、前端路径 ↔ 后端路由表 100% 命中、错误码逐条对齐 |

> 补上的测试盲区：本决策同时暴露了「**21 个 MockMvc 全走 `standaloneSetup`、`@WebMvcTest` = 0** → 真实报文格式无覆盖」。守卫测试以「配置断言」替代「序列化断言」，成本最低地堵住回归；后续建议为 `ia-api` 各 `*CO` 补序列化快照测试。

## 实现选型（第 ③ 类载体）

采用 **A1：自定义 `HandlerMethodArgumentResolver`（`@QueryModel`）**，用**既有 Jackson `ObjectMapper`** 将查询参数绑成 DTO。

| 备选 | 否决理由 |
| --- | --- |
| **A2：自定义 `ServletRequestDataBinder` + `ServletModelAttributeMethodProcessor`**（`addBindValues` 改写键名） | ⛔ ① **snake 与 camel 都能绑**（改写后 camel 本就匹配属性名）—— 与「规范只有一份、后端不迎合」冲突，正是 servlet 包装方案被否决的同一条理由；② 需**替换 Spring 内建的 `ServletModelAttributeMethodProcessor`**（identity 改列表脆弱，或继承内部类、升级敏感），而 A1 用的是**稳定公开 SPI**。其唯一优点（保留 `@Valid`/`BindingResult`/转换器管线）**当前用不上**：3 个查询 DTO 校验注解数为 0，全仓 `@Valid` 的 POJO 形参清一色是 `@RequestBody` |
| **servlet 层别名包装**（`HttpServletRequestWrapper` + Filter） | ⛔ 隐式魔法、容忍双写法、一处改动波及全部 15 个服务的参数语义（已删除） |
| **B：复杂筛选改 `POST` + `@RequestBody`** | ⛔ 读取语义配请求体有歧义（用户明确否决） |
| **D：Java 字段改 snake** | ⛔ 违反 Java 规约 |

**准入升级条件**：若将来查询 DTO 需要校验（如分页 `@Max`），在 **A1 内部**改用 `WebDataBinderFactory.createBinder(...)` → 按 Jackson 策略改写键 → `bind()` → `validate()`，即可获得 A2 的全部好处而**不碰 Spring 内部 processor**；严格性仍由「只接受 snake 键」保证。

## 未覆盖项 / 后续

1. **POJO 查询绑定（第三类载体）—— 已收口（形态经两次演进，当前以实现为准）**：
   - **第一次演进（原记录）**：三处控制器（`P1`/`P14`/`SE1`）改为**显式 `@RequestParam(name = "snake_name")`**，涉及 6 个多词字段：`categoryId→category_id`、`sellerId→seller_id`、`hasMedia→has_media`、`SearchPageReq` 的 `categoryId/minPrice/maxPrice`。
   - **第二次演进（2026-10-16 实测的现行形态）**：这三处已改为 **`@QueryModel` + Jackson 绑定**（`ia-common` `QueryModel` + `JacksonQueryModelArgumentResolver`，即 §实现选型 A1 本身）：`item` 的 `SpuController.page(@QueryModel SpuPageReq)`（P1）、`item` 的 `ReviewController`（P14，`ReviewFilterReq.hasMedia`）、`search` 的 `SearchController.search/facets(@QueryModel SearchPageReq)`（SE1）。**Java 字段名保持 camelCase，线上键由既有的 `property-naming-strategy: SNAKE_CASE` 唯一决定；`@QueryModel` 只认 snake，camelCase 写法被忽略（不迎合双写法）。** 单字段（`keyword`/`sort`/`order`/`page`/`size`）蛇形与驼形同形，无需处理。
   - **剩余边界**：本次实测 `@QueryModel` 仅覆盖上述 3 个类（+ 绑定测试 `QueryModelBindingTest`）；其余服务若仍有裸 POJO 查询形参，仍暴露于「静默绑定失败」风险，新增时须走 `@QueryModel`。
   > **曾被否决的方案（技术债）**：一度在共享内核加 `SnakeCaseAliasRequestWrapper` + `SnakeCaseAliasFilter`（servlet 层为蛇形参数追加驼峰别名）。已**移除**，理由：① 它让 `category_id` 与 `categoryId` **两种写法都能用**，等于默认容忍两种线上写法、使契约失去权威性；② 一处全局过滤器改全部 15 个服务的参数语义，而问题只涉及 3 个类 6 个字段，属隐式魔法；③ 它掩盖契约-代码分歧而非收口。**结论：报告文体走 Jackson，查询参数走显式钉名，不引入第三套机制。**
2. **前端构建产物**：`apps/miniprogram/dist/**` 为陈旧产物且仍含已修复的死路径 —— 但**当前交付范围仅 Web 端**（用户 2026-10-15 明确），小程序/其它端不在范围内，故不作为收口条件；待该端立项时重新构建。
3. 前端字段改名的**排期归属**：已并入 ADR-054 多平台重构，不与其并行改同一批文件。

## 日期

2026-10-15

## 参考

- `project-docs/contract/05-API-Specification.md`（线上契约，已按本决策迁移）
- `project-docs/quality/20-Doc-Code-Consistency-Audit.md`（命名不一致的取证 §C2-3）
- `project-docs/quality/22-API-Spec-BigTech-Conformance-Review.md` §4（方案 A/B/C 成本对比与大厂依据）
- ADR-004（内部接口异常透传）、ADR-040（共享内核包名与 `Result`）、ADR-053（文档归属）、ADR-054（前端多平台，前端改名排期）
