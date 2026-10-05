package org.icedamericanomall.marketing.adapter.internal;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.dto.TaskCompleteResultCO;
import org.icedamericanomall.marketing.app.task.TaskManager;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalTaskController 适配层接线测试（V5.10 I49，ADR-047）。
 *
 * <p>控制器只做参数校验与委托：{@code POST /internal/task/complete} 委托
 * {@code TaskManager.completeTask}；{@code /internal/**} 不包 {@code Result}、
 * 裸 CO 透出（ADR-004）；userId/taskCode 缺失 → 400。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalTaskController — I49 委托 / 参数校验")
class InternalTaskControllerTest {

    @Mock
    private TaskManager taskManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalTaskController(taskManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("complete — 合法 body → 委托 taskManager.completeTask 且透传 userId/taskCode")
    void shouldDelegateComplete() throws Exception {
        when(taskManager.completeTask(any(TaskCompleteCO.class)))
                .thenReturn(new TaskCompleteResultCO(true, 20));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/task/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"taskCode\":\"DAILY_ORDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.rewardPoints").value(20));

        verify(taskManager).completeTask(argThat(co -> co != null
                && Long.valueOf(1L).equals(co.getUserId())
                && "DAILY_ORDER".equals(co.getTaskCode())));
    }

    @Test
    @DisplayName("complete — userId/taskCode 缺失 → 400（@Valid），不触达应用层")
    void shouldReturn400_whenBodyFieldsMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/task/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskManager);
    }

    @Test
    @DisplayName("complete — 幂等回落 → 原样透出 false+0（裸 CO，不包 Result，不抛业务异常）")
    void shouldReturnRawNotCompleted_whenReplay() throws Exception {
        when(taskManager.completeTask(any(TaskCompleteCO.class)))
                .thenReturn(new TaskCompleteResultCO(false, 0));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/task/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"taskCode\":\"DAILY_ORDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.rewardPoints").value(0));
    }
}
