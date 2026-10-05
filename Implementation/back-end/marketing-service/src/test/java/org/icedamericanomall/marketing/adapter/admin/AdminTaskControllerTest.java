package org.icedamericanomall.marketing.adapter.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.marketing.adapter.request.TaskCreateReq;
import org.icedamericanomall.marketing.adapter.request.TaskUpdateReq;
import org.icedamericanomall.marketing.app.task.TaskAdminManager;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminTaskController 适配层接线测试（V5.13 AD51-AD53）。
 *
 * <p>控制器只做参数校验与委托：三端点分别委托 {@code TaskAdminManager} 对应方法；
 * 业务守卫（code 唯一双路径 400 / 1103 / 停用口径）的断言在 {@code TaskAdminManagerTest}
 * （应用层）。另以反射断言写操作均标注 {@code @OperationLog}（ADR-032 审计管道一）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTaskController — AD51-AD53 委托 / 参数校验 / 审计注解")
class AdminTaskControllerTest {

    @Mock
    private TaskAdminManager taskAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminTaskController(taskAdminManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("page — status/page/size 查询参数透传 taskAdminManager.page")
    void shouldDelegatePage_withQueryParams() throws Exception {
        when(taskAdminManager.page(0, 2, 10)).thenReturn(new Page<>(2, 10));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/task")
                        .param("status", "0")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(taskAdminManager).page(0, 2, 10);
    }

    @Test
    @DisplayName("page — 参数缺省 → status=null, page=1, size=20（契约缺省口径）")
    void shouldUseDefaultPaging_whenParamsAbsent() throws Exception {
        when(taskAdminManager.page(null, 1, 20)).thenReturn(new Page<>(1, 20));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/task"))
                .andExpect(status().isOk());

        verify(taskAdminManager).page(null, 1, 20);
    }

    @Test
    @DisplayName("create — 合法请求 → 委托 taskAdminManager.create")
    void shouldDelegateCreate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DAILY_ORDER","name":"每日下单任务","rewardPoints":20}
                                """))
                .andExpect(status().isOk());

        verify(taskAdminManager).create(any(TaskCreateReq.class));
    }

    @Test
    @DisplayName("create — code 含小写（违反 ^[A-Z0-9_]{1,32}$ 正则）→ 400，不触达应用层")
    void shouldReturn400_whenCodeLowercase() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"daily_order","name":"每日下单任务","rewardPoints":20}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskAdminManager);
    }

    @Test
    @DisplayName("create — rewardPoints 缺失（违反 @NotNull）→ 400，不触达应用层")
    void shouldReturn400_whenRewardPointsMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DAILY_ORDER","name":"每日下单任务"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskAdminManager);
    }

    @Test
    @DisplayName("update — 委托 taskAdminManager.update(code, req)")
    void shouldDelegateUpdate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/task/DAILY_ORDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardPoints\":50}"))
                .andExpect(status().isOk());

        verify(taskAdminManager).update(eq("DAILY_ORDER"), any(TaskUpdateReq.class));
    }

    @Test
    @DisplayName("审计（ADR-032）— 写操作均标注 @OperationLog 且模块为 MARKETING_TASK")
    void shouldCarryOperationLog_onWriteEndpoints() throws Exception {
        Method create = AdminTaskController.class.getMethod("create", TaskCreateReq.class);
        Method update = AdminTaskController.class.getMethod("update", String.class, TaskUpdateReq.class);

        for (Method method : List.of(create, update)) {
            OperationLog audit = method.getAnnotation(OperationLog.class);
            assertNotNull(audit, method.getName() + " 缺少 @OperationLog 审计注解");
            assertEquals("MARKETING_TASK", audit.module());
        }
    }
}
