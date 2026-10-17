package org.icedamericanomall.pay.infrastructure.persistence;

import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkKindEnum;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InvoiceLinkCompensationRepositoryImpl 单元测试 —— 聚合 → PO 映射（BINARY(16) 字节化、列口径）
 * 与「先插后捕」的幂等归一。
 *
 * <p>本类只验证<b>应用侧语义</b>（撞键不抛、字段落位正确）；「唯一键真的在库里」由
 * {@link InvoiceLinkCompensationH2Test} 用真实 H2 约束证明 —— 二者缺一，幂等都只是口头承诺。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceLinkCompensationRepositoryImpl 测试")
class InvoiceLinkCompensationRepositoryImplTest {

    /** 订单号（32 位 hex 业务键，落库为 BINARY(16)）。 */
    private static final String ORDER_NO = "bbbbbbbb000070008000000000000001";
    private static final String SCENE_REF = "AS:9";

    @Mock
    private InvoiceLinkCompensationMapper invoiceLinkCompensationMapper;

    @InjectMocks
    private InvoiceLinkCompensationRepositoryImpl repository;

    private InvoiceLinkCompensation pending(InvoiceLinkKindEnum kind, String lastError) {
        return InvoiceLinkCompensation.newPending(ORDER_NO, SCENE_REF, kind, lastError);
    }

    @Test
    @DisplayName("createPending — PO 落位：order_no 转 BINARY(16) 原始字节、kind=1、status=0、retry_count=0")
    void shouldMapAggregateToPo_whenCreatePending() {
        InvoiceLinkCompensation record = pending(InvoiceLinkKindEnum.VOID, "BizException[SYSTEM_BUSY]: 系统繁忙");

        boolean created = repository.createPending(record);

        assertTrue(created);
        ArgumentCaptor<InvoiceLinkCompensationPO> captor =
                ArgumentCaptor.forClass(InvoiceLinkCompensationPO.class);
        verify(invoiceLinkCompensationMapper).insert(captor.capture());
        InvoiceLinkCompensationPO po = captor.getValue();
        assertArrayEquals(IdGenerator.toBytes(ORDER_NO), po.getOrderNo(),
                "order_no 必须以 16 字节原始形态落库（ADR-050），不得写 hex 字符串");
        assertEquals(SCENE_REF, po.getSceneRef());
        assertEquals(InvoiceLinkKindEnum.VOID.getCode(), po.getKind());
        assertEquals(InvoiceLinkCompensationStatusEnum.PENDING.getCode(), po.getStatus());
        assertEquals(0, po.getRetryCount());
        assertNotNull(po.getNextRetryAt(), "next_retry_at 必须写入（调度器就绪后即刻可拾取）");
        assertTrue(po.getLastError().contains("SYSTEM_BUSY"));
    }

    @Test
    @DisplayName("createPending — 撞 uk_order_scene_kind：DuplicateKeyException 归一为幂等命中（返回 false，不抛）")
    void shouldReturnFalse_whenDuplicateKeyOnInsert() {
        when(invoiceLinkCompensationMapper.insert(any(InvoiceLinkCompensationPO.class)))
                .thenThrow(new DuplicateKeyException("uk_order_scene_kind"));

        boolean created = repository.createPending(pending(InvoiceLinkKindEnum.VOID, "boom"));

        assertFalse(created, "重复 (order_no, scene_ref, kind) 必须归一为幂等命中而不是异常");
    }

    @Test
    @DisplayName("createPending — last_error 超长截断至 500（列宽），落库不因摘要过长失败")
    void shouldTruncateLastError_whenTooLong() {
        String longError = "x".repeat(800);

        repository.createPending(pending(InvoiceLinkKindEnum.PARTIAL, longError));

        ArgumentCaptor<InvoiceLinkCompensationPO> captor =
                ArgumentCaptor.forClass(InvoiceLinkCompensationPO.class);
        verify(invoiceLinkCompensationMapper).insert(captor.capture());
        assertEquals(InvoiceLinkCompensation.LAST_ERROR_MAX_LENGTH, captor.getValue().getLastError().length());
        assertEquals(InvoiceLinkKindEnum.PARTIAL.getCode(), captor.getValue().getKind(),
                "部分退款分支必须落 kind=2（PARTIAL）");
    }
}
