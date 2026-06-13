package org.icedAmericanoMall.convert;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.vo.OrderItemVO;
import org.icedAmericanoMall.domain.vo.OrderVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-03T21:54:52+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class OrderConverterImpl implements OrderConverter {

    @Override
    public OrderVO entityToVO(OrderEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OrderVO orderVO = new OrderVO();

        orderVO.setId( entity.getId() );
        orderVO.setOrderNo( entity.getOrderNo() );
        orderVO.setUserId( entity.getUserId() );
        orderVO.setSellerId( entity.getSellerId() );
        orderVO.setTotalAmount( entity.getTotalAmount() );
        orderVO.setPayAmount( entity.getPayAmount() );
        orderVO.setDiscountAmount( entity.getDiscountAmount() );
        orderVO.setStatus( entity.getStatus() );
        orderVO.setPaymentType( entity.getPaymentType() );
        orderVO.setReceiverName( entity.getReceiverName() );
        orderVO.setReceiverPhone( entity.getReceiverPhone() );
        orderVO.setReceiverAddress( entity.getReceiverAddress() );
        orderVO.setCreateTime( entity.getCreateTime() );
        orderVO.setPayTime( entity.getPayTime() );
        orderVO.setConsignTime( entity.getConsignTime() );
        orderVO.setEndTime( entity.getEndTime() );

        return orderVO;
    }

    @Override
    public OrderItemVO itemEntityToVO(OrderItemEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OrderItemVO orderItemVO = new OrderItemVO();

        orderItemVO.setId( entity.getId() );
        orderItemVO.setSkuId( entity.getSkuId() );
        orderItemVO.setProductName( entity.getProductName() );
        orderItemVO.setSkuSpec( entity.getSkuSpec() );
        orderItemVO.setPrice( entity.getPrice() );
        orderItemVO.setQuantity( entity.getQuantity() );
        orderItemVO.setSubTotal( entity.getSubTotal() );
        orderItemVO.setImage( entity.getImage() );

        return orderItemVO;
    }

    @Override
    public List<OrderItemVO> itemEntitiesToVOs(List<OrderItemEntity> entities) {
        if ( entities == null ) {
            return null;
        }

        List<OrderItemVO> list = new ArrayList<OrderItemVO>( entities.size() );
        for ( OrderItemEntity orderItemEntity : entities ) {
            list.add( itemEntityToVO( orderItemEntity ) );
        }

        return list;
    }
}
