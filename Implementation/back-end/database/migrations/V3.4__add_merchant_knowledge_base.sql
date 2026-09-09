-- V3.4: 店铺AI — 商家知识库 + 回复模板
CREATE TABLE IF NOT EXISTS `merchant_knowledge_base`
(
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `seller_id`    BIGINT       NOT NULL COMMENT '商家ID',
    `title`        VARCHAR(200) NOT NULL COMMENT '知识条目标题',
    `content`      TEXT         NOT NULL COMMENT '知识内容(FAQ/政策/产品说明)',
    `category`     VARCHAR(30)  NOT NULL COMMENT '分类: FAQ/POLICY/PRODUCT/ANNOUNCEMENT',
    `embedding_id` VARCHAR(64)  NULL COMMENT 'ES/Milvus 向量ID',
    `status`       TINYINT      DEFAULT 1 COMMENT '1=草稿, 2=已发布, 3=已归档',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_seller_category` (`seller_id`, `category`),
    INDEX `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商家AI知识库';

CREATE TABLE IF NOT EXISTS `merchant_reply_template`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `seller_id`   BIGINT       NOT NULL COMMENT '商家ID',
    `title`       VARCHAR(200) NOT NULL COMMENT '模板标题',
    `content`     TEXT         NOT NULL COMMENT '模板内容(变量: {buyer_name},{product_name},{order_no})',
    `category`    VARCHAR(30)  NOT NULL COMMENT 'GREETING/FAQ/OFFLINE/ORDER',
    `sort_order`  INT          DEFAULT 0,
    `status`      TINYINT      DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_seller_category` (`seller_id`, `category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商家回复模板';
