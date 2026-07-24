-- V3.5: 商品详情页京东标准改造
ALTER TABLE `product` ADD COLUMN `images`     JSON NULL COMMENT '多图URL列表' AFTER `main_image`;
ALTER TABLE `product` ADD COLUMN `video_url`  VARCHAR(500) NULL COMMENT '视频URL' AFTER `images`;
ALTER TABLE `product` ADD COLUMN `attributes` JSON NULL COMMENT '规格参数K-V' AFTER `video_url`;
ALTER TABLE `product` ADD COLUMN `service_tags` JSON NULL COMMENT '售后标签' AFTER `attributes`;
ALTER TABLE `product` ADD COLUMN `view_count` INT DEFAULT 0 COMMENT '浏览次数' AFTER `sold_count`;

ALTER TABLE `sku` ADD COLUMN `original_price` INT NULL COMMENT '原价(分)' AFTER `price`;

ALTER TABLE `review` ADD COLUMN `media_urls`  JSON NULL COMMENT '媒体URL [{type,url}]' AFTER `images`;
ALTER TABLE `review` ADD COLUMN `is_anonymous` TINYINT DEFAULT 0 COMMENT '匿名评价' AFTER `media_urls`;
ALTER TABLE `review` ADD COLUMN `helpful_count` INT DEFAULT 0 COMMENT '有用数' AFTER `is_anonymous`;

-- 商品浏览记录
CREATE TABLE IF NOT EXISTS `product_view_log`
(
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT NULL COMMENT '用户ID(NULL=匿名)',
    `product_id` BIGINT NOT NULL,
    `view_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_product_time` (`product_id`, `view_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品浏览记录';

-- 店铺关注
CREATE TABLE IF NOT EXISTS `store_follow`
(
    `user_id`     BIGINT NOT NULL,
    `seller_id`   BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `seller_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺关注';
