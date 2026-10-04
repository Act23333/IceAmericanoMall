-- ======================================================
-- V5.4: 商家 / 店铺 1:N 拆分（Phase A，ADR-019）
--   seller 瘦身为"商家身份 + 生命周期"，店铺资料迁入新表 shop（1 商家 : N 店铺）
--   存量数据：每个商家生成唯一默认店铺（status 原样带过来）
--   下游（product/orders/coupon/settlement/merchant_knowledge_base）Phase A 保持
--   seller_id = 商家 user_id 口径，不在本迁移内改动
-- ======================================================

-- ------------------------------------------------------
-- 1. 店铺表
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shop` (
    `id`             BIGINT AUTO_INCREMENT COMMENT '技术主键',
    `merchant_id`    BIGINT       NOT NULL COMMENT '所属商家ID（seller.id）',
    `shop_name`      VARCHAR(100) NOT NULL COMMENT '店铺名称',
    `shop_logo`      VARCHAR(255) DEFAULT NULL COMMENT '店铺logo',
    `contact_phone`  VARCHAR(20)  NOT NULL COMMENT '联系电话',
    `province`       VARCHAR(50)  NOT NULL COMMENT '发货省',
    `city`           VARCHAR(50)  NOT NULL COMMENT '发货市',
    `district`       VARCHAR(50)  NOT NULL COMMENT '发货区/县',
    `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-审核中，1-正常，2-冻结',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_shop_name` (`merchant_id`, `shop_name`),
    KEY `idx_merchant_status` (`merchant_id`, `status`),
    CONSTRAINT `fk_shop_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- ------------------------------------------------------
-- 2. 存量回填：一个商家 → 一个默认店铺（id 最小者为默认店铺，解析见 ShopManager）
--    幂等：重复执行因 uk_merchant_shop_name 冲突被 IGNORE 跳过
-- ------------------------------------------------------
INSERT IGNORE INTO `shop`
(`merchant_id`, `shop_name`, `shop_logo`, `contact_phone`, `province`, `city`, `district`, `detail_address`,
 `status`, `create_time`, `update_time`)
SELECT `id`, `shop_name`, `shop_logo`, `contact_phone`, `province`, `city`, `district`, `detail_address`,
       `status`, `create_time`, `update_time`
FROM `seller`;

-- ------------------------------------------------------
-- 3. seller 瘦身：删除 7 列店铺资料（已整体迁入 shop）
-- ------------------------------------------------------
ALTER TABLE `seller`
    DROP COLUMN `shop_name`,
    DROP COLUMN `shop_logo`,
    DROP COLUMN `contact_phone`,
    DROP COLUMN `province`,
    DROP COLUMN `city`,
    DROP COLUMN `district`,
    DROP COLUMN `detail_address`;
