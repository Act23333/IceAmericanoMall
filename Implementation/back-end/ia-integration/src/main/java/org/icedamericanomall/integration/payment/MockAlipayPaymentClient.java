package org.icedamericanomall.integration.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
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
 *
 * <p><b>退款 / 对账 Mock 口径（F1 / F4）</b>：退款<b>同步成功</b>（{@code settled=true}）——
 * 与支付宝真实「同步返回退款结果」的语义一致，故不存在微信那样的异步退款回调分支；
 * 账单返回两条样例流水，供本地打通对账链路。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "alipay.enabled", havingValue = "false", matchIfMissing = true)
public class MockAlipayPaymentClient implements AlipayPaymentClient {

    /** Mock 渠道退款单号前缀。 */
    static final String MOCK_CHANNEL_REFUND_NO_PREFIX = "mock-ali-rfd-";

    /** Mock 渠道交易号前缀。 */
    static final String MOCK_CHANNEL_TXN_NO_PREFIX = "mock-ali-txn-";

    @Override
    public String initiatePayment(String outTradeNo, int amount, String description) {
        String payUrl = "https://mock-alipay.example.com/pay?order=" + outTradeNo + "&amount=" + amount;
        log.info("[MockAlipay] 发起支付: outTradeNo={}, amount={} -> {}（虚拟链接）", outTradeNo, amount, payUrl);
        return payUrl;
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        return true;
    }

    @Override
    public ChannelRefundResult refund(String outTradeNo, String outRefundNo, int refundAmount, int orderAmount,
                                      String reason) {
        log.info("===== MOCK ALIPAY REFUND ===== outTradeNo={}, outRefundNo={}, refundAmount={}, orderAmount={}",
                outTradeNo, outRefundNo, refundAmount, orderAmount);
        return ChannelRefundResult.settled(MOCK_CHANNEL_REFUND_NO_PREFIX + outRefundNo);
    }

    @Override
    public List<ChannelBillItem> billDownload(String billDate) {
        List<ChannelBillItem> items = List.of(
                new ChannelBillItem("MOCK-ALI-PAY-0001", MOCK_CHANNEL_TXN_NO_PREFIX + billDate + "-1", 6600),
                new ChannelBillItem("MOCK-ALI-PAY-0002", MOCK_CHANNEL_TXN_NO_PREFIX + billDate + "-2", 8800));
        log.info("[MockAlipay] 账单下载: billDate={}, rows={}（样例账单）", billDate, items.size());
        return items;
    }

    @Override
    public String queryStatus(String outTradeNo) {
        return "SUCCESS";
    }
}

