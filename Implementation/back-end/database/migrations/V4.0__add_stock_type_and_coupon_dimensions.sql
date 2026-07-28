-- ======================================================
-- V4.0: SKU库存类型 + 热销标识 + 销售标签 + 优惠券多维度模型
-- 京东/淘宝标准：库存不限量跳过扣减，优惠券按5个维度拆分
-- ======================================================

-- === SKU & Product: Stock type + Hot-selling + Sales tags ===
ALTER TABLE `sku`
    ADD COLUMN `stock_type`  TINYINT NOT NULL DEFAULT 1 COMMENT '库存类型: 1=LIMITED限量, 2=UNLIMITED不限量, 3=PRESALE预售',
    ADD COLUMN `is_hot`      TINYINT NOT NULL DEFAULT 0 COMMENT '热销标识: 0=否, 1=是',
    ADD COLUMN `hot_reason`  VARCHAR(50) NULL COMMENT '热销原因: discount/new_arrival/best_seller/clearance',
    ADD COLUMN `sales_tags`  JSON NULL COMMENT '销售标签JSON: ["限时优惠","新品"]';

ALTER TABLE `product`
    ADD COLUMN `sales_tags`  JSON NULL COMMENT '商品级销售标签';

-- === Coupon: Multi-dimensional model (JD standard) ===
ALTER TABLE `coupon`
    ADD COLUMN `discount_type`   TINYINT NOT NULL DEFAULT 1 COMMENT '优惠类型: 1=FIXED满减, 2=PERCENTAGE折扣, 3=CASH_COUPON代金券',
    ADD COLUMN `coupon_category` TINYINT NOT NULL DEFAULT 2 COMMENT '券类别: 1=PLATFORM平台券, 2=SHOP店铺券, 3=FLASH_SALE秒杀券, 4=EXCLUSIVE独占券',
    ADD COLUMN `grant_type`      TINYINT NOT NULL DEFAULT 1 COMMENT '获取方式: 1=FREE_CLAIM免费领, 2=PAID_PURCHASE付费购买, 3=INVITATION邀请, 4=AUTO_ISSUE自动发放',
    ADD COLUMN `stock_type`      TINYINT NOT NULL DEFAULT 1 COMMENT '总量模型: 1=LIMITED限量, 2=UNLIMITED不限量',
    ADD COLUMN `grab_type`       TINYINT NOT NULL DEFAULT 1 COMMENT '领取方式: 1=NORMAL普通, 2=NEED_GRAB需抢, 3=PLATFORM_EXCLUSIVE平台独占',
    ADD COLUMN `price_in_cents`  INT NULL COMMENT '付费券价格(分), grantType=PAID_PURCHASE时必填';

-- Data migration: old type → new discount_type
UPDATE `coupon` SET `discount_type` = `type`;

-- Derive coupon_category from existing seller_id
UPDATE `coupon` SET `coupon_category` = 1 WHERE `seller_id` IS NULL;
UPDATE `coupon` SET `coupon_category` = 2 WHERE `seller_id` IS NOT NULL;

-- Mark old type column as deprecated (kept for backward compat)
ALTER TABLE `coupon` MODIFY COLUMN `type` TINYINT NULL COMMENT 'DEPRECATED: 请使用 discount_type';
