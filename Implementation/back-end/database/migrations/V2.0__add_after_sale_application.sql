-- V2.0: 售后申请
CREATE TABLE IF NOT EXISTS `after_sale` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(32) NOT NULL COMMENT '关联订单号',
    `user_id` BIGINT NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后申请';

-- V2.0: 商家入驻申请表
CREATE TABLE IF NOT EXISTS `seller_application` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `shop_name` VARCHAR(100) NOT NULL,
    `contact_phone` VARCHAR(20) NOT NULL,
    `province` VARCHAR(50),
    `city` VARCHAR(50),
    `district` VARCHAR(50),
    `detail_address` VARCHAR(200),
    `description` VARCHAR(500) COMMENT '商家描述',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核, 1=通过, 2=拒绝',
    `admin_remark` VARCHAR(500),
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家入驻申请';
