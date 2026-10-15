# ADR-057: 契约声明的展示字段回填 —— 应用层读模型端口 + 批量 + fail-open

## 状态

已实施（user-service 收藏、marketing-service 秒杀）；优惠券券面与店铺关注数随契约补丁落地

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

## 参考

- `contract/05-API-Specification.md` §4.2.6 / §4.10.2 / §4.10.1 / §4.3.1（展示字段声明）
- `quality/22-API-Spec-BigTech-Conformance-Review.md`（口径：代码不得领先契约）
- ADR-038（Feign 只在 app 层）、ADR-052（DB 直读受控豁免）、`collaboration/09-constraints.md`（事务与 Feign 红线）
