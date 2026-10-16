package org.icedamericanomall.marketing.adapter.admin;

import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.app.flash.FlashSaleAdminManager;
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

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminFlashController 适配层接线测试（V5.7 AD40-AD42）。
 *
 * <p>控制器只做参数校验与委托：三端点分别委托 {@code FlashSaleAdminManager} 对应方法；
 * 业务守卫（已结束禁编 / 进行中禁删）的断言在 {@code FlashSaleAdminManagerTest}（应用层）。
 * 另以反射断言三个写操作均标注 {@code @OperationLog}（ADR-032 审计管道一）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFlashController — AD40-AD42 委托 / 参数校验 / 审计注解")
class AdminFlashControllerTest {

    @Mock
    private FlashSaleAdminManager flashSaleAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminFlashController(flashSaleAdminManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("create — 合法请求 → 委托 flashSaleAdminManager.createFlashSale")
    void shouldDelegateCreate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/flash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spuId":10,"skuId":1000,"flashPrice":9900,"stock":100,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isOk());

        verify(flashSaleAdminManager).createFlashSale(any(FlashSaleCreateReq.class));
    }

    @Test
    @DisplayName("create — flashPrice=0 违反 @Positive → 400，不触达应用层")
    void shouldReturn400_whenCreateBodyInvalid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/flash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spuId":10,"skuId":1000,"flashPrice":0,"stock":100,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(flashSaleAdminManager);
    }

    @Test
    @DisplayName("update — 委托 flashSaleAdminManager.updateFlashSale(id, req)")
    void shouldDelegateUpdate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/flash/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flashPrice\":5900}"))
                .andExpect(status().isOk());

        verify(flashSaleAdminManager).updateFlashSale(eq(9L), any(FlashSaleUpdateReq.class));
    }

    @Test
    @DisplayName("delete — 委托 flashSaleAdminManager.deleteFlashSale(id)")
    void shouldDelegateDelete() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/flash/9"))
                .andExpect(status().isOk());

        verify(flashSaleAdminManager).deleteFlashSale(9L);
    }

    @Test
    @DisplayName("审计（ADR-032）— 三个写操作均标注 @OperationLog 且模块为 MARKETING_FLASH")
    void shouldCarryOperationLog_onAllWriteEndpoints() throws Exception {
        Method create = AdminFlashController.class.getMethod("create", FlashSaleCreateReq.class);
        Method update = AdminFlashController.class.getMethod("update", Long.class, FlashSaleUpdateReq.class);
        Method delete = AdminFlashController.class.getMethod("delete", Long.class);

        for (Method method : List.of(create, update, delete)) {
            OperationLog audit = method.getAnnotation(OperationLog.class);
            assertNotNull(audit, method.getName() + " 缺少 @OperationLog 审计注解");
            assertEquals("MARKETING_FLASH", audit.module());
        }
    }
}
