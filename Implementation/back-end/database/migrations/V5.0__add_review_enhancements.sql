-- V5.0: 评价增强 — 商家回复 + 用户追评 + 点赞
-- 京东/淘宝标准: 评价支持回复、追评、点赞互动

-- 1. review 表增加字段: 商家回复 + 追评 + 点赞数
ALTER TABLE `review`
    ADD COLUMN `reply` TEXT COMMENT '商家回复内容' AFTER `images`,
    ADD COLUMN `reply_time` DATETIME COMMENT '商家回复时间' AFTER `reply`,

    ADD COLUMN `append_content` TEXT COMMENT '追评内容' AFTER `reply_time`,
    ADD COLUMN `append_media_urls` VARCHAR(1000) COMMENT '追评图片，逗号分隔' AFTER `append_content`,
    ADD COLUMN `append_time` DATETIME COMMENT '追评时间' AFTER `append_media_urls`,

    ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数' AFTER `append_time`,
    ADD COLUMN `tags` VARCHAR(500) COMMENT '评价标签，JSON数组 ["质量好","物流快"]' AFTER `like_count`;

-- 2. 评价点赞表 (用户-评价 多对多)
CREATE TABLE IF NOT EXISTS `review_like` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `review_id` BIGINT NOT NULL COMMENT '评价ID',
    `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE KEY `uk_review_user` (`review_id`, `user_id`),
    KEY `idx_review_id` (`review_id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_review_like_review` FOREIGN KEY (`review_id`) REFERENCES `review` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价点赞表';
