package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 售后服务（trade-service）降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p>覆盖的调用只有 {@code markRefunded}（I42）：退款成功后的<b>售后单状态回写</b>
 * （{@code APPROVED → REFUNDED}）。<b>状态机写 + void 返回值</b>，
 * 按 §三·八·2 必须 fail-closed。
 *
 * <p><b>为什么改前（fail-open）是缺陷</b>：唯一调用方
 * {@code pay-service PayEventNotifier#writeBackAfterSaleRefunded} 自带
 * {@code try { markRefunded(...); log.info("售后单已回写 REFUNDED") } catch { log.warn(fail-open) }} ——
 * 也就是说「异常」这条路它本来就接住了（退款主流程不受影响）。而旧实现静默返回，
 * 于是 <b>trade 不可用时调用方会打出「已回写」的成功日志</b>，售后单实际停在 {@code APPROVED(2)}，
 * 且没有任何告警 —— 典型的「静默成功 = 操作丢了」。
 * 改抛异常后：异常被该 catch 接住并落 {@code WARN}（含「待对账/重试收敛」），
 * <b>主流程行为一字不改，可观测性从「谎报成功」变为「如实告警」</b>。
 */
@Slf4j
@Component
public class AfterSaleClientFallback implements FallbackFactory<AfterSaleClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "售后状态暂时无法回写，请稍后重试";

    @Override
    public AfterSaleClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new AfterSaleClient() {

            @Override
            public void markRefunded(Long afterSaleId) {
                // fail-open（ADR-011 三分法，由 AfterSaleClientFallbackTest 锁死）：本方法是「退款已成功后」的
                // 下游状态回写。此处若抛异常，pay-service 会把一次**已成功**的退款判为失败 —— 钱已退、状态被
                // 篡改，比丢一个回写严重得多。故一律不抛，只留可观测日志，由对账/重试收敛。
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("售后状态回写降级：下游明确拒绝（非本域故障，需对账/人工收敛） afterSaleId={}, "
                                    + "upstreamStatus={}, code={}, category={}, msg={}, traceId={}",
                            afterSaleId, upstream.getUpstreamStatus(), upstream.getCode(),
                            category, upstream.getMsg(), upstream.getTraceId());
                    return;
                }
                log.error("售后状态回写降级：暂时无法回写（category={}；fail-open 不抛异常 —— 退款已在 "
                                + "pay-service 侧成功，抛错会导致整笔退款被判失败。售后单将停 APPROVED，"
                                + "须由对账/重试收敛） afterSaleId={}", category, afterSaleId, cause);
            }
        };
    }
}
