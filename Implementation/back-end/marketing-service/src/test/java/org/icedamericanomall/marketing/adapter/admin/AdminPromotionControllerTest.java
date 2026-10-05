package org.icedamericanomall.marketing.adapter.admin;

import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.marketing.adapter.request.PromotionCreateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionUpdateReq;
import org.icedamericanomall.marketing.app.promotion.PromotionAdminManager;
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
 * AdminPromotionController 适配层接线测试（V5.8 AD48-AD50）。
 *
 * <p>控制器只做参数校验与委托：三端点分别委托 {@code PromotionAdminManager} 对应方法；
 * 业务守卫（已结束禁编 / 进行中禁删）的断言在 {@code PromotionAdminManagerTest}（应用层）。
 * 另以反射断言三个写操作均标注 {@code @OperationLog}（ADR-032 审计管道一）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPromotionController — AD48-AD50 委托 / 参数校验 / 审计注解")
class AdminPromotionControllerTest {

    @Mock
    private PromotionAdminManager promotionAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPromotionController(promotionAdminManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("create — 合法请求 → 委托 promotionAdminManager.createPromotion")
    void shouldDelegateCreate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/promotion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"双11店铺满减","sellerId":500,"type":1,"threshold":10000,
                                 "discountValue":2000,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isOk());

        verify(promotionAdminManager).createPromotion(any(PromotionCreateReq.class));
    }

    @Test
    @DisplayName("create — threshold=0 违反 @Positive → 400，不触达应用层")
    void shouldReturn400_whenCreateBodyInvalid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/promotion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"双11店铺满减","sellerId":500,"type":1,"threshold":0,
                                 "discountValue":2000,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promotionAdminManager);
    }

    @Test
    @DisplayName("create — maxDiscount 显式 0（不封顶）→ @PositiveOrZero 放行并委托")
    void shouldDelegateCreate_whenMaxDiscountExplicitZero() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/promotion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"双11店铺满折","sellerId":500,"type":2,"threshold":10000,
                                 "discountValue":8500,"maxDiscount":0,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isOk());

        verify(promotionAdminManager).createPromotion(any(PromotionCreateReq.class));
    }

    @Test
    @DisplayName("update — 委托 promotionAdminManager.updatePromotion(id, req)")
    void shouldDelegateUpdate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/promotion/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"双12店铺满折\"}"))
                .andExpect(status().isOk());

        verify(promotionAdminManager).updatePromotion(eq(9L), any(PromotionUpdateReq.class));
    }

    @Test
    @DisplayName("delete — 委托 promotionAdminManager.deletePromotion(id)")
    void shouldDelegateDelete() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/promotion/9"))
                .andExpect(status().isOk());

        verify(promotionAdminManager).deletePromotion(9L);
    }

    @Test
    @DisplayName("审计（ADR-032）— 三个写操作均标注 @OperationLog 且模块为 MARKETING_PROMOTION")
    void shouldCarryOperationLog_onAllWriteEndpoints() throws Exception {
        Method create = AdminPromotionController.class.getMethod("create", PromotionCreateReq.class);
        Method update = AdminPromotionController.class.getMethod("update", Long.class, PromotionUpdateReq.class);
        Method delete = AdminPromotionController.class.getMethod("delete", Long.class);

        for (Method method : List.of(create, update, delete)) {
            OperationLog audit = method.getAnnotation(OperationLog.class);
            assertNotNull(audit, method.getName() + " 缺少 @OperationLog 审计注解");
            assertEquals("MARKETING_PROMOTION", audit.module());
        }
    }
}
