package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;

public interface LogisticsService extends IService<OrderLogisticsEntity> {

    OrderLogisticsEntity getByOrderId(Long orderId);

    void createLogistics(OrderLogisticsEntity entity);
}
