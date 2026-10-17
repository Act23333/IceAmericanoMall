package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.RefundClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 退款服务（pay-service）降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p>退款发起是<b>资金命令</b>（ADR-011 三分法中的 ③ 类）：静默降级 = 管理端以为同意即退款，
 * 实际退款单未创建、钱未退。必须让调用方（trade {@code RefundManager}）感知失败——
 * 售后单停留 {@code APPROVED(2)}，由管理端通过 AD43 重试收敛。
 *
 * <p><b>分支（{@code 09-constraints} §三·八·1 透传优先）</b>：
 * <ul>
 *   <li>{@code category == CLIENT} 且异常链中带已类型化的下游错误 → <b>原样抛
 *       {@link UpstreamException}</b>（下游 4xx + 已知 {@code code} 是<b>业务事实</b>，
 *       如「原支付单不存在 / 渠道不支持该笔退款」；说成「退款服务暂不可用」会让运维
 *       反复重试一个重试永远不会成功的请求）；</li>
 *   <li>{@code TRANSIENT} / {@code BUG} → <b>原有 fail-closed 口径一字不变</b>：
 *       {@code BizException(SYSTEM_BUSY)}（503 / {@code TRANSIENT} / 可重试）。</li>
 * </ul>
 * 两条分支都仍然是「抛异常」—— 资金命令<b>不存在</b>静默成功的路径。
 *
 * <p><b>透传 4xx 不会让调用方误判（已核实）</b>：trade 侧
 * {@code RefundManager#triggerRefund} 以 {@code catch (Exception e)} 记 error 日志后
 * <b>原样 rethrow</b>（不吞、不改写），售后单停留 {@code APPROVED} 待管理端重试；
 * 拼团闭环 {@code GroupBuyParticipationService#refundByOrder} 同样 {@code catch (Exception)}
 * 只记日志（退款单停留失败态，由补偿语义收敛）。两条调用路径都不会把异常当成「退款成功」。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}。
 */
@Slf4j
@Component
public class RefundClientFallback implements FallbackFactory<RefundClient> {

    @Override
    public RefundClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new RefundClient() {

            @Override
            public RefundResultCO refund(RefundCreateCO request) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("退款服务降级(透传): 下游业务失败 afterSaleId={}, orderNo={}, refundAmount={},"
                                    + " code={}, msg={}",
                            request == null ? null : request.getAfterSaleId(),
                            request == null ? null : request.getOrderNo(),
                            request == null ? null : request.getRefundAmount(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("退款服务降级：发起退款失败（fail-closed，禁止静默丢失）"
                        + " afterSaleId={}, orderNo={}, refundAmount={}",
                        request == null ? null : request.getAfterSaleId(),
                        request == null ? null : request.getOrderNo(),
                        request == null ? null : request.getRefundAmount(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "退款服务暂不可用，退款未发起");
            }
        };
    }
}
