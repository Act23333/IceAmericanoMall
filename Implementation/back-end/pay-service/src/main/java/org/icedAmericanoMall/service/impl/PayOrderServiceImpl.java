package org.icedAmericanoMall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.OrderClient;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.dto.OrderSummaryDTO;
import org.icedAmericanoMall.enums.PayStatusEnum;
import org.icedAmericanoMall.mapper.PayOrderMapper;
import org.icedAmericanoMall.service.PayOrderService;
import org.noLazy.common.client.payment.PaymentClient;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Order status update via Feign after payment:
 * PENDING_PAYMENT(1) → PENDING_SHIPMENT(2) upon successful payment.
 */
@Slf4j
@Service
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrderEntity> implements PayOrderService {

    private static final int ORDER_STATUS_PENDING_SHIPMENT = 2;

    private final PaymentClient paymentClient;
    private final OrderClient orderClient;

    public PayOrderServiceImpl(PaymentClient paymentClient, OrderClient orderClient) {
        this.paymentClient = paymentClient;
        this.orderClient = orderClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderEntity initiatePayment(String orderNo, Long userId) {
        // Check for existing pay order (idempotency)
        PayOrderEntity existing = lambdaQuery()
                .eq(PayOrderEntity::getBizOrderNo, orderNo).one();
        if (existing != null && existing.getStatus() == PayStatusEnum.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单已支付");
        }

        // Fetch actual order amount from trade-service
        OrderSummaryDTO orderSummary = orderClient.getOrder(orderNo);
        if (orderSummary == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        int actualAmount = orderSummary.getTotalAmount();

        PayOrderEntity payOrder = new PayOrderEntity();
        payOrder.setBizOrderNo(orderNo);
        payOrder.setPayOrderNo(IdUtil.fastSimpleUUID());
        payOrder.setBizUserId(userId);
        payOrder.setPayChannelCode("WECHAT");
        payOrder.setAmount(actualAmount);
        payOrder.setPayType(4); // 扫码支付
        payOrder.setStatus(PayStatusEnum.PENDING_PAY.getCode());
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(30));

        // Call payment client (stub for dev)
        String qrCodeUrl = paymentClient.initiatePayment(orderNo, payOrder.getAmount(), "订单支付");
        payOrder.setQrCodeUrl(qrCodeUrl);

        save(payOrder);
        return payOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(Map<String, String> params) {
        boolean verified = paymentClient.verifyCallback(params);
        if (!verified) {
            log.error("支付回调签名验证失败: {}", params);
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "支付回调验证失败");
        }

        String payOrderNo = params.get("out_trade_no");
        PayOrderEntity payOrder = lambdaQuery()
                .eq(PayOrderEntity::getPayOrderNo, payOrderNo).one();
        if (payOrder == null) {
            log.error("支付单不存在: {}", payOrderNo);
            throw new BizException(ErrorCode.USER_NOT_FOUND, "支付单不存在");
        }
        if (payOrder.getStatus() == PayStatusEnum.SUCCESS.getCode()) {
            // Idempotent — already processed
            return;
        }

        payOrder.setStatus(PayStatusEnum.SUCCESS.getCode());
        payOrder.setPaySuccessTime(LocalDateTime.now());
        payOrder.setResultCode(params.get("result_code"));
        updateById(payOrder);

        // Update order status to PENDING_SHIPMENT via Feign
        try {
            orderClient.updateOrderStatus(payOrder.getBizOrderNo(), ORDER_STATUS_PENDING_SHIPMENT);
            log.info("支付成功，订单状态已更新: payOrderNo={}, orderNo={}", payOrderNo, payOrder.getBizOrderNo());
        } catch (Exception e) {
            log.error("支付成功但订单状态更新失败: orderNo={}", payOrder.getBizOrderNo(), e);
            // Non-fatal: payment is recorded; order status can be reconciled
        }
    }

    @Override
    public PayOrderEntity queryStatus(String payOrderNo) {
        return lambdaQuery().eq(PayOrderEntity::getPayOrderNo, payOrderNo).one();
    }
}
