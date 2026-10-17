package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.domain.enums.PayStatusEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 支付单仓储实现 —— MyBatis-Plus 访问 {@code pay_order} 表，PO ↔ 聚合转换内聚于此。
 * 状态迁移一律条件更新（WHERE 带期望状态），逻辑自 {@code PayOrderServiceImpl} 原样收编。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PayOrderRepositoryImpl implements PayOrderRepository {

    /** 支付超时窗口（分钟），与 {@link PayOrder#PAY_TIMEOUT_MINUTES} 同源。 */
    private static final int PAY_TIMEOUT_MINUTES = PayOrder.PAY_TIMEOUT_MINUTES;

    private final PayOrderMapper payOrderMapper;

    @Override
    public PayOrder getByBizOrderNo(String bizOrderNo) {
        // 防多行炸裂：同一业务订单历史可能有多条支付记录，取首行
        return payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderPO>()
                        .eq(PayOrderPO::getBizOrderNo, IdGenerator.toBytes(bizOrderNo))
                        .orderByDesc(PayOrderPO::getId))
                .stream().findFirst().map(PayOrderRepositoryImpl::toDomain).orElse(null);
    }

    @Override
    public PayOrder getByPayOrderNo(String payOrderNo) {
        return payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderPO>()
                        .eq(PayOrderPO::getPayOrderNo, IdGenerator.toBytesOrNull(payOrderNo)))
                .stream().findFirst().map(PayOrderRepositoryImpl::toDomain).orElse(null);
    }

    /**
     * 按对外渠道交易号取（回调 / 对账的寻址入口）。
     *
     * <p>{@code channel_trade_no} 是 <b>VARCHAR</b> 列（不是 BINARY(16) 业务键），故等值参数直传
     * {@code String} —— 此处<b>不得</b>套 {@code IdGenerator.toBytes}（那会把 20 位短号当 32 位 hex 解析，
     * 直接抛 IllegalArgumentException）。空值返回 {@code null}（渠道报文缺字段时的防御口径）。
     */
    @Override
    public PayOrder getByChannelTradeNo(String channelTradeNo) {
        if (channelTradeNo == null || channelTradeNo.isBlank()) {
            return null;
        }
        return payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderPO>()
                        .eq(PayOrderPO::getChannelTradeNo, channelTradeNo))
                .stream().findFirst().map(PayOrderRepositoryImpl::toDomain).orElse(null);
    }

    @Override
    public PayOrder createPending(String bizOrderNo, Long userId, int amount,
                                  String channelCode, int payType) {
        PayOrder payOrder = PayOrder.newPending(bizOrderNo, userId, amount, channelCode, payType);
        PayOrderPO po = toPO(payOrder);
        payOrderMapper.insert(po);
        payOrder.setId(po.getId());
        return payOrder;
    }

    @Override
    public PayOrder resetPending(PayOrder existing, int amount, String channelCode, int payType) {
        LocalDateTime payOverTime = LocalDateTime.now().plusMinutes(PAY_TIMEOUT_MINUTES);
        boolean updated = payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrderPO>()
                .eq(PayOrderPO::getId, existing.getId())
                .eq(PayOrderPO::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .set(PayOrderPO::getAmount, amount)
                .set(PayOrderPO::getPayChannelCode, channelCode)
                .set(PayOrderPO::getQrCodeUrl, null)
                .set(PayOrderPO::getPayType, payType)
                .set(PayOrderPO::getPayOverTime, payOverTime)) > 0;
        if (!updated) {
            log.info("支付单状态已变更，无法复用: payOrderNo={}", existing.getPayOrderNo());
            return null;
        }
        existing.applyPendingReset(amount, channelCode, payType, payOverTime);
        return existing;
    }

    @Override
    public boolean attachPayUrl(PayOrder payOrder, String payUrl) {
        boolean updated = payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrderPO>()
                .eq(PayOrderPO::getId, payOrder.getId())
                .eq(PayOrderPO::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .set(PayOrderPO::getQrCodeUrl, payUrl)) > 0;
        if (updated) {
            payOrder.applyPayUrl(payUrl);
        } else {
            log.info("支付单状态已变更，支付链接不回填: payOrderNo={}", payOrder.getPayOrderNo());
        }
        return updated;
    }

    @Override
    public boolean markSuccessIfPending(String payOrderNo, String resultCode) {
        return payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrderPO>()
                .eq(PayOrderPO::getPayOrderNo, IdGenerator.toBytesOrNull(payOrderNo))
                .eq(PayOrderPO::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .set(PayOrderPO::getStatus, PayStatusEnum.SUCCESS.getCode())
                .set(PayOrderPO::getResultCode, resultCode)
                .set(PayOrderPO::getPaySuccessTime, LocalDateTime.now())) > 0;
    }

    @Override
    public boolean markBalancePaidIfPending(Long payOrderId) {
        return payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrderPO>()
                .eq(PayOrderPO::getId, payOrderId)
                .eq(PayOrderPO::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .set(PayOrderPO::getStatus, PayStatusEnum.SUCCESS.getCode())
                .set(PayOrderPO::getPaySuccessTime, LocalDateTime.now())) > 0;
    }

    @Override
    public boolean cancelPending(Long payOrderId) {
        return payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrderPO>()
                .eq(PayOrderPO::getId, payOrderId)
                .eq(PayOrderPO::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .set(PayOrderPO::getStatus, PayStatusEnum.TIMEOUT_CANCEL.getCode())) > 0;
    }

    @Override
    public List<PayOrder> listTimeout(LocalDateTime cutoff) {
        return payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderPO>()
                        .eq(PayOrderPO::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                        .lt(PayOrderPO::getPayOverTime, cutoff))
                .stream().map(PayOrderRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<PayOrder> listPaidByDateAndChannel(LocalDate billDate, String channel) {
        return payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderPO>()
                        .eq(PayOrderPO::getStatus, PayStatusEnum.SUCCESS.getCode())
                        .eq(PayOrderPO::getPayChannelCode, channel)
                        .apply("DATE(pay_success_time) = {0}", billDate))
                .stream().map(PayOrderRepositoryImpl::toDomain).toList();
    }

    private static PayOrder toDomain(PayOrderPO po) {
        return PayOrder.builder()
                .id(po.getId())
                .bizOrderNo(IdGenerator.toHexString(po.getBizOrderNo()))
                .payOrderNo(IdGenerator.toHexString(po.getPayOrderNo()))
                .channelTradeNo(po.getChannelTradeNo())
                .bizUserId(po.getBizUserId())
                .payChannelCode(po.getPayChannelCode())
                .amount(po.getAmount())
                .payType(po.getPayType())
                .status(po.getStatus())
                .expandJson(po.getExpandJson())
                .resultCode(po.getResultCode())
                .resultMsg(po.getResultMsg())
                .paySuccessTime(po.getPaySuccessTime())
                .payOverTime(po.getPayOverTime())
                .qrCodeUrl(po.getQrCodeUrl())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private static PayOrderPO toPO(PayOrder payOrder) {
        PayOrderPO po = new PayOrderPO();
        po.setId(payOrder.getId());
        po.setBizOrderNo(IdGenerator.toBytes(payOrder.getBizOrderNo()));
        po.setPayOrderNo(IdGenerator.toBytes(payOrder.getPayOrderNo()));
        po.setChannelTradeNo(payOrder.getChannelTradeNo());
        po.setBizUserId(payOrder.getBizUserId());
        po.setPayChannelCode(payOrder.getPayChannelCode());
        po.setAmount(payOrder.getAmount());
        po.setPayType(payOrder.getPayType());
        po.setStatus(payOrder.getStatus());
        po.setExpandJson(payOrder.getExpandJson());
        po.setResultCode(payOrder.getResultCode());
        po.setResultMsg(payOrder.getResultMsg());
        po.setPaySuccessTime(payOrder.getPaySuccessTime());
        po.setPayOverTime(payOrder.getPayOverTime());
        po.setQrCodeUrl(payOrder.getQrCodeUrl());
        return po;
    }
}
