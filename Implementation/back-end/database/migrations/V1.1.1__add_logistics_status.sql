-- ======================================================
-- V1.1: 物流状态追踪
-- ======================================================
-- 为 order_logistics 表添加 status 字段，支持物流生命周期管理
-- 1-待揽收, 2-运输中, 3-已签收, 4-已退回
-- ======================================================

ALTER TABLE `order_logistics`
    ADD COLUMN `status` INT NOT NULL DEFAULT 1 COMMENT '物流状态：1-待揽收,2-运输中,3-已签收,4-已退回'
    AFTER `detail`;

-- 为已有记录设置默认值（若存在数据）
UPDATE `order_logistics` SET `status` = 1 WHERE `status` IS NULL;
