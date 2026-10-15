package org.icedamericanomall.notification.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.notification.app.InAppMessageManager;
import org.icedamericanomall.notification.domain.model.PageResult;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分页查询参数「线格式为 snake_case」回归测试 —— {@code GET /api/notifications}。
 *
 * <p><b>为什么需要</b>：全平台线上字段统一 snake_case（Stripe/GitHub 风格）时，
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE} <b>只管 JSON body/响应</b>，
 * 管不到查询参数 —— 查询参数的线上名取自 {@code @RequestParam} 的 {@code name}
 * （缺省时回落到 Java 形参名）。本端点曾以 camelCase {@code pageNum}/{@code pageSize}
 * 暴露，客户端按契约传 {@code page_num}/{@code page_size} 会静默落默认值。
 *
 * <p>本类是该断言的**双向守护**：正例证明 snake 名能取到值；反例证明 camelCase 名
 * <b>不再</b>被绑定（即线上名确已切换，而不是两种写法都收）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController — 分页参数线格式 snake_case（page_num/page_size）")
class NotificationControllerQueryNamingTest {

    private static final long USER_ID = 7L;

    @Mock
    private InAppMessageManager inAppMessageManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(inAppMessageManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UserContext.setUser(UserInfo.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("list — snake 名 page_num=2&page_size=5 → 原样绑定并委托 pageInApp(7, null, 2, 5)")
    void shouldBindPaging_whenSnakeQueryNamesUsed() throws Exception {
        when(inAppMessageManager.pageInApp(USER_ID, null, 2L, 5L))
                .thenReturn(new PageResult<>(List.of(), 0L, 2L, 5L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications")
                        .param("page_num", "2").param("page_size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(inAppMessageManager).pageInApp(USER_ID, null, 2L, 5L);
    }

    @Test
    @DisplayName("list — snake 名 type 筛选 → 与分页参数一并绑定")
    void shouldBindTypeFilter_whenSnakeQueryNamesUsed() throws Exception {
        when(inAppMessageManager.pageInApp(USER_ID, "SYSTEM", 3L, 20L))
                .thenReturn(new PageResult<>(List.of(), 0L, 3L, 20L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications")
                        .param("type", "SYSTEM")
                        .param("page_num", "3").param("page_size", "20"))
                .andExpect(status().isOk());

        verify(inAppMessageManager).pageInApp(USER_ID, "SYSTEM", 3L, 20L);
    }

    @Test
    @DisplayName("list — camelCase pageNum/pageSize 不再绑定 → 回落契约默认值 page=1/size=10")
    void shouldIgnoreCamelCaseQueryNames_whenLegacyNamesUsed() throws Exception {
        when(inAppMessageManager.pageInApp(USER_ID, null, 1L, 10L))
                .thenReturn(new PageResult<>(List.of(), 0L, 1L, 10L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications")
                        .param("pageNum", "2").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(inAppMessageManager).pageInApp(USER_ID, null, 1L, 10L);
    }

    @Test
    @DisplayName("list — 分页参数缺省 → 契约默认值 page_num=1&page_size=10")
    void shouldUseContractDefaults_whenPagingParamsAbsent() throws Exception {
        when(inAppMessageManager.pageInApp(USER_ID, null, 1L, 10L))
                .thenReturn(new PageResult<>(List.of(), 0L, 1L, 10L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications"))
                .andExpect(status().isOk());

        verify(inAppMessageManager).pageInApp(USER_ID, null, 1L, 10L);
    }
}
