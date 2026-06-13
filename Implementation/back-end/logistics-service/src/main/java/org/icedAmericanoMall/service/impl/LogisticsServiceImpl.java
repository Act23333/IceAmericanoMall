package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.OrderLogisticsEntity;
import org.icedAmericanoMall.mapper.OrderLogisticsMapper;
import org.icedAmericanoMall.service.LogisticsService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;

@Service
public class LogisticsServiceImpl extends ServiceImpl<OrderLogisticsMapper, OrderLogisticsEntity> implements LogisticsService {

    @Override
    public OrderLogisticsEntity getByOrderId(Long orderId) {
        OrderLogisticsEntity entity = getById(orderId);
        if (entity == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "物流信息不存在");
        }
        return entity;
    }
}
