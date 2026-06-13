package org.noLazy.common.client.payment.impl;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.payment.PaymentClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stub WeChat payment client for local development.
 * Real WeChat Pay integration requires merchant credentials and the WeChat Pay SDK.
 */
@Slf4j
@Component
public class WechatPaymentClient implements PaymentClient {

    @Override
    public String initiatePayment(String orderNo, int amount, String description) {
        log.info("===== MOCK WECHAT PAY =====");
        log.info("Order: {}, Amount: {} cents, Description: {}", orderNo, amount, description);
        log.info("===== MOCK PAY END =====");
        return "https://mock-pay.example.com/qrcode?order=" + orderNo;
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        log.info("Mock WeChat callback verification — always passes");
        return true;
    }

    @Override
    public String queryStatus(String payOrderNo) {
        log.info("Mock query pay status for: {}", payOrderNo);
        return "SUCCESS";
    }
}
