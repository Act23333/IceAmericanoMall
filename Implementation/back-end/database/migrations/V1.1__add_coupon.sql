-- V1.1: 优惠券系统
CREATE TABLE IF NOT EXISTS `coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `coupon_id` VARCHAR(32) NOT NULL UNIQUE COMMENT '业务唯一标识',
    `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    `type` TINYINT NOT NULL COMMENT '1=满减, 2=折扣',
    `value` INT NOT NULL COMMENT '券面值（满减=分, 折扣=百分比 85=8.5折）',
    `min_amount` INT NOT NULL DEFAULT 0 COMMENT '最低消费金额（分）',
    `total_qty` INT NOT NULL COMMENT '发行总量',
    `issued_qty` INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=生效, 0=失效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `coupon_id` BIGINT NOT NULL COMMENT '关联 coupon 表主键',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=未使用, 2=已使用, 3=已过期',
    `used_order_no` VARCHAR(32) COMMENT '使用的订单号',
    `use_time` DATETIME,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_status` (`user_id`, `status`),
    UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';
