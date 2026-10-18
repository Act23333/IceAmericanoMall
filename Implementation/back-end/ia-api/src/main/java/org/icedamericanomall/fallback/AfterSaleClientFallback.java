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
 * 改抛异常后：异常被该 catch 接住并落 {@code WARN}（带 {@code afterSaleId}，可定位），
 * <b>主流程行为一字不改，可观测性从「谎报成功」变为「如实告警」</b>。
 *
 * <p><b>⚠ 收敛路径的诚实口径（2026-10-18 U17 catch 体审计结论）</b>：调用点 catch 体内
 * **既没有自动重试、也没有对账消费者** —— 也就是说售后单一旦停在 {@code APPROVED(2)}，
 * **目前只能靠人工在管理端核对**。这条缺口已登记在 {@code collaboration/open-items.md}（U17），
 * <b>不要</b>在日志或注释里写成「待对账/重试收敛」—— 那是本仓反复出现过的假承诺
 * （同族事故：`flash_order_log` 的列注释自称幂等兜底、`notification.type` 的注释教义）。
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
                // 4xx 必须原样抛出去（§三·八·1）：只打日志就 return，调用方会把「下游明确拒绝」当成
                // 「已回写」并打出成功日志 —— 那正是本类此前的事故形态。
                // 唯一调用方 pay-service PayEventNotifier#writeBackAfterSaleRefunded:106 自带
                // try/catch(Exception)，抛错不会把「已成功的退款」判为失败；catch 体审计结论见
                // collaboration/open-items.md 的 U17（当前无自动消费者，需人工收敛）。
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("售后状态回写降级(透传): 下游业务失败 afterSaleId={}, "
                                    + "upstreamStatus={}, code={}, category={}, msg={}, traceId={}",
                            afterSaleId, upstream.getUpstreamStatus(), upstream.getCode(),
                            category, upstream.getMsg(), upstream.getTraceId());
                    throw upstream;
                }
                log.error("售后状态回写降级: 暂时无法回写，售后单将停在 APPROVED（category={}；"
                        + "目前无自动重试/对账消费者，需人工在管理端核对） afterSaleId={}",
                        category, afterSaleId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
