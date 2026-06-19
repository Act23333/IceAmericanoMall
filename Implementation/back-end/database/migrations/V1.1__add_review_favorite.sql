-- V1.1: 商品评价
CREATE TABLE IF NOT EXISTS `review` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `sku_id` BIGINT,
    `rating` TINYINT NOT NULL COMMENT '评分 1-5',
    `content` TEXT COMMENT '评价内容',
    `images` VARCHAR(500) COMMENT '评价图片，逗号分隔',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_order_user` (`order_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';

-- V1.1: 商品收藏
CREATE TABLE IF NOT EXISTS `favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏';
