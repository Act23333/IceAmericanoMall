-- V3.7: 秒杀漏斗模型 — 本地事务表（幂等保障） + 乐观锁兜底
CREATE TABLE IF NOT EXISTS `flash_order_log`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no`    VARCHAR(32)  NOT NULL UNIQUE COMMENT '订单号',
    `flash_id`    BIGINT       NOT NULL COMMENT '秒杀活动ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `status`      TINYINT      DEFAULT 0 COMMENT '0=待处理, 1=已创建, 2=失败, 3=超时取消',
    `retry_count` INT          DEFAULT 0 COMMENT '重试次数',
    `error_msg`   VARCHAR(500) NULL COMMENT '失败原因',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_order_no` (`order_no`),
    INDEX `idx_status_time` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀订单事务日志(幂等+兜底)';
