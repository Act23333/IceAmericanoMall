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

-- ===================== 种子数据 =====================

-- 角色
INSERT INTO `sys_role` (`id`, `name`, `code`, `description`, `status`, `is_system`) VALUES
(1, '普通用户', 'ROLE_USER', '平台注册用户的默认角色', 1, 1),
(2, 'VIP用户',   'ROLE_VIP',   'VIP会员', 1, 1),
(3, '入驻商家',   'ROLE_SELLER','商家角色', 1, 1),
(4, '管理员',     'ROLE_ADMIN', '系统管理员', 1, 1);

-- 权限
INSERT INTO `sys_permission` (`id`, `name`, `code`, `type`, `path`, `sort_order`, `status`) VALUES
(1,  'AI 对话',        'ai:chat',        3, '/api/ai/**',           1, 1),
(2,  '订单查询',        'order:read',     3, '/api/trade/order/**', 2, 1),
(3,  '商品浏览',        'product:read',   3, '/api/item/**',        3, 1),
(4,  '用户管理',        'user:admin',     3, '/api/admin/**',       10, 1),
(5,  '商家管理',        'seller:admin',   3, '/api/seller/**',      11, 1);

-- 角色-权限关联
-- ROLE_USER: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1,1), (1,2), (1,3);
-- ROLE_VIP: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2,1), (2,2), (2,3);
-- ROLE_SELLER: AI + 订单 + 商品 + 商家管理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (3,1), (3,2), (3,3), (3,5);
-- ROLE_ADMIN: 全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (4,1), (4,2), (4,3), (4,4), (4,5);
