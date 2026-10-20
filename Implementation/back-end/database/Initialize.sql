-- =====================================================================
-- IceAmericanoMall — 数据库【全量唯一结构真相源】建库脚本（Initialize.sql）
-- =====================================================================
--
-- 【文件语义】（定稿，改动前必读；口径依据 ADR-045，2026-10-05）
--   (a) 本文件是数据库结构的**唯一真相源**：新装库只需执行本文件**一次**，即得到
--       完整终态结构 + 种子数据 —— 装库一次到位，不存在「先快照、再补迁移」两段式路径。
--   (b) **迁移机制已废除**：`database/migrations/` 目录**已不存在**（ADR-045：库内无存量
--       数据，逐版升级路径的前提不成立）。不要再找版本迁移脚本，也不要新增迁移文件。
--       本文件中出现的 V1.1.0 → V5.17 字样只是**区块标注**（某段结构由哪个版本引入），
--       既不是文件路径，也不构成执行顺序。
--   (c) **结构变更直接修改本文件**：加表 / 加列 / 改索引与约束 / 调 seed 一律在本文件落地。
--       库内无存量数据 ⇒「改本文件 + 删库重建」与「写 ALTER」等价，无需数据保全流程。
--   (d) **复跑（重建）语义**：本文件按「先全删、再全建」实现幂等重建（见「一、删除所有
--       表」区块）。在已有本项目的库上重跑 = **清空数据后重建**，这是刻意设计而非意外；
--       因此**每张表都必须在「一」区块有一条对应的 `DROP TABLE IF EXISTS`**。
--   (e) **新增表时必须同时补 DROP**：在文件末尾追加建表语句的同时，必须在「一」区块补同名
--       `DROP TABLE IF EXISTS`。漏补 → 在已有库上复跑时于该表的建表处失败（V5.8–V5.17
--       期间追加的 17 张表曾漏补，2026-10-18 批次 C3 补齐）。
--   (f) 建表语句**刻意不加 `IF NOT EXISTS`**：幂等性由「DROP + CREATE」保证。加上它会让
--       「结构变更后重建」静默跳过该表、留下旧结构 —— 与「全量重建」语义冲突。
--
-- 【设计说明】（大厂规范约定，代码/文档需与此保持一致）
--   1) 软删除策略：**默认不启用** MyBatis-Plus `@TableLogic`，绝大多数表**没有** `deleted` 列。
--      可恢复数据由**状态列**承担软删除语义：
--        user.status(0 禁用) / spu.status(3 删除) / seller.status /
--        shop.status / coupon.status / user_auth.status(0 已解绑)
--      ⚠️ **唯一例外**：`invoice_title.deleted`（TINYINT，`@TableLogic`）——
--      抬头是「用户私有、可随意增删」的配置类数据，需要软删（保留历史订单里的抬头快照可追溯）。
--      此前本文件写着「未启用 @TableLogic、没有统一的 deleted 标志列」，在抬头落地后**已成错事实**，
--      U2 已订正（判据：`grep -rn "TableLogic" --include=*.java` 能且只能命中抬头一族）。
--      新增软删表时：本段必须同步登记，**不要**让「默认不启用」这句话继续说谎。
--      明确的**物理删除**场景（删除即不可恢复，属用户主动意愿）：
--        cart（购物车条目）、favorite（取消收藏）、address（用户主动删除）。
--      订单及支付/物流/售后/结算等**事实数据禁止物理删除**，仅通过状态机推进
--        （orders.status：待付款→待发货→待收货→已完成/已取消→待评价）。
--   2) 跨服务外键（FK）为**单库过渡设计**：当前全部表同处一个 schema（V5.5 资金线新增
--      refund_order / wallet_transaction / reconciliation_batch / reconciliation_detail；
--      V5.6 履约线新增 freight_template）
--      （icedamericano_mall），保留 FK 以保障存量数据完整性。微服务化（分库）后
--      FK 必须移除，改由**服务边界 + 最终一致性**（领域事件 / MQ）保证；服务间
--      内部调用统一走 `/internal/**` 接口（该路径异常直传，不包 Result）。
--   3) 金额一律 `INT`（单位：**分**），禁止 float / double / BigDecimal；
--      布尔用 TINYINT(1/0)；状态枚举用 TINYINT + 注释列明取值。
--   4) 技术主键：每张**业务表**以 `BIGINT AUTO_INCREMENT` 为技术主键 `id`，业务标识另立
--      `xxx_id`（UUID，`BINARY(16)`）/ `xxx_no`（可读流水号）。原 order_logistics「主键复用
--      order_id」的例外已在 V5.6 收敛为代理主键 id（order_id 降为 UNIQUE 键，见其表注释）。
--      ⚠️ **例外（U2 订正）**：3 张**关联表**不设代理主键，用**双列联合主键**——
--        sys_user_role(user_id, role_id) / sys_role_permission(role_id, permission_id) /
--        sys_user_permission(user_id, permission_id)。
--      理由：它们是纯关系载体（无独立生命周期、无业务键需求），联合主键本身就是
--      「同对关系只允许一行」的约束，再加代理主键只会多一条永不被引用的序列。
--   5) **`xxx_id` 同名不同型：按「聚合根/快照」vs「引用」两类语义读，不要按列名猜**（U2 标注）。
--      实测存在四组同名列（`user_id` / `spu_id` / `sku_id` / `coupon_id`）两种物理类型，
--      根因是 ADR-050（业务键 `BINARY(16)`）在「引用表仍存技术键」的过渡态：
--
--        ┌ 语义 A：**业务键**（`BINARY(16)`，对外 wire 形态 = 32-hex，ADR-050/062）
--        │   · 聚合根表自持：`user.user_id` / `spu.spu_id` / `sku.sku_id` / `coupon.coupon_id`
--        │   · **快照表**：`order_item.spu_id` / `order_item.sku_id`（ADR-061：订单项自持
--        │     业务键，可跨域追溯商品源头；快照**不随主表变化**，这正是它存业务键的理由）
--        └ 语义 B：**技术键**（`BIGINT`，= 被引用表的 `id`，可建 FK / 走索引）
--            · 其余所有引用列：`cart.sku_id` / `review.spu_id` / `favorite.spu_id` /
--             `user_coupon.coupon_id` / `flash_sale.spu_id` / 以及全部 `user_id`
--             （除 `user.user_id` 自身）
--
--      **判据一句话**：能出现在**对外报文里**的标识是 A；只用来**在本库内 join/过滤**的是 B。
--      要问「这张表的 `spu_id` 是哪一类」，看它有没有 `FOREIGN KEY ... REFERENCES spu(id)`
--      （有 ⇒ B）——**不要**看列名，也不要看类型像不像 id。
--      ⚠️ 已知代价（登记，不假装已解决）：同一个列名两种类型，**跨表 join 时必须先确认语义**，
--      否则会出现「类型不报错、结果为空」的静默错配（BINARY(16) 与 BIGINT 比较恒不相等）。
--      只读核对：`bash Implementation/back-end/scripts/check-schema-keys.sh`（U4，不进 CI）。

--
-- 适用数据库：MySQL 8.0+（推荐 8.4；语法不兼容 MySQL 5.7 之外的 MariaDB 扩展）
-- 字符集/引擎：utf8mb4 + utf8mb4_unicode_ci / InnoDB（全表统一）
-- =====================================================================

-- 数据库创建（按需执行；脚本默认在当前库内建表）
--   CREATE DATABASE IF NOT EXISTS icedamericano_mall
--       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 禁用外键检查，避免删表/建表时因外键约束顺序报错
SET FOREIGN_KEY_CHECKS = 0;

-- 强制客户端使用 utf8mb4，防止中文注释乱码
SET NAMES utf8mb4;

-- =====================================================================
-- 一、删除所有表（外键检查已禁用，顺序无关）
-- =====================================================================
DROP TABLE IF EXISTS `review_like`;
DROP TABLE IF EXISTS `review`;
DROP TABLE IF EXISTS `favorite`;
DROP TABLE IF EXISTS `spu_view_log`;
DROP TABLE IF EXISTS `order_logistics`;
DROP TABLE IF EXISTS `pay_order`;
DROP TABLE IF EXISTS `reconciliation_detail`;
DROP TABLE IF EXISTS `reconciliation_batch`;
DROP TABLE IF EXISTS `refund_order`;
DROP TABLE IF EXISTS `cart`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `sku`;
DROP TABLE IF EXISTS `spu`;
DROP TABLE IF EXISTS `freight_template`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `merchant_knowledge_base`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `shop`;
DROP TABLE IF EXISTS `seller`;
DROP TABLE IF EXISTS `address`;
DROP TABLE IF EXISTS `user_auth`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `wallet_transaction`;
DROP TABLE IF EXISTS `wallet_account`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `points_log`;
DROP TABLE IF EXISTS `user_coupon`;
DROP TABLE IF EXISTS `coupon`;
DROP TABLE IF EXISTS `settlement`;
DROP TABLE IF EXISTS `withdrawal`;
DROP TABLE IF EXISTS `withdrawal_settlement`;
DROP TABLE IF EXISTS `seller_fund_account`;
DROP TABLE IF EXISTS `after_sale`;
DROP TABLE IF EXISTS `flash_sale`;
DROP TABLE IF EXISTS `home_config`;
DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `sys_user_permission`;
DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;
-- V5.8–V5.17 期间追加在文件末尾（「九、种子数据」之后）的表，此前只有建表语句、没有对应
-- DROP，导致在已有库上复跑时于首条缺 DROP 的表处失败（2026-10-18 批次 C3 补齐）。
-- 组内顺序沿用本区块「子表在前」的风格；这 17 张彼此之间、以及与其余表之间均无 FK 约束
-- （既不声明 FK，也不被任何 FK 引用），故顺序不影响执行结果 —— 本节已 `SET FOREIGN_KEY_CHECKS = 0`。
DROP TABLE IF EXISTS `member_growth_log`;
DROP TABLE IF EXISTS `task_record`;
DROP TABLE IF EXISTS `task_def`;
DROP TABLE IF EXISTS `shop_decoration`;
DROP TABLE IF EXISTS `note_like`;
DROP TABLE IF EXISTS `note_spu`;
DROP TABLE IF EXISTS `note`;
DROP TABLE IF EXISTS `group_team_member`;
DROP TABLE IF EXISTS `group_team`;
DROP TABLE IF EXISTS `group_buy_activity`;
DROP TABLE IF EXISTS `invoice`;
DROP TABLE IF EXISTS `invoice_title`;
DROP TABLE IF EXISTS `pairing_rule`;
DROP TABLE IF EXISTS `exchange_rate`;
DROP TABLE IF EXISTS `currency`;
DROP TABLE IF EXISTS `promotion`;
DROP TABLE IF EXISTS `member_level_rule`;
-- 发票联动补偿（I56/I60 联动失败的承载表）—— 同样追加在「九、种子数据」之后，故 DROP 补在此处
DROP TABLE IF EXISTS `invoice_link_compensation`;

-- =====================================================================
-- 二、核心业务表（初始基线 11 表 + V5.4 shop + V5.6 freight_template，已合并各版本 ALTER 的终态列）
-- =====================================================================

-- ---------------------------------------------------------------
-- user（用户资料表）— 终态含 V2.2 / V2.5 / V3.2 / V5.2 全部增量
--   凭证（密码/微信）归 user_auth（V5.1）；角色归 RBAC 五表；余额归 wallet_account（V5.3）
--   带 deprecated 标记的列已停止读写，观察期后另行 DDL 删列
-- ---------------------------------------------------------------
CREATE TABLE `user` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `user_id` BINARY(16) NOT NULL COMMENT '业务唯一标识（UUIDv7 原始字节，IdGenerator.nextId() 生成，ADR-050）',
                        `username` VARCHAR(50) DEFAULT NULL COMMENT '昵称（V5.2 起为展示字段，可重复）',
                        `phone` VARCHAR(20) DEFAULT NULL COMMENT 'deprecated V5.2：手机号存 phone_cipher/phone_hash/phone_mask，迁移后应为 NULL',
                        `phone_cipher` VARCHAR(512) DEFAULT NULL COMMENT '手机号密文（AES-256-GCM，Base64，V5.2）',
                        `phone_hash` CHAR(64) DEFAULT NULL COMMENT '手机号盲索引（HMAC-SHA256 十六进制，V2.5）',
                        `phone_mask` VARCHAR(64) DEFAULT NULL COMMENT '手机号脱敏展示值（V5.2）',
                        `email_cipher` VARCHAR(512) DEFAULT NULL COMMENT '邮箱密文（AES-256-GCM，Base64，V5.2）',
                        `email_hash` VARCHAR(128) DEFAULT NULL COMMENT '邮箱盲索引（HMAC-SHA256，V5.2）',
                        `email_mask` VARCHAR(64) DEFAULT NULL COMMENT '邮箱脱敏展示值（V5.2）',
                        `password` VARCHAR(100) DEFAULT NULL COMMENT 'deprecated V5.2：密码凭证归 user_auth.credential，观察期后删列',
                        `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
                        `gender` TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女（V5.2）',
                        `birthday` DATE DEFAULT NULL COMMENT '生日（V5.2）',
                        `wx_openid` VARCHAR(128) DEFAULT NULL COMMENT 'deprecated V5.2：微信身份归 user_auth.identifier_plain，观察期后删列',
                        `member_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '会员等级（NORMAL/SILVER/GOLD/PLATINUM，V5.2）',
                        `register_time` DATETIME NOT NULL COMMENT '注册时间（审计列；领域语义由 create_time 承担）',
                        `last_login_time` DATETIME DEFAULT NULL COMMENT 'deprecated V5.2：登录链路已离开 user 上下文，观察期后删列',
                        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
                        `balance` INT NOT NULL DEFAULT 0 COMMENT 'deprecated（2026-10）：余额迁至 wallet_account（V5.3），列冻结不再读写',
                        `role_type` TINYINT NOT NULL DEFAULT 0 COMMENT 'deprecated V5.2：角色唯一权威为 RBAC 五表（sys_user_role），观察期后删列',
                        `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（V3.2 @Version）',
                        `create_time` DATETIME NOT NULL COMMENT '记录创建时间',
                        `update_time` DATETIME NOT NULL COMMENT '最后更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_user_id` (`user_id`),
                        UNIQUE KEY `uk_phone_hash` (`phone_hash`),
                        UNIQUE KEY `uk_email_hash` (`email_hash`),
                        UNIQUE KEY `uk_wx_openid` (`wx_openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表（资料域）';

-- ---------------------------------------------------------------
-- address（收货地址表）
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- seller（商家表：商家身份 + 生命周期）— 终态为 V5.4 瘦身后的形态
--   店铺资料已迁至 shop（1 商家 : N 店铺，ADR-019）
-- ---------------------------------------------------------------
CREATE TABLE `seller` (
                          `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                          `user_id` BIGINT NOT NULL COMMENT '关联用户ID（1:1）',
                          `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-审核中，1-正常，2-冻结',
                          `create_time` DATETIME NOT NULL COMMENT '创建时间',
                          `update_time` DATETIME NOT NULL COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_id` (`user_id`),
                          CONSTRAINT `fk_seller_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家表（商家身份+生命周期）';

-- ---------------------------------------------------------------
-- shop（店铺表：1 商家 : N 店铺）— V5.4 新增
-- ---------------------------------------------------------------
CREATE TABLE `shop` (
                        `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                        `merchant_id` BIGINT NOT NULL COMMENT '所属商家ID（seller.id）',
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
                        UNIQUE KEY `uk_merchant_shop_name` (`merchant_id`, `shop_name`),
                        KEY `idx_merchant_status` (`merchant_id`, `status`),
                        CONSTRAINT `fk_shop_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- ---------------------------------------------------------------
-- category（商品类目表，支持多级分类）
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- freight_template（运费模板）— 归属 item-service 商品域（V5.6 履约线 F2）
--   V5.6 引入的结构（ADR-045 后无迁移文件，版本号仅作区块标注）
--   seller_id 口径 = 商家 user_id（ADR-019 Phase A）；同商家内模板名唯一
--   fee / free_threshold 单位分；charge_mode=1 固定运费（Phase 1 唯一值）
--   默认模板 = 该商家最早创建的启用模板（WHERE seller_id=? AND status=1 ORDER BY id ASC LIMIT 1）
-- ---------------------------------------------------------------
CREATE TABLE `freight_template` (
    `id`             BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `template_no`    BINARY(16)  NOT NULL COMMENT '模板业务编号（UUIDv7 原始字节，ADR-050）',
    `seller_id`      BIGINT      NOT NULL COMMENT '商家ID（口径 = 商家 user_id）',
    `template_name`  VARCHAR(50) NOT NULL COMMENT '模板名称（同商家内唯一）',
    `charge_mode`    TINYINT     NOT NULL DEFAULT 1 COMMENT '计费方式：1-固定运费（Phase 1 唯一值）',
    `fee`            INT         NOT NULL COMMENT '固定运费（分，≥0）',
    `free_threshold` INT         NOT NULL DEFAULT 0 COMMENT '满额包邮阈值（分）；0 = 不参与满额包邮',
    `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-停用（停用模板不参与计价）',
    `create_time`    DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_no` (`template_no`),
    UNIQUE KEY `uk_seller_template` (`seller_id`, `template_name`),
    KEY `idx_seller_status` (`seller_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运费模板（item-service 商品域）';

-- ---------------------------------------------------------------
-- spu（商品表 / SPU）— 终态含 V3.5 详情页字段；sales_tags 为 JSON（V4.0）
-- ---------------------------------------------------------------
CREATE TABLE `spu` (
                           `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                           `spu_id` BINARY(16) NOT NULL COMMENT '业务商品编号（UUIDv7 原始字节，ADR-050）',
                           `short_code` VARCHAR(16) NOT NULL COMMENT '对外寻址短码（10 位小写 hex，ADR-063 §决策 8）：小程序码 scene="p="+短码，落地页经 P38 换回 SPU；非实体标识、不进任何 VO、不过期、永不复用（软删保留占位）',
                           `seller_id` BIGINT NOT NULL COMMENT '所属商家ID',
                           `category_id` BIGINT NOT NULL COMMENT '所属类目ID',
                           `freight_template_id` BIGINT DEFAULT NULL COMMENT '绑定运费模板ID（V5.6，NULL=回落商家默认模板；模板删除经 ON DELETE SET NULL 回落）',
                           `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
                           `main_image` VARCHAR(255) DEFAULT NULL COMMENT '主图URL（列表页封面）',
                           `images` VARCHAR(2000) DEFAULT NULL COMMENT '多图URL, JSON数组（详情页轮播，V3.5）',
                           `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL（V3.5）',
                           `attributes` JSON DEFAULT NULL COMMENT '规格参数, K-V对（V3.5）',
                           `service_tags` VARCHAR(500) DEFAULT NULL COMMENT '售后标签, JSON数组 ["7天退换","正品保证"]（V3.5）',
                           `sales_tags` JSON DEFAULT NULL COMMENT '商品级销售标签, JSON数组（V4.0 升级为 JSON）',
                           `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数（V3.5）',
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
                           UNIQUE KEY `uk_spu_id` (`spu_id`),
                           UNIQUE KEY `uk_short_code` (`short_code`),
                           KEY `idx_seller_id` (`seller_id`),
                           KEY `idx_category_id` (`category_id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_seller_status` (`seller_id`, `status`),
                           CONSTRAINT `fk_spu_seller` FOREIGN KEY (`seller_id`) REFERENCES `seller` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                           CONSTRAINT `fk_spu_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
                           CONSTRAINT `fk_spu_freight_template` FOREIGN KEY (`freight_template_id`) REFERENCES `freight_template` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表（SPU）';

-- ---------------------------------------------------------------
-- sku（规格表 / SKU）— 终态含 V3.5 original_price + V4.0 库存类型/热销元数据
--   hot_reason 取值受 HotReasonEnum 白名单归一：discount/new_arrival/best_seller/clearance
-- ---------------------------------------------------------------
CREATE TABLE `sku` (
                       `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                       `sku_id` BINARY(16) NOT NULL COMMENT '业务SKU编码（UUIDv7 原始字节，ADR-050）',
                       `spu_id` BIGINT NOT NULL COMMENT '所属商品ID',
                       `spec` VARCHAR(200) NOT NULL COMMENT '规格描述（如“黑色 128G”）',
                       `price` INT NOT NULL COMMENT '价格（单位：分）',
                       `original_price` INT DEFAULT NULL COMMENT '原价（分），划线价（V3.5）',
                       `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
                       `stock_type` TINYINT NOT NULL DEFAULT 1 COMMENT '库存类型：1=LIMITED限量, 2=UNLIMITED不限量, 3=PRESALE预售（V4.0）',
                       `is_hot` TINYINT NOT NULL DEFAULT 0 COMMENT '热销标识：1=是，0=否（V4.0）',
                       `hot_reason` VARCHAR(50) DEFAULT NULL COMMENT '热销原因: discount/new_arrival/best_seller/clearance（V4.0，HotReasonEnum 归一）',
                       `sales_tags` JSON DEFAULT NULL COMMENT '销售标签, JSON数组 ["限时优惠","新品"]（V4.0）',
                       `image` VARCHAR(255) DEFAULT NULL COMMENT '规格专属图片',
                       `sold_count` INT NOT NULL DEFAULT 0 COMMENT '该规格销量',
                       `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-可售，0-停售',
                       `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                       `create_time` DATETIME NOT NULL COMMENT '创建时间',
                       `update_time` DATETIME NOT NULL COMMENT '更新时间',
                       PRIMARY KEY (`id`),
                       UNIQUE KEY `uk_sku_id` (`sku_id`),
                       KEY `idx_spu_id` (`spu_id`),
                       KEY `idx_status` (`status`),
                       CONSTRAINT `fk_sku_spu` FOREIGN KEY (`spu_id`) REFERENCES `spu` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规格表（SKU）';

-- ---------------------------------------------------------------
-- orders（订单表）— 终态含 V4.1 order_type
-- ---------------------------------------------------------------
CREATE TABLE `orders` (
                         `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                         `order_no` BINARY(16) NOT NULL COMMENT '订单号（业务唯一，UUIDv7 原始字节，ADR-050）',
                         `user_id` BIGINT NOT NULL COMMENT '买家ID',
                         `seller_id` BIGINT NOT NULL COMMENT '商家ID',
                         `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型: 1=NORMAL购物车, 2=DIRECT立即购买, 3=FLASH_SALE秒杀, 4=PRESALE预售（V4.1）',
                         `total_amount` INT NOT NULL COMMENT '订单总金额（分）',
                         `pay_amount` INT NOT NULL COMMENT '实付金额（分）',
                         `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
                         `post_fee` INT NOT NULL DEFAULT 0 COMMENT '运费（分），下单时按运费模板计算并快照（V5.6）',
                         `remark` VARCHAR(200) NULL COMMENT '买家留言',
                         `display_currency` VARCHAR(3)  NULL COMMENT '展示币种快照（ISO 4217；NULL=CNY 订单或快照降级；展示参考、非结算币种——V5.17 多币种 PRD D1-B/D3-B）',
                         `display_rate`     BIGINT      NULL COMMENT '下单时点定点整数汇率快照（每 1 CNY 兑目标币主单位数 × 10⁶；展示参考、非结算参数，不参与金额不变式）',
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

-- ---------------------------------------------------------------
-- order_item（订单项表，下单快照）
-- ---------------------------------------------------------------
CREATE TABLE `order_item` (
                              `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                              `order_id` BIGINT NOT NULL COMMENT '所属订单ID',
                              `sku_id` BINARY(16) NOT NULL COMMENT 'SKU 业务键（ADR-061：订单项自持业务键，可跨域追溯）',
                              `spu_id` BINARY(16) NOT NULL COMMENT 'SPU 业务键（ADR-061：商品源头回链/比价锚点）',
                              `spu_name` VARCHAR(200) NOT NULL COMMENT 'SPU 名快照（原 product_name，ADR-061 §决策 5）',
                              `sku_spec` VARCHAR(200) NOT NULL COMMENT '规格快照',
                              `price` INT NOT NULL COMMENT '单价快照（分）',
                              `quantity` INT NOT NULL COMMENT '购买数量',
                              `sub_total` INT NOT NULL COMMENT '小计（分）',
                              `image` VARCHAR(255) DEFAULT NULL COMMENT '图片快照',
                              `package_no` INT DEFAULT NULL COMMENT '归属包裹号（V5.6；NULL=未发货，对应 order_logistics.package_no）',
                              `create_time` DATETIME NOT NULL COMMENT '创建时间',
                              `update_time` DATETIME NOT NULL COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_order_id` (`order_id`),
                              KEY `idx_sku_id` (`sku_id`),
                              KEY `idx_spu_id` (`spu_id`),
                              KEY `idx_order_package` (`order_id`, `package_no`),
                              CONSTRAINT `fk_orderitem_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- ---------------------------------------------------------------
-- cart（购物车表）— (user_id, sku_id) 唯一去重
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- pay_order（支付订单表）
--   2026-10-18（批次 X 资金链路口径修复）：新增 channel_trade_no —— 对外渠道号。
--   背景：发起侧此前把 orders.order_no 当微信 out_trade_no 传给渠道，回调/对账侧却按
--   pay_order.pay_order_no 解析 → 真实回调必然查不到本地支付单。现对外号是独立列：
--     ① 12–20 位随机 hex（取 20 位，80 bit 熵），跨重试稳定，不可从时间序推断（不用 UUIDv7：
--        其前 48 位是毫秒时间戳，且 32 位 hex 对微信 out_trade_no 上限 32 位是零余量）；
--     ② VARCHAR 短号，不是 BINARY(16) 业务键 —— 与 pay_order_no 彻底分离；
--     ③ 余额渠道本地闭环、不对外发起，故允许 NULL（MySQL UNIQUE 允许多个 NULL）。
--   库无存量数据（ADR-045），故无迁移脚本、无回填。
-- ---------------------------------------------------------------
CREATE TABLE `pay_order` (
                             `id` BIGINT AUTO_INCREMENT COMMENT '技术主键',
                             `biz_order_no` BINARY(16) NOT NULL COMMENT '业务订单号（关联 orders.order_no，UUIDv7 原始字节，ADR-050）',
                             `pay_order_no` BINARY(16) NOT NULL COMMENT '支付单号（纯内部口径，不下发渠道；UUIDv7 原始字节，ADR-050）',
                             `channel_trade_no` VARCHAR(32) DEFAULT NULL COMMENT '对外渠道号（渠道 out_trade_no 的唯一取值来源；12–20 位随机 hex，跨重试稳定，库内唯一；余额渠道为 NULL）',
                             `biz_user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
                             `pay_channel_code` VARCHAR(20) NOT NULL COMMENT '支付渠道代码',
                             `amount` INT NOT NULL COMMENT '支付金额（分）',
                             `pay_type` TINYINT NOT NULL COMMENT '支付类型：1-h5，2-公众号(JSAPI)，3-小程序(MINI)，4-扫码(NATIVE)，5-余额（与 PayTypeEnum 对齐，V5.18 修正）',
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
                             UNIQUE KEY `uk_channel_trade_no` (`channel_trade_no`),
                             KEY `idx_biz_order_no` (`biz_order_no`),
                             KEY `idx_biz_user_id` (`biz_user_id`),
                             KEY `idx_status` (`status`),
                             CONSTRAINT `fk_payorder_user` FOREIGN KEY (`biz_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';

-- ---------------------------------------------------------------
-- order_logistics（订单物流表）— 终态含 V1.1.1 status + V5.6 拆单发货（1:1 → 1:N）
--   V5.6 收敛此前「主键复用 order_id」的约定偏差（D4）：
--     ① 代理主键 id BIGINT AUTO_INCREMENT；order_id 降为普通外键列（不建单列唯一键 ——
--        单列 UNIQUE 会禁止一单多包裹，与 1:N 语义冲突）；
--     ② 新增 package_no INT NOT NULL DEFAULT 1，唯一键 uk_order_package(order_id, package_no)；
--     ③ 语义由「一单一物流」升级为「一单一包裹一条物流记录」（存量数据 package_no=1）。
--   order_id 上的索引由 uk_order_package 前缀承担，外键 fk_logistics_order 关系不变。
-- ---------------------------------------------------------------
CREATE TABLE `order_logistics` (
                                   `id` BIGINT AUTO_INCREMENT COMMENT '技术主键（V5.6）',
                                   `order_id` BIGINT NOT NULL COMMENT '订单ID（一单多包裹，按 package_no 区分）',
                                   `package_no` INT NOT NULL DEFAULT 1 COMMENT '包裹号（同一订单从 1 递增，V5.6）',
                                   `logistics_number` VARCHAR(50) NOT NULL COMMENT '物流单号',
                                   `logistics_company` VARCHAR(50) NOT NULL COMMENT '物流公司名称',
                                   `contact` VARCHAR(50) NOT NULL COMMENT '收件人（快照）',
                                   `mobile` VARCHAR(20) NOT NULL COMMENT '电话（快照）',
                                   `province` VARCHAR(50) NOT NULL COMMENT '省（快照）',
                                   `city` VARCHAR(50) NOT NULL COMMENT '市（快照）',
                                   `district` VARCHAR(50) NOT NULL COMMENT '区/县（快照）',
                                   `street` VARCHAR(100) NOT NULL COMMENT '街道（快照）',
                                   `detail` VARCHAR(200) NOT NULL COMMENT '详细地址（快照）',
                                   `status` INT NOT NULL DEFAULT 1 COMMENT '物流状态：1-待揽收, 2-运输中, 3-已签收, 4-已退回（V1.1.1）',
                                   `create_time` DATETIME NOT NULL COMMENT '创建时间',
                                   `update_time` DATETIME NOT NULL COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_order_package` (`order_id`, `package_no`),
                                   KEY `idx_logistics_number` (`logistics_number`),
                                   CONSTRAINT `fk_logistics_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单物流表（一单一包裹一条记录）';

-- =====================================================================
-- 三、RBAC 权限体系 — 大厂标准五表模型（V3.0）
-- 参考: Alibaba/JD RBAC 设计规范
-- =====================================================================

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

-- =====================================================================
-- 四、营销域：优惠券 / 积分 / 秒杀 / 首页装修（V1.1 / V1.2 / V4.0 / V4.3）
-- =====================================================================

-- coupon（优惠券模板，五维模型 + 适用范围/叠加规则）
CREATE TABLE `coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `coupon_id` BINARY(16) NOT NULL UNIQUE COMMENT '业务唯一标识（可省略由系统生成 UUIDv7；提供则须 32-hex/36-hyphen 并归一为 32-hex 落库，ADR-050 修订）',
    `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    `type` TINYINT NULL COMMENT 'DEPRECATED（V4.0）：请使用 discount_type',
    `value` INT NOT NULL COMMENT '券面值（满减=分, 折扣=百分比 85=8.5折）',
    `min_amount` INT NOT NULL DEFAULT 0 COMMENT '最低消费金额（分）',
    `total_qty` INT NOT NULL COMMENT '发行总量',
    `issued_qty` INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `start_time` DATETIME NOT NULL COMMENT '生效开始时间',
    `end_time` DATETIME NOT NULL COMMENT '生效结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=生效, 0=失效',
    `seller_id` BIGINT DEFAULT NULL COMMENT '商家ID（NULL=平台券）',
    `discount_type` TINYINT NOT NULL DEFAULT 1 COMMENT '优惠类型: 1=FIXED满减, 2=PERCENTAGE折扣, 3=CASH_COUPON代金券（V4.0）',
    `coupon_category` TINYINT NOT NULL DEFAULT 2 COMMENT '券类别: 1=PLATFORM平台券, 2=SHOP店铺券, 3=FLASH_SALE秒杀券, 4=EXCLUSIVE独占券（V4.0）',
    `grant_type` TINYINT NOT NULL DEFAULT 1 COMMENT '获取方式: 1=FREE_CLAIM免费领, 2=PAID_PURCHASE付费购买, 3=INVITATION邀请, 4=AUTO_ISSUE自动发放（V4.0）',
    `stock_type` TINYINT NOT NULL DEFAULT 1 COMMENT '总量模型: 1=LIMITED限量, 2=UNLIMITED不限量（V4.0）',
    `grab_type` TINYINT NOT NULL DEFAULT 1 COMMENT '领取方式: 1=NORMAL普通, 2=NEED_GRAB需抢, 3=PLATFORM_EXCLUSIVE平台独占（V4.0）',
    `price_in_cents` INT DEFAULT NULL COMMENT '付费券价格(分), grantType=PAID_PURCHASE时必填（V4.0）',
    `scope_type` TINYINT NOT NULL DEFAULT 1 COMMENT '适用范围: 1=ALL全场, 2=CATEGORY品类, 3=PRODUCT单品（V4.3）',
    `scope_values` JSON DEFAULT NULL COMMENT '适用范围的ID列表: categoryIds 或 spuIds（V4.3）',
    `stack_rule` TINYINT NOT NULL DEFAULT 1 COMMENT '叠加规则: 1=MUTUAL_EXCLUSIVE互斥, 2=STACKABLE可叠加（V4.3）',
    `stack_group` VARCHAR(32) DEFAULT NULL COMMENT '叠加分组ID，同组券可叠加使用（V4.3）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

-- user_coupon（用户优惠券）
CREATE TABLE `user_coupon` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '关联 coupon 表主键',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=未使用, 2=已使用, 3=已过期',
    `used_order_no` BINARY(16) COMMENT '使用的订单号（orders.order_no 复制，UUIDv7 原始字节，ADR-050）',
    `use_time` DATETIME COMMENT '使用时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_status` (`user_id`, `status`),
    UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券';

-- points_log（积分变动日志）— marketing-service 积分账本
CREATE TABLE `points_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `points` INT NOT NULL COMMENT '积分变动（正=获得，负=消耗）',
    `type` TINYINT NOT NULL COMMENT '1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期',
    `source` VARCHAR(50) COMMENT '来源描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分变动日志';

-- flash_sale（秒杀活动）
CREATE TABLE `flash_sale` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `spu_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `flash_price` INT NOT NULL COMMENT '秒杀价（分）',
    `stock` INT NOT NULL COMMENT '秒杀库存',
    `sold_count` INT NOT NULL DEFAULT 0 COMMENT '已售数量',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=未开始, 2=进行中, 3=已结束',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_time` (`start_time`, `end_time`),
    KEY `idx_spu` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动';

CREATE TABLE `home_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `slot` VARCHAR(30) NOT NULL COMMENT '位置标识(banner/hot/new/sale)',
    `type` TINYINT NOT NULL COMMENT '1=商品组, 2=广告图, 3=活动链接',
    `title` VARCHAR(100) COMMENT '标题',
    `spu_ids` VARCHAR(500) COMMENT '商品列表 CSV：**SPU 业务键 32-hex**，逗号分隔；容量上限 15 项（15×32+14=494，列宽 500 倒推，ADR-063 §决策 7 表 #3）；写侧形态校验+上限+去重，读侧单项非法 fail-open 过滤',
    `image` VARCHAR(255) COMMENT '图片URL',
    `link_url` VARCHAR(255) COMMENT '跳转链接',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用, 0=停用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_slot` (`slot`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页配置';

-- =====================================================================
-- 五、评价 / 收藏 / 浏览足迹（V1.1 / V5.0）
-- =====================================================================

-- review（商品评价，含 V5.0 商家回复 + 追评 + 点赞 + 标签）
CREATE TABLE `review` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
    `spu_id` BIGINT NOT NULL COMMENT '商品ID',
    `order_no` BINARY(16) NOT NULL COMMENT '关联订单号（orders.order_no，无外键；去重锚点，ADR-050 三批收敛）',
    `sku_id` BIGINT DEFAULT NULL COMMENT 'SKU ID',
    `rating` TINYINT NOT NULL COMMENT '评分 1-5',
    `content` TEXT COMMENT '评价内容',
    `images` VARCHAR(500) COMMENT '评价图片，逗号分隔',
    `reply` TEXT COMMENT '商家回复内容（V5.0）',
    `reply_time` DATETIME DEFAULT NULL COMMENT '商家回复时间（V5.0）',
    `append_content` TEXT COMMENT '追评内容（V5.0）',
    `append_media_urls` VARCHAR(1000) COMMENT '追评图片，逗号分隔（V5.0）',
    `append_time` DATETIME DEFAULT NULL COMMENT '追评时间（V5.0）',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数（V5.0）',
    `tags` VARCHAR(500) COMMENT '评价标签，JSON数组 ["质量好","物流快"]（V5.0）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_spu_id` (`spu_id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_order_user` (`order_no`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价';

-- review_like（评价点赞，多对多）
CREATE TABLE `review_like` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `review_id` BIGINT NOT NULL COMMENT '评价ID',
    `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE KEY `uk_review_user` (`review_id`, `user_id`),
    KEY `idx_review_id` (`review_id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_review_like_review` FOREIGN KEY (`review_id`) REFERENCES `review` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价点赞表';

-- favorite（商品收藏）
CREATE TABLE `favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `spu_id` BIGINT NOT NULL COMMENT '商品ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_spu` (`user_id`, `spu_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏';

-- spu_view_log（商品浏览足迹）
CREATE TABLE `spu_view_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `spu_id` BIGINT NOT NULL COMMENT '商品ID(技术主键)',
    `view_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
    UNIQUE KEY `uk_user_spu` (`user_id`, `spu_id`),
    INDEX `idx_user_time` (`user_id`, `view_time`),
    CONSTRAINT `fk_view_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_view_log_spu` FOREIGN KEY (`spu_id`) REFERENCES `spu` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品浏览足迹';

-- =====================================================================
-- 六、售后 / 入驻 / 结算（V2.0 / V2.1）
-- =====================================================================

-- after_sale（售后申请）
CREATE TABLE `after_sale` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` BINARY(16) NOT NULL COMMENT '关联订单号（orders.order_no，UUIDv7 原始字节，ADR-050）',
    `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
    `type` TINYINT NOT NULL COMMENT '1=退货退款, 2=仅退款',
    `reason` VARCHAR(500) NOT NULL COMMENT '申请原因',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=待审核, 2=同意, 3=拒绝, 4=已退货, 5=已退款, 6=已完成',
    `admin_remark` VARCHAR(500) COMMENT '审核备注',
    `refund_amount` INT COMMENT '退款金额（分）',
    `logistics_number` VARCHAR(50) COMMENT '退货物流单号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_order_user` (`order_no`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后申请';


-- settlement（结算单）— trade-service 结算域
--   **状态语义**：1=待结算 / 2=已结算（**= 可提现，不代表「未打款」**）/ 3=已打款（**历史语义**，T-WITHDRAW 第一步起不再写入）。
--   **已打款的事实源是 withdrawal 表**（status=PAID_OUT），聚合值看 seller_fund_account.paid_total ——
--   **不要**从本表推断「这笔钱打没打过」。故本表不再做「打款后置位」；按金额核销由后续的分配表承担，与行状态解耦。
CREATE TABLE `settlement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `settlement_no` BINARY(16) NOT NULL UNIQUE COMMENT '结算单号（UUIDv7 原始字节，ADR-050）',
    `seller_id` BIGINT NOT NULL COMMENT '商家ID',
    `period_start` DATE NOT NULL COMMENT '结算周期开始',
    `period_end` DATE NOT NULL COMMENT '结算周期结束',
    `order_count` INT NOT NULL DEFAULT 0 COMMENT '订单数量',
    `total_amount` INT NOT NULL DEFAULT 0 COMMENT '订单总金额（分）',
    `commission` INT NOT NULL DEFAULT 0 COMMENT '平台佣金（分）',
    `settlement_amount` INT NOT NULL DEFAULT 0 COMMENT '结算金额（分）',
    `withdrawn_amount` INT NOT NULL DEFAULT 0 COMMENT '已核销提现金额（分）：只能由核销仓储写（带边界条件的条件更新）；只统计 SETTLED 单',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=待结算, 2=已结算(=可提现，不代表未打款), 3=已打款(历史语义，本批起不再写入；已打款事实看 withdrawal)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_id` (`seller_id`),
    KEY `idx_period` (`period_start`, `period_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算单';

-- withdrawal（提现申请）— trade-service 结算域（不属钱包域）
--   审核为「已打款」时在 seller_fund_account 上记 paid_total（不再把结算单全量置为已打款，见下表注释）
CREATE TABLE `withdrawal` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `withdrawal_no` BINARY(16) NOT NULL UNIQUE COMMENT '提现单号（UUIDv7 原始字节，ADR-050）',
    `seller_id` BIGINT NOT NULL COMMENT '商家ID',
    `amount` INT NOT NULL COMMENT '提现金额（分）',
    `payout_no` VARCHAR(64) DEFAULT NULL COMMENT '出款幂等键（渠道去重依据，全生命周期不变）',
    `last_payout_attempt_at` DATETIME DEFAULT NULL COMMENT '上次出款尝试时间（重试节流依据；NULL=从未尝试）',
    `last_payout_result` VARCHAR(160) DEFAULT NULL COMMENT '上次出款结果 分类:渠道号（超长截断并标记）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=待审核, 2=已打款, 3=拒绝, 4=付款中（结果未知，仍占冻结）',
    `bank_account` VARCHAR(50) COMMENT '银行账号',
    `bank_name` VARCHAR(100) COMMENT '开户行',
    `admin_remark` VARCHAR(500) COMMENT '审核备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_id` (`seller_id`),
    KEY `idx_status` (`status`),
    UNIQUE KEY `uk_payout_no` (`payout_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提现申请';

CREATE TABLE `withdrawal_settlement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `withdrawal_id` BIGINT NOT NULL COMMENT '提现单技术主键',
    `settlement_id` BIGINT NOT NULL COMMENT '结算单技术主键',
    `amount` INT NOT NULL COMMENT '本笔提现从该结算单核销的金额（分）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_withdrawal_settlement` (`withdrawal_id`, `settlement_id`),
    KEY `idx_settlement_id` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='提现核销明细（按金额把提现分配到结算单；拒绝时删行，不留 released 标记）';

-- seller_fund_account（商家资金账户）— T-WITHDRAW 第一步：把「可提现额」从「Σ 结算单」搬到一行权威账户上
--   口径（写死，别按列名猜）：可提现额 = settled_total - frozen_amount - paid_total。
--   · settled_total：**累计已结算**，唯一写入者是 settle()（PENDING→SETTLED 的唯一入口），只增不减；
--   · paid_total   ：**累计已打款**，打款时条件累加，只增不减；
--   · frozen_amount：**待审提现冻结额**，第一步**不写**（第一步还没人冻结它，先做减法就会扣一个从未加过的字段）；
--     由第二步 applyWithdrawal 预占时写入，拒绝/打款时释放。
--   为什么不再用「Σ 结算单 amount」当余额：那个口径既不扣已打款、也不扣待审提现（T-WITHDRAW 可超额提现的根因）。
CREATE TABLE `seller_fund_account` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `seller_id`     BIGINT   NOT NULL COMMENT '商家ID（seller.user_id；一格一商家，见 uk_seller_id）',
    `settled_total` INT      NOT NULL DEFAULT 0 COMMENT '累计已结算金额（分）：settle() 同事务累加，只增',
    `frozen_amount` INT      NOT NULL DEFAULT 0 COMMENT '待审提现冻结额（分）：第二步 applyWithdrawal 预占后写入；第一步恒 0',
    `paid_total`    INT      NOT NULL DEFAULT 0 COMMENT '累计已打款金额（分）：打款时同事务累加，只增（审核侧不做额度判断，额度在申请侧）',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家资金账户（权威余额：可提现额 = settled_total - frozen_amount - paid_total）';

-- =====================================================================
-- 七、AI 客服域：会话 / 消息 / 人工 IM / 知识库（V2.5 / V3.3 / V3.4）
-- =====================================================================

CREATE TABLE `chat_message`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id`      BINARY(16)  NOT NULL UNIQUE COMMENT '消息业务ID（UUIDv7 原始字节，ADR-050）',
    `conversation_id` VARCHAR(36)  NOT NULL COMMENT '会话键：内容派生值 sha256("<buyerId>|<sellerId>|<spuId>") 前 32 位小写 hex（服务端唯一派生点 ConversationId；ADR-063 §决策 7 表 #4）。会话键口径唯一：另一套 BINARY(16) 会话表已按 U3 删除，勿再引入第二套',
    `sender_id`       BIGINT       NOT NULL COMMENT '发送者用户ID',
    `sender_role`     VARCHAR(10)  NOT NULL COMMENT 'BUYER / SELLER / AI',
    `receiver_id`     BIGINT       NOT NULL COMMENT '接收者用户ID',
    `content`         TEXT         NOT NULL COMMENT '消息内容',
    `content_type`    VARCHAR(10)  DEFAULT 'TEXT' COMMENT 'TEXT / IMAGE / PRODUCT_CARD / ORDER_CARD',
    `extra`           JSON         DEFAULT NULL COMMENT '扩展数据',
    `is_read`         TINYINT      DEFAULT 0 COMMENT '0=未读, 1=已读',
    `is_ai_generated` TINYINT      DEFAULT 0 COMMENT '0=人工, 1=AI自动代答',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX `idx_conversation` (`conversation_id`),
    INDEX `idx_sender_receiver` (`sender_id`, `receiver_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '买家-商家聊天消息';

-- merchant_knowledge_base（商家 AI 知识库）
CREATE TABLE `merchant_knowledge_base`
(
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `seller_id`    BIGINT       NOT NULL COMMENT '商家ID',
    `title`        VARCHAR(200) NOT NULL COMMENT '知识条目标题',
    `content`      TEXT         NOT NULL COMMENT '知识内容(FAQ/政策/产品说明)',
    `category`     VARCHAR(30)  NOT NULL COMMENT '分类: FAQ/POLICY/PRODUCT/ANNOUNCEMENT',
    `embedding_id` VARCHAR(64)  NULL COMMENT 'ES/Milvus 向量ID',
    `status`       TINYINT      DEFAULT 1 COMMENT '1=草稿, 2=已发布, 3=已归档',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_seller_category` (`seller_id`, `category`),
    INDEX `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商家AI知识库';

CREATE TABLE `notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `recipient_id` BIGINT NOT NULL COMMENT '接收者用户ID',
    `sender_id` BIGINT DEFAULT NULL COMMENT '发送者用户ID (NULL=系统)',
    `sender_name` VARCHAR(100) DEFAULT NULL COMMENT '发送者名称 (用户名或店铺名)',
    `sender_avatar` VARCHAR(255) DEFAULT NULL COMMENT '发送者头像',
    `type` VARCHAR(32) NOT NULL COMMENT '通知类型：取值域见各生产方常量（item/merchant/trade/pay 四服务）；**不要在本注释维护清单**（曾因照抄本注释导致前端 4 个标签页恒空）',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(500) DEFAULT NULL COMMENT '通知摘要',
    `link_url` VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读, 1=已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_recipient_read` (`recipient_id`, `is_read`),
    INDEX `idx_recipient_time` (`recipient_id`, `create_time`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- operation_log（操作审计日志）
CREATE TABLE `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '操作用户名',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `target` VARCHAR(100) COMMENT '操作目标',
    `detail` VARCHAR(500) COMMENT '详细信息',
    `ip` VARCHAR(45) COMMENT '客户端IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

-- user_auth（用户认证凭证表）— authorization-service 凭证唯一所有权（ADR-017）
--   三字段模型：identifier_cipher(AES-256-GCM 密文) + identifier_hash(HMAC-SHA256 盲索引) + identifier_mask(脱敏)
CREATE TABLE `user_auth` (
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

-- wallet_account（钱包账户）— wallet-service 资金域；user.balance 列已冻结
CREATE TABLE `wallet_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `balance` INT NOT NULL DEFAULT 0 COMMENT '余额（单位：分）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包账户';

-- =====================================================================
-- 八之四、资金线 P0（V5.5 引入；ADR-045 后无迁移文件，版本号仅作区块标注）
--   F1 买家退款资金流：refund_order（pay-service） + wallet_transaction（wallet-service）
--   F4 渠道对账：reconciliation_batch / reconciliation_detail（pay-service）
--   规格：project-docs/backend/04-Data-Model.md §十二 12.1 / 12.4
-- =====================================================================

-- refund_order（退款单）— pay-service；uk_after_sale 是一售后单一次退款的幂等锚点
--   2026-10-18（批次 X）：新增 out_refund_no —— 对外退款项号（渠道 out_refund_no 的唯一取值来源）。
--   与内部 refund_no 分离，理由同 pay_order.channel_trade_no（换内部 ID 方案不构成对外 BREAKING）。
--   与既有 channel_refund_no（渠道回填的微信 refund_id）是**两个不同字段**，不可互相顶替：
--   若共用一个列，回调写入 refund_id 就会覆盖对外退款项号 → 后续重试/查询的 out_refund_no 丢失。
CREATE TABLE `refund_order` (
    `id`                BIGINT       AUTO_INCREMENT COMMENT '技术主键',
    `refund_no`         BINARY(16)   NOT NULL COMMENT '退款单号（内部业务唯一键，UUIDv7 原始字节；管理端重试定位键，ADR-050）',
    `out_refund_no`     VARCHAR(64)  DEFAULT NULL COMMENT '对外退款项号（渠道 out_refund_no 的唯一取值来源；12–20 位随机 hex，跨重试稳定，库内唯一）',
    `order_no`          BINARY(16)   NOT NULL COMMENT '关联订单号（orders.order_no 复制，UUIDv7 原始字节，ADR-050）',
    `pay_order_no`      BINARY(16)   NOT NULL COMMENT '关联支付单号（pay_order.pay_order_no 复制，UUIDv7 原始字节，ADR-050）',
    `after_sale_id`     BIGINT       NULL        COMMENT '关联售后单（after_sale.id）——售后场景退款；拼团等无售后场景为 NULL（ADR-049）',
    `biz_ref`           VARCHAR(64)  NOT NULL COMMENT '统一幂等业务键：售后=AS:{afterSaleId}，拼团=GB:{orderNo}（ADR-049）',
    `user_id`           BIGINT       NOT NULL COMMENT '买家用户ID',
    `channel`           VARCHAR(16)  NOT NULL COMMENT '退款渠道：WECHAT / ALIPAY / BALANCE（与原支付渠道一致，原路退回）',
    `refund_amount`     INT          NOT NULL COMMENT '退款金额（分），申请时已校验 ≤ 订单实付 payAmount',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-成功，2-失败',
    `channel_refund_no` VARCHAR(64)  DEFAULT NULL COMMENT '渠道退款单号（微信 refund_id 等，回调回填；对外退款项号见 out_refund_no）',
    `fail_reason`       VARCHAR(255) DEFAULT NULL COMMENT '失败原因（渠道返回或系统错误摘要，不含敏感细节）',
    `create_time`       DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_out_refund_no` (`out_refund_no`),
    UNIQUE KEY `uk_after_sale` (`after_sale_id`),
    UNIQUE KEY `uk_biz_ref` (`biz_ref`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单（pay-service 退款域）';

-- wallet_transaction（钱包流水）— wallet-service；transaction_no 幂等键
CREATE TABLE `wallet_transaction` (
    `id`             BIGINT       AUTO_INCREMENT COMMENT '技术主键',
    `transaction_no` BINARY(16)   NOT NULL COMMENT '流水号（幂等键，= refund_order.refund_no 复制；UUIDv7 原始字节，ADR-050）',
    `user_id`        BIGINT       NOT NULL COMMENT '用户ID（wallet_account.user_id 口径）',
    `type`           TINYINT      NOT NULL COMMENT '类型：1-收入，2-支出',
    `amount`         INT          NOT NULL COMMENT '变动金额（分，恒正；方向由 type 表达）',
    `biz_no`         VARCHAR(64)  NOT NULL COMMENT '业务关联号（退款流水=退款单号 refund_no；支出流水=订单号等）',
    `remark`         VARCHAR(100) DEFAULT NULL COMMENT '摘要（如"售后退款"）',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transaction_no` (`transaction_no`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包流水（wallet-service 资金域）';

-- reconciliation_batch（对账批次）— pay-service；uk_date_channel 先删后写的幂等锚点
CREATE TABLE `reconciliation_batch` (
    `id`            BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `bill_date`     DATE        NOT NULL COMMENT '账单日',
    `channel`       VARCHAR(16) NOT NULL COMMENT '对账渠道：WECHAT / ALIPAY（余额渠道本地闭环不参与对账）',
    `status`        TINYINT     NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-完成，2-失败',
    `channel_total` INT         DEFAULT NULL COMMENT '渠道账单总额（分，账单解析后回填）',
    `local_total`   INT         DEFAULT NULL COMMENT '本地支付成功总额（分，pay_order.status=成功 按日汇总）',
    `create_time`   DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_channel` (`bill_date`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账批次（pay-service）';

-- reconciliation_detail（对账差异明细）— pay-service；result 四态，Phase 1 仅记录不自动调账
--   2026-10-18（批次 X）：比对键改为「对外渠道号」。
--     ① 新增 channel_trade_no —— 渠道账单里的「商户订单号」列，即 pay_order.channel_trade_no；
--     ② pay_order_no 放开 NOT NULL —— 「渠道有本地无」的差异行本就没有本地支付单，强行填号是伪造数据。
CREATE TABLE `reconciliation_detail` (
    `id`               BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `batch_id`         BIGINT      NOT NULL COMMENT '所属批次（reconciliation_batch.id）',
    `pay_order_no`     BINARY(16)  DEFAULT NULL COMMENT '本地支付单号（内部定位用；渠道有本地无时为 NULL，UUIDv7 原始字节，ADR-050）',
    `channel_trade_no` VARCHAR(32) DEFAULT NULL COMMENT '对外渠道号（比对键，pay_order.channel_trade_no）',
    `channel_txn_id`   VARCHAR(64) DEFAULT NULL COMMENT '渠道交易号（渠道账单有则回填）',
    `channel_amount`   INT         DEFAULT NULL COMMENT '渠道账单金额（分；渠道无此单为 NULL）',
    `local_amount`     INT         DEFAULT NULL COMMENT '本地金额（分；本地无此单为 NULL）',
    `result`           TINYINT     NOT NULL COMMENT '比对结果：1-匹配，2-渠道有本地无，3-本地有渠道无，4-金额不一致',
    `create_time`      DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`      DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_batch_result` (`batch_id`, `result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差异明细（pay-service）';

-- =====================================================================
-- 九、种子数据（RBAC + 管理员 / 商家占位账号）
--   种子集合以本文件为**权威定义**（V3.0 为引入版本标注；ADR-045 后无迁移文件）
--   幂等：重复执行不因主键 / 唯一键冲突失败
--   注: VALUES() 在 MySQL 8.0.20+ 标记为 deprecated（仍可用）
-- =====================================================================

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
(6, '商家管理',    'seller:admin', 3, '/api/seller/**',       11, 1);

-- RBAC0 权限位拆分（2026-10-18，草案 rbac-permission-matrix-draft.md §6.1/§7.2）。
-- 执行顺序「先种子 → 再注解 → 后删旧位」：本段只**新增**位（status=1），
-- 旧开关 user:admin 暂不在此停用 —— 它必须等注解逐个替换完之后才停用（否则中间态全线 403）。
INSERT INTO `sys_permission` (`id`, `name`, `code`, `type`, `path`, `sort_order`, `status`) VALUES
(7,  '用户查询',    'user:read',       3, '/api/admin/user/**',        10, 1),
(8,  '用户变更',    'user:write',      3, '/api/admin/user/**',        11, 1),
-- 批 3 裁决（2026-10-18）：管理端订单统一用 order:admin，**不按方法分**；
-- 买家侧 order:read 保持不变（原 order:write 与买家 read 会构成同域两名，故改名）。
(9,  '订单管理',    'order:admin',     3, '/api/admin/order/**',       12, 1),
(10, '售后处理',    'aftersale:write', 3, '/api/admin/after-sale/**',  13, 1),
(11, '售后查询',    'aftersale:read',  3, '/api/admin/after-sale/**',  14, 1),
(12, '支付运维',    'pay:admin',       3, '/api/admin/pay/**',         15, 1),
(13, '财务结算',    'finance:admin',   3, '/api/admin/finance/**',     16, 1),
(14, '发票开具',    'invoice:issue',   3, '/api/admin/invoice/**',     17, 1),
(15, '发票作废',    'invoice:void',    3, '/api/admin/invoice/**',     18, 1),
(16, '汇率维护',    'currency:admin',  3, '/api/admin/currency/**',    19, 1),
(17, '营销查询',    'marketing:read',  3, '/api/admin/**',             20, 1),
(18, '营销管理',    'marketing:admin', 3, '/api/admin/**',             21, 1),
(19, '商家审核',    'merchant:admin',  3, '/api/admin/seller/**',      22, 1),
(20, '首页装修',    'home:admin',      3, '/api/admin/home/**',        23, 1),
(21, '知识图谱',    'kg:admin',        3, '/api/admin/kg/**',          24, 1),
(22, 'RBAC 管理',   'rbac:admin',      3, '/api/admin/roles/**',       25, 1),
(23, '商品维护',    'product:write',   3, '/api/seller/**',            26, 1),
(24, '商家结算',    'seller:finance',  3, '/api/seller/finance/**',    27, 1),
(25, '商家券',      'seller:coupon',   3, '/api/seller/coupon/**',     28, 1),
(26, '商家知识库',  'seller:knowledge',3, '/api/seller/knowledge/**',  29, 1),
-- Q8 裁决（2026-10-18）：平台级商品配置（类目/品牌/属性）单独一位，**不新造 category:admin**。
-- 与 product:write 的区别：write 是"商家维护自有商品"，admin 是"平台维护全局配置"。
(27, '平台商品配置','product:admin',  3, '/api/item/category/**',     30, 1),
-- Q10 裁决：仪表盘/统计独立成位（与 order:admin 分开 —— 看数据不等于能改订单）。
(28, '看板数据',    'dashboard:read', 3, '/api/admin/dashboard/**',   31, 1),
-- 批 8 裁决：审计日志查询独立成位（**不挂 user:read** —— 读审计不等于读用户资料）。
(29, '审计日志查询','audit:read',     3, '/api/admin/log/**',         32, 1),
-- Q1「每域两档」：发票域补读档（GET /page）。写档已拆 invoice:issue / invoice:void（Q2）。
(30, '发票查询',    'invoice:read',   3, '/api/admin/invoice/**',     33, 1),
-- 判断 A（2026-10-19，5b 签字）：**提现申请独立成位**，不与 seller:finance 合并。
-- 语义 = 「发起资金请求」，**不含打款权**（打款在管理端 review，属 finance:admin）。
-- 理由：Q7 已裁决员工角色另立项，届时「能看账、不能提现」必须可分。
(31, '商家提现',    'seller:withdrawal', 3, '/api/seller/finance/withdrawal', 34, 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `code` = VALUES(`code`), `type` = VALUES(`type`),
    `path` = VALUES(`path`), `sort_order` = VALUES(`sort_order`), `status` = VALUES(`status`);
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

-- RBAC0 拆位（2026-10-18）：
--   ROLE_ADMIN 必须**显式**持有 rbac:admin —— PermissionBits 的通配符对它是排除的，
--     只有种子能授予它（RbacSeedEscalationGuardTest 会校验"只能给 ROLE_ADMIN"）。
--   ROLE_SELLER 一次性拿全三位（Q3「存量不失权」）：拆位不能让商家丢能力。
--     注意：**不给 aftersale:write / pay:admin / finance:admin 等 admin 档** ——
--     商家侧暂无售后功能（after_sale 无 seller 维度），给位等于让商家读别家售后。
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `sys_permission` WHERE `code` = 'rbac:admin';

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 3, id FROM `sys_permission`
WHERE `code` IN ('product:write', 'seller:finance', 'seller:coupon', 'seller:knowledge', 'seller:withdrawal');
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

-- =====================================================================
-- 管理员 / 商家种子用户（本地联调占位；账号 admin、seller）
-- =====================================================================
-- ⚠️⚠️ 安全警告 ⚠️⚠️
--   以下账号为**硬编码弱口令的开发占位账号**，仅为本地联调提供可用的管理端 / 商家端身份：
--     - 严禁流向共享 / 测试 / 预发 / 生产等任何非本地环境；
--     - 任何非本地部署必须先删除这些行，或改为经运维注入的强口令账号；
--     - V5.2 起密码凭证的权威为 user_auth.credential（ADR-017）：本区块的 user 行只是资料载体，
--       「能否登录」只取决于下方 user_auth 行，「有何角色」只取决于下方 sys_user_role 行。
--       user.password / user.phone / user.role_type 均为 deprecated 列，登录链路不读它们
--       （见 user 表列注释与 RoleTypeDerivation：对外 roleType 由 RBAC 角色编码派生）。
--   账号 / 口令 / 角色（明文仅记录在本注释，禁止外传）：
--     - admin  / Admin@123456   → ROLE_ADMIN（sys_role 种子 id=4）
--     - seller / Seller@123456  → ROLE_SELLER（sys_role 种子 id=3；另带 seller + shop 记录，供商家端开发）
--   BCrypt 哈希生成方式（可复核，勿自行编造字符串）：Spring Security 6.5.2 ——
--     即 Spring Boot 3.5.4 所管理的版本，与 authorization-service 的 PasswordVerifierAdapter
--     注入的 PasswordEncoder bean 同源（BCryptPasswordEncoder 默认构造 = BCryptVersion $2A + strength 10）：
--       new BCryptPasswordEncoder().encode("Admin@123456")     → 下方 60 字符 $2a$10$… 字面量
--       new BCryptPasswordEncoder().encode("Seller@123456")    → 下方 60 字符 $2a$10$… 字面量
--     两个字面量均已用 matches(明文, 该字面量) == true 反向自证（且 matches("wrong-pass", …) == false）。
-- =====================================================================
-- admin 账号业务 ID 为定值（32-hex UUIDv7 形态）；BINARY(16) 列不可存 'admin-001' 字面量（ADR-050）
--   列序：user_id / username / phone / password / role_type / status / balance / register_time / create_time / update_time
--   register_time、create_time、update_time 为 NOT NULL 且无默认值 ⇒ 必须显式给（NOW()）；
--   status=1（正常）、balance=0（deprecated 列，恒 0）、role_type=2（deprecated 列，2=管理员，仅历史留痕）。
--   第 4 值 password 为 42a080f7 之前的历史占位哈希（明文不可考），该列 deprecated 且不被读取 ——
--   admin 的真实可用凭证是下面 user_auth 行（Admin@123456）。
INSERT INTO `user` (`user_id`, `username`, `phone`, `password`, `role_type`, `status`, `balance`, `register_time`, `create_time`, `update_time`)
VALUES (UNHEX('aaaaaaaa000070008000000000000001'), 'admin', '10000000000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 2, 1, 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- admin 的登录凭证（USERNAME + 密码）—— 按 user.id 关联，供 POST /api/auth/web/login 使用
--   列口径取证：user_auth DDL（identity_type ∈ USERNAME/PHONE/EMAIL/WECHAT/WECHAT_MINI）
--   + PasswordLoginStrategy（USERNAME 属非敏感标识 → 查 identifier_plain，不走盲索引）
--   + UserAuthRepositoryImpl.findByIdentity（identity_type + identifier_plain + status=1）
--   ⇒ identifier_plain='admin'，identifier_cipher/hash/mask 三列留 NULL，credential=BCrypt，status=1（有效）
--   幂等用 INSERT IGNORE（唯一键 uk_type_plain(identity_type, identifier_plain) 命中即跳过），
--   与下方 member_level_rule / task_def 种子同一写法；换口令时请先删该行再重跑（IGNORE 不覆盖已有值）。
--   ⚠ 互斥提示：可选的开发数据脚本 database/seed-data.js 也会写 USERNAME/'admin' 凭证行（口令 Dev123456），
--     与本行命中同一唯一键 —— 二者只应取其一（本文件是装库真相源；seed-data.js 非必需，且其业务 ID 仍是
--     ADR-050 之前的非 hex 形态）。先跑本文件时，seed-data.js 会因唯一键冲突整事务回滚。
INSERT IGNORE INTO `user_auth` (`user_id`, `identity_type`, `identifier_plain`, `credential`, `status`)
SELECT u.id, 'USERNAME', 'admin', '$2a$10$US9lcWT7eSp5fPuJzCm9/ePoEHIELv.xSrinyJW3RwiXBcKrXWPpS', 1
FROM `user` u WHERE u.user_id = UNHEX('aaaaaaaa000070008000000000000001');

-- 为管理员分配角色 (user 表 id 为自增，通过子查询获取)
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, 4 FROM `user` u WHERE u.user_id = UNHEX('aaaaaaaa000070008000000000000001')
ON DUPLICATE KEY UPDATE `role_id` = 4;

-- ---------------------------------------------------------------
-- 商家端开发用占位商家（seller 登录身份 + 商家资料 + 店铺）
--   前端商家端（apps/seller）与 merchant-service 的 /api/seller/** 需要一条
--   「可登录 + 已是商家 + 有店铺」的完整链路；此前 user 表只有 admin 一行、user_auth 无任何种子 ⇒ 无人能登录。
--   三张表按依赖顺序插入：user → user_auth / sys_user_role → seller → shop。
-- ---------------------------------------------------------------
-- seller 账号业务 ID 为定值（与 admin 同段，末位 0002；ADR-050 BINARY(16)）
--   phone 与 password 两个 deprecated 列对本行留 NULL（新行无历史值可留，凭证一律落 user_auth）：
--   手机号明文列 V5.2 起应由 phone_cipher/phone_hash/phone_mask 三字段承载，种子无法生成 PII 密文故不填。
INSERT INTO `user` (`user_id`, `username`, `phone`, `password`, `role_type`, `status`, `balance`, `register_time`, `create_time`, `update_time`)
VALUES (UNHEX('aaaaaaaa000070008000000000000002'), 'seller', NULL, NULL, 1, 1, 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- seller 的登录凭证（USERNAME + 密码），列口径与幂等写法同 admin（Seller@123456）
INSERT IGNORE INTO `user_auth` (`user_id`, `identity_type`, `identifier_plain`, `credential`, `status`)
SELECT u.id, 'USERNAME', 'seller', '$2a$10$6pzd4KGAF9p9v57/RHkD8ujDz7dMA72SuDh60fdPPNDscacOjsr4S', 1
FROM `user` u WHERE u.user_id = UNHEX('aaaaaaaa000070008000000000000002');

-- 分配 ROLE_SELLER —— role_id 按角色编码从上方 sys_role 种子（本文件 §九 首段，id=3）实取，不写死猜测值
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `sys_role` r ON r.code = 'ROLE_SELLER'
WHERE u.user_id = UNHEX('aaaaaaaa000070008000000000000002');

-- 商家身份（seller.user_id 指向 user.id 技术主键；1:1 由 uk_user_id 保证）
--   status=1（正常）⇒ 商家端无需再走入驻审批即可联调
INSERT INTO `seller` (`user_id`, `status`, `create_time`, `update_time`)
SELECT u.id, 1, NOW(), NOW()
FROM `user` u WHERE u.user_id = UNHEX('aaaaaaaa000070008000000000000002')
ON DUPLICATE KEY UPDATE `status` = 1;

-- 店铺（shop.merchant_id 指向 seller.id；1 商家 : N 店铺由 uk_merchant_shop_name 保证幂等，ADR-019）
--   status=1（正常）；contact_phone 为店铺对外联系电话（NOT NULL，非 PII 三字段列）
INSERT INTO `shop` (`merchant_id`, `shop_name`, `contact_phone`, `province`, `city`, `district`, `detail_address`, `status`, `create_time`, `update_time`)
SELECT s.id, 'IceAmericanoMall 测试旗舰店', '10000000001', '浙江省', '杭州市', '西湖区', '文三路 100 号 3 幢 501 室', 1, NOW(), NOW()
FROM `seller` s
JOIN `user` u ON u.id = s.user_id
WHERE u.user_id = UNHEX('aaaaaaaa000070008000000000000002')
ON DUPLICATE KEY UPDATE `status` = 1, `update_time` = NOW();

-- =====================================================================
-- 重新启用外键检查
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- V5.8 促销引擎 + 会员等级（F5/F6，ADR-043/044；V5.8 引入，ADR-045 后无迁移文件）
-- =====================================================================

CREATE TABLE `promotion` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`           VARCHAR(50) NOT NULL COMMENT '活动名称',
    `seller_id`      BIGINT      NOT NULL COMMENT '商家 user_id（店铺级活动；跨店下单已禁，一单仅触达单商家）',
    `type`           TINYINT     NOT NULL COMMENT '1=满减(FIXED) 2=满折(PERCENTAGE)',
    `threshold`      INT         NOT NULL COMMENT '门槛：订单商品总额 ≥ threshold 生效（分）',
    `discount_value` INT         NOT NULL COMMENT '满减=减免金额（分）；满折=折扣率万分比（8500=8.5折）',
    `max_discount`   INT NOT NULL DEFAULT 0 COMMENT '满折封顶减免额（分）；0=不封顶（满减恒等于 discount_value）',
    `start_time`     DATETIME    NOT NULL COMMENT '开始时间',
    `end_time`       DATETIME    NOT NULL COMMENT '结束时间',
    `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1=未开始 2=进行中 3=已结束（时间窗推导）',
    `version`        INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_time` (`seller_id`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺满减/满折促销活动';

CREATE TABLE `member_level_rule` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `level`         VARCHAR(16) NOT NULL COMMENT '等级（NORMAL/SILVER/GOLD/PLATINUM，与 MemberLevelEnum 对齐）',
    `threshold`     INT         NOT NULL COMMENT '累计成长值门槛（分）',
    `discount_rate` INT         NOT NULL COMMENT '会员折扣率万分比（10000=不折扣，9800=98%）',
    `points_rate`   INT NOT NULL DEFAULT 100 COMMENT '积分倍率百分比（100=1倍，作用于下单奖励积分）',
    `sort`          INT NOT NULL DEFAULT 0 COMMENT '比较顺序（升序）',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级规则（门槛/折扣率/积分倍率）';

INSERT IGNORE INTO `member_level_rule` (`level`, `threshold`, `discount_rate`, `points_rate`, `sort`) VALUES
    ('NORMAL',   0,       10000, 100, 0),
    ('SILVER',   100000,  9900,  110, 1),
    ('GOLD',     500000,  9800,  120, 2),
    ('PLATINUM', 2000000, 9500,  150, 3);

CREATE TABLE `member_growth_log` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT      NOT NULL COMMENT '买家 user_id',
    `order_no`      BINARY(16)  NOT NULL COMMENT '来源订单号（幂等锚点，orders.order_no 复制，UUIDv7 原始字节，ADR-050）',
    `growth_amount` INT         NOT NULL COMMENT '本单成长值（=订单实付金额，分）',
    `after_level`   VARCHAR(16) NOT NULL COMMENT '入账后等级',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员成长值入账流水（只升不降）';

-- =====================================================================
-- V5.10 每日任务体系（F9，ADR-047；按 2026-10-05 口径直接修改本快照，无迁移文件）
-- ---------------------------------------------------------------------
-- task_def：任务定义（表驱动，Phase 1 seed 两个每日任务；运营扩展直接加行）
-- task_record：任务完成记录（uk_user_task_date 每日幂等锚点，一用户一任务一天一次）
-- 积分发放经 PointsManager 单出口（points_log type=3 任务奖励），与任务记录同事务。
-- ---------------------------------------------------------------------

CREATE TABLE `task_def` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`         VARCHAR(32)  NOT NULL COMMENT '任务编码（DAILY_ORDER/DAILY_REVIEW，调用方锚定）',
    `name`         VARCHAR(50)  NOT NULL COMMENT '任务名称',
    `type`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=每日重置（Phase 1 唯一值）',
    `reward_points` INT         NOT NULL COMMENT '完成奖励积分',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务定义（表驱动）';

INSERT IGNORE INTO `task_def` (`code`, `name`, `type`, `reward_points`, `status`) VALUES
    ('DAILY_ORDER',  '每日完成 1 笔订单确认', 1, 20, 1),
    ('DAILY_REVIEW', '每日发表 1 条评价',     1, 10, 1);

CREATE TABLE `task_record` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT      NOT NULL COMMENT '用户 user_id',
    `task_code`     VARCHAR(32) NOT NULL COMMENT '任务编码',
    `reward_points` INT         NOT NULL COMMENT '实发积分',
    `biz_date`      DATE        NOT NULL COMMENT '业务日（每日重置锚点）',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_task_date` (`user_id`, `task_code`, `biz_date`),
    KEY `idx_user_date` (`user_id`, `biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务完成记录（每日幂等）';

-- =====================================================================
-- V5.12 店铺装修（F11）+ 内容社区笔记（F12，ADR-048；按 2026-10-05 口径直接修改本快照）
-- ---------------------------------------------------------------------
-- shop_decoration：店铺装修（merchant 域，与 shop 1:1；JSON 以字符串列存储，领域层校验）
-- note / note_spu / note_like：种草笔记（item 域，与 review 同属商品 UGC）
-- ---------------------------------------------------------------------

CREATE TABLE `shop_decoration` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id`         BIGINT       NOT NULL COMMENT '店铺 id（shop.id，1:1）',
    `announcement`    VARCHAR(200) DEFAULT NULL COMMENT '店铺公告',
    `banners`         VARCHAR(2000) DEFAULT NULL COMMENT '轮播图 JSON 数组（≤5 个 URL）',
    `hot_spu_ids` VARCHAR(1000) DEFAULT NULL COMMENT '推荐商品位 JSON 数组（≤8 个 SPU 业务键 32-hex，展示端过滤非在售；ADR-062）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺装修（与 shop 1:1）';

CREATE TABLE `note` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL COMMENT '作者 user_id',
    `title`       VARCHAR(50)  NOT NULL COMMENT '标题',
    `content`     VARCHAR(2000) NOT NULL COMMENT '正文（Phase 1 纯文本）',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图',
    `images`      VARCHAR(2000) DEFAULT NULL COMMENT '图集 JSON 数组（≤9 个 URL）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已发布 2=已删除（软删）',
    `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数，原子维护）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='种草笔记';

CREATE TABLE `note_spu` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `note_id`    BIGINT NOT NULL COMMENT '笔记 id',
    `spu_id` BIGINT NOT NULL COMMENT '关联商品 id（≤5，发布时校验存在且在售）',
    `sort`       INT    NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_note_spu` (`note_id`, `spu_id`),
    KEY `idx_spu` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记关联商品';

CREATE TABLE `note_like` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `note_id`     BIGINT NOT NULL COMMENT '笔记 id',
    `user_id`     BIGINT NOT NULL COMMENT '点赞用户 user_id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_note_user` (`note_id`, `user_id`),
    KEY `idx_note` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记点赞（uk 幂等）';

-- =====================================================================
-- V5.14 拼团（F13，ADR-049；schema 直改本文件，无迁移文件）
-- 两段式参团：下单不占团位（未支付走既有 30 分钟超时取消），支付成功后 I53 参团；
-- 参团竞态由条件更新 + 唯一键兜底；团超时由 GroupBuyExpireJob 结团并联动退款。
-- =====================================================================

-- 拼团活动（marketing-service 域；管理端 AD54-56，复用 promotion/flash 时间窗推导先例）
CREATE TABLE `group_buy_activity` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`             VARCHAR(50) NOT NULL COMMENT '活动名称',
    `seller_id`        BIGINT      NOT NULL COMMENT '商家 user_id（店铺级活动）',
    `spu_id`       BIGINT      NOT NULL COMMENT '商品ID',
    `sku_id`           BIGINT      NOT NULL COMMENT 'SKU ID（拼团仅该 SKU 参与）',
    `group_price`      INT         NOT NULL COMMENT '拼团价（分，>0）',
    `required_members` TINYINT     NOT NULL COMMENT '成团人数（≥2）',
    `time_limit_hours` INT         NOT NULL COMMENT '成团时限（小时，开团后超时未满员即失败）',
    `start_time`       DATETIME    NOT NULL COMMENT '活动开始时间',
    `end_time`         DATETIME    NOT NULL COMMENT '活动结束时间',
    `status`           TINYINT     NOT NULL DEFAULT 1 COMMENT '1=未开始 2=进行中 3=已结束（时间窗推导）',
    `version`          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_seller_time` (`seller_id`, `start_time`, `end_time`),
    KEY `idx_status` (`status`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拼团活动';

-- 拼团团（成团状态机：1拼团中 2已成团 3已失败；参团条件更新 +1，满员条件置已成团）
CREATE TABLE `group_team` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `team_no`          BINARY(16)  NOT NULL COMMENT '团号（业务唯一键，UUIDv7 原始字节，ADR-050）',
    `activity_id`      BIGINT      NOT NULL COMMENT '拼团活动ID',
    `leader_user_id`   BIGINT      NOT NULL COMMENT '团长 user_id（开团者）',
    `required_members` TINYINT     NOT NULL COMMENT '成团人数（开团时从活动快照）',
    `current_members`  TINYINT     NOT NULL DEFAULT 0 COMMENT '当前人数（已支付入团成员数）',
    `status`           TINYINT     NOT NULL DEFAULT 1 COMMENT '1=拼团中 2=已成团 3=已失败（超时未满员）',
    `expire_time`      DATETIME    NOT NULL COMMENT '成团截止时间（开团时间 + time_limit_hours）',
    `success_time`     DATETIME    DEFAULT NULL COMMENT '成团时间',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_team_no` (`team_no`),
    KEY `idx_activity_status_expire` (`activity_id`, `status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拼团团（成团状态机）';

-- 拼团团成员（uk_order_no 幂等锚点：一单一团一次；uk_team_user 防同团重复参团）
CREATE TABLE `group_team_member` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `team_id`     BIGINT      NOT NULL COMMENT '团ID',
    `user_id`     BIGINT      NOT NULL COMMENT '参团用户 user_id（团长亦入列）',
    `order_no`    BINARY(16)  NOT NULL COMMENT '关联拼团订单号（orders.order_no，UUIDv7 原始字节，ADR-050）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_team_user` (`team_id`, `user_id`),
    KEY `idx_team` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拼团团成员';

-- =====================================================================
-- V5.15 发票（F14，PRD 01-PRD §十五 已定稿；schema 直改本文件，无迁移文件）
-- 决策：D1 商家开票（Phase 1 管理端代办模拟开具）/ D3 金额=payAmount 快照 /
--       D4 全额退款自动冲红 / D5 一单一有效票（uk_order_active 多 NULL）/ O4 税号 PII 三字段。
-- =====================================================================

-- 发票单（trade-service 域；买家 INV1-INV5 + 管理端 AD57-60；状态机 1待开具 2已开具 3已冲红 4已作废）
    CREATE TABLE `invoice` (
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `invoice_no`          BINARY(16)   NOT NULL COMMENT '发票申请号（业务唯一键，UUIDv7 原始字节，ADR-050）',
    `order_no`            BINARY(16)   NOT NULL COMMENT '关联订单号（orders.order_no，UUIDv7 原始字节，ADR-050）',
    `active_flag`         TINYINT      NULL COMMENT '1=有效（待开具/已开具）；NULL=终态（已冲红/已作废）——与 order_no 组成 uk_order_active，多 NULL 特性实现一单一有效票',
    `user_id`             BIGINT       NOT NULL COMMENT '买家 user_id',
    `seller_id`           BIGINT       NOT NULL COMMENT '开票主体商家 user_id（= orders.seller_id，决策 D1）',
    `type`                TINYINT      NOT NULL COMMENT '抬头类型：1=PERSONAL 2=COMPANY',
    `title`               VARCHAR(100) NOT NULL COMMENT '抬头快照（个人=姓名；企业=企业名称）',
    `tax_no_cipher`       VARCHAR(512) NULL COMMENT '企业税号密文（AES-256-GCM，PII 三字段口径 O4）',
    `tax_no_hash`         VARCHAR(128) NULL COMMENT '企业税号盲索引（HMAC-SHA256）',
    `tax_no_mask`         VARCHAR(64)  NULL COMMENT '企业税号脱敏展示',
    `email_cipher`        VARCHAR(512) NULL COMMENT '交付邮箱密文（PII 三字段口径）',
    `email_hash`          VARCHAR(128) NULL COMMENT '交付邮箱盲索引',
    `email_mask`          VARCHAR(64)  NULL COMMENT '交付邮箱脱敏展示',
    `amount`              INT          NOT NULL COMMENT '开票金额（分）= 申请时 payAmount 快照（决策 D3）',
    `status`              TINYINT      NOT NULL COMMENT '状态：1=待开具 2=已开具 3=已冲红 4=已作废（条件迁移状态机）',
    `issue_time`          DATETIME     NULL COMMENT '开具时间',
    `mock_invoice_no`     VARCHAR(64)  NULL COMMENT '模拟票号（Phase 2 语义替换为服务商票号）',
    `pdf_key`             VARCHAR(255) NULL COMMENT '样票 MinIO objectKey（决策 O5）',
    `red_for_id`          BIGINT       NULL COMMENT '冲红关联：换开新票指向被冲红原票 id',
    `red_reason`          VARCHAR(255) NULL COMMENT '冲红/作废原因（自动冲红=系统原因码；人工必填）',
    `partial_refund_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '部分退款待人工处理标记（决策 D4/O6）',
    `urge_time`           DATETIME     NULL COMMENT '最近催开时间（24h 限频判定，O10）',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_invoice_no` (`invoice_no`),
    UNIQUE KEY `uk_order_active` (`order_no`, `active_flag`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_seller_id` (`seller_id`),
    KEY `idx_status` (`status`),
    KEY `idx_partial_refund` (`partial_refund_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票单（模拟开具 Phase 1）';

-- 常用发票抬头（决策 O2 随批简版；发票单为抬头快照，本表仅服务申请体验）
CREATE TABLE `invoice_title` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT       NOT NULL COMMENT '所属用户',
    `type`          TINYINT      NOT NULL COMMENT '1=PERSONAL 2=COMPANY',
    `title`         VARCHAR(100) NOT NULL COMMENT '抬头（个人=姓名；企业=企业名称）',
    `tax_no_cipher` VARCHAR(512) NULL COMMENT '企业税号密文（PII 三字段口径）',
    `tax_no_hash`   VARCHAR(128) NULL COMMENT '企业税号盲索引',
    `tax_no_mask`   VARCHAR(64)  NULL COMMENT '企业税号脱敏展示',
    `is_default`    TINYINT      NULL COMMENT '1=默认抬头；uk_user_default 多 NULL → 一用户至多一条默认',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记（@TableLogic）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_default` (`user_id`, `is_default`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='常用发票抬头';

-- =====================================================================
-- V5.16 知识图谱 Phase 1（F15，PRD 01-PRD §十六 已定稿；D1 混合最小形态——
-- MySQL 真相源（本表）+ ES kg_edges 边索引投影；schema 直改本文件，无迁移文件）
-- =====================================================================

-- 类目搭配规则（item-service 域；管理端 AD61-63 治理 + AD64 重建；PAIR_WITH 边唯一新增 MySQL 真相源）
CREATE TABLE `pairing_rule` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `src_category_id` BIGINT      NOT NULL COMMENT '源类目（如：手机）',
    `dst_category_id` BIGINT      NOT NULL COMMENT '目标类目（如：手机配件）',
    `weight`          INT         NOT NULL DEFAULT 0 COMMENT '排序权重（越大越靠前）',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用即时生效于邻居查询）',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_src_dst` (`src_category_id`, `dst_category_id`),
    KEY `idx_src` (`src_category_id`),
    KEY `idx_dst` (`dst_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='类目搭配规则（KG PAIR_WITH 边真相源，平台级类目粒度）';

-- =====================================================================
-- V5.17 多币种 Phase 1（F16，PRD 01-PRD §十七 已定稿；D1-B 折中：展示换算 +
-- 订单快照锁汇；资金链路恒 CNY 分零改动；schema 直改本文件，无迁移文件）
-- =====================================================================

-- 币种（wallet-service 域；ISO 4217；CNY 为主币种不入表）
CREATE TABLE `currency` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`        VARCHAR(3)   NOT NULL COMMENT '币种代码（ISO 4217，如 USD/EUR/JPY）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '币种名称',
    `symbol`      VARCHAR(8)   NOT NULL COMMENT '符号（如 $/€/¥）',
    `exponent`    TINYINT      NOT NULL COMMENT '最小单位指数（ISO 4217：USD/EUR=2、JPY/KRW=0、BHD=3）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用后 C 端列表不返回，换算请求回落 CNY）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='币种（展示换算；O8 裁决种子六币种）';

-- 汇率（一行一币 upsert；定点整数 rate_scaled = 每 1 CNY 兑目标币主单位数 × 10⁶，O6 不做历史版本化）
CREATE TABLE `exchange_rate` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`        VARCHAR(3)   NOT NULL COMMENT '币种代码（对应 currency.code）',
    `rate_scaled` BIGINT       NOT NULL COMMENT '定点整数汇率：每 1 元人民币兑换目标币主单位数 × 10⁶（如 USD 0.140300 → 140300）',
    `source`      VARCHAR(16)  NOT NULL COMMENT '来源：API（定时拉取）/ MANUAL（管理端人工修正）',
    `effect_time` DATETIME     NOT NULL COMMENT '生效时间（上次成功更新时点；失败保留上次值 + 告警）',
    `updater_id`  BIGINT       NULL COMMENT '修正人（MANUAL 时记录；API 拉取为 NULL）',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='展示汇率（定点整数，O6 裁决不做历史版本化）';

INSERT IGNORE INTO `currency` (`code`, `name`, `symbol`, `exponent`, `status`) VALUES
    ('USD', '美元',   '$', 2, 1),
    ('EUR', '欧元',   '€', 2, 1),
    ('JPY', '日元',   '¥', 0, 1),
    ('KRW', '韩元',   '₩', 0, 1),
    ('GBP', '英镑',   '£', 2, 1),
    ('HKD', '港币',   'HK$', 2, 1);

-- 初始占位汇率（source=MANUAL；接入汇率 API 后由定时拉取日更覆盖——O2/O9 裁决）
INSERT IGNORE INTO `exchange_rate` (`code`, `rate_scaled`, `source`, `effect_time`, `remark`) VALUES
    ('USD', 140300,    'MANUAL', NOW(), '初始占位汇率，接入汇率 API 后日更覆盖（O9）'),
    ('EUR', 130500,    'MANUAL', NOW(), '初始占位汇率，接入汇率 API 后日更覆盖（O9）'),
    ('JPY', 20910000,  'MANUAL', NOW(), '初始占位汇率，接入汇率 API 后日更覆盖（O9）'),
    ('KRW', 190000000, 'MANUAL', NOW(), '初始占位汇率，接入汇率 API 后日更覆盖（O9）'),
    ('GBP', 110800,    'MANUAL', NOW(), '初始占位汇率，接入汇率 API 后日更覆盖（O9）'),
    ('HKD', 1095000,   'MANUAL', NOW(), '初始占位汇率，接入汇率 API 后日更覆盖（O9）');

-- =====================================================================
-- 发票联动补偿（I56 全额退款冲红 / I60 部分退款置标 —— 联动失败的承载表）
-- 由 pay-service 在发票联动失败时落一条 PENDING 记录（「静默丢失」升级为「可补偿」）；
-- 调度器按 (status, next_retry_at) 拾取、先用 lease_until 认领再重试（租约到期自动回到可认领集合，
-- 故不需要回收任务/不需要 PROCESSING 状态），或由管理端人工收敛。
-- schema 直改本文件，无迁移文件（ADR-045）。
-- =====================================================================

-- 发票联动补偿记录（pay-service 域；幂等锚点 = order_no + scene_ref + kind）
    CREATE TABLE `invoice_link_compensation` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no`      BINARY(16)   NOT NULL COMMENT '关联订单号（orders.order_no 复制，UUIDv7 原始字节，ADR-050；与 refund_order.order_no 同口径）',
    `scene_ref`     VARCHAR(64)  NOT NULL COMMENT '联动场景号（= refund_order.biz_ref：售后 AS:{afterSaleId}、拼团 GB:{orderNo}，ADR-049）',
    `kind`          TINYINT      NOT NULL COMMENT '补偿类型：1=VOID 全额退款冲红/作废（I56） 2=PARTIAL 部分退款置标待人工处理（I60）',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=PENDING 待补偿 1=DONE 已补偿 2=FAILED_PERMANENT 永久失败（重试耗尽/人工终止）',
    `retry_count`   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数（调度器每次拾取重试 +1）',
    `next_retry_at` DATETIME     NULL COMMENT '下次可拾取时间（落库即当前时间——调度器就绪后即刻可拾取；重试失败按退避推迟）',
    `lease_until`   DATETIME     NULL COMMENT '认领租约到期时间；NULL=未认领。到期后自动可被再次认领（无需回收任务）',
    `last_error`    VARCHAR(500) NULL COMMENT '最近一次失败原因摘要（异常类型 + 消息，不含敏感细节；超长截断至 500）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_scene_kind` (`order_no`, `scene_ref`, `kind`),
    KEY `idx_status_next_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票联动补偿记录（I56 全额冲红 / I60 部分退款置标的联动失败承载，PENDING 待重试或人工收敛）';


-- 出款停留告警的去重账（多实例共用；按 payout_no+代次 唯一）
DROP TABLE IF EXISTS `payout_alert_log`;
CREATE TABLE `payout_alert_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `payout_no` VARCHAR(64) NOT NULL COMMENT '出款幂等键',
    `alert_gen` INT NOT NULL COMMENT '告警代次 = 停留分钟 / N',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_payout_alert` (`payout_no`, `alert_gen`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出款停留告警去重账（保留 180 天，每日批删）';
