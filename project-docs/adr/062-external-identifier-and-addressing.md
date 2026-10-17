# ADR-062: 对外标识与寻址口径 —— 业务键单形态、SPU 资源段改名与编解码唯一出口

## 状态

**已裁决（2026-10-17，批次 B4）** —— 契约与决策记录已落定；**代码已落码（2026-10-17，批次 B5）**，前端（B6）未落码。
本 ADR 是 [ADR-058](./058-vo-identifier-semantics.md)（VO 标识语义）、[ADR-059](./059-spu-sku-vocabulary-disambiguation.md)（SPU/SKU 词汇消歧）、
[ADR-061](./061-order-item-business-key-snapshot-and-price-compare.md)（线格式标识类型维度）的**第三条腿**：
前两条定「标识用什么形态」，本条定「**对外怎么寻址**」—— URL 资源段用哪个词、路径变量叫什么名、技术键允许出现在哪里、编解码在哪里实现。

> **B5 落码结果（一句话）**：契约侧 27 处改名、15 条端点路径、18 处路径变量口径全部落码，
> `extract-endpoints.mjs` 由 **272 / 15 / 58** 回到 **287 / 0 / 43**（`check-section7.mjs` 由 121/22/2 回到 **130 / 13 / 2**）；
> 四类守卫（§守卫 1–4）全部实现并可变异捕获。落地细节与残余风险变化见文末 **§B5 实施记录**。

> **（以下为 B4 当时状态，历史记录，勿据此判现状）** 本批唯一写入面 = 文档：`project-docs/contract/05-API-Specification.md`（§1.9 新增 + §四 全量改名 + §十二 挂账）、本 ADR、`adr/README.md`、根 `CLAUDE.md`/`AGENTS.md` 的 ADR 区间。
> 零代码改动（`Implementation/**` 未触碰），故当时 `extract-endpoints.mjs` 报 **matched 272 / codeOnly 15 / docOnly 58**（B4 前 287 / 0 / 43）——
> 三项差值全部是同一批 15 条端点的「契约新路径 ↔ 代码旧路径」；**B5 落码后已回到 287 / 0 / 43（见文末 §B5 实施记录）**。

> ⚠ **授权边界说明（登记用）**：按 [ADR-053](./053-doc-ownership-by-nature-and-two-layer-standards.md) 的分权，决策类 ADR 归该域决策者（本条属后端/契约口径，正常归 java-architect）。
> 本条由 **doc-engineer 在批次 B4 的显式指令下代笔**（主 agent 派单已授权 `project-docs/**` 写入），**裁决内容由用户（项目方）拍定**，非作者自行判断。

## 背景

### 1. 数字型 id 在 JSON 里天然不可靠

JavaScript 的 `Number` 是 IEEE 754 双精度浮点，**整数安全区上限 `Number.MAX_SAFE_INTEGER = 9007199254740991`（2⁵³ − 1）**。
超过该值的整数经过 `JSON.parse` 会**静默丢精度**（不是抛错）。雪花 ID、`BIGINT AUTO_INCREMENT` 的长生命周期表、
以及任何未来可能增长的序列，都迟早越过这条线。

**结论**：把标识放在 JSON 的**数字**里，等于把「未来某天悄悄接错对象」写进契约。这不是风格问题，是**正确性问题**。

### 2. 技术键的唯一性作用域止于本服务本库

`spu.id` / `sku.id` 这类自增技术键是**库内代理键**：

- 唯一性**只在本库本表**成立 —— 换库、换环境、分片、重建数据即失去含义；
- 它**可枚举**（遍历/爬取风险），且天然表达「第几条」这类无业务含义的信息；
- 它**不出现在任何对外承诺里**才是健康的：一旦出口，客户端会把它当稳定标识缓存/落库，之后改名、迁移、分片全部变成 BREAKING。

而业务键（`BINARY(16)` UUIDv7，[ADR-050](./050-business-id-binary16-uuidv7.md)）**全局唯一、可跨环境、不可枚举**，是唯一适合跨服务边界的形式。

### 3. 「两套词汇并存」已经真实造成过静默失效

本会话（B1–B3 批次）实测到的失效**全部**同源：同一个概念在**两处用了不同词**，于是「看起来对」的代码不生效：

| 失效 | 成因 |
| :-- | :-- |
| `product_id` 线格式残留 | 表/类已叫 `spu`，线格式仍叫 `product_id`（ADR-059） |
| `categoryId`（camelCase）与 `category_id`（snake_case） | 线上名两套方言（ADR-056） |
| `spu_name` / `spu_image` 收口反复 | 同一字段两种线上名（ADR-061 §决策 5） |
| I22 `spu_ids` 传技术键、券 `scope_values` 预过滤永不命中 | 业务键与技术键两套取值并存（ADR-061 §后果 R3） |

**共同点**：这些都不是「写错了」，而是「**两种写法都有人认，于是没人被编译器/门禁拦住**」。词汇孤岛的成本不在写的那一次，而在**此后每一次**。

## 决策

### 1. 四位置四形态：存储 / 编解码 / 线格式 / 命名，各管一段

| 位置 | 形态 | 约束 |
| :-- | :-- | :-- |
| **存储** | `BINARY(16)` UUIDv7（[ADR-050](./050-business-id-binary16-uuidv7.md)） | 全局唯一；**这是内部选择，与线格式解耦** —— 允许将来换存储形态而不动契约 |
| **适配层编解码** | `bytes ⇄ wire string`，**唯一一处实现** | 出：`toWire`；入：`parseWire` + **形态校验，非法即 400 fail-fast**（**禁止静默落 null** —— 现有 `IdGenerator.toBytesOrNull` 的宽松语义要**收严**，见 §决策 7） |
| **线格式** | **永远 `String`** | **禁止数字型 id 字段**（JS 精度安全区，见 §背景 1） |
| **路径 / 字段名** | `/spus/{spuId}`、`"spu_id"` | **由名字承担类型**，不依赖 id 自带前缀（见 §决策 5） |

**读法**：一条标识从库到客户端，只允许在「适配层」这一个位置被翻译一次；翻译是**双向且对称**的（`toWire ∘ parseWire = id`）。
除这一处之外，任何地方再出现 `byte[]`↔`String` 的手工转换，都视为违规。

### 2. 资源段改名：`product` / `products` → `spu` / `spus`

**裁决理由（记录原文要点，不再论证）**：本项目**无外部消费者**（前端同仓、无第三方客户端），改名**当前最便宜**；
而 URL 说 `product`、代码/表/类说 `spu` 会形成**永久的词汇孤岛** —— 本会话已发生的 `product_id` / `categoryId` /
`spu_name` / `spu_image` 静默失效**全部源于「两套词汇并存」**。

#### 表 A — 判定「该段指向 SPU 实体」→ 改名（27 处入口）

| # | 改前 | 改后 | 判定依据 |
| :- | :-- | :-- | :-- |
| 1 | `GET /api/item/product/page` | `GET /api/item/spu/page` | SPU 集合分页（P1） |
| 2 | `GET /api/item/product/{id}` | `GET /api/item/spu/{spuId}` | SPU 实体（P2） |
| 3 | `POST /api/item/product` | `POST /api/item/spu` | SPU 集合创建（P3） |
| 4 | `PUT /api/item/product/{id}` | `PUT /api/item/spu/{spuId}` | SPU 实体（P4） |
| 5 | `PUT /api/item/product/{id}/status` | `PUT /api/item/spu/{spuId}/status` | SPU 实体上下架（P5） |
| 6 | `GET /api/item/product/{id}/detail` | `GET /api/item/spu/{spuId}/detail` | SPU 实体（P6 ⚪ 未实现） |
| 7 | `GET /api/item/product/{id}/skus` | `GET /api/item/spu/{spuId}/skus` | 子资源是 SKU 集合，**父段是 SPU**（P7） |
| 8 | `GET /api/item/product/compare` | `GET /api/item/spu/compare` | 对比的集合元素是 SPU（P24 ⚪） |
| 9 | `GET /api/item/product/{productId}/similar` | `GET /api/item/spu/{spuId}/similar` | SPU 实体（P27） |
| 10 | `GET /api/item/product/{productId}/pairing` | `GET /api/item/spu/{spuId}/pairing` | SPU 实体（P35） |
| 11 | `GET /api/item/product/{id}/wxa-code` | `GET /api/item/spu/{spuId}/wxa-code` | SPU 实体（P37） |
| 12 | `GET /api/item/review/product/{productId}` | `GET /api/item/review/spu/{spuId}` | **被评价的 SPU**（P13） |
| 13 | `GET /api/item/review/product/{productId}/filter` | `GET /api/item/review/spu/{spuId}/filter` | P14 |
| 14 | `GET /api/item/review/product/{productId}/summary` | `GET /api/item/review/spu/{spuId}/summary` | P15 |
| 15 | `PUT /api/admin/product/{id}/status` | `PUT /api/admin/spu/{spuId}/status` | SPU 实体（AD38 ⚪） |
| 16 | `GET /api/admin/product/page` | `GET /api/admin/spu/page` | SPU 集合（AD39 ⚪） |
| 17 | `GET /api/shop/{sellerId}/products` | `GET /api/shop/{sellerId}/spus` | **店铺的 SPU 集合**（S5 ⚪；`{sellerId}` 是商家，**不动**） |
| 18 | `GET /api/search/product` | `GET /api/search/spu` | SPU 检索（SE1） |
| 19 | `POST /internal/search/vector/product/upsert` | `POST /internal/search/vector/spu/upsert` | SPU 向量（I47） |
| 20 | `GET /internal/search/vector/product/similar` | `GET /internal/search/vector/spu/similar` | SPU 向量 kNN（I48） |
| 21 | `@FeignClient(path = "/internal/search/vector/product")` | `.../spu` | 类级前缀（`SpuVectorClient`），随 19/20 同批 |
| 22 | `GET /internal/item/search/detail/{id}` | `GET /internal/item/search/detail/{spuId}` | SPU 实体（I65）+ **形态收口**，见 §决策 4 |
| 23 | `/api/v2/item/product`（§1.6 版本策略示例） | `/api/v2/item/spu` | 示例同口径 |
| 24 | `/api/item/product/page?page=1&size=20…`（§1.4 分页示例） | `/api/item/spu/page?…` | 示例同口径 |
| 25 | `GET /api/item/product/{id}`（§1.2 方法示例） | `GET /api/item/spu/{spuId}` | 示例同口径 |
| 26 | `/api/item/product`（§1.1 单数名词示例） | `/api/item/spu` | 示例同口径 |
| 27 | `admin/product/*`（§七 映射表说明文字） | `admin/spu/*` | 引用同口径 |

> **为何父段是 SPU 而不是加个 `spu` 前缀**：`/api/item/product/{id}/skus` 里 `skus` 已经承担子资源语义，
> `product` 段是**父实体的名字** —— 改成 `spu` 后读作「SPU 的 SKU 列表」，语义完整且无需新造词。

#### 表 B — 判定「非 SPU 实体 / 非 API 路径段」→ **保留**（8 条，逐条给理由）

| # | 保留项 | 判定 | 理由 |
| :- | :-- | :-- | :-- |
| 1 | `GET /api/trade/seller/dashboard/product-stats`（S11） | **非 SPU 实体寻址** | 是**分析型聚合资源**（按天聚合的销量/金额行），不指向任何 SPU 实例；此处 `product` 是「商品线」泛称 |
| 2 | `GET /api/trade/admin/stats/top-products`（AD14） | 同上 | 排行聚合资源，非实体寻址 |
| 3 | MinIO objectKey 前缀 `wxa-code/product/{id}.png` | **非 API 路径段** | 存储对象命名空间；改名会**孤立既有缓存对象**（`StorageClient` 无 exists 探测，只会重复生成），收益为零 |
| 4 | ES 索引名 `index=products`（I28） | **非 API 路径段** | 与 reindex / 文档 `_id` 同源；改名需**重建索引** |
| 5 | DB 表名 `note_product` | **非 API 路径段** | DB 表名，且 [ADR-045](./045-db-snapshot-single-source.md) 的结构变更另有批次口径 |
| 6 | CDN 示例路径 `.../product/main_001.jpg` | **非 API 路径段** | 对象存储路径 |
| 7 | 示例串 `"buyer_1001_seller_5_product_P001"` | **非 API 路径段** | 会话 ID 的样例值 |
| 8 | 前端页面路由 `/product/[id]`、`/seller/products`、`/admin/products` | **非 API 路径段** | 前端路由（影响外链/SEO/收藏夹）；是否跟改由 B6 + 产品决定，**不得由 API 改名自动推导** |

#### 表 C — 路径变量：命名去歧义 + 形态收口为业务键

**规则**：指向 SPU/SKU 实体的路径变量一律为 **32-hex `String`**，变量名**显式表达实体**；
`{id}` 因「既不表达实体、也不表达标识形态」而在 SPU/SKU 资源段内**全部退役**。

| 端点 | 变量改前 | 变量改后 | 形态（契约） | 代码实测 |
| :-- | :-- | :-- | :-- | :-- |
| `GET /api/item/spu/{spuId}`（P2） | `id` | `spuId` | `String` 业务键 | `@PathVariable Long id` ⏳ |
| `PUT /api/item/spu/{spuId}`（P4） | `id` | `spuId` | 同上 | ⏳ |
| `PUT /api/item/spu/{spuId}/status`（P5） | `id` | `spuId` | 同上 | ⏳ |
| `GET /api/item/spu/{spuId}/detail`（P6） | `id` | `spuId` | 同上 | ⚪ 未实现 |
| `GET /api/item/spu/{spuId}/skus`（P7） | `id` | `spuId` | 同上 | ⏳ |
| `GET /api/item/spu/{spuId}/similar`（P27） | `productId` | `spuId` | 同上 | ⏳ |
| `GET /api/item/spu/{spuId}/pairing`（P35） | `productId` | `spuId` | 同上 | ⏳ |
| `GET /api/item/spu/{spuId}/wxa-code`（P37） | `productId`（契约）/ `id`（代码） | `spuId` | 同上 | ⏳ **且受 `scene` ≤32 字符约束，见 §后果** |
| `PUT /api/admin/spu/{spuId}/status`（AD38） | `id` | `spuId` | 同上 | ⚪ 未实现 |
| `GET /api/item/review/spu/{spuId}`（P13-P15） | `productId`（契约） | `spuId` | 同上 | 名字已是 `spuId`，**类型仍 `Long`** ⏳ |
| `GET /internal/item/search/detail/{spuId}`（I65） | `id` | `spuId` | 同上 + **单形态** | `String` 但**接受双形态** ⏳ |
| `DELETE /api/user/history/{historyId}`（U33） | `productId` | `historyId` | `Long` **足迹行主键** | 名字 `id`，**非 SPU 标识**（勘误，见 §决策 4 末尾） |
| `GET /api/item/spu/compare`（P24） | Query `ids` | Query `spu_ids` | `String` CSV 业务键 | ⚪ 未实现 |
| `/api/cart/item/{skuId}`（C4/C5） | — | — | 已是 `String` 业务键 | ✅ **B2 已收口，无需改** |
| `/api/item/sku/{skuId}/stock-notify`（P25/P26） | — | — | `String` 业务键 | ⚪ 未实现 |

> **`{spuId}` 的 `spu` 前缀不是冗余**：它承担「这是 SPU 还是 SKU」这一**类型信息**，是 §决策 5「不加值前缀」能成立的前提。

### 3. 技术键的允许位置：只许活在服务内部的持久化与领域层

**硬口径**：`spu.id` / `sku.id` 这类技术键，以及 `sku.spu_id` / `order_item.order_id` 这类技术外键，

- **可以**出现在：PO（含 `@TableName` 字段）、Mapper、RepositoryImpl、领域模型/值对象、库内 SQL 与索引；
- **不得**出现在：任何 VO / DTO / CO（含内部 Feign 契约）、**任何路径变量**、**任何查询参数**、任何请求体字段。

**理由**：跨服务边界的形态必须与「唯一性作用域」匹配（§背景 2）。技术键一旦出口，调用方就会把它当稳定标识用，
而它连跨环境都活不下来。

**解析方向**：业务键 → 技术键的解析**只许在服务内**完成（同库、同库内唯一索引，如 `uk_spu_id`）；
**禁止**新增「把技术键翻译成业务键」的对外端点，也**禁止**让调用方持有技术键。

### 4. 删除「双形态」入参，并确立纪律（防复发）

**被删除的东西**：`GET /internal/item/search/detail/{id}` 曾同时接受「纯数字 = 技术键」与「32-hex = 业务键」
两种入参形态（[ADR-061](./061-order-item-business-key-snapshot-and-price-compare.md) §决策 6）。
本批改为 **业务键单形态**，形态非法 **400 `PARAM_ERROR(1101)` fail-fast**（不再「静默落 null」）。

**纪律（引用原文，不得再论证）**：

> **双形态是为了绕开「不新增端点」的门禁而制造的垫片，代价是留下一个永久接受技术键的入口。**
> 若确需新入口，应**显式新增路由并同步契约与端点数**（`codeOnly` 从 0 → 1 属**显式决策**）；
> **门禁的目的是暴露决策，不是逼人绕开它。**

**为何要写进 ADR 而不是只改契约**：ADR-061 §决策 6 把「新端点方案被门禁拦下（`codeOnly: 1`）」当作
**改写入参口径**的依据 —— 这条推理本身是错的，且**已成文、会被后来者当先例复用**。必须显式反转，
否则下一批会有人照着它再造一个双形态入口。

**同时登记一条勘误（U33）**：契约原写 `DELETE /api/user/history/{productId}`，读起来是「按 SPU 键删足迹」，
而**实测该入参是足迹行主键**（`user-service` `HistoryManager.deleteById(userId, logId)` + `@PathVariable Long id`）。
这是**「命名说 A、实现是 B」型漂移**的又一例，本批订正为 `{historyId}`。

### 5. 值前缀（`spu_…`）：**不加**，但保留将来加的能力

**决策：REST 路径与字段名已承担类型，故不给 id 值加前缀。**

| 维度 | 判断 |
| :-- | :-- |
| REST 内是否需要 | **不需要** —— `/spus/{spuId}` 与 `"spu_id"` 已经把类型写在**名字**上，值再带 `spu_` 是冗余 |
| 前缀真正有价值的场景 | ① **无类型位置**：webhook / 事件总线 / 多态负载里裸放一个 id 字符串，读者无从知道它是什么；② **日志与客服排障**：`cus_xxx` 一眼可辨，不必回查上下文；③ **跨类型防误用**：把 `cus_x` 传进期望 `pi_y` 的位置时**立即报错**而不是静默查空（Stripe `cus_`/`pi_`、GitHub `gid://` 的由来） |
| 保留的能力 | 将来若要加前缀，**必须只改 §决策 1 的编解码一处**（`toWire` 加前缀、`parseWire` 去前缀并校验），**不得**散落到各 VO/Controller |
| 因此的前置条件 | **必须先收敛为唯一实现** —— 这正是 §决策 1「唯一一处实现」与 §决策 7 的原因 |

### 6. 被否决 / 被纠正的做法（**重要，防后人重犯**）

| # | 做法 | 处置 | 理由 |
| :- | :-- | :-- | :-- |
| 1 | **双形态入参**（纯数字=技术键 / 32-hex=业务键） | ❌ **否决并删除** | 见 §决策 4 —— 它是绕门禁的垫片，代价是**永久**留下一个接受技术键的入口 |
| 2 | **保留 `/product` 段以「兼容外部消费者」** | ❌ **否决** | **本项目无外部消费者**（前端同仓、无第三方客户端）—— 该理由**不成立**；以「兼容」为名保留旧词，换来的正是 §背景 3 的永久词汇孤岛 |
| 3 | **用「内部 join 用技术键」为保留技术键辩护** | ❌ **纠正为不可作设计依据** | 库内 join 用技术键在**同库同分片**下确实是行业标准（ADR-061 §决策 4 据此把内部外键保持 `BIGINT`）—— 但这只是**「内部实现选择」层面的理由**，**推不出**「技术键可以出口」。更要紧的是：**join 本身是要消除的债**（跨分片不可 join、耦合数据层、把读扩散变成隐式依赖），把「我要 join」当设计前提，等于用旧约束锁死演进方向。**正确表述**：join 是当前实现，不是设计依据 |

### 7. 上游依赖：编解码必须先收敛为唯一实现（B5 的**第一件事**）

**现状（实测 `ia-common` `common/utils/IdGenerator`，2026-10-17）**：

| 方法 | 现语义 | 与本 ADR 的关系 |
| :-- | :-- | :-- |
| `nextId()` | UUIDv7 → **32 位小写 hex**（无连字符） | ✅ 就是 `toWire` 的输出形态 |
| `toBytes(String)` | 严格：长度 ≠32 / 含非 hex → 抛 `IllegalArgumentException` | ✅ 可作为 `parseWire` 的内核 |
| `toHexString(byte[])` | `BINARY(16)` → 32-hex | ✅ 就是 `toWire` |
| `toBytesOrNull(String)` | **宽松**：任何解析失败 → `null` | ⚠ **本 ADR 要求收严**：见下 |
| `secureToken()` | 32 字节全随机，**专供凭据** | 无关（凭据不是标识） |

**问题**：`toBytesOrNull` 的「失败即 null」在**仓储层做等值查询**时是合理的设计（`col = NULL` 永不命中 → 天然「未命中」，
不把垃圾入参放大成 500，ADR-061 §决策 3 已采用）。但**同一个宽松语义一旦被适配层复用**，
就变成「**非法入参静默变成『查不到』**」—— 客户端拼错一个 hex 字符，得到的是 **404/空列表**而不是 **400**，
排查成本被完全转嫁给调用方。**这正是 §决策 1「禁止静默落 null」要切断的路径。**

**决策（B5 实施契约）**：

1. 适配层（Controller 的 `@PathVariable` / `@RequestParam` / 请求体字段）**一律走形态校验**（32-hex），
   **非法即 400 fail-fast**，**不得**落到「查不到」；
2. 编解码**收敛为一处门面**（方向：`toWire` / `parseWire`），仓储层不得各自实现 byte↔String 转换；
3. 仓储层的宽松语义**只允许出现在「已通过适配层校验」之后**，或明确标注为「内部防御」；
   **不得**作为线格式入口的解析路径。
   > 具体的类名/方法名（是否复用 `IdGenerator`、是否新建 `WireId` 门面）**归 B5 决定**，本 ADR 只锁**语义与落点**。

## 守卫（防复发 —— 落点写清，供 B5 实施）

| # | 守卫 | 断言与落点 | 现状 |
| :- | :-- | :-- | :-- |
| 1 | **外部 `@PathVariable` 不得为技术键 / 数字** | 扫描 `adapter/**/Controller.java`：凡路径模板命中 SPU/SKU 实体语义者，`@PathVariable` 类型**必须**为 `String` 且变量名**显式**（`spuId`/`skuId`/`orderNo`…，**不得**为 `id`）。落点：`scripts/check-id-invariants.sh` 新增「检查 5」 | ✅ **已实现（B5）**：命中面 = 路径段 `spu(s)`/`sku(s)` 或占位符 `{spuId\|skuId\|productId}`；断言类型为 `String` + 名为 `spuId/skuId/spuIds/skuIds` + 路径不得残留 `{id}`；自证「扫到 0 个 Controller 即失败」；实测 **89 个 Controller / 13 个路径变量**，变异（改回 `Long`）即红 |
| 2 | **线格式不得出现数字型 id 字段** | 在既有「检查 4」（字段名 → 类型必须 `String`/`List<String>`）之外，**反向**扫 `adapter/{response,request}` + `ia-api/dto`：字段名含 `spuId`/`skuId`/`…No` 但类型为 `Long`/`Integer`/基本类型即失败（= 检查 4 的补集，防止改名绕过） | ✅ **已实现（B5）**：检查 4 的命中面扩为 `spuId(s)`/`skuId(s)`/**`productId(s)`（旧词改名绕过面）**/单号族 `…No(s)`；白名单收敛为 3 处 `packageNo`（包裹序号，`Integer` 正当）且**陈旧即失败**；原三项白名单（`RecommendSpuVO.spuId`、`ShopDecoration{VO,Req}.hotSpuIds`）已随收口**删除** |
| 3 | **往返性质测试**：`parse(render(x)) == x` | 对**每个含 id 的 DTO/VO** 断言往返等价；实现形态建议：参数化测试遍历 `adapter/{response,request}` + `ia-api/dto` 的 id 字段，用 `IdGenerator.nextId()` 造 32-hex、序列化→反序列化→比对。**失败即哨兵** | ✅ **已实现（B5）**：`architecture-tests` `IdWireRoundTripTest`（URLClassLoader 装载各模块 `target/classes`，SNAKE_CASE 渲染 + 断言「必为 JSON 字符串」+ 反序列化逐字段比对）；实测命中 **66 个类 / 93 个字段**（源文件 68 个），命中面为 0 即失败 |
| 4 | **业务键 ↔ 技术键解析只许在服务内** | 断言：解析调用点（`IdGenerator.toBytes`/`toBytesOrNull`）**不得**出现在 `adapter/` 包；只能出现在 `infrastructure/persistence/`（与既有「检查 2/3」同面）；且必须走**唯一索引**（`uk_spu_id`/`uk_sku_id`），不得全表扫描 | ✅ **已实现（B5）**：`scripts/check-id-invariants.sh` 新增「检查 6」断言 `IdGenerator.{toBytes,toBytesOrNull,toHexString,parseWire}` 不出现在任何 `adapter/` 目录（适配层只许 `IdGenerator.requireWire` 做形态校验）；实测 **24 个 adapter 目录**，命中面 0 即失败 |

> **守卫 3 的补充口径**：`check-id-invariants.sh` 现有 4 项检查是**静态**的；本条的往返性质是**行为**约束，
> 静态扫描表达不了，必须落**测试**。建议落在 `architecture-tests`（已有 ArchUnit 门禁，19 模块）或各服务 `adapter` 测试包内。

## 备选方案

| 方案 | 优点 | 缺点 |
| :-- | :-- | :-- |
| **保留 `/product` 段（现状）** | 零改动、零 BREAKING | 永久保留 §背景 3 的词汇孤岛；与表/类/业务键三处不同词；**本会话已为此付出多次静默失效的代价** |
| **给 id 值加类型前缀（`spu_<hex>`）** | 无类型位置（webhook/日志）可自解释；跨类型误用立即报错 | REST 内与路径/字段名重复；**需要先有唯一编解码出口**（否则前缀逻辑散落）；改变既有 32-hex 形态 → 更大 BREAKING |
| **只改文档不改代码（即本批）** | 契约先行、可评审、可回退；改代码前先把口径钉死 | 存在窗口期：契约已改、代码未跟，**照契约编码会 404**（已在 §1.9⑤ 与契约文件头显式标注） |
| **把 `{id}` 保留为技术键、只改资源段名（分批小步）** | 改动面更小 | **不可行**：改完即形成「路径段说 `spu` 业务词汇、路径变量收技术键」的**新**词汇孤岛，正是 R1 旧病 |

## 后果

### 正面

- 对外标识**全链路同型同义**：业务键 32-hex `String` 从 VO/请求体/查询参数到**路径变量**贯通，
  「列表拿到 → 直接用于详情/操作」在**类型层面**成立（ADR-061 §后果 R1 的病灶闭合）；
- URL、DB 表、聚合类、业务键列**四处同词**（`spu`），消除词汇孤岛；
- 门禁语义回归本位：**新入口要显式走「新增 + 同步计数」**，杜绝再造垫片；
- 存储形态（`BINARY(16)`）与线格式**解耦**，将来换存储不动契约。

### 负面 / 成本

| 成本 | 说明 |
| :-- | :-- |
| **BREAKING（对外路径）** | 15 条端点的路径字面量变化；前端（B6）**必须**同批跟改 |
| **BREAKING（语义）** | I65 不再接受技术键；非法形态由「空体」变为 **400** —— 若有调用方依赖旧行为，需同步 |
| **窗口期风险** | B4（文档）与 B5（代码）之间，**照契约编码会 404**：已写入契约文件头与 §1.9⑤，接入方以代码实测为准 |
| **门禁数字暂时变差** | `extract-endpoints.mjs` 由 287/0/43 变 **272/15/58** —— 这是契约先行的**投影**，不是回归；B5 后应复原 |
| **前端路由是否跟改仍开放** | `/product/[id]` 等前端路由涉及外链/SEO/收藏夹，属产品决策，**未裁决**（§十二 12.2 新增行）|

### 已知残余风险（须显式登记，不得视为已解决）

| # | 残余风险 | 触发条件 | 后果 | 收敛方向 |
| :- | :-- | :-- | :-- | :-- |
| E1 | **P37 `scene` 长度约束** | 微信 `wxacode.getUnlimited` 的 `scene` 上限 **32 可见字符**；现实现 `scene = "p=" + 技术键`（短），按本条改为 32-hex 业务键后为 **34 字符 → 超限** | 生成失败（fail-loud）或**截断后回链错商品**（后者不可接受） | ⏳ **仍开放（B5 按寄存形态处置并显式登记）**：B5 保留 `scene`/objectKey 走**技术键**（零行为回归、长度不超限），并在 `SpuShareManager` 类注释与交付报告登记为本规则的**唯一例外**；**不做短码映射**（属小程序承载层设计，须单独立项）、**不截断**。待裁决：(a) 现状（保技术键并登记）；(b) 短码/短链（推荐，需新批次）；(c) 截断（**不可接受**）。旁证：小程序前端当前**零处**解析该 `scene`（仅登录用 `scene`），故暂无观测影响 |
| E2 | **统计行与内部 CO 的 `productName` 键名** | S11/AD14 返回 `Map` 字面键 `"productName"`（camelCase）；`CartItemCO.productName`（I16） | 与 ADR-056（snake_case）与 ADR-061（`spu_name`）双重不符；前端需按方言解析 | ✅ **已闭合（B5）**：`SellerDashboardManager.KEY_SPU_NAME` / `AdminManager.topSpus` 字面键、`CartItemCO` 字段全部收口为 `spu_name`（**BREAKING**）；`HistoryVO.product_name` 仍为例外保留（`@JsonProperty` 显式钉名） |
| E3 | **`RecommendSpuVO.spuId` 仍为 `Long`** | P27/P35 卡内 `spu_id` 实测技术键（ID 门禁白名单项） | 前端若按契约当 32-hex 消费则**回链失败** | ✅ **已闭合（B5）**：`RecommendSpuVO.spuId` 改 `String` 业务键（`SpuManager#toRecommendVO` / `KgPairingManager#toCard` 同批改），路径变量同型同义；**门禁白名单项已同步删除**（否则报「白名单陈旧」） |
| E4 | **`ShopDecoration{VO,Req}.hotSpuIds` 仍为 `List<Long>`** | merchant 域不持有 SPU 业务键（ADR-048 §决策 4：库列保持 `BIGINT`） | 写侧与读侧必须同型同义，否则半侧收口更糟 | ✅ **已闭合（B5，用户裁决「继续」）**：`List<Long>` → `List<String>` 业务键（**写读同批收口**，含领域工厂/仓储 JSON 列/PO 注释/`Initialize.sql` 列语义），形态校验走 `IdGenerator.requireWire`（非法即 400）；**不做跨域存在性校验**（ADR-048 Phase 1 + ADR-057「业务判断不得同库直读」）。详见 §B5 实施记录「DB 列改型取舍」 |
| E5 | **`KgEdgeUpsertCO.srcId`（`Long`）** | 不在 ID 门禁命中面（字段名不含 `spuId`） | 内部契约遗留技术键 | 随 KG 域批次收口（**未做**） |
| E7 | **`SpuDetailClient` 走公开契约 + 技术键入参** | `ia-api` `client/SpuDetailClient.java`：`@FeignClient(path = "/api/item/product")` + `@GetMapping("/{id}") Result<SpuDetailCO> getSpuDetail(@PathVariable Long id)`，调用方 `ai-service` `FeignSpuDetailAdapter`（**活链路，非死代码**） | ① 它是**服务间调用却走公开契约**（应走 `/internal/**`，ADR-004）；② 路径变量是**技术键 `Long`**，违反本条 §决策 3 | 🟡 **部分收敛（B5 最小改动）**：`path` → `/api/item/spu`、`@PathVariable Long id` → `@PathVariable String spuId`（并随之下沉 `SpuDetailPort`/`SpuDetailTool`/降级工厂为业务键）；**根治（迁 `/internal/**`）仍待单独立项** —— 会**新增端点 → `codeOnly` +1**，按 §决策 4 属显式决策 |
| E6 | **跨文档引用未同步** | 其他 `project-docs/` 文档仍引用旧路径字面量（12 个文件、35 处） | 文档间自相矛盾，读者按旧路径编码 | ⏳ **仍开放**（B5 无 `project-docs/` 除 `adr/` 外写权，按分权经主 agent 派单同步） |

## B5 实施记录（2026-10-17，代码落码批）

> 本段是**实施证据**（谁在哪个文件落了什么码、门禁数字与变异验证），不是新决策；
> 决策口径仍以上文 §决策 1–7 为准。

### 1. 编解码唯一出口（§决策 1/7：B5 的**第一件事**）

`ia-common` `common/utils/IdGenerator` 在既有 `nextId/toBytes/toHexString/toBytesOrNull/secureToken`
之上收敛出**语义命名**的三个门面方法（实现复用既有内核，零行为变化）：

| 方法 | 语义 | 使用面 |
| :-- | :-- | :-- |
| `toWire(byte[])` | 出方向：`BINARY(16)` → 32-hex | 持久化/领域层出口（可检索的「线格式出口」名） |
| `parseWire(String)` | 入方向（严格）：线格式 → `byte[]` | 只许服务内（仓储/领域） |
| `requireWire(String field, String wire)` | 适配层入参**形态校验唯一口**：合法 → 归一化 32-hex；非法 → `PARAM_ERROR(1101)`/**HTTP 400** fail-fast | 所有外部路径变量/入参 |
| `isWire(String)` | 形态判定（不抛） | 防御性分支 |

同时**收严 `toBytesOrNull` 的语义边界**（javadoc 显式写定）：宽松语义**只许内部防御**
（未命中归一 / 已校验后的二次解析），**不得作为线格式入口** —— 门禁「检查 6」把这条钉成机械约束。

### 2. 落码面（文件级）

| # | 决策条款 | 落码文件（要点） |
| :- | :-- | :-- |
| 1 | §决策 2 表 A（27 处入口） | `item-service` `SpuController`（类级 `/api/item/spu`）、`ReviewController`（`/spu/{spuId}` ×3）、`InternalItemSearchController`（`/detail/{spuId}`）；`search-service` `SearchController`（`/spu`）、`InternalSearchController`（`/vector/spu/{upsert,similar}`）；`gateway-service` `SecurityConfig` javadoc + 3 个测试夹具（路由通配，**零白名单改动**） |
| 2 | §决策 2 表 A #21（Feign） | `ia-api` `SpuVectorClient`（`path=/internal/search/vector/spu`）、`ItemSearchClient`（`/detail/{spuId}`）、**`SpuDetailClient`**（`path=/api/item/spu` + `String spuId`；连带 `SpuDetailClientFallback`、`ai-service` `SpuDetailPort`/`FeignSpuDetailAdapter`/`SpuDetailTool` 收业务键） |
| 3 | §决策 2 表 C（路径变量） | 18 处路径变量：`SpuController` 7、`ReviewController` 3（类型 `Long`→`String`；`/{id}/reply\|append\|like` **保持 `Long`**：评价行主键）、`InternalItemSearchController` 1、`CartController` 2（B2 已合规，本次纳入门禁面）；`user-service` `HistoryController` `{id}`→**`{historyId}`**（足迹行主键勘误，类型保持 `Long`）；**下游 Manager 同步改型**：`SpuManager.{getSpuDetail,listSkus,similarSpus,updateSpu,updateSpuStatus}`、`ReviewManager.{pageBySpu,pageBySpuFiltered,reviewSummary}`、`KgPairingManager.pairing`、`SpuShareManager.getWxaCode` |
| 4 | §决策 3（解析只许服务内） | 新增 `SpuRepository.findByBusinessId(String)`（走 `uk_spu_id`；`SpuMapper.selectByBusinessId`）；`ReviewManager#spuTechnicalIdOrNull`（读路径 fail-open）、`SpuManager`（详情 fail-loud / 列表与推荐 fail-open）、`KgPairingManager`（fail-open 空态） |
| 5 | §决策 4（删双形态） | `ItemSearchQueryManager#detail` 只按业务键解析；`isTechnicalKey` 分支删除；**连带退役**再无调用方的技术键查询 `SpuRepository.findSearchCardById` + `SpuMapper.selectSearchCardById`（H2 用例同步合并到业务键变体，`publishTime` 断言保留） |
| 6 | §决策 3（E4 收口） | `merchant-service`：`ShopDecoration{VO,Req}.hotSpuIds` → `List<String>`、领域工厂 `normalizeHotSpuIds` 走 `requireWire`、`ShopDecorationRepositoryImpl` JSON 列统一 `List<String>`、`ShopDecorationPO` 注释、`Initialize.sql` `hot_spu_ids` 列**语义改型**（物理仍 `VARCHAR(1000)`，容量核算见下） |
| 7 | E2 线格式键名 | `trade-service` `SellerDashboardManager.KEY_SPU_NAME`/`AdminManager.topSpus` 字面键 → `spu_name`；`ia-api` `CartItemCO.productName` → `spuName`（线上名 `spu_name`），`cart-service` `CartConverter` 忽略项跟改 |
| 8 | 测试夹具 | `item` `SpuControllerWxaCodeTest`（+新增 400 fail-fast 用例）、`SpuManagerTest`、`ReviewManagerTest`（+新增「未知业务键 → 空页」用例）、`KgPairingManagerTest`、`SpuShareManagerTest`、`ItemSearchQueryManagerTest`、`SpuSearchMapperH2Test`；`search` `InternalSearchControllerTest`；`merchant` 3 个装修测试；`gateway` 3 个测试；`cart` `CartConverterContractTest` |

### 3. DB 列改型取舍（E4，写读同型同义）

**列物理形态不变**（`hot_spu_ids VARCHAR(1000)`，JSON 文本），**改的是列的语义**（注释 + 存储元素类型）：

- **容量核算**：≤8 个业务键 ×(32 字符 + 2 引号 + 1 分隔) ≈ **274 字节 ≤ 1000** → 无需扩容；
- **为何不改 `BINARY(16)` 或 JSON 类型**：该列是「列表 JSON 文本」，`banners` 同列族；改类型需要 `TypeHandler` + 迁移，而 ADR-045 已定「库无存量数据、结构直接改快照」——**收益为零、风险更高**；
- **为何不做跨域存在性校验**：① ADR-048 Phase 1 明示「不校验归属/存在性，展示端过滤」；② 补校验须读商品数据，而同库直读他域表做**业务判断**被 ADR-057 明令禁止（只允许展示读模型），走 Feign 又受「调 Feign 的 Manager 不得 `@Transactional`」硬约束并需**新增端点**（§决策 4：新端点属显式决策）。
  → 因此本批只做**形态校验（32-hex，非法即 400 fail-fast）+ 原样存取**：写侧与读侧**同型同义**，不留半侧收口。

### 4. 门禁数字与变异验证

| 门禁 | B4 后（本批前） | B5 后 | 判据 |
| :-- | :-- | :-- | :-- |
| `extract-endpoints.mjs` | matched 272 / codeOnly 15 / docOnly 58 | **287 / 0 / 43** | 核心收敛判据，回到 B4 前基线 |
| `check-section7.mjs` | 存在 121 / 不存在 22 / 方法不符 2 | **130 / 13 / 2** | 同上 |
| `check-id-invariants.sh` | `[OK]`（4 项检查；路径变量与适配层面未覆盖） | `[OK]`（**6 项检查**，含新增检查 5/6 + 两条命中面自证 + 白名单陈旧自证） | 见 §守卫 1/4 |
| `IdWireRoundTripTest` | 不存在 | ✅ 命中 66 类 / 93 字段 | 见 §守卫 3 |

**变异验证（证明门禁有捕获力，非空转）**：把外部 `@PathVariable` 改回 `Long` → 检查 5 报红（列出文件:行 + 路径 + 类型）；
把 `RecommendSpuVO.spuId` 改回 `Long` → `IdWireRoundTripTest` 报红（渲染为 JSON 数字）；
在某 `adapter/` 写入 `IdGenerator.toBytesOrNull(...)` → 检查 6 报红。三者恢复后均回绿（原样输出见 B5 交付报告）。

### 5. P37 `scene`（E1）在本批的处置与登记位置

- **代码登记**：`item-service` `SpuShareManager` 类 javadoc「⚠ P37 `scene` 与业务键寻址的冲突（本批显式登记，未裁决，不静默截断）」+ `SCENE_PREFIX` 常量注释 + `generateAndUpload` 行内注释；
- **实现形态**：`scene = "p=" + 技术键` 与 objectKey 继续走技术键（**零行为回归、长度不超限**），
  入参侧则已是业务键并做形态校验 —— 即「入参收业务键、承载层字段暂存技术键」的**寄存形态**；
- **为何不选其它**：短码/短链属小程序承载层设计（须单独立项），截断会指向错商品（**不可接受**）；
- **旁证**：小程序前端当前**零处**解析该 `scene`（仅登录用 `scene`），故本冲突暂无观测影响；一旦前端按 `scene` 回链，必须先落地短码方案。

## 日期

2026-10-17

## 参考

- **契约**：`contract/05-API-Specification.md` §1.8（SPU/SKU 标识口径）、**§1.9（SPU 资源段与寻址口径，本 ADR 的正文载体）**、
  §4.4.x（P1-P7/P24/P27/P35/P37）、§4.4.4（P13-P15）、§4.2.11（P37 + scene 注）、§4.14.5.1（I65）、§4.14.10（I47/I48）、
  §4.3.4（S11）、§4.13.4（AD14）、§4.13.12（AD38/AD39）、§十二 12.2（挂账与残余风险）
- **代码（实测依据，2026-10-17）**：
  - `ia-common/src/main/java/org/icedamericanomall/common/utils/IdGenerator.java`（`nextId`/`toBytes`/`toHexString`/`toBytesOrNull`/`secureToken`）
  - `item-service` `adapter/web/SpuController.java`、`adapter/web/ReviewController.java`、`adapter/internal/InternalItemSearchController.java`、`app/share/SpuShareManager.java`
  - `search-service` `adapter/internal/InternalSearchController.java`（`/vector/product/upsert`、`/vector/product/similar`）、`adapter/web/SearchController.java`（`/product`）
  - `ia-api` `client/SpuVectorClient.java`（`path = "/internal/search/vector/product"`）、`client/ItemSearchClient.java`（`/detail/{id}`）、`dto/CartItemCO.java`（`productName`）
  - `cart-service` `adapter/web/CartController.java`（`@PathVariable String skuId`，B2 已收口）
  - `user-service` `adapter/web/HistoryController.java` + `adapter/response/HistoryVO.java`（`product_name` 例外、`{historyId}` 勘误）、`app/history/HistoryManager.java`
  - `trade-service` `app/dashboard/{SellerDashboardManager,AdminManager}.java`（`"productName"` 字面键）、`adapter/response/{OrderItemVO,SkuVO}.java`
  - `merchant-service` `adapter/response/{ShopDecorationVO,ShopDecorationReq}.java`（`List<Long> hotSpuIds`）
  - `gateway-service` `config/SecurityConfig.java`（`PUBLIC_GET_PATHS` 用 `/api/item/**` 等通配前缀）+ `resources/application.yml`（路由同样通配）→ **本改名零网关改动**
  - `back-end/scripts/{extract-endpoints.mjs, check-id-invariants.sh}`
- **ADR**：[ADR-002](./002-dual-primary-key-and-money.md)（双主键 + 订单快照）、[ADR-050](./050-business-id-binary16-uuidv7.md)（`BINARY(16)`+UUIDv7 + `IdGenerator` 唯一出口）、
  [ADR-052](./052-search-db-fallback-exemption.md)（I62-I65 检索数据面）、[ADR-056](./056-wire-naming-snake-case.md)（snake_case）、
  [ADR-057](./057-display-field-read-model-ports.md)（读模型端口）、[ADR-058](./058-vo-identifier-semantics.md)（VO 标识语义唯一性）、
  [ADR-059](./059-spu-sku-vocabulary-disambiguation.md)（SPU/SKU 词汇消歧）、[ADR-061](./061-order-item-business-key-snapshot-and-price-compare.md)（线格式标识类型维度；**本 ADR 修订其 §决策 6**）、
  [ADR-045](./045-db-snapshot-single-source.md)（库无存量数据）、[ADR-048](./048-content-notes-in-item-domain.md)（`shop_decoration` 内部外键保持 `BIGINT`）、
  [ADR-053](./053-doc-ownership-by-nature-and-two-layer-standards.md)（文档分权）
