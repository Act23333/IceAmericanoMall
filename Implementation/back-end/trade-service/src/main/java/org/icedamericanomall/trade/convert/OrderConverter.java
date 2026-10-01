package org.icedamericanomall.trade.convert;

import org.icedamericanomall.trade.domain.entity.OrderEntity;
import org.icedamericanomall.trade.domain.entity.OrderItemEntity;
import org.icedamericanomall.trade.domain.vo.OrderItemVO;
import org.icedamericanomall.trade.domain.vo.OrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderConverter {

    @Mapping(target = "items", ignore = true)
    OrderVO entityToVO(OrderEntity entity);

    OrderItemVO itemEntityToVO(OrderItemEntity entity);

    List<OrderItemVO> itemEntitiesToVOs(List<OrderItemEntity> entities);
}
