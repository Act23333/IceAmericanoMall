package org.icedamericanomall.client;

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
 * <p><b>降级语义：fail-open + 告警</b>——触发器语义（同 I54）：资金终态已定，联动失败依赖
 * trade 管理端"部分退款/异常"筛选人工收敛（PRD D4 已知风险登记），与资金命令 I55 的
 * fail-closed 刻意不同。
 */
@FeignClient(name = "trade-service", path = "/internal/trade/invoice",
        contextId = "tradeInvoice", fallbackFactory = TradeInvoiceClientFallback.class)
public interface TradeInvoiceClient {

    /** 全额退款联动：待开具→自动作废 / 已开具→自动冲红；无有效票幂等空操作。 */
    @PostMapping("/void-on-full-refund")
    void voidOnFullRefund(@RequestBody InvoiceVoidCO invoiceVoid);
}
