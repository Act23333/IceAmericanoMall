package org.icedamericanomall.pay.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.pay.app.OrderRefundManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 无售后单退款内部端点（I55 {@code POST /internal/pay/refund/order}）—— 仅集群内 Feign 调用
 * （ia-api {@code OrderRefundClient}，调用方 trade-service 拼团参团失败/结团退款编排层，ADR-049）。
 *
 * <p>控制器只做参数接收 + 调 Manager；Domain → CO 装配由 Manager 内聚完成，本类不持有转换器。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}：成功返回原始 {@link RefundResultCO}，
 * 业务异常以原始 HTTP 状态 + 极简 {@code {"code","msg"}} 透出（ADR-004）。
 *
 * <p><b>幂等</b>：同 {@code sceneRef}（{@code refund_order.uk_biz_ref}，拼团 = {@code GB:{orderNo}}）
 * 重复请求不创建新退款单，返回既有退款单；退款金额 = 订单实付<b>全额</b>（ADR-049）。
 */
@RestController
@RequestMapping("/internal/pay/refund/order")
@RequiredArgsConstructor
public class InternalPayOrderRefundController {

    private final OrderRefundManager orderRefundManager;

    /**
     * 按订单全额退款（无售后单场景，原路退回）。
     *
     * @param request 退款命令（orderNo 必填 / sceneRef 必填幂等键 / reason 可选摘要）
     * @return 退款单号与状态（0-处理中 / 1-成功 / 2-失败）
     */
    @PostMapping
    public RefundResultCO refundByOrder(@Validated @RequestBody OrderRefundCO request) {
        return orderRefundManager.refundByOrder(request);
    }
}
