package org.icedamericanomall.trade.adapter.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.InvoicePartialRefundCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.trade.app.invoice.InvoiceAdminManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal invoice endpoints for inter-service Feign calls (F14 发票 I56/I60，契约 §4.14.16c).
 * All endpoints return raw types without Result wrapping (internal API convention, ADR-004).
 *
 * <p>调用方：pay-service（退款资金终态触发，ia-api {@code TradeInvoiceClient}，
 * 降级 fail-open + 告警——触发器语义同 I54：资金终态已定，联动失败依赖管理端筛选人工收敛；
 * full/partial 判定在 pay 侧，两侧各自 fail-open）。幂等：无有效票 / 已终态 / 已置标 =
 * 空操作成功返回（语义在 {@link InvoiceAdminManager}）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/trade/invoice")
@RequiredArgsConstructor
public class InternalTradeInvoiceController {

    private final InvoiceAdminManager invoiceAdminManager;

    /**
     * I56 全额退款发票联动 —— 待开具(1)→自动作废(4)、已开具(2)→自动冲红(3)，
     * 原因 = 系统原因码（含退款幂等键）；部分退款不触发（调用方已按全额判定）。
     */
    @PostMapping("/void-on-full-refund")
    public void voidOnFullRefund(@RequestBody InvoiceVoidCO invoiceVoid) {
        invoiceAdminManager.voidOnFullRefund(invoiceVoid.getOrderNo(), invoiceVoid.getSceneRef());
    }

    /**
     * I60 部分退款发票置标 —— 已开具有效票置 partial_refund_flag=1 待人工处理
     * （PRD D4/O6：部分退款不改状态）；待开具/终态/无票 = 幂等空操作（语义在 Manager）。
     */
    @PostMapping("/partial-refund-flag")
    public void markPartialRefundFlag(
            @Validated @RequestBody InvoicePartialRefundCO partialRefund) {
        invoiceAdminManager.markPartialRefundFlag(partialRefund.getOrderNo(), partialRefund.getSceneRef());
    }
}
