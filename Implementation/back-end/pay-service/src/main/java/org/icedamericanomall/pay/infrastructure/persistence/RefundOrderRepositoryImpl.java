package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
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
                        .eq(RefundOrderPO::getRefundNo, IdGenerator.toBytesOrNull(refundNo)))
                .stream().findFirst().map(RefundOrderRepositoryImpl::toDomain).orElse(null);
    }

    /**
     * 按对外退款项号取（退款回调 / 退款状态查询的寻址入口）。
     *
     * <p>{@code out_refund_no} 是 <b>VARCHAR</b> 列（不是 BINARY(16) 业务键），等值参数直传
     * {@code String} —— 套 {@code IdGenerator.toBytes} 会把 20 位短号当 32 位 hex 解析而抛异常。
     */
    @Override
    public RefundOrder getByOutRefundNo(String outRefundNo) {
        if (outRefundNo == null || outRefundNo.isBlank()) {
            return null;
        }
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .eq(RefundOrderPO::getOutRefundNo, outRefundNo))
                .stream().findFirst().map(RefundOrderRepositoryImpl::toDomain).orElse(null);
    }

    @Override
    public RefundOrder getByAfterSaleId(Long afterSaleId) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .eq(RefundOrderPO::getAfterSaleId, afterSaleId))
                .stream().findFirst().map(RefundOrderRepositoryImpl::toDomain).orElse(null);
    }

    @Override
    public RefundOrder getByBizRef(String bizRef) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                        .eq(RefundOrderPO::getBizRef, bizRef))
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
    public RefundOrder createOrderRefundProcessing(String orderNo, String payOrderNo, Long userId,
                                                   String channel, int refundAmount, String bizRef) {
        RefundOrder refundOrder = RefundOrder.newOrderProcessing(orderNo, payOrderNo, userId,
                channel, refundAmount, bizRef);
        RefundOrderPO po = toPO(refundOrder);
        refundOrderMapper.insert(po);
        refundOrder.setId(po.getId());
        return refundOrder;
    }

    @Override
    public boolean markSuccessIfProcessing(String refundNo, String channelRefundNo) {
        return refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundNo, IdGenerator.toBytesOrNull(refundNo))
                .eq(RefundOrderPO::getStatus, RefundStatusEnum.PROCESSING.getCode())
                .set(RefundOrderPO::getStatus, RefundStatusEnum.SUCCESS.getCode())
                .set(RefundOrderPO::getChannelRefundNo, channelRefundNo)
                .set(RefundOrderPO::getFailReason, null)) > 0;
    }

    @Override
    public boolean markFailedIfProcessing(String refundNo, String failReason) {
        return refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundNo, IdGenerator.toBytesOrNull(refundNo))
                .eq(RefundOrderPO::getStatus, RefundStatusEnum.PROCESSING.getCode())
                .set(RefundOrderPO::getStatus, RefundStatusEnum.FAILED.getCode())
                .set(RefundOrderPO::getFailReason, failReason)) > 0;
    }

    @Override
    public boolean markProcessingIfFailed(String refundNo) {
        return refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getRefundNo, IdGenerator.toBytesOrNull(refundNo))
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
                .refundNo(IdGenerator.toHexString(po.getRefundNo()))
                .outRefundNo(po.getOutRefundNo())
                .orderNo(IdGenerator.toHexString(po.getOrderNo()))
                .payOrderNo(IdGenerator.toHexString(po.getPayOrderNo()))
                .afterSaleId(po.getAfterSaleId())
                .bizRef(po.getBizRef())
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
        po.setRefundNo(IdGenerator.toBytes(refundOrder.getRefundNo()));
        po.setOutRefundNo(refundOrder.getOutRefundNo());
        po.setOrderNo(IdGenerator.toBytes(refundOrder.getOrderNo()));
        po.setPayOrderNo(IdGenerator.toBytes(refundOrder.getPayOrderNo()));
        po.setAfterSaleId(refundOrder.getAfterSaleId());
        po.setBizRef(refundOrder.getBizRef());
        po.setUserId(refundOrder.getUserId());
        po.setChannel(refundOrder.getChannel());
        po.setRefundAmount(refundOrder.getRefundAmount());
        po.setStatus(refundOrder.getStatus());
        po.setChannelRefundNo(refundOrder.getChannelRefundNo());
        po.setFailReason(refundOrder.getFailReason());
        return po;
    }
}
