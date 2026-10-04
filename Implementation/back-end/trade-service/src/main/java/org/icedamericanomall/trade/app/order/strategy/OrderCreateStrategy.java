package org.icedamericanomall.trade.app.order.strategy;

import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;

/**
 * V4.1: 订单创建策略接口（京东/淘宝统一订单中心标准）。
 * 不同订单类型实现各自的前置校验、订单构建、后置处理逻辑。
 */
public interface OrderCreateStrategy {

    /** 此策略处理的订单类型 */
    OrderTypeEnum supportedType();

    /** 创建订单前的校验（库存、活动状态、优惠券等） */
    void validate(OrderCreateContext ctx);

    /** 构建订单实体 + 填充上下文（金额、优惠券折扣等） */
    Order buildOrder(OrderCreateContext ctx);

    /** 创建成功后的后置处理（清购物车、异步通知等） */
    void afterCreate(OrderCreateContext ctx, Order order);
}
