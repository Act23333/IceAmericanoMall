package org.icedamericanomall.trade.adapter.internal;

import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.trade.app.invoice.InvoiceAdminManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;

/**
 * I56 内部联动端点接线测试（F14，契约 §4.14.16c）：委托
 * {@code InvoiceAdminManager.voidOnFullRefund(orderNo, sceneRef)}；
 * 幂等空操作语义（无票/已终态静默返回）在应用层测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalTradeInvoiceController — I56 委托")
class InternalTradeInvoiceControllerTest {

    @Mock private InvoiceAdminManager invoiceAdminManager;

    private InternalTradeInvoiceController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalTradeInvoiceController(invoiceAdminManager);
    }

    @Test
    @DisplayName("I56 — 报文 orderNo/sceneRef 原样透传给管理端编排器")
    void shouldDelegateVoidOnFullRefund() {
        InvoiceVoidCO co = new InvoiceVoidCO("ORD-1", "AS-9");

        assertDoesNotThrow(() -> controller.voidOnFullRefund(co));

        verify(invoiceAdminManager).voidOnFullRefund("ORD-1", "AS-9");
    }
}
