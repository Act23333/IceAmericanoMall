-- V3.3: 买家-商家消息系统
CREATE TABLE IF NOT EXISTS `chat_message`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id`      VARCHAR(36)  NOT NULL UNIQUE COMMENT '消息业务ID (UUID)',
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话ID (buyer_{buyerId}_seller_{sellerId}_product_{productId})',
    `sender_id`       BIGINT       NOT NULL COMMENT '发送者用户ID',
    `sender_role`     VARCHAR(10)  NOT NULL COMMENT 'BUYER / SELLER / AI',
    `receiver_id`     BIGINT       NOT NULL COMMENT '接收者用户ID',
    `content`         TEXT         NOT NULL COMMENT '消息内容',
    `content_type`    VARCHAR(10)  DEFAULT 'TEXT' COMMENT 'TEXT / IMAGE / PRODUCT_CARD / ORDER_CARD',
    `extra`           JSON         DEFAULT NULL COMMENT '扩展数据',
    `is_read`         TINYINT      DEFAULT 0 COMMENT '0=未读, 1=已读',
    `is_ai_generated` TINYINT      DEFAULT 0 COMMENT '0=人工, 1=AI自动代答',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX `idx_conversation` (`conversation_id`),
    INDEX `idx_sender_receiver` (`sender_id`, `receiver_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '买家-商家聊天消息';
