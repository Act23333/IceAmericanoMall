package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.SkuClient;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;
import org.icedAmericanoMall.dto.StockOpDTO;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.mapper.OrderItemMapper;
import org.icedAmericanoMall.mapper.OrderLogisticsMapper;
import org.icedAmericanoMall.mapper.OrderMapper;
import org.icedAmericanoMall.service.OrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    private final OrderItemMapper orderItemMapper;
    private final OrderLogisticsMapper orderLogisticsMapper;
    private final SkuClient skuClient;

    public OrderServiceImpl(OrderItemMapper orderItemMapper,
                            OrderLogisticsMapper orderLogisticsMapper,
                            SkuClient skuClient) {
        this.orderItemMapper = orderItemMapper;
        this.orderLogisticsMapper = orderLogisticsMapper;
        this.skuClient = skuClient;
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, Long userId) {
        OrderEntity order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "仅待付款订单可取消");
        }
        lambdaUpdate()
                .eq(OrderEntity::getId, order.getId())
                .set(OrderEntity::getStatus, OrderStatusEnum.CANCELLED.getCode())
                .set(OrderEntity::getCloseTime, LocalDateTime.now())
                .update();

        // Restore stock via Feign to item-service
        restoreOrderStock(order.getId());
    }

    private void restoreOrderStock(Long orderId) {
        List<OrderItemEntity> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderId, orderId));
        if (items.isEmpty()) {
            return;
        }
        List<StockOpDTO> stockOps = items.stream().map(item -> {
            StockOpDTO op = new StockOpDTO();
            op.setSkuId(item.getSkuId());
            op.setQuantity(item.getQuantity());
            return op;
        }).collect(Collectors.toList());

        try {
            skuClient.restoreStock(stockOps);
            log.info("订单取消，库存已恢复: orderId={}", orderId);
        } catch (Exception e) {
            log.error("库存恢复失败，需人工处理: orderId={}", orderId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(String orderNo, Long userId) {
        OrderEntity order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
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

        OrderLogisticsEntity logistics = new OrderLogisticsEntity();
        logistics.setOrderId(order.getId());
        logistics.setLogisticsNumber(logisticsNumber);
        logistics.setLogisticsCompany(logisticsCompany);
        logistics.setContact(order.getReceiverName());
        logistics.setMobile(order.getReceiverPhone());
        orderLogisticsMapper.insert(logistics);
    }
}
