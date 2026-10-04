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

    @Mapping(target = "items", ignore = true)
    OrderVO toVO(Order order);

    OrderItemVO toItemVO(OrderItem item);

    List<OrderItemVO> toItemVOs(List<OrderItem> items);
}
