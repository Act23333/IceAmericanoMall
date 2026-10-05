-- =====================================================================
-- V5.8 促销引擎 + 会员等级（P1 第二批：F5 满减/满折促销 + F6 会员等级升降级与权益）
-- 契约：05-API-Specification §4.13.8b AD48-AD50 / §4.14.8b I44 / §4.14.1b I45-I46（V5.8.0）
-- 决策：project-docs/adr/043-pricing-center-and-promotion-engine.md
--       project-docs/adr/044-member-level-and-benefits.md
-- 数据模型：04-Data-Model §十二 12.5（F5）/ 12.6（F6）
-- 纪律：仅适用于"已按版本顺序执行完 V1.1.0 → V5.7 的存量库"；新装库直接执行
--       Initialize.sql 全量快照（本文件的三张表已并入快照，勿重复执行）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- F5 促销活动（marketing-service 域）：店铺级满减/满折，时间窗推导状态
-- 计价口径（ADR-043）：同一订单命中多个生效活动时取减免金额最大者（同类型不叠加），
-- 与会员折扣、用户券三者可叠加；总额守卫 discountAmount ≤ totalAmount。
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- F6 会员等级规则（user-service 域）：Phase 1 seed 预置，管理端编辑留 roadmap
-- 成长值 = 累计实付金额（分，订单完成入账）；等级只升不降（Phase 1，ADR-044）。
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- F6 会员成长值入账流水（user-service 域）：uk_order_no 幂等锚点（一单一次成长）
-- ---------------------------------------------------------------------
CREATE TABLE `member_growth_log` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT      NOT NULL COMMENT '买家 user_id',
    `order_no`      VARCHAR(32) NOT NULL COMMENT '来源订单号（幂等锚点）',
    `growth_amount` INT         NOT NULL COMMENT '本单成长值（=订单实付金额，分）',
    `after_level`   VARCHAR(16) NOT NULL COMMENT '入账后等级',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员成长值入账流水（只升不降）';
