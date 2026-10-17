package org.icedamericanomall.client;

import org.icedamericanomall.dto.InvoicePartialRefundCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.fallback.TradeInvoiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 全额退款发票联动 Feign 客户端（F14 发票：pay-service → trade-service，契约 §4.14.16c I56，V5.15）。
 *
 * <p>调用方：pay-service（退款成功且退款金额 = 支付单全额时触发；PRD D4：全额自动冲红/作废）。
 *
 * <p><b>降级语义（2026-10-18 批次 F·收口 修订）</b>：<b>4xx 透传 / 其余 fail-closed</b>。
 * 此前是「静默 void + 告警」，但 {@code void} 让调用方无法区分「联动成功」与「联动丢失」，
 * 会打出假的「已联动冲红」成功日志。现在降级必须抛出，由调用点
 * （{@code PayEventNotifier#triggerInvoiceLinkage}）的 {@code try/catch} 接住：
 * 退款主流程仍 fails-open（<b>退款优先</b>，PRD D4 口径不变），但异常路径会落一条
 * {@code invoice_link_compensation} 补偿记录（PENDING）供重试/人工收敛 ——
 * 即「静默」升级为「可观测 + 可补偿」。
 */
@FeignClient(name = "trade-service", path = "/internal/trade/invoice",
        contextId = "tradeInvoice", fallbackFactory = TradeInvoiceClientFallback.class)
public interface TradeInvoiceClient {

    /** 全额退款联动：待开具→自动作废 / 已开具→自动冲红；无有效票幂等空操作。 */
    @PostMapping("/void-on-full-refund")
    void voidOnFullRefund(@RequestBody InvoiceVoidCO invoiceVoid);

    /** 部分退款联动（✅ V5.16.1）：有效发票置 partial_refund_flag=1 待人工处理；无有效票幂等空操作。 */
    @PostMapping("/partial-refund-flag")
    void markPartialRefund(@RequestBody InvoicePartialRefundCO partialRefund);
}
