-- V5.3: 余额域迁出 user —— 钱包账户表（wallet-service 所有）
-- user.balance 列保留（历史数据备份），代码层不再读写
CREATE TABLE IF NOT EXISTS `wallet_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `balance` INT NOT NULL DEFAULT 0 COMMENT '余额（单位：分）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包账户';

-- 存量数据迁移：user.balance → wallet_account.balance（幂等：唯一键冲突跳过）
INSERT IGNORE INTO `wallet_account` (`user_id`, `balance`)
SELECT `user_id`, `balance` FROM `user` WHERE `balance` IS NOT NULL;
