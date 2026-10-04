# ADR-020: SKU 库存扣减/恢复改用单条原子 SQL（放弃 version 读-比-写）

## 背景

`item-service` 的 SKU 库存是全链路资金安全关键路径：`trade-service` 下单（`OrderCreationManager` → `SkuClient.deductStock`）、取消/超时（`restoreStock`）都经 `/internal/item/sku/**` 落到 `SkuServiceImpl`。

修复前的实现是「读-比-写」伪乐观锁：

```java
SkuEntity sku = getById(skuId);
if (sku.getStock() < quantity) throw ...;
lambdaUpdate().eq(id).eq(version, sku.getVersion())   // 只比较 version，set 里没有 version=version+1
        .set(stock, sku.getStock() - quantity).update();
if (!updated) throw ...;
```

三个具体缺陷：

1. **version 只比较不自增**：`set(...)` 未写入 `version = version + 1`，且 item-service 从未注册 MyBatis-Plus `OptimisticLockerInnerInterceptor`（只有 `MyMetaObjectHandler`），因此 `version` 条件恒真 —— 两个并发请求读到同一 version 后会**双双成功**，库存覆盖写（超卖）。
2. **判断与扣减分离**：`stock >= quantity` 在 Java 侧判断，与 UPDATE 之间存在竞态窗口。
3. **死代码误导**：`SkuEntity` 上挂着"DDD 充血模型 V5.0"注释与 `deductStock/restoreStock/putOnSale/isInStock/...` 一整套行为方法（全仓库无调用），其内部 `stockType != STOCK_UNLIMITED` 还对 `Integer` 直接拆箱，是 NPE 隐患；同文件 31-37 行又重复定义了 `STOCK_*` / `STATUS_*` 常量，与 `enums/StockTypeEnum`、`enums/SkuStatusEnum` 双份口径。

`SkuMapper` 里其实**已经存在**两条正确的原子 SQL（`deductStock` / `restoreStock`），只是服务层没用它们。

## 决策

1. **库存变更只走单条原子 SQL**，校验条件内嵌在 WHERE 中，以受影响行数为唯一判定依据：
   - `deductStock`：`UPDATE sku SET stock = stock - #{qty}, sold_count = sold_count + #{qty}, version = version + 1 WHERE id = #{id} AND stock >= #{qty} AND status = 1`；
   - `restoreStock`：`UPDATE sku SET stock = stock + #{qty}, sold_count = GREATEST(0, sold_count - #{qty}), version = version + 1 WHERE id = #{id}`；
   - 新增 `increaseSoldCount` / `decreaseSoldCount`：不限量 SKU（`stock_type = 2`）只动销量，不校验、不改动库存。
2. **失败语义收敛到行数**：`deductStock` 行数为 0 → `BizException(STOCK_INSUFFICIENT, 3012)`（涵盖库存不足 / 已停售 / 记录消失）；`restoreStock` 行数为 0 → 记 `warn` 后幂等返回（订单取消/超时重试链路不得因库存回滚失败而抛错）。
3. **枚举取代魔法值**：`StockTypeEnum.UNLIMITED` 判定不限量，`SkuStatusEnum.ON_SALE` 用于在售过滤；删除 `SkuEntity` 中重复常量与全部无调用行为方法，`SkuEntity` 回归纯持久化对象，Javadoc 如实描述"不变量由服务层 + 原子 SQL 保证"。
4. **version 列语义降级为"每次库存变更自增的审计计数"**，不再作为并发控制手段（item-service 不注册乐观锁拦截器）；并发安全由 SQL 的 `stock >= #{qty}` 谓词保证。`orders` 表的 version 乐观锁不受影响。
5. **列表页最低价**：`ProductVO` 增加 `minPrice`，列表项不再挂完整 SKU 列表；最低价用一次批量查询 + 内存聚合（`SkuService.getMinPriceMapByProductIds`），`sort=price` 由数据库按在售 SKU 最低价排序（`ProductMapper.selectPageOrderByMinPrice` 的 LEFT JOIN 聚合子查询，排序方向为 Java 侧白名单后的 `ASC`/`DESC`）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 保持读-比-写，补 `version = version + 1` 并注册 `OptimisticLockerInnerInterceptor` | 改动最小，贴合原文档"乐观锁"表述 | 冲突率高时需业务层重试循环；拦截器是全局配置，会给所有 SKU 写入加 WHERE version，影响面大于收益 |
| 用 `SELECT ... FOR UPDATE` 悲观锁 | 语义直观 | 行锁持有时长受事务边界影响，下单链路易与其它写事务互相阻塞 |
| Redis 预扣库存（Lua） | 峰值吞吐最好 | 引入缓存与库的一致性对账成本，当前规模不需要；且要先有正确的库侧兜底 |
| **单条原子 SQL（采纳）** | 无额外组件；条件与变更原子；行数即结果；可单测 | 依赖数据库行锁语义；`status` 过滤写在 SQL 中（停售商品返回"库存不足"文案） |

## 后果

**正面**

- 超卖窗口消除：库存校验与扣减在同一条 UPDATE 内完成，行数=0 即失败。
- 不限量 SKU（`stock_type = 2`）扣减/恢复对称（只动 `sold_count`），修复了原实现"扣减加销量、恢复什么都不做"的销量漂移。
- 死代码与重复常量清除，`SkuEntity` 不再对外宣称充血模型；枚举成为库存类型/状态/热销原因的唯一口径。
- `SkuServiceImplTest` 由"本地算术自证"改为 Mock `SkuMapper` 的真实分支测试（成功 / 行数 0 / SKU 不存在 / 不限量 / 恢复幂等），库存路径首次具备可回归的单元测试。

**负面 / 风险**

- `AGENTS.md` 中"Optimistic locking：`orders` 和 `sku` 表使用 `version` 列 + `@Version`"的表述对 `sku` 已不再成立（`orders` 不变），需由 doc-engineer 同步（本 ADR 记录该偏差）。
- 停售 SKU 的扣减失败文案为"库存不足"（3012）而非"商品已停售"，若前端需要区分文案，需新增错误码。
- `ProductMapper.selectPageOrderByMinPrice` 使用 `ORDER BY s.min_price ${orderDirection}`，方向值必须在 Java 侧白名单为 `ASC`/`DESC` 后才可拼接；新增调用方须遵守同一约束。
- 列表页不再返回 `skus`（改为 `minPrice`），依赖旧字段的前端需改用 `GET /api/item/product/{id}` 取完整 SKU。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/item-service/src/main/java/org/icedamericanomall/item/mapper/SkuMapper.java`、`service/impl/SkuServiceImpl.java`、`mapper/ProductMapper.java`
- `Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/manager/OrderCreationManager.java`（唯一的扣减调用方）
- `AGENTS.md`（Key Domain Invariants：Stock non-negative / Optimistic locking）
