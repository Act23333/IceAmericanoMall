package org.icedAmericanoMall.manager;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.client.BalanceClient;
import org.icedAmericanoMall.enums.PayChannelEnum;
import org.icedAmericanoMall.integration.payment.AlipayPaymentClient;
import org.icedAmericanoMall.integration.payment.PaymentClient;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付渠道路由器 —— 封装 PaymentClient(WeChat) + AlipayPaymentClient + UserClient(Balance)，
 * 使 PayManager 由 6 依赖降至 4。
 *
 * <pre>
 * initiate：按渠道调对应客户端生成支付链接；BALANCE 不在此方法（扣款由 PayManager 直接处理）
 * verifyCallback：按渠道选择验签器
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelRouter {

    private final PaymentClient wechatClient;
    private final AlipayPaymentClient alipayClient;
    private final BalanceClient balanceClient;

    /** 微信/支付宝：返回支付链接；余额不在此路由（扣款在 PayManager 直接处理）。 */
    public String initiatePayment(String orderNo, int amount, String description, PayChannelEnum channel) {
        return switch (channel) {
            case WECHAT -> wechatClient.initiatePayment(orderNo, amount, description);
            case ALIPAY -> alipayClient.initiatePayment(orderNo, amount, description);
            case BALANCE -> throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "余额支付请走 PayManager.payByBalance 扣款路径");
        };
    }

    /** 余额扣款（代理 BalanceClient）。 */
    public void deductBalance(Long userId, int amount) {
        balanceClient.deductBalance(userId, amount);
    }

    /** 按渠道验签回调。 */
    public boolean verifyCallback(Map<String, String> params, PayChannelEnum channel) {
        return channel == PayChannelEnum.ALIPAY
                ? alipayClient.verifyCallback(params)
                : wechatClient.verifyCallback(params);
    }
}
