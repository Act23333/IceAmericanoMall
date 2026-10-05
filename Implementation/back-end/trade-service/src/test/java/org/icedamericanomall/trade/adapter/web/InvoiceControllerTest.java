package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.app.invoice.InvoiceApplyManager;
import org.icedamericanomall.trade.app.invoice.InvoiceBuyerManager;
import org.icedamericanomall.trade.adapter.request.InvoiceApplyReq;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 买家发票接口适配层接线测试（INV1-INV5，F14）：登录态身份注入、参数校验
 * （@Valid 必填约束）、各端点对应用例执行器的委托关系。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceController — INV1-INV5 委托与校验")
class InvoiceControllerTest {

    private static final long USER_ID = 100L;
    private static final String INVOICE_NO = "3f2a1b6c-0000-0000-0000-000000000001";

    @Mock private InvoiceApplyManager invoiceApplyManager;
    @Mock private InvoiceBuyerManager invoiceBuyerManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InvoiceController(invoiceApplyManager, invoiceBuyerManager)).build();
        UserContext.setUser(UserInfo.builder().userId(USER_ID).username("buyer").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("INV1 申请 — 委托 InvoiceApplyManager.apply(userId, req)")
    void shouldDelegateApply() throws Exception {
        when(invoiceApplyManager.apply(eq(USER_ID), any(InvoiceApplyReq.class)))
                .thenReturn(new InvoiceVO());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/invoice/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNo":"ORD-1","type":1,"title":"张三"}
                                """))
                .andExpect(status().isOk());

        verify(invoiceApplyManager).apply(eq(USER_ID), any(InvoiceApplyReq.class));
    }

    @Test
    @DisplayName("INV1 校验 — orderNo 缺失 / type 缺失 → 400（@Valid 必填约束）")
    void shouldRejectApply_whenRequiredFieldsMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/invoice/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"张三\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/invoice/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"ORD-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("INV2 分页 — query 绑定默认 page=1/size=20，委托 pageMy")
    void shouldDelegateHistory() throws Exception {
        when(invoiceBuyerManager.pageMy(eq(USER_ID), any())).thenReturn(new Page<>());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/invoice/history"))
                .andExpect(status().isOk());

        verify(invoiceBuyerManager).pageMy(eq(USER_ID), any());
    }

    @Test
    @DisplayName("INV3 详情 — 委托 detail(userId, invoiceNo)")
    void shouldDelegateDetail() throws Exception {
        when(invoiceBuyerManager.detail(USER_ID, INVOICE_NO)).thenReturn(new InvoiceVO());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/invoice/" + INVOICE_NO))
                .andExpect(status().isOk());

        verify(invoiceBuyerManager).detail(USER_ID, INVOICE_NO);
    }

    @Test
    @DisplayName("INV4 撤回 / INV5 催开 — 分别委托 cancel 与 urge")
    void shouldDelegateCancelAndUrge() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/invoice/" + INVOICE_NO + "/cancel"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.put("/api/invoice/" + INVOICE_NO + "/urge"))
                .andExpect(status().isOk());

        verify(invoiceBuyerManager).cancel(USER_ID, INVOICE_NO);
        verify(invoiceBuyerManager).urge(USER_ID, INVOICE_NO);
    }
}
