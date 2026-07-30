-- V4.3: 优惠券适用范围 + 叠加规则（京东标准）
-- scopeType: 券适用的商品范围（全场/品类/单品）
-- stackRule: 多券叠加规则（互斥/可叠加）

ALTER TABLE `coupon`
    ADD COLUMN `scope_type`   TINYINT NOT NULL DEFAULT 1 COMMENT '适用范围: 1=ALL全场, 2=CATEGORY品类, 3=PRODUCT单品',
    ADD COLUMN `scope_values` JSON NULL COMMENT '适用范围的ID列表: categoryIds 或 productIds',
    ADD COLUMN `stack_rule`   TINYINT NOT NULL DEFAULT 1 COMMENT '叠加规则: 1=MUTUAL_EXCLUSIVE互斥, 2=STACKABLE可叠加',
    ADD COLUMN `stack_group`  VARCHAR(32) NULL COMMENT '叠加分组ID，同组券可叠加使用';
