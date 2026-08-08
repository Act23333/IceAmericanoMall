-- ======================================================
-- IcedAmericanoMall 数据库建表脚本
-- 适用数据库：MySQL 5.7+ / 8.0+
-- 说明：运行前请确保已创建数据库，例如：
  CREATE DATABASE IF NOT EXISTS icedamericano_mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- ======================================================

-- 禁用外键检查，避免删除表时因外键约束报错
SET FOREIGN_KEY_CHECKS = 0;

-- 强制客户端使用 utf8mb4，防止中文注释乱码
SET NAMES utf8mb4;

-- ======================================================
-- 删除所有表（按依赖顺序逆序，也可直接全删，外键检查已禁用）
-- ======================================================
DROP TABLE IF EXISTS `order_logistics`;
DROP TABLE IF EXISTS `pay_order`;
DROP TABLE IF EXISTS `cart`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `sku`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `seller`;
DROP TABLE IF EXISTS `address`;
DROP TABLE IF EXISTS `user`;

-- ======================================================
-- 创建用户表
-- ======================================================
CREATE TABLE `user` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `user_id` VARCHAR(32) NOT NULL COMMENT '业务唯一标识（UUID）',
                        `username` VARCHAR(50) DEFAULT NULL COMMENT '用户名（可选）',
                        `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号（登录账号，微信-only 用户可空）',
                        `password` VARCHAR(100) DEFAULT NULL COMMENT '加密密码（微信-only 用户可空）',
                        `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
                        `wx_openid` VARCHAR(128) DEFAULT NULL COMMENT '微信 openid（第三方登录）',
                        `register_time` DATETIME NOT NULL COMMENT '注册时间（业务时间）',
                        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
                        `balance` INT NOT NULL DEFAULT 0 COMMENT '余额（单位：分）',
                        `role_type` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户，1-商家，2-管理员',
                        `create_time` DATETIME NOT NULL COMMENT '记录创建时间',
                        `update_time` DATETIME NOT NULL COMMENT '最后更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_user_id` (`user_id`),
                        UNIQUE KEY `uk_phone` (`phone`),
                        UNIQUE KEY `uk_wx_openid` (`wx_openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ======================================================
-- 创建收货地址表
-- ======================================================
CREATE TABLE `address` (
                           `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                           `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
                           `receiver` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
                           `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                           `province` VARCHAR(50) NOT NULL COMMENT '省',
                           `city` VARCHAR(50) NOT NULL COMMENT '市',
                           `district` VARCHAR(50) NOT NULL COMMENT '区/县',
                           `street` VARCHAR(100) NOT NULL COMMENT '街道/镇',
                           `detail` VARCHAR(200) NOT NULL COMMENT '详细地址（门牌号等）',
                           `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认地址：1-是，0-否',
                           `label` VARCHAR(20) DEFAULT NULL COMMENT '地址标签（如“家”、“公司”）',
                           `longitude` DECIMAL(10,7) DEFAULT NULL COMMENT '经度（可选）',
                           `latitude` DECIMAL(10,7) DEFAULT NULL COMMENT '纬度（可选）',
                           `create_time` DATETIME NOT NULL COMMENT '创建时间',
                           `update_time` DATETIME NOT NULL COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`),
                           CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- ======================================================
-- 创建商家表
-- ======================================================
CREATE TABLE `seller` (
                          `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                          `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
                          `shop_name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
                          `shop_logo` VARCHAR(255) DEFAULT NULL COMMENT '店铺logo',
                          `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                          `province` VARCHAR(50) NOT NULL COMMENT '发货省',
                          `city` VARCHAR(50) NOT NULL COMMENT '发货市',
                          `district` VARCHAR(50) NOT NULL COMMENT '发货区/县',
                          `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
                          `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-审核中，1-正常，2-冻结',
                          `create_time` DATETIME NOT NULL COMMENT '创建时间',
                          `update_time` DATETIME NOT NULL COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_id` (`user_id`),
                          CONSTRAINT `fk_seller_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家表';

-- ======================================================
-- 创建类目表（支持多级分类）
-- ======================================================
CREATE TABLE `category` (
                            `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                            `name` VARCHAR(50) NOT NULL COMMENT '类目名称',
                            `parent_id` BIGINT DEFAULT NULL COMMENT '父类目ID（顶级为NULL）',
                            `level` TINYINT NOT NULL COMMENT '层级：1-一级，2-二级，3-三级',
                            `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
                            `create_time` DATETIME NOT NULL COMMENT '创建时间',
                            `update_time` DATETIME NOT NULL COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_parent_id` (`parent_id`),
                            CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品类目表';

-- ======================================================
-- 创建商品表（SPU）
-- ======================================================
CREATE TABLE `product` (
                           `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                           `product_id` VARCHAR(32) NOT NULL COMMENT '业务商品编号',
                           `seller_id` BIGINT NOT NULL COMMENT '所属商家ID',
                           `category_id` BIGINT NOT NULL COMMENT '所属类目ID',
                           `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
                           `main_image` VARCHAR(255) DEFAULT NULL COMMENT '主图URL（列表页封面）',
                           `images` VARCHAR(2000) DEFAULT NULL COMMENT '多图URL, JSON数组（详情页轮播）',
                           `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL',
                           `attributes` JSON DEFAULT NULL COMMENT '规格参数, K-V对',
                           `service_tags` VARCHAR(500) DEFAULT NULL COMMENT '售后标签, JSON数组 ["7天退换","正品保证"]',
                           `sales_tags` VARCHAR(500) DEFAULT NULL COMMENT '销售标签, JSON数组 ["热销","新品"]',
                           `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
                           `description` TEXT COMMENT '图文描述（HTML）',
                           `brand` VARCHAR(100) DEFAULT NULL COMMENT '品牌',
                           `sold_count` INT NOT NULL DEFAULT 0 COMMENT '总销量',
                           `comment_count` INT NOT NULL DEFAULT 0 COMMENT '总评论数',
                           `is_ad` TINYINT NOT NULL DEFAULT 0 COMMENT '是否广告商品：1-是，0-否',
                           `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-上架，2-下架，3-删除',
                           `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
                           `create_time` DATETIME NOT NULL COMMENT '创建时间',
                           `update_time` DATETIME NOT NULL COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uk_product_id` (`product_id`),
                           KEY `idx_seller_id` (`seller_id`),
                           KEY `idx_category_id` (`category_id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_seller_status` (`seller_id`, `status`),
                           CONSTRAINT `fk_product_seller` FOREIGN KEY (`seller_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                           CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表（SPU）';

-- ======================================================
-- 创建规格表（SKU）
-- ======================================================
CREATE TABLE `sku` (
                       `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                       `sku_id` VARCHAR(32) NOT NULL COMMENT '业务SKU编码',
                       `product_id` BIGINT NOT NULL COMMENT '所属商品ID',
                       `spec` VARCHAR(200) NOT NULL COMMENT '规格描述（如“黑色 128G”）',
                       `price` INT NOT NULL COMMENT '价格（单位：分）',
                           `original_price` INT DEFAULT NULL COMMENT '原价（分），划线价',
                       `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
                           `stock_type` TINYINT DEFAULT 1 COMMENT '库存类型：1=LIMITED, 2=UNLIMITED, 3=PRESALE',
                           `is_hot` TINYINT DEFAULT 0 COMMENT '热销标识：1=是, 0=否',
                           `hot_reason` VARCHAR(100) DEFAULT NULL COMMENT '热销原因',
                           `sales_tags` VARCHAR(500) DEFAULT NULL COMMENT '销售标签, JSON数组',
                       `image` VARCHAR(255) DEFAULT NULL COMMENT '规格专属图片',
                       `sold_count` INT NOT NULL DEFAULT 0 COMMENT '该规格销量',
                       `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-可售，0-停售',
                       `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                       `create_time` DATETIME NOT NULL COMMENT '创建时间',
                       `update_time` DATETIME NOT NULL COMMENT '更新时间',
                       PRIMARY KEY (`id`),
                       UNIQUE KEY `uk_sku_id` (`sku_id`),
                       KEY `idx_product_id` (`product_id`),
                       KEY `idx_status` (`status`),
                       CONSTRAINT `fk_sku_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规格表（SKU）';

-- ======================================================
-- 创建订单表
-- ======================================================
CREATE TABLE `orders` (
                         `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                         `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（业务唯一）',
                         `user_id` BIGINT NOT NULL COMMENT '买家ID',
                         `seller_id` BIGINT NOT NULL COMMENT '商家ID',
                         `total_amount` INT NOT NULL COMMENT '订单总金额（分）',
                         `pay_amount` INT NOT NULL COMMENT '实付金额（分）',
                         `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
                         `status` TINYINT NOT NULL COMMENT '状态：1-待付款，2-待发货，3-待收货，4-已完成，5-已取消，6-待评价',
                         `payment_type` TINYINT DEFAULT NULL COMMENT '支付类型：1-支付宝，2-微信，3-余额',
                         `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名（快照）',
                         `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话（快照）',
                         `receiver_address` VARCHAR(500) NOT NULL COMMENT '收货地址（快照，完整拼接）',
                         `create_time` DATETIME NOT NULL COMMENT '下单时间',
                         `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
                         `consign_time` DATETIME DEFAULT NULL COMMENT '发货时间',
                         `end_time` DATETIME DEFAULT NULL COMMENT '交易完成时间',
                         `close_time` DATETIME DEFAULT NULL COMMENT '交易关闭时间',
                         `comment_time` DATETIME DEFAULT NULL COMMENT '评价时间',
                         `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                         `update_time` DATETIME NOT NULL COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_order_no` (`order_no`),
                         KEY `idx_user_id` (`user_id`),
                         KEY `idx_seller_id` (`seller_id`),
                         KEY `idx_status` (`status`),
                         KEY `idx_user_status` (`user_id`, `status`),
                         CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT `fk_order_seller` FOREIGN KEY (`seller_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ======================================================
-- 创建订单项表
-- ======================================================
CREATE TABLE `order_item` (
                              `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                              `order_id` BIGINT NOT NULL COMMENT '所属订单ID',
                              `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
                              `product_name` VARCHAR(200) NOT NULL COMMENT '商品名快照',
                              `sku_spec` VARCHAR(200) NOT NULL COMMENT '规格快照',
                              `price` INT NOT NULL COMMENT '单价快照（分）',
                              `quantity` INT NOT NULL COMMENT '购买数量',
                              `sub_total` INT NOT NULL COMMENT '小计（分）',
                              `image` VARCHAR(255) DEFAULT NULL COMMENT '图片快照',
                              `create_time` DATETIME NOT NULL COMMENT '创建时间',
                              `update_time` DATETIME NOT NULL COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_order_id` (`order_id`),
                              KEY `idx_sku_id` (`sku_id`),
                              CONSTRAINT `fk_orderitem_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
                              CONSTRAINT `fk_orderitem_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- ======================================================
-- 创建购物车表
-- ======================================================
CREATE TABLE `cart` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `user_id` BIGINT NOT NULL COMMENT '用户ID',
                        `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
                        `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
                        `selected` TINYINT NOT NULL DEFAULT 1 COMMENT '是否选中：1-是，0-否',
                        `create_time` DATETIME NOT NULL COMMENT '加入时间',
                        `update_time` DATETIME NOT NULL COMMENT '更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`), -- 避免同一用户重复添加同一SKU
                        KEY `idx_user_id` (`user_id`),
                        KEY `idx_sku_id` (`sku_id`),
                        CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
                        CONSTRAINT `fk_cart_sku` FOREIGN KEY (`sku_id`) REFERENCES `sku` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- ======================================================
-- 创建支付订单表
-- ======================================================
CREATE TABLE `pay_order` (
                             `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                             `biz_order_no` VARCHAR(32) NOT NULL COMMENT '业务订单号（关联order.order_no）',
                             `pay_order_no` VARCHAR(32) NOT NULL COMMENT '支付单号',
                             `biz_user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
                             `pay_channel_code` VARCHAR(20) NOT NULL COMMENT '支付渠道代码',
                             `amount` INT NOT NULL COMMENT '支付金额（分）',
                             `pay_type` TINYINT NOT NULL COMMENT '支付类型：1-h5，2-小程序，3-公众号，4-扫码，5-余额',
                             `status` TINYINT NOT NULL COMMENT '状态：0-待提交，1-待支付，2-超时取消，3-成功',
                             `expand_json` JSON DEFAULT NULL COMMENT '扩展字段',
                             `result_code` VARCHAR(50) DEFAULT NULL COMMENT '第三方返回码',
                             `result_msg` VARCHAR(200) DEFAULT NULL COMMENT '第三方返回信息',
                             `pay_success_time` DATETIME DEFAULT NULL COMMENT '支付成功时间',
                             `pay_over_time` DATETIME DEFAULT NULL COMMENT '支付超时时间',
                             `qr_code_url` VARCHAR(255) DEFAULT NULL COMMENT '支付二维码链接',
                             `create_time` DATETIME NOT NULL COMMENT '创建时间',
                             `update_time` DATETIME NOT NULL COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_pay_order_no` (`pay_order_no`),
                             KEY `idx_biz_order_no` (`biz_order_no`),
                             KEY `idx_biz_user_id` (`biz_user_id`),
                             KEY `idx_status` (`status`),
                             CONSTRAINT `fk_payorder_user` FOREIGN KEY (`biz_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';

-- ======================================================
-- 创建订单物流表
-- ======================================================
CREATE TABLE `order_logistics` (
                                   `order_id` BIGINT NOT NULL COMMENT '订单ID（主键，与订单一对一）',
                                   `logistics_number` VARCHAR(50) NOT NULL COMMENT '物流单号',
                                   `logistics_company` VARCHAR(50) NOT NULL COMMENT '物流公司名称',
                                   `contact` VARCHAR(50) NOT NULL COMMENT '收件人（快照）',
                                   `mobile` VARCHAR(20) NOT NULL COMMENT '电话（快照）',
                                   `province` VARCHAR(50) NOT NULL COMMENT '省（快照）',
                                   `city` VARCHAR(50) NOT NULL COMMENT '市（快照）',
                                   `district` VARCHAR(50) NOT NULL COMMENT '区/县（快照）',
                                   `street` VARCHAR(100) NOT NULL COMMENT '街道（快照）',
                                   `detail` VARCHAR(200) NOT NULL COMMENT '详细地址（快照）',
                                   `create_time` DATETIME NOT NULL COMMENT '创建时间',
                                   `update_time` DATETIME NOT NULL COMMENT '更新时间',
                                   PRIMARY KEY (`order_id`),
                                   KEY `idx_logistics_number` (`logistics_number`),
                                   CONSTRAINT `fk_logistics_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单物流表';

-- ======================================================
-- ======================================================
-- RBAC 权限体系 — 大厂标准五表模型 (V5.0)
-- 参考: Alibaba/JD RBAC 设计规范
-- ======================================================
DROP TABLE IF EXISTS `sys_user_permission`;
DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;

-- 角色表
CREATE TABLE `sys_role`
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
CREATE TABLE `sys_permission`
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
CREATE TABLE `sys_user_role`
(
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-角色关联';

-- 角色-权限关联表
CREATE TABLE `sys_role_permission`
(
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`role_id`, `permission_id`),
    CONSTRAINT `fk_role_perm_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_role_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色-权限关联';

-- 用户-直接权限表 (支持临时授权，绕过角色)
CREATE TABLE `sys_user_permission`
(
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`user_id`, `permission_id`),
    CONSTRAINT `fk_user_perm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-直接权限关联';

-- ===================== 种子数据 =====================

-- 角色 (ID 1-4 为系统内置，与 RoleEnum 对齐)
INSERT INTO `sys_role` (`id`, `name`, `code`, `description`, `status`, `is_system`) VALUES
(1, '普通用户', 'ROLE_USER', '平台注册用户的默认角色', 1, 1),
(2, 'VIP用户',   'ROLE_VIP',   'VIP会员', 1, 1),
(3, '入驻商家',   'ROLE_SELLER','商家角色', 1, 1),
(4, '管理员',     'ROLE_ADMIN', '系统管理员，拥有全部权限', 1, 1);

-- 权限 (含通配符 *:*:* 超级管理员权限)
INSERT INTO `sys_permission` (`id`, `name`, `code`, `type`, `path`, `sort_order`, `status`) VALUES
(1,  '超级管理员',   '*:*:*',           3, '/**',                   0,  1),
(2,  'AI 对话',      'ai:chat',         3, '/api/ai/**',           1,  1),
(3,  '订单查询',      'order:read',      3, '/api/trade/order/**',  2,  1),
(4,  '商品浏览',      'product:read',    3, '/api/item/**',         3,  1),
(5,  '用户管理',      'user:admin',      3, '/api/admin/**',        10, 1),
(6,  '商家管理',      'seller:admin',    3, '/api/seller/**',       11, 1);

-- 角色-权限关联
-- ROLE_USER: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1,2), (1,3), (1,4);
-- ROLE_VIP: AI + 订单 + 商品
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2,2), (2,3), (2,4);
-- ROLE_SELLER: AI + 订单 + 商品 + 商家管理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (3,2), (3,3), (3,4), (3,6);
-- ROLE_ADMIN: 全部权限 (含 *:*:* 超级管理员)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (4,1), (4,2), (4,3), (4,4), (4,5), (4,6);

-- 管理员种子用户 (admin / admin123, BCrypt)
INSERT INTO `user` (`user_id`, `username`, `phone`, `password`, `role_type`, `status`, `balance`, `register_time`, `create_time`, `update_time`)
VALUES ('admin-001', 'admin', '10000000000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 2, 1, 0, NOW(), NOW(), NOW());

-- 为管理员分配角色 (user 表的 id 为自动递增，通过子查询获取)
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, 4 FROM `user` u WHERE u.user_id = 'admin-001';

-- ======================================================
-- 重新启用外键检查
-- ======================================================
SET FOREIGN_KEY_CHECKS = 1;