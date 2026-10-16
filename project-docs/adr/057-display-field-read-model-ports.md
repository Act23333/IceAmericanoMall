# ADR-057: 契约声明的展示字段回填 —— 应用层读模型端口 + 批量 + fail-open

## 状态

已实施（user-service 收藏、marketing-service 秒杀、marketing-service 券面、merchant-service 店铺关注数、item-service I13 商品字段）

## 背景

契约（`contract/05`）对若干响应 VO 明文声明了**展示型字段**（如 `FavoriteVO.product_name/main_image/price/status`、
`FlashSaleVO.product_name/product_image/original_price/progress_percent`），而实现只投影主键与少量字段，
前端只能渲染「商品 #1001」。补字段会遇到三个必须统一口径的问题：

1. **数据在别的域**（商品名/主图/价格属商品域）；
2. **不得引入 N+1**（列表页逐行取数）；
3. **不得把主接口变脆**（展示字段取不到不应让收藏/秒杀列表整体失败）。

## 决策

**契约声明的展示字段，一律按「应用层读模型端口 + 按 ID 批量 + fail-open」回填。**

| 约束 | 规则 |
| --- | --- |
| 位置 | 端口定义在应用层 `app/port/`，实现在 `infrastructure/persistence/`，不外泄到适配层 |
| 批量 | 签名按 **ID 集合**入参（如 `findBriefs(Collection<Long>)`）；Manager 整页去重后**一次**查询，禁止逐行调用 |
| fail-open | 数据源异常 / 记录缺失 → 展示字段保持 `null`，**主列表照常返回**；禁止为补展示字段抛异常 |
| 事务边界 | **`@Transactional` 的 Manager 只能调用本地（同库）只读实现**；若要跨服务走 Feign，该方法不得带事务 |
| 只读 | 读模型实现不得改写被读域数据（只读 SELECT / `SCARD` 等） |

### 为何本轮不走 Feign

`FlashSaleAdminManager` 等带 `@Transactional`，而本仓库硬约束是「**调用 Feign 的 Manager 不得 `@Transactional`**」。
改走 Feign 就必须重构这些 Manager 的事务边界，改动面与风险都更大。

**同库只读**（user/marketing 与 item **共用同一 schema**；user 域足迹 `HistoryViewQueryImpl` 已有同口径先例）
同时满足批量、fail-open 与事务合规。**边界已隔离在端口之后**：将来若要改走 item 的内部批量摘要端点，
**只需替换端口实现类一处**，应用层与适配层零改动。

> **边界对照（防止被误用）**：本决策只允许「**展示读模型**同库直读」；
> **业务判断**仍必须走 Feign 或本域数据，禁止直读他域写表做规则判定。

## 后果

**正面**：契约声明的展示字段可补齐，且不同步引入 N+1、脆弱性与事务违规；前端不再需要多端点本地 join（消除双端口径分叉）。
**负面 / 风险**：产生对**他域表的只读耦合**（表结构变更会波及读模型实现）。缓解：SQL 限定在端口实现内，并由 H2 语义测试锁定列口径。

## 落地清单（2026-10-16 本批）

| 域 | 展示字段 | 端口（app/port） | 实现 | 数据源 |
| :-- | :-- | :-- | :-- | :-- |
| user | `FavoriteVO.product_name/main_image/price/status` | `FavoriteProductBriefQuery`（**现名 `FavoriteSpuBriefQuery`**，ADR-059） | `infrastructure/persistence/favorite/FavoriteProductBriefQueryImpl`（**现名 `FavoriteSpuBriefQueryImpl`**） | 同库 `product`（现名 `spu`）/`sku` JOIN |
| marketing | `FlashSaleVO.product_name/product_image/original_price` | `FlashSaleProductBriefQuery`（**现名 `FlashSaleSpuBriefQuery`**） | `infrastructure/persistence/flash/FlashSaleProductBriefQueryImpl`（**现名 `FlashSaleSpuBriefQueryImpl`**） | 同库 `sku` JOIN `product`（现名 `spu`） |
| marketing | `UserCouponVO.name/value/min_amount/discount_type` | `CouponFaceQuery` | `infrastructure/persistence/coupon/CouponFaceQueryImpl` | 同库 `coupon` 按 ID 批量（`discount_type` 于 2026-10-15 在同一语句上增列，契约 §4.10.1） |
| merchant | `ShopVO.follower_count` | `StoreFollowStore.followerCounts`（既有端口扩批量方法） | `infrastructure/adapter/RedisStoreFollowStoreAdapter`（pipeline + `SCARD`） | 本地 Redis Set 基数（与 SF4 同源） |
| item | `SkuCO.product_name/seller_id/category_id/freight_template_id`（I13） | `SkuProductDisplayQuery`（**现名 `SkuSpuDisplayQuery`**） | `infrastructure/persistence/SkuProductDisplayQueryImpl`（**现名 `SkuSpuDisplayQueryImpl`**） | 同库 `sku LEFT JOIN product`（现名 `spu`） |

### I13 根因与收口（本批最高优先）

`sku` 表**没有商品名列**，而 `SkuCO` 的四个展示/计价字段只能来自 `product` 表；此前 I13 =
纯 `sku` 表查询 + MapStruct 投影，**无源可映射 → 四字段恒 null**（上层单测因 mock `SkuCO`
而看不见）。已按本 ADR 在 item 域加读模型端口并**联表一次**回填（无 N+1），缺失商品时四字段
为 null、SKU 本体照常返回（`LEFT JOIN`，见下）。

**为何用 `LEFT JOIN` 而非 `INNER JOIN`**：I13 的两个调用方（购物车回填、trade 下单取数）必须先
拿到 SKU 本体（价格/库存/规格）；`INNER JOIN` 会让「商品行缺失」等价于「SKU 不存在」，
把展示字段的缺失放大成购物车漏项/下单失败，违反本 ADR 的 fail-open 口径。

**连带收益（trade-service，本批未改 trade 代码）**：`DirectOrderStrategy` /
`NormalCartOrderStrategy` / `GroupBuyOrderStrategy` / `FlashSaleOrderStrategy` 都从同一个 `SkuCO`
取这四字段 —— 修复前订单快照 `product_name` / `seller_id` 亦为空，并连带影响「单店铺校验」、
`orders.seller_id`（结算归属）与运费计价（`sellerId` 为空时 `FreightPostageResolver` 直接返回 0）。
本批修复后这些字段开始有值，**运费将从「恒 0」恢复到按模板计价**（ADR-037 设计口径）。

## 参考

- `contract/05-API-Specification.md` §4.2.6 / §4.10.2 / §4.10.1 / §4.3.1（展示字段声明）
- `quality/22-API-Spec-BigTech-Conformance-Review.md`（口径：代码不得领先契约）
- ADR-038（Feign 只在 app 层）、ADR-052（DB 直读受控豁免）、`collaboration/09-constraints.md`（事务与 Feign 红线）
