# ADR-061: 订单项 = 业务键标识 + 快照 —— 线格式标识收口与比价/失效读时增强

## 状态

**已实施（2026-10-17，提交 `2325031d`）** —— 决策日期 2026-10-16（批次 B1.5，契约与决策先行），实施日期 2026-10-17（批次 B2）；**守卫缺陷与残留于同日批次 B2.5 修复/处置**（见 §决策 8 补注、§后果 R7/R8）。

**实施范围与证据**：`order_item` 三处 DB 改动、线格式标识类型收口、比价读模型端口与装配、ES 文档标识口径、券 scope 业务键化**均已落码并通过全量验证**（1913 tests / 0 failures / 0 errors / 1 skipped；端点对账 287/0/43；ID 门禁 `[OK]`）。逐项实施事实见本 ADR §决策 1/2/3/5 与 §后果 R1-R4 的「**实施**」标注，以及交付记录 `Implementation/back-end/docs/b2-delivery-status.md`。

**B2.5 补记（2026-10-17）**：① ID 门禁第 4 项检查的命中面 glob **对 adapter 面零匹配**（假绿，见 R7），已改 `find` 遍历 + 自证；② 该缺陷掩盖的 **9 处** type 违规中 **6 处收口为 `String` 业务键**、**3 处登记白名单保留技术键**（逐项理由见 §决策 8.2）；③ **零 DB 改动**（A 案）；④ 全量 **1918 / 0 / 0 / 1**（+5 用例，无删除），端点对账 **287/0/43 不变**。**据此不得外推为「全平台标识类型已全量收口」** —— 未收口项见 R1/R8。

> ⚠ **两项本文档据此更正的既有表述**：
> 1. **§决策 2 的「增业务键字段」建议未采纳**：B2 实际选择**直接改型**（`SkuCO.spuId`、`SpuVector*` 全链由 `Long` → `String`），未走 ADR-058 §3 的「先增字段」过渡路径 —— 依据是库无存量数据（ADR-045）且该批已是一次性 BREAKING 批次，双字段会重新引入 ADR-058 记录的「同名异型异义」。
> 2. **§决策 5 的主图线上名结论被推翻**：`spu_image` **未采用**，线上名最终统一为 `main_image`（见本 ADR §决策 5 的「B2 落定」段与 §后果 R6）。

**本 ADR 一并收口 ADR-059 §2 的既有违规**：`SkuVO.spuId` 在 B1.5 决策时仍是 `Long` 技术外键（ADR-059 §背景已自认「守卫只查命名、未查类型，故漏放」），本 ADR 补上类型维度。**B2 已落码为 `String`**（`item-service` `SkuVO.spuId` / `skuId`）。

## 背景

### 三个业务诉求

| # | 诉求 | 用户原话要点 |
| --- | --- | --- |
| 1 | **可追溯商品源头** | 从历史订单能回到商品实体（哪怕 SKU 已换、已下架） |
| 2 | **不暴露技术 id** | 报文里不出现可枚举的自增主键（遍历/爬取风险） |
| 3 | **可做价格对比与失效提示** | 「之前购买」与「现在购买」比价；下架/缺货要有提示 |

### 现状取证（2026-10-16 实测，`database/Initialize.sql` + 后端源码 + 契约）

**① 订单项标识是技术键，且缺 SPU 键**（`Initialize.sql` L352-371）：

```sql
CREATE TABLE `order_item` (
  `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
  `order_id` BIGINT NOT NULL COMMENT '所属订单ID',
  `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名快照',
  `sku_spec` VARCHAR(200) NOT NULL COMMENT '规格快照',
  `price` INT NOT NULL COMMENT '单价快照（分）',
  `quantity` INT NOT NULL,
  `sub_total` INT NOT NULL COMMENT '小计（分）',
  `image` VARCHAR(255) DEFAULT NULL COMMENT '图片快照',
  `package_no` INT DEFAULT NULL COMMENT '归属包裹号',
  ...
  CONSTRAINT `fk_orderitem_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`)
) COMMENT='订单项表';
```

即：`sku_id` 是**技术外键**（指向 `sku.id`）；**无 `spu_id` 列**（连技术键都没有）；`product_name` 是唯一的商品名快照列。

**② 业务键在实体表早已就位**：

| 表 | 列 | 形态 |
| --- | --- | --- |
| `spu` | `spu_id BINARY(16) NOT NULL` + `UNIQUE KEY uk_spu_id` | **SPU 业务键**（ADR-050） |
| `spu` | `name VARCHAR(200) NOT NULL` | SPU 名称 |
| `sku` | `sku_id BINARY(16) NOT NULL` + `UNIQUE KEY uk_sku_id` | **SKU 业务键** |
| `sku` | `spu_id BIGINT NOT NULL` + `fk_sku_spu → spu(id)` | **技术外键**（同名列、不同型） |

**③ 命名：`product_name` 是 SPU 的名字，但没随 SPU 实体化改名**

`Initialize.sql` 中 `product_name` 只有 **2** 处命中：`order_item.product_name`（L356，唯一列定义）与 `merchant_reply_template.content` 注释里的模板占位符 `{product_name}`（L841，非列定义）。**暴露面（2026-10-16 取证口径，`grep -rn ... --include=*.java --exclude-dir=target`）**：

| 载体 | 处数 | 分布 |
| --- | --- | --- |
| 后端 Java `productName` | **36**（主代码 29 + 测试 7；主代码含 3 处 MapStruct `@Mapping(target = "productName", ignore = true)`） | item 8 / user 6 / trade 6 / marketing 6 / cart 5 / ia-api 3 / search 2 |
| 后端 Java `productImage` | **5** | marketing `FlashSaleVO.productImage` 1 + `FlashSaleSpuBrief` 2 + `FlashSaleViewManager` 1 + 1 处 H2 测试 |
| 契约 `product_name` / `product_image` | **14** / **2** | 端点字段表 + §八 样例 |
| 前端 `product_name` / `product_image` | **32**（12 文件）/ **9**（5 文件） | web / miniprogram / `packages/api/types` |

> 处数为**取证时点快照**；B2/B3 实施后须重新测量（不得沿用本表数字）。

SPU 实体化（ADR-059）后，其名字列/线上名仍叫 `product_name`，属**词汇遗留**——与「SPU 实体的名字」这一语义不符；SPU 主图线上名 `product_image`（后端 5 处 / 前端 9 处）同理。

**④ 类型：线格式标识的类型口径未收口**（ADR-059 自认的漏洞）

| 线格式字段 | 实现类型 | 应为 |
| --- | --- | --- |
| `SkuVO.spuId`（item `SkuVO` L14） | `Long`（技术键） | `String` 32-hex |
| `FavoriteVO.spuId` / `HistoryVO.spuId`（user） | `Long` | `String` 32-hex |
| `ReviewVO.spuId` / `ReviewCreateReq.spuId`（item） | `Long` | `String` 32-hex |
| `FlashSaleVO.spuId` / `skuId`、`FlashBuyVO.spuId`（marketing） | `Long` | `String` 32-hex |
| `CartItemVO.skuId`、`CartAddReq.skuId`、`CartUpdateReq.skuId`（cart） | `Long` | `String` 32-hex |
| `OrderItemVO`（trade） | 有 `skuId:Long`；**无 `spuId`** | 两者均 `String` 32-hex |

**后果**：客户端拿到的是**技术键**（可枚举）且与商品域产出的业务键**不同型**——「从收藏/订单回链商品页」在类型层面不可能成立（与 ADR-058 记录的 `coupon_id` join 缺陷同型）。

## 决策

### 1. 订单项 = 标识（业务键）+ 快照，两者同批冻结

`order_item` 的**标识**部分改为业务键，与**快照**在同一事务、同一时点冻结：

| 部分 | 列 | 语义 |
| --- | --- | --- |
| **标识** | `spu_id BINARY(16) NOT NULL`（**新增**） | SPU 业务键 —— 商品源头回链/比价的锚点 |
| **标识** | `sku_id BINARY(16) NOT NULL`（**改型**，原 `BIGINT` + `fk_orderitem_sku` 一并移除） | SKU 业务键 |
| **快照** | `spu_name`（原 `product_name`，**改名**）/ `sku_spec` / `price` / `quantity` / `sub_total` / `image` / `package_no` | 下单时点事实，**绝不回查实时表** |

- 「标识」是**指向**（可用于查询实时表），「快照」是**事实**（用于渲染历史）。二者**不得互相顶替**：活表改名/改价/下架后，历史订单必须仍显示下单时点的快照。
- 保留 `order_id BIGINT`（订单内部技术外键，不出线格式）。
- 索引：`idx_sku_id(sku_id)`、`idx_spu_id(spu_id)`（业务键唯一索引已在 `spu`/`sku` 上，`order_item` 侧为查询索引）、`idx_order_package(order_id, package_no)` 不变。

> **为何 `sku_id` 改型而非「业务键 + 技术外键」双列**：双列会保留两个都叫 `sku_id`/`skuId` 语义不同的字段（技术外键 vs 业务键），正是 ADR-058 记录的「同名异型异义」病灶；且订单项对本项目不存在「按技术键 join 订单项」的需求（订单项子实体只按 `order_id` 聚合）。**代价**：失去 DB 级 FK 完整性（见 §后果 R3）。

### 2. 线格式标识一律业务键（32-hex `String`）

**凡代表 SPU/SKU 实体的标识**，无论出现在响应 VO、请求体、查询参数还是内嵌对象，一律 `String` 32-hex（`BINARY(16)` 的十六进制形态，经 `ia-common` `IdGenerator` 编解码，ADR-050 §「唯一出口」）。

- **技术键（`spu.id`/`sku.id`）与技术外键（`sku.spu_id`/`order_item.order_id`）只保留在 PO 与领域层**，不得出现在任何 VO/DTO（ADR-058 §1 原口径，本 ADR 补类型维度）。
- 与业务标识**不同名**的字段不受本约束：`SpuVO.id`/`SkuVO.id`/`OrderItemVO.id`/`ReviewVO.id` 等**行主键/渲染 key** 保持现状（它们是资源键，不是 SPU/SKU 实体标识的对外口径）；其收敛属 ADR-058 §1 的既有偏差，**本 ADR 不扩大范围**（已登记 §十二 12.2）。
- **示例值规范形态为 32-hex**（如 `0f1e2d3c4b5a69788796a5b4c3d2e1f0`）；可读编码（`prod-001`/`sku-001`）在写入端即被 fail-fast 拒绝（对齐 §4.10.1 券号先例）。

### 3. 比价/失效提示 = ADR-057 读模型 + 按业务键批量 + fail-open

**定位**：**读时增强（read-time enrichment）**，不是订单域事实。它**不写入订单**、不改变任何金额、不改变订单状态机。

**实现形态**（沿用 ADR-057 的「应用层端口 + 按 ID 批量 + fail-open」三件套，端口实现只读他域表）：

- 端口定义在应用层 `app/port/`，实现在 `infrastructure/persistence/`，**不外泄到适配层**；
- 按**业务键集合**批量入参（整页一次查询，**禁止逐行调用**，无 N+1）；
- 只读（`SELECT`），**不得**改写被读域数据；
- 定位为**展示读模型**：输出**仅用于展示**，**禁止**作为下单/退款/售后等**业务判定**依据（业务判断仍必须走 Feign 或本域数据——ADR-057 §「边界对照」不变）。

**端口签名（B2 已落码，与建议一致，仅类型归属按分层修正）**：

```java
// trade-service/app/port/OrderItemCurrentStateQuery.java
Map<String, SkuCurrentState> findSkuStates(Collection<String> skuBusinessIds);   // key = sku 业务键
Map<String, SpuCurrentState> findSpuStates(Collection<String> spuBusinessIds);   // key = spu 业务键

// trade-service/domain/model/order/SkuCurrentState.java（值对象归位，见 §决策 7）
record SkuCurrentState(String skuId, String spuId, Integer price, Integer stock,
                       Integer stockType, Integer status) {}   // 行不存在 = 该键不出现

// trade-service/domain/model/order/SpuCurrentState.java
record SpuCurrentState(String spuId, Integer status, Integer minPrice) {}  // minPrice = 在售 SKU 最低价
```

> **与建议的唯一差异**：两个 record **未**留在 `app/port/` 作嵌套类型，而是上移为领域层值对象（§决策 7）。

**比对语义**（纯函数，落 `domain/service/` 或同层，便于单测）：

| 快照 vs 实时 | 判定 | 展示口径 |
| --- | --- | --- |
| SKU 业务键**查不到行**（物理删除） | `UNCOMPARABLE` | 只显示快照 + 「无法比对」（**不得**推断为已下架、**不得**回落技术键） |
| `spu`/`sku` 行在，`sku.price ≠ order_item.price` | `PRICE_CHANGED` | 回显现价（分）；同 SPU 有更低价时可用 `SpuCurrentState.minPrice` 提示「同款现价 ¥X」 |
| `spu.status = 2 下架 / 3 删除` | `OFF_SHELF` | 提示「已下架」（快照照常显示） |
| `sku.status = 0 停售` | `DISCONTINUED` | 提示「已停售」 |
| `sku.stock = 0` **且** `stockType` 为需校验库存的类型（`LIMITED`/`PRESALE`，对齐 `StockTypeEnum.requiresStockCheck`） | `OUT_OF_STOCK` | 提示「暂时缺货」 |
| 以上皆否 | `IN_SYNC` | 不提示 |

> **为何必须带 `stockType`**：`sku.stock_type = 2 UNLIMITED 不限量` 的 SKU 库存恒为 0 也**不是**缺货；漏掉该列会把不限量商品一律标成缺货（假提示）。

**fail-open（硬口径）**：

| 情形 | 读模型行为 | 契约 / 前端行为 |
| --- | --- | --- |
| 数据源异常（DB 不可用 / 超时） | 捕获 → 返回空 `Map` + `WARN` 日志 | 明细**照常返回**，比价态 = 「无法比对」；**不报错、不缩行** |
| 单条业务键无对应行 | 该键不出现（不伪造） | 同 `UNCOMPARABLE` |
| 读模型未启用 / 端口未接线 | 空 `Map` | 只显示快照（与「历史订单」现状零差异） |

「不缩行」是本决策的验收条件：**读时增强的失败不得让订单列表/详情少一条**（与 ADR-057 §决策 的 fail-open 同口径）。

> **B2 落地形态（2026-10-17 实测，与本节口径逐条对齐）**：
> 1. **端口**：`trade.app.port.OrderItemCurrentStateQuery` —— `findSkuStates(Collection<String>)` / `findSpuStates(Collection<String>)`，**按业务键批量**（实现 `OrderItemCurrentStateQueryImpl` 每条 SQL 一次 `IN (...)`，**无 N+1**）；非法业务键经 `IdGenerator.toBytesOrNull` 落「未命中」（不放大成 500）；返回 `LinkedHashMap`，**行不存在时该键不出现**。
> 2. **fail-open 的落点与建议不同**：端口接口**不吞异常**（真实故障须在调用侧可见），fail-open 由应用层 `trade.app.order.OrderCurrentStateEnricher` 统一兜住（`catch` → 空 `Map` + `WARN`），**不缩行**由此保证。SPU 侧降级**不整体放弃** SKU 侧结果（价格/缺货提示仍可给出），故两侧各自 try/catch。
> 3. **比对纯函数**：`trade.domain.service.OrderItemPriceComparator#compare(snapshotPrice, snapshotSpuId, currentSku, currentSpu)` —— 判定**短路顺序**为 `UNCOMPARABLE`（SKU 键查不到行）→ `OFF_SHELF`（SPU status 2/3）→ `DISCONTINUED`（SKU status 0）→ `OUT_OF_STOCK`（库存 0 且需校验库存类型）→ `PRICE_CHANGED` → `IN_SYNC`；`CompareState` 是**服务端内部枚举**（⛔ **不随订单报文出栈**）。
> 4. **`StockAvailabilityPolicy` 为独立类**：缺货提示口径（`LIMITED`/`PRESALE` 需校验，`null` 按 `LIMITED`）与下单校验口径 `StockTypeEnum.requiresStockCheck`（`null`/`LIMITED` 才看库存上限）**两口径并存为有意选择** —— 目的是**不影响既有下单语义**（改它会影响下单成败）。两处各自有单测与变异验证锁定。
> 5. **线格式未加比价字段（有意）**：`OrderItemVO` **无任何比价字段**，`OrderCurrentStateEnricher.compare` 的产出现**无对外消费点** —— 按契约 §十二 12.2「形态未定、前端不得对接」处理；能力已就绪，**契约定稿后接线即用**（避免无排期承诺）。

### 4. DB 变更范围限定：只改 `order_item`

| 表 | 处理 |
| --- | --- |
| `order_item` | **改**：`sku_id` 改型 `BINARY(16)`、新增 `spu_id BINARY(16)`、`product_name` → `spu_name` |
| 其余全部表（`sku.spu_id`、`review.spu_id`、`favorite.spu_id`、`spu_view_log.spu_id`、`note_spu.spu_id`、`flash_sale.spu_id`、`group_buy_activity.spu_id`、`shop_decoration.hot_spu_ids` 等） | **不动**，内部外键保持技术键 `BIGINT` |

> **B2 实施事实（2026-10-17 落码，实测 `Initialize.sql` L352-372）**：`order_item` 的 `sku_id` 已为 `BINARY(16) NOT NULL`、`spu_id BINARY(16) NOT NULL` 已新增、`spu_name` 已改名、`fk_orderitem_sku` 已移除、`idx_spu_id` 已建；**保留 `fk_orderitem_order`**（`order_id` → `orders.id`，订单内部技术外键，`ON DELETE CASCADE`）。索引实况 = `idx_order_id` / `idx_sku_id` / `idx_spu_id` / `idx_order_package(order_id, package_no)`。**其余表的内部 FK 一律未动**（A 案：`sku.spu_id`、`review.spu_id`、`favorite.spu_id`、`spu_view_log.spu_id`、`note_spu.spu_id`、`flash_sale.spu_id`、`group_buy_activity.spu_id`、`shop_decoration.hot_spu_ids` 等保持技术键 `BIGINT`）。

理由：**库内 join 用技术键是行业标准**（同库同分片下性能最优、外键完整性可保），而**这些内部外键不出线格式**（ADR-058 §1）；订单项之所以例外，是因为它承载「三个诉求」且**必须自持商品源头的业务键**（历史订单不能依赖 `sku` 行仍然存在）。

### 5. 命名收口：`product_name` → `spu_name`；主图线上名统一为 `main_image`

SPU 实体化后其名字列/线上名统一为 `spu_name`（列名与 JSON 键名同改）；SPU 主图线上名统一为 **`main_image`**（**B2 落定，非 B1.5 提案的 `spu_image`**）。

**为何是 `main_image` 而不是 `spu_image`**（决策记录，避免后人回改）：

| 理由 | 说明 |
| --- | --- |
| **与 DB 列一致** | DB 列即 `spu.main_image`；`main_image` 与列名同名，`spu_image` 需新造一个与列名不一致的线上方言 |
| **与多数既有 VO 一致** | `SpuVO` / `FavoriteVO` / `HistoryVO` / `SpuSearchVO` / `RecommendSpuVO` / `NoteDetailVO.spus[]` / `SpuVectorUpsertCO` 早已用 `main_image` —— 采用 `main_image` **只需回退本 VO（`FlashSaleVO`）一处**；采用 `spu_image` 则须改 6+ 处线上名并演进 DB 列名 |
| **「主图」是区分性命名** | 行业形态中**主图 / 图集 / 详情图 / SKU 图并存**，`main_image` 表达的是「哪一张」这一**区分信息**；`spu_image` 只表达归属（SPU 的图），与 SKU 图/图集相比**区分力更弱** |

- **实测口径（2026-10-17）**：后端主代码**零处** `spuImage` / `productImage`；`FlashSaleVO` 已回退为 `mainImage`（`marketing-service` `adapter/response/FlashSaleVO.java`），`FlashSaleSpuBrief.mainImage` 注释同步改记「线上名统一 `main_image`」。

- DB：`order_item.product_name` → `order_item.spu_name`（唯一列定义，`merchant_reply_template.content` 的模板占位符 `{product_name}` 见 §后果 R4）。
- 线格式：契约 14 处 `product_name` 全量改为 `spu_name`（**逐处核对语义均为 SPU 名称**，无一处是 SKU 名或订单项自身名）；2 处主图字段**最终回退为 `main_image`**（B1.5 曾改为 `spu_image`，B2 按上表理由回退 —— 契约内 `spu_image` 现为 **0 处**）。
- 保留项：`sku_spec`（规格）、`image`（订单项图片快照）不动。

### 6. 业务键 ↔ 技术键的解析面：**扩展既有入参口径，不新增端点**

**上下文**：I47（商品向量摄取）的幂等键、I48（相似检索）的锚点均收口为业务键后，search-service 侧仍需把业务键解析为 `spu.id` 技术键（ES 文档 `_id` 与 publishTime 回填取数用）。最初的实现方案是**新增一条解析端点** `GET /internal/item/search/detail/key/{spuId}`。

**决策**：**不新增路由**，改为让既有 `GET /internal/item/search/detail/{id}` 的**入参口径同时接受两种形态**：

| 入参形态 | 解析口径 | 消费方 |
| --- | --- | --- |
| 纯数字（如 `1001`） | 技术键 → `spu.id` | search-service 的 publishTime 回填（既有行为，**不变**） |
| 32-hex（如 `0f1e2d3c4b5a69788796a5b4c3d2e1f0`） | 业务键 → `spu.spu_id`（走 `uk_spu_id` 唯一索引） | 商品向量摄取/相似检索的锚点解析（本批新增） |

**为何值得记录**：该「新增端点」方案被**端点对账门禁当场拦下** —— `node Implementation/back-end/scripts/extract-endpoints.mjs` 报 `codeOnly: 1`（287/1/43），即出现未登记契约的新端点，违反「不改路径、不新增端点」的批次硬约束；回退为扩展入参口径后回到 `codeOnly: 0`（287/0/43）。这是**门禁真实拦下一次违规**的实例，也是把「新增端点」改写为「扩展入参口径」的决策依据。

**代价与边界**：

- 路径变量类型由 `Long` 放宽为 `String` —— 代价是**丢失框架层的数字校验**：非法串不再 400，而是落到「查不到 → 返回 null 体」（与既有「不存在返回 null 体」语义一致，故不放大成 500）。消费方（search-service）已按 fail-open 处理。
- 该端点仍是**内部端点**，形态变宽不改变对外契约（`codeOnly` / `docOnly` 均不受影响）。
- **不推广**：本决策只针对「同一实体、同一语义、两种形态入参」的内部解析场景。对外路径变量（R1）是否收口为业务键是**独立议题**，不因本决策自动成立。

### 7. 领域读模型值对象归位：`SkuCurrentState` / `SpuCurrentState` 上移领域层

**上下文**：B2 首轮把上述两个 record 定义在 `app/port/OrderItemCurrentStateQuery` 内作**嵌套类型**（端口自持入参/出参形状，看似内聚）。

**问题**：比对规则 `domain/service/OrderItemPriceComparator`（领域服务）以这两个类型为入参，于是 `domain → app` 形成**反向依赖**，触发 architecture-tests 分层守卫 `domainDependsOnNothingOutward` —— 实测 **20 次违规**（同一守卫多计）。

**决策**：

1. 两个 record **上移为领域层值对象** `trade.domain.model.order.SkuCurrentState` / `SpuCurrentState`；`app.port.OrderItemCurrentStateQuery` 只**引用**领域类型，依赖方向回归 `app/infra → domain`（合法）。依据：**比对规则是领域职责，其入参类型必须由领域层拥有**，否则领域服务无法独立于应用层编译与测试。
2. `OrderRepository.pageOrders` 的返回类型由 MyBatis-Plus `IPage<Order>` 改为**框架无关**的 `trade.domain.model.order.PageResult<Order>`（**新增**，与 item 域 `PageResult` 同形）。持久化实现内部完成 `IPage → PageResult` 转换，应用层 `OrderQueryManager` 再转回适配层既有的 `IPage<OrderVO>` 信封 —— **Controller 签名与 HTTP 报文零变化**。

**基线影响（已核对）**：`architecture-tests/archunit_store/` 冻结条目 **25 → 24**，且**只减不增、未新增任何冻结项**。机制 = 已冻结的旧签名字段失配：`Method <…OrderRepository.pageOrders(java.lang.Long, java.lang.Long, java.lang.Integer, int, int)> has return type <com.baomidou.mybatisplus.core.metadata.IPage>` —— B1 改签名（加 `keyword` / `startDate` / `endDate`）后该条目已与实现不符，B2 消除该违规后从基线文件删除（**有意治理动作，非静默冻结**）。净效果 = 21 次新增违规全部按实现修正归零，基线净删 1 条。

> 核对方式：`wc -l Implementation/back-end/architecture-tests/archunit_store/33c4c196-5fdf-4aab-920e-05e5c6c112b0` → **24**；`git show 2325031d -- Implementation/back-end/architecture-tests/archunit_store/` → **1 file changed, 1 deletion(-)**，diff 中删除的正是 `OrderRepository.pageOrders` 那一条。

### 8. B2.5 补注（2026-10-17）：守卫 glob 修复 + 9 处违规的逐项处置

**背景**：B2.5 实测发现 §守卫 表中「线格式标识类型断言」**对 adapter 面零匹配**（glob 不跨 `/`），
即 §决策 2 的类型收口在 **adapter 面从未被机械校验**（详见 §后果 R7）。本节登记修复与处置结论。

**8.1 守卫修复**：命中面由 bash glob 改为 **`find` 遍历**（对路径深度不敏感），并加两条自证 ——
① `SCANNED_DIRS == 0` → 直接 `[FAIL]`（杜绝「零匹配却报 OK」）；② 白名单条目**陈旧即失败**
（字段改名/收口后仍挂白名单会被点名）。修复后命中 **26 个目录**（13 `adapter/response` +
12 `adapter/request` + 1 `ia-api/dto`），`[OK]` 行会打印该数字。

**8.2 逐项处置（9 处）**

| # | 字段 | 处置 | 依据 |
| :- | :-- | :-- | :-- |
| 1–2 | `ReviewVO.spuId` / `.skuId` | **`Long` → `String`** | §决策 2；聚合仍持技术键，业务键由 `ReviewManager` **整页批量**回填（`spu` 主键 IN + I13 联表读模型各一次；fail-open 不缩行） |
| 3–4 | `ReviewCreateReq.spuId` / `.skuId` | **`Long` → `String`** | §决策 2；写路径同库只读解析（`spu_id` / `sku_id` 唯一索引），**解析不到 → `PARAM_ERROR` fail-fast**（不静默落指向不存在商品的评价） |
| 5 | `NoteDetailVO.spus[].spuId` | **`Long` → `String`** | §决策 2；`NoteManager` 同批一次反向解析（新增 `SpuRepository.businessIdMapByTechnicalIds`，`spu` 主键 IN） |
| 6 | `NotePublishReq.spuIds` | **`List<Long>` → `List<String>`** | §决策 2；整批解析 + **完备性守卫**（解析不到的键 → 400，**不静默丢弃**） |
| 7 | `RecommendSpuVO.spuId` | **白名单保留 `Long`** | 与商品详情路径变量 `GET /api/item/product/{id}` **同口径**（P27 卡 / P35 搭配卡回链走同一路径）—— §后果 R1 已登记为「有意的口径并存」；收口须与路径变量同批（本批未获授权） |
| 8–9 | `ShopDecorationVO.hotSpuIds` / `ShopDecorationReq.hotSpuIds` | **白名单保留 `List<Long>`** | 该字段是 **`spu.id` 寻址**且**展示端过滤失效/非在售**；ADR-048 §决策 4 明确登记 `shop_decoration.hot_spu_ids` **内部外键保持 `BIGINT`**；merchant 域**不持有也不解析** SPU 业务键（跨域校验留 Phase 2），收口需**改 DB 列形态 + 新增解析端点**（违反「不新增端点」硬约束）。写侧与读侧**必须同型同义**，故一并保留 |

**8.3 零 DB 改动（A 案）**：本批全部走「**线格式收业务键、库内保持技术键**」——
`review.spu_id/sku_id`、`note_spu.spu_id`、`shop_decoration.hot_spu_ids` 一律**未动**
（§决策 4 原文口径）。业务键 ↔ 技术键的解析/回填全在应用层，走既有唯一索引与主键，无 N+1。

**8.4 变异验证（adapter 面）**：把 `user-service` `adapter/response/FavoriteVO.spuId` 临时改回 `Long`
→ 门禁红并指名该文件该行；恢复 → 绿。**为何必须改在 adapter 面**：B2 的变异落在 `ia-api/dto`
（当时唯一生效的扫描面），**不能**证明 adapter 面被扫描；本批的变异面正是修前**漏扫**的面。

**8.5 仍未收口（不得据此宣称「全量收口」）**：`RecommendSpuVO.spuId`、
`ShopDecoration{VO,Req}.hotSpuIds`（白名单，见 R1/R8 与 §8.2 #7–#9）、
`KgEdgeUpsertCO.srcId`（不在命中面）、**对外路径变量**（R1，含 `ReviewController` 的
`/api/item/review/product/{spuId}` —— 由此形成「`ReviewVO.spuId` 是业务键、该端点入参仍是技术键」
的并存口径，属 R1 待裁决）。

## 备选方案

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| **`order_item` 双列**（业务键 + 保留 `sku_id BIGINT` 技术外键） | 保住 DB 级 FK 完整性；改动面小 | 两个字段都叫 `sku_id` 但一为业务键一为技术外键 —— 正是 ADR-058 记录的「同名异型异义」病灶；线格式仍需选一个，另一个成为纯冗余列 |
| **比价走高德/外部价格服务或 Feign 实时查** | 边界干净（不直读他域表） | 订单列表 ⇒ 每单一次跨服务调用（或批量端点 + 逐行回退），把列表页变脆；ADR-057 已裁定同库只读读模型「批量 + fail-open」更优，且本项目为单一 schema |
| **下单时就把「实时性」冻结进订单（存 `current_status`）** | 无需读模型 | 语义错误：订单项存的是**下单时点事实**，「现在是否下架」按定义是**读时**信息，冻结即永远过期 |
| **比价直接暴露技术 id 给前端自行查询** | 前端自由 | 违反诉求 2（不暴露技术 id），且前端需 N 次请求 |
| **不改（维持现状）** | 零成本 | 三个诉求全部无法满足；`SkuVO.spuId: Long` 违规常驻 |

## 后果

**正面**

- 三个诉求同时成立：业务键可回链源头、报文无技术 id、业务键可批量比价；
- 订单项自持 `spu_id`/`sku_id` 业务键 ⇒ **即使 SKU 行已删除也能落到 SPU 粒度做「同款现价」比对**；
- 线格式标识首次**同型同义**（**本轮收口面**为 32-hex `String`），跨接口本地 join 在类型层可成立；
  > ⚠ 限定：本项指**本轮实际收口的字段面**（订单项、购物车、收藏/足迹、秒杀/拼团、`SkuCO`、`SpuVector*` 全链等）。**并非全平台标识字段均已收口** —— 未收口清单与原因见 §后果 **R7 / R8**（`ReviewVO`、`ReviewCreateReq`、`NoteDetailVO.spus[]`、`NotePublishReq.spuIds`、`RecommendSpuVO.spuId`、对外路径变量）。
- 命名与实体对齐（`product_name` → `spu_name`），消除 SPU 实体化后的词汇遗留；
- 比价是纯旁路（读时增强 + fail-open），**零资损面**：不写入订单、不改变金额与状态机。

**负面 / 成本**

- **BREAKING CHANGE（线格式）**：`OrderItemVO.sku_id` 由数字变 32-hex 字符串、`product_name` → `spu_name`；`SkuVO.spu_id`、`CartItemVO.sku_id`、`FavoriteVO.spu_id`、`HistoryVO.spu_id`、`ReviewVO.spu_id`、`FlashSaleVO.spu_id`/`sku_id`、`FlashBuyVO.spu_id`、`SpuSearchVO.spu_id` 等同步改型 —— 前端**必须**同步（B3）。
- 数据库结构变更：`order_item` 三处改动（含一处 FK 移除）。
- 新增一处「跨域只读展示读模型」的只读耦合（`spu`/`sku` 列变更会波及端口实现）—— 缓解：SQL 限定在端口实现内，由 H2 语义测试锁定列口径（ADR-057 先例）。
- 前端消费点数量不小（实测 `product_name` 32 处 / 12 文件），且 `spu_id` 类型变化会波及类型定义与回链逻辑。

**历史数据**：**无需迁移** —— ADR-045（2026-10-05）已废除迁移机制且库无存量数据，结构变更直接改 `Initialize.sql`。

**已知残余风险（须显式登记，不得视为已解决）**

| # | 残余风险 | 触发条件 | 后果 | 收敛方向 |
| --- | --- | --- | --- | --- |
| R1 | **路径变量未收口**（🔄 **B2 后仍遗留，且新增一条并存口径**） | 路径变量 `{id}`/`{spuId}`/`{skuId}`/`{productId}` 仍为技术键（`GET /api/item/product/{id}`、`DELETE /api/cart/item/{skuId}`、`GET /api/item/review/product/{spuId}` 等） | 「同一实体在路径与报文中两种形态」—— 客户端拿到 `CartItemVO.sku_id`（业务键）后**无法**用它调 `DELETE /api/cart/item/{skuId}`（该路径收技术键）；`SkuVO.spu_id` 同理无法直接用于 `GET /api/item/product/{id}` | ADR-058 §1 的**字面范围**只约束 VO/DTO 字段，未含路径变量，故本 ADR 不单方面扩大；**已登记 §十二 12.2 待裁决**：建议后续批次把「标识 SPU/SKU 实体」的路径/查询变量一并收口为业务键（影响面须先测算并随 BREAKING 批次发布）。<br>**B2 实施补注**：① C4/C5/C10（`/api/cart/item/{skuId}` 系列）**已随本批按业务键收口**（与 `CartItemVO.sku_id` 必须同型同义）；② **`RecommendSpuVO.spuId` 明确保持技术键**（`Long`）—— 其寻址口径与商品详情路径变量 `GET /api/item/product/{id}` **一致**（P27 相似商品卡回链走同一路径），故**不随本批收口**，属**有意的口径并存**而非遗漏；③ 其余路径变量（`{id}` / `{productId}`）与 `ReviewVO` / `ReviewCreateReq` / `NoteDetailVO.spus[]` / `NotePublishReq.spuIds` 的标识类型**仍未收口**（见 R7 / R8）。 |
| R2 | **`/internal/**` 内部契约收口**（✅ **B2 已收口**） | 已收口：`SpuVectorHitCO.spuId` / `SpuVectorUpsertCO.spuId` **由 `Long` 改型为 `String` 业务键**（32-hex）；同批一并收口 `SpuVectorClient.similar(spu_id)`、`SpuVectorUpsertReq.spuId`、`SpuVectorDoc.spuId`、`SpuSearchGateway.findVectorById`、`InternalSearchController` 的 `spu_id` 查询参数，以及 `SkuCO.spuId`（→ `String`）与 `ItemSearchClient.detail(id)`（`Long` → `String`）。**唯一保留技术键的是 ES 文档 `_id` / `id`** —— 与 reindex、I28 商品命中 `id` **同源**（`spuId` 列另写业务键） | 内部 DTO 亦属 ADR-058 §1「任何 VO/DTO」的字面范围；不一致会在「trade 传给 I22 的 `spu_ids`」处显形（见 R3） | **已消解**（本 ADR §决策 2 落码）。**未采纳** B1.5 的「先增字段」过渡建议 —— 改为直接改型（库无存量数据 ADR-045，且该批已是一次性 BREAKING）。**顺带修出两处实现错**：① `SpuRecommendManager` 曾以**技术键**作 I47 幂等键（业务键收口后即跨服务语义错），现改 `spu.getSpuId()`，并加「缺业务键 → 跳过摄取 + WARN」守卫；② search-service 测试夹具的业务键常量只有 31 位 hex（非法线格式），已改严格 32 位。**仍遗留**：`KgEdgeUpsertCO.srcId`（`Long`，不在本批门禁命中面）、I13/I14/I15 入参形态（见 R8） |
| R3 | **券适用范围取值语义与 ADR-058 冲突**（✅ **B2 已修复**） | 契约 §4.10.1 记 `scope_type=3` 的 `scope_values` 装「spu 业务标识」；实测 `PricingService.buildSpuIds` 取 `SkuCO.spuId`（`Long` 技术键）拼接后经 I22 传入，`coupon.scope_values` 存的实为 `spu.id` 技术键字符串 | 客户端按 `scope_values` 与商品业务键做预过滤**永不命中**（与 ADR-058 记录的 join 缺陷同型）；服务端 `CouponScope.accepts` 两侧同为技术键，**校验本身仍正确**（故非资损，属功能失效） | **已随 R2 收口统一为业务键而消解**。**B2 实施事实**：`PricingService.buildSpuIds` 现取 `CartItemSnapshot.spuId`（**业务键 32-hex**，原取 `SkuCO.spuId` 技术键）拼接 CSV，与线格式 `spu_id`、与客户端 `scope_values` 预过滤**同型同义** —— **客户端券范围预过滤恢复可用**（原为技术键串，**永不命中**）。服务端 `CouponScope.accepts` 仍做同型字符串比较，**校验语义不变**（故该缺陷自始非资损）。 |
| R4 | `merchant_reply_template.content` 的 `{product_name}` 占位符 | ✅ **B2 已顺手改**（该功能端点 CH6-CH8 `/api/seller/templates` **仍未实现**） | 模板变量名残留旧词 | **已落地**：`Initialize.sql` 注释改为 `变量: {buyer_name},{spu_name},{order_no}`（属 SQL 注释，端点未实现故**不构成线上契约变更**） |
| R5 | 前端消费点尚未同步 | B3 未执行前 | 前端按旧键名/旧类型消费 → 渲染空白或字符串拼接异常 | B3 实施清单已随本批次交付（含空态/降级态要求） |
| R6 | **SPU 主图线上名出现两种方言** | ✅ **B2 已落定：统一为 `main_image`**（契约与后端主代码零 `spu_image`、零 `product_image` 线上名） | 同一概念两种线上名 —— 与 ADR-058 §2「同型同义」精神不符 | **已消解**，依据见 §决策 5「为何是 `main_image`」表；本批实际改动仅 `FlashSaleVO` 一处（回退），其余 VO 本已一致 |
| R7 | **ADR-061 类型守卫存在实现缺陷 —— 扫描路径 glob 不匹配，实际未生效**（🆕 2026-10-17 B2.5 实测发现，非 B2 引入；✅ **B2.5 已修复**） | ① `Implementation/back-end/scripts/check-id-invariants.sh` 检查 4 的命中面写为 `*/src/main/java/*/adapter/response` 等 glob，而实际目录深度为 `<service>/src/main/java/org/icedamericanomall/<svc>/adapter/response`（多一层包路径）；bash glob 的 `*` **不跨 `/`** 且脚本未开 `globstar` → 该 glob **零匹配**，检查 4 对 adapter 面**从未真正扫描**。② 理论命中面内确实仍有 **9 处**标识类型违规未被捕获（修正案正则实测，2026-10-17）：`ReviewVO.spuId` / `ReviewVO.skuId`（`Long`）、`ReviewCreateReq.spuId` / `ReviewCreateReq.skuId`（`Long`）、`NoteDetailVO.spus[].spuId`（`Long`）、`RecommendSpuVO.spuId`（`Long`，见 R1）、`NotePublishReq.spuIds`（`List<Long>`）、**`ShopDecorationVO.hotSpuIds` / `ShopDecorationReq.hotSpuIds`（`List<Long>`，`merchant-service`）**。`ia-api/src/main/java/org/icedamericanomall/dto` 这一项 glob **正好命中**，故 B2 的变异验证（`SpuVectorHitCO.spuId` 改回 `Long` → 红）**成立**，但那是**唯一**实际生效的扫描面 | 门禁给**假绿**：`[OK]` 只证明 `ia-api` dto 面合规，**不能**推广为「全量线格式标识类型已收口」；据此宣称「已收口」即为**失实** | **已消解（B2.5，2026-10-17）**。修法 = 命中面改 **`find` 遍历**（对路径深度不敏感）：`find . -type d -path '*/src/main/java/*/adapter/{response,request}'` + `find ia-api/…/dto -type d`；并加两条**自证**防再犯 —— ① `SCANNED_DIRS == 0` 直接 `[FAIL]`（不再「零匹配却报 OK」）；② 白名单条目**陈旧即失败**。修复后实测命中 **26 个目录**（13 response + 12 request + 1 dto；`[OK]` 行会打印该数字）。上表 9 处的处置：**6 处收口为 `String` 业务键**（`ReviewVO.spuId/skuId`、`ReviewCreateReq.spuId/skuId`、`NoteDetailVO.spus[].spuId`、`NotePublishReq.spuIds`），**3 处登记白名单保留技术键**（`RecommendSpuVO.spuId`、`ShopDecorationVO/Req.hotSpuIds`，理由见下）。**零 DB 改动**（A 案：库内仍技术键，业务键由应用层解析/回填）。变异验证**改在 adapter 面**（`user-service` `FavoriteVO.spuId` 改回 `Long` → 红；恢复 → 绿），用以证明修复后的扫描面确有捕获力 —— B2 的 `ia-api/dto` 变异**不能**证明这一点 |
| R8 | **I13/I14/I15 等内部入参形态与部分 adapter 标识类型仍未收口**（🔄 **B2.5 部分收口，仍遗留**） | `KgEdgeUpsertCO.srcId`（`Long`，不在本批门禁命中面）；**✅ B2.5 已收口**：`ReviewVO` / `ReviewCreateReq` / `NoteDetailVO.spus[]` / `NotePublishReq` 的标识类型（→ `String` 32-hex）；**🔄 仍保留**：`ShopDecorationVO`·`ShopDecorationReq.hotSpuIds`（`List<Long>`，**白名单 + 理由**见 §「B2.5 补注」）、`RecommendSpuVO.spuId`（`Long`，见 R1）；对外路径变量（见 R1）；**契约-实现漂移**（🆕 2026-10-17 实测）：契约 P35 记「卡内 `spu_id` 为 SPU 业务键 32-hex」且卡类型写作 `SpuCardVO`，而**代码中不存在 `SpuCardVO`** —— `PairingCardVO.items` 的实际类型是 `RecommendSpuVO`，故 P35 卡内 `spu_id` **实测仍为技术键 `Long`**（与 P27 同因，见 R1），**契约该句失实** | 调用方在「业务键已得、仍需技术键入参」处口径不一致；P35 前端若按契约把 `spu_id` 当 32-hex 消费则**回链失败** | **已随 R7 的 glob 修复处理 4 项**（收口为 `String`）；**未收口的 3 项已登记白名单**（不得据 `[OK]` 外推）；对外路径变量随 R1 待裁决；P35 契约表述须由契约 Owner 订正（本 ADR 只登记，不改契约域正文） |

## 守卫（防复发）

| 守卫 | 断言 |
| --- | --- |
| **线格式标识类型断言（本 ADR 新增，闭合 ADR-059 漏洞）** ✅ **B2.5 已修复 glob（见 R7）** | 扫描 `*/src/main/java/**/adapter/response/**`、`*/adapter/request/**` 与 `ia-api/src/main/java/**/dto/**`：**字段名**对应 `spu_id`/`sku_id`（`spuId`/`skuId`/`spuIds`/`skuIds`，含 `hotSpuIds` 等前缀形态）者，其 Java 类型**必须**是 `String`（`List<String>`）；出现 `Long`/`Integer`/基本类型即失败 —— **命名之外的第二个维度**，正是本条收口的既有违规（`SkuVO.spuId: Long`）的拦截点。已落为 `check-id-invariants.sh` 的「检查 4」（CI 既有调用点 `.github/workflows/ci.yml` → `ID Invariants Gate`）。**B2.5 修正**：命中面由 bash glob（零匹配，见 R7）改为 **`find` 遍历**，并加 ① 命中目录数为 0 即失败（自证，防假绿）② 白名单条目陈旧即失败（防静默豁免）；修复后实测命中 **26 个目录**（13 + 12 + 1），`[OK]` 行打印该数字。**白名单**（有意保留技术键，共 3 条，逐条附理由）= `RecommendSpuVO.spuId`、`ShopDecorationVO.hotSpuIds`、`ShopDecorationReq.hotSpuIds`（依据见 §决策 8.2 #7–#9 与 §后果 R1） |
| 业务键编解码唯一出口 | 业务标识编解码不得绕开 `ia-common` `IdGenerator`（`check-id-invariants.sh` 检查 1/2/3 已覆盖：禁散落 UUID、禁对 BINARY(16) 列传字符串字面量） |
| **订单项快照不回查实时表** | 订单读取路径（`OrderManager` 详情/列表装配）**不得**调用任何实时商品查询来「补齐/刷新」快照列；比价读模型只允许**额外**产出比对态，禁止改写快照字段 |
| 比价 fail-open | 端口实现异常 → 空 `Map`（不得抛）；装配后明细条数**必须**与无读模型时逐条相等（「不缩行」断言）。建议以 H2 语义测试 + 端口异常注入各一条用例锁定 |
| 变异验证 | 上线前做三次变异：① 端口实现改为「查不到即抛异常」；② 去掉 `stockType` 判定（让不限量 SKU 报缺货）；③ 把 `sku.price` 比对反向（同价报变动）—— 三次均必须被上表用例捕获（挂测） |
| 契约同步 | 本 ADR 的线格式口径由 `contract/05-API-Specification.md` §1.8「SPU/SKU 标识口径」承载；改契约必须与 ADR-061 同批 |

## 日期

2026-10-16

## 参考

- 代码与 DDL（实测依据）：
  - **决策时点（2026-10-16，已过期，保留以存证）**：`Initialize.sql` L247-279（`spu`）、L285-308（`sku`）、L352-371（`order_item`）、L841（`merchant_reply_template`）；`OrderItemVO`（无 `spuId`）、`SkuVO.spuId: Long`、`SkuCO.spuId: Long` + `productName`、`PricingService#buildSpuIds`（技术键拼接）
  - **实施后实测（2026-10-17）**：`Initialize.sql` L352-372（`order_item` 已是 `sku_id`/`spu_id BINARY(16)` + `spu_name`）、L842（模板占位符已改 `{spu_name}`）；`OrderItemVO.skuId/spuId/spuName` 均为 `String`；`SkuVO.spuId/skuId: String`；`SkuCO.spuId: String` + `spuName`；`PricingService#buildSpuIds` 取业务键
  - `item-service/.../app/SkuManager.java#enrichSpuDisplay`（ADR-057 读模型落地先例）
- ADR-002（双主键 + 订单快照模式）、ADR-020（SKU 库存原子 SQL）、ADR-045（库无存量数据、`Initialize.sql` 唯一结构真相源）、ADR-050（业务标识 `BINARY(16)`+UUIDv7 + `IdGenerator` 唯一出口）、**ADR-057**（展示字段的应用层读模型端口 + 批量 + fail-open）、**ADR-058**（VO 标识字段语义唯一性；§3 读模型须带出业务标识）、**ADR-059**（SPU/SKU 词汇消歧；§2 线格式一律业务标识 —— 本 ADR 补其缺失的类型维度）
- 契约：`contract/05-API-Specification.md` §1.8（标识口径）、§4.6.1（订单项不变式）、§4.10.1（券适用范围）、§4.14.10（I47/I48）、§十二 12.2（挂账）
- 实施证据（B2，2026-10-17，提交 `2325031d`）：
  - `Implementation/back-end/docs/b2-delivery-status.md`（批次自证材料：全量 1913/0/0/1、端点对账 287/0/43、ID 门禁、变异验证）
  - `Implementation/back-end/database/Initialize.sql` L352-372（`order_item` 落码后形状）、L842（模板占位符已改 `{spu_name}`）
  - `trade-service` `domain/model/order/{SkuCurrentState,SpuCurrentState,PageResult}.java`、`app/port/OrderItemCurrentStateQuery.java`、`app/order/OrderCurrentStateEnricher.java`、`domain/service/{OrderItemPriceComparator,StockAvailabilityPolicy}.java`、`infrastructure/persistence/order/OrderItemCurrentStateQueryImpl.java`
  - `item-service` `adapter/internal/InternalItemSearchController.java#detail`（双形态入参，路由不变）
  - `architecture-tests/archunit_store/33c4c196-5fdf-4aab-920e-05e5c6c112b0`（24 条；`git show 2325031d` 删 1 条）
