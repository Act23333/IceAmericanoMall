-- V2.1: 商家财务结算
CREATE TABLE IF NOT EXISTS `settlement` (
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

CREATE TABLE IF NOT EXISTS `withdrawal` (
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
