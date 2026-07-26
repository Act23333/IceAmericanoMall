package org.icedamericanomall.integration.payment;

import java.util.Map;

/**
 * 支付客户端接口 — 微信支付 Native / JSAPI 抽象。
 */
public interface PaymentClient {

    /** 发起支付，返回二维码链接 / 支付链接 */
    String initiatePayment(String orderNo, int amount, String description);

    /** 验证支付回调签名 */
    boolean verifyCallback(Map<String, String> params);

    /** 查询支付单状态 */
    String queryStatus(String payOrderNo);
}
