-- V1.2: 秒杀活动
CREATE TABLE IF NOT EXISTS `flash_sale` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NOT NULL,
    `sku_id` BIGINT NOT NULL,
    `flash_price` INT NOT NULL COMMENT '秒杀价（分）',
    `stock` INT NOT NULL COMMENT '秒杀库存',
    `sold_count` INT NOT NULL DEFAULT 0,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=未开始, 2=进行中, 3=已结束',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_time` (`start_time`, `end_time`),
    KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

-- V1.2: 首页装修
CREATE TABLE IF NOT EXISTS `home_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `slot` VARCHAR(30) NOT NULL COMMENT '位置标识(banner/hot/new/sale)',
    `type` TINYINT NOT NULL COMMENT '1=商品组, 2=广告图, 3=活动链接',
    `title` VARCHAR(100),
    `product_ids` VARCHAR(500) COMMENT '商品ID列表，逗号分隔',
    `image` VARCHAR(255),
    `link_url` VARCHAR(255),
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_slot` (`slot`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页配置';
