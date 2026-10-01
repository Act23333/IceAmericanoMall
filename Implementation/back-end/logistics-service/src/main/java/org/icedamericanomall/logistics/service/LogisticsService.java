package org.icedamericanomall.logistics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.logistics.domain.entity.OrderLogisticsEntity;

public interface LogisticsService extends IService<OrderLogisticsEntity> {

    OrderLogisticsEntity getByOrderId(Long orderId);

    void createLogistics(OrderLogisticsEntity entity);

    void updateStatus(Long orderId, Integer status);
}
