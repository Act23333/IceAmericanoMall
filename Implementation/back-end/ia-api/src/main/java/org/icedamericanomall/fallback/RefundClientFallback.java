package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.RefundClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 退款服务（pay-service）降级工厂 —— <b>fail-closed（抛异常）</b>。
 *
 * <p>退款发起是<b>资金命令</b>（ADR-011 三分法中的 ③ 类）：静默降级 = 管理端以为同意即退款，
 * 实际退款单未创建、钱未退。必须让调用方（trade {@code RefundManager}）感知失败——
 * 售后单停留 {@code APPROVED(2)}，由管理端通过 AD43 重试收敛。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}。
 */
@Slf4j
@Component
public class RefundClientFallback implements FallbackFactory<RefundClient> {

    @Override
    public RefundClient create(Throwable cause) {
        return new RefundClient() {

            @Override
            public RefundResultCO refund(RefundCreateCO request) {
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
