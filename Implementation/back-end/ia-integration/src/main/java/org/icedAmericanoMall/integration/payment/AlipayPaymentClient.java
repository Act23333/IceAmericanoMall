package org.icedAmericanoMall.integration.payment;

import java.util.Map;

/**
 * 支付宝支付客户端 —— 与 {@link PaymentClient}（微信）平行的独立接口，避免 Bean 歧义。
 * 当前仅提供 {@link MockAlipayPaymentClient}（虚拟支付链接）；接入支付宝开放平台后追加真实实现即可。
 */
public interface AlipayPaymentClient {

    /** 发起支付，返回支付链接（PC 网页/二维码）。 */
    String initiatePayment(String orderNo, int amount, String description);

    /** 验证支付回调签名。 */
    boolean verifyCallback(Map<String, String> params);

    /** 查询支付单状态。 */
    String queryStatus(String payOrderNo);
}
