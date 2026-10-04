-- ======================================================
-- V5.5: 资金线 P0（F1 买家退款资金流 + F4 渠道对账）
--   规格来源：project-docs/backend/04-Data-Model.md §十二 12.1 / 12.4
--   契约来源：project-docs/contract/05-API-Specification.md §4.7.3 / §4.13.14 / §4.14.15-17
--   迁移只做加法：不改动任何既有表（refund_order 经 after_sale_id / order_no 逻辑关联，
--   不建外键——与 pay_order.biz_order_no 同口径，避免与交易库耦合）
--
--   归属：
--     refund_order / reconciliation_batch / reconciliation_detail —— pay-service
--     wallet_transaction                                          —— wallet-service
--
--   注：`Initialize.sql`（全量最新快照）已同步追加本文件四张表的建表语句，逐列一致。
--       新装库只跑 Initialize.sql；存量库按版本顺序执行本文件。
-- ======================================================

-- ------------------------------------------------------
-- 1. refund_order（退款单，pay-service）
--    一售后单一次退款：uk_after_sale 是 I40 幂等的兜底锚点
--    状态：0-处理中 / 1-成功 / 2-失败（失败可经管理端 AD43 重试）
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refund_order` (
    `id`                BIGINT       AUTO_INCREMENT COMMENT '技术主键',
    `refund_no`         CHAR(36)     NOT NULL COMMENT '退款单号（业务唯一键，UUID；对外与幂等重试的定位键）',
    `order_no`          VARCHAR(64)  NOT NULL COMMENT '关联订单号（orders.order_no，无外键）',
    `pay_order_no`      VARCHAR(64)  NOT NULL COMMENT '关联支付单号（pay_order.pay_order_no）',
    `after_sale_id`     BIGINT       NOT NULL COMMENT '关联售后单（after_sale.id）——一售后单一次退款',
    `user_id`           BIGINT       NOT NULL COMMENT '买家用户ID',
    `channel`           VARCHAR(16)  NOT NULL COMMENT '退款渠道：WECHAT / ALIPAY / BALANCE（与原支付渠道一致，原路退回）',
    `refund_amount`     INT          NOT NULL COMMENT '退款金额（分），申请时已校验 ≤ 订单实付 payAmount',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-成功，2-失败',
    `channel_refund_no` VARCHAR(64)  DEFAULT NULL COMMENT '渠道退款单号（微信 refund_id 等，回调回填）',
    `fail_reason`       VARCHAR(255) DEFAULT NULL COMMENT '失败原因（渠道返回或系统错误摘要，不含敏感细节）',
    `create_time`       DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_after_sale` (`after_sale_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单（pay-service 退款域）';

-- ------------------------------------------------------
-- 2. wallet_transaction（钱包流水，wallet-service）
--    幂等键 transaction_no：重复请求按唯一约束/先查判定，幂等成功（不重复加余额）
--    type 表达方向，amount 恒正
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wallet_transaction` (
    `id`             BIGINT       AUTO_INCREMENT COMMENT '技术主键',
    `transaction_no` CHAR(36)     NOT NULL COMMENT '流水号（幂等键，UUID）',
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

-- ------------------------------------------------------
-- 3. reconciliation_batch（对账批次，pay-service）
--    uk_date_channel：同日同渠道一批次——重复执行"先删后写"的幂等锚点 + 并发兜底
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reconciliation_batch` (
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

-- ------------------------------------------------------
-- 4. reconciliation_detail（对账差异明细，pay-service）
--    result 四态：1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致
--    Phase 1 仅落库记录供管理端查看，不自动调账
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reconciliation_detail` (
    `id`             BIGINT      AUTO_INCREMENT COMMENT '技术主键',
    `batch_id`       BIGINT      NOT NULL COMMENT '所属批次（reconciliation_batch.id）',
    `pay_order_no`   VARCHAR(64) NOT NULL COMMENT '本地支付单号（比对键）',
    `channel_txn_id` VARCHAR(64) DEFAULT NULL COMMENT '渠道交易号（渠道账单有则回填）',
    `channel_amount` INT         DEFAULT NULL COMMENT '渠道账单金额（分；渠道无此单为 NULL）',
    `local_amount`   INT         DEFAULT NULL COMMENT '本地金额（分；本地无此单为 NULL）',
    `result`         TINYINT     NOT NULL COMMENT '比对结果：1-匹配，2-渠道有本地无，3-本地有渠道无，4-金额不一致',
    `create_time`    DATETIME    NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_batch_result` (`batch_id`, `result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差异明细（pay-service）';
