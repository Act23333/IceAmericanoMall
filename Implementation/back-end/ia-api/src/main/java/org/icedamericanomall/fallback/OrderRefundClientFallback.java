package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderRefundClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 无售后单退款命令降级工厂 —— <b>fail-closed（抛异常）</b>，资金命令（同 {@code RefundClientFallback} 定类）。
 */
@Slf4j
@Component
public class OrderRefundClientFallback implements FallbackFactory<OrderRefundClient> {

    @Override
    public OrderRefundClient create(Throwable cause) {
        return new OrderRefundClient() {

            @Override
            public RefundResultCO refundByOrder(OrderRefundCO refund) {
                log.error("订单退款命令降级：pay-service 不可用（资金命令禁止静默丢失）"
                                + " orderNo={}, sceneRef={}",
                        refund == null ? null : refund.getOrderNo(),
                        refund == null ? null : refund.getSceneRef(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "退款服务暂不可用，请稍后重试");
            }
        };
    }
}
