-- V5.2: user 表资料/凭证分离 + PII 加密（ADR-017）
--
-- 目标形态：user 表只承载"资料"（昵称/头像/性别/生日/会员等级/余额/状态 + 手机号、邮箱三字段密文）；
-- 密码凭证与微信身份的唯一权威是 authorization-service 的 user_auth（凭证唯一所有权，ADR-017）；
-- 角色/权限的唯一权威是 RBAC 五表（sys_role/sys_user_role/...），role_type 列退役。
--
-- 数据搬迁由 authorization-service 的存量迁移器完成（AUTH_LEGACY_MIGRATION_ENABLED=true 启动一次）：
--   ① user.password → user_auth.credential（PHONE 行）后置 NULL；
--   ② user.phone 明文 → phone_cipher/phone_hash/phone_mask 后置 NULL；
--   ③ user.wx_openid → user_auth（WECHAT 行，identifier_plain）。
-- 本 DDL 只做结构变更（旧列保留，注释标 deprecated，观察期后删列）。

-- ======================================================
-- 1. 资料增量列（gender / birthday / member_level）
-- ======================================================
ALTER TABLE `user`
    ADD COLUMN `gender` TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女' AFTER `avatar`,
    ADD COLUMN `birthday` DATE DEFAULT NULL COMMENT '生日' AFTER `gender`,
    ADD COLUMN `member_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '会员等级（NORMAL/SILVER/GOLD/PLATINUM）' AFTER `balance`;

-- ======================================================
-- 2. PII 三字段：手机号（cipher/mask 补齐）+ 邮箱（全新）
-- ======================================================
ALTER TABLE `user`
    ADD COLUMN `phone_cipher` VARCHAR(512) DEFAULT NULL COMMENT '手机号密文（AES-256-GCM，Base64）' AFTER `phone`,
    ADD COLUMN `phone_mask` VARCHAR(64) DEFAULT NULL COMMENT '手机号脱敏展示值' AFTER `phone_hash`,
    ADD COLUMN `email_cipher` VARCHAR(512) DEFAULT NULL COMMENT '邮箱密文（AES-256-GCM，Base64）' AFTER `phone_mask`,
    ADD COLUMN `email_hash` VARCHAR(128) DEFAULT NULL COMMENT '邮箱盲索引（HMAC-SHA256，唯一键）' AFTER `email_cipher`,
    ADD COLUMN `email_mask` VARCHAR(64) DEFAULT NULL COMMENT '邮箱脱敏展示值' AFTER `email_hash`;

-- ======================================================
-- 3. 唯一索引：手机号/邮箱按盲索引唯一（明文列退役）
-- ======================================================
-- 说明：uk_phone_hash 已在 V2.5 建立（ADD UNIQUE KEY uk_phone_hash (phone_hash)）；
-- 若目标库未应用 V2.5，请先手动补：ALTER TABLE `user` ADD UNIQUE KEY `uk_phone_hash` (`phone_hash`);
ALTER TABLE `user` DROP INDEX `uk_phone`;
ALTER TABLE `user` ADD UNIQUE KEY `uk_email_hash` (`email_hash`);

-- ======================================================
-- 4. 退役列标注（代码已停止读写，观察期后删列）
-- ======================================================
ALTER TABLE `user`
    MODIFY COLUMN `password` VARCHAR(100) DEFAULT NULL COMMENT 'deprecated V5.2：密码凭证归 user_auth.credential，观察期后删列',
    MODIFY COLUMN `wx_openid` VARCHAR(128) DEFAULT NULL COMMENT 'deprecated V5.2：微信身份归 user_auth.identifier_plain，观察期后删列',
    MODIFY COLUMN `role_type` TINYINT NOT NULL DEFAULT 0 COMMENT 'deprecated V5.2：RBAC 五表（sys_user_role）为准，观察期后删列',
    MODIFY COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT 'deprecated V5.2：手机号存 phone_cipher/phone_hash/phone_mask，迁移后应为 NULL',
    MODIFY COLUMN `last_login_time` DATETIME DEFAULT NULL COMMENT 'deprecated V5.2：登录链路已离开 user 上下文（奖励改为建档奖励），观察期后删列',
    MODIFY COLUMN `register_time` DATETIME NOT NULL COMMENT '注册时间（审计列；领域语义已由 create_time 承担，观察期后评估删列）';
