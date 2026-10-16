# ADR-061: 订单项 = 业务键标识 + 快照 —— 线格式标识收口与比价/失效读时增强

## 状态

已决定（2026-10-16，批次 B1.5）。**契约与决策先行，代码由 B2/B3 实施**：本 ADR 落定后，`order_item` 两列与线格式类型收口、比价读模型端口均**尚未落码**（当前代码仍为 `order_item.sku_id BIGINT` 技术外键、`SkuVO.spuId: Long`、`OrderItemVO` 无 `spuId`）；落码完成后本行改为「已实施」。

**本 ADR 一并收口 ADR-059 §2 的既有违规**：`SkuVO.spuId` 至今仍是 `Long` 技术外键（ADR-059 §背景已自认「守卫只查命名、未查类型，故漏放」），本 ADR 补上类型维度。

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

**端口签名建议**（trade-service，B2 落地以代码为准）：

```java
// app/port/OrderItemCurrentStateQuery.java
Map<String, SkuCurrentState> findSkuStates(Collection<String> skuBusinessIds);   // key = sku 业务键
Map<String, SpuCurrentState> findSpuStates(Collection<String> spuBusinessIds);   // key = spu 业务键

record SkuCurrentState(String skuId, String spuId, Integer price, Integer stock,
                       Integer stockType, Integer status) {}   // 行不存在 = 该键不出现

record SpuCurrentState(String spuId, Integer status, Integer minPrice) {}  // minPrice = 在售 SKU 最低价
```

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

### 4. DB 变更范围限定：只改 `order_item`

| 表 | 处理 |
| --- | --- |
| `order_item` | **改**：`sku_id` 改型 `BINARY(16)`、新增 `spu_id BINARY(16)`、`product_name` → `spu_name` |
| 其余全部表（`sku.spu_id`、`review.spu_id`、`favorite.spu_id`、`spu_view_log.spu_id`、`note_spu.spu_id`、`flash_sale.spu_id`、`group_buy_activity.spu_id`、`shop_decoration.hot_spu_ids` 等） | **不动**，内部外键保持技术键 `BIGINT` |

理由：**库内 join 用技术键是行业标准**（同库同分片下性能最优、外键完整性可保），而**这些内部外键不出线格式**（ADR-058 §1）；订单项之所以例外，是因为它承载「三个诉求」且**必须自持商品源头的业务键**（历史订单不能依赖 `sku` 行仍然存在）。

### 5. 命名收口：`product_name` → `spu_name`、`product_image` → `spu_image`

SPU 实体化后其名字列/线上名统一为 `spu_name`（列名与 JSON 键名同改）；SPU 主图线上名统一为 `spu_image`。

- DB：`order_item.product_name` → `order_item.spu_name`（唯一列定义，`merchant_reply_template.content` 的模板占位符 `{product_name}` 见 §后果 R4）。
- 线格式：契约 14 处 `product_name` 与 2 处 `product_image` 全量改为 `spu_name` / `spu_image`（**逐处核对语义均为 SPU 名称/主图**，无一处是 SKU 名或订单项自身名）。
- 保留项：`sku_spec`（规格）、`image`（订单项图片快照）不动。

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
- 线格式标识首次**同型同义**（全部 32-hex `String`），跨接口本地 join 在类型层可成立；
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
| R1 | **路径变量未收口** | 路径变量 `{id}`/`{spuId}`/`{skuId}`/`{productId}` 仍为技术键（`GET /api/item/product/{id}`、`DELETE /api/cart/item/{skuId}`、`GET /api/item/review/product/{spuId}` 等） | 「同一实体在路径与报文中两种形态」—— 客户端拿到 `CartItemVO.sku_id`（业务键）后**无法**用它调 `DELETE /api/cart/item/{skuId}`（该路径收技术键）；`SkuVO.spu_id` 同理无法直接用于 `GET /api/item/product/{id}` | ADR-058 §1 的**字面范围**只约束 VO/DTO 字段，未含路径变量，故本 ADR 不单方面扩大；**已登记 §十二 12.2 待裁决**：建议后续批次把「标识 SPU/SKU 实体」的路径/查询变量一并收口为业务键（影响面须先测算并随 BREAKING 批次发布） |
| R2 | **`/internal/**` 内部契约的收口未定** | `SkuCO.spuId`（`Long` 技术外键）、`SpuVectorHitCO.spuId`（`Long`）、`KgEdgeUpsertCO.srcId`（`Long`）、I13/I14/I15 的 `sku_ids`/`sku_id` 入参仍为技术键 | 内部 DTO 亦属 ADR-058 §1「任何 VO/DTO」的字面范围；不一致会在「trade 传给 I22 的 `spu_ids`」处显形（见 R3） | 已登记 §十二 12.2 待裁决（附 B2 建议：按 ADR-058 §3 先「**增**业务键字段」，改型与否由 java-architect 裁决） |
| R3 | **券适用范围取值语义与 ADR-058 冲突** | 契约 §4.10.1 记 `scope_type=3` 的 `scope_values` 装「spu 业务标识」；实测 `PricingService.buildSpuIds` 取 `SkuCO.spuId`（`Long` 技术键）拼接后经 I22 传入，`coupon.scope_values` 存的实为 `spu.id` 技术键字符串 | 客户端按 `scope_values` 与商品业务键做预过滤**永不命中**（与 ADR-058 记录的 join 缺陷同型）；服务端 `CouponScope.accepts` 两侧同为技术键，**校验本身仍正确**（故非资损，属功能失效） | 随 R2 收口一并统一为业务键后自动消解；在收敛前**不得**声称该字段可用于客户端预过滤（契约已改记实测口径 + 待裁决标注） |
| R4 | `merchant_reply_template.content` 的 `{product_name}` 占位符 | 该功能端点（CH6-CH8 `/api/seller/templates`）**未实现** | 模板变量名残留旧词 | 不为未实现功能预先定名；随该功能实现时一并改名 `{spu_name}`（B2 可顺手改，属 `Initialize.sql` 注释） |
| R5 | 前端消费点尚未同步 | B3 未执行前 | 前端按旧键名/旧类型消费 → 渲染空白或字符串拼接异常 | B3 实施清单已随本批次交付（含空态/降级态要求） |
| R6 | **SPU 主图线上名出现两种方言** | 本 ADR §决策 5 把 `product_image` 收口为 `spu_image`，而其余 VO（`SpuVO`/`FavoriteVO`/`HistoryVO`/`SpuSearchVO`/`RecommendSpuVO`/`NoteDetailVO.spus[]`）早已用 `main_image`（与 DB 列 `spu.main_image` 同名） | 同一概念两种线上名 —— 与 ADR-058 §2「同型同义」精神不符 | 已登记 §十二 12.2 待裁决 R4：**建议统一为 `main_image`**（与 DB 列对齐，仅需回退本 VO 一处）；若统一为 `spu_image` 则须连同 DB 列名一起演进 |

## 守卫（防复发）

| 守卫 | 断言 |
| --- | --- |
| **线格式标识类型断言（本 ADR 新增，闭合 ADR-059 漏洞）** | 扫描 `*/src/main/java/**/adapter/response/**`、`*/adapter/request/**` 与 `ia-api/src/main/java/**/dto/**`：**字段名**对应 `spu_id`/`sku_id`（`spuId`/`skuId`/`spuIds`/`skuIds`…）者，其 Java 类型**必须**是 `String`（`List<String>`）；出现 `Long`/`Integer`/基本类型即失败 —— **命名之外的第二个维度**，正是本条收口的既有违规（`SkuVO.spuId: Long`）的拦截点。建议落为 `check-id-invariants.sh` 的「检查 4」（CI 既有调用点 `.github/workflows/ci.yml` → `ID Invariants Gate`） |
| 业务键编解码唯一出口 | 业务标识编解码不得绕开 `ia-common` `IdGenerator`（`check-id-invariants.sh` 检查 1/2/3 已覆盖：禁散落 UUID、禁对 BINARY(16) 列传字符串字面量） |
| **订单项快照不回查实时表** | 订单读取路径（`OrderManager` 详情/列表装配）**不得**调用任何实时商品查询来「补齐/刷新」快照列；比价读模型只允许**额外**产出比对态，禁止改写快照字段 |
| 比价 fail-open | 端口实现异常 → 空 `Map`（不得抛）；装配后明细条数**必须**与无读模型时逐条相等（「不缩行」断言）。建议以 H2 语义测试 + 端口异常注入各一条用例锁定 |
| 变异验证 | 上线前做三次变异：① 端口实现改为「查不到即抛异常」；② 去掉 `stockType` 判定（让不限量 SKU 报缺货）；③ 把 `sku.price` 比对反向（同价报变动）—— 三次均必须被上表用例捕获（挂测） |
| 契约同步 | 本 ADR 的线格式口径由 `contract/05-API-Specification.md` §1.8「SPU/SKU 标识口径」承载；改契约必须与 ADR-061 同批 |

## 日期

2026-10-16

## 参考

- 代码与 DDL（实测依据）：
  - `Implementation/back-end/database/Initialize.sql` L247-279（`spu`）、L285-308（`sku`）、L352-371（`order_item`）、L841（`merchant_reply_template`）
  - `trade-service/.../adapter/response/OrderItemVO.java`（无 `spuId`）、`item-service/.../adapter/response/SkuVO.java`（`spuId: Long`）
  - `ia-api/.../dto/SkuCO.java`（`spuId: Long` + `productName` 展示字段）、`trade-service/.../app/order/pricing/PricingService.java#buildSpuIds`（技术键拼接）
  - `item-service/.../app/SkuManager.java#enrichSpuDisplay`（ADR-057 读模型落地先例）
- ADR-002（双主键 + 订单快照模式）、ADR-020（SKU 库存原子 SQL）、ADR-045（库无存量数据、`Initialize.sql` 唯一结构真相源）、ADR-050（业务标识 `BINARY(16)`+UUIDv7 + `IdGenerator` 唯一出口）、**ADR-057**（展示字段的应用层读模型端口 + 批量 + fail-open）、**ADR-058**（VO 标识字段语义唯一性；§3 读模型须带出业务标识）、**ADR-059**（SPU/SKU 词汇消歧；§2 线格式一律业务标识 —— 本 ADR 补其缺失的类型维度）
- 契约：`contract/05-API-Specification.md` §1.8（标识口径）、§4.6.1（订单项不变式）、§4.10.1（券适用范围）、§十二 12.2（挂账）
