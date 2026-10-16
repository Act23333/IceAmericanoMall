# 批次 B2 交付状态（java-architect）

> **状态：已完成并全绿可验证。** 本文件是自证材料 —— 每条结论都对应下方可复现的命令与原始输出。
> 上一轮（B2 90%）的遗留项已全部收口；本文件「§1 九红逐条处置」是上一轮失败清单的**结案**。

## 0. 一句话结论

`mvn -f Implementation/back-end/pom.xml clean test` → **BUILD SUCCESS，1913 用例 / 0 失败 / 0 错误 / 1 skipped**
（≥1886 门槛满足）；`Unmapped target properties` = **0**；端点对账 **287/0/43 不变**；ID 门禁 **`[OK]`**；
门禁变异验证**已做且能捕获**。`project-docs/**` 未改动（按批次边界），ADR-061 状态更新要点见交付报告。

## 1. 上一轮 9 个红测试 —— 逐条处置（全部判定为**测试错**，实现无误）

| # | 用例 | 根因判定 | 依据 | 改法 |
| :-- | :-- | :-- | :-- | :-- |
| 1–7 | `OrderCreationManagerDisplayCurrencyTest`（7 个用例全红） | **测试错**（夹具字段冲突 + 陈旧桩 + 陈旧 DDL） | 见下 | 见下 |
| 8–9 | `OrderCreationManagerGroupBuyTest#shouldCreateGroupBuyOrder_whenValidOpenTeam` / `shouldPassTeamNoThrough_whenJoinExistingTeam` | **测试错**（请求侧业务键 ≠ I13 mock 回执业务键） | `BizException: SKU不存在: 0000…03e8` 来自 `NormalCartOrderStrategy:70` | 见下 |

### 1.1 根因 A：测试夹具里 `skuId` 被**写了两次**，后者覆盖前者（**测试错**）

```java
// 旧（错）：第一行是"本行业务键"，第二行把它覆盖成一个无关常量
s.setSkuId(businessKey(id));
s.setSkuId(SKU_BUSINESS_ID);        // ← 覆盖：所有 SKU 回执都变成同一个常量业务键
```

装配层（`NormalCartOrderStrategy:58–70`、`GroupBuyOrderStrategy:114`）按 **`SkuCO.skuId` 业务键**
与请求侧（购物车行 / `ctx.skuId` / I52 回执 `GroupBuyValidateResultCO.skuId`）做 join，
而 mock 回执恒为常量 → **join 必然落空** → `SKU不存在`。

**依据（为何判"测试错"而非"实现错"）**：实现侧 join 用的是**回执自带的业务键**（`sku.getSkuId()`），
而请求侧业务键来自 `cartItem.skuId` / I13 契约，两者在真实链路中**同源同值**（I13 按 `sku_ids` 业务键查询、
原样回带）。是测试夹具人为制造了两个不同值，属夹具缺陷 —— 且该模式在**同仓同批**另有反例：
`OrderCreationManagerTest` 的 `sku()` 同样有两行赋值，但两行**同值**（`businessKey(id)`），故它当时是绿的。

**改法**：删除覆盖行，回执 `skuId` = `businessKey(technicalId)`（`%032x`），与请求侧逐位同值。
`OrderCreationManagerGroupBuyTest` 反之 —— 请求侧是 I52 常量，故回执改为该常量。

### 1.2 根因 B：I43 运费桩的 `totalAmount` 与实现入参不符（**测试错**）

`FreightPostageResolver.resolve(sellerId, snapshots, totalAmount)` 的第三方参是
**商品总额 = Σ(price×qty)**，不是单品价、也不是常量。旧桩写成 `eq(10000)`（单品价×1 的误推）
或 `eq(1000)`（拼团价误取 SKU 原价）、甚至把第二个形参写成 `List.of(SKU_BUSINESS_ID)`
（类型都不同）→ mock 回落到返回 `null` → `Integer` 拆箱 **NPE** → 被编排层 catch 包装成
`BizException: 订单创建失败`（`OrderCreationManager:166`）。

**改法**：桩按真实口径对齐 —— NORMAL 购物车（5000×2）→ `eq(10000)`；拼团（拼团价 1000×1）→ `eq(1000)`；
明细形参一律 `anyList()`（其内容不是本用例断言点，价格与金额已由 `totalAmount` 与落库断言锁定）。

### 1.3 根因 C：两个测试类的 H2 `order_item` DDL **仍是 ADR-061 前的形状**（**测试错**）

```
旧：sku_id BIGINT NOT NULL, product_name VARCHAR(100)                       ← 缺 spu_id、sku_id 类型错
新：sku_id BINARY(16) NOT NULL, spu_id BINARY(16) NOT NULL, spu_name VARCHAR(100)
```

ADR-061 后 `OrderItemPO.skuId/spuId` 是 `byte[]`，插进 `BIGINT` 列 → 序列化/类型异常 →
同样被包装成 `订单创建失败`。**这是同一文件的 `OrderCreationManagerTest` 已修、而这两个类漏修的遗漏**
（其 DDL 与可用的 `OrderCreationManagerTest` 逐列对齐即为证据）。

### 1.4 修复后的强断言（**没有放松断言强度**）

- `OrderCreationManagerDisplayCurrencyTest`：7/7 绿，金额不变式 V2 逐字断言保留
  （`total/discount/postFee/payAmount` **逐值**相等），快照两列与 VO 回显断言保留；
- `OrderCreationManagerGroupBuyTest`：5/5 绿，`ArgumentCaptor` 断言**扣库存用的就是 I52 下发的业务键**
  （`assertEquals(SKU_BUSINESS_ID, stockCaptor.getValue().get(0).getSkuId())`）保留；
- 未出现 `assertNotNull` 顶替、未手工填字段再断言。

## 2. 两处搜索服务违规（ADR-061 §后果 R2）—— **已级联修复，未停手**

`check-id-invariants.sh` 检查 4 报出的 2 处（`SpuVectorHitCO.spuId` / `SpuVectorUpsertCO.spuId` 仍是 `Long`）
已按业务键收口，级联面**全部落在本批写权内**（`ia-api` / `search-service` / `item-service`），故按任务要求做实：

| 层 | 载体 | 改前 → 改后 |
| :-- | :-- | :-- |
| ia-api | `SpuVectorUpsertCO.spuId` | `@NotNull Long` → `@NotBlank String`（幂等键语义不变） |
| ia-api | `SpuVectorHitCO.spuId` | `Long` → `String`（I48 命中出**业务键**） |
| ia-api | `SpuVectorClient.similar(spu_id)` | `Long` → `String`；`SpuVectorClientFallback` 同步 |
| ia-api | `ItemSearchClient.detail(id)` | `Long` → `String`（**同一路由**，入参兼容技术键与业务键） |
| search | `SpuVectorUpsertReq.spuId` | `@NotNull Long` → `@NotBlank String` |
| search | `SpuVectorDoc.spuId` | `Long` → `String` |
| search | `SpuSearchGateway.findVectorById` | `Long` → `String`（+ `ItemServiceSpuSearchGateway` 降级实现） |
| search | `SpuVectorManager.similar` | 锚点改业务键；命中排除按 `hit.spuId()`（业务键）比对 |
| search | `InternalSearchController.similarSpuVector` | `@RequestParam spu_id` `Long` → `String` |
| search | `EsSpuDocuments.toDocument(doc, id)` | `_id`/`id` 仍是**技术键**（与 reindex、I28 命中 id 同源）；`spuId` 列写业务键 |
| search | `EsSpuSearchGateway.upsertVectorDoc` / `findVectorById` | 先经 item 域出口把业务键解析为技术键，**顺带取回 publishTime**（原 `applyPublishTimeQuietly` 合并进同一次往返，净**减少**一次跨服务调用） |
| item | `SpuRecommendManager.buildUpsertCO` | `co.setSpuId(spu.getId())` → `spu.getSpuId()`（**实现错**：摄取幂等键曾用技术键） |
| item | `SpuManager.similarSpus` | I48 业务键 → 一次批量 SQL 解析技术键（`SpuRepository.idMapByBusinessIds`）；锚点技术键 → 业务键经 `findSearchCardById` |
| item | `SpuMapper.selectSearchCardByBusinessId` / `selectIdByBusinessIds`（新增，走 `spu_id` 唯一索引） | 同库内部**仍是技术键**（A 案） |
| item | `InternalItemSearchController.detail` | 路径变量 `Long` → `String`，**路由不变**（见 §4 「不新增端点」） |

**未改（A 案边界）**：`cart.sku_id`、`favorite/review/spu_view_log/note_spu/flash_sale/group_buy_activity .spu_id`
保持技术键；`RecommendSpuVO.spuId` 保持 `Long` 技术键（P27 既有寻址口径，同商品详情路径变量，
ADR-061 §后果 R1 已登记）；`NoteVectorHitCO` / `KgEdgeUpsertCO` / `SpuSearchItemCO` 不在本次门禁命中面，未动。

### 2.1 附带发现并修复的两处**实现错**（非测试问题）

1. `SpuRecommendManager` 曾用**技术键**作 I47 幂等键（业务键收口后即为跨服务语义错）；
   现改为 `spu.getSpuId()`，并新增守卫：缺业务键时**跳过摄取 + WARN**（宁不摄取，不发语义错误的消息）。
2. `search-service` 测试夹具里的业务键常量只有 **31 位 hex**（`bbbbbbbb…` + `%02d`），
   不是合法 `BINARY(16)` 线格式；已改为严格 32 位（`%032x`）。属测试夹具缺陷，已一并订正。

## 3. 全部验证命令与原始输出（本批实测，`mvn` 走 JDK 25）

> **环境事实（须登记）**：本机只有 JDK 25（`C:\java0\java.jdk\jdk-21` 是**空目录**，
> 无 `bin/java.exe`）。因此 JaCoCo 0.8.12 会打印 1410 行
> `java.io.IOException: Error while instrumenting … Unsupported class file major version 69`
> —— 这是**告警不是失败**：`prepare-agent` 已启动、用例照常跑完，退出码 0、`BUILD SUCCESS`。
> 若需干净的覆盖率报告，须在装有 JDK 21 的机器上跑（`mvn -Djacoco.skip=true` 亦可静音）。

### 命令 1：全量测试（要求形式，未加任何 skip）

```
$ mvn -f Implementation/back-end/pom.xml clean test
[INFO] BUILD SUCCESS
[INFO] Total time:  04:44 min
```

各模块计数（取自各模块 `target/surefire-reports/*.txt` 逐文件求和，19 个模块全部一致）：

```
  79  ia-common                    29  cart-service
  66  ia-integration              128  pay-service
  14  ia-api                      368  trade-service
  91  user-service               211  item-service
 137  authorization-service       33  logistics-service
  74  gateway-service            174  search-service
  52  ai-service                 273  marketing-service（1 skipped 为既有 @Disabled）
  13  notification-service        30  verification-service
  54  wallet-service              81  merchant-service
   6  architecture-tests
TOTAL Tests: 1913  Failures: 0  Errors: 0  Skipped: 1
```

（校验方式：`for m in <19 模块>; do grep -h "^Tests run:" $m/target/surefire-reports/*.txt | …`，
与日志中 `[INFO] Results:` 后的模块汇总行逐一相同。）

**用例数与基线对比**：2026-10-15 基线为 1775 → 现 **1913**（**≥1886 门槛满足**）。
可逐字归因的：`cart 23 → 29`（上一轮）；`trade 368 → 368`（本批只修夹具，**净零增减**）；
其余模块增量为 B2 两轮（含 B1 遗留补齐）自动带入，**本批未删除任何用例**。
`architecture-tests`（6）为**首次纳入全量验证**（上一轮从未跑）。

### 命令 2：`Unmapped target properties` 计数

```
$ mvn -f Implementation/back-end/pom.xml clean test 2>&1 | grep -c "Unmapped target properties"
0
```

### 命令 3：端点对账

```
$ node Implementation/back-end/scripts/extract-endpoints.mjs | grep -E 'matched|codeOnly|docOnly'
 "matched": 287,
 "codeOnly": 0,
 "docOnly": 43,
```

（**287/0/43 不变** —— 本批**未新增任何路由**，见 §4。）

### 命令 4：命名审计

```
$ node Implementation/back-end/scripts/naming-audit.mjs | tail -1
  共 6 组同名异形
```

（与上一轮同一脚本口径，输出为**统计行而非门禁**：脚本退出码 0，未构成本批新增违规；
具体 6 组是否需收敛属文档/契约侧议题，未在本批扩范围。）

### 命令 5：ID 门禁

```
$ bash Implementation/back-end/scripts/check-id-invariants.sh
[OK] ID 不变量门禁通过（ADR-050/051/061：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令 / 线格式标识类型）
EXIT=0
```

## 4. 门禁变异验证（**已做**，证明守卫不是装饰）

手段：把 `ia-api` `SpuVectorHitCO.spuId` 从 `String` **临时改回 `Long`**，跑门禁 → 必须红；再恢复 → 必须绿。

**变异后（`private Long spuId;`）—— 门禁红：**

```
$ bash Implementation/back-end/scripts/check-id-invariants.sh
[FAIL] ADR-061：以下线格式字段名对应 spu_id/sku_id，但类型不是 String（应 32-hex 业务键 String / List<String>）：
ia-api/src/main/java/org/icedamericanomall/dto/SpuVectorHitCO.java:30:    private Long spuId;   // MUTATION-TEST: 临时改回 Long（验证门禁捕获力），随后立即恢复
EXIT=1
```

**恢复后（`private String spuId;`）—— 门禁绿：**

```
$ bash Implementation/back-end/scripts/check-id-invariants.sh
[OK] ID 不变量门禁通过（ADR-050/051/061：生成唯一出口 / 查询参数 byte[] / SQL 字面量禁令 / 线格式标识类型）
EXIT=0
```

变异已恢复，仓库当前为 `String`（`grep -n "private String spuId"` 命中第 30 行）；恢复后又跑了一次
全量 `clean test` → `BUILD SUCCESS / 1913 / 0 / 0 / 1`。

### 4.1 关于「不新增端点」的说明（**中途发现的真实违规并已回退**）

为把 I47 业务键解析为技术键，最初新增了一条 `GET /internal/item/search/detail/key/{spuId}`。
**端点对账立刻报 `codeOnly: 1`**（287/1/43）—— 即未登记契约的新端点，违反「不改路径、不新增端点」。
已回退：改为**扩展既有 `GET /internal/item/search/detail/{id}` 的入参口径**（纯数字 → 技术键；
32-hex → 业务键），路由不变，`codeOnly` 回到 0。这是**门禁真实拦下一次违规**的实例。

## 5. `architecture-tests` 模块（上一轮从未跑，本批首次纳入全量）

**初始状态：红**（1 个失败，22 次违规）。其中 21 次是上一轮新增的**真实新增违规**：

```
ArchitectureTest.domainDependsOnNothingOutward:
  domain.service.OrderItemPriceComparator 依赖 app.port.OrderItemCurrentStateQuery.SkuCurrentState / SpuCurrentState（20 次）
  domain.repository.OrderRepository.pageOrders 返回 IPage（签名被上一轮加参改变，基线条目失配 → 计为新增）
```

**处置（均为实现修正，非基线放水）**：

1. `SkuCurrentState` / `SpuCurrentState` 由 `app.port` 的嵌套 record **上移为领域层值对象**
   （`trade.domain.model.order.SkuCurrentState` / `SpuCurrentState`）。方向变为
   `app/infra → domain`（合法），`app.port.OrderItemCurrentStateQuery` 只引用领域类型 —— **21 次违规归零**。
2. `OrderRepository.pageOrders` 返回类型由 MyBatis-Plus `IPage<Order>` 改为**框架无关**
   `trade.domain.model.order.PageResult<Order>`（新增，与 item 域 `PageResult` 同形）；持久化实现内完成
   `IPage → PageResult` 转换，应用层 `OrderQueryManager` 再转回适配层既有的 `IPage<OrderVO>` 信封
   （Controller 签名与 HTTP 报文**零变化**）。一并删除 `OrderRepository` 的 `IPage` import。

净效果：**基线条目 25 → 24（只减不增），且不新增任何冻结项**。

> **登记**：本批曾把 `OrderRepository.pageOrders` 的**旧签名**基线条目留在 `archunit_store` 中，
> 因签名改变（上一轮加 `keyword/startDate/endDate`）该条目已失配；现按「消除该违规」处理，
> 基线文件删除该行属**有意治理动作**（ADR 方向明确为「域层 PageResult 抽象」），非静默冻结。

## 6. 本批改动文件清单（全部在 `Implementation/back-end/**`）

**生产代码**

- `ia-api`：`SpuVectorUpsertCO`、`SpuVectorHitCO`、`SpuVectorClient`、`SpuVectorClientFallback`、
  `ItemSearchClient`、`ItemSearchClientFallback`
- `trade-service`：`domain/model/order/SkuCurrentState`(新)、`domain/model/order/SpuCurrentState`(新)、
  `domain/model/order/PageResult`(新)、`app/port/OrderItemCurrentStateQuery`、
  `domain/service/OrderItemPriceComparator`、`app/order/OrderCurrentStateEnricher`、
  `domain/repository/OrderRepository`、`infrastructure/persistence/order/OrderRepositoryImpl`、
  `infrastructure/persistence/order/OrderItemCurrentStateQueryImpl`、`app/order/OrderQueryManager`
- `item-service`：`app/SpuRecommendManager`、`app/SpuManager`、`app/search/ItemSearchQueryManager`、
  `adapter/internal/InternalItemSearchController`、`domain/repository/SpuRepository`、
  `infrastructure/persistence/SpuMapper`、`infrastructure/persistence/SpuRepositoryImpl`、
  `infrastructure/persistence/SpuBusinessKeyRow`(新)
- `search-service`：`app/SpuVectorManager`、`adapter/request/SpuVectorUpsertReq`、
  `adapter/internal/InternalSearchController`、`domain/gateway/SpuSearchGateway`、
  `domain/model/search/SpuVectorDoc`、`infrastructure/es/EsSpuSearchGateway`、
  `infrastructure/es/SpuEsDocuments`、`infrastructure/adapter/ItemServiceSpuSearchGateway`

**测试代码**：`trade`（3 个下单集成测试的夹具/DDL/桩）、`item`（`SpuManagerTest`、`SpuRecommendManagerTest`、
`SpuSearchMapperH2Test`、`ItemSearchQueryManagerTest`）、`user`（`FavoriteSpuBriefQueryImplH2Test`
DDL + 业务键用例）、`search`（`SpuVectorManagerTest`、`InternalSearchControllerTest`、
`EsSpuSearchGatewayTest`、`ItemServiceSpuSearchGatewayTest`）

**门禁基线**：`architecture-tests/archunit_store/…`（25 → 24 条，删除失配的 `OrderRepository.pageOrders` 旧签名条目）

**未改**：`project-docs/**`（含 `adr/061`）、前端、`target/**`、任何路径/路由。

## 7. 仍遗留 / 未做（如实登记，**不得视为已成立**）

1. **未做 H2 之外的集成验证**：ES 真索引、Feign 真链路、MySQL 真库**均未运行**（无基础设施）。
   本批对 ES 行为的结论仅来自 mock 单测（`EsSpuSearchGatewayTest`）与代码走查。
2. **未跑端到端**：无 `@SpringBootTest` / Testcontainers 用例，跨服务真实契约（I13/I47/I48）未端到端跑通。
3. **`RecommendSpuVO.spuId` 仍是技术键**（A 案 + 契约 §R1 保留）；前端拿到 I48 卡后
   走商品详情路径变量仍是技术键口径 —— 两条口径并存**是当前事实**，需契约侧确认。
4. **`naming-audit.mjs` 的「6 组同名异形」未逐组排查**（脚本非门禁，非本批范围）。
5. **JaCoCo 覆盖率报告在本机不可用**（JDK 25 vs JaCoCo 0.8.12，见 §3 环境事实）；
   退出码与用例结果不受影响，但**覆盖率数字未测**。
6. **`OrderItemVO` 无任何比价字段**（按契约 §十二 12.2「形态未定、前端不得对接」刻意不出栈）；
   读模型与领域服务能力已就绪（`OrderQueryManager.compareStates`），契约定稿后接线即用。
7. **`StockAvailabilityPolicy` 与 `StockTypeEnum.requiresStockCheck` 两口径并存**（本批有意保留，见 ADR 要点）。

## 8. 给主 agent 的文档更新要点（`project-docs/**` 不在本批写权内，未改）

见交付报告「给主 agent 的文档更新要点」一节：ADR-061 状态行、§十二 挂账条目、契约 §4.14.10 的
标识类型口径（I47/I48 入参出参由技术键改为业务键 `String`）。
