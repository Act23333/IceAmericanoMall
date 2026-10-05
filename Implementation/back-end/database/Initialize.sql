-- =====================================================================
-- IceAmericanoMall — 数据库【全量最新快照】建库脚本（Initialize.sql）
-- =====================================================================
--
-- 【文件语义】（定稿，改动前必读）
--   (a) 本文件是**全量最新快照**：新装库只需执行本文件即可得到与全部迁移依次
--       执行后**完全相同**的终态 schema + 种子数据。不要再在新建库上执行
--       migrations/ 下的脚本（会对已存在的列/表重复操作而报错）。
--   (b) migrations/ 是**历史库的逐版升级路径**：仅用于把已存在的旧库按版本顺序
--       升级到最新终态。两条路径必须指向同一终态；若修改任一侧，必须同步另一侧。
--   (c) 升级顺序要求：按版本号升序逐个执行（文件名前缀即版本号），完整顺序为
--       V1.1.0 → V1.1.1 → V1.1.2 → V1.1.3 → V1.2.0 → V1.2.1 → V1.2.2 → V2.0 →
--       V2.1 → V2.2 → V2.5.0 → V2.5.1 → V3.0 → V3.2 → V3.3 → V3.4 → V3.5 →
--       V3.7 → V4.0 → V4.1 → V4.3 → V5.0.0 → V5.0.1 → V5.0.2 → V5.1 → V5.2 →
--       V5.3 → V5.4 → V5.5 → V5.6 → V5.7 → V5.8。中断重跑前请先确认目标对象是否已创建/修改（多数 DDL 非幂等）。
--       ※ 2026-10-05 起：库无存量数据，迁移文件机制废除（ADR-045）——结构变更直接修改本文件，本版本链仅作演进存档。
--
-- 【设计说明】（大厂规范约定，代码/文档需与此保持一致）
--   1) 软删除策略：当前**未启用** MyBatis-Plus `@TableLogic`，也没有统一的
--      `deleted` 标志列。可恢复数据由**状态列**承担软删除语义：
--        user.status(0 禁用) / product.status(3 删除) / seller.status /
--        shop.status / coupon.status / user_auth.status(0 已解绑)
--      明确的**物理删除**场景（删除即不可恢复，属用户主动意愿）：
--        cart（购物车条目）、favorite（取消收藏）、address（用户主动删除）。
--      订单及支付/物流/售后/结算等**事实数据禁止物理删除**，仅通过状态机推进
--        （orders.status：待付款→待发货→待收货→已完成/已取消→待评价）。
--   2) 跨服务外键（FK）为**单库过渡设计**：当前 48 张表同处一个 schema（V5.5 资金线新增
--      refund_order / wallet_transaction / reconciliation_batch / reconciliation_detail；
--      V5.6 履约线新增 freight_template）
--      （icedamericano_mall），保留 FK 以保障存量数据完整性。微服务化（分库）后
--      FK 必须移除，改由**服务边界 + 最终一致性**（领域事件 / MQ）保证；服务间
--      内部调用统一走 `/internal/**` 接口（该路径异常直传，不包 Result）。
--   3) 金额一律 `INT`（单位：**分**），禁止 float / double / BigDecimal；
--      布尔用 TINYINT(1/0)；状态枚举用 TINYINT + 注释列明取值。
--   4) 每张表以 `BIGINT AUTO_INCREMENT` 为技术主键 `id`，业务标识另立
--      `xxx_id`（UUID）/ `xxx_no`（可读流水号）。原 order_logistics「主键复用 order_id」的
--      例外已在 V5.6 收敛为代理主键 id（order_id 降为 UNIQUE 键，见其表注释）。
--
-- 适用数据库：MySQL 8.0+（推荐 8.4；语法不兼容 MySQL 5.7 之外的 MariaDB 扩展）
-- 字符集/引擎：utf8mb4 + utf8mb4_unicode_ci / InnoDB（全表统一）
-- =====================================================================

-- 数据库创建（按需执行；脚本默认在当前库内建表）
--   CREATE DATABASE IF NOT EXISTS icedamericano_mall
--       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 禁用外键检查，避免删表/建表时因外键约束顺序报错
SET FOREIGN_KEY_CHECKS = 0;

-- 强制客户端使用 utf8mb4，防止中文注释乱码
SET NAMES utf8mb4;

-- =====================================================================
-- 一、删除所有表（外键检查已禁用，顺序无关）
-- =====================================================================
DROP TABLE IF EXISTS `review_like`;
DROP TABLE IF EXISTS `review`;
DROP TABLE IF EXISTS `favorite`;
DROP TABLE IF EXISTS `product_view_log`;
DROP TABLE IF EXISTS `order_logistics`;
DROP TABLE IF EXISTS `pay_order`;
DROP TABLE IF EXISTS `reconciliation_detail`;
DROP TABLE IF EXISTS `reconciliation_batch`;
DROP TABLE IF EXISTS `refund_order`;
DROP TABLE IF EXISTS `cart`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `sku`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `freight_template`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `merchant_reply_template`;
DROP TABLE IF EXISTS `merchant_knowledge_base`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `ai_message`;
DROP TABLE IF EXISTS `ai_conversation`;
DROP TABLE IF EXISTS `shop`;
DROP TABLE IF EXISTS `seller`;
DROP TABLE IF EXISTS `seller_application`;
DROP TABLE IF EXISTS `address`;
DROP TABLE IF EXISTS `user_auth`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `wallet_transaction`;
DROP TABLE IF EXISTS `wallet_account`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `points_log`;
DROP TABLE IF EXISTS `user_coupon`;
DROP TABLE IF EXISTS `coupon`;
DROP TABLE IF EXISTS `settlement`;
DROP TABLE IF EXISTS `withdrawal`;
DROP TABLE IF EXISTS `after_sale`;
DROP TABLE IF EXISTS `flash_order_log`;
DROP TABLE IF EXISTS `flash_sale`;
DROP TABLE IF EXISTS `home_config`;
DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `sys_user_permission`;
DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;

-- =====================================================================
-- 二、核心业务表（初始基线 11 表 + V5.4 shop + V5.6 freight_template，已合并各版本 ALTER 的终态列）
-- =====================================================================

-- ---------------------------------------------------------------
-- user（用户资料表）— 终态含 V2.2 / V2.5 / V3.2 / V5.2 全部增量
--   凭证（密码/微信）归 user_auth（V5.1）；角色归 RBAC 五表；余额归 wallet_account（V5.3）
--   带 deprecated 标记的列已停止读写，观察期后另行 DDL 删列
-- ---------------------------------------------------------------
CREATE TABLE `user` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `user_id` VARCHAR(32) NOT NULL COMMENT '业务唯一标识（UUID）',
                        `username` VARCHAR(50) DEFAULT NULL COMMENT '昵称（V5.2 起为展示字段，可重复）',
                        `phone` VARCHAR(20) DEFAULT NULL COMMENT 'deprecated V5.2：手机号存 phone_cipher/phone_hash/phone_mask，迁移后应为 NULL',
                        `phone_cipher` VARCHAR(512) DEFAULT NULL COMMENT '手机号密文（AES-256-GCM，Base64，V5.2）',
                        `phone_hash` CHAR(64) DEFAULT NULL COMMENT '手机号盲索引（HMAC-SHA256 十六进制，V2.5）',
                        `phone_mask` VARCHAR(64) DEFAULT NULL COMMENT '手机号脱敏展示值（V5.2）',
                        `email_cipher` VARCHAR(512) DEFAULT NULL COMMENT '邮箱密文（AES-256-GCM，Base64，V5.2）',
                        `email_hash` VARCHAR(128) DEFAULT NULL COMMENT '邮箱盲索引（HMAC-SHA256，V5.2）',
                        `email_mask` VARCHAR(64) DEFAULT NULL COMMENT '邮箱脱敏展示值（V5.2）',
                        `password` VARCHAR(100) DEFAULT NULL COMMENT 'deprecated V5.2：密码凭证归 user_auth.credential，观察期后删列',
                        `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
                        `gender` TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女（V5.2）',
                        `birthday` DATE DEFAULT NULL COMMENT '生日（V5.2）',
                        `wx_openid` VARCHAR(128) DEFAULT NULL COMMENT 'deprecated V5.2：微信身份归 user_auth.identifier_plain，观察期后删列',
                        `member_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '会员等级（NORMAL/SILVER/GOLD/PLATINUM，V5.2）',
                        `register_time` DATETIME NOT NULL COMMENT '注册时间（审计列；领域语义由 create_time 承担）',
                        `last_login_time` DATETIME DEFAULT NULL COMMENT 'deprecated V5.2：登录链路已离开 user 上下文，观察期后删列',
                        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
                        `balance` INT NOT NULL DEFAULT 0 COMMENT 'deprecated（2026-10）：余额迁至 wallet_account（V5.3），列冻结不再读写',
                        `role_type` TINYINT NOT NULL DEFAULT 0 COMMENT 'deprecated V5.2：角色唯一权威为 RBAC 五表（sys_user_role），观察期后删列',
                        `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（V3.2 @Version）',
                        `create_time` DATETIME NOT NULL COMMENT '记录创建时间',
                        `update_time` DATETIME NOT NULL COMMENT '最后更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_user_id` (`user_id`),
                        UNIQUE KEY `uk_phone_hash` (`phone_hash`),
                        UNIQUE KEY `uk_email_hash` (`email_hash`),
                        UNIQUE KEY `uk_wx_openid` (`wx_openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表（资料域）';

-- ---------------------------------------------------------------
-- address（收货地址表）
-- ---------------------------------------------------------------
CREATE TABLE `address` (
                           `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                           `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
                           `receiver` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
                           `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                           `province` VARCHAR(50) NOT NULL COMMENT '省',
                           `city` VARCHAR(50) NOT NULL COMMENT '市',
                           `district` VARCHAR(50) NOT NULL COMMENT '区/县',
                           `street` VARCHAR(100) NOT NULL COMMENT '街道/镇',
                           `detail` VARCHAR(200) NOT NULL COMMENT '详细地址（门牌号等）',
                           `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认地址：1-是，0-否',
                           `label` VARCHAR(20) DEFAULT NULL COMMENT '地址标签（如“家”、“公司”）',
                           `longitude` DECIMAL(10,7) DEFAULT NULL COMMENT '经度（可选）',
                           `latitude` DECIMAL(10,7) DEFAULT NULL COMMENT '纬度（可选）',
                           `create_time` DATETIME NOT NULL COMMENT '创建时间',
                           `update_time` DATETIME NOT NULL COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`),
                           CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- ---------------------------------------------------------------
-- seller（商家表：商家身份 + 生命周期）— 终态为 V5.4 瘦身后的形态
--   店铺资料已迁至 shop（1 商家 : N 店铺，ADR-019）
-- ---------------------------------------------------------------
CREATE TABLE `seller` (
                          `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                          `user_id` BIGINT NOT NULL COMMENT '关联用户ID（1:1）',
                          `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-审核中，1-正常，2-冻结',
                          `create_time` DATETIME NOT NULL COMMENT '创建时间',
                          `update_time` DATETIME NOT NULL COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_id` (`user_id`),
                          CONSTRAINT `fk_seller_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家表（商家身份+生命周期）';

-- ---------------------------------------------------------------
-- shop（店铺表：1 商家 : N 店铺）— V5.4 新增
-- ---------------------------------------------------------------
CREATE TABLE `shop` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `merchant_id` BIGINT NOT NULL COMMENT '所属商家ID（seller.id）',
                        `shop_name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
                        `shop_logo` VARCHAR(255) DEFAULT NULL COMMENT '店铺logo',
                        `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                        `province` VARCHAR(50) NOT NULL COMMENT '发货省',
                        `city` VARCHAR(50) NOT NULL COMMENT '发货市',
                        `district` VARCHAR(50) NOT NULL COMMENT '发货区/县',
                        `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
                        `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-审核中，1-正常，2-冻结',
                        `create_time` DATETIME NOT NULL COMMENT '创建时间',
                        `update_time` DATETIME NOT NULL COMMENT '更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_merchant_shop_name` (`merchant_id`, `shop_name`),
                        KEY `idx_merchant_status` (`merchant_id`, `status`),
                        CONSTRAINT `fk_shop_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- ---------------------------------------------------------------
-- category（商品类目表，支持多级分类）
-- ---------------------------------------------------------------
CREATE TABLE `category` (
                            `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                            `name` VARCHAR(50) NOT NULL COMMENT '类目名称',
                            `parent_id` BIGINT DEFAULT NULL COMMENT '父类目ID（顶级为NULL）',
                            `level` TINYINT NOT NULL COMMENT '层级：1-一级，2-二级，3-三级',
                            `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
                            `create_time` DATETIME NOT NULL COMMENT '创建时间',
                            `update_time` DATETIME NOT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_parent_id` (`parent_id`),
                            CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品类目表';

-- ---------------------------------------------------------------
-- freight_template（运费模板）— 归属 item-service 商品域（V5.6 履约线 F2）
--   与 migrations/V5.6__freight_template_and_package_split.sql 逐列一致
--   seller_id 口径 = 商家 user_id（ADR-019 Phase A）；同商家内模板名唯一
--   fee / free_threshold 单位分；charge_mode=1 固定运费（Phase 1 唯一值）
--   默认模板 = 该商家最早创建的启用模板（WHERE seller_id=? AND status=1 ORDER BY id ASC LIMIT 1）
-- ---------------------------------------------------------------
CREATE TABLE `freight_template` (
    `id`             BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `template_no`    CHAR(36)    NOT NULL COMMENT '模板业务编号（UUID）',
    `seller_id`      BIGINT      NOT NULL COMMENT '商家ID（口径 = 商家 user_id）',
    `template_name`  VARCHAR(50) NOT NULL COMMENT '模板名称（同商家内唯一）',
    `charge_mode`    TINYINT     NOT NULL DEFAULT 1 COMMENT '计费方式：1-固定运费（Phase 1 唯一值）',
    `fee`            INT         NOT NULL COMMENT '固定运费（分，≥0）',
    `free_threshold` INT         NOT NULL DEFAULT 0 COMMENT '满额包邮阈值（分）；0 = 不参与满额包邮',
    `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-停用（停用模板不参与计价）',
    `create_time`    DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_no` (`template_no`),
    UNIQUE KEY `uk_seller_template` (`seller_id`, `template_name`),
    KEY `idx_seller_status` (`seller_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运费模板（item-service 商品域）';

-- ---------------------------------------------------------------
-- product（商品表 / SPU）— 终态含 V3.5 详情页字段；sales_tags 为 JSON（V4.0）
-- ---------------------------------------------------------------
CREATE TABLE `product` (
                           `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                           `product_id` VARCHAR(32) NOT NULL COMMENT '业务商品编号',
                           `seller_id` BIGINT NOT NULL COMMENT '所属商家ID',
                           `category_id` BIGINT NOT NULL COMMENT '所属类目ID',
                           `freight_template_id` BIGINT DEFAULT NULL COMMENT '绑定运费模板ID（V5.6，NULL=回落商家默认模板；模板删除经 ON DELETE SET NULL 回落）',
                           `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
                           `main_image` VARCHAR(255) DEFAULT NULL COMMENT '主图URL（列表页封面）',
                           `images` VARCHAR(2000) DEFAULT NULL COMMENT '多图URL, JSON数组（详情页轮播，V3.5）',
                           `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL（V3.5）',
                           `attributes` JSON DEFAULT NULL COMMENT '规格参数, K-V对（V3.5）',
                           `service_tags` VARCHAR(500) DEFAULT NULL COMMENT '售后标签, JSON数组 ["7天退换","正品保证"]（V3.5）',
                           `sales_tags` JSON DEFAULT NULL COMMENT '商品级销售标签, JSON数组（V4.0 升级为 JSON）',
                           `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数（V3.5）',
                           `description` TEXT COMMENT '图文描述（HTML）',
                           `brand` VARCHAR(100) DEFAULT NULL COMMENT '品牌',
                           `sold_count` INT NOT NULL DEFAULT 0 COMMENT '总销量',
                           `comment_count` INT NOT NULL DEFAULT 0 COMMENT '总评论数',
                           `is_ad` TINYINT NOT NULL DEFAULT 0 COMMENT '是否广告商品：1-是，0-否',
                           `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-上架，2-下架，3-删除',
                           `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
                           `create_time` DATETIME NOT NULL COMMENT '创建时间',
                           `update_time` DATETIME NOT NULL COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uk_product_id` (`product_id`),
                           KEY `idx_seller_id` (`seller_id`),
                           KEY `idx_category_id` (`category_id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_seller_status` (`seller_id`, `status`),
                           CONSTRAINT `fk_product_seller` FOREIGN KEY (`seller_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                           CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                           CONSTRAINT `fk_product_freight_template` FOREIGN KEY (`freight_template_id`) REFERENCES `freight_template` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表（SPU）';

-- ---------------------------------------------------------------
-- sku（规格表 / SKU）— 终态含 V3.5 original_price + V4.0 库存类型/热销元数据
--   hot_reason 取值受 HotReasonEnum 白名单归一：discount/new_arrival/best_seller/clearance
-- ---------------------------------------------------------------
CREATE TABLE `sku` (
                       `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                       `sku_id` VARCHAR(32) NOT NULL COMMENT '业务SKU编码',
                       `product_id` BIGINT NOT NULL COMMENT '所属商品ID',
                       `spec` VARCHAR(200) NOT NULL COMMENT '规格描述（如“黑色 128G”）',
                       `price` INT NOT NULL COMMENT '价格（单位：分）',
                       `original_price` INT DEFAULT NULL COMMENT '原价（分），划线价（V3.5）',
                       `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
                       `stock_type` TINYINT NOT NULL DEFAULT 1 COMMENT '库存类型：1=LIMITED限量, 2=UNLIMITED不限量, 3=PRESALE预售（V4.0）',
                       `is_hot` TINYINT NOT NULL DEFAULT 0 COMMENT '热销标识：1=是，0=否（V4.0）',
                       `hot_reason` VARCHAR(50) DEFAULT NULL COMMENT '热销原因: discount/new_arrival/best_seller/clearance（V4.0，HotReasonEnum 归一）',
                       `sales_tags` JSON DEFAULT NULL COMMENT '销售标签, JSON数组 ["限时优惠","新品"]（V4.0）',
                       `image` VARCHAR(255) DEFAULT NULL COMMENT '规格专属图片',
                       `sold_count` INT NOT NULL DEFAULT 0 COMMENT '该规格销量',
                       `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-可售，0-停售',
                       `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                       `create_time` DATETIME NOT NULL COMMENT '创建时间',
                       `update_time` DATETIME NOT NULL COMMENT '更新时间',
                       PRIMARY KEY (`id`),
                       UNIQUE KEY `uk_sku_id` (`sku_id`),
                       KEY `idx_product_id` (`product_id`),
                       KEY `idx_status` (`status`),
                       CONSTRAINT `fk_sku_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规格表（SKU）';

-- ---------------------------------------------------------------
-- orders（订单表）— 终态含 V4.1 order_type
-- ---------------------------------------------------------------
CREATE TABLE `orders` (
                         `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                         `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（业务唯一）',
                         `user_id` BIGINT NOT NULL COMMENT '买家ID',
                         `seller_id` BIGINT NOT NULL COMMENT '商家ID',
                         `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型: 1=NORMAL购物车, 2=DIRECT立即购买, 3=FLASH_SALE秒杀, 4=PRESALE预售（V4.1）',
                         `total_amount` INT NOT NULL COMMENT '订单总金额（分）',
                         `pay_amount` INT NOT NULL COMMENT '实付金额（分）',
                         `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
                         `post_fee` INT NOT NULL DEFAULT 0 COMMENT '运费（分），下单时按运费模板计算并快照（V5.6）',
                         `remark` VARCHAR(200) NULL COMMENT '买家留言',
                         `status` TINYINT NOT NULL COMMENT '状态：1-待付款，2-待发货，3-待收货，4-已完成，5-已取消，6-待评价',
                         `payment_type` TINYINT DEFAULT NULL COMMENT '支付类型：1-支付宝，2-微信，3-余额',
                         `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名（快照）',
                         `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话（快照）',
                         `receiver_address` VARCHAR(500) NOT NULL COMMENT '收货地址（快照，完整拼接）',
                         `create_time` DATETIME NOT NULL COMMENT '下单时间',
                         `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
                         `consign_time` DATETIME DEFAULT NULL COMMENT '发货时间',
                         `end_time` DATETIME DEFAULT NULL COMMENT '交易完成时间',
                         `close_time` DATETIME DEFAULT NULL COMMENT '交易关闭时间',
                         `comment_time` DATETIME DEFAULT NULL COMMENT '评价时间',
                         `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                         `update_time` DATETIME NOT NULL COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_order_no` (`order_no`),
                         KEY `idx_user_id` (`user_id`),
                         KEY `idx_seller_id` (`seller_id`),
                         KEY `idx_status` (`status`),
                         KEY `idx_user_status` (`user_id`, `status`),
                         CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT `fk_order_seller` FOREIGN KEY (`seller_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ---------------------------------------------------------------
-- order_item（订单项表，下单快照）
-- ---------------------------------------------------------------
CREATE TABLE `order_item` (
                              `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                              `order_id` BIGINT NOT NULL COMMENT '所属订单ID',
                              `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
                              `product_name` VARCHAR(200) NOT NULL COMMENT '商品名快照',
                              `sku_spec` VARCHAR(200) NOT NULL COMMENT '规格快照',
                              `price` INT NOT NULL COMMENT '单价快照（分）',
                              `quantity` INT NOT NULL COMMENT '购买数量',
                              `sub_total` INT NOT NULL COMMENT '小计（分）',
                              `image` VARCHAR(255) DEFAULT NULL COMMENT '图片快照',
                              `package_no` INT DEFAULT NULL COMMENT '归属包裹号（V5.6；NULL=未发货，对应 order_logistics.package_no）',
                              `create_time` DATETIME NOT NULL COMMENT '创建时间',
                              `update_time` DATETIME NOT NULL COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_order_id` (`order_id`),
                              KEY `idx_sku_id` (`sku_id`),
                              KEY `idx_order_package` (`order_id`, `package_no`),
                              CONSTRAINT `fk_orderitem_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
                              CONSTRAINT `fk_orderitem_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- ---------------------------------------------------------------
-- cart（购物车表）— (user_id, sku_id) 唯一去重
-- ---------------------------------------------------------------
CREATE TABLE `cart` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `user_id` BIGINT NOT NULL COMMENT '用户ID',
                        `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
                        `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
                        `selected` TINYINT NOT NULL DEFAULT 1 COMMENT '是否选中：1-是，0-否',
                        `create_time` DATETIME NOT NULL COMMENT '加入时间',
                        `update_time` DATETIME NOT NULL COMMENT '更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`), -- 避免同一用户重复添加同一SKU
                        KEY `idx_user_id` (`user_id`),
                        KEY `idx_sku_id` (`sku_id`),
                        CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
                        CONSTRAINT `fk_cart_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- ---------------------------------------------------------------
-- pay_order（支付订单表）
-- ---------------------------------------------------------------
CREATE TABLE `pay_order` (
                             `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                             `biz_order_no` VARCHAR(32) NOT NULL COMMENT '业务订单号（关联 orders.order_no）',
                             `pay_order_no` VARCHAR(32) NOT NULL COMMENT '支付单号',
                             `biz_user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
                             `pay_channel_code` VARCHAR(20) NOT NULL COMMENT '支付渠道代码',
                             `amount` INT NOT NULL COMMENT '支付金额（分）',
                             `pay_type` TINYINT NOT NULL COMMENT '支付类型：1-h5，2-小程序，3-公众号，4-扫码，5-余额',
                             `status` TINYINT NOT NULL COMMENT '状态：0-待提交，1-待支付，2-超时取消，3-成功',
                             `expand_json` JSON DEFAULT NULL COMMENT '扩展字段',
                             `result_code` VARCHAR(50) DEFAULT NULL COMMENT '第三方返回码',
                             `result_msg` VARCHAR(200) DEFAULT NULL COMMENT '第三方返回信息',
                             `pay_success_time` DATETIME DEFAULT NULL COMMENT '支付成功时间',
                             `pay_over_time` DATETIME DEFAULT NULL COMMENT '支付超时时间',
                             `qr_code_url` VARCHAR(255) DEFAULT NULL COMMENT '支付二维码链接',
                             `create_time` DATETIME NOT NULL COMMENT '创建时间',
                             `update_time` DATETIME NOT NULL COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_pay_order_no` (`pay_order_no`),
                             KEY `idx_biz_order_no` (`biz_order_no`),
                             KEY `idx_biz_user_id` (`biz_user_id`),
                             KEY `idx_status` (`status`),
                             CONSTRAINT `fk_payorder_user` FOREIGN KEY (`biz_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';

-- ---------------------------------------------------------------
-- order_logistics（订单物流表）— 终态含 V1.1.1 status + V5.6 拆单发货（1:1 → 1:N）
--   V5.6 收敛此前「主键复用 order_id」的约定偏差（D4）：
--     ① 代理主键 id BIGINT AUTO_INCREMENT；order_id 降为普通外键列（不建单列唯一键 ——
--        单列 UNIQUE 会禁止一单多包裹，与 1:N 语义冲突）；
--     ② 新增 package_no INT NOT NULL DEFAULT 1，唯一键 uk_order_package(order_id, package_no)；
--     ③ 语义由「一单一物流」升级为「一单一包裹一条物流记录」（存量数据 package_no=1）。
--   order_id 上的索引由 uk_order_package 前缀承担，外键 fk_logistics_order 关系不变。
-- ---------------------------------------------------------------
CREATE TABLE `order_logistics` (
                                   `id` BIGINT AUTO_INCREMENT COMMENT '技术主键（V5.6）',
                                   `order_id` BIGINT NOT NULL COMMENT '订单ID（一单多包裹，按 package_no 区分）',
                                   `package_no` INT NOT NULL DEFAULT 1 COMMENT '包裹号（同一订单从 1 递增，V5.6）',
                                   `logistics_number` VARCHAR(50) NOT NULL COMMENT '物流单号',
                                   `logistics_company` VARCHAR(50) NOT NULL COMMENT '物流公司名称',
                                   `contact` VARCHAR(50) NOT NULL COMMENT '收件人（快照）',
                                   `mobile` VARCHAR(20) NOT NULL COMMENT '电话（快照）',
                                   `province` VARCHAR(50) NOT NULL COMMENT '省（快照）',
                                   `city` VARCHAR(50) NOT NULL COMMENT '市（快照）',
                                   `district` VARCHAR(50) NOT NULL COMMENT '区/县（快照）',
                                   `street` VARCHAR(100) NOT NULL COMMENT '街道（快照）',
                                   `detail` VARCHAR(200) NOT NULL COMMENT '详细地址（快照）',
                                   `status` INT NOT NULL DEFAULT 1 COMMENT '物流状态：1-待揽收, 2-运输中, 3-已签收, 4-已退回（V1.1.1）',
                                   `create_time` DATETIME NOT NULL COMMENT '创建时间',
                                   `update_time` DATETIME NOT NULL COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_order_package` (`order_id`, `package_no`),
                                   KEY `idx_logistics_number` (`logistics_number`),
                                   CONSTRAINT `fk_logistics_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单物流表（一单一包裹一条记录）';

-- =====================================================================
-- 三、RBAC 权限体系 — 大厂标准五表模型（V3.0）
-- 参考: Alibaba/JD RBAC 设计规范
-- =====================================================================

-- 角色表
CREATE TABLE `sys_role`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `code`        VARCHAR(64)  NOT NULL UNIQUE COMMENT '角色编码 (ROLE_USER/ROLE_VIP/ROLE_SELLER/ROLE_ADMIN)',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '角色描述',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `is_system`   TINYINT      DEFAULT 0 COMMENT '1=系统内置角色(不可删除)',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统角色';

-- 权限表 (含菜单/按钮/API三级)
CREATE TABLE `sys_permission`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    `code`        VARCHAR(128) NOT NULL UNIQUE COMMENT '权限编码 (domain:action:resource)',
    `type`        TINYINT      DEFAULT 3 COMMENT '1=菜单, 2=按钮, 3=API接口',
    `parent_id`   BIGINT       DEFAULT NULL COMMENT '父权限ID (菜单树)',
    `path`        VARCHAR(256) DEFAULT NULL COMMENT '资源路径 (API URL pattern)',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统权限';

-- 用户-角色关联表
CREATE TABLE `sys_user_role`
(
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-角色关联';

-- 角色-权限关联表
CREATE TABLE `sys_role_permission`
(
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`role_id`, `permission_id`),
    CONSTRAINT `fk_role_perm_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_role_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-权限关联';

-- 用户-直接权限表 (支持临时授权，绕过角色)
CREATE TABLE `sys_user_permission`
(
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`user_id`, `permission_id`),
    CONSTRAINT `fk_user_perm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-直接权限关联';

-- =====================================================================
-- 四、营销域：优惠券 / 积分 / 秒杀 / 首页装修（V1.1 / V1.2 / V4.0 / V4.3）
-- =====================================================================

-- coupon（优惠券模板，五维模型 + 适用范围/叠加规则）
CREATE TABLE `coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `coupon_id` VARCHAR(32) NOT NULL UNIQUE COMMENT '业务唯一标识',
    `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    `type` TINYINT NULL COMMENT 'DEPRECATED（V4.0）：请使用 discount_type',
    `value` INT NOT NULL COMMENT '券面值（满减=分, 折扣=百分比 85=8.5折）',
    `min_amount` INT NOT NULL DEFAULT 0 COMMENT '最低消费金额（分）',
    `total_qty` INT NOT NULL COMMENT '发行总量',
    `issued_qty` INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `start_time` DATETIME NOT NULL COMMENT '生效开始时间',
    `end_time` DATETIME NOT NULL COMMENT '生效结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=生效, 0=失效',
    `seller_id` BIGINT DEFAULT NULL COMMENT '商家ID（NULL=平台券）',
    `discount_type` TINYINT NOT NULL DEFAULT 1 COMMENT '优惠类型: 1=FIXED满减, 2=PERCENTAGE折扣, 3=CASH_COUPON代金券（V4.0）',
    `coupon_category` TINYINT NOT NULL DEFAULT 2 COMMENT '券类别: 1=PLATFORM平台券, 2=SHOP店铺券, 3=FLASH_SALE秒杀券, 4=EXCLUSIVE独占券（V4.0）',
    `grant_type` TINYINT NOT NULL DEFAULT 1 COMMENT '获取方式: 1=FREE_CLAIM免费领, 2=PAID_PURCHASE付费购买, 3=INVITATION邀请, 4=AUTO_ISSUE自动发放（V4.0）',
    `stock_type` TINYINT NOT NULL DEFAULT 1 COMMENT '总量模型: 1=LIMITED限量, 2=UNLIMITED不限量（V4.0）',
    `grab_type` TINYINT NOT NULL DEFAULT 1 COMMENT '领取方式: 1=NORMAL普通, 2=NEED_GRAB需抢, 3=PLATFORM_EXCLUSIVE平台独占（V4.0）',
    `price_in_cents` INT DEFAULT NULL COMMENT '付费券价格(分), grantType=PAID_PURCHASE时必填（V4.0）',
    `scope_type` TINYINT NOT NULL DEFAULT 1 COMMENT '适用范围: 1=ALL全场, 2=CATEGORY品类, 3=PRODUCT单品（V4.3）',
    `scope_values` JSON DEFAULT NULL COMMENT '适用范围的ID列表: categoryIds 或 productIds（V4.3）',
    `stack_rule` TINYINT NOT NULL DEFAULT 1 COMMENT '叠加规则: 1=MUTUAL_EXCLUSIVE互斥, 2=STACKABLE可叠加（V4.3）',
    `stack_group` VARCHAR(32) DEFAULT NULL COMMENT '叠加分组ID，同组券可叠加使用（V4.3）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

-- user_coupon（用户优惠券）
CREATE TABLE `user_coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '关联 coupon 表主键',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=未使用, 2=已使用, 3=已过期',
    `used_order_no` VARCHAR(32) COMMENT '使用的订单号',
    `use_time` DATETIME COMMENT '使用时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_status` (`user_id`, `status`),
    UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券';

-- points_log（积分变动日志）— marketing-service 积分账本
CREATE TABLE `points_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points` INT NOT NULL COMMENT '积分变动（正=获得，负=消耗）',
    `type` TINYINT NOT NULL COMMENT '1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期',
    `source` VARCHAR(50) COMMENT '来源描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分变动日志';

-- flash_sale（秒杀活动）
CREATE TABLE `flash_sale` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `flash_price` INT NOT NULL COMMENT '秒杀价（分）',
    `stock` INT NOT NULL COMMENT '秒杀库存',
    `sold_count` INT NOT NULL DEFAULT 0 COMMENT '已售数量',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=未开始, 2=进行中, 3=已结束',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_time` (`start_time`, `end_time`),
    KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动';

-- flash_order_log（秒杀订单事务日志，幂等+兜底）
CREATE TABLE `flash_order_log`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no`    VARCHAR(32)  NOT NULL UNIQUE COMMENT '订单号',
    `flash_id`    BIGINT       NOT NULL COMMENT '秒杀活动ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `status`      TINYINT      DEFAULT 0 COMMENT '0=待处理, 1=已创建, 2=失败, 3=超时取消',
    `retry_count` INT          DEFAULT 0 COMMENT '重试次数',
    `error_msg`   VARCHAR(500) NULL COMMENT '失败原因',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status_time` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '秒杀订单事务日志(幂等+兜底)';

-- home_config（首页装修配置）
CREATE TABLE `home_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `slot` VARCHAR(30) NOT NULL COMMENT '位置标识(banner/hot/new/sale)',
    `type` TINYINT NOT NULL COMMENT '1=商品组, 2=广告图, 3=活动链接',
    `title` VARCHAR(100) COMMENT '标题',
    `product_ids` VARCHAR(500) COMMENT '商品ID列表，逗号分隔',
    `image` VARCHAR(255) COMMENT '图片URL',
    `link_url` VARCHAR(255) COMMENT '跳转链接',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用, 0=停用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_slot` (`slot`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页配置';

-- =====================================================================
-- 五、评价 / 收藏 / 浏览足迹（V1.1 / V5.0）
-- =====================================================================

-- review（商品评价，含 V5.0 商家回复 + 追评 + 点赞 + 标签）
CREATE TABLE `review` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `sku_id` BIGINT DEFAULT NULL COMMENT 'SKU ID',
    `rating` TINYINT NOT NULL COMMENT '评分 1-5',
    `content` TEXT COMMENT '评价内容',
    `images` VARCHAR(500) COMMENT '评价图片，逗号分隔',
    `reply` TEXT COMMENT '商家回复内容（V5.0）',
    `reply_time` DATETIME DEFAULT NULL COMMENT '商家回复时间（V5.0）',
    `append_content` TEXT COMMENT '追评内容（V5.0）',
    `append_media_urls` VARCHAR(1000) COMMENT '追评图片，逗号分隔（V5.0）',
    `append_time` DATETIME DEFAULT NULL COMMENT '追评时间（V5.0）',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数（V5.0）',
    `tags` VARCHAR(500) COMMENT '评价标签，JSON数组 ["质量好","物流快"]（V5.0）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_order_user` (`order_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价';

-- review_like（评价点赞，多对多）
CREATE TABLE `review_like` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `review_id` BIGINT NOT NULL COMMENT '评价ID',
    `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE KEY `uk_review_user` (`review_id`, `user_id`),
    KEY `idx_review_id` (`review_id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_review_like_review` FOREIGN KEY (`review_id`) REFERENCES `review` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价点赞表';

-- favorite（商品收藏）
CREATE TABLE `favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏';

-- product_view_log（商品浏览足迹）
CREATE TABLE `product_view_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID(技术主键)',
    `view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_time` (`user_id`, `view_time`),
    CONSTRAINT `fk_view_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_view_log_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品浏览足迹';

-- =====================================================================
-- 六、售后 / 入驻 / 结算（V2.0 / V2.1）
-- =====================================================================

-- after_sale（售后申请）
CREATE TABLE `after_sale` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(32) NOT NULL COMMENT '关联订单号',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `type` TINYINT NOT NULL COMMENT '1=退货退款, 2=仅退款',
    `reason` VARCHAR(500) NOT NULL COMMENT '申请原因',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=待审核, 2=同意, 3=拒绝, 4=已退货, 5=已退款, 6=已完成',
    `admin_remark` VARCHAR(500) COMMENT '审核备注',
    `refund_amount` INT COMMENT '退款金额（分）',
    `logistics_number` VARCHAR(50) COMMENT '退货物流单号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_order_user` (`order_no`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后申请';

-- seller_application（商家入驻申请）
--   ⚠️ deprecated（2026-10 第六轮）：平行入驻流程已退役；现行入驻以 seller 表为准。
--   表保留作数据备份，不做数据迁移。
CREATE TABLE `seller_application` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `shop_name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
    `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
    `province` VARCHAR(50) COMMENT '省',
    `city` VARCHAR(50) COMMENT '市',
    `district` VARCHAR(50) COMMENT '区/县',
    `detail_address` VARCHAR(200) COMMENT '详细地址',
    `description` VARCHAR(500) COMMENT '商家描述',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核, 1=通过, 2=拒绝',
    `admin_remark` VARCHAR(500) COMMENT '审核备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家入驻申请（deprecated）';

-- settlement（结算单）— trade-service 结算域
CREATE TABLE `settlement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `settlement_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '结算单号',
    `seller_id` BIGINT NOT NULL COMMENT '商家ID',
    `period_start` DATE NOT NULL COMMENT '结算周期开始',
    `period_end` DATE NOT NULL COMMENT '结算周期结束',
    `order_count` INT NOT NULL DEFAULT 0 COMMENT '订单数量',
    `total_amount` INT NOT NULL DEFAULT 0 COMMENT '订单总金额（分）',
    `commission` INT NOT NULL DEFAULT 0 COMMENT '平台佣金（分）',
    `settlement_amount` INT NOT NULL DEFAULT 0 COMMENT '结算金额（分）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=待结算, 2=已结算, 3=已打款',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_id` (`seller_id`),
    KEY `idx_period` (`period_start`, `period_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算单';

-- withdrawal（提现申请）— trade-service 结算域（与 settlement 级联，不属钱包域）
CREATE TABLE `withdrawal` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `withdrawal_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '提现单号',
    `seller_id` BIGINT NOT NULL COMMENT '商家ID',
    `amount` INT NOT NULL COMMENT '提现金额（分）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=待审核, 2=已打款, 3=拒绝',
    `bank_account` VARCHAR(50) COMMENT '银行账号',
    `bank_name` VARCHAR(100) COMMENT '开户行',
    `admin_remark` VARCHAR(500) COMMENT '审核备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_id` (`seller_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提现申请';

-- =====================================================================
-- 七、AI 客服域：会话 / 消息 / 人工 IM / 知识库（V2.5 / V3.3 / V3.4）
-- =====================================================================

-- ai_conversation（AI 对话会话）
CREATE TABLE `ai_conversation`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` VARCHAR(36)  NOT NULL UNIQUE COMMENT '会话业务ID (UUID)',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `agent_type`      VARCHAR(20)  NOT NULL COMMENT 'Agent类型: SHOPPING / CUSTOMER_SERVICE',
    `title`           VARCHAR(100) DEFAULT NULL COMMENT '会话标题（首条用户消息截断）',
    `message_count`   INT          DEFAULT 0 COMMENT '消息总数',
    `status`          TINYINT      DEFAULT 1 COMMENT '1=活跃, 2=已归档, 3=已删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX `idx_user_agent` (`user_id`, `agent_type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 对话会话';

-- ai_message（AI 对话消息）
CREATE TABLE `ai_message`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(10)  NOT NULL COMMENT '角色: USER / ASSISTANT / SYSTEM / TOOL',
    `content`         TEXT         NOT NULL COMMENT '消息内容',
    `tool_calls`      JSON         DEFAULT NULL COMMENT 'Tool调用记录: [{toolName, input, output, latencyMs}]',
    `token_count`     INT          DEFAULT NULL COMMENT 'Token消耗估算',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间',
    INDEX `idx_conversation_id` (`conversation_id`),
    CONSTRAINT `fk_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`conversation_id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 对话消息';

-- chat_message（买家-商家 IM 聊天消息）
CREATE TABLE `chat_message`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id`      VARCHAR(36)  NOT NULL UNIQUE COMMENT '消息业务ID (UUID)',
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话ID (buyer_{buyerId}_seller_{sellerId}_product_{productId})',
    `sender_id`       BIGINT       NOT NULL COMMENT '发送者用户ID',
    `sender_role`     VARCHAR(10)  NOT NULL COMMENT 'BUYER / SELLER / AI',
    `receiver_id`     BIGINT       NOT NULL COMMENT '接收者用户ID',
    `content`         TEXT         NOT NULL COMMENT '消息内容',
    `content_type`    VARCHAR(10)  DEFAULT 'TEXT' COMMENT 'TEXT / IMAGE / PRODUCT_CARD / ORDER_CARD',
    `extra`           JSON         DEFAULT NULL COMMENT '扩展数据',
    `is_read`         TINYINT      DEFAULT 0 COMMENT '0=未读, 1=已读',
    `is_ai_generated` TINYINT      DEFAULT 0 COMMENT '0=人工, 1=AI自动代答',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX `idx_conversation` (`conversation_id`),
    INDEX `idx_sender_receiver` (`sender_id`, `receiver_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '买家-商家聊天消息';

-- merchant_knowledge_base（商家 AI 知识库）
CREATE TABLE `merchant_knowledge_base`
(
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `seller_id`    BIGINT       NOT NULL COMMENT '商家ID',
    `title`        VARCHAR(200) NOT NULL COMMENT '知识条目标题',
    `content`      TEXT         NOT NULL COMMENT '知识内容(FAQ/政策/产品说明)',
    `category`     VARCHAR(30)  NOT NULL COMMENT '分类: FAQ/POLICY/PRODUCT/ANNOUNCEMENT',
    `embedding_id` VARCHAR(64)  NULL COMMENT 'ES/Milvus 向量ID',
    `status`       TINYINT      DEFAULT 1 COMMENT '1=草稿, 2=已发布, 3=已归档',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_seller_category` (`seller_id`, `category`),
    INDEX `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商家AI知识库';

-- merchant_reply_template（商家回复模板）
CREATE TABLE `merchant_reply_template`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `seller_id`   BIGINT       NOT NULL COMMENT '商家ID',
    `title`       VARCHAR(200) NOT NULL COMMENT '模板标题',
    `content`     TEXT         NOT NULL COMMENT '模板内容(变量: {buyer_name},{product_name},{order_no})',
    `category`    VARCHAR(30)  NOT NULL COMMENT 'GREETING/FAQ/OFFLINE/ORDER',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_seller_category` (`seller_id`, `category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商家回复模板';

-- =====================================================================
-- 八、触达 / 资金 / 日志（V1.2 / V5.0 / V5.1 / V5.3）
-- =====================================================================

-- notification(消息通知中心) — notification-service 触达域
CREATE TABLE `notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `recipient_id` BIGINT NOT NULL COMMENT '接收者用户ID',
    `sender_id` BIGINT DEFAULT NULL COMMENT '发送者用户ID (NULL=系统)',
    `sender_name` VARCHAR(100) DEFAULT NULL COMMENT '发送者名称 (用户名或店铺名)',
    `sender_avatar` VARCHAR(255) DEFAULT NULL COMMENT '发送者头像',
    `type` VARCHAR(32) NOT NULL COMMENT '通知类型: LIKE/REPLY/APPEND/FOLLOW/ORDER/SYSTEM',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(500) DEFAULT NULL COMMENT '通知摘要',
    `link_url` VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读, 1=已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_recipient_read` (`recipient_id`, `is_read`),
    INDEX `idx_recipient_time` (`recipient_id`, `create_time`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- operation_log（操作审计日志）
CREATE TABLE `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '操作用户名',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `target` VARCHAR(100) COMMENT '操作目标',
    `detail` VARCHAR(500) COMMENT '详细信息',
    `ip` VARCHAR(45) COMMENT '客户端IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

-- user_auth（用户认证凭证表）— authorization-service 凭证唯一所有权（ADR-017）
--   三字段模型：identifier_cipher(AES-256-GCM 密文) + identifier_hash(HMAC-SHA256 盲索引) + identifier_mask(脱敏)
CREATE TABLE `user_auth` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`           BIGINT       NOT NULL COMMENT '关联 user.id（技术主键，跨服务 user_id 语义）',
    `identity_type`     VARCHAR(32)  NOT NULL COMMENT '标识类型: USERNAME/PHONE/EMAIL/WECHAT/WECHAT_MINI',
    `identifier_plain`  VARCHAR(128) DEFAULT NULL COMMENT '非敏感标识明文（openid/用户名）',
    `identifier_cipher` VARCHAR(512) DEFAULT NULL COMMENT '敏感标识密文（AES-256-GCM，Base64）',
    `identifier_hash`   VARCHAR(128) DEFAULT NULL COMMENT '敏感标识盲索引（HMAC-SHA256，查询用）',
    `identifier_mask`   VARCHAR(64)  DEFAULT NULL COMMENT '敏感标识脱敏展示值',
    `union_id`          VARCHAR(64)  DEFAULT NULL COMMENT '微信 UnionID（跨应用打通）',
    `credential`        VARCHAR(255) DEFAULT NULL COMMENT '登录凭证（密码哈希；验证码/微信登录为空）',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '0=已解绑, 1=有效, 2=已锁定',
    `last_login_time`   DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 业务唯一键：敏感标识走盲索引，非敏感标识走明文（MySQL 唯一索引允许多个 NULL）
    UNIQUE KEY `uk_type_hash`  (`identity_type`, `identifier_hash`),
    UNIQUE KEY `uk_type_plain` (`identity_type`, `identifier_plain`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_union_id` (`union_id`),
    CONSTRAINT `fk_user_auth_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证凭证表';

-- wallet_account（钱包账户）— wallet-service 资金域；user.balance 列已冻结
CREATE TABLE `wallet_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `balance` INT NOT NULL DEFAULT 0 COMMENT '余额（单位：分）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包账户';

-- =====================================================================
-- 八之四、资金线 P0（V5.5，与 migrations/V5.5__refund_wallet_tx_and_reconciliation.sql 逐列一致）
--   F1 买家退款资金流：refund_order（pay-service） + wallet_transaction（wallet-service）
--   F4 渠道对账：reconciliation_batch / reconciliation_detail（pay-service）
--   规格：project-docs/backend/04-Data-Model.md §十二 12.1 / 12.4
-- =====================================================================

-- refund_order（退款单）— pay-service；uk_after_sale 是一售后单一次退款的幂等锚点
CREATE TABLE `refund_order` (
    `id`                BIGINT       AUTO_INCREMENT COMMENT '技术主键',
    `refund_no`         CHAR(36)     NOT NULL COMMENT '退款单号（业务唯一键，UUID；对外与幂等重试的定位键）',
    `order_no`          VARCHAR(64)  NOT NULL COMMENT '关联订单号（orders.order_no，无外键）',
    `pay_order_no`      VARCHAR(64)  NOT NULL COMMENT '关联支付单号（pay_order.pay_order_no）',
    `after_sale_id`     BIGINT       NOT NULL COMMENT '关联售后单（after_sale.id）——一售后单一次退款',
    `user_id`           BIGINT       NOT NULL COMMENT '买家用户ID',
    `channel`           VARCHAR(16)  NOT NULL COMMENT '退款渠道：WECHAT / ALIPAY / BALANCE（与原支付渠道一致，原路退回）',
    `refund_amount`     INT          NOT NULL COMMENT '退款金额（分），申请时已校验 ≤ 订单实付 payAmount',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-成功，2-失败',
    `channel_refund_no` VARCHAR(64)  DEFAULT NULL COMMENT '渠道退款单号（微信 refund_id 等，回调回填）',
    `fail_reason`       VARCHAR(255) DEFAULT NULL COMMENT '失败原因（渠道返回或系统错误摘要，不含敏感细节）',
    `create_time`       DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_after_sale` (`after_sale_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单（pay-service 退款域）';

-- wallet_transaction（钱包流水）— wallet-service；transaction_no 幂等键
CREATE TABLE `wallet_transaction` (
    `id`             BIGINT       AUTO_INCREMENT COMMENT '技术主键',
    `transaction_no` CHAR(36)     NOT NULL COMMENT '流水号（幂等键，UUID）',
    `user_id`        BIGINT       NOT NULL COMMENT '用户ID（wallet_account.user_id 口径）',
    `type`           TINYINT      NOT NULL COMMENT '类型：1-收入，2-支出',
    `amount`         INT          NOT NULL COMMENT '变动金额（分，恒正；方向由 type 表达）',
    `biz_no`         VARCHAR(64)  NOT NULL COMMENT '业务关联号（退款流水=退款单号 refund_no；支出流水=订单号等）',
    `remark`         VARCHAR(100) DEFAULT NULL COMMENT '摘要（如"售后退款"）',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transaction_no` (`transaction_no`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包流水（wallet-service 资金域）';

-- reconciliation_batch（对账批次）— pay-service；uk_date_channel 先删后写的幂等锚点
CREATE TABLE `reconciliation_batch` (
    `id`            BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `bill_date`     DATE        NOT NULL COMMENT '账单日',
    `channel`       VARCHAR(16) NOT NULL COMMENT '对账渠道：WECHAT / ALIPAY（余额渠道本地闭环不参与对账）',
    `status`        TINYINT     NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-完成，2-失败',
    `channel_total` INT         DEFAULT NULL COMMENT '渠道账单总额（分，账单解析后回填）',
    `local_total`   INT         DEFAULT NULL COMMENT '本地支付成功总额（分，pay_order.status=成功 按日汇总）',
    `create_time`   DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_channel` (`bill_date`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账批次（pay-service）';

-- reconciliation_detail（对账差异明细）— pay-service；result 四态，Phase 1 仅记录不自动调账
CREATE TABLE `reconciliation_detail` (
    `id`             BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `batch_id`       BIGINT      NOT NULL COMMENT '所属批次（reconciliation_batch.id）',
    `pay_order_no`   VARCHAR(64) NOT NULL COMMENT '本地支付单号（比对键）',
    `channel_txn_id` VARCHAR(64) DEFAULT NULL COMMENT '渠道交易号（渠道账单有则回填）',
    `channel_amount` INT         DEFAULT NULL COMMENT '渠道账单金额（分；渠道无此单为 NULL）',
    `local_amount`   INT         DEFAULT NULL COMMENT '本地金额（分；本地无此单为 NULL）',
    `result`         TINYINT     NOT NULL COMMENT '比对结果：1-匹配，2-渠道有本地无，3-本地有渠道无，4-金额不一致',
    `create_time`    DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_batch_result` (`batch_id`, `result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差异明细（pay-service）';

-- =====================================================================
-- 九、种子数据（RBAC + 管理员）
--   与 migrations/V3.0__add_rbac_tables.sql 的种子集合**完全一致**（权威定义）
--   幂等：重复执行不因主键 / 唯一键冲突失败
--   注: VALUES() 在 MySQL 8.0.20+ 标记为 deprecated（仍可用）
-- =====================================================================

-- 角色 (ID 1-4 为系统内置，与 RoleEnum 对齐)
INSERT INTO `sys_role` (`id`, `name`, `code`, `description`, `status`, `is_system`) VALUES
(1, '普通用户', 'ROLE_USER', '平台注册用户的默认角色', 1, 1),
(2, 'VIP用户',   'ROLE_VIP',   'VIP会员', 1, 1),
(3, '入驻商家',   'ROLE_SELLER','商家角色', 1, 1),
(4, '管理员',     'ROLE_ADMIN', '系统管理员，拥有全部权限', 1, 1)
ON DUPLICATE KEY UPDATE
    `name`        = VALUES(`name`),
    `code`        = VALUES(`code`),
    `description` = VALUES(`description`),
    `status`      = VALUES(`status`),
    `is_system`   = VALUES(`is_system`);

-- 权限 (ID 1 = 通配符 *:*:* 超级管理员权限)
INSERT INTO `sys_permission` (`id`, `name`, `code`, `type`, `path`, `sort_order`, `status`) VALUES
(1, '超级管理员', '*:*:*',        3, '/**',                  0,  1),
(2, 'AI 对话',    'ai:chat',      3, '/api/ai/**',           1,  1),
(3, '订单查询',    'order:read',   3, '/api/trade/order/**',  2,  1),
(4, '商品浏览',    'product:read', 3, '/api/item/**',         3,  1),
(5, '用户管理',    'user:admin',   3, '/api/admin/**',        10, 1),
(6, '商家管理',    'seller:admin', 3, '/api/seller/**',       11, 1)
ON DUPLICATE KEY UPDATE
    `name`       = VALUES(`name`),
    `code`       = VALUES(`code`),
    `type`       = VALUES(`type`),
    `path`       = VALUES(`path`),
    `sort_order` = VALUES(`sort_order`),
    `status`     = VALUES(`status`);

-- 角色-权限关联
-- ROLE_USER: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1,2), (1,3), (1,4)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
-- ROLE_VIP: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2,2), (2,3), (2,4)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
-- ROLE_SELLER: AI + 订单 + 商品 + 商家管理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (3,2), (3,3), (3,4), (3,6)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
-- ROLE_ADMIN: 全部权限 (含 *:*:* 超级管理员)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (4,1), (4,2), (4,3), (4,4), (4,5), (4,6)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

-- =====================================================================
-- 管理员种子用户 (admin / admin123)
-- =====================================================================
-- ⚠️⚠️ 安全警告 ⚠️⚠️
--   以下账号为**硬编码弱口令的开发占位账号**，仅为本地联调提供可用管理员：
--     - 严禁流向共享 / 测试 / 预发 / 生产等任何非本地环境；
--     - 任何非本地部署必须先删除该行，或改为经运维注入的强口令账号；
--     - V5.2 起密码凭证的权威为 user_auth.credential，本行的 password 仅为历史兼容：
--       如需该账号可登录，需按 §V5.2 运行存量迁移器（LegacyCredentialMigrationRunner）
--       生成 user_auth 行，或直接改为在 user_auth 中建档。
-- =====================================================================
INSERT INTO `user` (`user_id`, `username`, `phone`, `password`, `role_type`, `status`, `balance`, `register_time`, `create_time`, `update_time`)
VALUES ('admin-001', 'admin', '10000000000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 2, 1, 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- 为管理员分配角色 (user 表 id 为自增，通过子查询获取)
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, 4 FROM `user` u WHERE u.user_id = 'admin-001'
ON DUPLICATE KEY UPDATE `role_id` = 4;

-- =====================================================================
-- 重新启用外键检查
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- V5.8 促销引擎 + 会员等级（F5/F6，ADR-043/044；与 migrations/V5.8__promotion_and_member_level.sql 逐字一致）
-- =====================================================================

CREATE TABLE `promotion` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`           VARCHAR(50) NOT NULL COMMENT '活动名称',
    `seller_id`      BIGINT      NOT NULL COMMENT '商家 user_id（店铺级活动；跨店下单已禁，一单仅触达单商家）',
    `type`           TINYINT     NOT NULL COMMENT '1=满减(FIXED) 2=满折(PERCENTAGE)',
    `threshold`      INT         NOT NULL COMMENT '门槛：订单商品总额 ≥ threshold 生效（分）',
    `discount_value` INT         NOT NULL COMMENT '满减=减免金额（分）；满折=折扣率万分比（8500=8.5折）',
    `max_discount`   INT NOT NULL DEFAULT 0 COMMENT '满折封顶减免额（分）；0=不封顶（满减恒等于 discount_value）',
    `start_time`     DATETIME    NOT NULL COMMENT '开始时间',
    `end_time`       DATETIME    NOT NULL COMMENT '结束时间',
    `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1=未开始 2=进行中 3=已结束（时间窗推导）',
    `version`        INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_time` (`seller_id`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺满减/满折促销活动';

CREATE TABLE `member_level_rule` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `level`         VARCHAR(16) NOT NULL COMMENT '等级（NORMAL/SILVER/GOLD/PLATINUM，与 MemberLevelEnum 对齐）',
    `threshold`     INT         NOT NULL COMMENT '累计成长值门槛（分）',
    `discount_rate` INT         NOT NULL COMMENT '会员折扣率万分比（10000=不折扣，9800=98%）',
    `points_rate`   INT NOT NULL DEFAULT 100 COMMENT '积分倍率百分比（100=1倍，作用于下单奖励积分）',
    `sort`          INT NOT NULL DEFAULT 0 COMMENT '比较顺序（升序）',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级规则（门槛/折扣率/积分倍率）';

INSERT IGNORE INTO `member_level_rule` (`level`, `threshold`, `discount_rate`, `points_rate`, `sort`) VALUES
    ('NORMAL',   0,       10000, 100, 0),
    ('SILVER',   100000,  9900,  110, 1),
    ('GOLD',     500000,  9800,  120, 2),
    ('PLATINUM', 2000000, 9500,  150, 3);

CREATE TABLE `member_growth_log` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT      NOT NULL COMMENT '买家 user_id',
    `order_no`      VARCHAR(32) NOT NULL COMMENT '来源订单号（幂等锚点）',
    `growth_amount` INT         NOT NULL COMMENT '本单成长值（=订单实付金额，分）',
    `after_level`   VARCHAR(16) NOT NULL COMMENT '入账后等级',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员成长值入账流水（只升不降）';
