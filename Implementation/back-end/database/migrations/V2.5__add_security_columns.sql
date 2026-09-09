-- V2.5: 安全加固 + 登录奖励
-- 1. phone_hash 辅助列（PII 加密后的手机号查找；默认不激活 pii.encryption.enabled）
-- 2. last_login_time（首次/回归登录奖励判断）

ALTER TABLE `user`
    ADD COLUMN `phone_hash` CHAR(64) DEFAULT NULL COMMENT 'SHA-256(phone)，辅助加密手机号查找（PII 加密激活后启用）' AFTER `phone`;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_phone_hash` (`phone_hash`);

ALTER TABLE `user`
    ADD COLUMN `last_login_time` DATETIME DEFAULT NULL COMMENT '最近登录时间（首次/回归奖励判断）' AFTER `register_time`;
