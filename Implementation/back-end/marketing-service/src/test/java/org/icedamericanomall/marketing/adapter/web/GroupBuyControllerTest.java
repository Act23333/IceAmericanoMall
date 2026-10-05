package org.icedamericanomall.marketing.adapter.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.adapter.response.GroupTeamVO;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyParticipationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GroupBuyController 适配层接线测试（V5.14 M15-M17，ADR-049）。
 *
 * <p>控制器只做参数绑定与委托（🌐 匿名，网关仅 GET 放行）；业务口径（1103、
 * 团列表 ≤10）断言在 {@code GroupBuyParticipationManagerTest}。
 * 另以反射锁定隐私口径：{@code GroupTeamVO} 不含任何 userId 字段（不出他人身份）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupBuyController — M15-M17 委托 / 分页默认值 / 隐私口径")
class GroupBuyControllerTest {

    @Mock
    private GroupBuyParticipationManager groupBuyParticipationManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GroupBuyController(groupBuyParticipationManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("activities — 显式 page=2&size=5 → 委托 pageActiveActivities(2, 5)")
    void shouldDelegateActivitiesWithExplicitPaging() throws Exception {
        when(groupBuyParticipationManager.pageActiveActivities(2, 5)).thenReturn(new Page<>(2, 5));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/group-buy/activities")
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(groupBuyParticipationManager).pageActiveActivities(2, 5);
    }

    @Test
    @DisplayName("activities — 缺省分页参数 → page=1&size=10（契约默认值）")
    void shouldUseDefaultPaging_whenParamsAbsent() throws Exception {
        when(groupBuyParticipationManager.pageActiveActivities(1, 10)).thenReturn(new Page<>(1, 10));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/group-buy/activities"))
                .andExpect(status().isOk());

        verify(groupBuyParticipationManager).pageActiveActivities(1, 10);
    }

    @Test
    @DisplayName("activityDetail — 委托 getActivityDetail(id)")
    void shouldDelegateActivityDetail() throws Exception {
        when(groupBuyParticipationManager.getActivityDetail(9L)).thenReturn(new GroupBuyActivityVO());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/group-buy/activity/9"))
                .andExpect(status().isOk());

        verify(groupBuyParticipationManager).getActivityDetail(9L);
    }

    @Test
    @DisplayName("teamDetail — 按团号委托 getTeamDetail(teamNo)")
    void shouldDelegateTeamDetail() throws Exception {
        when(groupBuyParticipationManager.getTeamDetail("team-no-aaa")).thenReturn(new GroupTeamVO());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/group-buy/team/team-no-aaa"))
                .andExpect(status().isOk());

        verify(groupBuyParticipationManager).getTeamDetail("team-no-aaa");
    }

    @Test
    @DisplayName("隐私口径 — GroupTeamVO/GroupBuyActivityVO 均无 userId 类字段（不出他人身份）")
    void shouldNotExposeUserIdFields_onTeamAndActivityVo() {
        List<Class<?>> voClasses = List.of(GroupTeamVO.class, GroupBuyActivityVO.class);
        for (Class<?> voClass : voClasses) {
            for (Field field : voClass.getDeclaredFields()) {
                assertFalse(field.getName().toLowerCase().contains("userid"),
                        voClass.getSimpleName() + " 泄露 userId 字段: " + field.getName());
            }
        }
    }
}
