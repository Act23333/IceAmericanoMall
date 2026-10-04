package org.icedamericanomall.client;

import org.icedamericanomall.fallback.AfterSaleClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 售后域 Feign Client —— pay-service → trade-service（I42 {@code PUT /internal/trade/after-sale/{id}/refunded}）。
 *
 * <p><b>语义</b>：退款成功后把售后单从 {@code APPROVED(2)} 条件迁移到 {@code REFUNDED(5)}，
 * 激活此前为死状态的 {@code REFUNDED}。{@code 0 行 = 状态已迁移 / 非 APPROVED → 幂等空操作成功}。
 *
 * <p><b>降级语义（ADR-011 三分法）</b>：<b>fail-open + 告警</b> —— 状态回写属「退款主流程的下游收尾」，
 * 静默丢失可由对账 / 管理端重试收敛；若此处 fail-closed，会把一次已成功的退款反判为失败，
 * 反而制造资金与状态的双重不一致。参见 {@link AfterSaleClientFallback}。
 */
@FeignClient(name = "trade-service", path = "/internal/trade/after-sale", contextId = "afterSale",
        fallbackFactory = AfterSaleClientFallback.class)
public interface AfterSaleClient {

    /**
     * 回写售后单为「已退款」（条件迁移 {@code APPROVED → REFUNDED}，幂等）。
     *
     * @param id 售后单 ID
     */
    @PutMapping("/{id}/refunded")
    void markRefunded(@PathVariable Long id);
}
