package org.icedAmericanoMall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.OrderClient;
import org.icedAmericanoMall.convert.PayOrderConverter;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.domain.vo.PayOrderVO;
import org.icedAmericanoMall.dto.OrderSummaryDTO;
import org.icedAmericanoMall.enums.PayStatusEnum;
import org.icedAmericanoMall.enums.TradeOrderStatus;
import org.icedAmericanoMall.integration.payment.PaymentClient;
import org.icedAmericanoMall.service.PayOrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付编排 Manager —— 承接跨服务 Feign（trade-service OrderClient）与支付渠道集成（PaymentClient），
 * 编排支付发起、回调、超时取消。领域内的支付单读写下沉至 {@link PayOrderService}。
 *
 * <pre>
 * Scenario: 微信支付发起
 *   Given 订单状态为"待付款"且存在于 trade-service
 *   When 用户请求发起支付
 *   Then 通过 Feign 获取订单金额，创建"待支付"支付单，返回二维码链接
 *
 * Scenario: 支付成功回调（幂等）
 *   Given 回调签名校验通过且支付单为"待支付"
 *   When 收到回调
 *   Then 支付单置为"已支付"，并通过 Feign 将订单更新为"待发货"
 *
 * Scenario: 支付超时自动取消
 *   Given 支付单"待支付"且已过期
 *   When 定时任务触发
 *   Then 支付单置为"超时取消"，并通过 Feign 将订单更新为"已取消"
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayManager {

    private final PayOrderService payOrderService;
    private final PayOrderConverter payOrderConverter;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;

    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO initiatePayment(String orderNo, Long userId) {
        PayOrderEntity existing = payOrderService.getByBizOrderNo(orderNo);
        if (existing != null && existing.getStatus() == PayStatusEnum.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单已支付");
        }
        // Feign：从 trade-service 获取订单实际金额
        OrderSummaryDTO summary = orderClient.getOrder(orderNo);
        if (summary == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        // 支付渠道集成：获取二维码链接
        String qrCodeUrl = paymentClient.initiatePayment(orderNo, summary.getTotalAmount(), "订单支付");
        PayOrderEntity payOrder = payOrderService.createPending(
                orderNo, userId, summary.getTotalAmount(), qrCodeUrl);
        return payOrderConverter.toVO(payOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(Map<String, String> params) {
        if (!paymentClient.verifyCallback(params)) {
            log.error("支付回调签名验证失败: {}", params);
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "支付回调验证失败");
        }
        String payOrderNo = params.get("out_trade_no");
        PayOrderEntity payOrder = payOrderService.getByPayOrderNo(payOrderNo);
        if (payOrder == null) {
            log.error("支付单不存在: {}", payOrderNo);
            throw new BizException(ErrorCode.USER_NOT_FOUND, "支付单不存在");
        }
        if (payOrder.getStatus() == PayStatusEnum.SUCCESS.getCode()) {
            return; // 幂等：已处理直接返回
        }
        payOrderService.markSuccess(payOrder, params.get("result_code"));
        // Feign：支付成功后将订单状态更新为待发货（非致命，失败可对账修复）
        try {
            orderClient.updateOrderStatus(payOrder.getBizOrderNo(), TradeOrderStatus.PENDING_SHIPMENT.getCode());
            log.info("支付成功，订单状态已更新: payOrderNo={}, orderNo={}", payOrderNo, payOrder.getBizOrderNo());
        } catch (Exception e) {
            log.error("支付成功但订单状态更新失败: orderNo={}", payOrder.getBizOrderNo(), e);
        }
    }

    public PayOrderVO queryStatus(String payOrderNo) {
        return payOrderConverter.toVO(payOrderService.getByPayOrderNo(payOrderNo));
    }

    public void cancelTimeoutPayOrders() {
        List<PayOrderEntity> timeoutOrders = payOrderService.listTimeout(LocalDateTime.now());
        for (PayOrderEntity payOrder : timeoutOrders) {
            try {
                payOrderService.markTimeoutCancel(payOrder.getId());
                try {
                    orderClient.updateOrderStatus(payOrder.getBizOrderNo(), TradeOrderStatus.CANCELLED.getCode());
                } catch (Exception e) {
                    log.error("支付超时取消订单失败: orderNo={}", payOrder.getBizOrderNo(), e);
                }
                log.info("支付超时已取消: payOrderNo={}, orderNo={}",
                        payOrder.getPayOrderNo(), payOrder.getBizOrderNo());
            } catch (Exception e) {
                log.error("处理支付超时订单失败: payOrderId={}", payOrder.getId(), e);
            }
        }
    }
}
