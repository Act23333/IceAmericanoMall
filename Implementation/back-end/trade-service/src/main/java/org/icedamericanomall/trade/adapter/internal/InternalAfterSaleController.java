package org.icedamericanomall.trade.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.app.aftersale.AfterSaleManager;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后内部端点（I42 {@code PUT /internal/trade/after-sale/{id}/refunded}）—— 仅集群内 Feign 调用
 * （ia-api {@code AfterSaleClient}，调用方 pay-service 退款成功后回写）。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}，业务异常以原始 HTTP 状态 + 类型化错误体
 * {@code ErrorInfo}（{@code code / msg / category / retryable / traceId}）透出（ADR-004）。
 *
 * <p><b>语义</b>：售后单 {@code APPROVED(2) → REFUNDED(5)} 条件迁移，激活此前为死状态的
 * {@code REFUNDED}。状态不匹配（已迁移 / 非 APPROVED）时为<b>幂等空操作成功返回</b>，
 * 使重复回调与并发回写都安全；售后单不存在 → 404 原始透出。
 */
@RestController
@RequestMapping("/internal/trade/after-sale")
@RequiredArgsConstructor
public class InternalAfterSaleController {

    private final AfterSaleManager afterSaleManager;

    /**
     * 回写售后单为「已退款」（退款成功后由 pay-service 调用）。
     *
     * @param id 售后单 ID
     */
    @PutMapping("/{id}/refunded")
    public void markRefunded(@PathVariable Long id) {
        afterSaleManager.markRefunded(id);
    }
}
