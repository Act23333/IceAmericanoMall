package org.icedamericanomall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.OrderLogisticsEntity;

public interface LogisticsService extends IService<OrderLogisticsEntity> {

    OrderLogisticsEntity getByOrderId(Long orderId);

    void createLogistics(OrderLogisticsEntity entity);

    void updateStatus(Long orderId, Integer status);
}
