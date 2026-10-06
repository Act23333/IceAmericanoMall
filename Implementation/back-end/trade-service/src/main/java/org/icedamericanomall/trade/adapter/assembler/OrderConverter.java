package org.icedamericanomall.trade.adapter.assembler;

import org.icedamericanomall.trade.adapter.response.OrderItemVO;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 订单装配器（适配层）—— 领域聚合 → 视图对象。聚合不泄漏到适配层之外。
 */
@Mapper(componentModel = "spring")
public interface OrderConverter {

    /**
     * 聚合 → 订单视图。{@code displayCurrency} 同名自动映射；定点汇率快照对外口径为
     * {@code displayRateScaled}（与 ia-api {@code CurrencyRateCO.rateScaled} 同名对齐），
     * 源字段 {@code displayRate} 需显式改名映射。快照为可空增量：未快照（CNY/降级）时
     * 两字段映射为 {@code null}（既有报文不含该键或出 null，前端按可空字段消费）。
     */
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "displayRateScaled", source = "displayRate")
    OrderVO toVO(Order order);

    OrderItemVO toItemVO(OrderItem item);

    List<OrderItemVO> toItemVOs(List<OrderItem> items);
}
