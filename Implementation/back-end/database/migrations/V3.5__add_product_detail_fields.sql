-- V3.5: 商品详情页增强 — 多图/视频/参数/服务标签/浏览量
-- 京东标准: 商品卡片店铺信息+多图轮播+结构化SKU

ALTER TABLE `product`
    ADD COLUMN IF NOT EXISTS `images` VARCHAR(2000) DEFAULT NULL COMMENT '多图URL, JSON数组' AFTER `main_image`,
    ADD COLUMN IF NOT EXISTS `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL' AFTER `images`,
    ADD COLUMN IF NOT EXISTS `attributes` JSON DEFAULT NULL COMMENT '规格参数, K-V对' AFTER `video_url`,
    ADD COLUMN IF NOT EXISTS `service_tags` VARCHAR(500) DEFAULT NULL COMMENT '售后标签, JSON数组' AFTER `attributes`,
    ADD COLUMN IF NOT EXISTS `sales_tags` VARCHAR(500) DEFAULT NULL COMMENT '销售标签, JSON数组' AFTER `service_tags`,
    ADD COLUMN IF NOT EXISTS `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数' AFTER `sales_tags`;
