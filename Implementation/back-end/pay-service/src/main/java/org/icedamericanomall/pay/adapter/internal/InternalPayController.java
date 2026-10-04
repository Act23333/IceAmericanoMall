package org.icedamericanomall.pay.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.pay.app.RefundManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款内部端点（I40 {@code POST /internal/pay/refund}）—— 仅集群内 Feign 调用
 * （ia-api {@code RefundClient}，调用方 trade-service 售后编排层）。
 *
 * <p>控制器只做参数接收 + 调 Manager；Domain → CO 装配由 Manager 内聚完成，本类不持有转换器。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}：成功返回原始 {@link RefundResultCO}，
 * 业务异常以原始 HTTP 状态 + 极简 {@code {"code","msg"}} 透出（ADR-004）。
 *
 * <p><b>幂等</b>：同 {@code afterSaleId} 重复请求不创建新退款单，返回既有退款单
 * （{@code refund_order.uk_after_sale} 兜底）。
 */
@RestController
@RequestMapping("/internal/pay")
@RequiredArgsConstructor
public class InternalPayController {

    private final RefundManager refundManager;

    /**
     * 发起退款（按原支付渠道原路退回）。
     *
     * @param request 退款发起参数（售后单 ID / 订单号 / 退款金额（分）/ 原因）
     * @return 退款单号与状态（0-处理中 / 1-成功 / 2-失败）
     */
    @PostMapping("/refund")
    public RefundResultCO refund(@RequestBody RefundCreateCO request) {
        return refundManager.refund(request);
    }
}
