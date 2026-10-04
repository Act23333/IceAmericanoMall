package org.icedamericanomall.pay.infrastructure.persistence;

import org.icedamericanomall.pay.domain.enums.RefundStatusEnum;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefundOrderRepositoryImpl 单元测试 —— PO ↔ 聚合映射与条件更新委托（F1 退款资金流）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundOrderRepositoryImpl 测试")
class RefundOrderRepositoryImplTest {

    @Mock
    private RefundOrderMapper refundOrderMapper;

    @InjectMocks
    private RefundOrderRepositoryImpl repository;

    @Test
    @DisplayName("getByRefundNo — 命中时返回聚合")
    void shouldReturnRefund_whenRefundNoHits() {
        RefundOrderPO po = new RefundOrderPO();
        po.setId(1L);
        po.setRefundNo("R123");
        po.setAfterSaleId(9L);
        po.setStatus(RefundStatusEnum.PROCESSING.getCode());
        when(refundOrderMapper.selectList(any())).thenReturn(java.util.List.of(po));

        RefundOrder result = repository.getByRefundNo("R123");

        assertEquals("R123", result.getRefundNo());
        assertEquals(9L, result.getAfterSaleId());
        assertEquals(RefundStatusEnum.PROCESSING.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("getByAfterSaleId — 未命中返回 null")
    void shouldReturnNull_whenAfterSaleMiss() {
        when(refundOrderMapper.selectList(any())).thenReturn(java.util.List.of());

        assertNull(repository.getByAfterSaleId(999L));
    }

    @Test
    @DisplayName("createProcessing — 经聚合工厂创建（状态默认处理中）后落库并回填 id")
    void shouldCreateProcessingViaFactory() {
        RefundOrder result = repository.createProcessing(9L, "ORD-1", "PAY-1", 100L, "BALANCE", 5000);

        assertEquals(RefundStatusEnum.PROCESSING.getCode(), result.getStatus());
        assertEquals(9L, result.getAfterSaleId());
        assertNotNull(result.getRefundNo());

        org.mockito.ArgumentCaptor<RefundOrderPO> captor =
                org.mockito.ArgumentCaptor.forClass(RefundOrderPO.class);
        verify(refundOrderMapper).insert(captor.capture());
        assertEquals("BALANCE", captor.getValue().getChannel());
        assertEquals(5000, captor.getValue().getRefundAmount());
    }
}
