package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InvoicePartialRefundCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 全额退款发票联动降级工厂 —— <b>4xx 透传 / 其余 fail-closed（抛 {@code SYSTEM_BUSY}）</b>。
 *
 * <p><b>为什么从「静默 void」改为「必须抛出」（2026-10-18 批次 F·收口）</b>：本类此前两个方法
 * 都只打 WARN 就返回，而 {@code void} 返回值让调用方<b>无法区分</b>「联动成功」与「联动丢失」。
 * 于是 {@code pay-service PayEventNotifier#triggerInvoiceLinkage} 在 trade 不可用时仍会打出
 * 「[refund-invoice][triggered] 已联动冲红」的成功日志 —— 静默成功正是 §三·八 要消灭的形态。
 *
 * <p>改为抛出后，退款主流程<b>一字不改</b>：调用点本就带 {@code try/catch}（fail-open 双保险），
 * 异常被接住并落 {@code WARN}，同时在 {@code catch} 内<b>写一条补偿记录</b>
 * （{@code invoice_link_compensation}，状态 PENDING）供后续重试/人工收敛。
 * 即：<b>退款优先不变，联动失败从「静默」升级为「可观测 + 可补偿」</b>。
 *
 * <p><b>两路分支（不要合并）</b>：
 * <ul>
 *   <li><b>{@code category == CLIENT}</b>（4xx：订单/发票不存在、参数错）：原样抛出
 *       {@link UpstreamException}，业务码与文案一字不改 —— 例如 trade 说「订单不存在(1103)」，
 *       调用方与运维看到的就应是 1103，而不是一个笼统的「暂时无法」。</li>
 *   <li><b>其余</b>（TRANSIENT 5xx/超时/熔断、BUG、无 {@code UpstreamException} 的连接层失败）：
 *       抛 {@code BizException(SYSTEM_BUSY)}，文案按 §三·八·2 用「暂时无法联动」，
 *       <b>不得</b>写成「服务不可用」。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}（pay-service 已满足）。
 */
@Slf4j
@Component
public class TradeInvoiceClientFallback implements FallbackFactory<TradeInvoiceClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法联动」，<b>不得</b>写成「服务不可用」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "发票联动暂时无法完成，请稍后重试";

    @Override
    public TradeInvoiceClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new TradeInvoiceClient() {

            @Override
            public void voidOnFullRefund(InvoiceVoidCO invoiceVoid) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("全额退款发票联动降级(透传): 下游业务失败 orderNo={}, sceneRef={}, "
                                    + "upstreamStatus={}, code={}, category={}, msg={}",
                            invoiceVoid == null ? null : invoiceVoid.getOrderNo(),
                            invoiceVoid == null ? null : invoiceVoid.getSceneRef(),
                            upstream.getUpstreamStatus(), upstream.getCode(), category, upstream.getMsg());
                    throw upstream;
                }
                log.error("全额退款发票联动降级: 暂时无法联动（category={}；禁止静默成功——发票将漏冲红/作废，"
                                + "调用方须落补偿记录） orderNo={}, sceneRef={}, cause={}",
                        category, invoiceVoid == null ? null : invoiceVoid.getOrderNo(),
                        invoiceVoid == null ? null : invoiceVoid.getSceneRef(),
                        cause == null ? "unknown" : cause.toString(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }

            @Override
            public void markPartialRefund(InvoicePartialRefundCO partialRefund) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("部分退款发票标记降级(透传): 下游业务失败 orderNo={}, sceneRef={}, "
                                    + "upstreamStatus={}, code={}, category={}, msg={}",
                            partialRefund == null ? null : partialRefund.getOrderNo(),
                            partialRefund == null ? null : partialRefund.getSceneRef(),
                            upstream.getUpstreamStatus(), upstream.getCode(), category, upstream.getMsg());
                    throw upstream;
                }
                log.error("部分退款发票标记降级: 暂时无法标记（category={}；禁止静默成功——partial_refund_flag 将漏置位，"
                                + "调用方须落补偿记录） orderNo={}, sceneRef={}, cause={}",
                        category, partialRefund == null ? null : partialRefund.getOrderNo(),
                        partialRefund == null ? null : partialRefund.getSceneRef(),
                        cause == null ? "unknown" : cause.toString(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
