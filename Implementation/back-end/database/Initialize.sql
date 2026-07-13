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
                           `main_image` VARCHAR(255) DEFAULT NULL COMMENT '主图URL',
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
                       `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
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
-- 重新启用外键检查
-- ======================================================
SET FOREIGN_KEY_CHECKS = 1;