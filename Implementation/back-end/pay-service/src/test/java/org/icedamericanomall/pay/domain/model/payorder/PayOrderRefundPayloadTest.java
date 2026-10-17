package org.icedamericanomall.pay.domain.model.payorder;

import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付单「退款要素完整性」不变式（批次 R1）—— 领域层单元测试，纯 {@code new}，零框架。
 *
 * <p><b>判据来源</b>：{@code collaboration/09-constraints.md} §三·六 的四类划分里，
 * 「支付单<b>存在</b>但缺 {@code bizUserId} / {@code amount}」<b>既不</b>是「资源不存在」（404），
 * 也<b>不</b>是「业务规则拒绝」（200 + code），而是<b>服务端数据不一致</b> —— 归
 * {@code INTERNAL_ERROR(1000)} / HTTP 500，{@link ErrorCategory} 派生为 {@code BUG}、
 * {@code retryable=false}（确定性失败，重试必然重复失败）。
 *
 * <p><b>改前必失败</b>：本批之前这两处由 {@code RefundManager} / {@code OrderRefundManager}
 * 各自抛 {@code ORDER_NOT_FOUND(3009)} —— 批次 ① 把该码的 HTTP 状态改成 404 后，对外表现为
 * 「404 + 支付单金额缺失」，客户端/运维会读成「这笔支付单不存在」，而真相是「这条数据坏了」。
 */
@DisplayName("PayOrder 退款要素完整性不变式（服务端数据缺陷 → INTERNAL_ERROR/BUG）")
class PayOrderRefundPayloadTest {

    private static final String PAY_ORDER_NO = "PAY-1";
    private static final String ORDER_NO = "ORD-1";
    /** 刻意取一个「不可能与支付单号/订单号/金额撞串」的值，锁死「文案不带支付用户 ID」。 */
    private static final long BUYER_ID = 987654L;
    private static final int AMOUNT = 12800;

    private PayOrder payOrder(Long bizUserId, Integer amount) {
        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderNo(PAY_ORDER_NO);
        payOrder.setBizOrderNo(ORDER_NO);
        payOrder.setBizUserId(bizUserId);
        payOrder.setAmount(amount);
        return payOrder;
    }

    /** 四类划分的断言口径：数据缺陷 = 500 语义 + BUG + 不可重试（三处缺陷用例共用）。 */
    private static void assertServerDataDefect(BizException ex) {
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode(), "数据缺陷必须归 INTERNAL_ERROR(1000)");
        assertEquals(1000, ex.getErrorCode().getCode());
        assertEquals(500, ex.getErrorCode().getHttpStatus().value(), "必须是 500，绝不是 404");
        assertEquals(ErrorCategory.BUG, ErrorCategory.of(ex.getErrorCode()),
                "category 必须派生为 BUG（不是 CLIENT/TRANSIENT）");
        assertFalse(ErrorCategory.of(ex.getErrorCode()).isRetryable(), "确定性失败不可重试");
    }

    @Test
    @DisplayName("缺少支付用户 — INTERNAL_ERROR(1000)/500/ BUG /不可重试，文案含支付单号与订单号")
    void shouldThrowInternalError_whenRefundPayeeMissing() {
        PayOrder incomplete = payOrder(null, AMOUNT);

        BizException ex = assertThrows(BizException.class, incomplete::assertRefundPayeePresent);

        assertServerDataDefect(ex);
        assertTrue(ex.getDisplayMessage().contains("支付单数据不完整"), ex.getDisplayMessage());
        assertTrue(ex.getDisplayMessage().contains("缺少支付用户"), ex.getDisplayMessage());
        // 可定位：运维凭这两个号能直接查到坏行，无需再问调用方
        assertTrue(ex.getDisplayMessage().contains(PAY_ORDER_NO), ex.getDisplayMessage());
        assertTrue(ex.getDisplayMessage().contains(ORDER_NO), ex.getDisplayMessage());
        // 不打 PII：支付用户 ID 属个人信息，不进错误文案（更不进 Feign 调用方日志）
        assertFalse(ex.getDisplayMessage().contains(String.valueOf(BUYER_ID)),
                "错误文案不得带支付用户 ID: " + ex.getDisplayMessage());
    }

    @Test
    @DisplayName("缺少金额 — INTERNAL_ERROR(1000)/500/ BUG /不可重试，文案含支付单号与订单号")
    void shouldThrowInternalError_whenRefundAmountMissing() {
        PayOrder incomplete = payOrder(BUYER_ID, null);

        BizException ex = assertThrows(BizException.class, incomplete::assertRefundAmountPresent);

        assertServerDataDefect(ex);
        assertTrue(ex.getDisplayMessage().contains("支付单数据不完整"), ex.getDisplayMessage());
        assertTrue(ex.getDisplayMessage().contains("缺少金额"), ex.getDisplayMessage());
        assertTrue(ex.getDisplayMessage().contains(PAY_ORDER_NO), ex.getDisplayMessage());
        assertTrue(ex.getDisplayMessage().contains(ORDER_NO), ex.getDisplayMessage());
    }

    @Test
    @DisplayName("退款要素齐全 — 两条不变式均放行（不误伤正常支付单）")
    void shouldPass_whenRefundPayloadComplete() {
        PayOrder complete = payOrder(BUYER_ID, AMOUNT);

        assertDoesNotThrow(complete::assertRefundPayeePresent);
        assertDoesNotThrow(complete::assertRefundAmountPresent);
    }
}
