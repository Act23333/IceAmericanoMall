-- ======================================================
-- V5.6: 履约线 P0（F2 运费模型 + F3 拆单发货）
--   规格来源：project-docs/backend/04-Data-Model.md §十二 12.2 / 12.3
--   契约来源：project-docs/contract/05-API-Specification.md §4.4.9 / §4.6.1 / §4.6.3 / §4.8.1 / §4.14.17
--
--   本批次含四组变更：
--     (1) 新表 freight_template（归属 item-service 商品域）
--     (2) product.freight_template_id（FK → freight_template.id ON DELETE SET NULL）
--     (3) orders.post_fee（运费快照，分）
--     (4) order_logistics 主键/包裹改造（1:1 → 1:N）+ order_item.package_no
--
--   迁移纪律（沿用 V5.5 口径）：
--     - 只做加法，不改既有列语义；订单/物流/订单项均为事实数据，存量行按默认值回填；
--     - `order_logistics` 存量数据回填 package_no = 1（保持「一单一物流」的历史语义）。
--
--   注：`Initialize.sql`（全量最新快照）已同步本文件的终态（freight_template 建表、
--       product/orders/order_item 终态列、order_logistics 代理主键形态）——逐列一致。
--       新装库只跑 Initialize.sql；存量库按版本顺序执行本文件（V5.5 之后）。
-- ======================================================

-- ------------------------------------------------------
-- 1. freight_template（运费模板）— item-service 商品域
--    同商家内模板名唯一 uk_seller_template
--    计费方式 charge_mode：1-固定运费（Phase 1 唯一值，为 Phase 2 按件/按重计费预留）
--    free_threshold：满额包邮阈值（分），0 = 不参与满额包邮（>0 且订单商品总额达标则运费 0）
--    seller_id 口径 = 商家 user_id（与 product.seller_id 等下游一致，ADR-019 Phase A）
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `freight_template` (
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

-- ------------------------------------------------------
-- 2. product 绑定运费模板
--    NULL / 模板被删除（ON DELETE SET NULL）→ 下单回落商家默认模板
--    （该商家最早创建的启用模板），见 04 §十二 12.2 计价规则第 5 条
-- ------------------------------------------------------
ALTER TABLE `product`
    ADD COLUMN `freight_template_id` BIGINT DEFAULT NULL COMMENT '绑定运费模板ID（NULL=回落商家默认模板）' AFTER `category_id`;

ALTER TABLE `product`
    ADD CONSTRAINT `fk_product_freight_template` FOREIGN KEY (`freight_template_id`)
        REFERENCES `freight_template` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- ------------------------------------------------------
-- 3. orders 运费快照
--    金额不变式变更（旧 → 新）：
--      旧：payAmount = totalAmount − discountAmount
--      新：payAmount = totalAmount − discountAmount + postFee
--    totalAmount 仍 = Σ order_item.sub_total（不含运费）；post_fee 单列快照，
--    模板后续改价/停用不影响已建订单
-- ------------------------------------------------------
ALTER TABLE `orders`
    ADD COLUMN `post_fee` INT NOT NULL DEFAULT 0 COMMENT '运费（分），下单时按运费模板计算并快照' AFTER `discount_amount`;

-- ------------------------------------------------------
-- 4. order_logistics 主键与包裹改造（收敛「主键复用 order_id」约定偏差，1:1 → 1:N）
--    步骤顺序说明（MySQL 在 DROP PRIMARY KEY 时要求 order_id 上仍有索引供外键
--    约束 fk_logistics_order 使用，故先补 package_no + 唯一键，再换主键）：
--      4.1 新增 package_no（DEFAULT 1 同时完成存量回填）；
--      4.2 新增 uk_order_package(order_id, package_no) —— 兼作外键所需索引；
--      4.3 丢弃原 order_id 主键，新增自增代理主键 id。
-- ------------------------------------------------------
ALTER TABLE `order_logistics`
    ADD COLUMN `package_no` INT NOT NULL DEFAULT 1 COMMENT '包裹号（同一订单从 1 递增）' AFTER `order_id`;

UPDATE `order_logistics` SET `package_no` = 1 WHERE `package_no` IS NULL;

ALTER TABLE `order_logistics`
    ADD UNIQUE KEY `uk_order_package` (`order_id`, `package_no`);

ALTER TABLE `order_logistics`
    DROP PRIMARY KEY,
    ADD COLUMN `id` BIGINT AUTO_INCREMENT FIRST COMMENT '技术主键',
    ADD PRIMARY KEY (`id`);

-- ------------------------------------------------------
-- 5. order_item 包裹归属
--    package_no NULL = 未发货；一明细只发一次（重复发货同一 sku 被拒，3010）
-- ------------------------------------------------------
ALTER TABLE `order_item`
    ADD COLUMN `package_no` INT DEFAULT NULL COMMENT '归属包裹号（NULL=未发货，对应 order_logistics.package_no）' AFTER `image`;

ALTER TABLE `order_item`
    ADD KEY `idx_order_package` (`order_id`, `package_no`);
