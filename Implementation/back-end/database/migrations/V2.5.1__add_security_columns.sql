-- V2.5: 安全加固 + 登录奖励
-- 1. phone_hash 盲索引列（HMAC-SHA256(phone)，等值查找用；运行时由 BlindIndexUtil 计算，
--    密钥经 CryptoKeyConfig 装载，与 AES 数据密钥分离 —— ADR-017）
-- 2. last_login_time（首次/回归登录奖励判断）

ALTER TABLE `user`
    ADD COLUMN `phone_hash` CHAR(64) DEFAULT NULL COMMENT 'HMAC-SHA256(phone) 盲索引（64 位十六进制），辅助加密手机号等值查找（PII 加密激活后启用）' AFTER `phone`;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_phone_hash` (`phone_hash`);

ALTER TABLE `user`
    ADD COLUMN `last_login_time` DATETIME DEFAULT NULL COMMENT '最近登录时间（首次/回归奖励判断）' AFTER `register_time`;
