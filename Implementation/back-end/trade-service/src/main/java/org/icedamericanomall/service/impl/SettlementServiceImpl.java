package org.icedamericanomall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.SettlementEntity;
import org.icedamericanomall.enums.OrderStatusEnum;
import org.icedamericanomall.enums.SettlementStatusEnum;
import org.icedamericanomall.mapper.SettlementMapper;
import org.icedamericanomall.service.OrderService;
import org.icedamericanomall.service.SettlementService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SettlementServiceImpl extends ServiceImpl<SettlementMapper, SettlementEntity> implements SettlementService {

    /** 平台抽成比例 5%。 */
    private static final double COMMISSION_RATE = 0.05;

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
        int commission = (int) (total * COMMISSION_RATE);
        int settlementAmount = total - commission;

        SettlementEntity s = new SettlementEntity();
        s.setSettlementNo(IdUtil.fastSimpleUUID());
        s.setSellerId(sellerId);
        s.setPeriodStart(periodStart);
        s.setPeriodEnd(periodEnd);
        s.setOrderCount(orders.size());
        s.setTotalAmount(total);
        s.setCommission(commission);
        s.setSettlementAmount(settlementAmount);
        s.setStatus(SettlementStatusEnum.PENDING.getCode());
        save(s);
        return s;
    }

    @Override
    public IPage<SettlementEntity> pageBySeller(Long sellerId, int page, int size) {
        return lambdaQuery().eq(SettlementEntity::getSellerId, sellerId)
                .orderByDesc(SettlementEntity::getCreateTime).page(new Page<>(page, size));
    }

    @Override
    public SettlementEntity getSellerSettlement(Long id, Long sellerId) {
        SettlementEntity s = getById(id);
        if (s == null || !s.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return s;
    }

    @Override
    public int availableBalance(Long sellerId) {
        return lambdaQuery()
                .eq(SettlementEntity::getSellerId, sellerId)
                .eq(SettlementEntity::getStatus, SettlementStatusEnum.SETTLED.getCode())
                .list().stream()
                .mapToInt(s -> s.getSettlementAmount() != null ? s.getSettlementAmount() : 0)
                .sum();
    }

    @Override
    public IPage<SettlementEntity> pageAll(int page, int size) {
        return lambdaQuery().orderByDesc(SettlementEntity::getCreateTime).page(new Page<>(page, size));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSellerSettlementsPaidOut(Long sellerId) {
        lambdaUpdate()
                .eq(SettlementEntity::getSellerId, sellerId)
                .eq(SettlementEntity::getStatus, SettlementStatusEnum.SETTLED.getCode())
                .set(SettlementEntity::getStatus, SettlementStatusEnum.PAID_OUT.getCode())
                .update();
    }
}
