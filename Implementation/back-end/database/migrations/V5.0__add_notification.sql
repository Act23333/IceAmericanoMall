-- V5.0: 消息通知中心 — 京东/淘宝标准
-- 点赞通知、回复通知、订单通知、系统通知

CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `recipient_id` BIGINT NOT NULL COMMENT '接收者用户ID',
    `sender_id` BIGINT DEFAULT NULL COMMENT '发送者用户ID (NULL=系统)',
    `sender_name` VARCHAR(100) DEFAULT NULL COMMENT '发送者名称 (用户名或店铺名)',
    `sender_avatar` VARCHAR(255) DEFAULT NULL COMMENT '发送者头像',
    `type` VARCHAR(32) NOT NULL COMMENT '通知类型: LIKE/REPLY/APPEND/FOLLOW/ORDER/SYSTEM',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(500) DEFAULT NULL COMMENT '通知摘要',
    `link_url` VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读, 1=已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_recipient_read` (`recipient_id`, `is_read`),
    INDEX `idx_recipient_time` (`recipient_id`, `create_time`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';
