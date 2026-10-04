package org.icedamericanomall.client;

import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.fallback.RefundClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 退款域 Feign Client —— trade-service → pay-service（I40 {@code POST /internal/pay/refund}）。
 *
 * <p><b>触发链路</b>：管理端售后审核同意（APPROVED）→ trade 编排层（{@code RefundManager}，
 * 禁 Service 直调 Feign）→ 本接口 → pay-service 建退款单并按原渠道退款 → 成功后经
 * {@link AfterSaleClient} 回写售后单 {@code REFUNDED(5)}。
 *
 * <p><b>降级语义（ADR-011 三分法）</b>：<b>fail-closed</b> —— 退款发起是资金命令，
 * 静默降级等于「用户退款凭空消失」。参见 {@link RefundClientFallback}。
 */
@FeignClient(name = "pay-service", path = "/internal/pay", contextId = "refund",
        fallbackFactory = RefundClientFallback.class)
public interface RefundClient {

    /**
     * 发起退款（同 {@code afterSaleId} 幂等，返回既有退款单）。
     *
     * @param request 退款发起参数（售后单 ID / 订单号 / 退款金额（分）/ 原因）
     * @return 退款单号与状态（0-处理中 / 1-成功 / 2-失败）
     */
    @PostMapping("/refund")
    RefundResultCO refund(@RequestBody RefundCreateCO request);
}
