package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 余额服务（wallet-service）降级工厂 —— <b>两个方法均 fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p>余额域全部是<b>资金命令</b>，静默降级会造成资金与业务状态的双重不一致：
 * <ul>
 *   <li>{@code deductBalance} —— 余额支付扣款。调用方
 *       {@code pay-service PaymentChannelRouter#deductBalance ← PayManager#payByBalance}
 *       在 {@code catch} 里把支付单条件取消并抛「余额不足，支付已取消」——
 *       静默成功 = 用户未扣钱却拿到订单；静默失败 = 用户已付却被判失败。两者都不可接受。</li>
 *   <li>{@code refund} —— 售后退款入账（F1 / I41）。调用方
 *       {@code RefundManager#requestChannel} 不吞异常 —— 异常使退款单停留失败态走管理端重试；
 *       静默成功 = 退款单被判成功但钱没到账。</li>
 * </ul>
 *
 * <p>分支：{@code category == CLIENT}（余额不足 4xx 等业务事实）→ <b>原样抛
 * {@link UpstreamException}</b>；{@code TRANSIENT} / {@code BUG} → 抛
 * {@code BizException(SYSTEM_BUSY)}（503 / {@code TRANSIENT} / <b>可重试</b>）：依赖不可用属<b>暂时性故障</b>，
 * 调用方应退避后重试；<b>不得</b>用 {@code BUSINESS_EXECUTION_EXCEPTION}(3000 / 200 / RULE / 不可重试) ——
 * 那是「业务规则拒绝」的承载，会把可用性问题报成规则问题（同 R-4 病）。
 */
@Slf4j
@Component
public class BalanceClientFallback implements FallbackFactory<BalanceClient> {

    /** TRANSIENT/BUG 路径的降级文案。 */
    private static final String DEGRADED_MESSAGE = "余额服务暂时无法处理，请稍后重试";

    @Override
    public BalanceClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new BalanceClient() {

            @Override
            public void deductBalance(Long userId, Integer amount, String bizRef) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("余额扣减降级(透传): 下游业务失败 userId={}, amount={}, code={}, msg={}",
                            userId, amount, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("余额扣减降级: 暂时无法处理（category={}；fail-closed，禁止静默成功）"
                        + " userId={}, amount={}", category, userId, amount, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }

            @Override
            public void refund(BalanceRefundCO request) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("余额退款入账降级(透传): 下游业务失败 transactionNo={}, userId={}, amount={}, code={}, msg={}",
                            request == null ? null : request.getTransactionNo(),
                            request == null ? null : request.getUserId(),
                            request == null ? null : request.getAmount(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("余额退款入账降级: 暂时无法处理（category={}；fail-closed，禁止静默成功）"
                        + " transactionNo={}, userId={}, amount={}", category,
                        request == null ? null : request.getTransactionNo(),
                        request == null ? null : request.getUserId(),
                        request == null ? null : request.getAmount(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
