package org.icedamericanomall.infrastructure;

import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.repository.OrderRepository;
import org.icedamericanomall.mapper.OrderMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 订单仓储 MyBatis 实现 — Infrastructure 层
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Optional<OrderEntity> findById(Long id) {
        return Optional.ofNullable(orderMapper.selectById(id));
    }

    @Override
    public Optional<OrderEntity> findByOrderNo(String orderNo) {
        return Optional.ofNullable(
                orderMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderEntity>()
                                .eq(OrderEntity::getOrderNo, orderNo)));
    }

    @Override
    public int updateStatusWithVersion(OrderEntity order, int newStatus) {
        order.setStatus(newStatus);
        return orderMapper.updateById(order);
    }
}
