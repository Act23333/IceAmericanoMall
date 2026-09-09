package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedamericanomall.domain.entity.OrderLogisticsEntity;
import org.icedamericanomall.enums.LogisticsStatusEnum;
import org.icedamericanomall.mapper.OrderLogisticsMapper;
import org.icedamericanomall.service.LogisticsService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * <pre>
 * Scenario: 创建物流记录
 *   Given trade-service 调用 InternalLogisticsController.createLogistics
 *   When 创建物流记录
 *   Then 物流状态初始化为"待揽收"(PENDING)
 *
 * Scenario: 更新物流状态
 *   Given 物流记录已存在
 *   When 调用 updateStatus
 *   Then 物流状态更新为指定值
 * </pre>
 */
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

    @Override
    public void createLogistics(OrderLogisticsEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(LogisticsStatusEnum.PENDING.getCode());
        }
        save(entity);
    }

    @Override
    public void updateStatus(Long orderId, Integer status) {
        OrderLogisticsEntity entity = getByOrderId(orderId);
        entity.setStatus(status);
        updateById(entity);
    }
}
