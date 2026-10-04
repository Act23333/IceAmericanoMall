package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单仓储实现 —— MyBatis-Plus 访问 {@code refund_order} 表，PO ↔ 聚合转换内聚于此。
 * 状态迁移一律条件更新，逻辑自 {@code RefundOrderServiceImpl} 原样收编。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RefundOrderRepositoryImpl implements RefundOrderRepository {

    private final RefundOrderMapper refundOrderMapper;

    @Override
    public RefundOrder getByRefundNo(String refundNo) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .eq(RefundOrderPO::getRefundNo, refundNo))
                .stream().findFirst().map(RefundOrderRepositoryImpl::toDomain).orElse(null);
    }

    @Override
    public RefundOrder getByAfterSaleId(Long afterSaleId) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .eq(RefundOrderPO::getAfterSaleId, afterSaleId))
                .stream().findFirst().map(RefundOrderRepositoryImpl::toDomain).orElse(null);
    }

    @Override
    public RefundOrder createProcessing(Long afterSaleId, String orderNo, String payOrderNo, Long userId,
                                        String channel, int refundAmount) {
        RefundOrder refundOrder = RefundOrder.newProcessing(afterSaleId, orderNo, payOrderNo, userId,
                channel, refundAmount);
        RefundOrderPO po = toPO(refundOrder);
        refundOrderMapper.insert(po);
        refundOrder.setId(po.getId());
        return refundOrder;
    }

    @Override
    public boolean markSuccessIfProcessing(String refundNo, String channelRefundNo) {
        return refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundNo, refundNo)
                .eq(RefundOrderPO::getStatus, RefundStatusEnum.PROCESSING.getCode())
                .set(RefundOrderPO::getStatus, RefundStatusEnum.SUCCESS.getCode())
                .set(RefundOrderPO::getChannelRefundNo, channelRefundNo)
                .set(RefundOrderPO::getFailReason, null)) > 0;
    }

    @Override
    public boolean markFailedIfProcessing(String refundNo, String failReason) {
        return refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundNo, refundNo)
                .eq(RefundOrderPO::getStatus, RefundStatusEnum.PROCESSING.getCode())
                .set(RefundOrderPO::getStatus, RefundStatusEnum.FAILED.getCode())
                .set(RefundOrderPO::getFailReason, failReason)) > 0;
    }

    @Override
    public boolean markProcessingIfFailed(String refundNo) {
        return refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundNo, refundNo)
                .eq(RefundOrderPO::getStatus, RefundStatusEnum.FAILED.getCode())
                .set(RefundOrderPO::getStatus, RefundStatusEnum.PROCESSING.getCode())
                .set(RefundOrderPO::getFailReason, null)) > 0;
    }

    @Override
    public List<RefundOrder> listStaleProcessing(LocalDateTime threshold, int limit) {
        return refundOrderMapper.selectStaleProcessing(RefundStatusEnum.PROCESSING.getCode(), threshold, limit)
                .stream().map(RefundOrderRepositoryImpl::toDomain).toList();
    }

    private static RefundOrder toDomain(RefundOrderPO po) {
        return RefundOrder.builder()
                .id(po.getId())
                .refundNo(po.getRefundNo())
                .orderNo(po.getOrderNo())
                .payOrderNo(po.getPayOrderNo())
                .afterSaleId(po.getAfterSaleId())
                .userId(po.getUserId())
                .channel(po.getChannel())
                .refundAmount(po.getRefundAmount())
                .status(po.getStatus())
                .channelRefundNo(po.getChannelRefundNo())
                .failReason(po.getFailReason())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private static RefundOrderPO toPO(RefundOrder refundOrder) {
        RefundOrderPO po = new RefundOrderPO();
        po.setId(refundOrder.getId());
        po.setRefundNo(refundOrder.getRefundNo());
        po.setOrderNo(refundOrder.getOrderNo());
        po.setPayOrderNo(refundOrder.getPayOrderNo());
        po.setAfterSaleId(refundOrder.getAfterSaleId());
        po.setUserId(refundOrder.getUserId());
        po.setChannel(refundOrder.getChannel());
        po.setRefundAmount(refundOrder.getRefundAmount());
        po.setStatus(refundOrder.getStatus());
        po.setChannelRefundNo(refundOrder.getChannelRefundNo());
        po.setFailReason(refundOrder.getFailReason());
        return po;
    }
}
