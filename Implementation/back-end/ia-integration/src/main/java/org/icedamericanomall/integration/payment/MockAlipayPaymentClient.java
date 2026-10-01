package org.icedamericanomall.integration.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝 Mock 客户端 —— 未接入支付宝开放平台时返回虚拟支付链接，验签恒通过。
 *
 * <p>激活条件（V5.2 补齐）：{@code alipay.enabled} 缺省或为 false。
 * 与 {@code sms/email/storage} 组的条件装配模板对齐
 * （Mock 侧一律 {@code havingValue = "false", matchIfMissing = true}）。
 * 改造前本类<b>没有任何条件注解</b>，永远激活，既无法关闭也违背五组配置的一致性约定；
 * 现在缺省行为不变（默认仍是 Mock），仅新增了一个显式的关闭开关。
 *
 * <p>真实接入：新增一个 {@code @ConditionalOnProperty(name = "alipay.enabled", havingValue = "true")}
 * 的实现即可（{@link AlipayProperties} 已预留 {@code appId} / 密钥 / 网关等字段）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "alipay.enabled", havingValue = "false", matchIfMissing = true)
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
