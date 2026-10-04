package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 余额服务（wallet-service）降级工厂 —— <b>两个方法均 fail-closed（抛异常）</b>。
 *
 * <p>余额域全部是<b>资金命令</b>，静默降级会造成资金与业务状态的双重不一致：
 * <ul>
 *   <li>{@code deductBalance} —— 余额支付扣款。静默成功 = 用户未扣钱却拿到订单；
 *       静默失败 = 用户已付却被判失败。两者都不可接受。</li>
 *   <li>{@code refund} —— 售后退款入账（F1 / I41）。静默成功 = 退款单被判成功但钱没到账；
 *       必须让 pay-service 感知失败并把退款单置「失败」，走管理端重试。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}。
 */
@Slf4j
@Component
public class BalanceClientFallback implements FallbackFactory<BalanceClient> {

    @Override
    public BalanceClient create(Throwable cause) {
        return new BalanceClient() {

            @Override
            public void deductBalance(Long userId, Integer amount) {
                log.error("余额扣减失败（服务不可用）: userId={}, amount={}", userId, amount, cause);
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "余额服务暂不可用");
            }

            @Override
            public void refund(BalanceRefundCO request) {
                log.error("余额退款入账失败（服务不可用，fail-closed）: transactionNo={}, userId={}, amount={}",
                        request == null ? null : request.getTransactionNo(),
                        request == null ? null : request.getUserId(),
                        request == null ? null : request.getAmount(), cause);
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "余额服务暂不可用");
            }
        };
    }
}
