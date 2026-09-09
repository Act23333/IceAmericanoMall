-- V2.5: AI 会话管理数据表
-- 对应 04-Data-Model.md §八 AI 服务数据模型

-- AI 对话会话
CREATE TABLE IF NOT EXISTS `ai_conversation`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` VARCHAR(36)  NOT NULL UNIQUE COMMENT '会话业务ID (UUID)',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `agent_type`      VARCHAR(20)  NOT NULL COMMENT 'Agent类型: SHOPPING / CUSTOMER_SERVICE',
    `title`           VARCHAR(100) DEFAULT NULL COMMENT '会话标题（首条用户消息截断）',
    `message_count`   INT          DEFAULT 0 COMMENT '消息总数',
    `status`          TINYINT      DEFAULT 1 COMMENT '1=活跃, 2=已归档, 3=已删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX `idx_user_agent` (`user_id`, `agent_type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 对话会话';

-- AI 对话消息
CREATE TABLE IF NOT EXISTS `ai_message`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(10)  NOT NULL COMMENT '角色: USER / ASSISTANT / SYSTEM / TOOL',
    `content`         TEXT         NOT NULL COMMENT '消息内容',
    `tool_calls`      JSON         DEFAULT NULL COMMENT 'Tool调用记录: [{toolName, input, output, latencyMs}]',
    `token_count`     INT          DEFAULT NULL COMMENT 'Token消耗估算',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间',
    INDEX `idx_conversation_id` (`conversation_id`),
    CONSTRAINT `fk_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`conversation_id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 对话消息';
