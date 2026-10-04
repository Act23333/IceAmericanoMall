package org.icedamericanomall.client;

import org.icedamericanomall.dto.BalanceRefundCO;
import org.icedamericanomall.fallback.BalanceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 余额域 Feign Client —— 余额能力归属 wallet-service（ADR-016 收尾，自 user-service 迁出）。
 *
 * <p><b>降级语义（ADR-011）</b>：两个方法<b>均 fail-closed</b>——都是资金命令，
 * 静默降级会造成「扣款未生效却以为已支付」或「退款未入账却以为已退款」。参见
 * {@link BalanceClientFallback}。
 */
@FeignClient(name = "wallet-service", path = "/internal/balance", contextId = "balance",
        fallbackFactory = BalanceClientFallback.class)
public interface BalanceClient {

    /** 原子扣减余额（余额支付）。 */
    @PostMapping("/deduct")
    void deductBalance(@RequestParam Long userId, @RequestParam Integer amount);

    /**
     * 余额退款入账（F1 余额渠道原路退回，I41）。
     *
     * <p>{@code transaction_no} 为幂等键：重复请求按 wallet-service 侧唯一约束幂等成功，
     * 不重复加余额。
     *
     * @param request 入账参数（流水号 / 用户 / 金额（分）/ 业务号）
     */
    @PostMapping("/refund")
    void refund(@RequestBody BalanceRefundCO request);
}

