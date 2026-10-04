package org.icedamericanomall.fallback;

import org.icedamericanomall.client.RefundClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.RefundCreateCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 退款服务降级语义测试（ADR-011 三分法 —— <b>fail-closed</b>）。
 *
 * <p>退款发起是资金命令：静默降级 = 管理端以为同意即退款、实际分文未退。
 * 本测试锁死「服务不可用时必须抛错」，避免后续有人把它改成 fail-open。
 */
@DisplayName("RefundClientFallback — 资金命令 fail-closed")
class RefundClientFallbackTest {

    private final RefundClient client = new RefundClientFallback()
            .create(new RuntimeException("pay-service unavailable"));

    @Test
    @DisplayName("服务不可用 → 抛 BizException(SYSTEM_BUSY)，不静默成功")
    void shouldThrowBizException_whenRefundServiceDown() {
        RefundCreateCO request = new RefundCreateCO();
        request.setAfterSaleId(1L);
        request.setOrderNo("ORD-1");
        request.setRefundAmount(12800);

        BizException ex = assertThrows(BizException.class, () -> client.refund(request));

        assertEquals(ErrorCode.SYSTEM_BUSY, ex.getErrorCode());
    }
}
