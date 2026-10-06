package org.icedamericanomall.client;

import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.fallback.CurrencyClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 币种当前汇率 Feign 客户端（F16 多币种：trade-service → wallet-service，契约 §4.14.3b I61，V5.17）。
 *
 * <p>调用方：trade-service 下单编排链（事务外；订单 `display_currency/display_rate` 快照）。
 *
 * <p><b>降级语义：fail-open（PRD §17.7.2）</b>——Feign 降级返回哨兵 {@code rateScaled=-1}，
 * trade 侧任一失败（哨兵/未启用 1103/异常）均**跳过快照**，订单照常创建（NULL=CNY），
 * **绝不因汇率失败阻塞交易主流程**。快照列是展示参考、非结算币种（PRD D1-B/D3-B）。
 */
@FeignClient(name = "wallet-service", path = "/internal/currency",
        contextId = "currency", fallbackFactory = CurrencyClientFallback.class)
public interface CurrencyClient {

    /** 查询币种当前汇率（未启用/未配置 → 404 极简透出；降级哨兵 rateScaled=-1）。 */
    @GetMapping("/current")
    CurrencyRateCO getCurrent(@RequestParam("code") String code);
}
