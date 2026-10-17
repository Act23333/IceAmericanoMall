package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderRefundClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 无售后单退款命令降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>，资金命令（同
 * {@code RefundClientFallback} 定类）。
 *
 * <p><b>分支（{@code 09-constraints} §三·八·1 透传优先）</b>：
 * <ul>
 *   <li>{@code category == CLIENT} 且异常链中带已类型化的下游错误 → <b>原样抛
 *       {@link UpstreamException}</b>（下游 4xx + 已知 {@code code} 是<b>业务事实</b>，
 *       如「订单不支持全额退款」；改写成 {@code SYSTEM_BUSY} 会让调用方退避重试一个
 *       重试永远不会成功的请求）；</li>
 *   <li>{@code TRANSIENT} / {@code BUG} → <b>原有 fail-closed 口径一字不变</b>：
 *       {@code BizException(SYSTEM_BUSY)}（503 / {@code TRANSIENT} / 可重试）。</li>
 * </ul>
 * 两条分支都仍然是「抛异常」—— 资金命令<b>不存在</b>静默成功的路径。
 */
@Slf4j
@Component
public class OrderRefundClientFallback implements FallbackFactory<OrderRefundClient> {

    @Override
    public OrderRefundClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new OrderRefundClient() {

            @Override
            public RefundResultCO refundByOrder(OrderRefundCO refund) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("订单退款命令降级(透传): 下游业务失败 orderNo={}, sceneRef={}, code={}, msg={}",
                            refund == null ? null : refund.getOrderNo(),
                            refund == null ? null : refund.getSceneRef(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("订单退款命令降级：pay-service 不可用（资金命令禁止静默丢失）"
                                + " orderNo={}, sceneRef={}",
                        refund == null ? null : refund.getOrderNo(),
                        refund == null ? null : refund.getSceneRef(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "退款服务暂不可用，请稍后重试");
            }
        };
    }
}
