# ADR-019: 商家 / 店铺 1:N 拆分（seller 瘦身 + shop 承载店铺档案）

## 背景

`merchant-service` 的 `seller` 表（自 user-service 边界重构迁入）同时承载两类关注点：

1. **商家身份 + 生命周期**：`user_id`（`uk_user_id`，一个用户至多一个商家）、`status`（0-审核中 / 1-正常 / 2-冻结）；
2. **店铺档案**：`shop_name` / `shop_logo` / `contact_phone` / `province` / `city` / `district` / `detail_address`。

在多店铺需求（一个商家开多个店）下这套模型有四个具体问题：

- **无法表达 1:N**：店铺资料内联在商家行上，一个商家只能有一个店；
- **状态机被绕过**：管理端审核走 `MerchantRepository.updateStatusById(id, status)` 直写状态列（宽松语义，任意状态互转），聚合并未承担迁移合法性；
- **并发重复申请裸 500**：`MerchantRegistrar.register` 先 `findByUserId` 再 `insert`，竞态下唯一键冲突以 `DuplicateKeyException` 直接冒泡（HTTP 500），未翻译为业务错误；
- **对外寻址口径混杂**：下游所有表的 `seller_id` 存的是 `seller.user_id`（商品 / 订单 / 优惠券 / 结算 / 知识库），而管理端审核 `PUT /api/admin/seller/{id}/approve` 用的是 `seller.id`；公开店铺页 `GET /api/shop/{sellerId}` 又以 `user_id` 为键，且店铺不存在时返回魔法值 `Result.error(404, "店铺不存在")`。

## 决策

**Phase A：拆表不拆口径。** 把店铺档案整体迁到新表，商家表瘦身为身份 + 生命周期；对外寻址口径（`sellerId = 商家 user_id`）在 Phase A 保持不变，靠"默认店铺"解析兼容。

1. **`seller` 瘦身**：保留 `id`（技术主键）、`user_id`（`uk_user_id`）、`status`、`create_time`、`update_time`；删除 7 列店铺资料。商家聚合只保留身份 + 生命周期，`updateShopInfo` 移除。
2. **新增 `shop` 表承载 1:N**：`id` PK、`merchant_id`（FK → `seller.id`）、店铺资料 7 列、`status`（0/1/2）、审计列；业务唯一键 `uk_merchant_shop_name(merchant_id, shop_name)`，查询键 `idx_merchant_status(merchant_id, status)`。
3. **聚合只持 ID**：`Shop.merchantId` 指向 `seller.id`（不持 `Merchant` 对象引用）；`Shop` 状态机（`approve` / `freeze` / `unfreeze` / `isNormal`）与资料更新（`updateProfile`，null-safe）内聚在实体，独立于 `Merchant` 生命周期（商家正常 ≠ 每个店铺都正常）。
4. **默认店铺 = 该商家 `shop.id` 最小者**：存量迁移每个商家只生成一个店铺，新增店铺 id 递增，解析稳定。遗留 `sellerId → 默认店铺` 的解析只在应用层 `ShopManager` 一处实现。
5. **状态迁移只经聚合方法**：`MerchantRepository.updateStatusById` 删除，改为 `updateStatus(Merchant)`（只落库聚合算好的状态）；`MerchantManager.updateStatus` 收紧为"置正常（审核通过 / 解冻）/ 置冻结"，目标为审核中一律 `PARAM_ERROR`。
6. **申请 = 商家 + 默认店铺成对创建**：`MerchantManager.register` 在一个 `@Transactional(rollbackFor = Exception.class)` 内，经领域服务 `MerchantRegistrar.register(merchant, defaultShop)` 先建商家、回填 `shop.merchantId` 再建店铺；并发重复申请由 `MerchantRepositoryImpl.insert` 捕获 `DuplicateKeyException` 翻译为 `BizException(USER_ALREADY_EXISTS)`。
7. **审核 = 两个聚合同步生效**：`PUT /api/admin/seller/{id}/approve` 先 `merchant.approve()` + `shopRepository.updateStatus`，再 `shop.approve()` + 落库，最后经 `AuthContextClient` 授予 `ROLE_SELLER`。该方法**不声明事务**（含 Feign，禁止事务内调 Feign）：两次状态写入均幂等，角色授予失败由管理员重试收敛（沿用既有语义）。
8. **对外契约**：
   - 新增 `GET /api/shops/{shopId}`（仅 `shop.status=1` 可见，返回强类型 `ShopVO`），`GET /api/seller/shops`（本人店铺列表）、`PUT /api/seller/shops/{shopId}`（校验归属，跨商家 `FORBIDDEN`）；
   - 遗留 `GET /api/shop/{sellerId}`、`GET/PUT /api/seller/shop` 保留为"默认店铺"别名并标 `@Deprecated`；店铺不存在改为 `BizException(SHOP_NOT_FOUND)`（ErrorCode 3015），消除魔法值 404；
   - 关注改为以 `shopId` 记账（请求参数 `shopId` 与遗留 `sellerId` 二选一）：Redis 键 `shop:followers:{sellerId}` → `shop:followers:{shopId}`、`user:follow:shops:{userId}` → `merchant:follow:shops:{userId}`，通知 `linkUrl` 用 `/shop/{shopId}`（开发期数据可弃）。
9. **Phase A 下游不动**：`product` / `orders` / `coupon` / `settlement` / `merchant_knowledge_base` 等继续以 `sellerId`（= 商家 `user_id`）为口径，`fk_product_seller` / `fk_order_seller` 等外键不受影响（`seller.id` 未变更）。

### Phase B 路线图（本次不实施）

下游逐域增加 `shop_id` 归属并切口径：`product.shop_id` → 商品归属到具体店铺；`orders` / `order_item` 订单落到下单店铺（跨店下单约束见 `CROSS_STORE_FORBIDDEN`）；`coupon.shop_id`（店铺券）；`settlement.shop_id`（按店铺结算）；`merchant_knowledge_base.shop_id`（店铺知识库）。配套下线"默认店铺解析"与 `@Deprecated` 别名（保留一个版本周期后移除）。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| `seller` 原地扩展（店铺资料存 JSON / 多行 seller 记录） | 无新表、改动最小 | 无唯一键与列约束；查询/索引困难；"商家"语义被污染；多店铺仍需去重 |
| `shop.merchant_id` 直接绑 `user_id`（沿用 sellerId 口径） | 表间口径统一 | 商家身份表与店铺表失去主外键关系；商家注销/审计链断裂；`seller.id` 永为孤儿键 |
| 顺手把下游 `seller_id` 全部切到 `shop_id`（一次到位） | 一步消除双口径 | 商品/订单/结算/知识库 5 域 + 前端同时改，回归面巨大，与"边界重构分批推进"原则冲突 |
| **拆表 + 默认店铺兼容解析（采纳）** | 下游与前端零改动；商家/店铺各自演进；状态机与并发问题一并修复 | 兼容期存在 `sellerId` 与 `shopId` 双口径，必须靠 `@Deprecated` 与 ADR 限期收敛 |
| `seller` 改名 `merchant` + 新建 `shop` | 命名更贴切 | 需改表名与所有引用（含 `sys_permission` 路径、下游注释），本轮收益不足，改名与拆表混在一起风险叠加 |

## 后果

**正面**

- 多店铺模型就位：`shop` 表以 `(merchant_id, shop_name)` 约束店铺，商家可扩展 N 个店而不动商家身份；
- 状态机回归聚合：审核 / 冻结 / 解冻只能经 `Merchant` / `Shop` 的实体方法，仓储不再提供状态直写入口；
- 并发重复申请从"裸 500"变为 `USER_ALREADY_EXISTS` 业务错误（唯一键兜底 + 仓储翻译）；
- 公开店铺页返回强类型 `ShopVO`（`shopId` / `sellerId` / `shopName` / `shopLogo` / `contactPhone` / `address` / `status`），`Map` 契约与魔法值 404 消失；`address` 只到省市区，不在公开页暴露发货明细；
- 店铺资料更新在持久化层带 `id + merchant_id` 双条件，越权写返回 0 行。

**负面 / 风险**

- 兼容期双口径：`sellerId` 语义（= `user_id`）与 `shopId` 并存，`resolveShopId(shopId, sellerId)` 是唯一解析点，需在 Phase B 限期收敛；
- `GET /api/shops/**` 是新路径，**网关 `merchant-service` 路由原只覆盖 `/api/shop/**` 与 `/api/seller/**`**，需在 gateway-service 增补 `/api/shops/**` 谓词后新路径才可在部署环境访问（**交付待办已于 2026-10-03 落地**：网关 `merchant-service` 路由已增补 `/api/shops/**` 谓词，白名单同步按方法级收紧为「仅 GET 匿名放行」，见 02-Architecture §9.3）；
- 关注 Redis 键改名后存量关注关系失效（开发期数据可弃，客户端重新关注即恢复）；
- 审核方法跨两个聚合、非事务：中途失败会留下"商家已正常、店铺未正常"的短暂不一致，靠幂等重试收敛（与既有角色授予失败的处置方式一致）；
- `MerchantVO` 瘦身（不再含店铺资料）后，管理端待审核列表不再携带店铺名；审核页如需店铺信息，Phase B 前需按 `merchantId` 反查 `shop`。
- 附带修复：`merchant-service` 此前缺少 `MetaObjectHandler`，PO 上的 `@TableField(fill = ...)` 实际未生效（`seller.create_time/update_time` 为 NOT NULL 且无默认值，真实库上入驻申请会落库失败）；本次补 `infrastructure/config/MyMetaObjectHandler` 使审计列正常填充。

## 日期

2026-10-03

## 参考

- `Implementation/back-end/database/migrations/V5.4__split_shop_from_seller.sql`、`Implementation/back-end/database/Initialize.sql`（基线同步）
- ADR-016（user-service DDD + 商家域迁出）、ADR-017（认证边界收敛）、ADR-018（统一身份 + RBAC 归属授权域）
- `project-docs/collaboration/09-constraints.md`（事务 / 金额 / 分层禁令）、`AGENTS.md`（Package Structure 与反模式）
