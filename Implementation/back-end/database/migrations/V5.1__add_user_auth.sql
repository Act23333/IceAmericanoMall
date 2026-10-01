-- V5.1: 认证凭证表（authorization-service 聚合 user_auth）
-- 三字段模型：identifier_cipher(AES-GCM 密文) + identifier_hash(HMAC 盲索引) + identifier_mask(脱敏)
-- 非敏感标识（openid/用户名）存 identifier_plain；微信跨应用关联存 union_id

CREATE TABLE IF NOT EXISTS `user_auth` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`           BIGINT       NOT NULL COMMENT '关联 user.id（技术主键，跨服务 user_id 语义）',
    `identity_type`     VARCHAR(32)  NOT NULL COMMENT '标识类型: USERNAME/PHONE/EMAIL/WECHAT/WECHAT_MINI',
    `identifier_plain`  VARCHAR(128) DEFAULT NULL COMMENT '非敏感标识明文（openid/用户名）',
    `identifier_cipher` VARCHAR(512) DEFAULT NULL COMMENT '敏感标识密文（AES-256-GCM，Base64）',
    `identifier_hash`   VARCHAR(128) DEFAULT NULL COMMENT '敏感标识盲索引（HMAC-SHA256，查询用）',
    `identifier_mask`   VARCHAR(64)  DEFAULT NULL COMMENT '敏感标识脱敏展示值',
    `union_id`          VARCHAR(64)  DEFAULT NULL COMMENT '微信 UnionID（跨应用打通）',
    `credential`        VARCHAR(255) DEFAULT NULL COMMENT '登录凭证（密码哈希；验证码/微信登录为空）',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '0=已解绑, 1=有效, 2=已锁定',
    `last_login_time`   DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 业务唯一键：敏感标识走盲索引，非敏感标识走明文（MySQL 唯一索引允许多个 NULL）
    UNIQUE KEY `uk_type_hash`  (`identity_type`, `identifier_hash`),
    UNIQUE KEY `uk_type_plain` (`identity_type`, `identifier_plain`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_union_id` (`union_id`),
    CONSTRAINT `fk_user_auth_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证凭证表';
