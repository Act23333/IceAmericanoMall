-- V2.2: 微信 OAuth 第三方登录
-- 为 user 表增加 wx_openid，并放宽 phone/password 为可空（支持微信-only 用户，无手机号/密码）。
-- MySQL 唯一索引允许多个 NULL，故 uk_wx_openid 不影响非微信用户。

ALTER TABLE `user`
    ADD COLUMN `wx_openid` VARCHAR(128) DEFAULT NULL COMMENT '微信 openid（第三方登录）' AFTER `avatar`;

ALTER TABLE `user`
    MODIFY COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号（登录账号，微信-only 用户可空）';

ALTER TABLE `user`
    MODIFY COLUMN `password` VARCHAR(100) DEFAULT NULL COMMENT '加密密码（微信-only 用户可空）';

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_wx_openid` (`wx_openid`);
