package org.icedamericanomall.marketing.adapter.internal;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyParticipationManager;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalGroupBuyController 适配层接线测试（V5.14 I52/I53，ADR-049）。
 *
 * <p>控制器只做参数校验与委托：{@code /internal/group-buy/validate|join} 委托
 * {@code GroupBuyParticipationManager}；{@code /internal/**} 不包 {@code Result}
 * （业务异常以真实 HTTP 状态透出，ADR-004）；{@code activityId}/{@code orderNo} 缺失 → 400。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalGroupBuyController — I52/I53 委托 / 参数校验 / 裸 CO 透出")
class InternalGroupBuyControllerTest {

    @Mock
    private GroupBuyParticipationManager groupBuyParticipationManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalGroupBuyController(groupBuyParticipationManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("validate — 合法 body → 委托 validate 且透传 activityId/teamNo/userId，裸 CO 透出")
    void shouldDelegateValidate() throws Exception {
        when(groupBuyParticipationManager.validate(anyValidateCO()))
                .thenReturn(new GroupBuyValidateResultCO(1L, "team-no-aaa", 9900, 3, 24, "0f1e2d3c4b5a69788796a5b4c3d2e1f0"));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/group-buy/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"teamNo\":\"team-no-aaa\",\"userId\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(1))
                .andExpect(jsonPath("$.teamNo").value("team-no-aaa"))
                .andExpect(jsonPath("$.groupPrice").value(9900));

        verify(groupBuyParticipationManager).validate(argThat(query -> query != null
                && Long.valueOf(1L).equals(query.getActivityId())
                && "team-no-aaa".equals(query.getTeamNo())
                && Long.valueOf(100L).equals(query.getUserId())));
    }

    @Test
    @DisplayName("validate — activityId 缺失 → 400（@Valid），不触达应用层")
    void shouldReturn400_whenActivityIdMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/group-buy/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":100}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupBuyParticipationManager);
    }

    @Test
    @DisplayName("join — 合法 body → 委托 join 且透传幂等锚点 orderNo，裸 CO 透出（不包 Result）")
    void shouldDelegateJoin() throws Exception {
        when(groupBuyParticipationManager.join(anyJoinCO()))
                .thenReturn(new GroupTeamCO("team-no-aaa", 1, 2, 3));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/group-buy/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":100,\"orderNo\":\"ON-1\",\"activityId\":1,\"teamNo\":\"team-no-aaa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamNo").value("team-no-aaa"))
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.currentMembers").value(2));

        verify(groupBuyParticipationManager).join(argThat(join -> join != null
                && "ON-1".equals(join.getOrderNo())
                && Long.valueOf(100L).equals(join.getUserId())
                && "team-no-aaa".equals(join.getTeamNo())));
    }

    @Test
    @DisplayName("join — orderNo 缺失（幂等锚点不得为空）→ 400（@Valid），不触达应用层")
    void shouldReturn400_whenOrderNoMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/group-buy/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":100,\"activityId\":1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupBuyParticipationManager);
    }

    // ==================== 类型辅助（argThat 泛型推断） ====================

    private static org.icedamericanomall.dto.GroupBuyValidateCO anyValidateCO() {
        return org.mockito.ArgumentMatchers.any(GroupBuyValidateCO.class);
    }

    private static GroupJoinCO anyJoinCO() {
        return org.mockito.ArgumentMatchers.any(GroupJoinCO.class);
    }
}
