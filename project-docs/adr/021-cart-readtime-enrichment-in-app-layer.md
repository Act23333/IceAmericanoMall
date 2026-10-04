# ADR-021: 购物车商品信息与金额在读时由应用层编排回填（跨服务明细只落在一个地方）

## 背景

`cart` 表只存 `(user_id, sku_id, quantity, selected)`，不存商品快照 —— 商品名 / 规格 / 单价 / 图片属于 `item-service` 的商品域。修复前的现状是：

1. `CartEntity` 声明了 4 个 `@TableField(exist = false)` 的"读时回填"字段（`productName` / `productId` / `skuSpec` / `price` / `image`）并注释"Enriched at read time from item-service"，但**全仓库没有任何回填代码**：`GET /api/cart` 恒返回 `productName=null`、`price=null`、`subTotal=0`、`totalPrice=0`、`selectedPrice=0`，`allSelected` 因 `respItems` 非空恒为 `true`（即使商品已下架无法计价）。
2. `app/CartManager` 是死透传（`CartController` 直接注入 `ICartService`），应用层没有承担编排职责。
3. `ICartService.getCart` 在 service 内部顺手算钱，而它拿不到商品价格，只能算出 0 —— 职责放错了层。

对照 `InternalCartController.getSelectedItems` 的调用方 `NormalCartOrderStrategy`（trade-service）：下单侧本就**不依赖** cart 返回的商品字段，而是自己用 `SkuClient.getSkuListByIds` 重新组装快照。也就是说商品明细的权威来源是 item-service，购物车只是"承载 skuId + 数量"的容器。

## 决策

1. **读时组装归属应用层**：`ICartService.getCart(userId)` 只负责"查行 + 转响应项"（金额字段给出确定的 0/false 初值）；`CartManager.getCartView(userId)` 负责编排 —— 取 `ICartService.getCart` 结果 → 一次 `SkuClient.getSkuListByIds(skuIds)` 批量回填 `productName / spec / image / price` → 计算每项 `subTotal`（分，`price × quantity`）与 `totalPrice / selectedPrice / allSelected`。
2. **Feign 只出现在应用层**：`CartManager` 是 `cart-service` 中唯一持有 `SkuClient` 的类，与"Service → Feign 禁止"一致（对照 `AGENTS.md` 的编排层规则）。`CartController` 改为注入 `CartManager`，写操作（加购 / 改数量 / 删除 / 选中 / 清空）也统一经 `CartManager` 透传，消灭死透传。
3. **禁止 N+1**：回填只发一次批量查询（`skuIds` 去重后传入）。
4. **`allSelected` 语义收紧**：购物车非空 **且** 每项 `selected = true` **且** 每项都能取到单价，才为 `true`；取不到价格的项（SKU 下架 / 已删除）`subTotal = 0`、不计入总价、并使 `allSelected = false`。
5. **`CartConverter` 相应调整**：不再在转换器里算 `subTotal`（`@Mapping(target = "subTotal", ignore = true)`），金额只由编排层产出，避免两处口径。
6. **并发加购兜底**：`CartServiceImpl.addItem` 的 check-then-insert 撞上 `uk_user_sku` 时捕获 `DuplicateKeyException`，重读最新行后按增量合并（`setIncrBy`），不再裸 500。
7. **数量校验口径统一**：`CartUpdateReq.quantity` 去掉 `@Min(1)`，改为 `@NotNull`，放行 `quantity ≤ 0` 走"删除该行"分支（京东标准），使 REST 行为与既有 H2 测试口径一致。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 加购时把商品快照写入 `cart` 表（冗余列） | 读购物车零跨服务调用 | 价格/上下架会与商品域漂移，必须靠失效逻辑兜底；购物车数据量小但字段失真代价高 |
| 在 `ICartService` 内直接调 `SkuClient` 回填 | 改一个类即可 | 违反"Service → Feign 禁止"，且让 service 依赖技术组件 |
| 由 gateway/BFF 聚合商品信息 | 服务端更薄 | 网关不承担业务组装；前端需要多次调用或新增聚合服务 |
| **应用层 `CartManager` 编排（采纳）** | 职责分层清晰；一次批量调用；与 trade 侧"以 item-service 为准"的口径一致 | 购物车读接口强依赖 item-service 可用性（降级时仅缺商品信息与金额，不影响行数据） |

## 后果

**正面**

- `GET /api/cart` 首次真正返回商品名 / 规格 / 图片 / 单价 / 小计 / 总价 / 选中总价，前端不再需要二次拼装。
- 下架商品不再被"全选"（金额与勾选状态自洽）。
- 应用层 `CartManager` 从死代码变为真实编排点，`CartManagerTest`（Mock `SkuClient`）覆盖回填、金额、空购物车、下架项、`allSelected` 四种分支。
- 并发加购由 500 变为幂等合并。

**负面 / 风险**

- 购物车读路径新增一次 item-service 调用：`SkuClient` 降级（fail-closed）时会抛异常，购物车页不可用 —— 属于可接受的依赖，但需在监控上区分"购物车服务异常"与"商品服务异常"。
- `InternalCartController` 返回的 `CartItemCO` 中 `productName / price / spec / image` 仍为空（trade-service 自行用 `SkuClient` 组装，未受影响）；若后续有新消费方直接用这些字段，需同样走"以 item-service 为准"的口径或在内部端点补回填。
- `cart-service/pojo` 包名未按四层规范改名 `entity`（涉及 8 个文件，超出本次改动 ≤6 文件的自限），保留原包名待后续统一。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/cart-service/src/main/java/org/icedamericanomall/cart/app/CartManager.java`、`service/impl/CartServiceImpl.java`、`adapter/CartController.java`、`CartApplication.java`
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/strategy/NormalCartOrderStrategy.java`（下单侧快照口径）
- ADR-011（Feign 降级语义）、`AGENTS.md`（编排层与 Service→Feign 禁令）
