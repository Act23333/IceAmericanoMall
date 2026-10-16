package org.icedamericanomall.marketing.app.groupbuy;

import org.icedamericanomall.client.GroupBuyTeamClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.domain.enums.GroupTeamStatusEnum;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeamMember;
import org.icedamericanomall.marketing.domain.repository.GroupBuyActivityRepository;
import org.icedamericanomall.marketing.domain.repository.GroupTeamMemberRepository;
import org.icedamericanomall.marketing.domain.repository.GroupTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupBuyParticipationManager 参与用例单元测试（V5.14 I52/I53/M15-M17/结团，ADR-049）。
 *
 * <p>覆盖：I52 校验面（活动无效/团不存在/团已结/团满/已参团/开团与参团校验通过）、
 * I53 语义（开团建团+首成员 / 参团条件 +1 / 满员条件置已成团 / 条件更新 0 行抛异常 /
 * uk_order_no 重放幂等返回现状 / uk_team_user 撞键回落）、
 * 结团（过期团条件置 3 + 逐团触发退款 + 单团异常继续 + 条件更新未中跳过退款）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupBuyParticipationManager — I52/I53 参团联动与结团单元测试")
class GroupBuyParticipationManagerTest {
    /** 出线格式的业务键（32-hex）+ 内部技术键（刻意不同值）。 */
    private static final String SKU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SPU_BUSINESS_ID = "aabbccddeeff00112233445566778899";
    private static final Long SKU_PK = 2L;
    private static final Long SPU_PK = 1L;

    /** I52 回执与视图装配共用的读模型端口（两种入参口径都命中同一行）。 */
    private final org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery briefQuery =
            new org.icedamericanomall.marketing.app.port.FlashSaleSpuBriefQuery() {
                @Override
                public java.util.Map<Long, org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief> findBySkuIds(
                        java.util.Collection<Long> skuIds) {
                    return java.util.Map.of(SKU_PK, brief());
                }

                @Override
                public java.util.Map<String, org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief>
                        findBySkuBusinessIds(java.util.Collection<String> skuBusinessIds) {
                    return java.util.Map.of(SKU_BUSINESS_ID, brief());
                }
            };

    private static org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief brief() {
        return new org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief(
                SKU_PK, SPU_PK, SKU_BUSINESS_ID, SPU_BUSINESS_ID, "拼团商品", "https://cdn/main.jpg", 12800);
    }


    private static final Long ACTIVITY_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long OTHER_TEAM_ID = 11L;
    private static final Long USER_ID = 100L;
    private static final Long LEADER_ID = 200L;
    private static final String TEAM_NO = "team-no-aaa";
    private static final String OTHER_TEAM_NO = "team-no-bbb";
    private static final String ORDER_NO = "order-no-001";

    @Mock
    private GroupBuyActivityRepository groupBuyActivityRepository;

    @Mock
    private GroupTeamRepository groupTeamRepository;

    @Mock
    private GroupTeamMemberRepository groupTeamMemberRepository;

    @Mock
    private GroupBuyTeamClient groupBuyTeamClient;

    private final MarketingConverter converter = Mappers.getMapper(MarketingConverter.class);

    private GroupBuyParticipationManager manager;

    @BeforeEach
    void setUp() {
        manager = new GroupBuyParticipationManager(groupBuyActivityRepository, groupTeamRepository,
                groupTeamMemberRepository, converter, groupBuyTeamClient,
                new org.icedamericanomall.marketing.app.groupbuy.GroupBuyViewManager(converter, briefQuery));
        // 仓储 INSERT 主键回填（模拟 MyBatis-Plus 生成主键后写回聚合）
        lenient().doAnswer(invocation -> {
            GroupTeam team = invocation.getArgument(0);
            team.setId(TEAM_ID);
            return null;
        }).when(groupTeamRepository).save(any(GroupTeam.class));
    }

    // ==================== I52 validate ====================

    @Test
    @DisplayName("validate — 开团校验（teamNo 空）→ 返回拼团价等建单依据，不触达团仓储")
    void shouldReturnGroupPrice_whenValidateOpenTeam() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());

        GroupBuyValidateResultCO result = manager.validate(validateCO(null));

        assertEquals(ACTIVITY_ID, result.getActivityId());
        assertEquals(9900, result.getGroupPrice());
        assertEquals(3, result.getRequiredMembers());
        assertEquals(24, result.getTimeLimitHours());
        assertEquals(SKU_BUSINESS_ID, result.getSkuId(),
                "ADR-061 §决策 2：I52 回执出 SKU 业务键（技术键 2 不得出现）");
        verify(groupTeamRepository, never()).findByTeamNo(anyString());
    }

    @Test
    @DisplayName("validate — 参团校验（进行中未满未参团）→ 回显 teamNo 并通过")
    void shouldPassValidate_whenJoiningValidOngoingTeam() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(2, 3));
        when(groupTeamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(false);

        GroupBuyValidateResultCO result = manager.validate(validateCO(TEAM_NO));

        assertEquals(TEAM_NO, result.getTeamNo());
        assertEquals(9900, result.getGroupPrice());
    }

    @Test
    @DisplayName("validate — 活动不存在 → PARAM_ERROR")
    void shouldThrow_whenValidateActivityMissing() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.validate(validateCO(null)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("validate — 活动已过时间窗（时间窗推导，不信任快照）→ PARAM_ERROR")
    void shouldThrow_whenValidateActivityWindowOver() {
        GroupBuyActivity ended = activity(LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(ended);

        BizException ex = assertThrows(BizException.class, () -> manager.validate(validateCO(null)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("validate — 参团时团不存在 → NO_RESOURCE_FOUND(1103)")
    void shouldThrow_whenValidateTeamMissing() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> manager.validate(validateCO(TEAM_NO)));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("validate — 团已失败（status=3）→ PARAM_ERROR")
    void shouldThrow_whenValidateTeamEnded() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
        when(groupTeamRepository.findByTeamNo(TEAM_NO))
                .thenReturn(team(TEAM_ID, TEAM_NO, 2, 3, GroupTeamStatusEnum.FAILED.getCode()));

        BizException ex = assertThrows(BizException.class, () -> manager.validate(validateCO(TEAM_NO)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("validate — 团已满员 → PARAM_ERROR")
    void shouldThrow_whenValidateTeamFull() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(3, 3));

        BizException ex = assertThrows(BizException.class, () -> manager.validate(validateCO(TEAM_NO)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("validate — 用户已在该团（uk_team_user 预检）→ PARAM_ERROR")
    void shouldThrow_whenValidateAlreadyJoined() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(2, 3));
        when(groupTeamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> manager.validate(validateCO(TEAM_NO)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    // ==================== I53 join ====================

    @Test
    @DisplayName("join — uk_order_no 幂等重放 → 返回其已入团的现状，不重复建团/参团")
    void shouldReturnCurrentState_whenOrderNoReplayed() {
        GroupTeamMember replayed = GroupTeamMember.join(TEAM_ID, USER_ID, ORDER_NO);
        when(groupTeamMemberRepository.findByOrderNo(ORDER_NO)).thenReturn(replayed);
        when(groupTeamRepository.findById(TEAM_ID))
                .thenReturn(team(TEAM_ID, TEAM_NO, 2, 2, GroupTeamStatusEnum.SUCCEEDED.getCode()));

        GroupTeamCO result = manager.join(joinCO(TEAM_NO));

        assertEquals(2, result.getCurrentMembers());
        assertEquals(GroupTeamStatusEnum.SUCCEEDED.getCode(), result.getStatus());
        verify(groupBuyActivityRepository, never()).findById(anyLong());
        verify(groupTeamRepository, never()).save(any(GroupTeam.class));
        verify(groupTeamMemberRepository, never()).save(any(GroupTeamMember.class));
    }

    @Test
    @DisplayName("join — 开团（teamNo 空）→ 建团（leader/currentMembers=1/expire≈now+时限）+ 首成员行")
    void shouldOpenTeamWithFirstMember_whenJoinWithoutTeamNo() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
        LocalDateTime before = LocalDateTime.now();

        GroupTeamCO result = manager.join(joinCO(null));

        ArgumentCaptor<GroupTeam> teamCaptor = ArgumentCaptor.forClass(GroupTeam.class);
        verify(groupTeamRepository).save(teamCaptor.capture());
        GroupTeam opened = teamCaptor.getValue();
        assertNotNull(opened.getTeamNo());
        assertEquals(32, opened.getTeamNo().length());
        assertEquals(ACTIVITY_ID, opened.getActivityId());
        assertEquals(USER_ID, opened.getLeaderUserId());
        assertEquals(3, opened.getRequiredMembers());
        assertEquals(1, opened.getCurrentMembers());
        assertEquals(GroupTeamStatusEnum.ONGOING.getCode(), opened.getStatus());
        assertTrue(opened.getExpireTime().isAfter(before.plusHours(23)));

        verify(groupTeamMemberRepository).save(argThat(member ->
                member != null && Long.valueOf(TEAM_ID).equals(member.getTeamId())
                        && Long.valueOf(USER_ID).equals(member.getUserId())
                        && ORDER_NO.equals(member.getOrderNo())));

        assertEquals(opened.getTeamNo(), result.getTeamNo());
        assertEquals(1, result.getCurrentMembers());
        assertEquals(3, result.getRequiredMembers());
        assertEquals(GroupTeamStatusEnum.ONGOING.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("join — 参团补满最后一员 → 条件 +1 成功且满员条件置已成团(2)，回读现状返回")
    void shouldJoinAndMarkSuccess_whenLastMemberFillsTeam() {
        stubActiveActivity();
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(1, 2));
        when(groupTeamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(false);
        when(groupTeamRepository.incrementCurrentMembers(TEAM_ID)).thenReturn(1);
        when(groupTeamRepository.markTeamSuccessIfFull(TEAM_ID)).thenReturn(1);
        when(groupTeamRepository.findById(TEAM_ID))
                .thenReturn(team(TEAM_ID, TEAM_NO, 2, 2, GroupTeamStatusEnum.SUCCEEDED.getCode()));

        GroupTeamCO result = manager.join(joinCO(TEAM_NO));

        verify(groupTeamMemberRepository).save(argThat(member ->
                member != null && Long.valueOf(USER_ID).equals(member.getUserId())
                        && ORDER_NO.equals(member.getOrderNo())));
        verify(groupTeamRepository).incrementCurrentMembers(TEAM_ID);
        verify(groupTeamRepository).markTeamSuccessIfFull(TEAM_ID);
        assertEquals(GroupTeamStatusEnum.SUCCEEDED.getCode(), result.getStatus());
        assertEquals(2, result.getCurrentMembers());
    }

    @Test
    @DisplayName("join — 参团条件更新 0 行（团满/已结竞态兜底）→ PARAM_ERROR，不置已成团")
    void shouldThrow_whenConditionalIncrementReturnsZero() {
        stubActiveActivity();
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(1, 3));
        when(groupTeamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(false);
        when(groupTeamRepository.incrementCurrentMembers(TEAM_ID)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> manager.join(joinCO(TEAM_NO)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupTeamRepository, never()).markTeamSuccessIfFull(anyLong());
    }

    @Test
    @DisplayName("join — 成员行插入撞 uk_order_no（重放入参竞态）→ 回落返回该单已入团现状")
    void shouldFallbackToCurrentState_whenOrderNoKeyCollides() {
        stubActiveActivity();
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(1, 3));
        when(groupTeamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(false);
        doThrow(new DuplicateKeyException("uk_order_no"))
                .when(groupTeamMemberRepository).save(any(GroupTeamMember.class));
        when(groupTeamMemberRepository.findByOrderNo(ORDER_NO))
                .thenReturn(null)
                .thenReturn(GroupTeamMember.join(TEAM_ID, USER_ID, ORDER_NO));
        when(groupTeamRepository.findById(TEAM_ID)).thenReturn(ongoingTeam(1, 3));

        GroupTeamCO result = manager.join(joinCO(TEAM_NO));

        assertEquals(TEAM_NO, result.getTeamNo());
        verify(groupTeamRepository, never()).incrementCurrentMembers(anyLong());
    }

    @Test
    @DisplayName("join — 成员行插入撞 uk_team_user（同人同团新订单）→ 回落返回现状")
    void shouldFallbackToCurrentState_whenTeamUserKeyCollides() {
        stubActiveActivity();
        when(groupTeamRepository.findByTeamNo(TEAM_NO)).thenReturn(ongoingTeam(1, 3));
        // 第 1 次 = ensureTeamJoinable 预检（未参团）；第 2 次 = resolveDuplicateJoin 回落确认（已参团）
        when(groupTeamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(false, true);
        doThrow(new DuplicateKeyException("uk_team_user"))
                .when(groupTeamMemberRepository).save(any(GroupTeamMember.class));
        when(groupTeamMemberRepository.findByOrderNo(ORDER_NO)).thenReturn(null);
        when(groupTeamRepository.findById(TEAM_ID)).thenReturn(ongoingTeam(1, 3));

        GroupTeamCO result = manager.join(joinCO(TEAM_NO));

        assertEquals(TEAM_NO, result.getTeamNo());
        verify(groupTeamRepository, never()).incrementCurrentMembers(anyLong());
    }

    @Test
    @DisplayName("join — 团已失败 → PARAM_ERROR，不插成员行")
    void shouldThrow_whenJoinEndedTeam() {
        stubActiveActivity();
        when(groupTeamRepository.findByTeamNo(TEAM_NO))
                .thenReturn(team(TEAM_ID, TEAM_NO, 2, 3, GroupTeamStatusEnum.FAILED.getCode()));

        BizException ex = assertThrows(BizException.class, () -> manager.join(joinCO(TEAM_NO)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(groupTeamMemberRepository, never()).save(any(GroupTeamMember.class));
    }

    // ==================== 结团 ====================

    @Test
    @DisplayName("expire — 过期团条件置已失败(1→3)并按成员 orderNos 触发退款联动")
    void shouldFailTeamAndTriggerRefund_whenTeamExpired() {
        GroupTeam expired = team(TEAM_ID, TEAM_NO, 2, 3, GroupTeamStatusEnum.ONGOING.getCode());
        when(groupTeamRepository.listExpiredTeams(any(LocalDateTime.class), anyInt())).thenReturn(List.of(expired));
        when(groupTeamRepository.updateTeamStatusConditional(TEAM_ID,
                GroupTeamStatusEnum.ONGOING.getCode(), GroupTeamStatusEnum.FAILED.getCode())).thenReturn(1);
        when(groupTeamMemberRepository.listByTeamId(TEAM_ID)).thenReturn(List.of(
                GroupTeamMember.join(TEAM_ID, LEADER_ID, "ON-1"),
                GroupTeamMember.join(TEAM_ID, USER_ID, "ON-2")));

        manager.expireOverdueTeams();

        ArgumentCaptor<GroupTeamRefundCO> captor = ArgumentCaptor.forClass(GroupTeamRefundCO.class);
        verify(groupBuyTeamClient).refundTeam(captor.capture());
        assertEquals(TEAM_NO, captor.getValue().getTeamNo());
        assertEquals(List.of("ON-1", "ON-2"), captor.getValue().getOrderNos());
    }

    @Test
    @DisplayName("expire — 条件置失败 0 行（已被其他执行方推进）→ 跳过退款，不重复触发")
    void shouldSkipRefund_whenConditionalUpdateMissed() {
        GroupTeam expired = team(TEAM_ID, TEAM_NO, 2, 3, GroupTeamStatusEnum.ONGOING.getCode());
        when(groupTeamRepository.listExpiredTeams(any(LocalDateTime.class), anyInt())).thenReturn(List.of(expired));
        when(groupTeamRepository.updateTeamStatusConditional(anyLong(), anyInt(), anyInt())).thenReturn(0);

        manager.expireOverdueTeams();

        verify(groupBuyTeamClient, never()).refundTeam(any(GroupTeamRefundCO.class));
    }

    @Test
    @DisplayName("expire — 单团退款联动异常 → try/catch 继续下一团，不中断整批")
    void shouldContinueNextTeam_whenSingleTeamRefundFails() {
        GroupTeam first = team(TEAM_ID, TEAM_NO, 2, 3, GroupTeamStatusEnum.ONGOING.getCode());
        GroupTeam second = team(OTHER_TEAM_ID, OTHER_TEAM_NO, 1, 3, GroupTeamStatusEnum.ONGOING.getCode());
        when(groupTeamRepository.listExpiredTeams(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(first, second));
        when(groupTeamRepository.updateTeamStatusConditional(anyLong(), anyInt(), anyInt())).thenReturn(1);
        when(groupTeamMemberRepository.listByTeamId(anyLong())).thenReturn(List.of());
        doThrow(new RuntimeException("trade unavailable"))
                .when(groupBuyTeamClient).refundTeam(argThat(r -> r != null && TEAM_NO.equals(r.getTeamNo())));

        manager.expireOverdueTeams();

        verify(groupBuyTeamClient, times(2)).refundTeam(any(GroupTeamRefundCO.class));
        verify(groupBuyTeamClient).refundTeam(argThat(r -> r != null && OTHER_TEAM_NO.equals(r.getTeamNo())));
    }

    @Test
    @DisplayName("expire — 无过期团 → 不触发任何退款")
    void shouldDoNothing_whenNoExpiredTeams() {
        when(groupTeamRepository.listExpiredTeams(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        manager.expireOverdueTeams();

        verify(groupBuyTeamClient, never()).refundTeam(any(GroupTeamRefundCO.class));
    }

    // ==================== 构造辅助 ====================

    /** 非重放的 join 路径均先经 requireActiveActivity，公共 stub 活动有效。 */
    private void stubActiveActivity() {
        when(groupBuyActivityRepository.findById(ACTIVITY_ID)).thenReturn(activeActivity());
    }

    private GroupBuyValidateCO validateCO(String teamNo) {
        return new GroupBuyValidateCO(ACTIVITY_ID, teamNo, USER_ID);
    }

    private GroupJoinCO joinCO(String teamNo) {
        return new GroupJoinCO(USER_ID, ORDER_NO, ACTIVITY_ID, teamNo);
    }

    private GroupBuyActivity activeActivity() {
        return activity(LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3));
    }

    private GroupBuyActivity activity(LocalDateTime start, LocalDateTime end) {
        return GroupBuyActivity.builder()
                .id(ACTIVITY_ID)
                .name("iPhone 拼团")
                .sellerId(500L)
                .spuId(1L)
                .skuId(2L)
                .groupPrice(9900)
                .requiredMembers(3)
                .timeLimitHours(24)
                .startTime(start)
                .endTime(end)
                .version(0)
                .build();
    }

    private GroupTeam ongoingTeam(int current, int required) {
        return team(TEAM_ID, TEAM_NO, current, required, GroupTeamStatusEnum.ONGOING.getCode());
    }

    private GroupTeam team(Long teamId, String teamNo, int current, int required, int status) {
        return GroupTeam.builder()
                .id(teamId)
                .teamNo(teamNo)
                .activityId(ACTIVITY_ID)
                .leaderUserId(LEADER_ID)
                .requiredMembers(required)
                .currentMembers(current)
                .status(status)
                .expireTime(LocalDateTime.now().plusHours(20))
                .build();
    }
}
