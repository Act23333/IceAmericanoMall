# 03 — 领域模型文档 (DDD Domain Model)

> 来源：`doc/项目的分析流程/03.领域建模.md`、`doc/项目的分析流程/00.用户分析.md`、`doc/IDEA.md`
> 最后更新: 2026-10-05（**V5.9→V5.13 五批交付（P2 增长线，[ADR-045](../adr/045-db-snapshot-single-source.md)/[ADR-046](../adr/046-product-vector-recommendation.md)/[ADR-047](../adr/047-daily-task-system.md)/[ADR-048](../adr/048-content-notes-in-item-domain.md)）**：§一 限界上下文补 V5.9→V5.13 聚合落位注记；§三 新增五节——3.15 促销活动（Promotion，时间窗推导状态，ADR-043）、3.16 每日任务（TaskDef/TaskRecord，`uk_user_task_date` 每日幂等，ADR-047）、3.17 会员等级（MemberLevelRule/MemberGrowthLog，`uk_order_no` 幂等入账 + 只升不降，ADR-044）、3.18 店铺装修（ShopDecoration 与 Shop 1:1，ADR-048）、3.19 种草笔记（Note 内容聚合，软删 + 向量摄取钩子，ADR-048）；§四 聚合表新增四行 + user-service 聚合分包结论同步（domain/model 4 子包）；§六 词汇表新增 11 条（促销引擎/促销标签/每日任务/任务完成记录/会员成长值/会员等级重算/相似商品推荐/种草笔记/店铺装修/搜索联想/活动中心）；§七 领域服务补记 `PricingCalculator`/`PostageCalculator`/`ReconciliationComparer`/`MemberLevelPolicy`/`DocumentChunker` 与向量文本组装纯函数。此前——**P0 四项落地批次（[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)/[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)/[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：金额不变式 **V2**（`payAmount = totalAmount − discountAmount + postFee`，§3.7/§四 同步）；§六 词汇表新增九条：运费模板/运费/满额包邮阈值/商家默认模板/包裹与包裹号/部分发货/确认收到退货/原路退回/对账四态差异；订单状态口径补「部分发货停留待发货」（§3.7/待发货条目）；物流聚合 1:N 包裹化（§3.11 约定偏差消除）。此前——结构规范化 + 数据库整治 + 1:N 拆分同步：Shop 聚合拆分（ADR-019）、SKU 原子扣减（ADR-020）、购物车读时回填（ADR-021）、售后/物流状态机与词汇表修订、`domain/model` 聚合分包同步（ADR-025）；§十 图表说明同步 IA_UML/IA_E-R（Merchant/Shop）；遗留风险清零批次词汇补遗：握手令牌/契约键/吊销名单/向量索引/向量摄取/审计双管道/AUDIT 审计日志/收款账号脱敏，RAGRetriever 接线注记（T14）；工具类收口批次词汇补遗：边缘对端地址（ADR-034）+ 收款账号脱敏掩码单点定义注记；多粒度令牌吊销批次（T19，[ADR-031](../adr/031-multi-granularity-token-revocation.md)，修订 ADR-027）词汇补遗：令牌版本/用户级吊销/全端下线三条新增，契约键与吊销名单条目更新）
> 归属域: backend | Owner: doc-engineer | 更新触发条件: 新增/变更聚合、端口、领域服务、领域事件时

---

## 一、限界上下文 (Bounded Contexts)

```
┌──────────────────────────────────────────────────────────┐
│                    IcedAmericanoMall                      │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐  │
│  │ 用户上下文    │  │ 商品上下文    │  │ 订单上下文     │  │ AI搜索上下文  │  │
│  │ User Context │  │Product Ctx   │  │ Order Context │  │ AI Search Ctx│  │
│  │              │  │              │  │               │  │ (✅ V2.0)    │  │
│  │ 用户(User)   │  │ 商品(Product)│  │ 订单(Orders)  │  │              │  │
│  │ 地址(Addr)   │  │ 规格(SKU)    │  │ 订单项(Item)  │  │ 商品向量索引  │  │
│  │ 商家(Merchant)│  │ 类目(Cat)    │  │ 购物车(Cart)  │  │ Embedding    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬────────┘  └──────┬───────┘  │
│         │                 │                  │                  │          │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌──────┴────────┐  ┌──────┴───────┐  │
│  │ 认证上下文    │  │ 搜索上下文    │  │ 支付上下文     │  │ AI客服上下文  │  │
│  │ Auth Context │  │Search Ctx    │  │Payment Ctx    │  │AIService Ctx │  │
│  │              │  │              │  │               │  │ (✅ V2.0)    │  │
│  │ Token        │  │ 索引商品     │  │ PayOrder      │  │              │  │
│  │ 凭证         │  │ 搜索结果     │  │ 支付渠道       │  │ RAG知识库    │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │ Agent对话    │  │
│                                                          └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐                     ┌──────────────┐  │
│  │ 物流上下文    │  │ 营销上下文    │                     │              │  │
│  │ Logistics Ctx│  │Marketing Ctx │                     │ 知识图谱      │  │
│  │              │  │ (V4.0+)      │                     │ KnowledgeCtx │  │
│  │ 物流单       │  │ 优惠券/秒杀   │                     │ (V3.0+)      │  │
│  └──────────────┘  └──────────────┘                     │ 实体关系图    │  │
│                                                          └──────────────┘  │
└──────────────────────────────────────────────────────────┘
```

> **2026-10 第六轮**：商家上下文（Merchant Ctx）随 merchant-service 独立——入驻申请/审核、店铺档案（`seller` 表所有权）、公开店铺页、店铺关注自用户上下文迁出（聚合/领域服务见 §3.3、§七）。
> **V5.9→V5.13（P2 增长线）**：营销上下文新增 促销（Promotion，[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）与 每日任务（TaskDef/TaskRecord，[ADR-047](../adr/047-daily-task-system.md)）聚合；用户上下文新增 会员等级（MemberLevelRule/MemberGrowthLog，[ADR-044](../adr/044-member-level-and-benefits.md)）；商家上下文新增 店铺装修（ShopDecoration，与 Shop 1:1，[ADR-048](../adr/048-content-notes-in-item-domain.md)）；商品上下文新增 内容聚合（Note + note_like/note_product，[ADR-048](../adr/048-content-notes-in-item-domain.md)，`domain/model/content` 分包预留 community-service 拆分）。明细见 §3.15–§3.19、§四。

### 上下文映射 (Context Map)

| 上游    | 下游    | 关系          | 实现                            |
| ----- | ----- | ----------- | ----------------------------- |
| 用户上下文 | 认证上下文 | 客户/供应商（开放主机服务） | user-service 提供 `/internal/user/getOrCreate` 幂等建档（ADR-006）；**`user_auth` 凭证唯一所有权归认证上下文**（V5.2，[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)），认证侧不再经 Feign 写 user 域凭证 |
| 用户上下文 | 订单上下文 | 客户/供应商      | trade-service 调用 user-service |
| 用户上下文 | 商家上下文 | 客户/供应商 | merchant-service 入驻申请/店铺查询；聚合间只存 `userId`（ADR-006）；2026-10 第六轮自用户上下文迁出 |
| 商家上下文 | 认证上下文 | 客户/供应商 | 入驻审批通过经 `AuthContextClient.setUserRole` 授予 ROLE_SELLER（ADR-018 缝合点，授权域即时失效权限缓存） |
| 商品上下文 | 订单上下文 | 客户/供应商      | item-service 提供商品信息           |
| 订单上下文 | 支付上下文 | 发布/订阅       | 订单创建事件 `order.created` 经 RocketMQ `domain-event-topic` 投递（trade→pay 已落地） |
| 订单上下文 | 物流上下文 | 客户/供应商 + 发布/订阅 | 物流记录创建经 Feign（trade→logistics，**幂等**）；发货事件 `order.shipped` 经 RocketMQ 投递（已落地） |
| 订单上下文 | 通知上下文 | 发布/订阅       | （2026-10 核对）原 gateway WebSocket 推送组件已删除、能力未采用；站内信触达由 notification-service 提供，IM 实时通道由 ai-service STOMP `/ws/chat` 承载 |
| 商品上下文 | AI搜索上下文 | 客户/供应商  | ai-service 调用 search-service + item-service |
| 用户上下文 | AI客服上下文 | 客户/供应商  | ai-service 调用 user-service + trade-service |
| 用户上下文 | 营销上下文 | 客户/供应商      | marketing-service 发放优惠券给用户     |
| 订单上下文 | 营销上下文 | 客户/供应商      | 下单时校验并使用优惠券；**秒杀订单创建归属订单上下文**（trade-service），营销上下文仅负责秒杀活动管理 + Redis 预扣库存 |
| 认证/用户上下文 | 验证上下文 | 客户/供应商 | verification-service 签发/校验/人机验证（唯一出口，fail-closed；ADR-007/016、2026-10 边界重构） |
| 验证上下文 | 触达上下文 | 客户/供应商 | notification-service 短信/邮件通道投递；站内信经 `/internal/notify/in-app` 落库 |
| 用户/支付上下文 | 资金上下文 | 客户/供应商 | wallet-service 余额账户（`/internal/balance/deduct`；pay-service 余额渠道经 `BalanceClient` 调用） |
| AI搜索上下文 | AI客服上下文 | 共享内核 (Embedding) | 向量检索能力复用                      |
| AI搜索上下文 | 知识图谱     | 发布/订阅（V3.0+） | 商品实体关系抽取 → 图谱更新              |

> **V4.1 限界上下文调整**：秒杀订单创建从营销上下文移至订单上下文。trade-service 的 `OrderCreateStrategy` 策略模式统一处理 NORMAL/DIRECT/FLASH_SALE/PRESALE 四种订单类型。marketing-service 仅负责秒杀活动管理（活动配置、库存预热）和 Redis Lua 预扣库存，不再直接创建订单。

> **2026-10 边界重构（五轮）**：新增/明确 验证上下文（verification-service）、触达上下文（notification-service）、资金上下文（wallet-service）；签到/积分并入营销上下文；人工 IM 与商家知识库并入 AI 客服上下文（ai-service）；RBAC 归认证上下文（ADR-018）。**第六轮**：商家上下文（Merchant Ctx）随 merchant-service 独立（入驻/店铺/关注自用户上下文迁出）。服务↔域完整归属见 [02-Architecture §3.1](./02-Architecture.md)。

---

## 二、核心域与支撑域

| 类型      | 上下文               | 说明         |
| ------- | ----------------- | ---------- |
| **核心域** | 订单上下文、支付上下文       | 核心竞争力，交易闭环 |
| **支撑域** | 用户上下文、商家上下文、商品上下文、AI搜索上下文 | 必不可少但非差异化  |
| **通用域** | 认证上下文、搜索上下文、物流上下文、营销上下文 | 可用通用方案实现   |
| **创新域** | AI客服上下文 (🟡 GATED)、知识图谱 (⚪ V3.x) | 差异化竞争力，智能体验  |

---

## 三、实体与属性

### 3.1 用户 (User) — 用户上下文聚合根

> **资料/凭证分离（V5.2，[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)）**：User 聚合只承载资料。密码凭证与微信身份的唯一权威是认证上下文 `user_auth`（见 §3.14）；聚合已移除 `password` / `wxOpenid` 字段，`verifyPassword` / `changePassword` 行为删除（密码校验能力不再出现在 user 域）。
> **术语统一（V5.2）**：领域字段 `username` → `nickname`（展示语义，可重复；持久化列名保留 `username`，Converter 显式映射，`UserInfoResp.username` 报文不变）。`registerTime` 领域语义由 `createTime` 承担；`lastLoginTime` 随登录链路退出 user 上下文停止读取。

| 属性           | 类型（业务） | 说明        |
| ------------ | ------ | --------- |
| userId       | 标识     | 用户业务唯一标识（UUIDv7，存储 `BINARY(16)`，ADR-002 双主键 / ADR-050 存储形态）  |
| nickname     | 字符串    | 昵称（展示字段；V5.2 术语统一，原 username 语义）   |
| phone        | 值对象    | 手机号 `EncryptedIdentifier`（cipher+hash+mask 三字段，等值查询走盲索引，见 §五/§六） |
| email        | 值对象    | 邮箱 `EncryptedIdentifier`（三字段模型，V5.2 新增） |
| avatar       | URL    | 头像        |
| gender       | 枚举     | 性别：0-未知 / 1-男 / 2-女（V5.2 新增） |
| birthday     | 日期     | 生日（V5.2 新增） |
| ~~balance~~  | 金额     | ⚠️ **已迁出（2026-10）**：余额归资金上下文 wallet-service（`wallet_account`，V5.3 存量迁移）；`user.balance` 列冻结保留，代码不再读写 |
| memberLevel  | 字符串    | 会员等级：NORMAL/SILVER/GOLD/PLATINUM（V5.2 新增；不开放用户自改） |
| status       | 枚举     | 正常(1) / 禁用(0) |
| createTime / updateTime | 日期时间 | 审计时间（注册语义由 createTime 承担） |

> **行为（内聚聚合根，不散落 Service）**：`assertActive()` 启停、`changeNickname` / `changeAvatar`、`changeGender` / `changeBirthday` / `changeMemberLevel`（含取值校验）、`changePhone` / `changeEmail`（格式校验 + 归一化 + `EncryptedIdentifier` 三字段整体替换）。余额相关行为（原 `assertBalanceSufficient`）随余额迁出（→ wallet-service，`wallet_account` 表；扣减不变式由仓储条件 UPDATE 承载，无独立领域聚合实体，[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）。
> **状态机**：正常 ⇄ 禁用，启/停迁移内聚于 User 实体方法（领域纯净，规则不散落 Service；ADR-016）。
> **role_type 退役（V5.2）**：聚合不再持有角色类型字段；`roleType` 兼容字段由 RBAC 五表角色编码派生（`RoleTypeDerivation`，见 §七），权限唯一权威为 RBAC 五表。

### 3.2 收货地址 (Address) — 用户上下文实体

| 属性                            | 类型  | 说明       |
| ----------------------------- | --- | -------- |
| addressId                     | 标识  | 地址唯一标识   |
| userId                        | 关联  | 所属用户     |
| receiver                      | 字符串 | 收货人      |
| phone                         | 字符串 | 联系电话     |
| province/city/district/street | 字符串 | 四级地址     |
| detail                        | 字符串 | 门牌号等详细地址 |
| isDefault                     | 布尔  | 默认地址标记   |
| label                         | 字符串 | 标签（家/公司） |

### 3.3 商家 (Merchant) — 商家上下文聚合根

> 归属 merchant-service（商家域，2026-10 第六轮自用户上下文迁出）。**V5.4 起 `seller` 表瘦身为「商家身份 + 生命周期」**，店铺档案拆至 `shop` 表（1 商家 : N 店铺，[ADR-019](../adr/019-merchant-shop-1n-split.md)，见 04-Data-Model §3.3）。

| 属性                                   | 类型  | 说明            |
| ------------------------------------ | --- | ------------- |
| id                                   | 标识  | 技术主键（仅持久化层使用；业务唯一键为用户关联 `userId`） |
| userId                               | 关联  | 关联用户（一对一，跨上下文只存 ID）     |
| status                               | 枚举  | `MerchantStatusEnum`：PENDING(0,审核中) / NORMAL(1,正常) / FROZEN(2,冻结) |
| createTime / updateTime              | 日期时间 | 审计时间           |

> **行为（内聚聚合根）**：工厂 `apply(userId)`（新建 PENDING）；`approve()`（→NORMAL，幂等）/ `freeze()`（→FROZEN，幂等）/ `unfreeze()`（仅 FROZEN→NORMAL）；`isActive()` / `isPending()` / `isFrozen()`。**`updateShopInfo` 已移除（店铺资料归 Shop 聚合）**；状态迁移只经聚合方法（管理端审核走 `MerchantManager.updateStatus`，仓储不再提供状态直写入口）。
> **状态机**：PENDING(0) → NORMAL(1) / FROZEN(2)；冻结/解冻为幂等迁移。准入由领域服务 `MerchantRegistrar` 保障「一用户至多一个商家」（`seller.user_id` 唯一，ADR-016）；并发重复申请由仓储捕获唯一键冲突翻译为 `USER_ALREADY_EXISTS`（原裸 500 修复）。
> **命名注记（2026-10 第六轮）**：领域类已改名 `Merchant`（聚合根 `Merchant` / `MerchantStatusEnum` / `MerchantRegistrar`）并随商家域整体迁出 user-service；持久化 PO `MerchantPO` 映射瘦身后的 `seller` 表。对外入驻端点 `POST /api/seller/apply`（原 `/api/seller/register` 更名，语义为「入驻申请」；**前端契约已变化**）。

#### 3.3.1 店铺 (Shop) — 商家上下文聚合根（V5.4 拆分）

> 店铺档案与商家生命周期解耦：**商家正常 ≠ 每个店铺都正常**。申请 = 商家 + 默认店铺成对创建（同一事务）；审核 = 两个聚合同步生效（商户 approve + 店铺 approve + 经 `AuthContextClient` 授予 ROLE_SELLER，方法不声明事务——禁事务内调 Feign，幂等重试收敛）。

| 属性                                   | 类型  | 说明            |
| ------------------------------------ | --- | ------------- |
| shopId                               | 标识  | 技术主键（`shop.id`） |
| merchantId                           | 关联  | 所属商家（→ `seller.id`，聚合间只持 ID，不持对象引用） |
| shopName / shopLogo                  | 字符串 / URL | 店铺名称 / logo（`uk_merchant_shop_name` 业务唯一键） |
| contactPhone                         | 字符串 | 联系电话          |
| province/city/district/detailAddress | 字符串 | 发货地址（公开页只到省市区，不暴露明细） |
| status                               | 枚举  | 店铺状态：0-审核中 / 1-正常 / 2-冻结（`approve` / `freeze` / `unfreeze` / `isNormal` 内聚实体） |
| createTime / updateTime              | 日期时间 | 审计时间           |

> **默认店铺口径**：遗留 `sellerId`（= 商家 `user_id`）经「该商家 `shop.id` 最小者」解析为默认店铺（应用层 `ShopManager` 唯一解析点，Phase A 兼容期）；店铺资料更新在持久化层带 `id + merchant_id` 双条件（越权写 0 行）。公开可见 = `shop.status=1`。下沉到下游的 `seller_id`（商品/订单/优惠券/结算/知识库）在 Phase B 前维持 `user_id` 口径不变。

### 3.4 商品 (Product/SPU) — 商品上下文聚合根

| 属性           | 类型   | 说明           |
| ------------ | ---- | ------------ |
| productId    | 标识   | 商品业务编号       |
| sellerId     | 关联   | 所属商家         |
| categoryId   | 关联   | 所属类目         |
| name         | 字符串  | 商品名称         |
| mainImage    | URL  | 主图           |
| description  | 富文本  | 图文描述         |
| brand        | 字符串  | 品牌           |
| soldCount    | 整数   | 总销量          |
| commentCount | 整数   | 总评论数         |
| isAd         | 布尔   | 广告商品标记       |
| salesTags    | JSON字符串 | 销售标签（如["热销","新品","限时优惠"]） |
| status       | 枚举   | 上架 / 下架 / 删除 |
| publishTime  | 日期时间 | 发布时间         |

### 3.5 规格 (SKU) — 商品上下文实体

| 属性        | 类型  | 说明               |
| --------- | --- | ---------------- |
| skuId     | 标识  | SKU 业务编码         |
| productId | 关联  | 所属商品             |
| spec      | 字符串 | 规格描述（如"黑色 128G"） |
| price     | 金额  | 价格               |
| stock     | 整数  | 库存数量             |
| stockType | 枚举  | 库存类型：LIMITED(1,限量)/UNLIMITED(2,不限量)/PRESALE(3,预售) |
| isHot     | 布尔  | 热销标识             |
| hotReason | 字符串 | 热销原因枚举码：discount / new_arrival / best_seller / clearance（`HotReasonEnum` 归一，非法值丢弃） |
| salesTags | JSON字符串 | 销售标签（继承自Product，可覆盖） |
| image     | URL | 规格专属图片           |
| soldCount | 整数  | 该规格销量            |
| status    | 枚举  | 可售 / 停售          |
| version   | 整数  | 版本列——**V5.4（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)）起降级为库存变更自增审计**；并发控制由单条原子 SQL（`UPDATE ... WHERE stock >= ?`，行数为唯一判定）保证，不再依赖 `@Version` 拦截器语义 |

### 3.6 类目 (Category) — 商品上下文实体

| 属性         | 类型  | 说明         |
| ---------- | --- | ---------- |
| categoryId | 标识  | 类目唯一标识     |
| name       | 字符串 | 类目名称       |
| parentId   | 关联  | 父类目（多级支持）  |
| level      | 整数  | 层级 (1/2/3) |
| sortOrder  | 整数  | 排序         |

### 3.7 订单 (Orders) — 订单上下文聚合根

> 数据库表名：`orders`（避免 MySQL 保留字 `order`）

| 属性                         | 类型   | 说明                      |
| -------------------------- | ---- | ----------------------- |
| orderId                    | 标识   | 订单唯一标识（技术主键）|
| orderNo                    | 业务编号 | 订单号（对外）                 |
| orderType                  | 枚举   | 订单类型：NORMAL(1,购物车下单)/DIRECT(2,立即购买)/FLASH_SALE(3,秒杀)/PRESALE(4,预售) — 驱动策略模式（V4.1） |
| userId                     | 关联   | 买家                      |
| sellerId                   | 关联   | 商家（一个订单属于一个商家）          |
| totalAmount                | 金额   | 订单原价 = Σ order_item.subTotal（**运费不进商品小计**，ADR-037） |
| payAmount                  | 金额   | 实付金额，**不变式 V2**：`payAmount = totalAmount − discountAmount + postFee` |
| discountAmount             | 金额   | 优惠金额（优惠券/满减等）        |
| postFee                    | 金额   | 运费（分）：下单时按运费模板计价并**快照**（Phase 1 取最高费率模板计一次 + 满额包邮阈值；秒杀单恒 0；ADR-037） |
| status                     | 枚举   | `OrderStatusEnum`：待付款(1)→待发货(2)→待收货(3)→已完成(4)；已取消(5)；待评价(6)。流转由「条件更新 + 生命周期 Manager」强制（不可回退）；**部分发货停留待发货(2)，全部明细发货后才转待收货(3)**（不新增状态，ADR-037） |
| paymentType                | 枚举   | 支付宝 / 微信 / 余额           |
| receiverName/Phone/Address | 快照   | 收货信息快照（address 最长 500 字符）|
| createTime                 | 日期时间 | 下单时间                    |
| payTime                    | 日期时间 | 支付时间                    |
| consignTime                | 日期时间 | 发货时间                    |
| endTime                    | 日期时间 | 完成时间                    |
| closeTime                  | 日期时间 | 关闭时间                    |
| version                    | 整数   | 乐观锁版本号（防并发更新丢失；`@Version` + ia-common `OptimisticLockerInnerInterceptor`，V5.3 注册）       |

### 3.8 订单项 (OrderItem) — 订单上下文实体（组合关系）

| 属性          | 类型   | 说明     |
| ----------- | ---- | ------ |
| orderItemId | 标识   | 订单项标识  |
| orderId     | 关联   | 所属订单   |
| skuId       | 关联   | 对应 SKU |
| productName | 快照   | 商品名称快照 |
| skuSpec     | 快照   | 规格描述快照 |
| price       | 金额快照 | 下单时单价  |
| quantity    | 整数   | 数量     |
| subTotal    | 金额   | 小计     |
| packageNo   | 整数   | 包裹号：发货时标记本明细归属包裹（对应 `order_logistics.package_no`）；NULL=未发货，一明细只发一次（ADR-037） |
| image       | 快照   | 商品图片快照 |

### 3.9 购物车项 (Cart) — 订单上下文实体

| 属性       | 类型  | 说明     |
| -------- | --- | ------ |
| cartId   | 标识  | 购物车项标识 |
| userId   | 关联  | 用户     |
| skuId    | 关联  | SKU    |
| quantity | 整数  | 数量     |
| selected | 布尔  | 是否选中   |

> **读时回填（V5.4，[ADR-021](../adr/021-cart-readtime-enrichment-in-app-layer.md)）**：购物车表不存商品快照；商品名/规格/图片/单价/小计/总价/selectedPrice/allSelected 一律由应用层 `CartManager` 读时一次批量查询回填（单一事实来源 = item-service）。`quantity ≤ 0` 的更新语义 = 删除该行；并发加购撞唯一键 → 重读合并（幂等）。

### 3.10 支付订单 (PayOrder) — 支付上下文聚合根

| 属性             | 类型   | 说明               |
| -------------- | ---- | ---------------- |
| payOrderId     | 标识   | 支付单标识            |
| bizOrderNo     | 关联   | 业务订单号            |
| payOrderNo     | 业务编号 | 支付单号             |
| bizUserId      | 关联   | 支付用户             |
| payChannelCode | 枚举   | 支付渠道             |
| amount         | 金额   | 支付金额             |
| payType        | 枚举   | h5/小程序/公众号/扫码/余额 |
| status         | 枚举   | 待提交→待支付→超时取消→成功  |
| expandJson     | JSON | 渠道扩展信息           |
| qrCodeUrl      | URL  | 支付二维码            |

### 3.11 订单物流 (OrderLogistics) — 物流上下文实体

> **拆单发货（V5.6，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：一订单 : N 包裹——`id` 代理主键 + `orderId` 普通外键 + `packageNo` 包裹号（`uk_order_package(order_id, package_no)`，存量回填 =1）；原「主键直用 orderId（一对一）」约定偏差已消除。包裹记录创建 `(order_id, package_no)` 幂等，重复发货事件不产生第二条物流记录。

| 属性                                   | 类型  | 说明         |
| ------------------------------------ | --- | ---------- |
| id                                   | 标识  | 技术主键（V5.6 代理主键） |
| orderId                              | 关联  | 所属订单（1:N 外键，一单多包裹） |
| packageNo                            | 整数  | 包裹号（同订单从 1 递增；与 `order_item.packageNo` 对应） |
| logisticsNumber                      | 字符串 | 物流单号       |
| logisticsCompany                     | 字符串 | 物流公司       |
| contact/mobile                       | 快照  | 收件人信息快照    |
| province/city/district/street/detail | 快照  | 地址快照       |
| status                               | 枚举  | `LogisticsStatusEnum`：PENDING(1,待揽收) → SHIPPED(2,运输中) → DELIVERED(3,已签收) ↔ RETURNED(4,已退回)；**单调迁移（不可回退），同态重放幂等空操作**（`canTransitTo` 只放行合法迁移） |

### 3.12 优惠券 (Coupon) — 营销上下文聚合根（V4.0+ 多维度模型）

> V4.0 重构：从单一 discountType 扩展为多维度优惠券模型，支持平台券/店铺券/秒杀券/专属券四种类别，每种可配置不同的发放方式、库存策略、领取方式和适用范围。

| 属性           | 类型     | 说明                                                         |
| -------------- | -------- | ------------------------------------------------------------ |
| couponId       | 标识     | 优惠券唯一标识（技术主键）                                    |
| couponNo       | 业务编号 | 优惠券对外编号                                               |
| name           | 字符串   | 优惠券名称（如"618满200减30"）                                |
| discountType   | 枚举     | 优惠类型：FIXED(1,满减券)/PERCENTAGE(2,折扣券)/CASH_COUPON(3,现金券) |
| couponCategory | 枚举     | 券类别：PLATFORM(1,平台券)/SHOP(2,店铺券)/FLASH_SALE(3,秒杀券)/EXCLUSIVE(4,专属券) |
| grantType      | 枚举     | 发放方式：FREE_CLAIM(1,免费领取)/PAID_PURCHASE(2,付费购买)/INVITATION(3,邀请发券)/AUTO_ISSUE(4,自动发放) |
| stockType      | 枚举     | 库存类型：LIMITED(1,限量)/UNLIMITED(2,不限量)                  |
| grabType       | 枚举     | 领取方式：NORMAL(1,普通领取)/NEED_GRAB(2,抢券)/PLATFORM_EXCLUSIVE(3,平台专享) |
| scopeType      | 枚举     | 适用范围类型：ALL(1,全场通用)/CATEGORY(2,指定类目)/PRODUCT(3,指定商品) |
| scopeValues    | JSON     | 适用范围值：当 scopeType=CATEGORY 时为类目ID列表，=PRODUCT 时为商品ID列表 |
| stackRule      | 枚举     | 叠加规则：MUTUAL_EXCLUSIVE(1,互斥)/STACKABLE(2,可叠加)         |
| stackGroup     | 字符串   | 叠加分组：同组内按 stackRule 决定是否可叠加，跨组始终可叠加    |
| priceInCents   | 整数     | 券面额（分）：FIXED 时为满减金额，PERCENTAGE 时为折扣百分比(如85表示85折)，CASH_COUPON 时为固定现金额 |
| minAmountInCents | 整数   | 最低消费金额（分），0 表示无门槛                                |
| totalStock     | 整数     | 总发行量（stockType=LIMITED 时有效）                           |
| claimedCount   | 整数     | 已领取数量（stockType=LIMITED 时有效）                         |
| usedCount      | 整数     | 已使用数量                                                    |
| startTime      | 日期时间 | 有效期开始                                                    |
| endTime        | 日期时间 | 有效期结束                                                    |
| status         | 枚举     | 状态：DRAFT(0,草稿)/ACTIVE(1,已发布)/PAUSED(2,已暂停)/EXPIRED(3,已过期)/TERMINATED(4,已终止) |
| sellerId       | 关联     | 所属商家（couponCategory=SHOP 时必填，PLATFORM 时为空）        |
| createTime     | 日期时间 | 创建时间                                                      |
| updateTime     | 日期时间 | 更新时间                                                      |

> **业务规则**：
> - `grantType=FREE_CLAIM` 时用户可直接领取；`PAID_PURCHASE` 需积分/余额兑换；`INVITATION` 仅受邀用户可见；`AUTO_ISSUE` 由系统自动发放（如新用户注册）
> - `grabType=NEED_GRAB` 时启用 Redis 原子库存扣减 + SISMEMBER 去重（`CouponGrabLuaScript`）
> - 使用优惠券时需校验 `couponCategory` 与卖家匹配规则（`useCoupon` V4.2）：平台券可用于任意商家，店铺券仅限发券商家，秒杀券仅限秒杀活动商品，专属券按规则匹配
> - 优惠券叠加由 `stackRule` + `stackGroup` 共同决定：同组互斥则只能选一张，跨组始终可叠加

### 3.13 用户优惠券 (UserCoupon) — 营销上下文实体

| 属性           | 类型     | 说明                                     |
| -------------- | -------- | ---------------------------------------- |
| userCouponId   | 标识     | 用户优惠券唯一标识                         |
| userId         | 关联     | 用户ID                                    |
| couponId       | 关联     | 优惠券ID                                   |
| status         | 枚举     | 状态：AVAILABLE(1,可用)/USED(2,已用)/EXPIRED(3,已过期) |
| usedOrderNo    | 业务编号 | 使用的订单号（status=USED 时有值）          |
| claimTime      | 日期时间 | 领取时间                                   |
| useTime        | 日期时间 | 使用时间                                   |
| expireTime     | 日期时间 | 过期时间（基于 Coupon.endTime 快照）       |

### 3.14 认证凭证 (UserAuth) — 认证上下文聚合根（V5.1 DDD 重构）

> 数据库表名：`user_auth`（见 04-Data-Model）。每种登录方式（手机号/邮箱/用户名/微信）对应一条记录，通过 `userId` 关联 user-service 的用户（聚合间只存 ID 引用，不持有对象）。
> 领域对象**零框架依赖**：密码哈希比较等能力通过 `PasswordVerifier` 端口以方法参数注入，不依赖 Spring Security。
> **凭证唯一所有权（V5.2，[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)）**：密码凭证与微信身份只落 `user_auth`（authorization-service 本地写入）。注册 = 幂等建档（getOrCreate）+ 本地建凭证行，已带凭证的手机号拒绝注册（`PHONE_ALREADY_REGISTERED`，防账号接管；用户名占用校验退役）；重置密码改为本端按手机号盲索引定位后 `updatePassword`，不再回调 user-service。user-service 侧 `/internal/auth/**` 六端点与 ia-api `AuthClient` 契约已废弃删除。

| 属性 | 类型（业务） | 说明 |
| ---- | ---- | ---- |
| id | 标识 | 技术主键，仅持久化层使用；领域内一律用 `userId + identityType` 定位 |
| userId | 关联 | user-service 的用户 ID（跨上下文仅存 ID） |
| identityType | 枚举 | USERNAME / PHONE / EMAIL / WECHAT / WECHAT_MINI |
| identifierPlain | 字符串 | 非敏感标识明文（微信 openid、用户名） |
| identifier | 值对象 | 敏感标识三字段模型（cipher + hash + mask），见 §五 |
| credential | 加密字符串 | 登录凭证（密码哈希；验证码/微信登录为空） |
| unionId | 字符串 | 微信 UnionID（跨应用打通） |
| status | 枚举 | UNBOUND(0,已解绑) / ACTIVE(1,有效) / LOCKED(2,已锁定) |
| lastLoginTime | 日期时间 | 最后登录时间 |

**业务方法（内聚于聚合根，不泄漏到 Service）**：`isSensitiveIdentity()`（手机号/邮箱走盲索引）、`verifyPassword(raw, PasswordVerifier)`、`assertActive()`、`markLoginSuccess()`、`updatePassword(hash)`、`bindUnionId()`、`unbind()`（状态置 UNBOUND，不物理删除，保留审计）、`getDisplayIdentifier()`（敏感标识返回脱敏值）。

**业务唯一键**（仓储只按业务键操作，无按技术主键的 save/update/deleteById）：

- 敏感标识（手机号/邮箱）：`identityType + identifierHash`（HMAC 盲索引）
- 非敏感标识（openid/用户名）：`identityType + identifierPlain`
- 归属定位：`userId + identityType`

> **V5.2 补充**：登录策略矩阵新增 `EmailLoginStrategy`（EMAIL + EMAIL_CODE，验证码经 verification-service 消费后走盲索引查找/创建）；验证码/人机验证校验收口 verification-service（见 ADR-007/008）。

### 3.15 促销活动 (Promotion) — 营销上下文聚合根（V5.8 促销引擎）

> 店铺级满减/满折活动（`promotion` 表，Phase 1 `sellerId` 必填、一单仅触达单商家）。**状态口径**：活动状态由 `startTime`/`endTime` 时间窗实时推导（`deriveStatus`：1-未开始 / 2-进行中 / 3-已结束），落库 `status` 仅为写入时刻快照——I44 计价查询与管理端守卫（已结束禁编 / 进行中禁删）均不信任快照（复用 AD40-42 秒杀管理端先例）。

| 属性 | 类型 | 说明 |
| ---- | ---- | ---- |
| id | 标识 | 技术主键 |
| name | 字符串 | 活动名称 |
| sellerId | 关联 | 所属店铺（Phase 1 必填） |
| type | 枚举 | `PromotionTypeEnum`：1-满减 / 2-满折 |
| threshold | 金额 | 门槛（分） |
| discountValue | 金额 | 满减 = 减免额（分）；满折 = 折扣率万分比（8500 = 8.5折） |
| maxDiscount | 金额 | 满折封顶（分，0 = 不封顶） |
| startTime / endTime | 日期时间 | 活动时间窗（endTime 严格晚于 startTime，聚合不变式） |
| status | 枚举 | 写入时刻快照（1/2/3），读路径一律按时间窗推导 |
| version | 整数 | 乐观锁（编辑冲突由 MyBatis-Plus 拦截器拦截） |

> **行为（内聚聚合根）**：`deriveStatus(now)` 时间窗状态推导、`ensureTimeWindowOrdered()` 时间窗有序校验、`ensureDiscountRuleLegal()` 优惠规则校验（类型合法 / 满折不越万分比上限）。管理端 `/api/admin/promotion`（AD48-50），计价查询 I44 由 trade `PricingCalculator` 消费（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）；商品侧促销标签（P2/P1）经 I44 fail-open + 60s 缓存组装（见 §六「促销标签」）。

### 3.16 每日任务 (TaskDef / TaskRecord) — 营销上下文聚合（V5.10 表驱动任务体系）

> 表驱动任务（[ADR-047](../adr/047-daily-task-system.md)）：运营加任务 = `task_def` 加一行 seed，代码零改动；任务编码 `code` 为业务唯一键（调用方锚定，如 DAILY_ORDER / DAILY_REVIEW）。完成触发口（I49）幂等：trade 确认收货 / item 评价完成（afterCommit，事务外触发）→ marketing 校验启用状态与每日幂等（fail-open，不阻断主流程）。

| 聚合 | 属性 | 说明 |
| ---- | ---- | ---- |
| TaskDef（`task_def`） | id / code / name / type / rewardPoints / status | `code` 业务唯一（`uk_code`）；type=1 每日重置（Phase 1 唯一值）；status 1-启用 / 0-停用（服务端权威校验，停用即回落未完成） |
| TaskRecord（`task_record`） | id / userId / taskCode / rewardPoints / bizDate / createTime | **`uk_user_task_date` 每日幂等锚点**：同一用户同一任务同一业务日至多一条；`bizDate` 服务端按当天计算（调用方不可传，防伪造刷分）；`rewardPoints` 为完成时刻定义值快照；并发重复插入被唯一键拦截并回落「未完成」 |

### 3.17 会员等级 (MemberLevelRule / MemberGrowthLog) — 用户上下文聚合（V5.9 会员等级引擎）

> 等级门槛规则 + 成长值入账流水（[ADR-044](../adr/044-member-level-and-benefits.md)）。`User.memberLevel`（见 §3.1）为重算结果字段；成长值只在确认收货时按实付金额入账（trade fail-open 触发 I46），等级权益/折扣经 I45 查询（fail-open）。

| 聚合 / 值对象 | 属性 | 说明 |
| ---- | ---- | ---- |
| MemberLevelRule（`member_level_rule`，record 值对象） | level / threshold / discountRate / pointsRate / sort | 等级门槛规则行（Phase 1 为 seed 预置，无管理端编辑，改 seed 即生效）；`threshold` 累计成长值门槛（分）为等级比较基准；`discountRate` 折扣率万分比（9800 = 98%）；`pointsRate` 积分倍率百分比（100 = 1 倍） |
| MemberGrowthLog（`member_growth_log`，聚合根） | id / userId / orderNo / growthAmount / afterLevel / createTime | 成长值入账流水：**`uk_order_no` 幂等锚点**（一订单至多入账一次）；`growthAmount` 只增不扣；`afterLevel` 入账后等级快照 |

> **重算口径（`MemberLevelPolicy` 纯函数，I45）**：目标等级 = max(当前等级, 门槛匹配等级)——**只升不降**；门槛匹配取「累计成长值 ≥ threshold」中门槛最高者；规则缺行按枚举声明序兜底。年度降级考核（周期快照/清零）留 roadmap。

### 3.18 店铺装修 (ShopDecoration) — 商家上下文聚合（V5.12，与 Shop 1:1）

> 与 `shop` 1:1 的装修聚合（`shop_decoration`，`uk_shop` 业务唯一键，[ADR-048](../adr/048-content-notes-in-item-domain.md)）。商家端 `GET/PUT /api/seller/shops/{shopId}/decoration`（S1c/S1d）；公开店铺页（ShopVO）增量返回 decoration。

| 属性 | 类型 | 说明 |
| ---- | ---- | ---- |
| shopId | 标识 | 店铺 ID（业务唯一键 `uk_shop`，与 Shop 1:1） |
| announcement | 字符串 | 店铺公告（≤200，空白归一化为 null） |
| banners | 列表 | 轮播图 URL（≤5，http(s) 校验） |
| hotProductIds | 列表 | 推荐商品位（≤8，按 ID 引用商品；Phase 1 不校验商品归属，展示端按需过滤失效/非在售） |

> **持久化口径**：`banners` / `hotProductIds` 以 JSON 字符串列存储（不引入 TypeHandler），JSON ↔ List 互转收口在仓储实现；条数 / URL 格式 / 去重校验内聚聚合（创建即校验 + 归一化）。

### 3.19 种草笔记 (Note) — 商品上下文内容聚合（V5.12 内容社区 Phase 1）

> 内容聚合落位 item 域 `domain/model/content/` 分包（与 review 同构；**包边界 = 未来 community-service 拆分边界**，ADR-025/[ADR-048](../adr/048-content-notes-in-item-domain.md)）。聚合根为 `note` 行；点赞 `note_like`（笔记 × 用户，uk 幂等 + 原子计数）与关联商品 `note_product`（笔记 × 商品，≤5 条）为**聚合内部关联**，只经各自仓储端口访问；笔记与商品按 `productId` 引用、不持对象引用——发布时的商品存在性/在售校验属跨聚合协作，上移应用层编排。

| 属性 | 类型 | 说明 |
| ---- | ---- | ---- |
| id | 标识 | 技术主键 |
| userId | 关联 | 作者 |
| title | 字符串 | 标题（≤50） |
| content | 字符串 | 纯文本正文（≤2000；Phase 1 不做富文本/视频） |
| coverImage / images | URL / 列表 | 封面 / 图集（≤9，得物式九宫格，http(s) 校验） |
| likeCount | 整数 | 点赞数（uk 幂等 + 原子计数维护） |
| status | 枚举 | 1-已发布（对外可见）/ 2-已删除（作者软删，流与详情不可见；**发布即上架，无草稿态**） |

> **Phase 1 能力边界（ADR-048）**：发布（逐个校验关联商品存在且在售）/ 分页流 / 详情 / 点赞 / 作者软删 / 相似笔记（P34，向量召回 fail-open）；不做笔记评论、关注流、推荐分发、草稿箱。发布/软删经 afterCommit 钩子触发笔记向量摄取（search I50/I51，`notes` 索引，fail-open）。

---

## 四、聚合与聚合根

| 聚合   | 聚合根            | 内部实体               | 不变量                            |
| ---- | -------------- | ------------------ | ------------------------------ |
| 用户聚合 | User           | Address (1:N)      | 一个用户可有多个地址        |
| 会员聚合 | MemberGrowthLog（成长值流水）+ MemberLevelRule（门槛规则，record 值对象） | — | 成长值只增不扣（`uk_order_no` 幂等入账）；等级重算只升不降 = max(当前, 门槛匹配)（[ADR-044](../adr/044-member-level-and-benefits.md)） |
| 商家聚合 | Merchant       | —                  | 一个用户至多一个商家（`seller.user_id` 唯一 + `MerchantRegistrar`）；状态机 PENDING→NORMAL/FROZEN；归 merchant-service（2026-10 第六轮迁出） |
| 店铺聚合 | Shop           | —                  | 1 商家 : N 店铺（`uk_merchant_shop_name(merchant_id, shop_name)`）；店铺状态机独立于商家生命周期；默认店铺 = 该商家 `shop.id` 最小者（V5.4，[ADR-019](../adr/019-merchant-shop-1n-split.md)） |
| 店铺装修聚合 | ShopDecoration | —                  | 与 Shop 1:1（`uk_shop`）；轮播图 ≤5 / 推荐商品位 ≤8 / 公告 ≤200，创建即校验 + 归一化（[ADR-048](../adr/048-content-notes-in-item-domain.md)） |
| 商品聚合 | Product        | SKU (1:N)          | SKU 不能脱离 Product 存在于订单中；库存不能为负 |
| 内容聚合 | Note           | NoteLike / NoteProduct（聚合内关联） | 一笔记至多关联 5 商品（发布即校验存在且在售）；点赞 uk 幂等 + 原子计数；作者软删（status=2），发布即上架无草稿态（[ADR-048](../adr/048-content-notes-in-item-domain.md)） |
| 订单聚合 | Orders         | OrderItem (1:N，组合) | **不变式 V2**：`payAmount = totalAmount − discountAmount + postFee`；`totalAmount` = Σ 订单项小计（运费不进小计，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）；订单项不能脱离订单     |
| 支付聚合 | PayOrder       | —                  | 支付金额 = 关联订单应付金额                |
| 物流聚合 | OrderLogistics | —                  | 物流必须关联已支付订单；1 订单 : N 包裹（`package_no` 区分，ADR-037） |
| 营销聚合 | Coupon         | UserCoupon (1:N)   | 优惠券总发行量 >= 已领取量 >= 已使用量；用户优惠券不能脱离 Coupon 存在 |
| 促销聚合 | Promotion      | —                  | 时间窗有序（endTime 严格晚于 startTime）；满折不越万分比上限；状态由时间窗实时推导，落库 status 仅写入快照（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)） |
| 任务聚合 | TaskDef        | TaskRecord (1:N)   | 任务编码 `uk_code` 业务唯一；完成记录 `uk_user_task_date` 每日幂等（一人一任务一业务日至多一条）；bizDate 服务端计算（[ADR-047](../adr/047-daily-task-system.md)） |
| 资金聚合 | 无独立领域实体（`wallet_account` 条件 UPDATE 承载余额扣减） | — | 余额扣减不变量（不足拒绝扣减），wallet-service（2026-10 新增；ADR-025 明确 `domain/model` 为空、不强行造聚合） |

**user-service 聚合划分结论（V5.2 四层重构 + 2026-10 边界重构后现状，见 [ADR-016](../adr/016-user-service-ddd-and-sms-migration.md)、[ADR-025](../adr/025-domain-aggregate-packaging-and-app-usecase-granularity.md)）**——包结构 `org.icedamericanomall.user.{adapter, app, domain, infrastructure}`，`domain/model` 按聚合 4 子包（user/address/favorite/member，会员等级 ADR-044），app 编排层 8 个子包（user/address/favorite/member/history/oplog/rbac/port；user 子包下原 `UserManager` 已按用例族拆为 `UserQueryManager`（读取+统计）/ `UserAdminManager`（管理端运营）/ `UserProvisionManager`（幂等开通）），infrastructure/persistence 按聚合 6 个持久化子包（user/address/favorite/member/history/oplog）：

| 层级 | 聚合/模块 | 落地形态 |
| ---- | -------- | -------- |
| 核心域聚合 | User / Address | 纯 POJO 聚合根 + 业务规则内聚实体方法（User 启停/资料变更行为 `changePhone`/`changeEmail` 等，凭证已移交认证上下文 ADR-017、Address `assertOwnedBy` 归属断言）；仓储按业务键操作（Address 更新/删除/标默认以 `{userId, id}` 为 WHERE，SQL 级越权兜底）；`UserProvisioner` 幂等建档（getOrCreate，新建分支统一发建档奖励） |
| 支撑域模块 | rbac | `RoleTypeDerivation`（roleType 五表派生读路径）+ 角色设置经 `AuthContextClient` 委托授权域（ADR-018） |
| 外围 CRUD | favorite / history / oplog | 无跨实体不变量，按 CQRS 读路径直用持久化读模型（DO 不出持久化层、不出 Controller）——记录在案的务实取舍（ADR-016 决策一·5） |

> 领域端口（domain/port）：`DistributedLock`（Redisson）/ `NewUserRegistrar`（注册链路）/ `NewUserRewardService`（新用户建档奖励，V5.2 ADR-017）。原 `SignInStore`（→ marketing-service）、`SmsCodeStore`、`PasswordVerifier`（ADR-017）均已不在 user 域；防腐网关（Verification/WechatOAuth）随边界重构删除。微信换取已移出事务边界（唯一本地事务为注册链路，ADR-016）。
> **2026-10 边界重构迁出记录**：signin（→ marketing）、sms 校验链（→ verification，含 `000000` 后门整体删除）、points（→ marketing）、notification（→ notification-service）、chat / knowledge（→ ai-service）、rbac 写侧（→ authorization-service，ADR-018）、seller/店铺档案/店铺关注（→ merchant-service，第六轮）。

---

## 五、值对象

| 值对象      | 属性            | 所属聚合                     |
| -------- | ------------- | ------------------------ |
| Money    | 金额(分) + 币种    | Order, PayOrder, Product |
| Address  | 省+市+区+街道+详细地址 | User (作为值对象拷贝到 Order)    |
| SpecDesc | 规格描述字符串       | Product/SKU              |

---

## 六、通用语言 (Ubiquitous Language) 词汇表

| 中文术语     | 英文                       | 定义              |
| -------- | ------------------------ | --------------- |
| 用户       | User                     | 平台的注册消费者        |
| 商家       | Merchant（原 Seller，2026-10 第六轮更名） | 拥有店铺的用户；聚合/领域服务归商家上下文（merchant-service）；持久化 `seller` 表**仅承载身份+生命周期**（id/user_id/status/审计列，V5.4 瘦身） |
| 店铺       | Shop                     | 商家经营单元：1 商家 : N 店铺（`shop` 表，V5.4 拆分，[ADR-019](../adr/019-merchant-shop-1n-split.md)）；店铺档案（名称/logo/联系电话/发货地址）与状态机内聚在 Shop 聚合，独立于商家生命周期 |
| 店铺状态     | Shop Status              | 0-审核中 / 1-正常 / 2-冻结；**公开可见 = shop.status=1** |
| 默认店铺     | Default Shop             | 遗留 `sellerId`（= 商家 user_id）解析为「该商家 `shop.id` 最小者」；解析只在应用层 `ShopManager` 一处实现（Phase A 兼容期） |
| 管理员      | Admin                    | 平台运营管理者         |
| 商品 (SPU) | Product                  | 标准产品单元，同一款商品的抽象 |
| 规格 (SKU) | SKU (Stock Keeping Unit) | 具体销售单元（如某颜色某尺寸） |
| 类目       | Category                 | 商品分类体系          |
| 订单       | Order                    | 一次购买行为生成的交易记录   |
| 订单项      | OrderItem                | 订单中的单品明细        |
| 购物车      | Cart                     | 用户暂存待购商品的容器     |
| 支付单      | PayOrder                 | 与支付渠道对应的支付记录    |
| 物流单      | Logistics                | 发货后的物流追踪记录      |
| 快照       | Snapshot                 | 下单时复制的商品/地址信息副本 |
| 待付款      | Pending Payment          | 订单已生成等待支付的初始状态  |
| 待发货      | Pending Shipment         | 支付完成后等待商家发货；**部分发货停留本状态**（全部明细均有 packageNo 后才转待收货，不新增状态，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)） |
| 待收货      | Pending Receipt          | 商家已发货等待用户确认     |
| 已完成      | Completed                | 用户确认收货，交易完成     |
| 已取消      | Cancelled                | 交易关闭（超时或用户主动取消） |
| 待审核      | Pending Review           | 售后申请提交后等待商家/平台审核处理；售后状态为 `AfterSaleStatusEnum` 六态：1-待审核 → 2-同意 →（4-已退货）→ 5-已退款 → 6-已完成；3-拒绝为分支态（一单一售后，`uk_order_user`） |
| 秒杀库存预热   | Flash Stock Pre-warm     | 秒杀库存双保险：活动流转到「进行中」时**显式预热**（平均分配到热点分片并写哨兵）；每次预扣前**惰性兜底**（分片与哨兵都不存在时按 DB 剩余库存原子重建，哨兵防重复重置）——未预热场次不会永远命中「售罄」 |
| 抢券补偿     | Coupon Grab Compensation | 抢券通道 Redis 预扣成功后 DB 落库失败时，回滚 Redis（INCR 归还库存 + SREM 撤销去重标记），防止券永久少一个名额（`CouponGrabLuaScript` 补偿通道，幂等） |
| 秒杀商品无店铺归属 | Flash Sale - No Shop Ownership | 秒杀为平台活动，秒杀订单 `sellerId` 固定为 0（marketing 与 trade 的跨服务约定；`MarketingManager.FLASH_SALE_NO_SELLER_ID`） |
| 最低价      | Minimum Price (minPrice) | 商品列表项的起售价：一次批量查询 + 内存聚合出各 SPU 在售 SKU 最低价（`getMinPriceMapByProductIds`）；`sort=price` 由数据库按在售 SKU 最低价排序，列表项不再挂完整 SKU 列表（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)） |
| 购物车读时回填  | Cart Read-time Enrichment | 购物车只存 `(user_id, sku_id, quantity, selected)`；商品名/规格/图片/单价/小计/总价由应用层 `CartManager` 在读时一次批量查询（`SkuClient.getSkuListByIds`）回填计算（[ADR-021](../adr/021-cart-readtime-enrichment-in-app-layer.md)） |
| 全选状态     | allSelected              | 购物车非空 **且** 每项 `selected=true` **且** 每项都能取到单价（可计价）才为 true；SKU 下架/删除项不计入总价并使 `allSelected=false` |
| 原子扣减     | Atomic Deduction         | 库存变更只走单条原子 SQL（校验条件内嵌 WHERE：`stock >= ?`），以受影响行数为唯一判定；不限量 SKU（stock_type=2）只动销量（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)） |
| 领域分页结果   | PageResult               | 领域层分页模型（record：records/total/pageNum/pageSize），不泄漏 MyBatis-Plus `IPage` 到领域层（notification-service `domain/model` 引入） |
| 优惠券      | Coupon                   | 营销工具，用户领取后可在下单时抵扣 |
| 优惠券类别  | Coupon Category          | PLATFORM(平台券)/SHOP(店铺券)/FLASH_SALE(秒杀券)/EXCLUSIVE(专属券) |
| 秒杀        | Flash Sale               | 限时限量的促销活动，库存通过 Redis Lua 原子扣减 |
| 订单类型    | Order Type               | NORMAL(购物车)/DIRECT(立即购买)/FLASH_SALE(秒杀)/PRESALE(预售)，驱动下单策略 |
| 通知通道      | Notification Channel     | 短信/邮件/站内信发送通道；站内信已落库（`notification` 表，公开 `/api/notifications/**`、内部 `/internal/notify/in-app`），归 notification-service（V5.2；2026-10 补齐落库） |
| 验证通道      | Verification Channel     | SMS / EMAIL，验证码签发与校验的通道维度，归 verification-service（V5.2） |
| 验证场景      | Verification Scene       | LOGIN / REGISTER / RESET_PASSWORD，决定验证码文案与有效期策略 |
| 校验结果码     | Verification Result Code | SUCCESS/CODE_MISMATCH/CODE_EXPIRED/TOO_MANY_ATTEMPTS/RATE_LIMITED/SEND_FAILED/SERVICE_UNAVAILABLE/CAPTCHA_FAILED/NOT_SUPPORTED/PARAM_INVALID——结构化结果码（而非异常）用于内部 Feign 校验契约 |
| 校验收口      | Verification Convergence | 验证码与人机验证统一由 verification-service 裁决（fail-closed：服务不可用即拒绝），调用方不自行校验 |
| 邮箱地址      | EmailAddress             | 认证上下文值对象：trim+小写归一化 + 格式校验 + HMAC 盲索引，保证盲索引/Redis Key/建号标识三方对齐（V5.2 邮箱登录） |
| 建档      | Provisioning             | 用户档案幂等创建/回查：按业务唯一键（user_id/手机号/微信 openid）查无则建、唯一键冲突回查重放，由领域服务 UserProvisioner 承载（getOrCreate 语义，ADR-006/016） |
| ~~灰度后门~~ | ~~Gray-release Bypass~~  | **已退役（2026-10 边界重构）**：user-service 本地验证码校验链（含万能码 `000000` + 宽松校验）已整体删除；验证码签发/校验一律经 verification-service 在线完成 |
| 重发限流  | Reissue Rate Limiting    | 同一手机号验证码重发的最小间隔（60 秒窗口），由 verification-service 签发侧执行；原 user-service 本地分级限流（`sms:minute/hour/day:`）已随 `/api/user/code` 删除（2026-10），对外发码限流由签发窗口 + 网关/端点限流承担 |
| 建档奖励      | Provisioning Reward      | 新用户建档时统一发放的一次性积分奖励（200 分、类型 2）：注册 / 短信首登 / 邮箱首登 / 微信登录共用建档漏斗（`UserProvisioner` 新建分支），已存在用户不发，失败仅 WARN 不阻断建档；V5.2 起取代"仅微信新用户"语义，"回归登录奖励（>30 天 100 分）"退役（ADR-017） |
| 盲索引定位     | Blind Index Lookup       | 敏感标识（手机号/邮箱）的等值查询方式：对归一化明文算 HMAC-SHA256（盲索引密钥）得不可逆哈希入唯一索引——密文不可比较，等值查询一律按 hash 定位（user 表 `phone_hash`/`email_hash`，user_auth 表 `identifier_hash`） |
| 头剥离       | Header Sanitization      | 网关在过滤器链最早处（order -1100，`UntrustedHeaderSanitizerFilter`）无条件移除入站 X-User-Id/X-Username/X-User-Roles/X-User-Permissions/X-Tenant-Id/X-Client-Ip，并剥离可伪造代理头（X-Forwarded-For/X-Real-IP/Forwarded）、按真实对端覆写 X-Client-Ip；可信上下文头只由网关在 JWT 验证后注入，X-Trace-Id 保留透传（ADR-017） |
| 余额账户     | Wallet Account           | 资金上下文账户：余额（分）与扣减不变量（不足拒绝扣减，由仓储条件 UPDATE 判定）；表 `wallet_account`（V5.3），归 wallet-service（2026-10 自 user 表迁出；无独立领域实体，ADR-025 不强行造） |
| 握手令牌     | Handshake Token          | WS 握手经 URL query 携带的 access token（`?token=...`）：浏览器 WebSocket 握手无法自定义 `Authorization` 头，由网关 `WsHandshakeTokenFilter`（WebFilter，order -900）换写为 `Authorization: Bearer` 并**剔出该参数**后再进安全链；鉴权口径与普通接口一致（签名 + 时效 + 吊销校验：jti 黑名单 + 用户级令牌版本，[ADR-031](../adr/031-multi-granularity-token-revocation.md)），令牌不落下游（[ADR-026](../adr/026-ws-handshake-query-token-edge-swap.md)） |
| 契约键       | Contract Key             | 被显式豁免「跨服务不得共享 Redis Key」约束的跨服务 Redis 键，仅限「**auth 写、网关只读**」：`user:info:{userId}` / `user:perms:{userId}`（[ADR-018](../adr/018-unified-identity-rbac-ownership.md)，权限缓存）、`auth:blacklist:{jti}`（[ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)，吊销名单）与 `auth:tokenver:{userId}`（[ADR-031](../adr/031-multi-granularity-token-revocation.md)，用户级令牌版本，不设 TTL）；任何第三方服务不得读写，改名/改 TTL 语义必须两侧同步发布（豁免清单见 [09-constraints §四](../collaboration/09-constraints.md)） |
| 吊销名单     | Blacklist                | access token 的 jti 黑名单（**单 token 粒度**）：**登出**成功由 authorization-service 写入 `auth:blacklist:{jti}`（TTL=max(剩余有效期,1s)），网关 `JwtBlacklistFilter`（order -700）**键存在即 401**（fail-open）；WS 与 HTTP 共用同一口径。**改密/重置密码、管理端禁用已改走用户级吊销**（原「改密仅拉黑当前令牌、禁用不拆会话」两条残余窗口已由 [ADR-031](../adr/031-multi-granularity-token-revocation.md) 关闭） |
| 令牌版本     | Token Version (tokenVer) | access token 的 `ver` claim + Redis 键 `auth:tokenver:{userId}`（**不设 TTL**）：登录与 refresh 换发（同一签发链路）写入**签发时刻**的当前版本（`INCR` 产出）；键缺失 = `0`、token 无 `ver` claim = `0`（存量令牌零迁移）。不设 TTL 是刻意语义——版本键若过期，读侧视为 `0`，「版本落后」的已吊销令牌会被**重新放行**；仅「被吊销过」的用户各留一个常驻键（[ADR-031](../adr/031-multi-granularity-token-revocation.md)） |
| 用户级吊销   | User-level Revocation    | 以「该用户全部令牌」为粒度的吊销，**改密 / 重置密码**与**管理端禁用**时执行：**① `auth:tokenver:{userId}` INCR（该用户此前签发的全部 access token 即刻 401）+ ② 按 `PlatformEnum` 全端拆除 refresh 会话（断开续期通道）**——两者必须同时做：只拆会话则已签发 access token 存活至过期，只推版本则旧 refresh 可换发出「新版本」令牌。管理端禁用经 user-service（`UserAdminManager`）`AuthContextClient.revokeUserTokens` → `POST /internal/auth/token-version/revoke`（降级 fail-open，不阻断禁用主流程；[ADR-031](../adr/031-multi-granularity-token-revocation.md)） |
| 全端下线     | All-device Logout        | 用户级吊销的可感知效果：该用户**全部端/设备**的 access token 经网关即刻 401、全部 refresh 会话被拆（旧 refresh 无法换发新令牌），由改密/重置密码/管理端禁用触发（[ADR-031](../adr/031-multi-granularity-token-revocation.md)）。单端登出（jti 黑名单 + 本端会话）不属此列；若产品需要「登出全部设备」，应复用 `revokeUserTokens` 而非扩大登出入口语义（ADR-031 残余风险记录） |
| 向量索引     | Knowledge Vector Index   | 知识库 chunk 的 ES 索引 `knowledge_vectors`（dense_vector，每 chunk 一 doc，按 knowledgeId 先删后写幂等），**归 search-service（ES 域）所有**：分块 + Embedding + 索引读写均在该服务完成，ai-service 只提交原文、取回 chunk 文本（见 02-Architecture §3.4） |
| 向量摄取     | Vector Ingest            | 知识库写入 → 向量索引的同步链路：知识库 CRUD → Feign `/internal/search/vector/knowledge/upsert` → search-service 分块（`DocumentChunker`）+ Embedding → `knowledge_vectors` → 回填 `merchant_knowledge_base.embedding_id = es:{knowledgeId}`；默认关闭（`ai.rag.enabled=false`），全链路 fail-open |
| 审计双管道   | Audit Dual Pipeline      | 管理操作留痕的两条管道（[ADR-032](../adr/032-operation-audit-dual-pipeline.md)）：①**`AUDIT` JSON 日志**——`@OperationLog` + 切面全平台输出（ELK 采集，覆盖全量管理写操作）；②**`operation_log` 落库**——仅 user 域管理操作显式写入（`AdminLogController` 查询）。注解只写日志、不落库；两处口径需人工对齐 |
| AUDIT 审计日志 | AUDIT Audit Log        | 专用 logger `AUDIT` 输出的**单行 JSON** 审计事件（timestamp / module / action / method / user / success / costMs；失败仅记异常类名；**默认不记参数值**），经既有 ELK 管道采集入 ES；查询「全平台谁改了什么」的入口（ADR-032） |
| 收款账号脱敏 | Masked Payout Account    | 提现单出参口径（V5.4）：**商家端只返回脱敏号 `bankAccountMasked`（如 `****1234`），明文字段恒为 null；管理端（已有权限保护）返回明文**以满足打款核对；装配差异由 Converter（`toSellerVO` / `toVO`）承担（12-Security-Model §6.4）；掩码规则由 ia-common `DesensitizeUtil.maskAccount` 单点定义（≤4 全遮 / >4 保留末 4 位，[ADR-034](../adr/034-cross-module-utility-consolidation-and-boundaries.md)） |
| 边缘对端地址 | Edge Peer Address | 网关 `EdgePeerIpResolver` 的 TCP 对端口径：只取连接对端地址、**绝不读任何入站代理头**（XFF 等已在链路入口被剥离），用于网关限流 Key / AI 配额 Key 并覆写 `X-Client-Ip` 下发；与下游消费的「客户端 IP」（ia-common `IpUtil` 只信网关注入头）分属两种口径、不可合并（[ADR-034](../adr/034-cross-module-utility-consolidation-and-boundaries.md)） |
| 运费模板 | Freight Template | 商家在商品域配置的运费计价模板（`freight_template`，V5.6 已建）：固定运费 `fee` + 满额包邮阈值 `free_threshold` + 启用状态（Phase 1 计费方式仅 1-固定运费）；归属 item-service（`/api/item/freight-template/**`），商品经 `product.freight_template_id` 绑定（`ON DELETE SET NULL` 自动回落默认模板，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)） |
| 运费 | postFee | 订单金额一等项（`orders.post_fee`，V5.6 已建）：下单时按 Phase 1 口径计价并**快照**（模板后续改价/停用不影响已建订单）；同单同商家、取各生效模板中 `fee` 最高者计一次；秒杀单（orderType=3）恒 0；模板查询（I43）**fail-closed**——查询失败下单失败，不默认免邮（[ADR-011](../adr/011-feign-fallback-degradation-semantics.md) 判别型定类，ADR-037） |
| 满额包邮阈值 | Free Threshold | `freight_template.free_threshold`（分）：0 = 不参与满额包邮；>0 时订单商品总额（`totalAmount`）达到该值则 `postFee = 0`（用**被选中模板**的阈值判定，ADR-037） |
| 商家默认模板 | Merchant Default Freight Template | 商品未绑定模板（NULL）或绑定模板已停用/已删时的回落：该商家**最早创建的启用模板**（I43 返回集合兜底补入 `isDefault` 行）；商家无任何启用模板时 `postFee = 0`（不阻断下单，ADR-037） |
| 包裹/包裹号 | Package / packageNo | 拆单发货（V5.6 已建）的物流单元：`order_logistics` 一订单 N 条包裹记录（1:N，`uk_order_package(order_id, package_no)`，存量回填 =1）；`order_item.packageNo` 标记明细归属包裹（NULL=未发货，**一明细只发一次**）（ADR-037） |
| 部分发货 | Partial Shipment | 只发部分明细的发货形态：订单**停留待发货(2)**（`consignTime` 首包写入、status 不动），全部明细均有 packageNo 后才转待收货(3)——不新增订单状态，复用既有状态机（ADR-037） |
| 确认收到退货 | Confirm Return | 退货退款（type=1）的放款前置：管理端 `PUT /api/admin/after-sale/{id}/confirm-return` 将售后单 `APPROVED(2) → RETURNED(4)`，**之后才触发退款**（货到放款）；仅退款（type=2）仍在审核同意后触发（[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)/ADR-037） |
| 原路退回 | Original-route Refund | 退款按原支付渠道退回（[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）：BALANCE 本地入账（`wallet_transaction` 幂等）、WECHAT v3 退款 + 异步回调（PAY6）确认、ALIPAY（mock）同步；一次退款 = 一行 `refund_order`（`uk_after_sale` 幂等锚点，`refund_no` 贯穿渠道幂等键与钱包流水号），退款成功才迁移 `REFUNDED(5)` |
| 对账四态差异 | Reconciliation Four-state Diff | 渠道账单 ↔ 本地成功支付流水的比对结果（[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)）：1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致；Phase 1 **只落库记录供管理端处置、不自动调账**（`uk_date_channel` + 先删后写幂等，日终 XXL-Job dev 默认关） |
| 促销引擎 | Promotion Engine | 店铺级满减/满折活动（`promotion`，ADR-043）：门槛 + 满减额/满折率（万分比，8500=8.5折）+ 满折封顶；活动状态由时间窗实时推导（聚合 `deriveStatus`，见 §3.15），落库 status 仅快照——I44 计价与管理端守卫均不信任快照；管理端 `/api/admin/promotion`（AD48-50），计价消费方为 trade `PricingCalculator`（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)） |
| 促销标签 | Promotion Tag | 商品详情/列表的进行中促销角标（`promotions` 字段，P2 详情/P1 分页，ADR-043）：item 读时经 I44 查询本商品生效促销组装标签，**fail-open**（查询失败仅缺标签不阻断详情）+ 60s 本地缓存 |
| 每日任务 | Daily Task | `task_def` 表驱动的运营任务（[ADR-047](../adr/047-daily-task-system.md)）：任务编码 `uk_code` 锚定、type=1 每日重置（Phase 1 唯一值）、启停由服务端权威校验；运营加任务 = 加一行 seed 代码零改动；管理端 CRUD `/api/admin/task`（AD51-53） |
| 任务完成记录 | Task Record | 每日任务完成流水（`task_record`，I49，[ADR-047](../adr/047-daily-task-system.md)）：`uk_user_task_date` 每日幂等锚点——同一用户同一任务同一业务日至多一条；`bizDate` 服务端按当天计算（调用方不可传，防伪造刷分）；`rewardPoints` 为完成时刻定义值快照；并发重复插入被唯一键拦截并回落「未完成」（幂等回落纪律） |
| 会员成长值 | Member Growth | 确认收货按实付金额入账的成长值流水（`member_growth_log`，I46，[ADR-044](../adr/044-member-level-and-benefits.md)）：**`uk_order_no` 幂等锚点**（一订单至多入账一次）；trade 确认收货 fail-open 触发；`growthAmount` 只增不扣（年度降级考核留 roadmap） |
| 会员等级重算 | Member Level Recalc | `MemberLevelPolicy` 纯函数（I45，[ADR-044](../adr/044-member-level-and-benefits.md)）：目标等级 = max(当前等级, 门槛匹配等级)——**只升不降**；门槛匹配取「累计成长值 ≥ threshold」最高者；等级权益/折扣查询 fail-open |
| 相似商品推荐 | Similar Product Recommendation | 详情页相似商品卡（P27，[ADR-046](../adr/046-product-vector-recommendation.md)）：向量侧 = search I48 kNN（`products` 索引 dense_vector + knn mapping，reindex 重建带向量；摄取 I47 upsert 由商品写路径 afterCommit 触发）；item 装配侧 fail-open（召回失败回退无推荐卡） |
| 种草笔记 | Content Note | 内容社区 Phase 1（P28-P33，[ADR-048](../adr/048-content-notes-in-item-domain.md)）：`note`/`note_product`/`note_like` 三表，item `domain/model/content` 聚合分包（包边界 = 未来 community-service 拆分边界）；纯文本 ≤2000 + 图集 ≤9 + 关联商品 ≤5（发布即校验存在且在售）；点赞 uk 幂等 + 原子计数；作者软删（status=2，无草稿态）；发布/软删 afterCommit 触发笔记向量摄取（I50/I51，`notes` 索引，fail-open） |
| 店铺装修 | Shop Decoration | 与 shop 1:1 的装修聚合（`shop_decoration`，`uk_shop`，[ADR-048](../adr/048-content-notes-in-item-domain.md)）：公告 ≤200 + 轮播图 ≤5 + 推荐商品位 ≤8（JSON 列持久化，领域层集中校验条数/URL/去重）；商家端 `GET/PUT /api/seller/shops/{shopId}/decoration`（S1c/S1d）；Phase 1 推荐商品位不校验商品归属 |
| 搜索联想 | Search Suggestion | 搜索框联想（SE5）：热搜词前缀 + 商品名前缀双源合并去重，fail-open（任一源失败降级单源/空列表） |
| 活动中心 | Activity Center | 聚合「进行中」促销的活动列表/详情（M12/M13，`/api/activity`，ActivityVO 源自 promotion 时间窗推导——不信任落库 status 快照）；首页活动入口配可领券三态计数（M7） |

---

## 七、领域服务 (Domain Services)

以下业务规则不属于单一实体，需定义为领域服务：

| 领域服务                | 职责                 | 所在上下文 |
| ------------------- | ------------------ | ----- |
| `UserProvisioner` | 用户幂等建档：查/建 user + 默认角色 + 新用户建档奖励（`NewUserRewardService`，已存在用户不发），业务唯一键冲突回查重放（ADR-016/017） | 用户上下文 |
| `MerchantRegistrar` | 商家准入：一用户至多一个商家唯一性校验；状态迁移 PENDING→NORMAL/FROZEN 内聚于 `Merchant` 实体方法（ADR-016）。2026-10 第六轮随商家域迁 merchant-service | 商家上下文 |
| `SignInPointsPolicy` | 每日签到积分规则（10/15/20 递增，纯 POJO；2026-10 迁入 marketing-service，配套 `SignInStore`/`SignManager`） | 营销上下文 |
| `SmsCodeVerifier`（已删除） | 原 user-service 本地验证码校验（含 `000000` 灰度后门）——2026-10 边界重构整体删除；校验一律经 verification-service（`/internal/verification/code/verify`） | — |
| `RoleTypeDerivation` | roleType 兼容字段派生：按 RBAC 五表角色编码派生（ADMIN→2 / SELLER→1 / 其余→0），`user.role_type` 列退役、五表为唯一口径（V5.2 ADR-017） | 用户上下文 |
| `PasswordVerifier`（端口） | 密码哈希比较（BCrypt）；V5.2 起密码能力收敛认证上下文（user 域端口与适配器已删除，ADR-017） | 认证上下文 |
| `LoginTokenService` | JWT RS256 签发、Access Token + Refresh Token 双Token模式 | 认证上下文 |
| `LoginStrategy` (接口) | 登录策略接口：`support(IdentityType, CredentialType)` + `login(LoginReq)` | 认证上下文 |
| `LoginStrategyFactory` | 注入 `List<LoginStrategy>`，按 identityType+credentialType 分发 | 认证上下文 |
| `LoginContext` | 登录门面：分布式锁(Redisson) + 失败计数(Lua) + 策略执行 | 认证上下文 |
| `StockManager`（已退役）      | 库存扣减、回滚（下单锁定/取消释放）——**无独立领域服务类**：由 item-service `SkuServiceImpl` + `SkuMapper` 单条原子 SQL 承载（条件内嵌 WHERE，行数判定，[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)） | 商品上下文 |
| `AmountCalculator`（未独立成类）  | 订单总价/小计计算内聚于下单策略（`NormalCartOrderStrategy` 等）与 `OrderCreationManager` | 订单上下文 |
| `OrderStateMachine`（未独立成类） | 订单状态流转控制：`OrderStatusEnum` + 条件更新 + `OrderLifecycleManager`（取消/确认收货/发货/超时）与 `OrderCreationManager`（下单） | 订单上下文 |
| `PaymentChannelRouter` | 根据支付方式路由到不同渠道（WECHAT/ALIPAY→渠道客户端；BALANCE→`BalanceClient`→wallet-service） | 支付上下文 |
| `OrderCreateStrategy` (接口) | 统一订单创建策略接口：`support(OrderType)` + `createOrder(CreateOrderReq)`（V4.1） | 订单上下文 |
| `NormalCartOrderStrategy` | 购物车下单策略：校验购物车→校验SKU→校验地址→扣库存→创建订单（V4.1） | 订单上下文 |
| `DirectOrderStrategy` | 立即购买策略：单个SKU直接下单，跳过购物车（V4.1） | 订单上下文 |
| `FlashSaleOrderStrategy` | 秒杀下单策略：Redis预扣库存校验→创建订单（V4.1） | 订单上下文 |
| `CouponClaimLuaScript` | Redis Lua 脚本：优惠券领取去重（SISMEMBER），适用所有券类型（V4.0） | 营销上下文 |
| `CouponGrabLuaScript` | Redis Lua 脚本：抢券原子库存扣减 + 去重（DECR + SISMEMBER），用于 NEED_GRAB 类型券（V4.0） | 营销上下文 |
| `FlashSaleLuaScript` | Redis Lua 脚本：秒杀热 Key 分片库存扣减，防超卖（V4.0） | 营销上下文 |
| `PricingCalculator`（纯函数） | 交易计价中心：促销（I44 fail-closed）→ 会员折扣（I45 fail-open）→ 优惠券核销，叠加 + 择优 + 总额封顶守卫（[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)）；编排入口为 app 层 `PricingService` | 订单上下文 |
| `PostageCalculator`（纯函数） | 运费计价：同单同商家取最高费率模板计一次 + 满额包邮阈值；秒杀单免邮；模板查询 I43 fail-closed（[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)） | 订单上下文 |
| `ReconciliationComparer`（纯函数） | 渠道对账比对：渠道账单 ↔ 本地成功流水四态差异判定，只记录不调账（[ADR-036](../adr/036-channel-reconciliation-record-only-and-rewrite-idempotency.md)） | 支付上下文 |
| `MemberLevelPolicy` | 会员等级重算纯函数（I45，[ADR-044](../adr/044-member-level-and-benefits.md)）：门槛匹配 + 只升不降 = max(当前, 匹配)；规则缺行按枚举声明序兜底；配套 `MemberLevelManager`（app 层编排入账与重算） | 用户上下文 |
| `DocumentChunker` | 知识库文本分块（自 ai-service 迁入；`knowledge_vectors` 摄取链路，见 02-Architecture §3.4） | 搜索上下文 |
| `ProductVectorTextBuilder` / `NoteVectorTextBuilder`（纯函数） | 商品 / 笔记 → Embedding 输入文本组装（I47 商品向量 / I50 笔记向量摄取链路，[ADR-046](../adr/046-product-vector-recommendation.md)/[ADR-048](../adr/048-content-notes-in-item-domain.md)） | 搜索上下文 |
| `EventPublisher`   | 领域事件投递：RocketMQ `domain-event-topic`（`order.created` trade→pay、`order.shipped` 已落地；`payment.succeeded` 常量预留未实现；原 RabbitMQ 通道已移除） | 全局（ia-common） |
| ~~`NotificationService`~~ | 原规划 WebSocket 实时推送（V1.2）——**能力未采用（2026-10 清理）**：gateway WebSocketConfig/WebSocketNotifyController 已删除；站内信触达由 notification-service（`notification` 表，`/api/notifications/**`）承载，IM 实时通道由 ai-service STOMP `/ws/chat` 承载 | — |
| `DistributedScheduler` | 分布式定时任务协调：XXL-Job 已落地（如 trade `AbnormalOrderJob.scanAbnormalOrders` 异常订单兜底；`@Scheduled` 保留单机兜底） | 全局 |
| `ProductVectorizer` | 商品信息 → Embedding 向量（🟡 GATED, ai.enabled=false） | AI搜索上下文 |
| `RAGRetriever`     | 多路召回（KNN + BM25）→ RRF 融合 → 重排序 → 上下文组装；`retrieveForSeller` **已接入客服应答链路**（`ai.rag.enabled` 渐进启用；失败/无命中返回 null 降级为无 RAG），商品召回管线供 `RAGSearchTool`（V5.4 接线，见 02-Architecture §3.4） | AI客服上下文 |
| `AgentPlanner`     | ReAct 推理 + 工具链编排（🟡 GATED, ai.enabled=false：LangChain4j AiServices） | AI搜索上下文 |
| `HandoffManager`   | 人机转交决策（🟡 GATED, ai.enabled=false）           | AI客服上下文 |
| `SessionManager`   | Per-User 会话隔离 + Redis 持久化（🟡 GATED, ai.enabled=false） | AI客服/AI搜索上下文 |
| `EvaluationCollector` | 用户反馈采集 + Langfuse 上报（⚪ V3.0） | AI客服/AI搜索上下文 |

---

## 八、领域事件

> **2026-10 现状**：跨服务即时动作走 **Feign 同步调用**（补偿必须幂等）；领域事件通道为 **RocketMQ `domain-event-topic`**（事件契约定义于 ia-common：`OrderCreatedEvent` 已落地 trade→pay、`OrderShippedEvent` 已落地）。`payment.succeeded` 仅保留 `RocketMqTopics` 常量（事件类已删除，**未实现**）；原 RabbitMQ 领域事件通道（`RabbitMqConfig`）已随重构移除。

| 事件 | 触发场景 | 消费者 | 投递方式 | 状态 |
|------|---------|--------|---------|--------|
| `OrderCreated`（`order.created`） | 用户提交订单 | 支付上下文（发起支付） | RocketMQ domain-event-topic | ✅ 已落地 |
| `OrderShipped`（`order.shipped`） | 商家发货 | 物流/触达侧扩展 | RocketMQ domain-event-topic | ✅ 已落地（trade 发布） |
| `payment.succeeded` | 支付成功回调 | 物流上下文（准备发货）、通知服务（推送） | RocketMQ（常量预留） | ❌ 未实现（`PaymentSucceededEvent` 已删） |
| `OrderCompleted` | 用户确认收货 | 积分上下文（下单积分奖励） | — | ❌ 未实现；当前由 trade 确认收货时经 `PointsClient` Feign 同步发放积分 |
| `UserRegistered` | 新用户注册 | 营销上下文（新用户奖励） | — | ❌ 未实现；当前建档奖励由 `UserProvisioner` 新建分支本地发放 |
| `OrderCancelled` | 订单超时/手动取消 | 商品上下文（库存回滚补偿） | Feign 同步 | 补偿必须幂等（`restoreStock` 行数 0 → WARN 幂等返回） |

---

## 九、领域模型 vs 数据模型 边界

| 方面  | 领域模型            | 数据模型        |
| --- | --------------- | ----------- |
| 关注点 | 业务概念、规则、关系      | 数据存储、表结构、约束 |
| 元素  | 实体、值对象、聚合       | 表、字段、主键、外键  |
| 标识  | 业务标识（如 orderNo） | 技术主键（自增ID）  |
| 关系  | 关联、聚合、组合        | 外键、中间表      |
| 目标  | 理解业务            | 实现存储        |

**开发原则**：先建领域模型理解业务，再建数据模型实现存储。两套模型可以不同，但不能矛盾。

---

## 十、相关图表

| 图表   | 文件                     | 说明                                       |
| ---- | ---------------------- | ---------------------------------------- |
| 领域类图 | `drawio/IA_UML.drawio` | 12 个实体类 + 关联关系（User/Address/Merchant/Shop/Product/Sku/Category/Order/OrderDetail/Cart/PayOrder/OrderLogistics）；Merchant–Shop 1:N（ADR-019），Merchant 与 User 关联 |
| ER 图 | `drawio/IA_E-R.drawio` | 物理表关系（12 张表）；`seller` 表瘦身为 5 列（id/user_id/status/create_time/update_time，物理表名保留、领域类已更名 Merchant），店铺档案拆至 `shop` 表（1 商家 : N 店铺，uk_merchant_shop_name / idx_merchant_status，ADR-019） |
