package org.icedAmericanoMall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.enums.PayStatusEnum;
import org.icedAmericanoMall.enums.PayTypeEnum;
import org.icedAmericanoMall.mapper.PayOrderMapper;
import org.icedAmericanoMall.service.PayOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单领域服务实现 —— 单域原子操作，不含 Feign / 支付渠道调用。
 */
@Slf4j
@Service
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrderEntity> implements PayOrderService {

    private static final int PAY_TIMEOUT_MINUTES = 30;
    private static final String CHANNEL_BALANCE = "BALANCE";

    @Override
    public PayOrderEntity getByBizOrderNo(String bizOrderNo) {
        return lambdaQuery().eq(PayOrderEntity::getBizOrderNo, bizOrderNo).one();
    }

    @Override
    public PayOrderEntity getByPayOrderNo(String payOrderNo) {
        return lambdaQuery().eq(PayOrderEntity::getPayOrderNo, payOrderNo).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderEntity createPending(String bizOrderNo, Long userId, int amount, String payUrl,
                                        String channelCode, int payType) {
        PayOrderEntity payOrder = new PayOrderEntity();
        payOrder.setBizOrderNo(bizOrderNo);
        payOrder.setPayOrderNo(IdUtil.fastSimpleUUID());
        payOrder.setBizUserId(userId);
        payOrder.setPayChannelCode(channelCode);
        payOrder.setAmount(amount);
        payOrder.setPayType(payType);
        payOrder.setStatus(PayStatusEnum.PENDING_PAY.getCode());
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(PAY_TIMEOUT_MINUTES));
        payOrder.setQrCodeUrl(payUrl);
        save(payOrder);
        return payOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderEntity createPaidByBalance(String bizOrderNo, Long userId, int amount) {
        PayOrderEntity payOrder = new PayOrderEntity();
        payOrder.setBizOrderNo(bizOrderNo);
        payOrder.setPayOrderNo(IdUtil.fastSimpleUUID());
        payOrder.setBizUserId(userId);
        payOrder.setPayChannelCode(CHANNEL_BALANCE);
        payOrder.setAmount(amount);
        payOrder.setPayType(PayTypeEnum.BALANCE.getCode());
        payOrder.setStatus(PayStatusEnum.SUCCESS.getCode());
        payOrder.setPaySuccessTime(LocalDateTime.now());
        save(payOrder);
        return payOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(PayOrderEntity payOrder, String resultCode) {
        payOrder.setStatus(PayStatusEnum.SUCCESS.getCode());
        payOrder.setPaySuccessTime(LocalDateTime.now());
        payOrder.setResultCode(resultCode);
        updateById(payOrder);
    }

    @Override
    public List<PayOrderEntity> listTimeout(LocalDateTime cutoff) {
        return lambdaQuery()
                .eq(PayOrderEntity::getStatus, PayStatusEnum.PENDING_PAY.getCode())
                .lt(PayOrderEntity::getPayOverTime, cutoff)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTimeoutCancel(Long payOrderId) {
        lambdaUpdate()
                .eq(PayOrderEntity::getId, payOrderId)
                .set(PayOrderEntity::getStatus, PayStatusEnum.TIMEOUT_CANCEL.getCode())
                .update();
    }
}
