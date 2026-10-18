package org.icedamericanomall.fallback;

import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 余额服务降级语义测试（ADR-011 三分法 —— <b>两个方法均 fail-closed</b>）。
 *
 * <p>扣款与退款入账都是资金命令：静默降级会造成「扣款未生效却以为已支付」或
 * 「退款未入账却以为已退款」。本测试同时覆盖 {@code deductBalance} 与
 * {@code refund}（V5.5 新增）两条分支。
 */
@DisplayName("BalanceClientFallback — 资金命令 fail-closed")
class BalanceClientFallbackTest {

    private final BalanceClient client = new BalanceClientFallback()
            .create(new RuntimeException("wallet-service unavailable"));

    @Test
    @DisplayName("deductBalance 服务不可用 → BizException(BUSINESS_EXECUTION_EXCEPTION)")
    void shouldThrow_whenDeductAndServiceDown() {
        BizException ex = assertThrows(BizException.class, () -> client.deductBalance(100L, 5000, "pay-no-1"));

        assertEquals(ErrorCode.SYSTEM_BUSY, ex.getErrorCode());
    }

    @Test
    @DisplayName("refund 服务不可用 → BizException(BUSINESS_EXECUTION_EXCEPTION)（退款不得静默成功）")
    void shouldThrow_whenRefundAndServiceDown() {
        BalanceRefundCO request = new BalanceRefundCO();
        request.setTransactionNo("RFD-1");
        request.setUserId(100L);
        request.setAmount(12800);
        request.setBizNo("RFD-1");

        BizException ex = assertThrows(BizException.class, () -> client.refund(request));

        assertEquals(ErrorCode.SYSTEM_BUSY, ex.getErrorCode());
    }
}
