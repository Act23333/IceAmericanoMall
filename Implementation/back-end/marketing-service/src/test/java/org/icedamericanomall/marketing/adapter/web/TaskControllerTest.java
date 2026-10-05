package org.icedamericanomall.marketing.adapter.web;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.marketing.adapter.response.DailyTaskVO;
import org.icedamericanomall.marketing.app.task.TaskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskController 适配层接线测试（V5.10 M14，ADR-047）。
 *
 * <p>控制器只做身份解析与委托：{@code GET /api/task/daily} 取 {@code UserContext}
 * 当前用户（网关注入，禁止信任前端传参）委托 {@code TaskManager.dailyTasks}，
 * {@code Result} 包装透出。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskController — M14 委托 / Result 包装")
class TaskControllerTest {

    private static final Long USER_ID = 9L;

    @Mock
    private TaskManager taskManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskManager)).build();
        UserContext.setUser(UserInfo.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("dailyTasks — 当前用户（UserContext）委托 TaskManager，Result 包装任务列表")
    void shouldDelegateWithCurrentUser_whenListDailyTasks() throws Exception {
        DailyTaskVO vo = new DailyTaskVO();
        vo.setCode("DAILY_ORDER");
        vo.setName("每日完成 1 笔订单确认");
        vo.setRewardPoints(20);
        vo.setCompleted(true);
        when(taskManager.dailyTasks(USER_ID)).thenReturn(List.of(vo));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/task/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("DAILY_ORDER"))
                .andExpect(jsonPath("$.data[0].rewardPoints").value(20))
                .andExpect(jsonPath("$.data[0].completed").value(true));

        verify(taskManager).dailyTasks(eq(USER_ID));
    }
}
