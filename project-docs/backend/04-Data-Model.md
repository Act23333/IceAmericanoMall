# 04 — 数据模型文档

> 来源：`doc/项目的分析流程/04.数据建模.md`、`doc/项目代码/数据库/Initialize.sql.md`、`doc/项目的分析流程/03.领域建模.md`、`drawio/IA_E-R.drawio`
> 最后更新: 2026-10-03（**P0 四项落地同步（V5.5/V5.6 已建，ADR-035/036/037）**：§十二 12.1–12.4 状态由"规划"收敛为"已建"——退款资金流（refund_order/wallet_transaction，F1）、渠道对账（reconciliation_batch/detail，F4）随 `V5.5__refund_wallet_tx_and_reconciliation.sql` 落库；运费模型（freight_template + product/orders 增量，F2）、拆单发货（order_logistics 主键与包裹改造 + order_item.package_no，F3）随 `V5.6__freight_template_and_package_split.sql` 落库；全库 **45 张表、30 个迁移（V1.1.0→V5.6）**；金额不变式定稿 **V2：`payAmount = totalAmount − discountAmount + postFee`**；§3.5/§3.7/§3.8/§3.10/§3.11/§7.3/§7.6 规划标注同步收敛；§3.11「主键约定偏差」注记**清除**（代理主键 id 已补，ADR-037）；§12.3 纠错删除「order_id 转 UNIQUE uk_order_id」自相矛盾表述（实现为**普通外键 + uk_order_package**，未建单列唯一键）。此前——V5.6 规格先行：新增 §十二 四组 P0 规划表——refund_order/wallet_transaction/reconciliation_batch/reconciliation_detail（**V5.5 资金线迁移**）、freight_template 及 product/orders/order_logistics/order_item 变更（**V5.6 履约线迁移**），§3.5/§3.7/§3.8/§3.10/§3.11/§7.3/§7.6 补规划增量标注，金额不变式变更为 `payAmount = totalAmount − discountAmount + postFee`；规划表均未建库，以"🆕 V5.6 规划"标注，实现落库后本注记同步收敛。此前：数据库整治 + 1:N 拆分同步：seller 瘦身/shop 表（V5.4，ADR-019）、product/review 实际列修正、after_sale/order_logistics 补注、Initialize.sql 全量快照语义、迁移三段式版本号；遗留风险清零批次：`auth:blacklist:{jti}` 契约键注记（ADR-027）、`operation_log` 写入方恢复（ADR-032 管道二）、`merchant_knowledge_base.embedding_id` 口径 `es:{knowledgeId}`）
> 归属域: backend | Owner: doc-engineer | 更新触发条件: 新增/变更表结构时必须同步

---

## 一、数据库设计约定

| 约定 | 说明 |
|------|------|
| **主键策略** | 见下方「主键决策矩阵」 |
| **金额** | 统一使用 `INT`，单位为「分」 |
| **金额不变式** | **V2（当前实现，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：`payAmount = totalAmount − discountAmount + postFee`；旧口径（V5.6 前）：`payAmount = totalAmount − discountAmount`。`totalAmount` 仍严格 = Σ `order_item.subTotal`（运费不进商品小计），运费单列入 `orders.post_fee`、下单时快照——详见 §十二 12.2 |
| **布尔** | 使用 `TINYINT`，1-是 / 0-否 |
| **状态枚举** | 使用 `TINYINT` + 注释说明枚举值 |
| **审计字段** | 所有表包含 `create_time` 和 `update_time` |
| **快照模式** | 订单相关的用户/商品/地址信息在下单时复制到订单表，保证历史可追溯 |
| **乐观锁** | `orders`：`version` INT + MyBatis-Plus `@Version`（乐观锁拦截器已在 ia-common 注册，V5.3 补齐）；`sku`：**并发控制改为单条原子 SQL**（`WHERE stock >= ?`，行数判定），`version` 仅作库存变更自增审计（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)）；`user`：`version` 防并发覆盖（V3.2） |
| **字符集** | `utf8mb4` + `utf8mb4_unicode_ci` |
| **引擎** | InnoDB |
| **JSON 扩展** | 使用 `JSON` 列类型（`expand_json`）预留灵活性 |

> **数据库目录与装库语义（2026-10 整治）**：脚本目录 `Implementation/back-end/database/`。**`Initialize.sql` 为全量最新快照（45 张表，一次装库到位）**；`migrations/`（30 个版本化文件，V1.1.0 → V5.6）是**存量库升级路径**——新装库无需执行迁移，存量库按版本号顺序应用（同版本号共存文件已重命名去重：12 个迁移改为三段式 `V<major>.<minor>.<patch>__desc.sql`）。RBAC 种子与基线一致且幂等：`ROLE_ADMIN` 持有 `*:*:*` 超管权限。

### 主键决策矩阵

**判断原点：业务是否需要看到和使用这个标识。**

```
业务需要看到？
  ├── 是 → 需要可读/可排序/有规则？
  │         ├── 是（客服报单号、支付对账） → 独立业务流水号（xxx_no）
  │         └── 否（仅需唯一引用，如分享链接） → 业务唯一标识（xxx_id，雪花/UUID）
  └── 否 → 仅技术主键 id
```

| 场景 | 主键方案 | 冰美商城示例 |
|------|---------|-------------|
| **业务需要看到 + 可读可排序** | `id` (技术PK) + `xxx_no` (独立流水号) | `orders.order_no`、`pay_order.pay_order_no` |
| **业务需要看到 + 仅需唯一引用** | `id` (技术PK) + `xxx_id` (雪花/短UUID) | `user.user_id`、`product.product_id`、`sku.sku_id` |
| **业务不需要看到**（子实体/关联表/内部表） | 仅 `id` (技术PK) | `order_item`、`cart`、`category`、`address`、`seller`、`order_logistics` |
| **现代轻量项目，无客服/对账场景** | 雪花ID直出，不额外维护业务编号 | （本项目不适用，保留 `order_no`） |

### 技术主键 `id` 生成策略

| 策略 | MyBatis-Plus 注解 | 适用场景 |
|------|------------------|---------|
| `AUTO_INCREMENT` | `@TableId(type = IdType.AUTO)` | 单库单表，ID 由 DB 生成，简单可靠 |
| `ASSIGN_ID`（雪花） | `@TableId(type = IdType.ASSIGN_ID)` | 分布式/分库分表，ID 由应用生成，避免 DB 自增冲突 |

冰美商城当前使用 `AUTO_INCREMENT`，分库分表前切换为 `ASSIGN_ID`。

**分库分表路线**：当前单库单表满足 MVP 需求。当单表数据量超过 500 万行时，引入 **Apache ShardingSphere** 做水平分片，同时将主键策略切换为 `ASSIGN_ID`（雪花算法），避免 DB 自增冲突。

### 数据同步策略（计划 V1.2）

当 ElasticSearch 上线后，需要保证 MySQL 与 ES 的数据一致性：

| 同步路径 | 方案 | 触发条件 |
|---------|------|---------|
| MySQL → ElasticSearch | **Canal** (binlog 订阅) | 商品上架/下架/信息变更时，近实时同步至 ES 索引 |
| MySQL → Redis | Canal → 刷新缓存 | 商品价格/库存变更时，失效 Redis 缓存 |
| 定时全量同步 | XXL-Job 日间低频 | 兜底：每天凌晨 3 点全量重建 ES 索引 |

### 标识使用原则

| 场景 | 使用 | 原因 |
|------|------|------|
| 数据库关联（外键） | 技术主键 `id` | BIGINT 索引性能最优，无业务耦合 |
| 对外暴露（API URL/响应） | 业务标识 `xxx_id` / `xxx_no` | 不暴露数据规模，语义明确 |
| 订单/支付号 | 独立业务流水号 `xxx_no` | 客服可读、对账可追溯、支持日期前缀等格式规则 |
| 内部调用（Feign） | 技术主键 `id` 或业务标识均可 | 视性能需求选择，id 更快 |

---

## 二、表清单与关系概览

```
user ───1:N─── address
  │
  └──1:1─── seller（商家身份 + 生命周期）
              │
              ├──1:N─── shop（店铺档案，V5.4 拆分，ADR-019）
              │
              └──1:N─── product ───1:N─── sku
                           │                  │
                           ├──0:N─── freight_template（运费模板，V5.6 已建，product.freight_template_id 可空回落商家默认）
                           │                  │
       ┌───────────────────┘                  │
       │                                      │
       ▼                                      ▼
    orders ───1:N─── order_item ───N:1─── (sku 快照)
       │
       ├──1:N─── order_logistics（V5.6 已建：1:1 → 1:N 拆单发货，按 package_no 区分包裹）
       │
       ├──1:1─── pay_order (通过 order_no 关联)
       │            │
       │            └──1:N─── refund_order（退款单，V5.5 已建；经 after_sale_id 关联售后单，1 售后单 : 1 退款单）
       │
       └──(对账，无外键) reconciliation_batch/detail（V5.5 已建，按 pay_order_no 比对）
       
cart ───N:1─── user
cart ───N:1─── sku

user ───1:N─── user_auth (authorization-service 认证凭证聚合, V5.1；V5.2 起凭证唯一所有权归 auth，ADR-017)
user ───1:1─── wallet_account (wallet-service 资金域, V5.3；user.balance 列冻结)
  │
  └──1:N─── wallet_transaction (钱包流水，V5.5 已建；幂等键 transaction_no)
user ───1:N─── notification (notification-service 触达域, V5.0；2026-10 所有权迁入)
user ───1:1─── seller (merchant-service 商家域, 2026-10 第六轮所有权迁入；V5.4 起仅身份+生命周期)
```

---

## 三、表详细定义

### 3.1 user（用户表）

> **V5.2 资料/凭证分离 + PII 加密**（[ADR-017](../adr/017-user-domain-split-and-auth-boundary.md)）：user 表为纯资料表；密码凭证/微信身份的唯一权威是 `user_auth`（§3.12），角色唯一权威是 RBAC 五表（§八）。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| user_id | VARCHAR(32) | UNIQUE, NOT NULL | 业务唯一标识（UUID） |
| username | VARCHAR(50) | NULL | 昵称（V5.2 术语统一：原"用户名"语义降级为展示字段，可重复；列名保留历史兼容） |
| phone | VARCHAR(20) | NULL | ⚠️ **deprecated V5.2**：明文手机号退役（迁移器搬数后置 NULL），权威数据在 phone_cipher/phone_hash/phone_mask |
| phone_cipher | VARCHAR(512) | NULL | 手机号密文（AES-256-GCM，Base64，V5.2 新增） |
| phone_hash | CHAR(64) | UNIQUE, NULL | 手机号盲索引（HMAC-SHA256，等值查询/唯一性校验，V2.5 建列） |
| phone_mask | VARCHAR(64) | NULL | 手机号脱敏展示值（如 `138****5678`，V5.2 新增） |
| email_cipher | VARCHAR(512) | NULL | 邮箱密文（AES-256-GCM，V5.2 新增；邮箱身份登录归 user_auth.EMAIL 行） |
| email_hash | VARCHAR(128) | UNIQUE, NULL | 邮箱盲索引（HMAC-SHA256，V5.2 新增） |
| email_mask | VARCHAR(64) | NULL | 邮箱脱敏展示值（V5.2 新增） |
| password | VARCHAR(100) | NULL | ⚠️ **deprecated V5.2**：密码凭证归 `user_auth.credential`（迁移器搬数后置 NULL），观察期后删列 |
| avatar | VARCHAR(255) | NULL | 头像 URL |
| gender | TINYINT | NOT NULL, DEFAULT 0 | 性别：0-未知，1-男，2-女（V5.2 新增） |
| birthday | DATE | NULL | 生日（V5.2 新增） |
| wx_openid | VARCHAR(128) | UNIQUE, NULL | ⚠️ **deprecated V5.2**：微信身份归 `user_auth`（WECHAT 行 `identifier_plain`），观察期后删列 |
| member_level | VARCHAR(20) | NOT NULL, DEFAULT 'NORMAL' | 会员等级：NORMAL/SILVER/GOLD/PLATINUM（V5.2 新增） |
| register_time | DATETIME | NOT NULL | 注册时间（审计列，metaObjectHandler 填充；领域语义由 create_time 承担） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1-正常，0-禁用 |
| balance | INT | NOT NULL, DEFAULT 0 | ⚠️ **deprecated（2026-10 边界重构）**：余额迁至 wallet-service `wallet_account`（V5.3 存量迁移）；列保留冻结，代码停止读写 |
| role_type | TINYINT | NOT NULL, DEFAULT 0 | ⚠️ **deprecated V5.2**：角色唯一权威为 RBAC 五表（sys_user_role），兼容字段由五表派生（RoleTypeDerivation），观察期后删列 |
| version | INT | NOT NULL, DEFAULT 0 | 乐观锁版本号（V3.2 @Version） |
| last_login_time | DATETIME | NULL | ⚠️ **deprecated V5.2**：登录链路已退出 user 上下文，停止写入，观察期后删列 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_user_id`, `uk_phone_hash`（V2.5）, `uk_email_hash`（V5.2 新增）, `uk_wx_openid`；`uk_phone` 已随明文手机号退役删除（V5.2）。

> **V5.2 迁移说明**：结构变更脚本 `database/migrations/V5.2__user_profile_split_and_encryption.sql`。`Initialize.sql` 已是**全量最新快照**（V5.2 形态：已含 `uk_phone_hash` / `uk_email_hash` 唯一索引，明文列仅为带 deprecated 注释的保留列），**存量库**按版本顺序应用迁移升级。本 DDL **只做结构变更**：①新增资料列 gender / birthday / member_level；②PII 三字段激活（phone_cipher/phone_mask 补齐，email 三列新增）；③唯一索引 `uk_phone` → `uk_phone_hash` + `uk_email_hash`（盲索引唯一，明文列退役）；④旧列（password / wx_openid / role_type / 明文 phone / last_login_time）注释标 **deprecated**，代码停止读写，观察期后另行 DDL 删列。**数据搬迁由 authorization-service 存量迁移器执行**（`LegacyCredentialMigrationRunner`，开关 `auth.legacy-migration.enabled` 默认 false）：①user.password → `user_auth.credential`（PHONE 行）后置 NULL；②user.phone 明文 → 三列后置 NULL；③user.wx_openid → `user_auth` WECHAT 行（identifier_plain，本轮不清理列）；幂等可重跑、逐条独立事务、失败 WARN 不中断；前置条件为 PII 密钥已装载（`CryptoKeyConfig`）。

### 3.2 address（收货地址表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, NOT NULL | 所属用户 |
| receiver | VARCHAR(50) | NOT NULL | 收货人 |
| phone | VARCHAR(20) | NOT NULL | 联系电话 |
| province | VARCHAR(50) | NOT NULL | 省 |
| city | VARCHAR(50) | NOT NULL | 市 |
| district | VARCHAR(50) | NOT NULL | 区/县 |
| street | VARCHAR(100) | NOT NULL | 街道/镇 |
| detail | VARCHAR(200) | NOT NULL | 详细地址 |
| is_default | TINYINT | DEFAULT 0 | 1-默认 |
| label | VARCHAR(20) | NULL | 标签（家/公司） |
| longitude | DECIMAL(10,7) | NULL | 经度（可选） |
| latitude | DECIMAL(10,7) | NULL | 纬度（可选） |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_address_user` → user.id ON DELETE CASCADE

### 3.3 seller（商家表）与 shop（店铺表）

> **V5.4 拆分（[ADR-019](../adr/019-merchant-shop-1n-split.md)）**：`seller` 瘦身为「商家身份 + 生命周期」，7 个店铺资料列整体迁至新表 `shop`（1 商家 : N 店铺）；表所有权归 merchant-service（商家域）。领域类 `Merchant` / `Shop`（见 03-Domain-Model §3.3/§3.3.1）。

**`seller`（商家身份表，V5.4 瘦身后）**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, UNIQUE (`uk_user_id`), NOT NULL | 关联用户（1:1，一个用户至多一个商家） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-审核中，1-正常，2-冻结 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

外键：`fk_seller_user` → user.id ON DELETE RESTRICT

**`shop`（店铺表，V5.4 新增，12 列）**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键（`shopId` 口径） |
| merchant_id | BIGINT | FK → seller.id (`fk_shop_merchant`), NOT NULL | 所属商家 |
| shop_name | VARCHAR(100) | NOT NULL | 店铺名称 |
| shop_logo | VARCHAR(255) | NULL | 店铺 logo |
| contact_phone | VARCHAR(20) | NOT NULL | 联系电话 |
| province | VARCHAR(50) | NOT NULL | 发货省 |
| city | VARCHAR(50) | NOT NULL | 发货市 |
| district | VARCHAR(50) | NOT NULL | 发货区/县 |
| detail_address | VARCHAR(200) | NOT NULL | 详细地址 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-审核中，1-正常，2-冻结；公开可见 = 1 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_merchant_shop_name(merchant_id, shop_name)`（业务唯一键）、`idx_merchant_status(merchant_id, status)`

> **默认店铺口径**：遗留 `sellerId`（= 商家 `user_id`）经「该商家 `shop.id` 最小者」解析为默认店铺（应用层 `ShopManager` 唯一解析点，Phase A 兼容期）；存量迁移时每个商家生成一个店铺。下游表（product/orders/coupon/settlement/merchant_knowledge_base）的 `seller_id` 在 Phase B 前维持 `user_id` 口径不变（`fk_product_seller` 等外键不受影响）。

### 3.4 category（类目表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| name | VARCHAR(50) | NOT NULL | 类目名称 |
| parent_id | BIGINT | FK → category.id, NULL | 父类目（NULL=顶级） |
| level | TINYINT | NOT NULL | 层级：1/2/3 |
| sort_order | INT | DEFAULT 0 | 排序序号 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_category_parent` → category.id ON DELETE SET NULL

### 3.5 product（商品表 / SPU）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| product_id | VARCHAR(32) | UNIQUE, NOT NULL | 业务商品编号 |
| seller_id | BIGINT | FK → seller.id, NOT NULL | 所属商家 |
| category_id | BIGINT | FK → category.id, NOT NULL | 所属类目 |
| name | VARCHAR(200) | NOT NULL | 商品名称 |
| main_image | VARCHAR(255) | NULL | 主图 URL |
| images | VARCHAR(2000) | NULL | 多图URL列表（JSON 数组**文本**，V3.5；非 JSON 列类型） |
| video_url | VARCHAR(500) | NULL | 视频URL (V3.5) |
| attributes | JSON | NULL | 规格参数K-V (V3.5) |
| description | TEXT | NULL | 图文描述（HTML） |
| brand | VARCHAR(100) | NULL | 品牌 |
| sold_count | INT | DEFAULT 0 | 总销量 |
| view_count | INT | DEFAULT 0 | 浏览次数 (V3.5) |
| comment_count | INT | DEFAULT 0 | 总评论数 |
| is_ad | TINYINT | DEFAULT 0 | 广告商品标记 |
| service_tags | VARCHAR(500) | NULL | 售后标签（JSON 数组**文本**，V3.5；非 JSON 列类型） |
| sales_tags | JSON | NULL | 商品级销售标签 (V4.0) |
| status | TINYINT | DEFAULT 1 | 1-上架，2-下架，3-删除 |
| freight_template_id | BIGINT | FK → freight_template.id, NULL（**V5.6 已建**） | 绑定运费模板；NULL → 用商家默认模板（该商家**最早创建的启用模板**）；模板被删除时经 `ON DELETE SET NULL` 自动回落。见 §十二 12.2 |
| publish_time | DATETIME | NULL | 发布时间 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

索引：`uk_product_id`, `idx_seller_id`, `idx_category_id`, `idx_status`, `idx_seller_status`

外键：`fk_product_seller` → seller.id ON DELETE RESTRICT，`fk_product_category` → category.id ON DELETE RESTRICT

### 3.6 sku（规格表 / SKU）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| sku_id | VARCHAR(32) | UNIQUE, NOT NULL | 业务 SKU 编码 |
| product_id | BIGINT | FK → product.id, NOT NULL | 所属商品 |
| spec | VARCHAR(200) | NOT NULL | 规格描述 |
| price | INT | NOT NULL | 价格（分） |
| original_price | INT | NULL | 原价（分）(V3.5) |
| stock | INT | NOT NULL, DEFAULT 0 | 库存数量 |
| image | VARCHAR(255) | NULL | 规格专属图片 |
| sold_count | INT | DEFAULT 0 | 该规格销量 |
| stock_type | TINYINT | DEFAULT 1 | 库存类型: 1=LIMITED限量, 2=UNLIMITED不限量, 3=PRESALE预售 (V4.0) |
| is_hot | TINYINT | DEFAULT 0 | 热销标识: 0=否, 1=是 (V4.0) |
| hot_reason | VARCHAR(50) | NULL | 热销原因: discount/new_arrival/best_seller/clearance (V4.0) |
| sales_tags | JSON | NULL | 销售标签JSON: ["限时优惠","新品"] (V4.0) |
| status | TINYINT | DEFAULT 1 | 1-可售，0-停售 |
| version | INT | NOT NULL, DEFAULT 0 | 版本列——**库存变更自增审计**；并发控制由原子 SQL（`WHERE stock >= ?`）保证，不注册 `@Version` 拦截器语义（[ADR-020](../adr/020-sku-stock-atomic-sql-deduction.md)） |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_sku_product` → product.id ON DELETE CASCADE

### 3.7 orders（订单表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| order_no | VARCHAR(32) | UNIQUE, NOT NULL | 订单号 |
| user_id | BIGINT | FK → user.id, NOT NULL | 买家 |
| seller_id | BIGINT | FK → seller.id, NOT NULL | 商家 |
| order_type | TINYINT | NOT NULL, DEFAULT 1 | 订单类型: 1=NORMAL购物车, 2=DIRECT立即购买, 3=FLASH_SALE秒杀, 4=PRESALE预售 (V4.1) |
| total_amount | INT | NOT NULL | 总金额（分）＝ Σ order_item.subTotal（不含运费） |
| pay_amount | INT | NOT NULL | 实付金额（分）。**不变式 V2（已生效，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：`payAmount = totalAmount − discountAmount + postFee`（旧口径无 `postFee` 项，见 §十二 12.2） |
| discount_amount | INT | NOT NULL, DEFAULT 0 | 优惠金额（分） |
| post_fee | INT | NOT NULL, DEFAULT 0（**V5.6 已建**） | 运费（分）；下单时按 §十二 12.2 计价规则计算并快照，Phase 1 同单同商家、取商品绑定模板中 fee 最高者计一次 |
| remark | VARCHAR(200) | NULL（**V5.7 已建**） | 买家留言快照（下单时随单快照，Phase 1 不提供修改端点） |
| status | TINYINT | NOT NULL | 1-待付款，2-待发货，3-待收货，4-已完成，5-已取消，6-待评价 |
| payment_type | TINYINT | NULL | 1-支付宝，2-微信，3-余额 |
| receiver_name | VARCHAR(50) | NOT NULL | 收货人快照 |
| receiver_phone | VARCHAR(20) | NOT NULL | 电话快照 |
| receiver_address | VARCHAR(500) | NOT NULL | 地址快照（完整拼接） |
| create_time | DATETIME | NOT NULL | 下单时间 |
| pay_time | DATETIME | NULL | 支付时间 |
| consign_time | DATETIME | NULL | 发货时间 |
| end_time | DATETIME | NULL | 完成时间 |
| close_time | DATETIME | NULL | 关闭时间 |
| comment_time | DATETIME | NULL | 评价时间 |
| version | INT | NOT NULL, DEFAULT 0 | 乐观锁版本号 |
| update_time | DATETIME | NOT NULL | |

索引：`uk_order_no`, `idx_user_id`, `idx_seller_id`, `idx_status`, `idx_user_status`

### 3.8 order_item（订单项表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| order_id | BIGINT | FK → orders.id, NOT NULL | 所属订单 |
| sku_id | BIGINT | FK → sku.id, NOT NULL | SKU ID |
| product_name | VARCHAR(200) | NOT NULL | 商品名快照 |
| sku_spec | VARCHAR(200) | NOT NULL | 规格快照 |
| price | INT | NOT NULL | 单价快照（分） |
| quantity | INT | NOT NULL | 数量 |
| sub_total | INT | NOT NULL | 小计（分） |
| package_no | INT | NULL（**V5.6 已建**） | 包裹号——发货时标记本明细归属包裹（对应 `order_logistics.package_no`）；NULL=尚未发货；一明细只发一次（写入后不可重复发货） |
| image | VARCHAR(255) | NULL | 图片快照 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_orderitem_order` → orders.id ON DELETE CASCADE（组合关系）

### 3.9 cart（购物车表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, NOT NULL | 用户 |
| sku_id | BIGINT | FK → sku.id, NOT NULL | SKU |
| quantity | INT | DEFAULT 1 | 数量 |
| selected | TINYINT | DEFAULT 1 | 1-选中，0-未选中 |
| create_time | DATETIME | NOT NULL | 加入时间 |
| update_time | DATETIME | NOT NULL | |

唯一约束：`uk_user_sku` (user_id, sku_id) — 同一用户同一 SKU 只有一条记录

### 3.10 pay_order（支付订单表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| biz_order_no | VARCHAR(32) | NOT NULL | 业务订单号（关联 order.order_no） |
| pay_order_no | VARCHAR(32) | UNIQUE, NOT NULL | 支付单号 |
| biz_user_id | BIGINT | FK → user.id, NULL | 用户 |
| pay_channel_code | VARCHAR(20) | NOT NULL | 支付渠道代码 |
| amount | INT | NOT NULL | 支付金额（分） |
| pay_type | TINYINT | NOT NULL | 1-h5, 2-小程序, 3-公众号, 4-扫码, 5-余额 |
| status | TINYINT | NOT NULL | 0-待提交, 1-待支付, 2-超时取消, 3-成功 |
| expand_json | JSON | NULL | 扩展字段 |
| result_code | VARCHAR(50) | NULL | 第三方返回码 |
| result_msg | VARCHAR(200) | NULL | 第三方返回信息 |
| pay_success_time | DATETIME | NULL | 支付成功时间 |
| pay_over_time | DATETIME | NULL | 支付超时时间 |
| qr_code_url | VARCHAR(255) | NULL | 二维码链接 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

> **幂等口径（2026-10 补注）**：支付成功处理走**条件更新**——`markSuccessIfPending(payOrderNo)` 仅在 `status=待支付` 时置成功（`UPDATE ... WHERE pay_order_no = ? AND status = ?`），条件未命中返回 false（重复回调 / 超时取消后的回调不重复处理，与 `pay_order_no` 唯一约束互为兜底）。
> **退款关联（V5.5 已建，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）**：本表同域子表 `refund_order`（退款单，`pay_order_no` 关联；三通道原路退回，`uk_after_sale` 一售后单一次退款幂等锚点）——见 §十二 12.1；同域对账两表 `reconciliation_batch` / `reconciliation_detail`（四态差异只记录不调账，§十二 12.4）。

### 3.11 order_logistics（订单物流表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键（V5.6 代理主键） |
| order_id | BIGINT | FK → orders.id, NOT NULL | 订单 ID（V5.6 起 **1:N 外键**，一单多包裹） |
| package_no | INT | NOT NULL, DEFAULT 1 | 包裹号（唯一约束 `uk_order_package(order_id, package_no)`，存量回填 =1） |
| logistics_number | VARCHAR(50) | NOT NULL | 物流单号 |
| logistics_company | VARCHAR(50) | NOT NULL | 物流公司 |
| contact | VARCHAR(50) | NOT NULL | 收件人快照 |
| mobile | VARCHAR(20) | NOT NULL | 电话快照 |
| province/city/district/street | VARCHAR | NOT NULL | 地址快照 |
| detail | VARCHAR(200) | NOT NULL | 详细地址快照 |
| status | INT | NOT NULL, DEFAULT 1 | 物流状态：1-待揽收(PENDING)，2-运输中(SHIPPED)，3-已签收(DELIVERED)，4-已退回(RETURNED)（V1.1.1） |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

> **状态迁移约束（2026-10 补注）**：物流状态**单调迁移、不可回退**（PENDING→SHIPPED→DELIVERED↔RETURNED）；同态重放视为幂等空操作（成功返回，不重复落库）；应用层经 `LogisticsStatusEnum.canTransitTo` 校验后才更新。
> **V5.6 已建（拆单发货，[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：原「主键复用 order_id」约定偏差已收敛——①新增代理主键 `id BIGINT AUTO_INCREMENT`，`order_id` 转为**普通外键**（**不建单列唯一键**——1:N 语义下单列唯一会禁止一单多包裹）；②新增 `package_no INT NOT NULL DEFAULT 1`，唯一约束 `uk_order_package(order_id, package_no)`，**存量数据回填 package_no=1**；③1:1 升级为 **1:N**（一订单多包裹，每包裹一条物流记录）。见 §十二 12.3。

### 3.12 user_auth（用户认证凭证表，V5.1）

> 归属 authorization-service 认证上下文聚合。每种登录方式（手机号/邮箱/用户名/微信）一条记录，通过 `user_id` 关联 user-service 的 `user` 表（聚合间只存 ID 引用）。建表脚本：`database/migrations/V5.1__add_user_auth.sql`。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键（领域层不可见，仓储按业务键操作） |
| user_id | BIGINT | NOT NULL, FK → user.id | 关联用户技术主键（跨服务 user_id 语义，见 ADR-006） |
| identity_type | VARCHAR(32) | NOT NULL | 标识类型：USERNAME/PHONE/EMAIL/WECHAT/WECHAT_MINI |
| identifier_plain | VARCHAR(128) | NULL | 非敏感标识明文（openid/用户名） |
| identifier_cipher | VARCHAR(512) | NULL | 敏感标识密文（AES-256-GCM，Base64 存储） |
| identifier_hash | VARCHAR(128) | NULL | 敏感标识盲索引（HMAC-SHA256，查询定位用） |
| identifier_mask | VARCHAR(64) | NULL | 敏感标识脱敏展示值（如 `138****5678`） |
| union_id | VARCHAR(64) | NULL | 微信 UnionID（跨应用打通） |
| credential | VARCHAR(255) | NULL | 登录凭证（BCrypt 密码哈希；验证码/微信登录为空） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 0=已解绑, 1=有效, 2=已锁定 |
| last_login_time | DATETIME | NULL | 最后登录时间 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_type_hash(identity_type, identifier_hash)`、`uk_type_plain(identity_type, identifier_plain)`、`idx_user_id`、`idx_union_id`、外键 `fk_user_auth_user`

**敏感数据三字段模型**：敏感标识（手机号/邮箱）密文 `identifier_cipher` + 盲索引 `identifier_hash` + 脱敏 `identifier_mask` 三列分离——密文可解密还原、盲索引支撑等值查询（不可逆）、脱敏值仅供展示。非敏感标识（openid/用户名）直接存 `identifier_plain`。两个唯一索引均含可为 NULL 的列，利用 MySQL 唯一索引多 NULL 特性：敏感类型只有 hash 入索引、非敏感类型只有 plain 入索引，同一 `(identity_type)` 下两类记录互不冲突，天然支撑幂等 upsert（`INSERT ... ON DUPLICATE KEY UPDATE`）。

**Repository 业务键约定**：领域仓储 `UserAuthRepository` 仅提供 `insert` / `updateByUserIdAndType` / `upsert` / `findByIdentity` / `findByIdentityHash` / `findFirstByUnionId` / `findByUserId(AndType)`，技术主键 `id` 不出持久化层；解绑通过 `status=0` 状态更新（软删除），不物理删除。

> **配套 Redis 契约键（[ADR-027](../adr/027-jwt-blacklist-writer-and-contract-key.md)）**：`auth:blacklist:{jti}` —— 登出 / 重置密码成功由 authorization-service 写入（值语义：**键存在即已吊销**；TTL = max(access token 剩余有效期, 1s)），网关 `JwtBlacklistFilter` **只读**（Redis 异常 fail-open）。该键为**网关依赖的跨服务契约键**（与 `user:info:` / `user:perms:` 同级，豁免「跨服务不得共享 Redis Key」约束，见 [09-constraints §四](../collaboration/09-constraints.md)）；非表结构，随本表所在认证上下文一并记录。

---

## 四、数据隔离规则 (DO/DTO/VO)

遵循三层对象隔离，禁止实体透传：

| 对象类型 | 使用范围 | 说明 |
|----------|---------|------|
| **DO** (Entity) | Mapper / Infra 层 | 数据库实体，仅数据访问层使用 |
| **DTO** | Controller ↔ Service; 服务间 Feign 调用 | 传输对象，接口入参/出参 |
| **VO** | Controller → 前端 | 视图对象，面向展示裁剪 |

```java
// 禁止：Controller 直接返回 DO
public UserEntity getUser(Long id) { ... }       // ❌

// 正确：Controller 返回 VO
public Result<UserVO> getUser(Long id) { ... }    // ✅

// 禁止：Service 直接操作数据库
public void createOrder(OrderDTO dto) {
    orderMapper.insert(...)  // ❌ Service 不应该直接调 Mapper
}

// 正确：Service 通过 Mapper 间接操作，且不暴露 DO
```

---

## 五、ER 图

完整 ER 图见：`drawio/IA_E-R.drawio`

核心关系要点：
- `user` ↔ `address`：一对多（CASCADE 删除）
- `user` ↔ `seller`：一对一（RESTRICT 删除）
- `seller` → `product`：一对多
- `product` → `sku`：一对多（CASCADE）
- `orders` → `order_item`：一对多组合（CASCADE）
- `orders` ↔ `pay_order`：通过 `order_no` 关联（非外键约束）
- `orders` ↔ `order_logistics`：一对多（V5.6 已建——按 `package_no` 区分包裹，代理主键 `id` + `uk_order_package`，见 §十二 12.3）
- `pay_order` → `refund_order`（V5.5 已建）：通过 `pay_order_no` 关联；`refund_order.after_sale_id` UNIQUE（一售后单一次退款，见 §十二 12.1）
- `cart` → `user` + `sku`：多对一

---

## 七、V1.1+ 扩展表

### 7.1 V1.1 积分 & 评价 & 收藏

> 表所有权（2026-10 边界重构）：`points_log` 归**营销域 marketing-service**（积分账本；写入经 `/internal/points/add`，签到积分由 marketing 本地发放）。

**`points_log`** — 积分变动日志
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 用户ID |
| points | INT | 积分变动（正=获得，负=消耗） |
| type | TINYINT | 1=签到, 2=下单, 3=任务, 4=兑换, 5=过期 |
| source | VARCHAR(50) | 来源描述 |
| create_time | DATETIME | |

**`review`** — 商品评价
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| product_id | BIGINT | |
| order_id | BIGINT | 关联订单，唯一约束 `uk_order_user` |
| rating | TINYINT | 1-5 星 |
| content | TEXT | |
| create_time | DATETIME | |

**`favorite`** — 商品收藏
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 唯一约束 `uk_user_product` |
| product_id | BIGINT | |
| create_time | DATETIME | |

### 7.2 V1.2 秒杀 & 首页装修 & 操作日志 & 优惠券

**`flash_sale`** — 秒杀活动
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| product_id | BIGINT | |
| sku_id | BIGINT | |
| flash_price | INT | 秒杀价（分） |
| stock | INT | 秒杀库存 |
| sold_count | INT | 已售 |
| version | INT | 乐观锁 |
| start_time / end_time | DATETIME | |

**`flash_order_log`** — 秒杀订单事务日志 (V3.7，幂等+兜底)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| order_no | VARCHAR(32) UNIQUE | 订单号 |
| flash_id | BIGINT | 秒杀活动ID |
| user_id | BIGINT | 用户ID |
| status | TINYINT DEFAULT 0 | 0=待处理, 1=已创建, 2=失败, 3=超时取消 |
| retry_count | INT DEFAULT 0 | 重试次数 |
| error_msg | VARCHAR(500) | 失败原因 |
| create_time | DATETIME | |
| update_time | DATETIME | |

索引: `idx_order_no`, `idx_status_time`(status, create_time)

**`home_config`** — 首页配置
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| slot | VARCHAR(30) | 位置(banner/hot/new/sale) |
| type | TINYINT | 1=商品组, 2=广告图, 3=活动链接 |
| product_ids | VARCHAR(500) | 商品ID CSV |
| image / link_url | VARCHAR(255) | |
| sort_order | INT | |

**`operation_log`** — 操作审计日志（user 域管理操作台账）

> **写入方恢复（2026-10，[ADR-032](../adr/032-operation-audit-dual-pipeline.md) 管道二）**：仅 **user 域管理操作**（用户启用/禁用、角色授予）经应用层 `OperationLogManager.record(userId, module, action, content)` **显式落库**（不经 AOP；`@OperationLog` 只写审计日志、不写本表）。`updateStatus` 状态变更与留痕同事务；`setUserRole` 审计独立事务（禁事务内调 Feign）。`GET /api/admin/log` 查询范围收窄为 user 域管理操作；全平台审计走 `AUDIT` JSON 日志管道（ELK 采集）——本表**不再是**全平台审计台账。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 操作者用户ID |
| username | VARCHAR(50) | 操作者用户名（当前 `UserContext`，取不到留空） |
| action | VARCHAR(50) | `MODULE:ACTION`（表无 module 列，模块限定词并入；常量 `OperationAuditConstants`） |
| target | VARCHAR(100) | **未被写入方使用**（历史行仍有值）；收敛（补 module 列或废弃 target）需 DDL，见 ADR-032 |
| detail | VARCHAR(500) | 动作 + 目标标识摘要（不含敏感值） |
| ip | VARCHAR(45) | 当前 `UserContext` 取值（取不到留空） |
| create_time | DATETIME | 列默认值 CURRENT_TIMESTAMP |

**`coupon`** — 优惠券模板（V1.1 迁移时加入，V1.2 添加 seller_id，V4.0 升级为五维模型）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| coupon_id | VARCHAR(32) UNIQUE | 业务标识 |
| name | VARCHAR(100) | |
| type | TINYINT | **DEPRECATED V4.0**: 请使用 discountType。1=满减, 2=折扣（保留向后兼容） |
| value | INT | 满减金额(分) / 折扣百分比(85=8.5折) |
| min_amount | INT | 最低消费(分) |
| seller_id | BIGINT | NULL=平台券, 非NULL=店铺券 |
| total_qty / issued_qty | INT | |
| start_time / end_time | DATETIME | |
| **V4.0 五维模型** | | |
| discount_type | TINYINT | **V4.0**: 优惠计算类型: 1=FIXED满减, 2=PERCENTAGE折扣, 3=CASH_COUPON代金券 |
| coupon_category | TINYINT | **V4.0**: 券类别: 1=PLATFORM平台券, 2=SHOP店铺券, 3=FLASH_SALE秒杀券, 4=EXCLUSIVE独占券 |
| grant_type | TINYINT | **V4.0**: 获取方式: 1=FREE_CLAIM免费领, 2=PAID_PURCHASE付费购买, 3=INVITATION邀请, 4=AUTO_ISSUE自动发放 |
| stock_type | TINYINT | **V4.0**: 总量模型: 1=LIMITED限量, 2=UNLIMITED不限量 |
| grab_type | TINYINT | **V4.0**: 领取方式: 1=NORMAL普通, 2=NEED_GRAB需抢(Redis Lua), 3=PLATFORM_EXCLUSIVE平台独占 |
| price_in_cents | INT | **V4.0**: 付费券价格(分), grantType=PAID_PURCHASE时必填 |
| **V4.3 适用范围** | | |
| scope_type | TINYINT | **V4.3**: 适用范围: 1=ALL全场, 2=CATEGORY指定类目, 3=PRODUCT指定商品 (计划) |
| scope_values | JSON | **V4.3**: 适用范围值: {"categoryIds":[1,2,3]} 或 {"productIds":[101,102]} (计划) |
| stack_rule | TINYINT | **V4.3**: 叠加规则: 1=不可叠加, 2=可与其他券叠加, 3=可与活动叠加 (计划) |
| stack_group | VARCHAR(50) | **V4.3**: 叠加分组: 同组券互斥、跨组可叠加 (计划) |

**`user_coupon`** — 用户优惠券
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| coupon_id | BIGINT | FK→coupon.id |
| status | TINYINT | 1=未使用, 2=已使用, 3=已过期 |
| used_order_no | VARCHAR(32) | |
| use_time | DATETIME | |

### 7.3 V2.0 售后 & 入驻

**`after_sale`** — 售后申请
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| order_no | VARCHAR(32) | 唯一约束 `uk_order_user` |
| user_id | BIGINT | |
| type | TINYINT | 1=退货退款, 2=仅退款 |
| reason | VARCHAR(500) | |
| status | TINYINT | **AfterSaleStatusEnum 六态**：1=待审核 → 2=同意 →（4=已退货）→ 5=已退款 → 6=已完成；3=拒绝（分支态） |
| refund_amount | INT | |
| logistics_number | VARCHAR(50) | 退货物流单号 |

> **V5.5/V5.6 已建（买家退款资金流，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)/[ADR-037](../adr/037-freight-model-package-split-and-refund-trigger.md)）**：状态 5=已退款（REFUNDED）已激活——pay-service 退款成功后经 `PUT /internal/trade/after-sale/{id}/refunded` 条件回写（**来源 APPROVED(2) | RETURNED(4) → REFUNDED(5)**，0 行=幂等空操作）；退款处理中/失败时售后单停留 2=同意（APPROVED），失败单可重试。退款触发时机（ADR-037 纠正）：**仅退款（type=2）审核同意即触发；退货退款（type=1）在商家确认收到退货后触发**（`PUT /api/admin/after-sale/{id}/confirm-return`，APPROVED→RETURNED）。每张售后单至多一次退款（退款单 `uk_after_sale` 唯一约束兜底，天然幂等）。退款金额在申请时已校验 ≤ 订单实付（`payAmount`）。退款单见 §十二 12.1。

**`seller_application`** — 商家入驻申请 ⚠️ **deprecated（2026-10 第六轮）**

> trade-service 平行入驻流程已退役删除（SellerApplicationController / AdminApplicationController / ApplicationService / SellerApplicationEntity / Mapper；原因：与商家域入驻流程平行重复，且网关 `/api/seller/**`、`/api/admin/**` 均路由不到 trade，早已不可达）。表保留在库作数据备份，DDL 保留于 `V2.0__add_after_sale_application.sql`；现行入驻以 merchant-service `seller` 表为准（见 03-Domain-Model §3.3）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| shop_name / contact_phone | VARCHAR(100)/(20) | |
| province / city / district / detail_address | | |
| status | TINYINT | 0=待审核, 1=通过, 2=拒绝 |

### 7.4 V2.1 财务结算

**`settlement`** — 结算单
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| settlement_no | VARCHAR(32) UNIQUE | |
| seller_id | BIGINT | |
| period_start / period_end | DATE | 结算周期 |
| total_amount / commission / settlement_amount | INT | 订单总额/佣金(5%)/结算额（分） |
| status | TINYINT | 1=待结算, 2=已结算, 3=已打款 |

**`withdrawal`** — 提现申请
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| withdrawal_no | VARCHAR(32) UNIQUE | |
| seller_id | BIGINT | |
| amount | INT | 提现金额（分） |
| status | TINYINT | 1=待审核, 2=已打款, 3=拒绝 |
| bank_account / bank_name | VARCHAR(50)/(100) | |

> **归属说明（2026-10 增量核对）**：`withdrawal` 归 **trade-service 结算域，暂不迁 wallet-service**——提现单与结算单（`settlement`）属同一聚合族的级联：提现审核通过（`PAID_OUT`）会将卖家结算单级联置为已打款（`WithdrawalServiceImpl.review` → `SettlementService.markSellerSettlementsPaidOut`）；与 C 端用户余额（`wallet_account`，wallet-service）是不同业务。此前「提现是钱包域迁移候选」的判断作废。

### 7.5 V5.0 站内信（`notification`，notification-service 所有）

> 表名不变；2026-10 边界重构后所有权归 notification-service（触达域）。公开读写 `/api/notifications/**`；内部投递 `/internal/notify/in-app`（self-send 忽略，`sender_name` 由调用方传入、不再反查档案）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| recipient_id | BIGINT | 接收者用户ID |
| sender_id | BIGINT NULL | 发送者用户ID（NULL=系统） |
| sender_name | VARCHAR(100) | 发送者名称（用户名或店铺名，由调用方传入） |
| sender_avatar | VARCHAR(255) | 发送者头像 |
| type | VARCHAR(32) | LIKE / REPLY / APPEND / FOLLOW / ORDER / SYSTEM |
| title | VARCHAR(200) | 通知标题 |
| content | VARCHAR(500) | 通知摘要 |
| link_url | VARCHAR(500) | 跳转链接 |
| is_read | TINYINT | 0=未读, 1=已读 |
| create_time | DATETIME | |

索引: `idx_recipient_read` (`recipient_id`, `is_read`), `idx_recipient_time` (`recipient_id`, `create_time`), `idx_type` (`type`)

### 7.6 V5.3 钱包账户（`wallet_account`，wallet-service 所有）

> 资金域迁出 user 的落地表：存量自 `user.balance` 幂等迁移（唯一键冲突跳过）；`user.balance` 列保留冻结、代码不再读写。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | BIGINT UNIQUE NOT NULL | 用户ID（`uk_user_id`） |
| balance | INT NOT NULL DEFAULT 0 | 余额（分） |
| create_time / update_time | DATETIME | |

> **V5.5 已建关联（买家退款资金流，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）**：本域流水表 `wallet_transaction`（余额每笔变动留痕，幂等键 `transaction_no`；退款入账 = pay-service 余额渠道退款经 `POST /internal/balance/refund` 触发）——见 §十二 12.1。

---

## 八、RBAC 权限体系 (V3.0+)

### 8.1 sys_role（角色表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(64) NOT NULL | 角色名称 |
| code | VARCHAR(64) UNIQUE NOT NULL | 角色编码（ROLE_USER/ROLE_VIP/ROLE_SELLER/ROLE_ADMIN） |
| description | VARCHAR(256) | |
| status | TINYINT DEFAULT 1 | 1=启用 |
| is_system | TINYINT DEFAULT 0 | 1=系统角色(不可删除) |

### 8.2 sys_permission（权限表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(64) NOT NULL | 权限名称 |
| code | VARCHAR(128) UNIQUE NOT NULL | 权限编码（domain:action） |
| type | TINYINT DEFAULT 3 | 1=菜单, 2=按钮, 3=API |
| parent_id | BIGINT NULL | 父权限ID（菜单树） |
| path | VARCHAR(256) NULL | 资源路径 |
| sort_order | INT DEFAULT 0 | |

### 8.3 sys_user_role / sys_role_permission / sys_user_permission
用户-角色、角色-权限、用户-直接权限三张关联表（联合主键，FK 约束）。

---

## 九、AI 服务数据模型 (V2.5+)

> 状态（2026-10 核对）：`merchant_knowledge_base` **已建（V3.4 迁移）**，所有权归 ai-service（客服域，2026-10 迁入）；`ai_evaluation`、`ai_token_usage` 仍为计划表（未创建迁移文件）。

> AI 数据表为 V2.5 新增，支撑会话管理、消息持久化和知识库功能。完整设计见 [13-AI-Technology-Selection.md](./13-AI-Technology-Selection.md)。

### 9.1 会话与消息

**`ai_conversation`** — AI 对话会话

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| conversation_id | VARCHAR(36) UNIQUE NOT NULL | 会话业务ID (UUID) |
| user_id | BIGINT NOT NULL | 用户ID，FK → `user.id` |
| agent_type | VARCHAR(20) NOT NULL | Agent 类型: `SHOPPING` / `CUSTOMER_SERVICE` |
| title | VARCHAR(100) | 会话标题（取首条用户消息截断） |
| message_count | INT DEFAULT 0 | 消息总数 |
| status | TINYINT DEFAULT 1 | 1=活跃, 2=已归档, 3=已删除 |
| create_time | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME ON UPDATE CURRENT_TIMESTAMP | 最后更新时间 |

索引: `idx_user_agent` (`user_id`, `agent_type`), `idx_create_time` (`create_time`)

**`ai_message`** — 对话消息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| conversation_id | VARCHAR(36) NOT NULL | 会话ID，FK → `ai_conversation.conversation_id` |
| role | VARCHAR(10) NOT NULL | 角色: `USER` / `ASSISTANT` / `SYSTEM` / `TOOL` |
| content | TEXT NOT NULL | 消息内容 |
| tool_calls | JSON | Tool 调用记录: `[{toolName, input, output, latencyMs}]` |
| token_count | INT | Token 消耗估算 |
| create_time | DATETIME DEFAULT CURRENT_TIMESTAMP | 消息时间 |

索引: `idx_conversation_id` (`conversation_id`)

### 9.2 知识库 (V3.4 已建)

**`merchant_knowledge_base`** — 商家 AI 知识库

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| seller_id | BIGINT NOT NULL | 商家ID，FK → `seller.id` |
| title | VARCHAR(200) NOT NULL | 知识条目标题 |
| content | TEXT NOT NULL | 知识内容（FAQ/政策/产品说明） |
| category | VARCHAR(30) NOT NULL | 分类: `FAQ` / `POLICY` / `PRODUCT` / `ANNOUNCEMENT` |
| embedding_id | VARCHAR(64) | 向量索引归属标识，口径 **`es:{knowledgeId}`**（摄取成功后回填：该知识的 chunk 已入 search-service 的 `knowledge_vectors` 索引；稳定可反查、不随切片策略变化）；向量链路默认关闭（`ai.rag.enabled=false`）时保持 NULL（见 02-Architecture §3.4） |
| status | TINYINT DEFAULT 1 | 1=草稿, 2=已发布, 3=已归档 |
| create_time | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

索引: `idx_seller_category` (`seller_id`, `category`), `idx_status` (`status`)

### 8.3 评估与统计 (V3.0+)

**`ai_evaluation`** — AI 回答用户反馈

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| message_id | BIGINT NOT NULL | 消息ID，FK → `ai_message.id` |
| user_id | BIGINT NOT NULL | 用户ID |
| rating | TINYINT | 评分: 1-5 (5=非常满意) |
| feedback_type | VARCHAR(20) | 反馈类型: `HELPFUL` / `NOT_HELPFUL` / `HARMFUL` |
| feedback_text | VARCHAR(500) | 文字反馈 |
| create_time | DATETIME DEFAULT CURRENT_TIMESTAMP | 反馈时间 |

**`ai_token_usage`** — Token 用量统计（按日聚合）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| stat_date | DATE NOT NULL | 统计日期 |
| model | VARCHAR(30) NOT NULL | 模型: `deepseek-chat` / `qwen-max` |
| provider | VARCHAR(20) NOT NULL | Provider: `DEEPSEEK` / `QWEN` |
| agent_type | VARCHAR(20) | Agent 类型: `SHOPPING` / `CUSTOMER_SERVICE` |
| prompt_tokens | BIGINT DEFAULT 0 | 输入 Token 总量 |
| completion_tokens | BIGINT DEFAULT 0 | 输出 Token 总量 |
| total_tokens | BIGINT DEFAULT 0 | Token 总量 |
| request_count | INT DEFAULT 0 | 请求次数 |
| cost_cents | INT DEFAULT 0 | 预估费用（分） |

索引: `uk_date_model_agent` UNIQUE (`stat_date`, `model`, `agent_type`)

---

## 十、买家-商家消息数据模型（V3.3；2026-10 所有权归 ai-service）

> 表所有权（2026-10 边界重构）：`chat_message` 归 AI 客服域 ai-service（人工 IM 会话；STOMP `/ws/chat` + REST `/api/chat/**` 由其提供）。

### 10.1 chat_message（聊天消息）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| message_id | VARCHAR(36) UNIQUE NOT NULL | 消息业务ID (UUID) |
| conversation_id | VARCHAR(36) NOT NULL | 会话ID (buyer_{buyerId}_seller_{sellerId}_product_{productId}) |
| sender_id | BIGINT NOT NULL | 发送者用户ID |
| sender_role | VARCHAR(10) NOT NULL | BUYER / SELLER / AI |
| receiver_id | BIGINT NOT NULL | 接收者用户ID |
| content | TEXT NOT NULL | 消息内容 |
| content_type | VARCHAR(10) DEFAULT 'TEXT' | TEXT / IMAGE / PRODUCT_CARD / ORDER_CARD |
| extra | JSON NULL | 扩展数据（商品卡片 productId、订单卡片 orderNo） |
| is_read | TINYINT DEFAULT 0 | 0=未读, 1=已读 |
| is_ai_generated | TINYINT DEFAULT 0 | 0=人工, 1=AI 自动代答 |
| create_time | DATETIME NOT NULL | 发送时间 |

索引: `idx_conversation` (`conversation_id`), `idx_sender_receiver` (`sender_id`, `receiver_id`)

### 10.2 merchant_reply_template（商家回复模板）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | |
| seller_id | BIGINT NOT NULL | 商家ID，FK → `seller.id` |
| title | VARCHAR(200) NOT NULL | 模板标题 |
| content | TEXT NOT NULL | 模板内容 (变量: {buyer_name}, {product_name}, {order_no}) |
| category | VARCHAR(30) NOT NULL | GREETING / FAQ / OFFLINE / ORDER |
| sort_order | INT DEFAULT 0 | 排序 |
| status | TINYINT DEFAULT 1 | 1=启用, 0=禁用 |
| create_time | DATETIME NOT NULL | |
| update_time | DATETIME NOT NULL | |

索引: `idx_seller_category` (`seller_id`, `category`)

---

## 十一、商品详情页增强数据模型 (V3.5 已实现)

### 11.1 已落库的 product/sku/review 字段

**`product` 表已新增** (V3.5 migration)：`images` VARCHAR(2000)（多图，JSON 数组文本）/`video_url` VARCHAR(500)/`attributes` JSON（规格参数）/`service_tags` VARCHAR(500)（售后标签，JSON 数组文本）/`view_count` INT（浏览数）；V4.0 追加 `sales_tags` JSON（商品级销售标签）。
**`sku` 表已新增** (V3.5 migration)：`original_price` INT（原价，分；V4.0 另增 stock_type/is_hot/hot_reason VARCHAR(50)/sales_tags JSON）。
**`review` 表实际列**（V3.5 基线 + V5.0.2 增强，与早期文档"media_urls/is_anonymous/helpful_count"不符，以本节为准）：`sku_id`、`images` VARCHAR(500)（逗号分隔）、`reply`/`reply_time`（商家回复）、`append_content`/`append_media_urls`/`append_time`（追评）、`like_count`（点赞数）、`tags` VARCHAR(500)（评价标签 JSON 数组文本）；唯一约束 `uk_order_user(order_id, user_id)`。

### 11.2 已建表

**`product_view_log`** — 商品浏览记录 (user_id/product_id/view_time), idx_product_time
**`store_follow`** — 店铺关注：**无 MySQL 表**（实现为 Redis Set，非表；2026-10 第六轮随商家域归 merchant-service，私有 Key `shop:followers:{sellerId}` / `user:follow:shops:{userId}`）
**`sku_spec_dimension`** — SKU规格维度 (product_id/name/sort_order) (计划)
**`sku_spec_option`** — SKU规格选项 (dimension_id/value/image) (计划)

---

## 十二、V5.5/V5.6 新增表与变更（P0 四项，已建库）

> **已建（2026-10-03 落地同步）**：本节四组表已随 `V5.5__refund_wallet_tx_and_reconciliation.sql`（资金线）与 `V5.6__freight_template_and_package_split.sql`（履约线）迁移落库并实现——F1 退款资金流（ADR-035）、F4 渠道对账（ADR-036）、F2 运费模型 + F3 拆单发货（ADR-037）及退款触发纠正；正文与 §3.5/§3.7/§3.8/§3.10/§3.11 的"🆕 V5.6 规划"标注已收敛为"已建"。API 契约见 `05-API-Specification.md` §4.4.9 / §4.7.3 / §4.13.14 / §4.14.15-17（"规划中"标注由 product-manager 收口中）。
>
> **迁移批次**（数据库三段式版本号，与 05 契约版本 V5.6.0 是两套序列）：
>
> | 迁移批次 | 表/变更 | 迁移文件（已落地） |
> | --- | --- | --- |
> | **V5.5 资金线** | `refund_order`、`wallet_transaction`（F1）+ `reconciliation_batch`、`reconciliation_detail`（F4） | `V5.5__refund_wallet_tx_and_reconciliation.sql` |
> | **V5.6 履约线** | `freight_template` 新表 + `product.freight_template_id` / `orders.post_fee`（F2）+ `order_logistics` 主键与包裹改造、`order_item.package_no`（F3） | `V5.6__freight_template_and_package_split.sql` |
> | **V5.7 订单留言** | `orders.remark VARCHAR(200) NULL`（买家留言快照，T1/T6 请求增量） | `V5.7__order_remark.sql` |
> | **V5.8 促销与会员** | `promotion`（F5 满减/满折活动）+ `member_level_rule`、`member_growth_log`（F6 等级规则与成长值流水，含 seed） | `V5.8__promotion_and_member_level.sql` |
>
> ※ **2026-10-05 用户裁决：迁移文件机制废除**（[ADR-045](../adr/045-db-snapshot-single-source.md)）——`database/migrations/` 目录已删除（库无存量数据，无需升级路径），`Initialize.sql` 为唯一结构真相源，此后结构变更直接修改快照；上表批次与文件名仅作演进存档。
>
> 全部新表遵循 §一 约定：`INT` 分、`TINYINT` 状态枚举、审计列 `create_time`/`update_time`、`utf8mb4` + InnoDB、技术主键 `id AUTO_INCREMENT` + 业务唯一键（主键决策矩阵）。
>
> **V5.8 促销与会员（F5/F6，[ADR-043](../adr/043-pricing-center-and-promotion-engine.md)/[ADR-044](../adr/044-member-level-and-benefits.md)）**：①`promotion`——店铺级满减/满折活动（name/seller_id/type[1满减 2满折]/threshold/discount_value[满减=分；满折=万分比]/max_discount[满折封顶]/start_time/end_time/status[时间窗推导]/version）；②`member_level_rule`——等级规则（level/threshold 成长值门槛/discount_rate 万分比/points_rate 百分比/sort，seed：NORMAL 0/10000/100、SILVER 100000/9900/110、GOLD 500000/9800/120、PLATINUM 2000000/9500/150）；③`member_growth_log`——成长值入账流水（`uk_order_no` 幂等锚点，一单一次成长，只升不降）。计价口径见 §十二 12.5/12.6 与 ADR-043/044。

### 12.1 F1 买家退款资金流（V5.5 资金线迁移）

**`refund_order`** — 退款单（**归属 pay-service**；V5.5 新建，三通道原路退回，[ADR-035](../adr/035-refund-fund-flow-and-refunded-activation.md)）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| refund_no | CHAR(36) | UNIQUE (`uk_refund_no`), NOT NULL | 退款单号（业务唯一键，UUID；对外与幂等重试的定位键） |
| order_no | VARCHAR(64) | NOT NULL | 关联订单号（`orders.order_no`，无外键，与 `pay_order.biz_order_no` 同口径） |
| pay_order_no | VARCHAR(64) | NOT NULL | 关联支付单号（`pay_order.pay_order_no`） |
| after_sale_id | BIGINT | NOT NULL, UNIQUE (`uk_after_sale`) | 关联售后单（`after_sale.id`）——**一售后单一次退款**，唯一约束天然幂等：同 `afterSaleId` 重复请求返回既有退款单 |
| user_id | BIGINT | NOT NULL | 买家用户ID |
| channel | VARCHAR(16) | NOT NULL | 退款渠道：`WECHAT` / `ALIPAY` / `BALANCE`（与原支付渠道一致，原路退回） |
| refund_amount | INT | NOT NULL | 退款金额（分）；申请时已校验 ≤ 订单实付 `payAmount`（`PARAM_ERROR(1101)`） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-处理中，1-成功，2-失败（失败可经管理端重试） |
| channel_refund_no | VARCHAR(64) | NULL | 渠道退款单号（微信 `refund_id` 等，回调回填） |
| fail_reason | VARCHAR(255) | NULL | 失败原因（渠道返回或系统错误摘要，不含敏感细节） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_refund_no(refund_no)`、`uk_after_sale(after_sale_id)`、`idx_order_no(order_no)`（按订单查退款）

> **状态口径**：退款**处理中/失败**时售后单停留 `APPROVED(2)`（可重试）；仅**成功**回写售后单 `REFUNDED(5)`（trade 经 `PUT /internal/trade/after-sale/{id}/refunded`，**来源 `APPROVED(2) | RETURNED(4)` 条件迁移**，0 行=幂等空操作）。余额渠道：本地同步入账（`wallet_transaction` 幂等）；微信/支付宝渠道：异步回调确认（微信退款回调复用 ADR-033 验签+AES-GCM 解密口径与 ADR-029 裸 JSON 应答协议）。

**`wallet_transaction`** — 钱包流水（**归属 wallet-service**；余额每笔变动留痕，V5.5 新建，wallet 首次具备流水能力）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| transaction_no | CHAR(36) | UNIQUE (`uk_transaction_no`), NOT NULL | 流水号（**幂等键**，UUID；重复请求按唯一约束/先查判定，幂等成功） |
| user_id | BIGINT | NOT NULL | 用户ID（`wallet_account.user_id` 口径） |
| type | TINYINT | NOT NULL | 1-收入，2-支出 |
| amount | INT | NOT NULL | 变动金额（分，恒正；方向由 type 表达） |
| biz_no | VARCHAR(64) | NOT NULL | 业务关联号（退款流水=退款单号 `refund_no`；支出流水=订单号等） |
| remark | VARCHAR(100) | NULL | 摘要（如"售后退款"） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间（§一 审计列约定） |

索引：`uk_transaction_no(transaction_no)`、`idx_user_time(user_id, create_time)`（流水分页查询）

> **入账原子性**：入账与流水写入同事务（`wallet_account.balance` 增量 + `wallet_transaction` 插入）；`transaction_no` 冲突视为重复请求，顺序重放幂等返回成功（不重复加余额）；真并发撞唯一键时抛异常令本次「加余额 + 写流水」整体回滚（管理端重试即幂等收敛，资金不重复入账——有意取舍见 ADR-035「后果」）。

### 12.2 F2 运费模型（V5.6 履约线迁移）

**`freight_template`** — 运费模板（**归属 item-service** 商品域）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| template_no | CHAR(36) | UNIQUE (`uk_template_no`), NOT NULL | 模板业务编号（UUID） |
| seller_id | BIGINT | NOT NULL | 商家ID——**口径 = 商家 `user_id`**（与 `product.seller_id` 等下游口径一致，ADR-019 Phase A） |
| template_name | VARCHAR(50) | NOT NULL | 模板名称；同商家内唯一 `uk_seller_template(seller_id, template_name)` |
| charge_mode | TINYINT | NOT NULL, DEFAULT 1 | 计费方式：1-固定运费（**Phase 1 唯一值**，为 Phase 2 模板/重量计费预留） |
| fee | INT | NOT NULL | 固定运费（分，≥0） |
| free_threshold | INT | NOT NULL, DEFAULT 0 | 满额包邮阈值（分）；**0 = 不参与满额包邮**（>0 时订单商品总额达到该值则运费为 0） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1-启用，0-停用（停用模板不参与计价） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_template_no(template_no)`、`uk_seller_template(seller_id, template_name)`、`idx_seller_status(seller_id, status)`（商家默认模板解析：该商家**最早创建的启用模板**，`WHERE seller_id=? AND status=1 ORDER BY id ASC LIMIT 1`）

**既有表增量（同迁移批次）**：

| 表 | 变更 | 说明 |
| --- | --- | --- |
| `product` | 新增 `freight_template_id BIGINT NULL`，FK `fk_product_freight_template` → `freight_template.id` **ON DELETE SET NULL** | NULL/模板被删 → 回落商家默认模板；见 §3.5 |
| `orders` | 新增 `post_fee INT NOT NULL DEFAULT 0` | 下单时计算并**快照**（模板后续改价不影响已建订单）；见 §3.7 |

**计价规则（Phase 1 口径，与 05 §4.4.9/§4.6.1 契约同步）**：

1. **金额不变式变更（旧→新）**：
   - 旧：`payAmount = totalAmount − discountAmount`
   - 新：`payAmount = totalAmount − discountAmount + postFee`
   - `totalAmount` 仍 = Σ `order_item.subTotal`（不含运费）；`postFee` 单列快照。（02-Architecture §六 与 03-Domain-Model §四 已同步 V2 口径。）
2. **同单同商家**：跨店下单已被禁止（`CROSS_STORE_FORBIDDEN(3014)`），一单内商品同商家，运费计一次。
3. **取最高者计一次**：取订单内各商品**绑定模板**中 `fee` 最高者计一次运费（不按商品叠加）。
4. **满额包邮**：订单商品总额（`totalAmount`）≥ 该模板 `free_threshold`（>0）时 `postFee = 0`。
5. **默认模板回落**：商品未绑定模板（NULL）或绑定模板**已停用**时，用商家默认模板（最早创建的启用模板）；商家无任何启用模板时 `postFee = 0`（Phase 1 兜底，不阻断下单）。
6. 秒杀订单（orderType=3，无店铺归属）`postFee = 0`。

### 12.3 F3 拆单发货（V5.6 履约线迁移）

**`order_logistics` 改造**（1:1 → 1:N，存量库迁移含数据回填）：

| 变更 | DDL 要点 | 说明 |
| --- | --- | --- |
| 新增代理主键 | `id BIGINT AUTO_INCREMENT`，**PRIMARY KEY (id)**；`order_id` 转为**普通外键**（`fk_logistics_order` 关系保留；**不建 `uk_order_id` 单列唯一键**——1:N 语义下单列唯一会禁止一单多包裹，原稿该表述已纠错删除） | 收敛 §3.11「主键复用 order_id」约定偏差；一单多包裹后按 `package_no` 区分包裹 |
| 新增包裹号 | `package_no INT NOT NULL DEFAULT 1`；`UNIQUE KEY uk_order_package(order_id, package_no)` | 同一订单包裹号从 1 递增；**存量数据回填 `package_no = 1`** |
| 语义变更 | `order_id` 由「1:1 主键」变为「1:N 外键」 | 每次发货（包裹）插入一条物流记录，字段口径（快照列/状态机）不变 |

**`order_item` 增量**：

| 表 | 变更 | 说明 |
| --- | --- | --- |
| `order_item` | 新增 `package_no INT NULL` | 发货时标记明细归属包裹（对应 `order_logistics.package_no`）；NULL=未发货；**一明细只发一次**（重复发货同一 sku 被拒，`ORDER_STATUS_INVALID(3010)`）；`KEY idx_order_package(order_id, package_no)` 支撑按包裹聚合明细 |

> **订单状态口径**：部分发货订单保持"待发货(2)"；**全部明细**均有 `package_no` 后才迁移"待收货(3)"（不新增订单状态，复用 §九 状态机）。空 `skuIds` = 全量发货（单包裹语义兼容）。

### 12.4 F4 渠道对账（V5.5 资金线迁移）

**`reconciliation_batch`** — 对账批次（**归属 pay-service**）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| bill_date | DATE | NOT NULL | 账单日 |
| channel | VARCHAR(16) | NOT NULL | 对账渠道：`WECHAT` / `ALIPAY`（Phase 1 两渠道；余额渠道本地闭环不参与对账） |
| status | TINYINT | NOT NULL, DEFAULT 0 | 0-处理中，1-完成，2-失败 |
| channel_total | INT | NULL | 渠道账单总额（分，账单解析后回填） |
| local_total | INT | NULL | 本地支付成功总额（分，`pay_order.status=成功` 按日汇总） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_date_channel(bill_date, channel)`（**同日同渠道一批次**——重复执行先删后写的幂等锚点，并兜底并发重复触发）

**`reconciliation_detail`** — 对账差异明细（**归属 pay-service**）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| batch_id | BIGINT | FK → reconciliation_batch.id, NOT NULL | 所属批次 |
| pay_order_no | VARCHAR(64) | NOT NULL | 本地支付单号（比对键） |
| channel_txn_id | VARCHAR(64) | NULL | 渠道交易号（渠道账单有则回填） |
| channel_amount | INT | NULL | 渠道账单金额（分；渠道无此单为 NULL） |
| local_amount | INT | NULL | 本地金额（分；本地无此单为 NULL） |
| result | TINYINT | NOT NULL | 比对结果：1-匹配，2-渠道有本地无，3-本地有渠道无，4-金额不一致 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间（§一 审计列约定） |

索引：`idx_batch_result(batch_id, result)`（管理端按差异类型筛选）

> **Phase 1 口径**：差异仅**落库记录供管理端查看**（§4.13.14 AD44-AD46），**不自动调账**；日终任务由 XXL-Job 触发（dev 默认关闭），渠道账单下载经 ia-integration（Phase 1 mock 返回样例账单）。**重复执行同日对账幂等**：同 `(bill_date, channel)` 先删旧批次与明细再重写（`uk_date_channel` 兜底并发重复触发）。

### 12.5 F13 拼团（V5.14，[ADR-049](../adr/049-group-buy-full-loop.md)，schema 直改 Initialize.sql）

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `group_buy_activity` | seller_id / product_id / sku_id / group_price(分) / required_members(≥2) / time_limit_hours / start_time / end_time / status(1/2/3 时间窗推导) / version | 拼团活动（marketing 域，管理端 AD54-56） |
| `group_team` | team_no(UK) / activity_id / leader_user_id / required_members / current_members / status(1拼团中 2已成团 3已失败) / expire_time / success_time | 团（成团状态机；参团条件更新 +1，满员条件置已成团） |
| `group_team_member` | team_id / user_id / order_no(**UK**，幂等锚点：一单一团一次) | 团成员（uk_team_user 防同团重复参团） |
| `refund_order`（改造） | `after_sale_id` 改**可空** + 新增 `biz_ref VARCHAR(64) NOT NULL` + `uk_biz_ref` | 幂等锚点泛化（修订 ADR-035 §1）：售后退款 `AS:{id}`、拼团退款 `GB:{orderNo}`；`uk_after_sale` 保留 |

> 计价口径：拼团单不进价格计算中心（`discountAmount=0`，与秒杀同口径，ADR-043）；参团两段式（下单不占团位、支付成功后 I53 入团）与结团退款联动见 ADR-049。
