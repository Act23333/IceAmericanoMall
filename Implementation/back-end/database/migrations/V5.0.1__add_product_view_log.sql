-- V5.0: 京东标准浏览足迹 — product_view_log 表
-- 记录每次商品浏览，同一商品重复浏览更新 view_time
CREATE TABLE IF NOT EXISTS `product_view_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID(技术主键)',
    `view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    INDEX `idx_user_time` (`user_id`, `view_time`),
    CONSTRAINT `fk_view_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_view_log_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品浏览足迹';
