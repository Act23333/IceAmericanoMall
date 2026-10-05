package org.icedamericanomall.trade.adapter.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.app.invoice.InvoiceAdminManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端发票接口适配层接线测试（AD57-AD60，F14）：端点路径/参数校验（冲红原因必填）/
 * 对 {@code InvoiceAdminManager} 的委托关系。审计注解 {@code @OperationLog} 为声明式
 * （AOP 管道一），注解存在性由编译保证，行为断言在应用层测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInvoiceController — AD57-AD60 委托与校验")
class AdminInvoiceControllerTest {

    private static final String INVOICE_NO = "3f2a1b6c-0000-0000-0000-000000000001";

    @Mock private InvoiceAdminManager invoiceAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminInvoiceController(invoiceAdminManager)).build();
    }

    @Test
    @DisplayName("AD57 分页 — query 筛选参数绑定，委托 invoiceAdminManager.page")
    void shouldDelegatePage() throws Exception {
        when(invoiceAdminManager.page(any())).thenReturn(new Page<>());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/invoice/page")
                        .param("status", "1").param("sellerId", "200")
                        .param("partialRefund", "true").param("urgeOnly", "true"))
                .andExpect(status().isOk());

        verify(invoiceAdminManager).page(any());
    }

    @Test
    @DisplayName("AD58 开具 — 委托 issue(invoiceNo)")
    void shouldDelegateIssue() throws Exception {
        when(invoiceAdminManager.issue(INVOICE_NO)).thenReturn(new InvoiceVO());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/invoice/" + INVOICE_NO + "/issue"))
                .andExpect(status().isOk());

        verify(invoiceAdminManager).issue(INVOICE_NO);
    }

    @Test
    @DisplayName("AD59 作废 — 委托 voidInvoice(invoiceNo)")
    void shouldDelegateVoid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/invoice/" + INVOICE_NO + "/void"))
                .andExpect(status().isOk());

        verify(invoiceAdminManager).voidInvoice(INVOICE_NO);
    }

    @Test
    @DisplayName("AD60 冲红 — 原因必填：缺失 → 400；齐备 → 委托 redFlush(invoiceNo, reason)")
    void shouldDelegateRedFlush_andValidateReason() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/invoice/" + INVOICE_NO + "/red-flush")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/invoice/" + INVOICE_NO + "/red-flush")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"部分退款人工处理\"}"))
                .andExpect(status().isOk());

        verify(invoiceAdminManager).redFlush(INVOICE_NO, "部分退款人工处理");
    }
}
