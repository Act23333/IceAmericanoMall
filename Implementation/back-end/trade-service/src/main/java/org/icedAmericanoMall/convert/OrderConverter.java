package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.vo.OrderItemVO;
import org.icedAmericanoMall.domain.vo.OrderVO;
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
