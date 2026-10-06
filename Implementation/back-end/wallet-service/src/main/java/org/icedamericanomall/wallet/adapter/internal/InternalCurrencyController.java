package org.icedamericanomall.wallet.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.wallet.app.currency.CurrencyQueryManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 币种内部端点（适配层）—— 仅集群内 Feign 调用（trade-service 下单快照消费面）。
 *
 * <p>契约 §4.14.3b I61（F16 订单快照锁汇 D1-B）：trade 在下单编排链内、事务外查询
 * 「每 1 CNY 兑目标币」的定点整数汇率，落 {@code orders.display_currency/display_rate}
 * 两可空列（展示参考，非结算币种）。<b>fail-open 双向</b>：本端点未启用/未配置 →
 * {@code NO_RESOURCE_FOUND(1103)} 极简透出（{@code /internal/**} 返回真实 HTTP 状态 +
 * 极简 JSON，ADR-004），trade 侧降级哨兵 {@code rateScaled=-1} 不快照，订单照常创建。
 *
 * <pre>
 * GET /internal/currency/current?code=USD   当前汇率（CurrencyRateCO：code/rateScaled/exponent）
 * </pre>
 */
@RestController
@RequestMapping("/internal/currency")
@RequiredArgsConstructor
public class InternalCurrencyController {

    private final CurrencyQueryManager currencyQueryManager;

    /**
     * I61：当前汇率（判别型查询；未启用/未配置 → 1103）。
     *
     * @param code 币种代码（ISO 4217，必填；缺失由框架映射 400）
     * @return 三字段契约对象（裸 CO 返回，不包 {@code Result}）
     */
    @GetMapping("/current")
    public CurrencyRateCO current(@RequestParam String code) {
        return currencyQueryManager.currentRate(code);
    }
}
