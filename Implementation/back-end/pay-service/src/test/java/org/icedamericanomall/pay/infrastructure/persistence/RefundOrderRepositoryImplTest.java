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

    @Test
    @DisplayName("createProcessing — 售后路径统一幂等键：biz_ref 自动落 AS:{afterSaleId}（uk_biz_ref NOT NULL 不变式，ADR-049）")
    void shouldFillAfterSaleBizRef_whenCreateProcessing() {
        repository.createProcessing(9L, "ORD-1", "PAY-1", 100L, "BALANCE", 5000);

        org.mockito.ArgumentCaptor<RefundOrderPO> captor =
                org.mockito.ArgumentCaptor.forClass(RefundOrderPO.class);
        verify(refundOrderMapper).insert(captor.capture());
        assertEquals("AS:9", captor.getValue().getBizRef());
        assertEquals(9L, captor.getValue().getAfterSaleId());
    }

    @Test
    @DisplayName("getByBizRef — 命中时返回聚合（biz_ref 幂等锚点，ADR-049）")
    void shouldReturnRefund_whenBizRefHits() {
        RefundOrderPO po = new RefundOrderPO();
        po.setId(1L);
        po.setRefundNo("R-GB");
        po.setAfterSaleId(null);
        po.setBizRef("GB:ORD-1");
        po.setStatus(RefundStatusEnum.PROCESSING.getCode());
        when(refundOrderMapper.selectList(any())).thenReturn(java.util.List.of(po));

        RefundOrder result = repository.getByBizRef("GB:ORD-1");

        assertEquals("R-GB", result.getRefundNo());
        assertEquals("GB:ORD-1", result.getBizRef());
        assertNull(result.getAfterSaleId());
    }

    @Test
    @DisplayName("getByBizRef — 未命中返回 null")
    void shouldReturnNull_whenBizRefMiss() {
        when(refundOrderMapper.selectList(any())).thenReturn(java.util.List.of());

        assertNull(repository.getByBizRef("GB:MISSING"));
    }

    @Test
    @DisplayName("createOrderRefundProcessing — 无售后单退款落库：after_sale_id=null + biz_ref 传入值（I55，ADR-049）")
    void shouldCreateOrderRefundProcessingWithoutAfterSale() {
        RefundOrder result = repository.createOrderRefundProcessing("ORD-1", "PAY-1", 100L,
                "BALANCE", 5000, "GB:ORD-1");

        assertEquals(RefundStatusEnum.PROCESSING.getCode(), result.getStatus());
        assertEquals("GB:ORD-1", result.getBizRef());
        assertNull(result.getAfterSaleId());
        assertNotNull(result.getRefundNo());

        org.mockito.ArgumentCaptor<RefundOrderPO> captor =
                org.mockito.ArgumentCaptor.forClass(RefundOrderPO.class);
        verify(refundOrderMapper).insert(captor.capture());
        assertNull(captor.getValue().getAfterSaleId());
        assertEquals("GB:ORD-1", captor.getValue().getBizRef());
        assertEquals("BALANCE", captor.getValue().getChannel());
        assertEquals(5000, captor.getValue().getRefundAmount());
    }
}
