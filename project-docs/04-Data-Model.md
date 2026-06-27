# 04 — 数据模型文档

> 来源：`doc/项目的分析流程/04.数据建模.md`、`doc/项目代码/数据库/Initialize.sql.md`、`doc/项目的分析流程/03.领域建模.md`、`drawio/IA_E-R.drawio`

---

## 一、数据库设计约定

| 约定 | 说明 |
|------|------|
| **主键策略** | 见下方「主键决策矩阵」 |
| **金额** | 统一使用 `INT`，单位为「分」 |
| **布尔** | 使用 `TINYINT`，1-是 / 0-否 |
| **状态枚举** | 使用 `TINYINT` + 注释说明枚举值 |
| **审计字段** | 所有表包含 `create_time` 和 `update_time` |
| **快照模式** | 订单相关的用户/商品/地址信息在下单时复制到订单表，保证历史可追溯 |
| **乐观锁** | 并发写入热点表（orders、sku）使用 `version` INT 字段 + MyBatis-Plus `@Version`，防止丢失更新 |
| **字符集** | `utf8mb4` + `utf8mb4_unicode_ci` |
| **引擎** | InnoDB |
| **JSON 扩展** | 使用 `JSON` 列类型（`expand_json`）预留灵活性 |
| **逻辑删除** | 需要数据恢复/审计追溯的核心表（如 user、product、orders）使用 `deleted` 字段 + MyBatis-Plus `@TableLogic`；关联表/日志表不强制 |

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
  └──1:1─── seller
              │
              └──1:N─── product ───1:N─── sku
                           │                  │
                           │                  │
       ┌───────────────────┘                  │
       │                                      │
       ▼                                      ▼
    orders ───1:N─── order_item ───N:1─── (sku 快照)
       │
       ├──1:1─── order_logistics
       │
       └──1:1─── pay_order (通过 order_no 关联)
       
cart ───N:1─── user
cart ───N:1─── sku
```

---

## 三、表详细定义

### 3.1 user（用户表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 技术主键 |
| user_id | VARCHAR(32) | UNIQUE, NOT NULL | 业务唯一标识（UUID） |
| username | VARCHAR(50) | NULL | 用户名（可选） |
| phone | VARCHAR(20) | UNIQUE, NOT NULL | 手机号（登录账号） |
| password | VARCHAR(100) | NOT NULL | BCrypt 加密密码 |
| avatar | VARCHAR(255) | NULL | 头像 URL |
| register_time | DATETIME | NOT NULL | 注册时间（业务时间） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 1-正常，0-禁用 |
| balance | INT | NOT NULL, DEFAULT 0 | 余额（分） |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

索引：`uk_user_id`, `uk_phone`

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

### 3.3 seller（商家表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 技术主键 |
| user_id | BIGINT | FK → user.id, UNIQUE, NOT NULL | 关联用户（1:1） |
| shop_name | VARCHAR(100) | NOT NULL | 店铺名称 |
| shop_logo | VARCHAR(255) | NULL | 店铺 logo |
| contact_phone | VARCHAR(20) | NOT NULL | 联系电话 |
| province/city/district | VARCHAR(50) | NOT NULL | 发货地址 |
| detail_address | VARCHAR(200) | NOT NULL | 详细地址 |
| status | TINYINT | DEFAULT 0 | 0-审核中，1-正常，2-冻结 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

外键：`fk_seller_user` → user.id ON DELETE RESTRICT

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
| description | TEXT | NULL | 图文描述（HTML） |
| brand | VARCHAR(100) | NULL | 品牌 |
| sold_count | INT | DEFAULT 0 | 总销量 |
| comment_count | INT | DEFAULT 0 | 总评论数 |
| is_ad | TINYINT | DEFAULT 0 | 广告商品标记 |
| status | TINYINT | DEFAULT 1 | 1-上架，2-下架，3-删除 |
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
| stock | INT | NOT NULL, DEFAULT 0 | 库存数量 |
| image | VARCHAR(255) | NULL | 规格专属图片 |
| sold_count | INT | DEFAULT 0 | 该规格销量 |
| status | TINYINT | DEFAULT 1 | 1-可售，0-停售 |
| version | INT | NOT NULL, DEFAULT 0 | 乐观锁版本号 |
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
| total_amount | INT | NOT NULL | 总金额（分） |
| pay_amount | INT | NOT NULL | 实付金额（分） |
| discount_amount | INT | NOT NULL, DEFAULT 0 | 优惠金额（分） |
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

### 3.11 order_logistics（订单物流表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| order_id | BIGINT | PK + FK → orders.id | 订单 ID（1:1） |
| logistics_number | VARCHAR(50) | NOT NULL | 物流单号 |
| logistics_company | VARCHAR(50) | NOT NULL | 物流公司 |
| contact | VARCHAR(50) | NOT NULL | 收件人快照 |
| mobile | VARCHAR(20) | NOT NULL | 电话快照 |
| province/city/district/street | VARCHAR | NOT NULL | 地址快照 |
| detail | VARCHAR(200) | NOT NULL | 详细地址快照 |
| create_time | DATETIME | NOT NULL | |
| update_time | DATETIME | NOT NULL | |

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
- `orders` ↔ `order_logistics`：一对一
- `cart` → `user` + `sku`：多对一

---

## 七、V1.1+ 扩展表

### 7.1 V1.1 积分 & 评价 & 收藏

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

**`home_config`** — 首页配置
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| slot | VARCHAR(30) | 位置(banner/hot/new/sale) |
| type | TINYINT | 1=商品组, 2=广告图, 3=活动链接 |
| product_ids | VARCHAR(500) | 商品ID CSV |
| image / link_url | VARCHAR(255) | |
| sort_order | INT | |

**`operation_log`** — 操作审计日志
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | |
| action | VARCHAR(50) | |
| target / detail | VARCHAR(100)/(500) | |
| ip | VARCHAR(45) | |
| create_time | DATETIME | |

**`coupon`** — 优惠券模板（V1.1 迁移时加入，V1.2 添加 seller_id）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| coupon_id | VARCHAR(32) UNIQUE | 业务标识 |
| name | VARCHAR(100) | |
| type | TINYINT | 1=满减, 2=折扣 |
| value | INT | 满减金额(分) / 折扣百分比 |
| min_amount | INT | 最低消费(分) |
| seller_id | BIGINT | NULL=平台券, 非NULL=店铺券 |
| total_qty / issued_qty | INT | |
| start_time / end_time | DATETIME | |

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
| status | TINYINT | 1=待审核→2=同意→4=已退货→5=已退款 |
| refund_amount | INT | |
| logistics_number | VARCHAR(50) | 退货物流单号 |

**`seller_application`** — 商家入驻申请
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
