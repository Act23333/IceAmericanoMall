package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.mapper.OrderItemMapper;
import org.icedAmericanoMall.mapper.OrderMapper;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单领域服务实现 —— 单域原子操作与状态流转，不含 Feign 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrderWithItems(OrderEntity order, List<OrderItemEntity> items) {
        order.setCreateTime(LocalDateTime.now());
        save(order);
        for (OrderItemEntity item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
    }

    @Override
    public OrderEntity getByOrderNo(String orderNo) {
        return lambdaQuery().eq(OrderEntity::getOrderNo, orderNo).one();
    }

    @Override
    public List<OrderItemEntity> listItems(Long orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, orderId));
    }

    @Override
    public IPage<OrderEntity> pageMyOrders(Long userId, Integer status, int page, int size) {
        var wrapper = lambdaQuery().eq(OrderEntity::getUserId, userId);
        if (status != null) {
            wrapper.eq(OrderEntity::getStatus, status);
        }
        wrapper.orderByDesc(OrderEntity::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public IPage<OrderEntity> pageSellerOrders(Long sellerId, Integer status, int page, int size) {
        var wrapper = lambdaQuery().eq(OrderEntity::getSellerId, sellerId);
        if (status != null) {
            wrapper.eq(OrderEntity::getStatus, status);
        }
        wrapper.orderByDesc(OrderEntity::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * <pre>
     * Scenario: 用户取消待付款订单
     *   Given 订单状态为"待付款"且当前用户是订单所属人
     *   When 用户请求取消订单
     *   Then 订单状态变更为"已取消"并记录关闭时间（库存回滚由 Manager 处理）
     * Scenario: 非待付款状态拒绝取消 / 非订单所属人无权操作 → 抛出异常
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, Long userId) {
        OrderEntity order = requireOwnedOrder(orderNo, userId);
        if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "仅待付款订单可取消");
        }
        lambdaUpdate()
                .eq(OrderEntity::getId, order.getId())
                .set(OrderEntity::getStatus, OrderStatusEnum.CANCELLED.getCode())
                .set(OrderEntity::getCloseTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(String orderNo, Long userId) {
        OrderEntity order = requireOwnedOrder(orderNo, userId);
        if (order.getStatus() != OrderStatusEnum.PENDING_RECEIPT.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "仅待收货订单可确认收货");
        }
        lambdaUpdate()
                .eq(OrderEntity::getId, order.getId())
                .set(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .set(OrderEntity::getEndTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(String orderNo, Long sellerId, String logisticsNumber, String logisticsCompany) {
        OrderEntity order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_SHIPMENT.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "仅待发货订单可发货");
        }
        lambdaUpdate()
                .eq(OrderEntity::getId, order.getId())
                .set(OrderEntity::getStatus, OrderStatusEnum.PENDING_RECEIPT.getCode())
                .set(OrderEntity::getConsignTime, LocalDateTime.now())
                .update();
    }

    @Override
    public IPage<OrderEntity> pageAllOrders(Integer status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<OrderEntity>();
        if (status != null) {
            wrapper.eq(OrderEntity::getStatus, status);
        }
        wrapper.orderByDesc(OrderEntity::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<OrderEntity> listTimeoutPending(LocalDateTime cutoff) {
        return lambdaQuery()
                .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                .lt(OrderEntity::getCreateTime, cutoff)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrder(Long orderId) {
        lambdaUpdate()
                .eq(OrderEntity::getId, orderId)
                .set(OrderEntity::getStatus, OrderStatusEnum.CANCELLED.getCode())
                .set(OrderEntity::getCloseTime, LocalDateTime.now())
                .update();
    }

    /** 查询订单并校验归属，供 cancel/confirm 共用。 */
    private OrderEntity requireOwnedOrder(String orderNo, Long userId) {
        OrderEntity order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        return order;
    }
}
