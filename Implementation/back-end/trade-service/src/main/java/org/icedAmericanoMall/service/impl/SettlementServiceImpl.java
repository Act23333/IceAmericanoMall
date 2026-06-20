package org.icedAmericanoMall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.OrderEntity;
import org.icedAmericanoMall.domain.entity.SettlementEntity;
import org.icedAmericanoMall.enums.OrderStatusEnum;
import org.icedAmericanoMall.mapper.SettlementMapper;
import org.icedAmericanoMall.service.OrderService;
import org.icedAmericanoMall.service.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SettlementServiceImpl extends ServiceImpl<SettlementMapper, SettlementEntity> implements SettlementService {

    private final OrderService orderService;

    public SettlementServiceImpl(OrderService orderService) { this.orderService = orderService; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettlementEntity generate(Long sellerId, String start, String end) {
        LocalDate periodStart = LocalDate.parse(start), periodEnd = LocalDate.parse(end);
        List<OrderEntity> orders = orderService.lambdaQuery()
                .eq(OrderEntity::getSellerId, sellerId)
                .eq(OrderEntity::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .ge(OrderEntity::getEndTime, periodStart.atStartOfDay())
                .lt(OrderEntity::getEndTime, periodEnd.plusDays(1).atStartOfDay())
                .list();

        int total = orders.stream().mapToInt(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
        int commission = (int)(total * 0.05); // 平台抽成 5%
        int settlementAmount = total - commission;

        SettlementEntity s = new SettlementEntity();
        s.setSettlementNo(IdUtil.fastSimpleUUID());
        s.setSellerId(sellerId);
        s.setPeriodStart(periodStart); s.setPeriodEnd(periodEnd);
        s.setOrderCount(orders.size()); s.setTotalAmount(total);
        s.setCommission(commission); s.setSettlementAmount(settlementAmount);
        s.setStatus(1);
        save(s);
        return s;
    }

    @Override
    public IPage<SettlementEntity> pageBySeller(Long sellerId, int page, int size) {
        return lambdaQuery().eq(SettlementEntity::getSellerId, sellerId)
                .orderByDesc(SettlementEntity::getCreateTime).page(new Page<>(page, size));
    }
}
