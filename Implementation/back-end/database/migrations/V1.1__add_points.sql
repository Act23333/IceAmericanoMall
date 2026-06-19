-- V1.1: 积分系统
CREATE TABLE IF NOT EXISTS `points_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points` INT NOT NULL COMMENT '积分变动（正=获得，负=消耗）',
    `type` TINYINT NOT NULL COMMENT '1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期',
    `source` VARCHAR(50) COMMENT '来源描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动日志';
