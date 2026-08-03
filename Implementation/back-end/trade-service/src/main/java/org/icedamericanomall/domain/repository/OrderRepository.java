package org.icedamericanomall.domain.repository;

import org.icedamericanomall.domain.entity.OrderEntity;

import java.util.Optional;

/**
 * 订单仓储接口 — Domain 层定义契约
 *
 * DDD: 接口在 domain 层，实现在 infrastructure 层（当前复用 mapper/）。
 * 充血 OrderEntity 的状态变更通过此仓储持久化。
 */
public interface OrderRepository {

    Optional<OrderEntity> findById(Long id);

    Optional<OrderEntity> findByOrderNo(String orderNo);

    /** 原子更新订单状态（乐观锁 version 防并发） */
    int updateStatusWithVersion(OrderEntity order, int newStatus);
}
