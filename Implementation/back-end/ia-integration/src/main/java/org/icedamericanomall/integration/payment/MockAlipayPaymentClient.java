package org.icedamericanomall.integration.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝 Mock 客户端 —— 未接入支付宝开放平台时返回虚拟支付链接，验签恒通过。
 * 真实接入：新增一个 {@code @ConditionalOnProperty("alipay.enabled"="true")} 的实现即可。
 */
@Slf4j
@Component
public class MockAlipayPaymentClient implements AlipayPaymentClient {

    @Override
    public String initiatePayment(String orderNo, int amount, String description) {
        String payUrl = "https://mock-alipay.example.com/pay?order=" + orderNo + "&amount=" + amount;
        log.info("[MockAlipay] 发起支付: orderNo={}, amount={} -> {}（虚拟链接）", orderNo, amount, payUrl);
        return payUrl;
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        return true;
    }

    @Override
    public String queryStatus(String payOrderNo) {
        return "SUCCESS";
    }
}
