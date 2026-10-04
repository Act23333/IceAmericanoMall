package org.icedamericanomall.trade.domain.repository;

import org.icedamericanomall.trade.domain.model.order.OrderItem;

import java.util.List;

/**
 * 订单项仓储（领域端口）—— 实现方：{@code infrastructure.persistence.order.OrderItemRepositoryImpl}。
 *
 * <p>拆单发货的条件更新（一明细只发一次）由 {@link OrderRepository#shipPackage} 统一编排，
 * 本端口只承载订单项的独立读写。
 */
public interface OrderItemRepository {

    /** 按主键取订单项（不存在返回 null）。 */
    OrderItem findById(Long id);

    /** 批量查询订单项（消除按单循环查询的 N+1）。 */
    List<OrderItem> findByOrderIds(List<Long> orderIds);
}
