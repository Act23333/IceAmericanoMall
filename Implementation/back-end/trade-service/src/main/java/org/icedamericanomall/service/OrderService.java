package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.OrderItemEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单领域服务 —— 仅负责订单/订单项的原子读写与状态流转。
 * 跨服务编排（库存回滚、积分发放、物流创建）由 OrderManager 承接。
 */
public interface OrderService extends IService<OrderEntity> {

    void createOrderWithItems(OrderEntity order, List<OrderItemEntity> items);

    OrderEntity getByOrderNo(String orderNo);

    /** 查询订单项列表（下单快照）。 */
    List<OrderItemEntity> listItems(Long orderId);

    /** 通用订单分页：userId/sellerId 可同时传（互斥）；均为 null 时查所有。 */
    IPage<OrderEntity> pageOrders(Long userId, Long sellerId, Integer status, int page, int size);

    /** 用户取消待付款订单（纯状态流转，库存回滚由 Manager 处理）。 */
    void cancelOrder(String orderNo, Long userId);

    /** 用户确认收货（纯状态流转，积分发放由 Manager 处理）。 */
    void confirmReceipt(String orderNo, Long userId);

    /** 商家发货（纯状态流转，物流创建由 Manager 处理）。 */
    void shipOrder(String orderNo, Long sellerId, String logisticsNumber, String logisticsCompany);

    /** 查询超时未支付订单（供超时任务使用）。 */
    List<OrderEntity> listTimeoutPending(LocalDateTime cutoff);

    /** 关闭超时订单（置为已取消，无归属校验，供超时任务使用）。 */
    void closeTimeoutOrder(Long orderId);
}
