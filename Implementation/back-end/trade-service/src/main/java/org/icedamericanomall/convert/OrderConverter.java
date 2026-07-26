package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.OrderItemEntity;
import org.icedamericanomall.domain.vo.OrderItemVO;
import org.icedamericanomall.domain.vo.OrderVO;
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
