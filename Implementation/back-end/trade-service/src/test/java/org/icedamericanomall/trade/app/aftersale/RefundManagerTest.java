package org.icedamericanomall.trade.app.aftersale;

import org.icedamericanomall.client.RefundClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.repository.AfterSaleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * trade 侧退款编排测试（F1）—— 售后审核同意后经 I40 触发 pay-service 退款的编排与降级语义。
 *
 * <p>锁死两条纪律：①退款参数取自售后单快照（afterSaleId / orderNo / refundAmount）；
 * ②退款发起失败<b>不吞异常</b>（fail-closed），售后单停留 APPROVED 由管理端重试收敛。
 * 退款成功回写（I42）不在本类——由 pay-service 退款成功后回调 {@code InternalAfterSaleController}。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("trade RefundManager — 售后退款触发编排")
class RefundManagerTest {

    @Mock AfterSaleRepository afterSaleRepository;
    @Mock RefundClient refundClient;
    @InjectMocks RefundManager refundManager;

    private AfterSale afterSale() {
        AfterSale entity = new AfterSale();
        entity.setId(9L);
        entity.setOrderNo("ORD-1");
        entity.setUserId(100L);
        entity.setType(2);
        entity.setReason("质量问题");
        entity.setRefundAmount(12800);
        entity.setStatus(2);
        return entity;
    }

    @Test
    @DisplayName("triggerRefund — 以售后单快照发起 I40（afterSaleId/orderNo/refundAmount/reason）")
    void shouldTriggerRefund_whenAfterSaleApproved() {
        when(afterSaleRepository.findById(9L)).thenReturn(afterSale());
        RefundResultCO result = new RefundResultCO();
        result.setRefundNo("RFD-1");
        result.setStatus(0);
        when(refundClient.refund(any(RefundCreateCO.class))).thenReturn(result);

        refundManager.triggerRefund(9L);

        ArgumentCaptor<RefundCreateCO> captor = ArgumentCaptor.forClass(RefundCreateCO.class);
        verify(refundClient).refund(captor.capture());
        assertEquals(9L, captor.getValue().getAfterSaleId());
        assertEquals("ORD-1", captor.getValue().getOrderNo());
        assertEquals(12800, captor.getValue().getRefundAmount());
        assertEquals("质量问题", captor.getValue().getReason());
    }

    @Test
    @DisplayName("triggerRefund — 售后单不存在：跳过触发，不调退款服务")
    void shouldSkip_whenAfterSaleMissing() {
        when(afterSaleRepository.findById(9L)).thenReturn(null);

        refundManager.triggerRefund(9L);

        verifyNoInteractions(refundClient);
    }

    @Test
    @DisplayName("triggerRefund — 退款发起失败：异常向上透出（fail-closed，不静默吞掉）")
    void shouldRethrow_whenRefundClientFails() {
        when(afterSaleRepository.findById(9L)).thenReturn(afterSale());
        when(refundClient.refund(any(RefundCreateCO.class)))
                .thenThrow(new BizException(ErrorCode.SYSTEM_BUSY, "退款服务暂不可用，退款未发起"));

        BizException ex = assertThrows(BizException.class, () -> refundManager.triggerRefund(9L));

        assertEquals(ErrorCode.SYSTEM_BUSY, ex.getErrorCode());
    }

    @Test
    @DisplayName("triggerRefund — 结果为空对象（契约异常）：不抛异常（仅告警），不做本地回写")
    void shouldTolerate_whenResultNull() {
        when(afterSaleRepository.findById(9L)).thenReturn(afterSale());
        when(refundClient.refund(any(RefundCreateCO.class))).thenReturn(null);

        refundManager.triggerRefund(9L);

        verify(refundClient).refund(any(RefundCreateCO.class));
        // I42 回写由 pay-service 回调完成，本编排不直接改售后单状态
        verify(afterSaleRepository, never()).updateStatusConditionallyFrom(anyLong(), anyList(), anyInt());
    }
}
