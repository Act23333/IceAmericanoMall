-- V4.1: 订单类型字段（京东/淘宝统一订单中心标准）
-- 统一所有订单类型到 orders 表，按 order_type 路由不同创建策略
ALTER TABLE `orders`
    ADD COLUMN `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型: 1=NORMAL购物车, 2=DIRECT立即购买, 3=FLASH_SALE秒杀, 4=PRESALE预售';

-- 现有订单默认 type=1（购物车下单），因 V4.0 的直接购买(type=2)尚未写入 DB（见 OrderServiceImpl 修复）
