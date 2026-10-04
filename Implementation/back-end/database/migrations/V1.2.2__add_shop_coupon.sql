-- V1.2: 商家店铺优惠券 — coupon 表增加 seller_id
ALTER TABLE `coupon` ADD COLUMN `seller_id` BIGINT NULL COMMENT '商家ID（NULL=平台券）' AFTER `status`;
CREATE INDEX `idx_seller_id` ON `coupon` (`seller_id`);
