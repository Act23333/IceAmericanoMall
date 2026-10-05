package org.icedamericanomall.marketing.adapter.admin;

import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.marketing.adapter.request.GroupBuyCreateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyUpdateReq;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyAdminManager;
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
 * AdminGroupBuyController 适配层接线测试（V5.14 AD54-AD56，ADR-049）。
 *
 * <p>控制器只做参数校验与委托：三端点分别委托 {@code GroupBuyAdminManager} 对应方法；
 * 业务守卫（已结束禁编 / 进行中禁删 / 字段守卫）的断言在 {@code GroupBuyAdminManagerTest}。
 * 另以反射断言三个写操作均标注 {@code @OperationLog}（ADR-032 审计管道一）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminGroupBuyController — AD54-AD56 委托 / 参数校验 / 审计注解")
class AdminGroupBuyControllerTest {

    @Mock
    private GroupBuyAdminManager groupBuyAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminGroupBuyController(groupBuyAdminManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("create — 合法请求 → 委托 groupBuyAdminManager.createActivity")
    void shouldDelegateCreate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/group-buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"iPhone 拼团","sellerId":500,"productId":1,"skuId":2,
                                 "groupPrice":9900,"requiredMembers":3,"timeLimitHours":24,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isOk());

        verify(groupBuyAdminManager).createActivity(any(GroupBuyCreateReq.class));
    }

    @Test
    @DisplayName("create — groupPrice=0 违反 @Positive → 400，不触达应用层")
    void shouldReturn400_whenCreateBodyInvalid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/group-buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"iPhone 拼团","sellerId":500,"productId":1,"skuId":2,
                                 "groupPrice":0,"requiredMembers":3,"timeLimitHours":24,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupBuyAdminManager);
    }

    @Test
    @DisplayName("create — requiredMembers=1 违反 @Min(2) → 400，不触达应用层")
    void shouldReturn400_whenRequiredMembersBelowTwo() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/group-buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"iPhone 拼团","sellerId":500,"productId":1,"skuId":2,
                                 "groupPrice":9900,"requiredMembers":1,"timeLimitHours":24,
                                 "startTime":"2026-10-05T10:00:00","endTime":"2026-10-06T10:00:00"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupBuyAdminManager);
    }

    @Test
    @DisplayName("update — 委托 groupBuyAdminManager.updateActivity(id, req)")
    void shouldDelegateUpdate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/group-buy/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"双12拼团\"}"))
                .andExpect(status().isOk());

        verify(groupBuyAdminManager).updateActivity(eq(9L), any(GroupBuyUpdateReq.class));
    }

    @Test
    @DisplayName("delete — 委托 groupBuyAdminManager.deleteActivity(id)")
    void shouldDelegateDelete() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/group-buy/9"))
                .andExpect(status().isOk());

        verify(groupBuyAdminManager).deleteActivity(9L);
    }

    @Test
    @DisplayName("审计（ADR-032）— 三个写操作均标注 @OperationLog 且模块为 MARKETING_GROUP_BUY")
    void shouldCarryOperationLog_onAllWriteEndpoints() throws Exception {
        Method create = AdminGroupBuyController.class.getMethod("create", GroupBuyCreateReq.class);
        Method update = AdminGroupBuyController.class.getMethod("update", Long.class, GroupBuyUpdateReq.class);
        Method delete = AdminGroupBuyController.class.getMethod("delete", Long.class);

        for (Method method : List.of(create, update, delete)) {
            OperationLog audit = method.getAnnotation(OperationLog.class);
            assertNotNull(audit, method.getName() + " 缺少 @OperationLog 审计注解");
            assertEquals("MARKETING_GROUP_BUY", audit.module());
        }
    }
}
