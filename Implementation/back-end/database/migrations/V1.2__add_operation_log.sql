-- V1.2: 操作日志
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50),
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `target` VARCHAR(100) COMMENT '操作目标',
    `detail` VARCHAR(500) COMMENT '详细信息',
    `ip` VARCHAR(45),
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';
