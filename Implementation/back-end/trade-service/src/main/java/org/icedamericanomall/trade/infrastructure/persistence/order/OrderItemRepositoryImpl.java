package org.icedamericanomall.trade.infrastructure.persistence.order;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.repository.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单项仓储实现 —— PO 与聚合互转在本类完成。
 */
@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemMapper orderItemMapper;

    @Override
    public OrderItem findById(Long id) {
        return OrderRepositoryImpl.toDomain(orderItemMapper.selectById(id));
    }

    @Override
    public List<OrderItem> findByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderItemMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItemPO>()
                                .in(OrderItemPO::getOrderId, orderIds))
                .stream().map(OrderRepositoryImpl::toDomain).toList();
    }
}
