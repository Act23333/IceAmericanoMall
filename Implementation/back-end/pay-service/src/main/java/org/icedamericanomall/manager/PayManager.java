package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.convert.PayOrderConverter;
import org.icedamericanomall.domain.entity.PayOrderEntity;
import org.icedamericanomall.domain.vo.PayOrderVO;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.icedamericanomall.enums.PayChannelEnum;
import org.icedamericanomall.enums.PayStatusEnum;
import org.icedamericanomall.enums.PayTypeEnum;
import org.icedamericanomall.enums.TradeOrderStatus;
import org.icedamericanomall.service.PayOrderService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付编排 Manager —— 按渠道（微信/支付宝/余额）路由发起支付与回调，编排跨服务 Feign。
 * 领域内的支付单读写下沉至 {@link PayOrderService}。
 *
 * <pre>
 * Scenario: 微信/支付宝发起
 *   Then 调对应渠道客户端拿支付链接，创建"待支付"支付单，返回链接
 * Scenario: 余额支付
 *   Then Feign 原子扣减用户余额（不足则失败不建单）→ 直接创建"已支付"支付单 → 通知订单待发货
 * Scenario: 回调/超时
 *   Then 按渠道验签置"已支付"通知待发货；超时置"取消"通知已取消
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayManager {

    private static final String PAY_DESC = "订单支付";

    private final PayOrderService payOrderService;
    private final PayOrderConverter payOrderConverter;
    private final OrderClient orderClient;
    private final PaymentChannelRouter channelRouter;

    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO initiatePayment(String orderNo, Long userId, PayChannelEnum channel) {
        PayOrderEntity existing = payOrderService.getByBizOrderNo(orderNo);
        if (existing != null && existing.getStatus() == PayStatusEnum.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单已支付");
        }
        OrderSummaryDTO summary = orderClient.getOrder(orderNo);
        if (summary == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        int amount = summary.getTotalAmount();
        PayOrderEntity payOrder = switch (channel) {
            case WECHAT, ALIPAY -> initiateThirdParty(orderNo, userId, amount,
                    channelRouter.initiatePayment(orderNo, amount, PAY_DESC, channel), channel);
            case BALANCE -> payByBalance(orderNo, userId, amount);
        };
        return payOrderConverter.toVO(payOrder);
    }

    /** 微信/支付宝：创建待支付单，附带支付链接。 */
    private PayOrderEntity initiateThirdParty(String orderNo, Long userId, int amount,
                                              String payUrl, PayChannelEnum channel) {
        return payOrderService.createPending(
                orderNo, userId, amount, payUrl, channel.getCode(), PayTypeEnum.NATIVE.getCode());
    }

    /** 余额支付：Feign 扣款成功后直接成单，并通知订单待发货。 */
    private PayOrderEntity payByBalance(String orderNo, Long userId, int amount) {
        channelRouter.deductBalance(userId, amount);
        PayOrderEntity payOrder;
        try {
            payOrder = payOrderService.createPaidByBalance(orderNo, userId, amount);
        } catch (Exception e) {
            log.error("余额已扣但支付单创建失败，需人工退款: userId={}, amount={}, orderNo={}",
                    userId, amount, orderNo, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "支付处理失败");
        }
        notifyOrderPaid(orderNo);
        return payOrder;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(Map<String, String> params, PayChannelEnum channel) {
        boolean verified = channelRouter.verifyCallback(params, channel);
        if (!verified) {
            log.error("支付回调签名验证失败: channel={}, {}", channel, params);
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "支付回调验证失败");
        }
        String payOrderNo = params.get("out_trade_no");
        PayOrderEntity payOrder = payOrderService.getByPayOrderNo(payOrderNo);
        if (payOrder == null) {
            log.error("支付单不存在: {}", payOrderNo);
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单不存在");
        }
        if (payOrder.getStatus() == PayStatusEnum.SUCCESS.getCode()) {
            return; // 幂等：已处理直接返回
        }
        payOrderService.markSuccess(payOrder, params.get("result_code"));
        notifyOrderPaid(payOrder.getBizOrderNo());
    }

    /** Feign：通知 trade-service 订单进入待发货（非致命，失败可对账修复）。 */
    private void notifyOrderPaid(String orderNo) {
        try {
            orderClient.updateOrderStatus(orderNo, TradeOrderStatus.PENDING_SHIPMENT.getCode());
            log.info("支付成功，订单状态已更新: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("支付成功但订单状态更新失败: orderNo={}", orderNo, e);
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
