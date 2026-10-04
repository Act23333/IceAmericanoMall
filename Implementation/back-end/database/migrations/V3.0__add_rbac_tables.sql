-- V3.0: RBAC 权限体系 — 大厂标准五表模型
-- 参考: Alibaba/JD RBAC 设计规范

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `code`        VARCHAR(64)  NOT NULL UNIQUE COMMENT '角色编码 (ROLE_USER/ROLE_VIP/ROLE_SELLER/ROLE_ADMIN)',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '角色描述',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `is_system`   TINYINT      DEFAULT 0 COMMENT '1=系统内置角色(不可删除)',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统角色';

-- 权限表 (含菜单/按钮/API三级)
CREATE TABLE IF NOT EXISTS `sys_permission`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    `code`        VARCHAR(128) NOT NULL UNIQUE COMMENT '权限编码 (domain:action:resource)',
    `type`        TINYINT      DEFAULT 3 COMMENT '1=菜单, 2=按钮, 3=API接口',
    `parent_id`   BIGINT       DEFAULT NULL COMMENT '父权限ID (菜单树)',
    `path`        VARCHAR(256) DEFAULT NULL COMMENT '资源路径 (API URL pattern)',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      DEFAULT 1 COMMENT '1=启用, 0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统权限';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role`
(
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-角色关联';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS `sys_role_permission`
(
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`role_id`, `permission_id`),
    CONSTRAINT `fk_role_perm_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_role_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-权限关联';

-- 用户-直接权限表 (支持临时授权，绕过角色)
CREATE TABLE IF NOT EXISTS `sys_user_permission`
(
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`user_id`, `permission_id`),
    CONSTRAINT `fk_user_perm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-直接权限关联';

-- ======================================================
-- user.role_type 兼容列（此前仅存在于 Initialize.sql 基线，迁移链缺失，补齐）
--   引入时语义: 0-普通用户，1-商家，2-管理员（V5.2 起降级为 deprecated，权威为 RBAC 五表）
-- ======================================================
ALTER TABLE `user`
    ADD COLUMN `role_type` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户，1-商家，2-管理员' AFTER `status`;

-- ===================== 种子数据 =====================
-- 幂等：重复执行不因主键 / 唯一键冲突失败（INSERT ... ON DUPLICATE KEY UPDATE）
-- 权威集合与 Initialize.sql 种子完全一致：ROLE_ADMIN 持有通配符 *:*:* 超级管理员权限。
-- 注: VALUES() 在 MySQL 8.0.20+ 标记为 deprecated（仍可用）；如需消除告警可改用 8.0.19+ 的行别名语法。

-- 角色 (ID 1-4 为系统内置，与 RoleEnum 对齐)
INSERT INTO `sys_role` (`id`, `name`, `code`, `description`, `status`, `is_system`) VALUES
(1, '普通用户', 'ROLE_USER', '平台注册用户的默认角色', 1, 1),
(2, 'VIP用户',   'ROLE_VIP',   'VIP会员', 1, 1),
(3, '入驻商家',   'ROLE_SELLER','商家角色', 1, 1),
(4, '管理员',     'ROLE_ADMIN', '系统管理员，拥有全部权限', 1, 1)
ON DUPLICATE KEY UPDATE
    `name`        = VALUES(`name`),
    `code`        = VALUES(`code`),
    `description` = VALUES(`description`),
    `status`      = VALUES(`status`),
    `is_system`   = VALUES(`is_system`);

-- 权限 (ID 1 = 通配符 *:*:* 超级管理员权限)
INSERT INTO `sys_permission` (`id`, `name`, `code`, `type`, `path`, `sort_order`, `status`) VALUES
(1, '超级管理员', '*:*:*',        3, '/**',                  0,  1),
(2, 'AI 对话',    'ai:chat',      3, '/api/ai/**',           1,  1),
(3, '订单查询',    'order:read',   3, '/api/trade/order/**',  2,  1),
(4, '商品浏览',    'product:read', 3, '/api/item/**',         3,  1),
(5, '用户管理',    'user:admin',   3, '/api/admin/**',        10, 1),
(6, '商家管理',    'seller:admin', 3, '/api/seller/**',       11, 1)
ON DUPLICATE KEY UPDATE
    `name`       = VALUES(`name`),
    `code`       = VALUES(`code`),
    `type`       = VALUES(`type`),
    `path`       = VALUES(`path`),
    `sort_order` = VALUES(`sort_order`),
    `status`     = VALUES(`status`);

-- 角色-权限关联
-- ROLE_USER: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1,2), (1,3), (1,4)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
-- ROLE_VIP: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2,2), (2,3), (2,4)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
-- ROLE_SELLER: AI + 订单 + 商品 + 商家管理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (3,2), (3,3), (3,4), (3,6)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
-- ROLE_ADMIN: 全部权限 (含 *:*:* 超级管理员)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (4,1), (4,2), (4,3), (4,4), (4,5), (4,6)
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

-- 管理员种子用户 (admin / admin123, BCrypt) + 角色绑定（幂等）
-- ⚠️ 仅限本地开发环境，禁止流向共享 / 生产环境（与 Initialize.sql 同名警告一致）
INSERT INTO `user` (`user_id`, `username`, `phone`, `password`, `role_type`, `status`, `balance`, `register_time`, `create_time`, `update_time`)
VALUES ('admin-001', 'admin', '10000000000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 2, 1, 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- 为管理员分配角色 (user 表 id 为自增，通过子查询获取)
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, 4 FROM `user` u WHERE u.user_id = 'admin-001'
ON DUPLICATE KEY UPDATE `role_id` = 4;
