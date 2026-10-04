-- V3.5: 商品详情页增强 — 多图/视频/参数/服务标签/浏览量 + SKU 原价（划线价）
-- 京东标准: 商品卡片店铺信息+多图轮播+结构化SKU
-- 注: MySQL 不支持 `ADD COLUMN IF NOT EXISTS`（MariaDB 扩展），此处使用标准语法；
--     本迁移仅在升级路径中执行一次（新库走 Initialize.sql 全量快照，不再执行迁移）。

ALTER TABLE `product`
    ADD COLUMN `images` VARCHAR(2000) DEFAULT NULL COMMENT '多图URL, JSON数组' AFTER `main_image`,
    ADD COLUMN `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL' AFTER `images`,
    ADD COLUMN `attributes` JSON DEFAULT NULL COMMENT '规格参数, K-V对' AFTER `video_url`,
    ADD COLUMN `service_tags` VARCHAR(500) DEFAULT NULL COMMENT '售后标签, JSON数组' AFTER `attributes`,
    ADD COLUMN `sales_tags` VARCHAR(500) DEFAULT NULL COMMENT '销售标签, JSON数组（V4.0 升级为 JSON 类型）' AFTER `service_tags`,
    ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数' AFTER `sales_tags`;

-- V3.5（同批）：SKU 划线原价（此前仅存在于 Initialize.sql 基线，迁移链缺失，补齐）
ALTER TABLE `sku`
    ADD COLUMN `original_price` INT DEFAULT NULL COMMENT '原价（分），划线价' AFTER `price`;
