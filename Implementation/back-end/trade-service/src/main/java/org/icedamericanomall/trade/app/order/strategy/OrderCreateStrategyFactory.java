package org.icedamericanomall.trade.app.order.strategy;

import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V4.1: 订单创建策略工厂（京东/淘宝标准）。
 * Spring 自动注入所有 OrderCreateStrategy 实现类，按 supportedType() 建立路由表。
 */
@Component
public class OrderCreateStrategyFactory {

    private final Map<OrderTypeEnum, OrderCreateStrategy> strategyMap;

    public OrderCreateStrategyFactory(List<OrderCreateStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(OrderCreateStrategy::supportedType, s -> s));
    }

    public OrderCreateStrategy getStrategy(OrderTypeEnum type) {
        OrderCreateStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的订单类型: " + type);
        }
        return strategy;
    }
}
