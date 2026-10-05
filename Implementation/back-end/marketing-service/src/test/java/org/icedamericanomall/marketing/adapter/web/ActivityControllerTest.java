package org.icedamericanomall.marketing.adapter.web;

import org.icedamericanomall.marketing.adapter.response.ActivityVO;
import org.icedamericanomall.marketing.app.promotion.ActivityManager;
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

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ActivityController 适配层接线测试（V5.11 M12/M13）。
 *
 * <p>控制器只做参数绑定与委托：列表与详情均委托 {@code ActivityManager}，
 * {@code Result} 包装透出；不校验业务规则（领域口径在应用层/领域层）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityController — M12/M13 委托 / Result 包装")
class ActivityControllerTest {

    @Mock
    private ActivityManager activityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActivityController(activityManager)).build();
    }

    @Test
    @DisplayName("list — type 查询参数透传 ActivityManager，Result 包装活动列表")
    void shouldDelegateWithType_whenListActivities() throws Exception {
        ActivityVO vo = new ActivityVO();
        vo.setId(1L);
        vo.setType("FULL_REDUCE");
        vo.setName("春季家装满减");
        when(activityManager.listActiveActivities("FULL_REDUCE")).thenReturn(List.of(vo));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/activity").param("type", "FULL_REDUCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].type").value("FULL_REDUCE"));

        verify(activityManager).listActiveActivities(eq("FULL_REDUCE"));
    }

    @Test
    @DisplayName("detail — 路径 id 透传 ActivityManager，Result 包装活动详情")
    void shouldDelegateWithId_whenGetDetail() throws Exception {
        ActivityVO vo = new ActivityVO();
        vo.setId(7L);
        vo.setType("LIMIT_DISCOUNT");
        when(activityManager.getActivityDetail(7L)).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/activity/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.type").value("LIMIT_DISCOUNT"));

        verify(activityManager).getActivityDetail(eq(7L));
    }
}
