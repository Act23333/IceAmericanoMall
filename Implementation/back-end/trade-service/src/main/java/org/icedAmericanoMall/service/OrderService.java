package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.OrderItemEntity;

import java.util.List;

public interface OrderService extends IService<OrderEntity> {

    void createOrderWithItems(OrderEntity order, List<OrderItemEntity> items);

    OrderEntity getByOrderNo(String orderNo);

    IPage<OrderEntity> pageMyOrders(Long userId, Integer status, int page, int size);

    IPage<OrderEntity> pageSellerOrders(Long sellerId, Integer status, int page, int size);

    void cancelOrder(String orderNo, Long userId);

    void confirmReceipt(String orderNo, Long userId);

    void shipOrder(String orderNo, Long sellerId, String logisticsNumber, String logisticsCompany);
}
