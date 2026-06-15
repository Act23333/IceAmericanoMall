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

    /**
     * <pre>
     * Scenario: 原子创建订单及订单项
     *   Given 订单实体和订单项列表已由 OrderManager 组装完毕
     *   When 调用 createOrderWithItems
     *   Then 在同一事务中保存订单和所有订单项
     *   And 订单项关联订单ID
     * </pre>
     */
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

    /**
     * <pre>
     * Scenario: 用户取消待付款订单
     *   Given 订单状态为"待付款"
     *   And 当前用户是订单所属人
     *   When 用户请求取消订单
     *   Then 订单状态变更为"已取消"
     *   And 记录关闭时间
     *   And 回滚已扣减的 SKU 库存
     *
     * Scenario: 非待付款状态拒绝取消
     *   Given 订单状态不是"待付款"
     *   When 用户请求取消订单
     *   Then 抛出 BizException "仅待付款订单可取消"
     *
     * Scenario: 非订单所属人无权操作
     *   Given 订单属于用户A
     *   When 用户B请求取消订单
     *   Then 抛出 ForbiddenException "无权操作该订单"
     * </pre>
     */
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

        // 释放被锁定的库存
        restoreOrderStock(order.getId());
    }

    /**
     * <pre>
     * Scenario: 用户确认收货
     *   Given 订单状态为"待收货"
     *   And 当前用户是订单所属人
     *   When 用户点击确认收货
     *   Then 订单状态变更为"已完成"
     *   And 记录完成时间
     * </pre>
     */
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

    /**
     * <pre>
     * Scenario: 商家发货
     *   Given 订单状态为"待发货"
     *   And 当前商家是订单所属商家
     *   When 商家填写物流单号和物流公司并提交发货
     *   Then 订单状态变更为"待收货"
     *   And 创建物流记录（物流单号、物流公司、收件人、联系电话）
     * </pre>
     */
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

    /**
     * 恢复订单关联的SKU库存 —— 用户取消 / 超时取消共用。
     * 通过 Feign 调用 item-service 的 restoreStock 接口批量恢复。
     */
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
}
